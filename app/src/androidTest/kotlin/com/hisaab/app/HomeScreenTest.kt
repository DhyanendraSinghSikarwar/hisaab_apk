package com.hisaab.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hisaab.app.ui.home.HomeScreen
import com.hisaab.app.ui.home.HomeState
import com.hisaab.app.ui.theme.HisaabTheme
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.Channel
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    private val swiggy = TransactionEntity(
        id = 1, amountMinor = 45000, currency = "INR", type = TransactionType.DEBIT, bankName = "HDFC Bank", accountLast4 = "1234",
        accountKind = AccountKind.ACCOUNT, accountId = null, merchant = "Swiggy", upiId = null, referenceNumber = "526812345678",
        channel = Channel.UPI, balanceMinor = null, availableLimitMinor = null, timestamp = System.currentTimeMillis(), hasExplicitTime = true,
        category = Category.FOOD, transactionHash = "h", confidence = 1f, createdAt = 0,
    )

    @Test
    fun showsTotalsRecentTransactionsAndReviewBanner() {
        var opened = -1L
        var reviewOpened = false
        compose.setContent {
            HisaabTheme {
                HomeScreen(
                    state = HomeState(spent = 45000, income = 500000, recent = listOf(swiggy), reviewCount = 2, loaded = true),
                    hasSmsPermission = true, onGrantSms = {}, onOpenTransaction = { opened = it }, onSeeAllTransactions = {},
                    onOpenAccounts = {}, onOpenReview = { reviewOpened = true }, onOpenBudgets = {}, contentPadding = PaddingValues(),
                )
            }
        }
        compose.onNodeWithText("₹450").assertIsDisplayed()
        compose.onNodeWithText("₹5,000").assertIsDisplayed()
        compose.onNodeWithText("2 possible duplicates to review").performClick()
        compose.onNodeWithText("Swiggy").performClick()
        assertEquals(1L, opened)
        assertEquals(true, reviewOpened)
    }

    @Test
    fun asksForSmsPermissionWhenMissing() {
        var asked = false
        compose.setContent {
            HisaabTheme {
                HomeScreen(HomeState(loaded = true), hasSmsPermission = false, onGrantSms = { asked = true }, onOpenTransaction = {},
                    onSeeAllTransactions = {}, onOpenAccounts = {}, onOpenReview = {}, onOpenBudgets = {}, contentPadding = PaddingValues())
            }
        }
        compose.onNodeWithTag("grant-sms").performClick()
        assertEquals(true, asked)
    }
}
