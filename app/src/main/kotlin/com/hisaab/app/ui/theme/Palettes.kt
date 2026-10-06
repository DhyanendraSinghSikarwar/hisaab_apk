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

/** The colours behind a [ThemePalette] choice. */
val ThemePalette.spec: PaletteSpec
    get() = when (this) {
        ThemePalette.CLASSIC -> Classic
        ThemePalette.EMERALD -> Emerald
        ThemePalette.GRAPHITE -> Graphite
        ThemePalette.INDIGO -> Indigo
        ThemePalette.SAFFRON -> Saffron
        ThemePalette.OCEAN -> Ocean
    }

/** The palette in use, for the drawings that go beyond the colour scheme (hero gradient, backdrop glows). */
val LocalPalette = staticCompositionLocalOf { Classic }

/**
 * True when the user turned animations off (Developer options or Accessibility, "Remove animations"):
 * decorative motion is then skipped, not merely sped up.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }
