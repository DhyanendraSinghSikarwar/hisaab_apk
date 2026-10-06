package com.hisaab.app.ui.more

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.hisaab.app.ui.format.Periods
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min

enum class TaxShareFormat(val mime: String, val ext: String) { IMAGE("image/png", "png"), PDF("application/pdf", "pdf") }

/**
 * The shareable tax summary: one layout of cards drawn with [Canvas], rendered either to a 1440 px wide PNG
 * or to an A4 [PdfDocument] whose text stays vector. Always uses real amounts (the caller asks first when
 * amounts are hidden on screen), and works from light-theme brand colours so it reads well anywhere.
 */
internal object TaxReport {
    private const val IMAGE_WIDTH_PX = 1440
    /** Logical width of the image layout; 1440 px / 480 = 3x density, so 12-unit text is crisp on any phone. */
    private const val IMAGE_UNITS = 480f
    private const val IMAGE_MARGIN = 20f
    private const val PDF_W = 595f
    private const val PDF_H = 842f
    private const val PDF_MARGIN = 36f
    private const val GAP = 12f

    // Brand colours, light theme.
    private val BG = 0xFFF2F3F7.toInt()
    private val CARD = 0xFFFFFFFF.toInt()
    private val INK = 0xFF16181D.toInt()
    private val INK2 = 0xFF5D626C.toInt()
    private val BORDER = 0xFFE6E6E1.toInt()
    private val SURFACE2 = 0xFFF0F0EC.toInt()
    private val ACCENT = 0xFF2F5BEA.toInt()
    private val ACCENT_SOFT = 0xFFE9EEFD.toInt()
    private val VIOLET = 0xFF7A5AE0.toInt()
    private val POS = 0xFF13895A.toInt()
    private val POS_SOFT = 0xFFE3F4EC.toInt()

    private const val DISCLAIMER =
        "This is an estimate, not tax advice. It is worked out from the transactions Hisaab tracked (and any what-if " +
            "figures entered) for a resident individual under 60, using the slabs, standard deduction, 87A rebate and 4% cess " +
            "for the year. It leaves out surcharge, capital gains and deductions Hisaab cannot see; salary credits are after " +
            "TDS and PF, so taxable salary may be higher. Check with a tax professional before you file."

    /** Writes the summary to cacheDir/shared/ and returns the file. Runs off the main thread. */
    fun export(context: Context, s: TaxState, format: TaxShareFormat): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.filter { it.name.startsWith("Hisaab-tax-estimate") }?.forEach { it.delete() }
        val file = File(dir, "Hisaab-tax-estimate-${s.fyLong.removePrefix("FY ")}.${format.ext}")
        when (format) {
            TaxShareFormat.IMAGE -> writePng(s, file)
            TaxShareFormat.PDF -> writePdf(s, file)
        }
        return file
    }

    private fun writePng(s: TaxState, file: File) {
        val scale = IMAGE_WIDTH_PX / IMAGE_UNITS
        val blocks = blocks(s, IMAGE_MARGIN, IMAGE_UNITS - 2 * IMAGE_MARGIN, pdf = false)
        val heightUnits = IMAGE_MARGIN * 2 + blocks.sumOf { it.height.toDouble() }.toFloat() + GAP * (blocks.size - 1)
        val bmp = Bitmap.createBitmap(IMAGE_WIDTH_PX, ceil(heightUnits * scale).toInt(), Bitmap.Config.ARGB_8888)
        try {
            val c = Canvas(bmp)
            c.drawColor(BG)
            c.scale(scale, scale)
            var y = IMAGE_MARGIN
            blocks.forEach { b -> b.draw(c, y); y += b.height + GAP }
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bmp.recycle()
        }
    }

    private fun writePdf(s: TaxState, file: File) {
        val contentW = PDF_W - 2 * PDF_MARGIN
        val blocks = blocks(s, PDF_MARGIN, contentW, pdf = true)
        val bottom = PDF_H - PDF_MARGIN - 18f // room for the page footer
        // Lay out first so every page can say "page x of n".
        val pages = mutableListOf(mutableListOf<Pair<Block, Float>>())
        var y = PDF_MARGIN
        blocks.forEach { b ->
            if (y + b.height > bottom && pages.last().isNotEmpty()) { pages.add(mutableListOf()); y = PDF_MARGIN }
            pages.last().add(b to y)
            y += b.height + GAP
        }
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { i, list ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PDF_W.toInt(), PDF_H.toInt(), i + 1).create())
                val c = page.canvas
                list.forEach { (b, top) -> b.draw(c, top) }
                val foot = text(8f, INK2)
                c.drawText("Hisaab · Tax estimate · ${s.fyLong}", PDF_MARGIN, PDF_H - PDF_MARGIN + 4f, foot)
                foot.textAlign = Paint.Align.RIGHT
                c.drawText("Page ${i + 1} of ${pages.size}", PDF_W - PDF_MARGIN, PDF_H - PDF_MARGIN + 4f, foot)
                doc.finishPage(page)
            }
            file.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    // -----------------------------------------------------------------------------------------
    // Layout.
    // -----------------------------------------------------------------------------------------

    private class Block(val height: Float, val draw: (Canvas, Float) -> Unit)

    /** One table row: a label (with an optional note under it) and right-aligned values. */
    private class TRow(
        val label: String,
        val values: List<String>,
        val sub: String? = null,
        val bold: Boolean = false,
        val divider: Boolean = false,
        val header: Boolean = false,
        val color: Int = INK,
    )

    private fun blocks(s: TaxState, x: Float, w: Float, pdf: Boolean): List<Block> = buildList {
        val generated = LocalDate.now(Periods.zone).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
        add(header(s, x, w, generated))
        add(result(s, x, w))
        add(card("Income", x, w, emptyList(), incomeRows(s)))
        add(card("Deductions", x, w, emptyList(), deductionRows(s)))
        add(card("How it adds up", x, w, listOf("New", "Old"), sumRows(s)))
        add(slabCard("New regime · slab breakdown", s.newRegime, TaxMath.NEW, x, w))
        add(slabCard("Old regime · slab breakdown", s.oldRegime, TaxMath.OLD, x, w))
        add(footer(x, w, generated, pdf))
    }

    private fun header(s: TaxState, x: Float, w: Float, generated: String): Block = Block(118f) { c, top ->
        val r = RectF(x, top, x + w, top + 118f)
        val g = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(r.left, r.top, r.right, r.bottom, ACCENT, VIOLET, Shader.TileMode.CLAMP) }
        c.drawRoundRect(r, 18f, 18f, g)
        // Two faint rings, as on the app's hero cards.
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 14f; color = 0x14FFFFFF }
        c.save(); c.clipRect(r)
        c.drawCircle(r.right - 30f, r.top + 10f, 60f, ring)
        c.drawCircle(r.right - 4f, r.bottom - 6f, 34f, ring)
        c.restore()

        val brand = text(10f, 0xCCFFFFFF.toInt(), bold = true).apply { letterSpacing = 0.25f }
        c.drawText("HISAAB", x + 20f, top + 28f, brand)
        if (s.whatIf.any) pill(c, "What-if figures", x + w - 20f, top + 16f, 0x33FFFFFF, 0xFFFFFFFF.toInt(), alignRight = true)
        c.drawText("Tax estimate · ${s.fyLong}", x + 20f, top + 62f, fit("Tax estimate · ${s.fyLong}", text(22f, 0xFFFFFFFF.toInt(), bold = true), w - 40f))
        c.drawText("Resident individual under 60 · new vs old regime", x + 20f, top + 84f, text(11f, 0xD9FFFFFF.toInt()))
        c.drawText("Generated on $generated", x + 20f, top + 102f, text(10f, 0xB3FFFFFF.toInt()))
    }

    private fun result(s: TaxState, x: Float, w: Float): Block {
        val pad = 16f
        val tileH = 74f
        val h = pad + 20f + tileH + 14f + 18f + 16f + pad
        return Block(h) { c, top ->
            cardBg(c, x, top, w, h)
            c.drawText("RESULT", x + pad, top + pad + 10f, text(10f, INK2, bold = true).apply { letterSpacing = 0.12f })
            val tw = (w - 2 * pad - 10f) / 2
            val ty = top + pad + 20f
            val newBest = s.newIsBetter && s.saving > 0
            val oldBest = !s.newIsBetter
            tile(c, "New regime", s.newRegime.total, newBest, x + pad, ty, tw, tileH)
            tile(c, "Old regime", s.oldRegime.total, oldBest, x + pad + tw + 10f, ty, tw, tileH)
            val rec = if (s.newIsBetter) "New regime" else "Old regime"
            val line = if (s.saving == 0L) "Both regimes come to the same tax." else "Recommended: $rec · saves ${inr(s.saving)}"
            c.drawText(line, x + pad, ty + tileH + 26f, fit(line, text(14f, if (s.saving == 0L) INK2 else POS, bold = true), w - 2 * pad))
            val note = "Taxable income ${inr(s.newRegime.taxable)} (new) · ${inr(s.oldRegime.taxable)} (old)"
            c.drawText(note, x + pad, ty + tileH + 46f, fit(note, text(11f, INK2), w - 2 * pad))
        }
    }

    private fun tile(c: Canvas, label: String, tax: Long, best: Boolean, x: Float, y: Float, w: Float, h: Float) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (best) ACCENT_SOFT else SURFACE2 }
        c.drawRoundRect(RectF(x, y, x + w, y + h), 12f, 12f, bg)
        c.drawText(label, x + 12f, y + 22f, text(11f, INK2))
        if (best) pill(c, "Lower", x + w - 10f, y + 10f, POS_SOFT, POS, alignRight = true)
        c.drawText(inr(tax), x + 12f, y + 54f, fit(inr(tax), text(22f, INK, bold = true), w - 24f))
        c.drawText("incl. 4% cess", x + 12f, y + 68f, text(9f, INK2))
    }

    private fun incomeRows(s: TaxState): List<TRow> = buildList {
        val salarySub = when {
            s.whatIf.salary != null -> "What-if figure · calculated ${inr(s.projectedSalary)}"
            s.salaryMonths in 1..11 -> "Projected from ${s.salaryMonths} month${if (s.salaryMonths == 1) "" else "s"} of credits (${inr(s.salarySoFar)} so far)"
            s.salaryMonths > 0 -> "From salary credits"
            else -> "No salary credits found"
        }
        add(TRow("Salary for the year", listOf(inr(s.salaryUsed)), salarySub))
        if (s.otherIncomeUsed > 0 || s.whatIf.otherIncome != null) {
            add(TRow("Other income", listOf(inr(s.otherIncomeUsed)), if (s.whatIf.otherIncome != null) "What-if figure" else "Interest, rent and other credits"))
        }
        add(TRow("Gross income", listOf(inr(s.income)), bold = true, divider = true))
    }

    private fun deductionRows(s: TaxState): List<TRow> = buildList {
        fun tag(edited: Boolean) = if (edited) " · what-if" else ""
        add(TRow("80C investments", listOf(inr(min(s.c80, TaxMath.LIMIT_80C))), "Up to ₹1,50,000 · old regime${tag(s.whatIf.c80 != null)}"))
        add(TRow("80D health insurance", listOf(inr(min(s.d80, TaxMath.LIMIT_80D))), "Up to ₹25,000 · old regime${tag(s.whatIf.d80 != null)}"))
        if (s.nps > 0) add(TRow("NPS 80CCD(1B)", listOf(inr(min(s.nps, TaxMath.LIMIT_NPS))), "Up to ₹50,000 · old regime${tag(true)}"))
        if (s.homeLoan > 0) add(TRow("Home-loan interest 24(b)", listOf(inr(min(s.homeLoan, TaxMath.LIMIT_HOME_LOAN))), "Up to ₹2,00,000 · old regime${tag(true)}"))
        if (s.hraOther > 0) add(TRow("HRA exemption / other", listOf(inr(s.hraOther)), "Old regime${tag(true)}"))
        if (s.employerNps > 0) add(TRow("Employer NPS 80CCD(2)", listOf(inr(s.employerNps)), "Both regimes${tag(true)}"))
        add(TRow("Standard deduction", listOf("${inr(s.newRegime.standardDeduction)} / ${inr(s.oldRegime.standardDeduction)}"), "New / old regime", divider = true))
    }

    private fun sumRows(s: TaxState): List<TRow> {
        val n = s.newRegime
        val o = s.oldRegime
        return listOf(
            TRow("Gross income", listOf(inr(n.gross), inr(o.gross))),
            TRow("Standard deduction", listOf(neg(n.standardDeduction), neg(o.standardDeduction)), color = INK2),
            TRow("Deductions", listOf(neg(n.deductions), neg(o.deductions)), color = INK2),
            TRow("Taxable income", listOf(inr(n.taxable), inr(o.taxable)), bold = true, divider = true),
            TRow("Tax on slabs", listOf(inr(n.slabTax), inr(o.slabTax)), color = INK2),
            TRow("Rebate u/s 87A", listOf(neg(n.rebate), neg(o.rebate)), color = INK2),
            TRow("Health & education cess 4%", listOf(inr(n.cess), inr(o.cess)), color = INK2),
            TRow("Tax payable", listOf(inr(n.total), inr(o.total)), bold = true, divider = true),
        )
    }

    private fun slabCard(title: String, r: RegimeTax, slabs: List<Pair<Long, Int>>, x: Float, w: Float): Block {
        val lines = TaxMath.slabLines(r.taxable, slabs)
        val rows = buildList {
            if (lines.isEmpty()) add(TRow("No taxable income", listOf("", "", inr(0))))
            lines.forEach { l ->
                val range = if (l.to == null) "Above ${lakhs(l.from)}" else "${lakhs(l.from)} – ${lakhs(l.to)}"
                add(TRow(range, listOf("${l.rate}%", inr(l.taxedPart), inr(l.tax))))
            }
            add(TRow("Tax on slabs", listOf("", inr(r.taxable), inr(r.slabTax)), bold = true, divider = true))
            if (r.rebate > 0) add(TRow("Less rebate u/s 87A", listOf("", "", neg(r.rebate)), color = INK2))
            add(TRow("Tax payable with cess", listOf("", "", inr(r.total)), bold = true))
        }
        return card(title, x, w, listOf("Rate", "Income", "Tax"), rows, colW = listOf(44f, 92f, 84f))
    }

    private fun footer(x: Float, w: Float, generated: String, pdf: Boolean): Block {
        val p = text(9.5f, INK2)
        val layout = staticLayout(DISCLAIMER, p, w.toInt())
        val h = 22f + layout.height + if (pdf) 0f else 6f
        return Block(h) { c, top ->
            val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x, top, x + 14f, top + 14f, ACCENT, VIOLET, Shader.TileMode.CLAMP) }
            c.drawRoundRect(RectF(x, top + 1f, x + 12f, top + 13f), 3.5f, 3.5f, mark)
            c.drawText("Estimate generated by Hisaab on $generated", x + 18f, top + 11f, text(11f, INK, bold = true))
            c.save(); c.translate(x, top + 20f); layout.draw(c); c.restore()
        }
    }

    // -----------------------------------------------------------------------------------------
    // Drawing helpers.
    // -----------------------------------------------------------------------------------------

    private const val PAD = 16f
    private const val TITLE_H = 30f
    private const val HEAD_H = 20f
    private const val ROW_H = 24f
    private const val SUB_H = 13f

    private fun rowHeight(r: TRow) = (if (r.header) HEAD_H else ROW_H) + (if (r.sub != null) SUB_H else 0f) + (if (r.divider) 8f else 0f)

    /** A white card with a title, optional column headings and rows; [colW] are the value columns' widths, left to right. */
    private fun card(title: String, x: Float, w: Float, columns: List<String>, rows: List<TRow>, colW: List<Float>? = null): Block {
        val all = if (columns.isEmpty()) rows else listOf(TRow("", columns, header = true)) + rows
        val h = PAD + TITLE_H + all.sumOf { rowHeight(it).toDouble() }.toFloat() + PAD - 6f
        return Block(h) { c, top ->
            cardBg(c, x, top, w, h)
            c.drawText(title.uppercase(Locale.ENGLISH), x + PAD, top + PAD + 10f, text(10f, INK2, bold = true).apply { letterSpacing = 0.12f })
            val inner = w - 2 * PAD
            val n = all.maxOf { it.values.size }
            val widths = colW ?: List(n) { if (n == 1) 150f else 104f }
            var y = top + PAD + TITLE_H
            all.forEach { r ->
                if (r.divider) {
                    c.drawLine(x + PAD, y + 2f, x + w - PAD, y + 2f, Paint().apply { color = BORDER; strokeWidth = 0.8f })
                    y += 8f
                }
                val base = y + if (r.header) 12f else 15f
                val size = if (r.header) 10f else 12f
                val labelW = inner - widths.sum() - 8f
                if (r.label.isNotEmpty()) {
                    val lp = text(size, if (r.bold) INK else r.color, bold = r.bold)
                    c.drawText(TextUtils.ellipsize(r.label, lp, labelW, TextUtils.TruncateAt.END).toString(), x + PAD, base, lp)
                }
                var right = x + w - PAD
                val vals = r.values.takeLast(widths.size)
                for (i in vals.indices.reversed()) {
                    val colWidth = widths[widths.size - vals.size + i]
                    val vp = text(size, if (r.header) INK2 else INK, bold = r.bold || r.header).apply { textAlign = Paint.Align.RIGHT }
                    if (vals[i].isNotEmpty()) c.drawText(fitText(vals[i], vp, colWidth - 4f), right, base, vp)
                    right -= colWidth
                }
                if (r.sub != null) {
                    val sp = text(9.5f, INK2)
                    c.drawText(TextUtils.ellipsize(r.sub, sp, inner - (if (vals.size == 1) widths.last() else 0f), TextUtils.TruncateAt.END).toString(), x + PAD, base + SUB_H, sp)
                }
                y += (if (r.header) HEAD_H else ROW_H) + (if (r.sub != null) SUB_H else 0f)
            }
        }
    }

    private fun cardBg(c: Canvas, x: Float, top: Float, w: Float, h: Float) {
        val r = RectF(x, top, x + w, top + h)
        c.drawRoundRect(r, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD })
        c.drawRoundRect(r, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BORDER; style = Paint.Style.STROKE; strokeWidth = 1f })
    }

    private fun pill(c: Canvas, label: String, x: Float, top: Float, bg: Int, fg: Int, alignRight: Boolean) {
        val p = text(9.5f, fg, bold = true)
        val tw = p.measureText(label)
        val left = if (alignRight) x - tw - 14f else x
        c.drawRoundRect(RectF(left, top, left + tw + 14f, top + 17f), 8.5f, 8.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })
        c.drawText(label, left + 7f, top + 12f, p)
    }

    private fun text(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
    }

    /** Shrinks [p] until [s] fits in [maxW]; for big figures that would otherwise overflow a tile. */
    private fun fit(s: String, p: TextPaint, maxW: Float): TextPaint {
        while (p.measureText(s) > maxW && p.textSize > 8f) p.textSize -= 0.5f
        return p
    }

    private fun fitText(s: String, p: TextPaint, maxW: Float): String { fit(s, p, maxW); return s }

    private fun staticLayout(s: String, p: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(s, 0, s.length, p, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f, 1f).build()

    // -----------------------------------------------------------------------------------------
    // Amounts, always real (never masked).
    // -----------------------------------------------------------------------------------------

    private val rupees: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).apply {
            currency = Currency.getInstance("INR")
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }
    }

    private fun inr(minor: Long): String = synchronized(rupees) { rupees.format(minor / 100.0) }.replace("₹ ", "₹")

    private fun neg(minor: Long): String = if (minor == 0L) inr(0) else "−" + inr(minor)

    /** ₹4L, ₹2.5L, ₹0 for slab bounds. */
    private fun lakhs(minor: Long): String {
        if (minor == 0L) return "₹0"
        val l = minor / 100.0 / 100_000
        return "₹" + (if (l % 1.0 == 0.0) l.toLong().toString() else "%.1f".format(Locale.ENGLISH, l)) + "L"
    }
}
