package com.hisaab.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hisaab.app.ui.home.HomeScreen
import com.hisaab.app.ui.home.HomeState
import com.hisaab.app.ui.theme.HisaabTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun asksForSmsPermissionWhenMissing() {
        var asked = false
        compose.setContent {
            HisaabTheme {
                HomeScreen(HomeState(), hasSmsPermission = false, onGrantSms = { asked = true }, onOpenTransaction = {},
                    onSeeAllTransactions = {}, onOpenAccounts = {}, onOpenReview = {}, onOpenBudgets = {}, contentPadding = PaddingValues())
            }
        }
        compose.onNodeWithTag("grant-sms").performClick()
        assertEquals(true, asked)
    }
}
