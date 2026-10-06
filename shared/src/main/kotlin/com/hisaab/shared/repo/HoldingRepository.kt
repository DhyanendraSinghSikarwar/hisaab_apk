package com.hisaab.shared.repo

import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.model.HoldingSnapshot
import com.hisaab.parser.statement.MfOrder
import com.hisaab.parser.statement.MfOrderParser
import com.hisaab.shared.db.HoldingDao
import com.hisaab.shared.db.HoldingEntity
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Investment holdings. A snapshot from an SMS or statement updates the holding with the same identifier, unless it is older. */
@Singleton
class HoldingRepository @Inject constructor(private val dao: HoldingDao) {

    suspend fun record(snapshots: List<HoldingSnapshot>, source: String): Int {
        var changed = 0
        val now = System.currentTimeMillis()
        for (s in snapshots) {
            val existing = dao.byIdentifier(s.identifier)
            when {
                existing == null -> {
                    // A fund first seen in SIP emails ("MF:<name>") becomes the statement's ISIN holding: one row, not two.
                    val fromEmails = if (s.kind == HoldingKind.MUTUAL_FUND) byFundName(s.name)?.takeIf { it.identifier.startsWith(MF_PREFIX) } else null
                    if (fromEmails != null) {
                        val newer = (fromEmails.asOf ?: 0) <= s.asOf
                        dao.update(
                            if (newer) {
                                fromEmails.copy(identifier = s.identifier, units = s.units ?: fromEmails.units, valueMinor = s.valueMinor ?: fromEmails.valueMinor,
                                    investedMinor = s.investedMinor ?: fromEmails.investedMinor, asOf = s.asOf, source = source, updatedAt = now,
                                    previousValueMinor = fromEmails.valueMinor, previousAsOf = fromEmails.asOf)
                            } else {
                                fromEmails.copy(identifier = s.identifier, updatedAt = now)
                            },
                        )
                    } else {
                        dao.insert(
                            HoldingEntity(kind = s.kind, name = s.name, identifier = s.identifier, units = s.units, valueMinor = s.valueMinor,
                                investedMinor = s.investedMinor, asOf = s.asOf, source = source, note = null, updatedAt = now),
                        )
                    }
                    changed++
                }
                (existing.asOf ?: 0) <= s.asOf -> {
                    // A name the user typed stays; the figures move on, and the old value is kept to measure growth.
                    val moved = s.valueMinor != null && s.valueMinor != existing.valueMinor && (existing.asOf ?: 0) < s.asOf
                    dao.update(
                        existing.copy(units = s.units ?: existing.units, valueMinor = s.valueMinor ?: existing.valueMinor,
                            investedMinor = s.investedMinor ?: existing.investedMinor, asOf = s.asOf, source = source, updatedAt = now,
                            previousValueMinor = if (moved) existing.valueMinor else existing.previousValueMinor,
                            previousAsOf = if (moved) existing.asOf else existing.previousAsOf),
                    )
                    changed++
                }
            }
        }
        return changed
    }

    /**
     * A mutual fund purchase from an app's or registrar's email (SIP instalment, lump sum). Adds the units and the
     * amount invested to the fund's holding (found by ISIN-less scheme name), and values it at units × the latest NAV.
     * A statement (CAS) dated on or after the purchase already counts it, so then nothing changes.
     * Returns false when the purchase was already counted.
     */
    suspend fun recordMfOrder(order: MfOrder, at: Long): Boolean {
        val now = System.currentTimeMillis()
        val existing = dao.byIdentifier(order.identifier) ?: byFundName(order.schemeName)
        val amount = order.amountMinor ?: 0L
        val orderUnits = order.units
        val nav = order.nav
        if (existing == null) {
            val value = if (orderUnits != null && nav != null) Math.round(orderUnits * nav * 100) else order.amountMinor
            dao.insert(
                HoldingEntity(kind = HoldingKind.MUTUAL_FUND, name = order.schemeName, identifier = order.identifier, units = order.units,
                    valueMinor = value, investedMinor = order.amountMinor, asOf = at, source = SOURCE_EMAIL,
                    note = listOfNotNull(order.platform, order.folio?.let { "folio $it" }).joinToString(" · "), updatedAt = now),
            )
            return true
        }
        if (existing.source == "STATEMENT" && (existing.asOf ?: 0) >= at) return false
        val units = if (orderUnits != null) (existing.units ?: 0.0) + orderUnits else existing.units
        val value = when {
            units != null && nav != null && (existing.units != null || existing.valueMinor == null) -> Math.round(units * nav * 100)
            else -> (existing.valueMinor ?: 0L) + amount
        }
        dao.update(
            existing.copy(units = units, valueMinor = value, investedMinor = (existing.investedMinor ?: 0L) + amount,
                asOf = maxOf(existing.asOf ?: 0L, at), source = if (existing.source == "MANUAL") existing.source else SOURCE_EMAIL, updatedAt = now,
                previousValueMinor = existing.valueMinor, previousAsOf = existing.asOf),
        )
        return true
    }

    /** The mutual fund whose name is the same as [name] once "Direct", "Growth", "Plan" and punctuation are set aside. */
    private suspend fun byFundName(name: String): HoldingEntity? {
        val key = MfOrderParser.normaliseScheme(name)
        if (key.isEmpty()) return null
        return dao.observeAll().first().firstOrNull { it.kind == HoldingKind.MUTUAL_FUND && MfOrderParser.normaliseScheme(it.name) == key }
    }

    private companion object {
        const val MF_PREFIX = "MF:"
        const val SOURCE_EMAIL = "EMAIL"
    }
}
