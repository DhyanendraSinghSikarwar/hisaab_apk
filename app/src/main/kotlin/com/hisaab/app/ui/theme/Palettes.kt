package com.hisaab.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.hisaab.app.settings.ThemePalette

// Every palette is calm neutrals with one accent. Money in stays green, money out red and warnings amber on
// every palette (see [Hx]), so the accent never carries meaning. Surfaces are flat; cards are set apart by a
// hairline border. Accents are deep enough for white text by day and light enough for dark text by night.

private val White = Color.White

/** A light scheme from a palette's few tokens. Cards sit on pure white; errors are the shared red. */
private fun lightScheme(
    primary: Color, primaryContainer: Color, onPrimaryContainer: Color,
    secondary: Color, secondaryContainer: Color, onSecondaryContainer: Color,
    tertiary: Color, tertiaryContainer: Color, onTertiaryContainer: Color,
    background: Color, ink: Color, muted: Color, outline: Color, outlineVariant: Color,
    high: Color, highest: Color, inversePrimary: Color,
): ColorScheme = lightColorScheme(
    primary = primary, onPrimary = White, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
    secondary = secondary, onSecondary = White, secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary, onTertiary = White, tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
    error = Color(0xFFD2453B), onError = White, errorContainer = Color(0xFFFBE0DD), onErrorContainer = Color(0xFF5C0E08),
    background = background, onBackground = ink, surface = background, onSurface = ink,
    surfaceVariant = high, onSurfaceVariant = muted, outline = outline, outlineVariant = outlineVariant,
    surfaceContainerLowest = White, surfaceContainerLow = White, surfaceContainer = White,
    surfaceContainerHigh = high, surfaceContainerHighest = highest, surfaceBright = White, surfaceDim = highest,
    inverseSurface = ink, inverseOnSurface = background, inversePrimary = inversePrimary, surfaceTint = Color.Transparent,
)

/** A dark scheme from a palette's few tokens: a near-black base, cards a step lighter. */
private fun darkScheme(
    primary: Color, onPrimary: Color, primaryContainer: Color, onPrimaryContainer: Color,
    secondary: Color, onSecondary: Color, secondaryContainer: Color, onSecondaryContainer: Color,
    tertiary: Color, onTertiary: Color, tertiaryContainer: Color, onTertiaryContainer: Color,
    background: Color, ink: Color, muted: Color, outline: Color, outlineVariant: Color,
    lowest: Color, container: Color, high: Color, highest: Color, bright: Color, inversePrimary: Color,
): ColorScheme = darkColorScheme(
    primary = primary, onPrimary = onPrimary, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
    secondary = secondary, onSecondary = onSecondary, secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary, onTertiary = onTertiary, tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
    error = Color(0xFFFF7B70), onError = Color(0xFF4A0904), errorContainer = Color(0xFF5C1A15), onErrorContainer = Color(0xFFFFDAD5),
    background = background, onBackground = ink, surface = background, onSurface = ink,
    surfaceVariant = high, onSurfaceVariant = muted, outline = outline, outlineVariant = outlineVariant,
    surfaceContainerLowest = lowest, surfaceContainerLow = container, surfaceContainer = container,
    surfaceContainerHigh = high, surfaceContainerHighest = highest, surfaceBright = bright, surfaceDim = background,
    inverseSurface = ink, inverseOnSurface = background, inversePrimary = inversePrimary, surfaceTint = Color.Transparent,
)

/**
 * Everything a palette paints: both colour schemes, the hero-card gradient (by day and by night), the three
 * soft backdrop glows, and the colour the backdrop fades to at the bottom.
 */
@Immutable
class PaletteSpec(
    val light: ColorScheme,
    val dark: ColorScheme,
    val heroLight: List<Color>,
    val heroDark: List<Color>,
    val glows: List<Color>,
    val bottomLight: Color,
    val bottomDark: Color,
)

private val Classic = PaletteSpec(
    // Blue on warm neutrals: the original look.
    light = lightScheme(
        Color(0xFF2F5BEA), Color(0xFFDCE4FD), Color(0xFF0A1F66),
        Color(0xFF4A5468), Color(0xFFE4E8F2), Color(0xFF16181D),
        Color(0xFF13895A), Color(0xFFD3F1E3), Color(0xFF00391F),
        background = Color(0xFFF7F7F5), ink = Color(0xFF16181D), muted = Color(0xFF5D626C), outline = Color(0xFF9A9EA6), outlineVariant = Color(0xFFE6E6E1),
        high = Color(0xFFF0F0EC), highest = Color(0xFFE9E9E4), inversePrimary = Color(0xFF7C9BFF),
    ),
    dark = darkScheme(
        Color(0xFF7C9BFF), Color(0xFF0A1A4D), Color(0xFF233A80), Color(0xFFDCE4FD),
        Color(0xFFB4BBC9), Color(0xFF1D222C), Color(0xFF2A2F3A), Color(0xFFECEDEF),
        Color(0xFF3CCB8B), Color(0xFF00391F), Color(0xFF0F4A31), Color(0xFFC9F3DF),
        background = Color(0xFF0E0F12), ink = Color(0xFFECEDEF), muted = Color(0xFFA1A6B0), outline = Color(0xFF6B707A), outlineVariant = Color(0xFF2A2D35),
        lowest = Color(0xFF0B0C0F), container = Color(0xFF17191E), high = Color(0xFF20232A), highest = Color(0xFF272A32), bright = Color(0xFF2A2D35),
        inversePrimary = Color(0xFF2F5BEA),
    ),
    heroLight = listOf(Color(0xFF2F5BEA), Color(0xFF5A48D6), Color(0xFF7A5AE0)),
    heroDark = listOf(Color(0xFF2B4BC4), Color(0xFF5B3FB8), Color(0xFF3A2A86)),
    glows = listOf(Color(0xFF2F5BEA), Color(0xFF7A5AE0), Color(0xFF16A394)),
    bottomLight = Color(0xFFF2F3F7), bottomDark = Color(0xFF0B0D13),
)

private val Emerald = PaletteSpec(
    // Deep green on mint-tinted neutrals; a quiet blue tertiary so it never reads as "money in".
    light = lightScheme(
        Color(0xFF0F7A55), Color(0xFFCFEFE0), Color(0xFF00311F),
        Color(0xFF4A5E55), Color(0xFFE0EBE5), Color(0xFF13201A),
        Color(0xFF2F6FB0), Color(0xFFD6E6F7), Color(0xFF0B2A4A),
        background = Color(0xFFF4F8F5), ink = Color(0xFF121A16), muted = Color(0xFF56635C), outline = Color(0xFF8F9C95), outlineVariant = Color(0xFFDCE6E0),
        high = Color(0xFFEAF1EC), highest = Color(0xFFE1EAE4), inversePrimary = Color(0xFF5FD3A2),
    ),
    dark = darkScheme(
        Color(0xFF5FD3A2), Color(0xFF003826), Color(0xFF0E4F38), Color(0xFFC9F2DF),
        Color(0xFFB0C4BA), Color(0xFF1B2A23), Color(0xFF24332C), Color(0xFFE3EEE8),
        Color(0xFF8EC0F2), Color(0xFF0B2A4A), Color(0xFF1D4570), Color(0xFFD6E6F7),
        background = Color(0xFF0B110E), ink = Color(0xFFE6EEEA), muted = Color(0xFF9DAAA3), outline = Color(0xFF66736C), outlineVariant = Color(0xFF233029),
        lowest = Color(0xFF080C0A), container = Color(0xFF131B17), high = Color(0xFF1B2520), highest = Color(0xFF222D27), bright = Color(0xFF26322C),
        inversePrimary = Color(0xFF0F7A55),
    ),
    heroLight = listOf(Color(0xFF0B6E4F), Color(0xFF0F8A6A), Color(0xFF0E6E7A)),
    heroDark = listOf(Color(0xFF0A5A40), Color(0xFF0C6655), Color(0xFF0A4A55)),
    glows = listOf(Color(0xFF10A36F), Color(0xFF0E8A8A), Color(0xFF3B82C4)),
    bottomLight = Color(0xFFEDF4F0), bottomDark = Color(0xFF08100C),
)

private val Graphite = PaletteSpec(
    // Monochrome slate with a steel-blue accent: the most restrained palette.
    light = lightScheme(
        Color(0xFF3D5A80), Color(0xFFDCE4EF), Color(0xFF0F1E33),
        Color(0xFF4B5563), Color(0xFFE5E7EB), Color(0xFF111827),
        Color(0xFF0E7490), Color(0xFFCFF0F7), Color(0xFF053542),
        background = Color(0xFFF5F6F7), ink = Color(0xFF111418), muted = Color(0xFF5B6270), outline = Color(0xFF9AA0AA), outlineVariant = Color(0xFFE2E4E8),
        high = Color(0xFFECEEF1), highest = Color(0xFFE3E6EA), inversePrimary = Color(0xFF9DB6D8),
    ),
    dark = darkScheme(
        Color(0xFF9DB6D8), Color(0xFF102238), Color(0xFF2C4361), Color(0xFFDCE4EF),
        Color(0xFFB6BDC8), Color(0xFF1D2128), Color(0xFF2A2F37), Color(0xFFECEEF1),
        Color(0xFF67C6DE), Color(0xFF053542), Color(0xFF0F4C5C), Color(0xFFCFF0F7),
        background = Color(0xFF0F1113), ink = Color(0xFFECEEF0), muted = Color(0xFFA3A9B2), outline = Color(0xFF6C727B), outlineVariant = Color(0xFF2A2E34),
        lowest = Color(0xFF0B0C0E), container = Color(0xFF181B1F), high = Color(0xFF212429), highest = Color(0xFF282C32), bright = Color(0xFF2C3036),
        inversePrimary = Color(0xFF3D5A80),
    ),
    heroLight = listOf(Color(0xFF2B3440), Color(0xFF3D5A80), Color(0xFF4A6587)),
    heroDark = listOf(Color(0xFF1F2730), Color(0xFF2C4361), Color(0xFF33475F)),
    glows = listOf(Color(0xFF3D5A80), Color(0xFF64748B), Color(0xFF0E7490)),
    bottomLight = Color(0xFFEEF0F2), bottomDark = Color(0xFF0B0D0F),
)

private val Indigo = PaletteSpec(
    // Indigo into violet on lavender-grey neutrals; at night an ink-blue base.
    light = lightScheme(
        Color(0xFF4F46E5), Color(0xFFE2E0FC), Color(0xFF1A1466),
        Color(0xFF5B5876), Color(0xFFE8E6F3), Color(0xFF1A1830),
        Color(0xFF7C3AED), Color(0xFFEDE3FD), Color(0xFF2E0F66),
        background = Color(0xFFF6F5FB), ink = Color(0xFF17162A), muted = Color(0xFF5E5C72), outline = Color(0xFF9C9AB0), outlineVariant = Color(0xFFE4E2EF),
        high = Color(0xFFEEEDF6), highest = Color(0xFFE5E3F0), inversePrimary = Color(0xFFA5A1FF),
    ),
    dark = darkScheme(
        Color(0xFFA5A1FF), Color(0xFF1A1466), Color(0xFF3A33A8), Color(0xFFE2E0FC),
        Color(0xFFBDB9D6), Color(0xFF24223A), Color(0xFF2D2B45), Color(0xFFECEAF7),
        Color(0xFFC9A3FF), Color(0xFF2E0F66), Color(0xFF4B2596), Color(0xFFEDE3FD),
        background = Color(0xFF0D0C17), ink = Color(0xFFECEBF5), muted = Color(0xFFA4A2BA), outline = Color(0xFF6E6C85), outlineVariant = Color(0xFF2A2840),
        lowest = Color(0xFF09080F), container = Color(0xFF16152A), high = Color(0xFF1F1D35), highest = Color(0xFF26243D), bright = Color(0xFF2B2944),
        inversePrimary = Color(0xFF4F46E5),
    ),
    heroLight = listOf(Color(0xFF4F46E5), Color(0xFF6D3FD9), Color(0xFF8B3FD0)),
    heroDark = listOf(Color(0xFF3730A3), Color(0xFF5126A8), Color(0xFF3B1D80)),
    glows = listOf(Color(0xFF4F46E5), Color(0xFF9333EA), Color(0xFFDB2777)),
    bottomLight = Color(0xFFF1F0F8), bottomDark = Color(0xFF0A0914),
)

private val Saffron = PaletteSpec(
    // Burnt amber with a terracotta tertiary on warm paper neutrals; deep enough for white text.
    light = lightScheme(
        Color(0xFFB45309), Color(0xFFFDE7CF), Color(0xFF4A2103),
        Color(0xFF6B5B4E), Color(0xFFF1E8DF), Color(0xFF2A1F16),
        Color(0xFFA8432A), Color(0xFFFADDD3), Color(0xFF44130A),
        background = Color(0xFFFAF6F0), ink = Color(0xFF1F1A15), muted = Color(0xFF6A6058), outline = Color(0xFFA39A90), outlineVariant = Color(0xFFEBE3D8),
        high = Color(0xFFF3ECE2), highest = Color(0xFFEBE3D7), inversePrimary = Color(0xFFF5B26B),
    ),
    dark = darkScheme(
        Color(0xFFF5B26B), Color(0xFF4A2103), Color(0xFF6E3A0E), Color(0xFFFDE7CF),
        Color(0xFFD3C3B4), Color(0xFF2E241B), Color(0xFF3A2F25), Color(0xFFF3EAE1),
        Color(0xFFF0A08A), Color(0xFF44130A), Color(0xFF6E2A18), Color(0xFFFADDD3),
        background = Color(0xFF13100D), ink = Color(0xFFF1ECE6), muted = Color(0xFFB0A69C), outline = Color(0xFF7A7067), outlineVariant = Color(0xFF322A23),
        lowest = Color(0xFF0E0B09), container = Color(0xFF1C1814), high = Color(0xFF26201B), highest = Color(0xFF2D2620), bright = Color(0xFF332B24),
        inversePrimary = Color(0xFFB45309),
    ),
    heroLight = listOf(Color(0xFFB45309), Color(0xFFC2410C), Color(0xFF9A3412)),
    heroDark = listOf(Color(0xFF8A4108), Color(0xFF94360E), Color(0xFF6E2A12)),
    glows = listOf(Color(0xFFF59E0B), Color(0xFFC2410C), Color(0xFFA8432A)),
    bottomLight = Color(0xFFF6F0E7), bottomDark = Color(0xFF0F0C0A),
)

private val Ocean = PaletteSpec(
    // Teal-cyan with a clear blue tertiary on sea-glass neutrals.
    light = lightScheme(
        Color(0xFF0E7490), Color(0xFFCCEFF6), Color(0xFF03323F),
        Color(0xFF4A5F66), Color(0xFFE0EBEE), Color(0xFF102027),
        Color(0xFF2563EB), Color(0xFFDBE6FD), Color(0xFF0B2566),
        background = Color(0xFFF3F8F9), ink = Color(0xFF0F1A1D), muted = Color(0xFF55666B), outline = Color(0xFF8FA1A6), outlineVariant = Color(0xFFDAE6E9),
        high = Color(0xFFE9F1F3), highest = Color(0xFFE0EAED), inversePrimary = Color(0xFF5ED0E6),
    ),
    dark = darkScheme(
        Color(0xFF5ED0E6), Color(0xFF00363F), Color(0xFF0C4E5E), Color(0xFFCCEFF6),
        Color(0xFFAFC4CA), Color(0xFF1A2A2F), Color(0xFF233439), Color(0xFFE2EEF1),
        Color(0xFF93B4FF), Color(0xFF0B2566), Color(0xFF1E3D8F), Color(0xFFDBE6FD),
        background = Color(0xFF0A1215), ink = Color(0xFFE5EFF1), muted = Color(0xFF9BB0B5), outline = Color(0xFF627579), outlineVariant = Color(0xFF1F2E33),
        lowest = Color(0xFF070D0F), container = Color(0xFF121C20), high = Color(0xFF1A262B), highest = Color(0xFF213035), bright = Color(0xFF25353A),
        inversePrimary = Color(0xFF0E7490),
    ),
    heroLight = listOf(Color(0xFF0E7490), Color(0xFF0891B2), Color(0xFF1D6FB8)),
    heroDark = listOf(Color(0xFF0A5568), Color(0xFF0A6A80), Color(0xFF1A4F86)),
    glows = listOf(Color(0xFF0891B2), Color(0xFF2563EB), Color(0xFF14B8A6)),
    bottomLight = Color(0xFFEBF4F6), bottomDark = Color(0xFF071013),
)

private val RoseQuartz = PaletteSpec(
    // Dusty rose and mauve on warm blush neutrals; deep plum at night with a rose accent.
    light = lightScheme(
        Color(0xFFA8466A), Color(0xFFFBDDE7), Color(0xFF3E0A20),
        Color(0xFF7A5C6E), Color(0xFFF1E2EA), Color(0xFF2C1824),
        Color(0xFF8A5A7A), Color(0xFFF5DDEE), Color(0xFF351029),
        background = Color(0xFFFBF6F6), ink = Color(0xFF211719), muted = Color(0xFF6E5D62), outline = Color(0xFFA9999E), outlineVariant = Color(0xFFEFE3E5),
        high = Color(0xFFF6EDEE), highest = Color(0xFFEEE3E5), inversePrimary = Color(0xFFF4A3BF),
    ),
    dark = darkScheme(
        Color(0xFFF4A3BF), Color(0xFF4A0F28), Color(0xFF6E2442), Color(0xFFFBDDE7),
        Color(0xFFD9BFCD), Color(0xFF33202B), Color(0xFF402B37), Color(0xFFF1E2EA),
        Color(0xFFE2B3D6), Color(0xFF3A1431), Color(0xFF5C2A50), Color(0xFFF5DDEE),
        background = Color(0xFF140C12), ink = Color(0xFFF2EAEE), muted = Color(0xFFB6A3AD), outline = Color(0xFF7E6C76), outlineVariant = Color(0xFF33242E),
        lowest = Color(0xFF0F090D), container = Color(0xFF1D131A), high = Color(0xFF271B23), highest = Color(0xFF2F222B), bright = Color(0xFF362832),
        inversePrimary = Color(0xFFA8466A),
    ),
    heroLight = listOf(Color(0xFF8E3A5C), Color(0xFFA8466A), Color(0xFF8A5A8E)),
    heroDark = listOf(Color(0xFF5E2240), Color(0xFF7A2E52), Color(0xFF4E2A5E)),
    glows = listOf(Color(0xFFE07A9E), Color(0xFFA87AB8), Color(0xFFC98A9A)),
    bottomLight = Color(0xFFF7EEEF), bottomDark = Color(0xFF0F090D),
)

private val LavenderBloom = PaletteSpec(
    // Soft lilac and violet on pearl neutrals; aubergine at night with a lilac accent.
    light = lightScheme(
        Color(0xFF6D4FC2), Color(0xFFE8E0FA), Color(0xFF22104F),
        Color(0xFF6E6680), Color(0xFFECE8F4), Color(0xFF211D2C),
        Color(0xFF9A5BB5), Color(0xFFF3E0FA), Color(0xFF3A0F4A),
        background = Color(0xFFF8F6FB), ink = Color(0xFF1A1722), muted = Color(0xFF645F72), outline = Color(0xFFA19CAD), outlineVariant = Color(0xFFE8E4F0),
        high = Color(0xFFF1EEF6), highest = Color(0xFFE9E5F0), inversePrimary = Color(0xFFCDB8FF),
    ),
    dark = darkScheme(
        Color(0xFFCDB8FF), Color(0xFF2E1366), Color(0xFF4A2E94), Color(0xFFE8E0FA),
        Color(0xFFCFC6DE), Color(0xFF2A2338), Color(0xFF372F46), Color(0xFFECE8F4),
        Color(0xFFE6B3F5), Color(0xFF3A0F4A), Color(0xFF5E2A73), Color(0xFFF3E0FA),
        background = Color(0xFF120C17), ink = Color(0xFFEEEAF4), muted = Color(0xFFABA3B8), outline = Color(0xFF756D82), outlineVariant = Color(0xFF2E2638),
        lowest = Color(0xFF0D0911), container = Color(0xFF1A1321), high = Color(0xFF231B2B), highest = Color(0xFF2A2133), bright = Color(0xFF31283B),
        inversePrimary = Color(0xFF6D4FC2),
    ),
    heroLight = listOf(Color(0xFF5A3FA8), Color(0xFF7A55C8), Color(0xFFA066C0)),
    heroDark = listOf(Color(0xFF3A2470), Color(0xFF523391), Color(0xFF6A3A85)),
    glows = listOf(Color(0xFF9B7BE8), Color(0xFFD08AE0), Color(0xFF6D4FC2)),
    bottomLight = Color(0xFFF2EFF7), bottomDark = Color(0xFF0D0911),
)

// Hero palettes: bolder accents on the same calm structure. Names stay neutral; money colours stay shared.

private val Midnight = PaletteSpec(
    // Graphite on cool grey by day, near-black by night; a signal-amber accent kept to a few touches.
    light = lightScheme(
        Color(0xFF26292E), Color(0xFFE4E6EA), Color(0xFF121417),
        Color(0xFF50555E), Color(0xFFE6E8EC), Color(0xFF15171A),
        Color(0xFF8A6200), Color(0xFFFFF0C2), Color(0xFF3A2800),
        background = Color(0xFFF2F3F5), ink = Color(0xFF121417), muted = Color(0xFF5A5F68), outline = Color(0xFF989DA6), outlineVariant = Color(0xFFDFE2E6),
        high = Color(0xFFEAECEF), highest = Color(0xFFE1E4E8), inversePrimary = Color(0xFFF5C518),
    ),
    dark = darkScheme(
        Color(0xFFF5C518), Color(0xFF241A00), Color(0xFF2E2608), Color(0xFFFFE7A0),
        Color(0xFFB9BDC4), Color(0xFF1A1C20), Color(0xFF26282C), Color(0xFFE8E9EC),
        Color(0xFF9AA3AF), Color(0xFF14181D), Color(0xFF2A3038), Color(0xFFDDE3EA),
        background = Color(0xFF000000), ink = Color(0xFFEDEDEF), muted = Color(0xFF9C9FA6), outline = Color(0xFF66696F), outlineVariant = Color(0xFF1F2023),
        lowest = Color(0xFF000000), container = Color(0xFF0A0A0B), high = Color(0xFF141416), highest = Color(0xFF1B1B1E), bright = Color(0xFF222226),
        inversePrimary = Color(0xFF8A6200),
    ),
    heroLight = listOf(Color(0xFF1A1C20), Color(0xFF2B2E34), Color(0xFF3D3624)),
    heroDark = listOf(Color(0xFF0E0F11), Color(0xFF1C1D21), Color(0xFF2E2710)),
    glows = listOf(Color(0xFFF5C518), Color(0xFF4B5563), Color(0xFF9CA3AF)),
    bottomLight = Color(0xFFECEEF1), bottomDark = Color(0xFF000000),
)

private val ArcRed = PaletteSpec(
    // Deep crimson with a gold tertiary on warm off-white; a dark red-black base at night.
    light = lightScheme(
        Color(0xFFB3122E), Color(0xFFFCDDE1), Color(0xFF4A0010),
        Color(0xFF6E5558), Color(0xFFF2E4E5), Color(0xFF2A1A1C),
        Color(0xFF8A6500), Color(0xFFFBEBC0), Color(0xFF3A2A00),
        background = Color(0xFFFAF6F5), ink = Color(0xFF1E1617), muted = Color(0xFF6A5D5F), outline = Color(0xFFA69A9C), outlineVariant = Color(0xFFEDE3E3),
        high = Color(0xFFF4EDEC), highest = Color(0xFFECE3E2), inversePrimary = Color(0xFFFF8A98),
    ),
    dark = darkScheme(
        Color(0xFFFF8A98), Color(0xFF4A0010), Color(0xFF6E0A20), Color(0xFFFFDADF),
        Color(0xFFD6C2C4), Color(0xFF2E1E20), Color(0xFF3A2A2C), Color(0xFFF3E6E7),
        Color(0xFFE8C25A), Color(0xFF3A2A00), Color(0xFF5A4300), Color(0xFFFBEBC0),
        background = Color(0xFF120B0C), ink = Color(0xFFF1EBEB), muted = Color(0xFFB3A5A7), outline = Color(0xFF7D6F71), outlineVariant = Color(0xFF34282A),
        lowest = Color(0xFF0D0809), container = Color(0xFF1C1314), high = Color(0xFF261B1C), highest = Color(0xFF2E2223), bright = Color(0xFF342728),
        inversePrimary = Color(0xFFB3122E),
    ),
    heroLight = listOf(Color(0xFF8E0E24), Color(0xFFB3122E), Color(0xFF9A6410)),
    heroDark = listOf(Color(0xFF6E0A1E), Color(0xFF8A1026), Color(0xFF6E4A0E)),
    glows = listOf(Color(0xFFD1213F), Color(0xFFE0A526), Color(0xFF8E0E24)),
    bottomLight = Color(0xFFF6EFEE), bottomDark = Color(0xFF0E0809),
)

private val StarShield = PaletteSpec(
    // Navy with a flag-red tertiary and silver neutrals.
    light = lightScheme(
        Color(0xFF1F3A93), Color(0xFFDCE3F7), Color(0xFF0A1A4D),
        Color(0xFF5E6673), Color(0xFFE6E9EE), Color(0xFF1A1E25),
        Color(0xFFB22234), Color(0xFFFBDDE0), Color(0xFF4A0A12),
        background = Color(0xFFF5F6F8), ink = Color(0xFF121620), muted = Color(0xFF5B6270), outline = Color(0xFF9AA0AB), outlineVariant = Color(0xFFE1E4EA),
        high = Color(0xFFECEEF2), highest = Color(0xFFE3E6EB), inversePrimary = Color(0xFFA9BCF5),
    ),
    dark = darkScheme(
        Color(0xFFA9BCF5), Color(0xFF0A1A4D), Color(0xFF22397F), Color(0xFFDCE3F7),
        Color(0xFFC3C8D1), Color(0xFF1E222A), Color(0xFF2A2F38), Color(0xFFECEEF2),
        Color(0xFFFF8A93), Color(0xFF4A0A12), Color(0xFF6E1620), Color(0xFFFBDDE0),
        background = Color(0xFF0B0E16), ink = Color(0xFFECEEF3), muted = Color(0xFFA2A9B6), outline = Color(0xFF6B7280), outlineVariant = Color(0xFF252B38),
        lowest = Color(0xFF080A10), container = Color(0xFF131826), high = Color(0xFF1B2130), highest = Color(0xFF222838), bright = Color(0xFF282F40),
        inversePrimary = Color(0xFF1F3A93),
    ),
    heroLight = listOf(Color(0xFF14275F), Color(0xFF1F3A93), Color(0xFF7A2333)),
    heroDark = listOf(Color(0xFF0F1E4A), Color(0xFF182E73), Color(0xFF5E1A27)),
    glows = listOf(Color(0xFF1F3A93), Color(0xFFB22234), Color(0xFFA0A8B8)),
    bottomLight = Color(0xFFEEF0F4), bottomDark = Color(0xFF080B12),
)

private val Thunder = PaletteSpec(
    // Storm steel-blue by day with an electric-cyan tertiary; cyan leads at night.
    light = lightScheme(
        Color(0xFF2C5C80), Color(0xFFDAE5F0), Color(0xFF0E2236),
        Color(0xFF56616D), Color(0xFFE3E8ED), Color(0xFF151B22),
        Color(0xFF007C99), Color(0xFFCCF3FB), Color(0xFF00313D),
        background = Color(0xFFF3F6F8), ink = Color(0xFF10161C), muted = Color(0xFF58636E), outline = Color(0xFF96A1AC), outlineVariant = Color(0xFFDEE4EA),
        high = Color(0xFFEAEFF3), highest = Color(0xFFE1E7EC), inversePrimary = Color(0xFF5CE1FF),
    ),
    dark = darkScheme(
        Color(0xFF5CE1FF), Color(0xFF00313D), Color(0xFF0B4A5C), Color(0xFFCCF3FB),
        Color(0xFFA9BACB), Color(0xFF18222D), Color(0xFF263240), Color(0xFFE2EAF2),
        Color(0xFF9CC2E6), Color(0xFF0E2236), Color(0xFF2A4560), Color(0xFFDAE5F0),
        background = Color(0xFF0B1016), ink = Color(0xFFE7EDF3), muted = Color(0xFF9DA9B5), outline = Color(0xFF65717D), outlineVariant = Color(0xFF222C36),
        lowest = Color(0xFF080C11), container = Color(0xFF121922), high = Color(0xFF1A232D), highest = Color(0xFF212B36), bright = Color(0xFF27323E),
        inversePrimary = Color(0xFF2C5C80),
    ),
    heroLight = listOf(Color(0xFF2A3F55), Color(0xFF2C5C80), Color(0xFF0A7E9C)),
    heroDark = listOf(Color(0xFF1C2A3A), Color(0xFF244560), Color(0xFF075E75)),
    glows = listOf(Color(0xFF00B4D8), Color(0xFF3A5A78), Color(0xFF7FA7C9)),
    bottomLight = Color(0xFFECF1F5), bottomDark = Color(0xFF080C12),
)

private val Gamma = PaletteSpec(
    // Deep forest green with a violet tertiary.
    light = lightScheme(
        Color(0xFF1F6B3A), Color(0xFFD3EEDB), Color(0xFF00321A),
        Color(0xFF5A6359), Color(0xFFE4EAE3), Color(0xFF171D17),
        Color(0xFF6B3FA0), Color(0xFFEADDFB), Color(0xFF2A0E52),
        background = Color(0xFFF4F7F3), ink = Color(0xFF131A14), muted = Color(0xFF586257), outline = Color(0xFF95A094), outlineVariant = Color(0xFFDDE5DC),
        high = Color(0xFFEBF0EA), highest = Color(0xFFE2E9E1), inversePrimary = Color(0xFF7FD99A),
    ),
    dark = darkScheme(
        Color(0xFF7FD99A), Color(0xFF00391B), Color(0xFF145230), Color(0xFFD3EEDB),
        Color(0xFFB7C4B6), Color(0xFF1C251C), Color(0xFF283228), Color(0xFFE4EDE3),
        Color(0xFFC7A6FF), Color(0xFF2A0E52), Color(0xFF4A2785), Color(0xFFEADDFB),
        background = Color(0xFF0B100C), ink = Color(0xFFE6EEE6), muted = Color(0xFF9DAA9C), outline = Color(0xFF667366), outlineVariant = Color(0xFF222E23),
        lowest = Color(0xFF080C08), container = Color(0xFF121A13), high = Color(0xFF1A241B), highest = Color(0xFF212C22), bright = Color(0xFF263227),
        inversePrimary = Color(0xFF1F6B3A),
    ),
    heroLight = listOf(Color(0xFF1B5E35), Color(0xFF2F6B3F), Color(0xFF5B3592)),
    heroDark = listOf(Color(0xFF123F25), Color(0xFF1E4B2E), Color(0xFF3E2470)),
    glows = listOf(Color(0xFF2FA35A), Color(0xFF7C4DCC), Color(0xFF1F6B3A)),
    bottomLight = Color(0xFFEDF3EC), bottomDark = Color(0xFF080C09),
)

private val Vibranium = PaletteSpec(
    // Royal violet with silver; night is almost black.
    light = lightScheme(
        Color(0xFF5B2DB3), Color(0xFFE7DDFB), Color(0xFF1E0A4D),
        Color(0xFF5E6069), Color(0xFFE7E8EC), Color(0xFF18191E),
        Color(0xFF3F4654), Color(0xFFE1E4EA), Color(0xFF121620),
        background = Color(0xFFF5F5F8), ink = Color(0xFF121218), muted = Color(0xFF5C5E6A), outline = Color(0xFF9C9DA8), outlineVariant = Color(0xFFE3E3EA),
        high = Color(0xFFEDEDF2), highest = Color(0xFFE4E4EB), inversePrimary = Color(0xFFBFA6FF),
    ),
    dark = darkScheme(
        Color(0xFFBFA6FF), Color(0xFF24104F), Color(0xFF43238F), Color(0xFFE7DDFB),
        Color(0xFFC9CBD3), Color(0xFF1E1F24), Color(0xFF2B2C33), Color(0xFFECEDF1),
        Color(0xFFD8DCE4), Color(0xFF1A1D24), Color(0xFF33363F), Color(0xFFEEF0F4),
        background = Color(0xFF050507), ink = Color(0xFFECECF1), muted = Color(0xFFA3A4AF), outline = Color(0xFF6C6D78), outlineVariant = Color(0xFF222229),
        lowest = Color(0xFF030304), container = Color(0xFF0F0F14), high = Color(0xFF18181F), highest = Color(0xFF1F1F27), bright = Color(0xFF26262F),
        inversePrimary = Color(0xFF5B2DB3),
    ),
    heroLight = listOf(Color(0xFF16141F), Color(0xFF3A1F7A), Color(0xFF5B2DB3)),
    heroDark = listOf(Color(0xFF0B0B10), Color(0xFF2A1660), Color(0xFF452399)),
    glows = listOf(Color(0xFF6D3FD6), Color(0xFFA0A4B0), Color(0xFF3A1F7A)),
    bottomLight = Color(0xFFEFEFF4), bottomDark = Color(0xFF040406),
)

// Cinema palettes: film-mood colour stories, same structure and the same shared money colours.

private val IronThrone = PaletteSpec(
    // Cold slate and ice-blue with an ember-gold tertiary; dark stone at night.
    light = lightScheme(
        Color(0xFF3E5A73), Color(0xFFDCE6EF), Color(0xFF10202E),
        Color(0xFF5D646C), Color(0xFFE5E8EB), Color(0xFF181C20),
        Color(0xFFA0521A), Color(0xFFFBE2CF), Color(0xFF3D1A03),
        background = Color(0xFFF3F5F7), ink = Color(0xFF13171B), muted = Color(0xFF5A616A), outline = Color(0xFF99A0A8), outlineVariant = Color(0xFFDFE3E7),
        high = Color(0xFFEAEDF0), highest = Color(0xFFE1E5E9), inversePrimary = Color(0xFFA9C6E0),
    ),
    dark = darkScheme(
        Color(0xFFA9C6E0), Color(0xFF0F2233), Color(0xFF2A4258), Color(0xFFDCE6EF),
        Color(0xFFBDC3C9), Color(0xFF1C2024), Color(0xFF2C3136), Color(0xFFE8EBEE),
        Color(0xFFF0A868), Color(0xFF3D1A03), Color(0xFF6A3410), Color(0xFFFBE2CF),
        background = Color(0xFF0E1013), ink = Color(0xFFE9ECEF), muted = Color(0xFFA0A7AF), outline = Color(0xFF6A7178), outlineVariant = Color(0xFF262A2F),
        lowest = Color(0xFF0A0B0D), container = Color(0xFF16191D), high = Color(0xFF1F2328), highest = Color(0xFF262A30), bright = Color(0xFF2C3137),
        inversePrimary = Color(0xFF3E5A73),
    ),
    heroLight = listOf(Color(0xFF2A3A4A), Color(0xFF3E5A73), Color(0xFF8A4A1C)),
    heroDark = listOf(Color(0xFF1A242E), Color(0xFF2A4258), Color(0xFF6A3410)),
    glows = listOf(Color(0xFF6F9CC4), Color(0xFFE08A3C), Color(0xFF3E5A73)),
    bottomLight = Color(0xFFECEFF2), bottomDark = Color(0xFF0A0C0F),
)

private val MiddleRealm = PaletteSpec(
    // Olive and antique gold on parchment by day; deep forest charcoal by night.
    light = lightScheme(
        Color(0xFF5B6B2E), Color(0xFFE6EBCF), Color(0xFF1C2306),
        Color(0xFF6B6352), Color(0xFFEEE8DA), Color(0xFF231F14),
        Color(0xFF8C6A1E), Color(0xFFF6E7C1), Color(0xFF33250A),
        background = Color(0xFFF8F4EA), ink = Color(0xFF1E1C16), muted = Color(0xFF665F50), outline = Color(0xFFA39C8A), outlineVariant = Color(0xFFE8E1D0),
        high = Color(0xFFF1ECDF), highest = Color(0xFFE9E3D3), inversePrimary = Color(0xFFC2D08A),
    ),
    dark = darkScheme(
        Color(0xFFC2D08A), Color(0xFF263008), Color(0xFF3D4A18), Color(0xFFE6EBCF),
        Color(0xFFCFC7B3), Color(0xFF2A261B), Color(0xFF36322A), Color(0xFFEEE8DA),
        Color(0xFFE2C27A), Color(0xFF33250A), Color(0xFF5A4515), Color(0xFFF6E7C1),
        background = Color(0xFF0D110D), ink = Color(0xFFECEDE4), muted = Color(0xFFA7AA9C), outline = Color(0xFF6F7366), outlineVariant = Color(0xFF252A22),
        lowest = Color(0xFF090C09), container = Color(0xFF151A14), high = Color(0xFF1D231C), highest = Color(0xFF242A22), bright = Color(0xFF2A3128),
        inversePrimary = Color(0xFF5B6B2E),
    ),
    heroLight = listOf(Color(0xFF3E4A20), Color(0xFF5B6B2E), Color(0xFF8C6A1E)),
    heroDark = listOf(Color(0xFF26301A), Color(0xFF3A4720), Color(0xFF5E4714)),
    glows = listOf(Color(0xFF7E9440), Color(0xFFC9A24A), Color(0xFF3E5A2E)),
    bottomLight = Color(0xFFF3EEE1), bottomDark = Color(0xFF090C09),
)

private val Nitro = PaletteSpec(
    // Asphalt neutrals, a neon-blue accent and an orange tertiary.
    light = lightScheme(
        Color(0xFF1F5FD6), Color(0xFFDCE6FC), Color(0xFF0A1F55),
        Color(0xFF4E535B), Color(0xFFE4E6EA), Color(0xFF15171B),
        Color(0xFFC2410C), Color(0xFFFDE1D2), Color(0xFF451404),
        background = Color(0xFFF4F5F7), ink = Color(0xFF111317), muted = Color(0xFF5A5E66), outline = Color(0xFF999DA5), outlineVariant = Color(0xFFE0E2E6),
        high = Color(0xFFEBECEF), highest = Color(0xFFE2E4E8), inversePrimary = Color(0xFF6EA8FF),
    ),
    dark = darkScheme(
        Color(0xFF4DA3FF), Color(0xFF00224D), Color(0xFF0D3A7A), Color(0xFFD6E6FF),
        Color(0xFFB7BBC2), Color(0xFF1A1C20), Color(0xFF26292E), Color(0xFFE9EAED),
        Color(0xFFFF8A3D), Color(0xFF451404), Color(0xFF6A2A08), Color(0xFFFDE1D2),
        background = Color(0xFF08090B), ink = Color(0xFFECEDEF), muted = Color(0xFF9DA1A8), outline = Color(0xFF676B72), outlineVariant = Color(0xFF1E2024),
        lowest = Color(0xFF050607), container = Color(0xFF111316), high = Color(0xFF1A1C20), highest = Color(0xFF212328), bright = Color(0xFF282A2F),
        inversePrimary = Color(0xFF1F5FD6),
    ),
    heroLight = listOf(Color(0xFF15171C), Color(0xFF1A3C8C), Color(0xFF1F5FD6)),
    heroDark = listOf(Color(0xFF0C0D10), Color(0xFF13306E), Color(0xFF1A4CB0)),
    glows = listOf(Color(0xFF2F7BFF), Color(0xFFFF7A1A), Color(0xFF3A3F48)),
    bottomLight = Color(0xFFEDEFF2), bottomDark = Color(0xFF050607),
)

private val NeoMatrix = PaletteSpec(
    // Near-black with a restrained terminal green.
    light = lightScheme(
        Color(0xFF1C6B3A), Color(0xFFD2EEDC), Color(0xFF00301A),
        Color(0xFF4F5A52), Color(0xFFE3E9E4), Color(0xFF151B17),
        Color(0xFF3E5F4A), Color(0xFFDCE8DF), Color(0xFF10241A),
        background = Color(0xFFF3F6F4), ink = Color(0xFF101512), muted = Color(0xFF57615A), outline = Color(0xFF95A098), outlineVariant = Color(0xFFDDE4DF),
        high = Color(0xFFEAF0EC), highest = Color(0xFFE1E8E3), inversePrimary = Color(0xFF5CE08A),
    ),
    dark = darkScheme(
        Color(0xFF5CE08A), Color(0xFF00391A), Color(0xFF0E3F22), Color(0xFFC8F5D8),
        Color(0xFFA9B8AE), Color(0xFF17201A), Color(0xFF222C25), Color(0xFFE1EBE4),
        Color(0xFF8FBFA0), Color(0xFF10241A), Color(0xFF23402E), Color(0xFFDCE8DF),
        background = Color(0xFF030504), ink = Color(0xFFE3ECE6), muted = Color(0xFF93A099), outline = Color(0xFF5F6B64), outlineVariant = Color(0xFF18201B),
        lowest = Color(0xFF020302), container = Color(0xFF0A0F0C), high = Color(0xFF121914), highest = Color(0xFF19211B), bright = Color(0xFF1F2821),
        inversePrimary = Color(0xFF1C6B3A),
    ),
    heroLight = listOf(Color(0xFF0F1A13), Color(0xFF173A24), Color(0xFF1C6B3A)),
    heroDark = listOf(Color(0xFF050806), Color(0xFF0D2416), Color(0xFF12482A)),
    glows = listOf(Color(0xFF22C55E), Color(0xFF0F3D22), Color(0xFF4ADE80)),
    bottomLight = Color(0xFFECF2EE), bottomDark = Color(0xFF020403),
)

private val InterstellarDust = PaletteSpec(
    // Deep-space navy with warm sand and amber; sand leads at night.
    light = lightScheme(
        Color(0xFF22355E), Color(0xFFDCE2F0), Color(0xFF0B1630),
        Color(0xFF66605A), Color(0xFFEEE8E0), Color(0xFF211D18),
        Color(0xFFA0661C), Color(0xFFF9E6CA), Color(0xFF3A2205),
        background = Color(0xFFF7F5F1), ink = Color(0xFF15171E), muted = Color(0xFF61636B), outline = Color(0xFFA09E9A), outlineVariant = Color(0xFFE6E2DB),
        high = Color(0xFFEFECE6), highest = Color(0xFFE7E3DC), inversePrimary = Color(0xFFE8C48A),
    ),
    dark = darkScheme(
        Color(0xFFE8C48A), Color(0xFF3A2605), Color(0xFF5A3F12), Color(0xFFF9E6CA),
        Color(0xFFB8BFCF), Color(0xFF1A1F2B), Color(0xFF262C3A), Color(0xFFE6E9F0),
        Color(0xFFA9B9E0), Color(0xFF0B1630), Color(0xFF2A3A63), Color(0xFFDCE2F0),
        background = Color(0xFF070A14), ink = Color(0xFFECEDF2), muted = Color(0xFFA0A4B2), outline = Color(0xFF686D7C), outlineVariant = Color(0xFF1F2433),
        lowest = Color(0xFF04060D), container = Color(0xFF0F1320), high = Color(0xFF171C2A), highest = Color(0xFF1E2332), bright = Color(0xFF252B3B),
        inversePrimary = Color(0xFF22355E),
    ),
    heroLight = listOf(Color(0xFF141E3A), Color(0xFF22355E), Color(0xFF8A5A1E)),
    heroDark = listOf(Color(0xFF0A1024), Color(0xFF18264A), Color(0xFF5E3E12)),
    glows = listOf(Color(0xFF3A5BA0), Color(0xFFE0A35A), Color(0xFF1A2550)),
    bottomLight = Color(0xFFF0EDE6), bottomDark = Color(0xFF04060D),
)

private val SpiceDune = PaletteSpec(
    // Burnt orange and ochre on desert sand; a night-desert brown-black after dark.
    light = lightScheme(
        Color(0xFFA34A12), Color(0xFFFBE0CC), Color(0xFF3D1603),
        Color(0xFF75664F), Color(0xFFF1E7D6), Color(0xFF261E10),
        Color(0xFF8A6A2A), Color(0xFFF5E6BF), Color(0xFF302208),
        background = Color(0xFFFAF5EC), ink = Color(0xFF211A12), muted = Color(0xFF6D6252), outline = Color(0xFFA89C88), outlineVariant = Color(0xFFEDE3D2),
        high = Color(0xFFF4ECDF), highest = Color(0xFFECE2D2), inversePrimary = Color(0xFFF5A26A),
    ),
    dark = darkScheme(
        Color(0xFFF5A26A), Color(0xFF3D1603), Color(0xFF6A2E0A), Color(0xFFFBE0CC),
        Color(0xFFD8C8AE), Color(0xFF2E2414), Color(0xFF3A3020), Color(0xFFF1E7D6),
        Color(0xFFE2C27A), Color(0xFF302208), Color(0xFF57421A), Color(0xFFF5E6BF),
        background = Color(0xFF120E0B), ink = Color(0xFFF2ECE4), muted = Color(0xFFB2A797), outline = Color(0xFF7C7264), outlineVariant = Color(0xFF322A21),
        lowest = Color(0xFF0D0A08), container = Color(0xFF1C1712), high = Color(0xFF261F19), highest = Color(0xFF2D251E), bright = Color(0xFF342B23),
        inversePrimary = Color(0xFFA34A12),
    ),
    heroLight = listOf(Color(0xFF7A3410), Color(0xFFA34A12), Color(0xFFA0702A)),
    heroDark = listOf(Color(0xFF4E220B), Color(0xFF6E330F), Color(0xFF6E5018)),
    glows = listOf(Color(0xFFE07A2E), Color(0xFFC9A050), Color(0xFF8A3A12)),
    bottomLight = Color(0xFFF5EEE2), bottomDark = Color(0xFF0D0A08),
)

/** True black for OLED screens: background and lowest surfaces #000000, cards a hair above (#0B0B0D). */
fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black, surface = Color.Black, surfaceDim = Color.Black, inverseOnSurface = Color.Black,
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0B0B0D), surfaceContainer = Color(0xFF0B0B0D),
    surfaceContainerHigh = Color(0xFF141417), surfaceContainerHighest = Color(0xFF1B1B1F), surfaceBright = Color(0xFF222226),
    surfaceVariant = Color(0xFF141417),
)

/** This palette with a true-black dark scheme and backdrop. */
fun PaletteSpec.pureBlack(): PaletteSpec = PaletteSpec(light, dark.pureBlack(), heroLight, heroDark, glows, bottomLight, Color.Black)

/** The colours behind a [ThemePalette] choice. */
val ThemePalette.spec: PaletteSpec
    get() = when (this) {
        ThemePalette.CLASSIC -> Classic
        ThemePalette.EMERALD -> Emerald
        ThemePalette.GRAPHITE -> Graphite
        ThemePalette.INDIGO -> Indigo
        ThemePalette.SAFFRON -> Saffron
        ThemePalette.OCEAN -> Ocean
        ThemePalette.ROSE_QUARTZ -> RoseQuartz
        ThemePalette.LAVENDER_BLOOM -> LavenderBloom
        ThemePalette.MIDNIGHT -> Midnight
        ThemePalette.ARC_RED -> ArcRed
        ThemePalette.STAR_SHIELD -> StarShield
        ThemePalette.THUNDER -> Thunder
        ThemePalette.GAMMA -> Gamma
        ThemePalette.VIBRANIUM -> Vibranium
        ThemePalette.IRON_THRONE -> IronThrone
        ThemePalette.MIDDLE_REALM -> MiddleRealm
        ThemePalette.NITRO -> Nitro
        ThemePalette.NEO_MATRIX -> NeoMatrix
        ThemePalette.INTERSTELLAR_DUST -> InterstellarDust
        ThemePalette.SPICE_DUNE -> SpiceDune
    }

/** The palette in use, for the drawings that go beyond the colour scheme (hero gradient, backdrop glows). */
val LocalPalette = staticCompositionLocalOf { Classic }

/**
 * True when the user turned animations off (Developer options or Accessibility, "Remove animations"):
 * decorative motion is then skipped, not merely sped up.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }
