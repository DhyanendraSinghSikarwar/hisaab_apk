package com.hisaab.shared.repo

import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.model.HoldingSnapshot
import com.hisaab.parser.statement.InvestmentParser
import com.hisaab.parser.statement.MfOrder
import com.hisaab.parser.statement.NpsContribution
import com.hisaab.shared.db.HoldingDao
import com.hisaab.shared.db.HoldingEntity
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Investment holdings. A snapshot from an SMS, email or statement updates the holding it is the same investment as, unless older. */
@Singleton
class HoldingRepository @Inject constructor(private val dao: HoldingDao) {

    /**
     * Records holding values from an SMS, email or statement. A snapshot updates the holding that is the same
     * investment ([HoldingMatch]: same identifier, or the same fund or company known by another key), unless it is
     * older; the better identifier (an ISIN, an EPF member id) is kept. Duplicates left by older versions are folded
     * together, and an aggregate ("Mutual funds (INDmoney)") gives way once the class is known holding by holding.
     */
    suspend fun record(snapshots: List<HoldingSnapshot>, source: String): Int {
        var changed = 0
        val now = System.currentTimeMillis()
        // Holdings this batch already wrote: another scheme of the batch with a similar name never replaces them
        // (one statement's rows are different holdings; the same scheme in two folios arrives summed, see HoldingTable).
        val written = HashSet<Long>()
        for (s in snapshots) {
            val all = dao.observeAll().first()
            if (HoldingMatch.isAggregate(s.identifier) &&
                HoldingMatch.aggregateClass(s.identifier) in HoldingMatch.aggregatesCovered(all.map(::keyOf))
            ) continue
            val existing = all.firstOrNull { it.identifier == s.identifier } ?: match(all.filter { it.id !in written }, HoldingMatch.Key(s.kind, s.identifier, s.name))
            if (existing == null) {
                written += dao.insert(
                    HoldingEntity(kind = s.kind, name = s.name, identifier = HoldingMatch.normalisedId(s.identifier), units = s.units,
                        valueMinor = s.valueMinor, investedMinor = s.investedMinor, asOf = s.asOf,
                        source = source, note = null, updatedAt = now),
                )
                changed++
                continue
            }
            written += existing.id
            // The better identifier, unless another row already has it.
            val id = HoldingMatch.preferredId(existing.identifier, s.identifier)
                .takeIf { it == existing.identifier || all.none { h -> h.identifier == it } } ?: existing.identifier
            if ((existing.asOf ?: 0) > s.asOf) {
                if (id != existing.identifier) { dao.update(existing.copy(identifier = id, updatedAt = now)); changed++ }
                continue
            }
            // A name the user typed stays; the figures move on, and the old value is kept to measure growth.
            val newer = (existing.asOf ?: 0) < s.asOf
            val moved = s.valueMinor != null && s.valueMinor != existing.valueMinor && newer
            // An EPFO SMS states the balance and the month's contribution, not the total invested: the contribution adds to it.
            val invested = s.investedMinor
                ?: s.contributionMinor?.takeIf { newer }?.let { c -> existing.investedMinor?.plus(c) }
                ?: existing.investedMinor
            dao.update(
                existing.copy(identifier = id, units = s.units ?: existing.units, valueMinor = s.valueMinor ?: existing.valueMinor,
                    investedMinor = invested, asOf = s.asOf, source = source, updatedAt = now,
                    previousValueMinor = if (moved) existing.valueMinor else existing.previousValueMinor,
                    previousAsOf = if (moved) existing.asOf else existing.previousAsOf),
            )
            changed++
        }
        dedupe()
        return changed
    }

    /**
     * Folds together holdings that are one investment (a fund from a CAS and from a Groww table, an EPF balance with and
     * without its member id): the statement's copy is kept, else the newest, under the better identifier. Removes
     * aggregates that itemised holdings now cover. Holdings the user added are never touched. Returns rows removed.
     */
    suspend fun dedupe(): Int {
        val all = dao.observeAll().first().filter { it.source != SOURCE_MANUAL && !it.identifier.startsWith("manual:") }
        var removed = 0
        for (group in HoldingMatch.duplicates(all, ::keyOf)) {
            val keep = group.maxWith(compareBy<HoldingEntity>({ if (it.source == SOURCE_STATEMENT) 1 else 0 }, { it.asOf ?: 0L }))
            val id = group.map { it.identifier }.reduce(HoldingMatch::preferredId)
            for (h in group) if (h.id != keep.id) { dao.delete(h.id); removed++ }
            if (id != keep.identifier) dao.update(keep.copy(identifier = id, updatedAt = System.currentTimeMillis()))
        }
        val covered = HoldingMatch.aggregatesCovered(all.map(::keyOf))
        for (h in all) {
            if (HoldingMatch.isAggregate(h.identifier) && HoldingMatch.aggregateClass(h.identifier) in covered) { dao.delete(h.id); removed++ }
        }
        return removed
    }

    private fun keyOf(h: HoldingEntity) = HoldingMatch.Key(h.kind, h.identifier, h.name)

    /** The holding [k] is another copy of; the most recently valued first, holdings the user added left out. */
    private fun match(all: List<HoldingEntity>, k: HoldingMatch.Key): HoldingEntity? =
        all.filter { it.source != SOURCE_MANUAL && !it.identifier.startsWith("manual:") }
            .sortedByDescending { it.asOf ?: 0L }
            .firstOrNull { HoldingMatch.same(keyOf(it), k) }


    /**
     * A mutual fund purchase from an app's or registrar's email (SIP instalment, lump sum). Adds the units and the
     * amount invested to the fund's holding (found by ISIN-less scheme name), and values it at units × the latest NAV.
     * A statement (CAS) dated on or after the purchase already counts it, so then nothing changes.
     * Returns false when the purchase was already counted.
     */
    suspend fun recordMfOrder(order: MfOrder, at: Long): Boolean {
        val now = System.currentTimeMillis()
        val existing = dao.byIdentifier(order.identifier) ?: byFundName(order.schemeName, order.identifier)
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
        // A statement that gave the value but not the cost: one purchase is not the total invested, so it stays unknown.
        val invested = existing.investedMinor?.plus(amount) ?: order.amountMinor?.takeIf { existing.valueMinor == null || existing.source == SOURCE_EMAIL }
        dao.update(
            existing.copy(units = units, valueMinor = value, investedMinor = invested,
                asOf = maxOf(existing.asOf ?: 0L, at), source = if (existing.source == "MANUAL") existing.source else SOURCE_EMAIL, updatedAt = now,
                previousValueMinor = existing.valueMinor, previousAsOf = existing.asOf),
        )
        return true
    }

    /**
     * An NPS contribution credited to a PRAN tier. Adds it to the invested total (when that total is known from a
     * statement, or the holding is new) and, when newer than the last valuation, to the value. A Statement of
     * Transaction dated on or after it already counts it. Returns false when nothing changed.
     */
    suspend fun recordNpsContribution(c: NpsContribution, at: Long): Boolean {
        val now = System.currentTimeMillis()
        val existing = dao.byIdentifier(c.identifier)
        if (existing == null) {
            dao.insert(
                HoldingEntity(kind = HoldingKind.NPS, name = InvestmentParser.npsName(c.pranLast4, c.tier), identifier = c.identifier, units = null,
                    valueMinor = c.amountMinor, investedMinor = c.amountMinor, asOf = at, source = "SMS", note = null, updatedAt = now),
            )
            return true
        }
        if (existing.source == "STATEMENT" && (existing.asOf ?: 0) >= at) return false
        val newer = at > (existing.asOf ?: 0)
        dao.update(
            existing.copy(
                investedMinor = existing.investedMinor?.plus(c.amountMinor) ?: c.amountMinor.takeIf { existing.valueMinor == null },
                valueMinor = if (newer) (existing.valueMinor ?: 0L) + c.amountMinor else existing.valueMinor,
                asOf = if (newer) at else existing.asOf,
                source = if (newer && existing.source != "MANUAL") "SMS" else existing.source, updatedAt = now,
                previousValueMinor = if (newer) existing.valueMinor else existing.previousValueMinor,
                previousAsOf = if (newer) existing.asOf else existing.previousAsOf,
            ),
        )
        return true
    }

    /** The fund that is the same as [name] (CAS, holdings table or earlier order emails), by [HoldingMatch]. */
    private suspend fun byFundName(name: String, identifier: String): HoldingEntity? =
        match(dao.observeAll().first(), HoldingMatch.Key(HoldingKind.MUTUAL_FUND, identifier, name))

    private companion object {
        const val SOURCE_EMAIL = "EMAIL"
        const val SOURCE_MANUAL = "MANUAL"
        const val SOURCE_STATEMENT = "STATEMENT"
    }
}
