package com.hisaab.email.mime

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The outcome of opening a statement file: its text, a password is needed (or was wrong), a password-protected spreadsheet, or it could not be read at all. */
sealed interface PdfOpen {
    data class Text(val text: String) : PdfOpen
    data object Locked : PdfOpen
    /** An .xls/.xlsx saved with a password: these cannot be opened on the phone. */
    data object Protected : PdfOpen
    data object Unreadable : PdfOpen
}

fun interface PdfTextSource {
    /** Text of the PDF, or null when it cannot be read (for example, a password-protected statement). */
    fun text(bytes: ByteArray): String?
}

/** PdfBox on the device. Nothing is sent anywhere; passwords are used for this call only. */
@Singleton
class PdfTextExtractor @Inject constructor(@ApplicationContext private val context: Context) : PdfTextSource {
    @Volatile private var initialized = false

    override fun text(bytes: ByteArray): String? = (open(bytes, null) as? PdfOpen.Text)?.text

    fun open(bytes: ByteArray, password: String?): PdfOpen {
        if (!initialized) {
            PDFBoxResourceLoader.init(context)
            initialized = true
        }
        return try {
            val doc = if (password == null) PDDocument.load(bytes) else PDDocument.load(bytes, password)
            doc.use { d -> PdfOpen.Text(PDFTextStripper().apply { sortByPosition = true }.getText(d)) }
        } catch (_: InvalidPasswordException) {
            PdfOpen.Locked
        } catch (_: java.io.IOException) {
            PdfOpen.Unreadable
        } catch (_: RuntimeException) {
            PdfOpen.Unreadable
        }
    }
}
