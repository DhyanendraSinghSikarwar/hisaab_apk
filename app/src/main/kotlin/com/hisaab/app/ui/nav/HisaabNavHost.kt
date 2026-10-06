package com.hisaab.app.ui.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.ui.unit.sp
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
import androidx.navigation.navDeepLink
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.filled.PieChart
import com.hisaab.app.ui.invest.StatementDetailRoute
import com.hisaab.app.ui.accounts.AccountsRoute
import com.hisaab.app.ui.analytics.AnalyticsRoute
import com.hisaab.app.ui.bench.ParserBenchRoute
import com.hisaab.app.ui.budgets.BudgetsRoute
import com.hisaab.app.ui.home.HomeRoute
import com.hisaab.app.ui.add.AddTransactionRoute
import com.hisaab.app.ui.invest.InvestmentsRoute
import com.hisaab.app.ui.invest.StatementsRoute
import com.hisaab.app.ui.review.CompareRoute
import com.hisaab.app.ui.review.ReviewRoute
import com.hisaab.app.ui.settings.SettingsRoute
import com.hisaab.app.ui.transactions.TransactionDetailRoute
import com.hisaab.app.ui.transactions.TransactionsRoute
import dev.chrisbanes.haze.HazeTint
import com.hisaab.app.ui.theme.appBackdrop
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** The bottom bar: icons only. Settings is reached from the profile, not from here. */
private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Filled.Home),
    TRANSACTIONS("transactions", "History", Icons.Filled.CurrencyRupee),
    INVESTMENTS("investments", "Portfolio", Icons.Filled.BusinessCenter),
    ANALYTICS("analytics", "Analytics", Icons.Filled.Insights),
}

private const val SETTINGS_ROUTE = "settings"

@Composable
private fun TabIcon(tab: Tab, tint: androidx.compose.ui.graphics.Color) {
    Icon(tab.icon, tab.label, tint = tint, modifier = Modifier.size(26.dp))
}

@Composable
fun HisaabNavHost(nav: NavHostController = rememberNavController()) {
    // Sign in first: name plus email or phone. That creates the profile.
    val welcome: com.hisaab.app.ui.onboarding.WelcomeViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val needsWelcome by welcome.needed.collectAsStateWithLifecycle()
    if (needsWelcome != false) {
        if (needsWelcome == true) com.hisaab.app.ui.onboarding.WelcomeScreen(welcome)
        return
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route?.substringBefore('?')
    val showBar = Tab.entries.any { it.route == route }
    val haze = rememberHazeState()
    val barColor = MaterialTheme.colorScheme.surfaceContainer

    Scaffold(
        bottomBar = {
            if (showBar) {
                // Icons only; the selected one sits in a pill.
                val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
                androidx.compose.foundation.layout.Row(
                    // Solid from the bar down to the screen edge: nothing scrolls visibly below or around it.
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .navigationBarsPadding()
                        .padding(horizontal = 40.dp, vertical = 10.dp)
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                        .hazeEffect(state = haze) {
                            blurRadius = 24.dp
                            tints = listOf(HazeTint(barColor))
                        }
                        .padding(6.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Tab.entries.forEach { tab ->
                        val selected = route == tab.route
                        val bg by androidx.compose.animation.animateColorAsState(
                            if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                            label = "tab-bg",
                        )
                        androidx.compose.foundation.layout.Row(
                            Modifier.testTag("tab-${tab.route}")
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                                .background(bg)
                                .clickable {
                                    if (!selected) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    if (tab == Tab.HOME) nav.goHome() else nav.openTab(tab.route)
                                }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            TabIcon(tab, if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
    ) { padding ->
        val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
        NavHost(
            nav, startDestination = Tab.HOME.route, modifier = Modifier.fillMaxSize().hazeSource(haze).appBackdrop(),
            // Tab to tab: a quick cross-fade. Into a detail screen: it slides in a little and fades, and back reverses it.
            enterTransition = {
                if (isTabSwitch()) fadeIn(tween(TAB_MS))
                else fadeIn(tween(PUSH_MS)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(PUSH_MS)) { it / 8 }
            },
            exitTransition = {
                if (isTabSwitch()) fadeOut(tween(TAB_MS))
                else fadeOut(tween(PUSH_MS / 2)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(PUSH_MS)) { it / 12 }
            },
            popEnterTransition = {
                if (isTabSwitch()) fadeIn(tween(TAB_MS))
                else fadeIn(tween(PUSH_MS)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(PUSH_MS)) { it / 12 }
            },
            popExitTransition = {
                if (isTabSwitch()) fadeOut(tween(TAB_MS))
                else fadeOut(tween(PUSH_MS / 2)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(PUSH_MS)) { it / 8 }
            },
        ) {
            composable(Tab.HOME.route) {
                HomeRoute(
                    onOpenTransaction = { nav.navigate("transaction/$it") },
                    // Switch tabs rather than push a tab onto Home's stack, or the Home tab stops responding.
                    onSeeAllTransactions = { month -> nav.openTab("${Tab.TRANSACTIONS.route}?month=$month", restore = false) },
                    onOpenAccounts = { nav.navigate("accounts") },
                    onOpenReview = { nav.navigate("review") },
                    onOpenBudgets = { nav.navigate("budgets") },
                    onAdd = { nav.navigate("add") },
                    onOpenInvestments = { nav.openTab(Tab.INVESTMENTS.route) },
                    onOpenStatements = { nav.navigate("statements") },
                    onOpenBills = { nav.navigate("bills") },
                    onOpenAnalytics = { nav.openTab(Tab.ANALYTICS.route) },
                    onOpenCategory = { c, m -> nav.navigate("category/${c.name}?month=$m") },
                    onOpenSettings = { nav.navigate(SETTINGS_ROUTE) },
                    contentPadding = bottom,
                    onOpenProfile = { nav.navigate("profile") },
                    onOpenCategoryKey = { nav.navigate("category/$it") },
                )
            }
            composable(
                "category/{key}?month={month}",
                arguments = listOf(navArgument("key") { type = NavType.StringType }, navArgument("month") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                com.hisaab.app.ui.category.CategoryDetailRoute(onBack = nav::popBackStack, onOpenTransaction = { nav.navigate("transaction/$it") })
            }
            composable(
                "transactions?accountId={accountId}&category={category}&month={month}",
                arguments = listOf(
                    navArgument("accountId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("category") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("month") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) {
                TransactionsRoute(onOpen = { nav.navigate("transaction/$it") }, onAdd = { nav.navigate("add") }, contentPadding = bottom,
                    onOpenBills = { nav.navigate("bills") })
            }
            composable(Tab.ANALYTICS.route) { AnalyticsRoute(contentPadding = bottom) }
            composable(Tab.INVESTMENTS.route) {
                InvestmentsRoute(onOpenStatements = { nav.navigate("statements") }, contentPadding = bottom,
                    onOpenAccounts = { tab -> nav.navigate("accounts?tab=$tab") })
            }
            composable("budgets", deepLinks = listOf(navDeepLink { uriPattern = "hisaab://budgets" })) {
                BudgetsRoute(
                    contentPadding = bottom, onBack = { if (!nav.popBackStack()) nav.goHome() },
                    onOpenCategory = { c -> nav.navigate("transactions?category=${c.name}&month=${java.time.YearMonth.now()}") },
                )
            }
            composable("bills", deepLinks = listOf(navDeepLink { uriPattern = "hisaab://bills" })) {
                com.hisaab.app.ui.plan.BillsRoute(onBack = { if (!nav.popBackStack()) nav.goHome() })
            }
            composable(SETTINGS_ROUTE) {
                SettingsRoute(
                    onOpenBench = { nav.navigate("bench") }, onOpenStatements = { nav.navigate("statements") },
                    onOpenInvestments = { nav.openTab(Tab.INVESTMENTS.route) }, contentPadding = PaddingValues(),
                    onOpenProfile = { nav.navigate("profile") }, onBack = nav::popBackStack,
                    onOpenCustomize = { nav.navigate("customize") },
                    onOpenForex = { nav.navigate("forex") },
                )
            }
            composable(
                "transaction/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType }),
                deepLinks = listOf(navDeepLink { uriPattern = "hisaab://transaction/{id}" }),
            ) {
                TransactionDetailRoute(onBack = nav::popBackStack)
            }
            composable("accounts?tab={tab}", arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })) { entry ->
                AccountsRoute(onBack = nav::popBackStack, onOpenAccount = { nav.navigate("account/$it") },
                    initialTab = entry.arguments?.getInt("tab") ?: 0)
            }
            composable("review") {
                ReviewRoute(onBack = nav::popBackStack, onOpen = { nav.navigate("transaction/$it") }, onCompare = { a, b -> nav.navigate("compare/$a/$b") })
            }
            composable("compare/{a}/{b}", arguments = listOf(navArgument("a") { type = NavType.LongType }, navArgument("b") { type = NavType.LongType })) {
                CompareRoute(onBack = nav::popBackStack)
            }
            composable("add") { AddTransactionRoute(onDone = nav::popBackStack) }
            composable(
                "statements?unlock={unlock}",
                arguments = listOf(navArgument("unlock") { type = NavType.LongType; defaultValue = -1L }),
                deepLinks = listOf(navDeepLink { uriPattern = "hisaab://statements?unlock={unlock}" }),
            ) { entry ->
                StatementsRoute(
                    onBack = { if (!nav.popBackStack()) nav.goHome() },
                    onOpenStatement = { nav.navigate("statement/$it") },
                    unlockId = entry.arguments?.getLong("unlock")?.takeIf { it > 0 },
                )
            }
            composable("statement/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                StatementDetailRoute(
                    onBack = nav::popBackStack, onOpenTransaction = { nav.navigate("transaction/$it") },
                    onOpenInvestments = { nav.openTab(Tab.INVESTMENTS.route) },
                )
            }
            composable("bench") { ParserBenchRoute(onBack = nav::popBackStack) }
            composable("account/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                com.hisaab.app.ui.accounts.AccountDetailRoute(onBack = nav::popBackStack, onOpenTransactions = { nav.navigate("transactions?accountId=$it") })
            }
            composable("forex") { com.hisaab.app.ui.settings.ForexRatesRoute(onBack = nav::popBackStack) }
            composable("customize") { com.hisaab.app.ui.settings.CustomizeTabsRoute(onBack = nav::popBackStack) }
            composable("profile") { com.hisaab.app.ui.profile.ProfileRoute(onBack = nav::popBackStack, onOpenSettings = { nav.navigate(SETTINGS_ROUTE) }) }
        }
    }
}

private const val TAB_MS = 180
private const val PUSH_MS = 280

private fun AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.isTabSwitch(): Boolean {
    fun base(e: androidx.navigation.NavBackStackEntry) = e.destination.route?.substringBefore('?')
    return Tab.entries.any { it.route == base(initialState) } && Tab.entries.any { it.route == base(targetState) }
}

/** Switches to a bottom-bar tab, keeping each tab's own back stack. [restore] false opens it fresh (new arguments win). */
private fun NavHostController.openTab(route: String, restore: Boolean = true) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = restore }
    launchSingleTop = true
    restoreState = restore
}

/** Home is the start destination: pop back to it, so it always responds whatever is stacked above it. */
private fun NavHostController.goHome() {
    if (!popBackStack(Tab.HOME.route, inclusive = false)) navigate(Tab.HOME.route) { launchSingleTop = true }
}
