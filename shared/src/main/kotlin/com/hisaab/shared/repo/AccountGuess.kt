package com.hisaab.shared.repo

import com.hisaab.parser.bank.Lenders
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.ParsedTransaction
import com.hisaab.shared.db.AccountType
import com.hisaab.shared.db.CardNetwork

/** A first guess at what a new account is, from the message that created it. The user can change it. */
object AccountGuess {
    data class Guess(val type: AccountType?, val network: CardNetwork?)

    // Only when the account itself is the deposit ("RD A/c XX1234"), not "RD instalment debited from A/c XX1234".
    private val RD = Regex("""\b(?:RD|recurring\s+deposit)\s+(?:a/c|account|ac|no)\b""", RegexOption.IGNORE_CASE)
    private val FD = Regex("""\b(?:FD|fixed\s+deposit|term\s+deposit|deposit)\s+(?:a/c|account|ac|no)\b""", RegexOption.IGNORE_CASE)
    private val PPF = Regex("""\bPPF\s+(?:a/c|account|ac)\b""", RegexOption.IGNORE_CASE)
    private val LOAN = Regex("""\bloan\s+(?:a/c|account|ac)\b""", RegexOption.IGNORE_CASE)
    private val CREDIT_CARD = Regex("""\bcredit\s+card\b""", RegexOption.IGNORE_CASE)
    private val PREPAID = Regex("""\b(?:prepaid|forex)\s+card\b""", RegexOption.IGNORE_CASE)
    private val NETWORKS = listOf(
        Regex("""\brupay\b""", RegexOption.IGNORE_CASE) to CardNetwork.RUPAY,
        Regex("""\bvisa\b""", RegexOption.IGNORE_CASE) to CardNetwork.VISA,
        Regex("""\bmaster\s*card\b""", RegexOption.IGNORE_CASE) to CardNetwork.MASTERCARD,
        Regex("""\b(?:amex|american\s+express)\b""", RegexOption.IGNORE_CASE) to CardNetwork.AMEX,
        Regex("""\bdiners\b""", RegexOption.IGNORE_CASE) to CardNetwork.DINERS,
    )

    fun of(tx: ParsedTransaction, rawText: String?): Guess {
        val text = rawText.orEmpty()
        val type = when (tx.accountKind) {
            AccountKind.CARD -> when {
                tx.isDebitCard -> AccountType.DEBIT_CARD
                PREPAID.containsMatchIn(text) -> AccountType.PREPAID_CARD
                CREDIT_CARD.containsMatchIn(text) || tx.availableLimitMinor != null -> AccountType.CREDIT_CARD
                else -> null
            }
            AccountKind.ACCOUNT -> when {
                // Every account at a lender (Propelld, Aditya Birla Capital, ...) is a loan.
                Lenders.isLender(tx.bankName) -> AccountType.LOAN
                RD.containsMatchIn(text) -> AccountType.RD
                FD.containsMatchIn(text) -> AccountType.FD
                PPF.containsMatchIn(text) -> AccountType.PPF
                LOAN.containsMatchIn(text) -> AccountType.LOAN
                else -> null
            }
        }
        val network = if (tx.accountKind == AccountKind.CARD) NETWORKS.firstOrNull { it.first.containsMatchIn(text) }?.second else null
        return Guess(type, network)
    }
}
