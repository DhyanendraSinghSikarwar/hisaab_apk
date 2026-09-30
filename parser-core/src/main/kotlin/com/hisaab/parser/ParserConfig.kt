package com.hisaab.parser

import java.time.ZoneId

data class ParserConfig(
    /** Zone the bank writes its dates in. Indian banks write IST. */
    val zone: ZoneId = ZoneId.of("Asia/Kolkata"),
    /** A date in the text older than this, relative to the message, is treated as unrelated. */
    val maxBackdateDays: Long = 45,
)
