package com.hisaab.email.mime

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

fun interface PdfTextSource {
    /** Text of the PDF, or null when it cannot be read (for example, a password-protected statement). */
    fun text(bytes: ByteArray): String?
}

@Singleton
class PdfTextExtractor @Inject constructor(@ApplicationContext private val context: Context) : PdfTextSource {
    @Volatile private var initialized = false

    override fun text(bytes: ByteArray): String? {
        if (!initialized) {
            PDFBoxResourceLoader.init(context)
            initialized = true
        }
        return try {
            PDDocument.load(bytes).use { doc -> PDFTextStripper().getText(doc) }
        } catch (_: InvalidPasswordException) {
            null
        } catch (_: java.io.IOException) {
            null
        }
    }
}
