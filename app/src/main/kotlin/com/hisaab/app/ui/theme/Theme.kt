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

// Emerald (money), sapphire (trust) and gold (wealth). Every neutral carries a faint green-blue tint rather than
// Material's default lilac, so no surface ever reads as purple.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6E52), onPrimary = Color.White, primaryContainer = Color(0xFFB4F0D6), onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF2A5D96), onSecondary = Color.White, secondaryContainer = Color(0xFFD5E4FA), onSecondaryContainer = Color(0xFF0B1D36),
    tertiary = Color(0xFF8A6A00), onTertiary = Color.White, tertiaryContainer = Color(0xFFFCE7A6), onTertiaryContainer = Color(0xFF2B2000),
    background = Color(0xFFF2F8F5), onBackground = Color(0xFF151D1A), surface = Color(0xFFF2F8F5), onSurface = Color(0xFF151D1A),
    surfaceVariant = Color(0xFFDCE6E2), onSurfaceVariant = Color(0xFF414B48), outline = Color(0xFF717C78), outlineVariant = Color(0xFFC1CBC7),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF4F9F7), surfaceContainer = Color(0xFFECF3F1),
    surfaceContainerHigh = Color(0xFFE5EEEB), surfaceContainerHighest = Color(0xFFDEE8E5), surfaceBright = Color(0xFFF8FCFA), surfaceDim = Color(0xFFD5DEDB),
    inverseSurface = Color(0xFF2A3230), inverseOnSurface = Color(0xFFEAF2EF), inversePrimary = Color(0xFF7FD9B4), surfaceTint = Color(0xFF0F6E52),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD9B4), onPrimary = Color(0xFF00382A), primaryContainer = Color(0xFF00513D), onPrimaryContainer = Color(0xFFB4F0D6),
    secondary = Color(0xFFA9C8F2), onSecondary = Color(0xFF0E2F55), secondaryContainer = Color(0xFF1F4571), onSecondaryContainer = Color(0xFFD5E4FA),
    tertiary = Color(0xFFE9C55A), onTertiary = Color(0xFF3D2F00), tertiaryContainer = Color(0xFF584500), onTertiaryContainer = Color(0xFFFCE7A6),
    background = Color(0xFF0B1714), onBackground = Color(0xFFDDE5E2), surface = Color(0xFF0B1714), onSurface = Color(0xFFDDE5E2),
    surfaceVariant = Color(0xFF3B4744), onSurfaceVariant = Color(0xFFBAC6C2), outline = Color(0xFF85918D), outlineVariant = Color(0xFF3B4744),
    surfaceContainerLowest = Color(0xFF070F0D), surfaceContainerLow = Color(0xFF121D1B), surfaceContainer = Color(0xFF16221F),
    surfaceContainerHigh = Color(0xFF1C2A27), surfaceContainerHighest = Color(0xFF243330), surfaceBright = Color(0xFF2E3B38), surfaceDim = Color(0xFF0B1714),
    inverseSurface = Color(0xFFDDE5E2), inverseOnSurface = Color(0xFF243330), inversePrimary = Color(0xFF0F6E52), surfaceTint = Color(0xFF7FD9B4),
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
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

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
