package com.hisaab.app.ui.loans

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hisaab.shared.repo.LoanLinks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.linkStore: DataStore<Preferences> by preferencesDataStore(name = "loan_links")

/** Which loan a payment or payee was filed under by the user. On the phone only; the payment stays on its bank account. */
@Singleton
class LoanLinkStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.linkStore
    private val txKey = stringSetPreferencesKey("tx")
    private val payeeKey = stringSetPreferencesKey("payee")
    private val manualKey = stringSetPreferencesKey("manual")

    val links: Flow<LoanLinks> = store.data.map {
        LoanLinks(LoanLinks.decodeTx(it[txKey].orEmpty()), LoanLinks.decodePayee(it[payeeKey].orEmpty()), it[manualKey].orEmpty())
    }

    suspend fun current(): LoanLinks = links.first()

    /** Files [txIds] and every future payment to [payee] under [loanId]. */
    suspend fun link(txIds: Collection<Long>, payee: String?, loanId: Long) {
        store.edit { p ->
            p[txKey] = LoanLinks.encodeTx(LoanLinks.decodeTx(p[txKey].orEmpty()) + txIds.associateWith { loanId })
            if (payee != null) {
                p[payeeKey] = LoanLinks.encodePayee(LoanLinks.decodePayee(p[payeeKey].orEmpty()) + (payee to loanId))
                p[manualKey] = p[manualKey].orEmpty() + payee
            }
        }
    }

    /** The user filed a payee under EMI & Loans: it shows in Loans from its first payment. */
    suspend fun markManual(payee: String) { store.edit { it[manualKey] = it[manualKey].orEmpty() + payee } }

    /** "Not part of a loan": forgets any link for these payments and payee. */
    suspend fun unlink(txIds: Collection<Long>, payee: String?) {
        store.edit { p ->
            p[txKey] = LoanLinks.encodeTx(LoanLinks.decodeTx(p[txKey].orEmpty()) - txIds.toSet())
            if (payee != null) {
                p[payeeKey] = LoanLinks.encodePayee(LoanLinks.decodePayee(p[payeeKey].orEmpty()) - payee)
                p[manualKey] = p[manualKey].orEmpty() - payee
            }
        }
    }
}
