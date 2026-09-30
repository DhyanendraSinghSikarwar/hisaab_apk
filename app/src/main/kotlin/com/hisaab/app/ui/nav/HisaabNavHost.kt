package com.hisaab.app.ui.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hisaab.app.ui.accounts.AccountsRoute
import com.hisaab.app.ui.analytics.AnalyticsRoute
import com.hisaab.app.ui.bench.ParserBenchRoute
import com.hisaab.app.ui.budgets.BudgetsRoute
import com.hisaab.app.ui.home.HomeRoute
import com.hisaab.app.ui.review.ReviewRoute
import com.hisaab.app.ui.settings.SettingsRoute
import com.hisaab.app.ui.transactions.TransactionDetailRoute
import com.hisaab.app.ui.transactions.TransactionsRoute
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Filled.Home),
    TRANSACTIONS("transactions", "Transactions", Icons.AutoMirrored.Filled.ReceiptLong),
    ANALYTICS("analytics", "Analytics", Icons.Filled.Insights),
    BUDGETS("budgets", "Budgets", Icons.Filled.Savings),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

@Composable
fun HisaabNavHost(nav: NavHostController = rememberNavController()) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route?.substringBefore('?')
    val showBar = Tab.entries.any { it.route == route }
    val haze = rememberHazeState()
    val barColor = MaterialTheme.colorScheme.surfaceContainer

    Scaffold(
        bottomBar = {
            if (showBar) {
                // The content blurs through the translucent bar.
                NavigationBar(
                    containerColor = Color.Transparent,
                    modifier = Modifier.hazeEffect(state = haze) {
                        blurRadius = 24.dp
                        tints = listOf(HazeTint(barColor.copy(alpha = 0.8f)))
                    },
                ) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("tab-${tab.route}"),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
        NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.fillMaxSize().hazeSource(haze)) {
            composable(Tab.HOME.route) {
                HomeRoute(
                    onOpenTransaction = { nav.navigate("transaction/$it") },
                    onSeeAllTransactions = { nav.navigate(Tab.TRANSACTIONS.route) },
                    onOpenAccounts = { nav.navigate("accounts") },
                    onOpenReview = { nav.navigate("review") },
                    onOpenBudgets = { nav.navigate(Tab.BUDGETS.route) },
                    contentPadding = bottom,
                )
            }
            composable(
                "transactions?accountId={accountId}&category={category}",
                arguments = listOf(
                    navArgument("accountId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("category") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { TransactionsRoute(onOpen = { nav.navigate("transaction/$it") }, contentPadding = bottom) }
            composable(Tab.ANALYTICS.route) { AnalyticsRoute(contentPadding = bottom) }
            composable(Tab.BUDGETS.route) { BudgetsRoute(contentPadding = bottom) }
            composable(Tab.SETTINGS.route) { SettingsRoute(onOpenBench = { nav.navigate("bench") }, contentPadding = bottom) }
            composable("transaction/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                TransactionDetailRoute(onBack = nav::popBackStack)
            }
            composable("accounts") { AccountsRoute(onBack = nav::popBackStack, onOpenAccount = { nav.navigate("transactions?accountId=$it") }) }
            composable("review") { ReviewRoute(onBack = nav::popBackStack, onOpen = { nav.navigate("transaction/$it") }) }
            composable("bench") { ParserBenchRoute(onBack = nav::popBackStack) }
        }
    }
}
