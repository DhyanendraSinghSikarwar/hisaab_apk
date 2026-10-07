package com.hisaab.app

import com.hisaab.app.ui.more.QrCode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeTest {
    @Test fun reedSolomonMatchesTheStandardHelloWorldExample() {
        val data = intArrayOf(32, 91, 11, 120, 209, 114, 220, 77, 67, 64, 236, 17, 236, 17, 236, 17)
        assertArrayEquals(intArrayOf(196, 35, 39, 119, 235, 215, 231, 226, 93, 23), QrCode.remainder(data, 10))
    }

    @Test fun formatBitsForLevelMMaskZero() = assertEquals(0x5412, QrCode.formatBits(0))

    @Test fun shortTextIsVersionOneWithFinderPatterns() {
        val qr = QrCode.encode("HELLO")
        assertEquals(21, qr.size)
        for ((ox, oy) in listOf(0 to 0, 14 to 0, 0 to 14)) {
            assertTrue(qr.dark(ox, oy)); assertTrue(qr.dark(ox + 6, oy + 6))
            assertFalse(qr.dark(ox + 1, oy + 1)); assertTrue(qr.dark(ox + 3, oy + 3))
        }
        assertTrue(qr.dark(8, 13))
    }

    @Test fun upiLinkFits() {
        val qr = QrCode.encode("upi://pay?pa=someone@okbank&pn=DhanKosh&cu=INR&am=100")
        assertTrue(qr.size in 29..41)
    }
}
