package com.hisaab.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Category as CategoryIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.hisaab.app.settings.ThemeMode
import com.hisaab.parser.model.Category

private val Brand = Color(0xFF1B6E4F)
private val LightColors = lightColorScheme(
    primary = Brand, onPrimary = Color.White, primaryContainer = Color(0xFFA6F2CB), onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4C6358), tertiary = Color(0xFF3D6373), background = Color(0xFFF6FAF7), surface = Color(0xFFF6FAF7),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD6B0), onPrimary = Color(0xFF003824), primaryContainer = Color(0xFF005236), onPrimaryContainer = Color(0xFFA6F2CB),
    secondary = Color(0xFFB3CCBF), tertiary = Color(0xFFA5CCDF), background = Color(0xFF101412), surface = Color(0xFF101412),
)

@Composable
fun HisaabTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = when {
        // Material You: follow the wallpaper on Android 12+.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    val base = Typography()
    val type = base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    )
    MaterialTheme(colorScheme = colors, typography = type) {
        androidx.compose.runtime.CompositionLocalProvider(LocalDarkTheme provides dark, content = content)
    }
}

val LocalDarkTheme = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Money in and out, chosen to stay readable in both themes. */
object MoneyColors {
    val credit: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFF7FD8A4) else Color(0xFF17703D)
    val debit: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFFFFB4A9) else Color(0xFFB3261E)
}

/** One hue per category, spaced around the wheel so neighbouring pie slices stay distinct. */
val Category.color: Color
    get() = when (this) {
        Category.FOOD -> Color(0xFFE8704A)
        Category.GROCERIES -> Color(0xFF7CB342)
        Category.TRANSPORT -> Color(0xFF3F88C5)
        Category.FUEL -> Color(0xFF8D6E63)
        Category.SHOPPING -> Color(0xFFD64E8C)
        Category.BILLS -> Color(0xFF5C6BC0)
        Category.ENTERTAINMENT -> Color(0xFFAB47BC)
        Category.TRAVEL -> Color(0xFF26A69A)
        Category.HEALTH -> Color(0xFFEF5350)
        Category.EDUCATION -> Color(0xFF42A5F5)
        Category.RENT -> Color(0xFF795548)
        Category.INSURANCE -> Color(0xFF78909C)
        Category.EMI_LOAN -> Color(0xFFFFA726)
        Category.INVESTMENT -> Color(0xFF2E7D32)
        Category.CASH -> Color(0xFF9E9D24)
        Category.SALARY -> Color(0xFF00897B)
        Category.INCOME -> Color(0xFF43A047)
        Category.REFUND -> Color(0xFF29B6F6)
        Category.TRANSFER -> Color(0xFF90A4AE)
        Category.OTHER -> Color(0xFF9E9E9E)
    }

val Category.icon: ImageVector
    get() = when (this) {
        Category.FOOD -> Icons.Filled.Restaurant
        Category.GROCERIES -> Icons.Filled.LocalGroceryStore
        Category.TRANSPORT -> Icons.Filled.DirectionsCar
        Category.FUEL -> Icons.Filled.LocalGasStation
        Category.SHOPPING -> Icons.Filled.ShoppingBag
        Category.BILLS -> Icons.Filled.Receipt
        Category.ENTERTAINMENT -> Icons.Filled.Movie
        Category.TRAVEL -> Icons.Filled.Flight
        Category.HEALTH -> Icons.Filled.HealthAndSafety
        Category.EDUCATION -> Icons.Filled.School
        Category.RENT -> Icons.Filled.Home
        Category.INSURANCE -> Icons.Filled.Shield
        Category.EMI_LOAN -> Icons.Filled.AccountBalance
        Category.INVESTMENT -> Icons.AutoMirrored.Filled.TrendingUp
        Category.CASH -> Icons.Filled.LocalAtm
        Category.SALARY -> Icons.Filled.Work
        Category.INCOME -> Icons.Filled.Payments
        Category.REFUND -> Icons.Filled.Undo
        Category.TRANSFER -> Icons.Filled.SwapHoriz
        Category.OTHER -> Icons.Filled.CategoryIcon
    }

@Suppress("unused")
private val keepCurrencyIcon = Icons.Filled.CurrencyExchange
