package com.hisaab.parser.statement

import java.time.LocalDate

/**
 * Turns the rows of a statement spreadsheet (.xls, .xlsx or .csv) into the text lines [StatementParser] reads
 * from a PDF: one line per row, cells joined by two spaces.
 *
 * Spreadsheets lose what a PDF shows: amounts are bare numbers ("500" rather than "500.00") and dates may be
 * Excel day serials. So the header row is found (Date, Narration, Withdrawal, Deposit, Balance...), the amount
 * columns are written with two decimals, a separate debit or credit column gets "Dr" or "Cr" after its amount,
 * and serial numbers in a date column become dd/MM/yyyy.
 */
object SpreadsheetLines {

    fun toText(rows: List<List<String>>): String = toLines(rows).joinToString("\n")

    fun toLines(rows: List<List<String>>): List<String> {
        val headerIndex = rows.indexOfFirst { isHeader(it) }
        val header = rows.getOrNull(headerIndex).orEmpty().map { it.trim().lowercase() }
        val roles = header.map(::roleOf)
        val out = ArrayList<String>(rows.size)
        for ((r, row) in rows.withIndex()) {
            val cells = if (headerIndex < 0 || r <= headerIndex) {
                row.map { it.trim() }
            } else {
                row.mapIndexed { c, raw -> format(raw.trim(), roles.getOrNull(c) ?: Role.OTHER) }
            }
            val line = cells.filter { it.isNotEmpty() }.joinToString("  ")
            if (line.isNotBlank()) out += line
        }
        return out
    }

    private enum class Role { DATE, DEBIT, CREDIT, AMOUNT, BALANCE, OTHER }

    private val DATE_HEAD = Regex("""\b(?:date|dt)\b""")
    private val DEBIT_HEAD = Regex("""\b(?:debit|debits|withdrawal|withdrawals|withdrawal amt|dr|paid out|money out)\b""")
    private val CREDIT_HEAD = Regex("""\b(?:credit|credits|deposit|deposits|deposit amt|cr|paid in|money in)\b""")
    private val AMOUNT_HEAD = Regex("""\b(?:amount|amt|transaction amount|inr|value)\b""")
    private val BALANCE_HEAD = Regex("""\bbalance\b|\bbal\b""")
    private val HEADER_WORDS = Regex("""\b(?:date|narration|description|particulars|details|remarks|withdrawal|deposit|debit|credit|amount|balance|chq|cheque|ref)\b""")

    private fun isHeader(row: List<String>): Boolean = row.count { HEADER_WORDS.containsMatchIn(it.lowercase()) && it.length < 40 } >= 3

    private fun roleOf(h: String): Role = when {
        BALANCE_HEAD.containsMatchIn(h) -> Role.BALANCE
        DATE_HEAD.containsMatchIn(h) -> Role.DATE
        DEBIT_HEAD.containsMatchIn(h) && !CREDIT_HEAD.containsMatchIn(h) -> Role.DEBIT
        CREDIT_HEAD.containsMatchIn(h) && !DEBIT_HEAD.containsMatchIn(h) -> Role.CREDIT
        AMOUNT_HEAD.containsMatchIn(h) -> Role.AMOUNT
        else -> Role.OTHER
    }

    private val PLAIN_NUMBER = Regex("""^-?[\d,]*\d(?:\.\d+)?$""")
    private val SUFFIXED = Regex("""^(-?[\d,]*\d(?:\.\d+)?)\s*(Cr|Dr|CR|DR|cr|dr)\.?$""")

    private fun format(cell: String, role: Role): String {
        if (cell.isEmpty()) return cell
        return when (role) {
            Role.DATE -> serialDate(cell) ?: cell
            Role.DEBIT, Role.CREDIT, Role.AMOUNT, Role.BALANCE -> {
                val suffixed = SUFFIXED.find(cell)
                val number = suffixed?.groupValues?.get(1) ?: cell.takeIf { PLAIN_NUMBER.matches(it) } ?: return cell
                val v = number.replace(",", "").toDoubleOrNull() ?: return cell
                if (v == 0.0 && role != Role.BALANCE) return "" // a zero in the unused debit/credit column
                val mark = suffixed?.groupValues?.get(2)?.let { if (it.startsWith("C", true)) " Cr" else " Dr" }
                    ?: when {
                        role == Role.DEBIT -> " Dr"
                        role == Role.CREDIT -> " Cr"
                        role == Role.AMOUNT && v < 0 -> " Dr"
                        else -> ""
                    }
                "%.2f".format(java.util.Locale.ROOT, kotlin.math.abs(v)) + mark
            }
            Role.OTHER -> cell
        }
    }

    /** Excel stores a date as days since 30 Dec 1899: 46000 is 9 Dec 2025. */
    fun serialDate(cell: String): String? {
        val v = cell.toDoubleOrNull() ?: return null
        if (v < 20_000 || v > 80_000) return null
        val d = LocalDate.of(1899, 12, 30).plusDays(v.toLong())
        return "%02d/%02d/%04d".format(d.dayOfMonth, d.monthValue, d.year)
    }

    /** Splits CSV text into rows: quoted cells, doubled quotes, and a comma, semicolon or tab delimiter. */
    fun parseCsv(text: String): List<List<String>> {
        val body = text.removePrefix("﻿")
        val firstLines = body.lineSequence().take(20).toList()
        val delimiter = listOf(',', ';', '\t', '|').maxBy { d -> firstLines.sumOf { l -> l.count { it == d } } }
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < body.length) {
            val ch = body[i]
            when {
                quoted && ch == '"' && body.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                ch == '"' && (quoted || cell.isBlank()) -> { quoted = !quoted; if (quoted) cell.setLength(0) }
                !quoted && ch == delimiter -> { row += cell.toString(); cell.setLength(0) }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && body.getOrNull(i + 1) == '\n') i++
                    row += cell.toString(); cell.setLength(0)
                    if (row.any { it.isNotBlank() }) rows += row
                    row = ArrayList()
                }
                else -> cell.append(ch)
            }
            i++
        }
        row += cell.toString()
        if (row.any { it.isNotBlank() }) rows += row
        return rows
    }
}
