package com.hisaab.shared.repo

import com.hisaab.parser.model.HoldingSnapshot
import com.hisaab.shared.db.HoldingDao
import com.hisaab.shared.db.HoldingEntity
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
                    dao.insert(
                        HoldingEntity(kind = s.kind, name = s.name, identifier = s.identifier, units = s.units, valueMinor = s.valueMinor,
                            investedMinor = s.investedMinor, asOf = s.asOf, source = source, note = null, updatedAt = now),
                    )
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
}
