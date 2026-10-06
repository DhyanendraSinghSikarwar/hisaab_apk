package com.hisaab.email.mime

import com.hisaab.parser.statement.SpreadsheetLines
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.util.TimeZone
import java.util.zip.ZipInputStream

/** What kind of statement file some bytes are, told from the bytes first and the file name second. */
enum class StatementFileKind(val extension: String) { PDF("pdf"), XLSX("xlsx"), XLS("xls"), CSV("csv"), UNKNOWN("bin") }

/**
 * Bank and card statements sent as spreadsheets (.xlsx, .xls) or CSV, read on the device into the same text
 * lines a PDF gives, so [com.hisaab.parser.statement.StatementParser] reads them alike.
 *
 * .xlsx is a zip of XML: the shared strings, the cell styles (to tell dates from numbers) and each sheet are read
 * with XmlPullParser. .xls (BIFF) is read with JExcelApi. A password-protected workbook is reported as
 * [PdfOpen.Protected]: neither format can be opened without Excel's own decryption.
 */
object SpreadsheetExtractor {

    private val NAMES = Regex("""\.(xlsx|xlsm|xls|csv)$""", RegexOption.IGNORE_CASE)

    /** True for the attachment names this reads: .xls, .xlsx, .xlsm and .csv. */
    fun isSpreadsheetName(name: String): Boolean = NAMES.containsMatchIn(name.trim())

    fun isSpreadsheetMime(type: String): Boolean = type.lowercase().let {
        it == "application/vnd.ms-excel" || it == "text/csv" || it == "application/csv" || it == "text/comma-separated-values" ||
            it == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" || it == "application/vnd.ms-excel.sheet.macroenabled.12"
    }

    fun kind(bytes: ByteArray, name: String): StatementFileKind {
        val head = String(bytes, 0, minOf(bytes.size, 1024), Charsets.ISO_8859_1)
        val lower = name.lowercase()
        return when {
            head.contains("%PDF") -> StatementFileKind.PDF
            bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() && bytes[2].toInt() == 3 && bytes[3].toInt() == 4 -> StatementFileKind.XLSX
            bytes.size >= 8 && OLE.indices.all { bytes[it] == OLE[it] } -> StatementFileKind.XLS
            lower.endsWith(".csv") || looksLikeText(bytes) && !lower.endsWith(".pdf") -> StatementFileKind.CSV
            lower.endsWith(".pdf") -> StatementFileKind.PDF
            else -> StatementFileKind.UNKNOWN
        }
    }

    /** The statement's text lines, or why there are none. Never throws. */
    fun open(bytes: ByteArray, name: String): PdfOpen = try {
        when (kind(bytes, name)) {
            StatementFileKind.XLSX -> text(readXlsx(bytes))
            StatementFileKind.XLS -> if (isEncryptedOoxml(bytes)) PdfOpen.Protected else readXls(bytes)
            StatementFileKind.CSV -> text(SpreadsheetLines.parseCsv(decode(bytes)))
            else -> PdfOpen.Unreadable
        }
    } catch (_: Exception) {
        PdfOpen.Unreadable
    } catch (_: OutOfMemoryError) {
        PdfOpen.Unreadable
    }

    private fun text(rows: List<List<String>>): PdfOpen =
        if (rows.isEmpty()) PdfOpen.Unreadable else PdfOpen.Text(SpreadsheetLines.toText(rows))

    // .xls

    private fun readXls(bytes: ByteArray): PdfOpen {
        val settings = jxl.WorkbookSettings().apply {
            setGCDisabled(true)
            setSuppressWarnings(true)
            setEncoding("Cp1252")
        }
        val workbook = try {
            jxl.Workbook.getWorkbook(ByteArrayInputStream(bytes), settings)
        } catch (_: jxl.read.biff.PasswordException) {
            return PdfOpen.Protected
        } catch (_: jxl.read.biff.BiffException) {
            return PdfOpen.Unreadable
        }
        try {
            val rows = ArrayList<List<String>>()
            for (sheet in workbook.sheets) {
                for (r in 0 until sheet.rows) {
                    rows += sheet.getRow(r).map { cell ->
                        when (cell) {
                            is jxl.DateCell -> {
                                // JExcelApi gives the date at midnight GMT.
                                val c = java.util.Calendar.getInstance(TimeZone.getTimeZone("GMT")).apply { time = cell.date }
                                "%02d/%02d/%04d".format(c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.YEAR))
                            }
                            is jxl.NumberCell -> plain(cell.value)
                            else -> cell.contents.orEmpty()
                        }
                    }
                }
            }
            return text(rows)
        } finally {
            workbook.close()
        }
    }

    /** An .xlsx saved with a password is an OLE2 file holding "EncryptionInfo" and "EncryptedPackage" streams. */
    private fun isEncryptedOoxml(bytes: ByteArray): Boolean {
        val needle = "EncryptionInfo".toByteArray(Charsets.UTF_16LE)
        val limit = minOf(bytes.size, 1 shl 20) - needle.size
        outer@ for (i in 0..limit) {
            for (j in needle.indices) if (bytes[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }

    // .xlsx

    private fun readXlsx(bytes: ByteArray): List<List<String>> {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var total = 0L
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.trimStart('/')
                if (name == "xl/sharedStrings.xml" || name == "xl/styles.xml" || name == "xl/workbook.xml" || SHEET.matches(name)) {
                    val data = readCapped(zip, MAX_ENTRY)
                    total += data.size
                    if (total > MAX_TOTAL) break
                    entries[name] = data
                }
            }
        }
        val strings = entries["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        val dateStyles = entries["xl/styles.xml"]?.let(::dateStyles).orEmpty()
        val sheets = entries.keys.filter { SHEET.matches(it) }.sortedBy { SHEET.find(it)!!.groupValues[1].toInt() }
        val rows = ArrayList<List<String>>()
        for (s in sheets) rows += sheetRows(entries.getValue(s), strings, dateStyles)
        return rows
    }

    private fun readCapped(input: InputStream, cap: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > cap) throw java.io.IOException("Sheet too large")
        }
        return out.toByteArray()
    }

    private fun parser(data: ByteArray): XmlPullParser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser().apply {
        setInput(ByteArrayInputStream(data), null)
    }

    private fun XmlPullParser.local(): String = name.orEmpty().substringAfter(':')

    private fun sharedStrings(data: ByteArray): List<String> {
        val out = ArrayList<String>()
        val p = parser(data)
        val sb = StringBuilder()
        var inT = false
        var phonetic = 0
        while (true) {
            when (p.next()) {
                XmlPullParser.END_DOCUMENT -> return out
                XmlPullParser.START_TAG -> when (p.local()) {
                    "si" -> sb.setLength(0)
                    "t" -> inT = phonetic == 0
                    "rPh" -> phonetic++
                }
                XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.local()) {
                    "si" -> out += sb.toString()
                    "t" -> inT = false
                    "rPh" -> phonetic--
                }
            }
        }
    }

    /** Indexes into cellXfs whose number format shows a date. */
    private fun dateStyles(data: ByteArray): Set<Int> {
        val customDate = HashSet<Int>()
        val out = HashSet<Int>()
        val p = parser(data)
        var inCellXfs = false
        var index = 0
        while (true) {
            when (p.next()) {
                XmlPullParser.END_DOCUMENT -> return out
                XmlPullParser.START_TAG -> when (p.local()) {
                    "numFmt" -> {
                        val id = p.getAttributeValue(null, "numFmtId")?.toIntOrNull()
                        val code = p.getAttributeValue(null, "formatCode").orEmpty()
                        if (id != null && isDateFormat(code)) customDate += id
                    }
                    "cellXfs" -> { inCellXfs = true; index = 0 }
                    "xf" -> if (inCellXfs) {
                        val id = p.getAttributeValue(null, "numFmtId")?.toIntOrNull() ?: 0
                        if (id in 14..22 || id in 45..47 || id in customDate) out += index
                        index++
                    }
                }
                XmlPullParser.END_TAG -> if (p.local() == "cellXfs") inCellXfs = false
            }
        }
    }

    private fun isDateFormat(code: String): Boolean {
        val bare = code.replace(Regex("""\[[^\]]*\]|"[^"]*"|\\."""), "").lowercase()
        return bare.contains('d') || bare.contains('y') || (bare.contains('m') && !bare.contains('h') && !bare.contains('s') && !bare.contains('0'))
    }

    private fun sheetRows(data: ByteArray, strings: List<String>, dateStyles: Set<Int>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val p = parser(data)
        var row: MutableList<String>? = null
        var col = 0
        var type: String? = null
        var style = -1
        val value = StringBuilder()
        var inValue = false
        while (true) {
            when (p.next()) {
                XmlPullParser.END_DOCUMENT -> return rows
                XmlPullParser.START_TAG -> when (p.local()) {
                    "row" -> { row = ArrayList(); col = 0 }
                    "c" -> {
                        col = p.getAttributeValue(null, "r")?.let(::columnOf) ?: col
                        type = p.getAttributeValue(null, "t")
                        style = p.getAttributeValue(null, "s")?.toIntOrNull() ?: -1
                        value.setLength(0)
                    }
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) value.append(p.text)
                XmlPullParser.END_TAG -> when (p.local()) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val r = row ?: continue
                        val raw = value.toString()
                        val cell = when (type) {
                            "s" -> raw.trim().toIntOrNull()?.let { strings.getOrNull(it) }.orEmpty()
                            "inlineStr", "str", "e" -> raw
                            "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                            else -> raw.toDoubleOrNull()?.let { if (style in dateStyles) excelDate(it) else plain(it) } ?: raw
                        }
                        while (r.size < col) r += ""
                        if (r.size == col) r += cell else r[col] = cell
                        col++
                    }
                    "row" -> { row?.let { if (it.any { c -> c.isNotBlank() }) rows += it }; row = null }
                }
            }
        }
    }

    /** "C12" -> 2 (columns from 0). */
    private fun columnOf(ref: String): Int? {
        var n = 0
        var any = false
        for (ch in ref) {
            if (ch in 'A'..'Z') { n = n * 26 + (ch - 'A' + 1); any = true } else if (ch in 'a'..'z') { n = n * 26 + (ch - 'a' + 1); any = true } else break
        }
        return if (any) n - 1 else null
    }

    private fun excelDate(v: Double): String {
        val d = LocalDate.of(1899, 12, 30).plusDays(v.toLong())
        return "%02d/%02d/%04d".format(d.dayOfMonth, d.monthValue, d.year)
    }

    /** 500.0 -> "500", 1234.5 -> "1234.5", 1.23456789012E11 -> "123456789012": never in exponent form. */
    private fun plain(v: Double): String = runCatching { BigDecimal(v.toString()).stripTrailingZeros().toPlainString() }.getOrDefault(v.toString())

    // CSV

    private fun decode(bytes: ByteArray): String {
        val utf8 = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            utf8.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }

    private fun looksLikeText(bytes: ByteArray): Boolean {
        val n = minOf(bytes.size, 2048)
        if (n == 0) return false
        var control = 0
        for (i in 0 until n) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) return false
            if (b < 9 || b in 14..31) control++
        }
        return control * 50 < n
    }

    private val OLE = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
    private val SHEET = Regex("""xl/worksheets/sheet(\d+)\.xml""")
    private const val MAX_ENTRY = 40 * 1024 * 1024
    private const val MAX_TOTAL = 80L * 1024 * 1024
}
