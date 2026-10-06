package com.hisaab.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.CreditScore
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VolunteerActivism
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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisaab.app.settings.ThemeMode
import com.hisaab.parser.model.Category

// Calm neutrals with one blue accent: warm off-white by day, near-black by night. Money in is green,
// money out red, warnings amber. Every surface is flat; cards are set apart by a hairline border.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5BEA), onPrimary = Color.White, primaryContainer = Color(0xFFDCE4FD), onPrimaryContainer = Color(0xFF0A1F66),
    secondary = Color(0xFF4A5468), onSecondary = Color.White, secondaryContainer = Color(0xFFE4E8F2), onSecondaryContainer = Color(0xFF16181D),
    tertiary = Color(0xFF13895A), onTertiary = Color.White, tertiaryContainer = Color(0xFFD3F1E3), onTertiaryContainer = Color(0xFF00391F),
    error = Color(0xFFD2453B), onError = Color.White, errorContainer = Color(0xFFFBE0DD), onErrorContainer = Color(0xFF5C0E08),
    background = Color(0xFFF7F7F5), onBackground = Color(0xFF16181D), surface = Color(0xFFF7F7F5), onSurface = Color(0xFF16181D),
    surfaceVariant = Color(0xFFF0F0EC), onSurfaceVariant = Color(0xFF5D626C), outline = Color(0xFF9A9EA6), outlineVariant = Color(0xFFE6E6E1),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFFFFF), surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F0EC), surfaceContainerHighest = Color(0xFFE9E9E4), surfaceBright = Color(0xFFFFFFFF), surfaceDim = Color(0xFFE9E9E4),
    inverseSurface = Color(0xFF16181D), inverseOnSurface = Color(0xFFF7F7F5), inversePrimary = Color(0xFF7C9BFF), surfaceTint = Color.Transparent,
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C9BFF), onPrimary = Color(0xFF0A1A4D), primaryContainer = Color(0xFF233A80), onPrimaryContainer = Color(0xFFDCE4FD),
    secondary = Color(0xFFB4BBC9), onSecondary = Color(0xFF1D222C), secondaryContainer = Color(0xFF2A2F3A), onSecondaryContainer = Color(0xFFECEDEF),
    tertiary = Color(0xFF3CCB8B), onTertiary = Color(0xFF00391F), tertiaryContainer = Color(0xFF0F4A31), onTertiaryContainer = Color(0xFFC9F3DF),
    error = Color(0xFFFF7B70), onError = Color(0xFF4A0904), errorContainer = Color(0xFF5C1A15), onErrorContainer = Color(0xFFFFDAD5),
    background = Color(0xFF0E0F12), onBackground = Color(0xFFECEDEF), surface = Color(0xFF0E0F12), onSurface = Color(0xFFECEDEF),
    surfaceVariant = Color(0xFF20232A), onSurfaceVariant = Color(0xFFA1A6B0), outline = Color(0xFF6B707A), outlineVariant = Color(0xFF2A2D35),
    surfaceContainerLowest = Color(0xFF0B0C0F), surfaceContainerLow = Color(0xFF17191E), surfaceContainer = Color(0xFF17191E),
    surfaceContainerHigh = Color(0xFF20232A), surfaceContainerHighest = Color(0xFF272A32), surfaceBright = Color(0xFF2A2D35), surfaceDim = Color(0xFF0E0F12),
    inverseSurface = Color(0xFFECEDEF), inverseOnSurface = Color(0xFF16181D), inversePrimary = Color(0xFF2F5BEA), surfaceTint = Color.Transparent,
)

@Composable
fun HisaabTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // Always Hisaab's own colours: wallpaper-based Material You would make it look like every other app.
    val colors = if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = HisaabTypography, shapes = HisaabShapes) {
        androidx.compose.runtime.CompositionLocalProvider(LocalDarkTheme provides dark, content = content)
    }
}

val LocalDarkTheme = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * Tabular figures everywhere ("tnum"): every digit is the same width, so amounts line up in columns and
 * a counting number doesn't jitter. Headings are a touch heavier and tighter, as finance apps set them.
 */
private val HisaabTypography: Typography = Typography().let { b ->
    fun androidx.compose.ui.text.TextStyle.num() = copy(fontFeatureSettings = "tnum")
    b.copy(
        displayLarge = b.displayLarge.num(), displayMedium = b.displayMedium.num(),
        displaySmall = b.displaySmall.num().copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineLarge = b.headlineLarge.num().copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        headlineMedium = b.headlineMedium.num().copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = b.headlineSmall.num().copy(fontWeight = FontWeight.SemiBold),
        titleLarge = b.titleLarge.num().copy(fontWeight = FontWeight.SemiBold),
        titleMedium = b.titleMedium.num().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = b.titleSmall.num(),
        bodyLarge = b.bodyLarge.num(), bodyMedium = b.bodyMedium.num(), bodySmall = b.bodySmall.num(),
        labelLarge = b.labelLarge.num(), labelMedium = b.labelMedium.num(), labelSmall = b.labelSmall.num(),
    )
}

/** Softer, more generous corners than the Material defaults. */
private val HisaabShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

/** Money in and out, chosen to stay readable in both themes. */
object MoneyColors {
    val credit: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFF3CCB8B) else Color(0xFF13895A)
    val debit: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFFFF7B70) else Color(0xFFD2453B)
}

/** The prototype's semantic colours: positive, negative, warning, transfer, and the soft accent wash. */
object Hx {
    val pos: Color @Composable @ReadOnlyComposable get() = MoneyColors.credit
    val neg: Color @Composable @ReadOnlyComposable get() = MoneyColors.debit
    val warn: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFFF2B84B) else Color(0xFFC98A0B)
    val transfer: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFF8B919C) else Color(0xFF7A808A)
    val accent: Color @Composable @ReadOnlyComposable get() = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val accentSoft: Color @Composable @ReadOnlyComposable get() = accent.copy(alpha = if (LocalDarkTheme.current) 0.14f else 0.10f)
    val surface: Color @Composable @ReadOnlyComposable get() = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer
    val surface2: Color @Composable @ReadOnlyComposable get() = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
    val border: Color @Composable @ReadOnlyComposable get() = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant
    val text2: Color @Composable @ReadOnlyComposable get() = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant

    /** Chart series, in order. */
    val palette = listOf(
        Color(0xFF2F5BEA), Color(0xFFE07A2E), Color(0xFF16A394), Color(0xFFC2418B),
        Color(0xFF7A5AE0), Color(0xFFC9A227), Color(0xFF3E9BD6), Color(0xFF8C8F96),
    )
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
        Category.SUBSCRIPTIONS -> Color(0xFF7E57C2)
        Category.PERSONAL_CARE -> Color(0xFFEC407A)
        Category.HOUSEHOLD -> Color(0xFFA1887F)
        Category.GIFTS -> Color(0xFFE53935)
        Category.DONATIONS -> Color(0xFFFF7043)
        Category.TAXES -> Color(0xFF546E7A)
        Category.FEES -> Color(0xFF8E7CC3)
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
        Category.EMI_LOAN -> Icons.Filled.CreditScore
        Category.INVESTMENT -> Icons.AutoMirrored.Filled.TrendingUp
        Category.CASH -> Icons.Filled.LocalAtm
        Category.SUBSCRIPTIONS -> Icons.Filled.Subscriptions
        Category.PERSONAL_CARE -> Icons.Filled.Spa
        Category.HOUSEHOLD -> Icons.Filled.Chair
        Category.GIFTS -> Icons.Filled.CardGiftcard
        Category.DONATIONS -> Icons.Filled.VolunteerActivism
        Category.TAXES -> Icons.Filled.AccountBalance
        Category.FEES -> Icons.Filled.Percent
        Category.SALARY -> Icons.Filled.Work
        Category.INCOME -> Icons.Filled.Payments
        Category.REFUND -> Icons.Filled.Undo
        Category.TRANSFER -> Icons.Filled.SwapHoriz
        Category.OTHER -> Icons.Filled.CategoryIcon
    }

@Suppress("unused")
private val keepCurrencyIcon = Icons.Filled.CurrencyExchange

/** Categories grouped for the picker. Every category is in exactly one group. */
enum class CategoryGroup(val label: String, val members: List<Category>) {
    FOOD("Food & drink", listOf(Category.FOOD, Category.GROCERIES)),
    LIFESTYLE("Shopping & lifestyle", listOf(Category.SHOPPING, Category.ENTERTAINMENT, Category.SUBSCRIPTIONS, Category.PERSONAL_CARE, Category.GIFTS, Category.DONATIONS)),
    TRAVEL("Transport & travel", listOf(Category.TRANSPORT, Category.FUEL, Category.TRAVEL)),
    HOME("Home & bills", listOf(Category.RENT, Category.BILLS, Category.HOUSEHOLD)),
    WELLBEING("Health & education", listOf(Category.HEALTH, Category.EDUCATION, Category.INSURANCE)),
    MONEY("Money", listOf(Category.EMI_LOAN, Category.INVESTMENT, Category.CASH, Category.TAXES, Category.FEES, Category.TRANSFER)),
    INCOME("Income", listOf(Category.SALARY, Category.INCOME, Category.REFUND)),
    OTHER("Other", listOf(Category.OTHER)),
}
