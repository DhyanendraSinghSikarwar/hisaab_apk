package com.hisaab.parser.template

import com.hisaab.parser.text.rx
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.TransactionType

/**
 * A bank-specific pattern. Named groups (amt, cur, acct, merchant, vpa, ref, bal) fill fields;
 * any field a template leaves out falls back to the generic extractors. Compiled once, at construction.
 */
class Template(
    pattern: String,
    val type: TransactionType? = null,
    val accountKind: AccountKind? = null,
) {
    private val regex = rx(pattern)
    // Matcher.group(name) throws for an unknown name, so record which groups this pattern declares.
    private val names: Set<String> = GROUP_NAME.findAll(pattern).map { it.groupValues[1] }.toSet()

    fun match(text: String): TemplateMatch? {
        val m = regex.find(text) ?: return null
        fun g(name: String): String? = if (name in names) m.groups[name]?.value?.trim()?.takeIf { it.isNotEmpty() } else null
        return TemplateMatch(
            amount = g("amt"), currency = g("cur"), account = g("acct"), merchant = g("merchant"),
            vpa = g("vpa"), reference = g("ref"), balance = g("bal"), type = type, accountKind = accountKind,
        )
    }

    companion object {
        private val GROUP_NAME = rx("""\(\?<([a-zA-Z][a-zA-Z0-9]*)>""")
        /** A number as it appears after normalization. Never swallows a trailing full stop. */
        const val AMT = """\d[\d,]*(?:\.\d{1,2})?"""
    }
}

data class TemplateMatch(
    val amount: String?,
    val currency: String?,
    val account: String?,
    val merchant: String?,
    val vpa: String?,
    val reference: String?,
    val balance: String?,
    val type: TransactionType?,
    val accountKind: AccountKind?,
)
