package com.hisaab.parser.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class TextNormalizerTest {
    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "Rs.250.00 spent|INR 250.00 spent",
            "Rs 1,23,456.00 spent|INR 1,23,456.00 spent",
            "rs.99 paid|INR 99 paid",
            "₹500 sent|INR 500 sent",
            "₹ 500 sent|INR 500 sent",
            "INR5,000.00 credited|INR 5,000.00 credited",
            "INR. 10 debited|INR 10 debited",
            "AvlBal:Rs12090.50|AvlBal:INR 12090.50",
            "Mrs 500 is not a currency|Mrs 500 is not a currency",
        ],
    )
    fun `every rupee spelling becomes INR`(raw: String, expected: String) {
        assertEquals(expected, TextNormalizer.normalize(raw))
    }

    @Test
    fun `zero-width characters and odd spaces are removed`() {
        assertEquals("INR 250.00 debited from A/c XX1234", TextNormalizer.normalize("Rs.2​50.00 debited‍ from A/c XX1234﻿"))
    }

    @Test
    fun `line breaks become sentence ends unless punctuation already ends the line`() {
        assertEquals("Credit Alert! INR 5.00 credited. A/c XX1234. On 25/09/26", TextNormalizer.normalize("Credit Alert!\nRs.5.00 credited\r\nA/c XX1234\n\n  On 25/09/26"))
    }

    @Test
    fun `whitespace is collapsed and trimmed`() {
        assertEquals("a b c", TextNormalizer.normalize("   a    b \t c  "))
    }

    @Test
    fun `email footer and disclaimers are cut`() {
        val body = TextNormalizer.normalize(
            "Dear Customer, INR 450.00 has been debited from account **1234 to VPA x@ybl on 25-09-26. " +
                "If you did not authorize this transaction, call us. Never share your OTP. Get pre-approved loans! Apply now.",
        )
        val trimmed = TextNormalizer.trimEmail(body)
        assertTrue(trimmed.endsWith("on 25-09-26."), trimmed)
    }

    @Test
    fun `a footer marker at the very start does not erase the body`() {
        val body = "Regards, INR 10.00 has been debited from account XX1234 on 25-09-26 towards SWIGGY"
        assertEquals(body, TextNormalizer.trimEmail(body))
    }
}
