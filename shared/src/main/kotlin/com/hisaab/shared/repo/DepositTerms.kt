package com.hisaab.shared.repo

import com.hisaab.parser.bank.DepositAction
import com.hisaab.parser.bank.DepositInfo
import com.hisaab.parser.bank.DepositKind
import com.hisaab.shared.db.AccountType
import com.hisaab.shared.db.MaturityAction
import java.time.Instant
import java.time.ZoneId

/** How a deposit message's terms land on its account: the account type, the maturity day and what happens then. */
object DepositTerms {
    private val IST: ZoneId = ZoneId.of("Asia/Kolkata")

    fun typeOf(kind: DepositKind): AccountType = when (kind) {
        DepositKind.FD -> AccountType.FD
        DepositKind.RD -> AccountType.RD
        DepositKind.PPF -> AccountType.PPF
    }

    fun actionOf(a: DepositAction): MaturityAction = when (a) {
        DepositAction.PAYOUT -> MaturityAction.CREDIT
        DepositAction.RENEW_PRINCIPAL -> MaturityAction.RENEW_PRINCIPAL
        DepositAction.RENEW_ALL -> MaturityAction.RENEW_ALL
    }

    /**
     * The maturity day (epoch day) and action to save for [d], stated at [at], over the account's [currentDay] and
     * [currentAction]; null to leave them. A closed deposit matured (paid out) on its date. An older message ([newest]
     * false) only fills a maturity day that is not known yet.
     */
    fun of(d: DepositInfo, at: Long, currentDay: Long?, currentAction: MaturityAction?, newest: Boolean): Pair<Long?, MaturityAction?>? {
        if (d.closed) {
            if (!newest) return null
            val day = d.maturity?.toEpochDay() ?: Instant.ofEpochMilli(at).atZone(IST).toLocalDate().toEpochDay()
            return day to MaturityAction.CREDIT
        }
        if (d.maturity == null && d.action == null) return null
        if (!newest && currentDay != null) return null
        val day = d.maturity?.toEpochDay() ?: currentDay
        val action = d.action?.let(::actionOf) ?: currentAction
        if (day == currentDay && action == currentAction) return null
        return day to action
    }
}
