package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    AMOLED("AMOLED"),
    SYSTEM("System")
}

enum class AccentPalette(val label: String, val previewColor: Color) {
    EMERALD("Signal Teal", EmeraldPrimaryDark),
    COBALT("Cobalt Pulse", CobaltPrimaryDark),
    AMBER("Amber Relay", AmberPrimaryDark),
    VIOLET("Violet Pulse", VioletPrimaryDark),
    DYNAMIC("Material You", AccentSkyBlue)
}

private fun buildDarkScheme(
    accent: AccentPalette,
    amoled: Boolean,
    highContrast: Boolean
): ColorScheme {
    val bg = if (amoled) ObsidianBlack else DeepNavyBg
    val surface = if (amoled) Color(0xFF06080D) else DeepNavySurface
    val surfaceVar = if (amoled) Color(0xFF101520) else DeepNavySurfaceHigh
    val surfaceHigh = if (amoled) Color(0xFF161D2C) else DeepNavySurfaceHighest
    val onText = if (highContrast) Color.White else TextPrimaryDark
    val onSecText = if (highContrast) Color(0xFFE2E8F0) else TextSecondaryDark
    val outline = if (highContrast) Color(0xFF64748B) else SlateOutlineDark

    val (primary, onPrimary, container, onContainer, secondary) = when (accent) {
        AccentPalette.EMERALD, AccentPalette.DYNAMIC -> AccentTuple(
            EmeraldPrimaryDark, EmeraldOnPrimaryDark, EmeraldContainerDark, EmeraldOnContainerDark, AccentSkyBlue
        )
        AccentPalette.COBALT -> AccentTuple(
            CobaltPrimaryDark, CobaltOnPrimaryDark, CobaltContainerDark, CobaltOnContainerDark, EmeraldPrimaryDark
        )
        AccentPalette.AMBER -> AccentTuple(
            AmberPrimaryDark, AmberOnPrimaryDark, AmberContainerDark, AmberOnContainerDark, AccentSkyBlue
        )
        AccentPalette.VIOLET -> AccentTuple(
            VioletPrimaryDark, VioletOnPrimaryDark, VioletContainerDark, VioletOnContainerDark, EmeraldPrimaryDark
        )
    }

    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = secondary,
        onSecondary = Color(0xFF002438),
        secondaryContainer = Color(0xFF0E3A54),
        onSecondaryContainer = Color(0xFFCBEAFF),
        tertiary = VioletPrimaryDark,
        onTertiary = VioletOnPrimaryDark,
        background = bg,
        onBackground = onText,
        surface = surface,
        onSurface = onText,
        surfaceVariant = surfaceVar,
        onSurfaceVariant = onSecText,
        surfaceContainerLowest = bg,
        surfaceContainerLow = surface,
        surfaceContainer = surfaceVar,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHigh,
        outline = outline,
        outlineVariant = if (amoled) Color(0xFF1C2434) else Color(0xFF1E2B40),
        error = CallEndRed,
        onError = Color.White,
        errorContainer = Color(0xFF521319),
        onErrorContainer = Color(0xFFFFDAD6)
    )
}

private fun buildLightScheme(accent: AccentPalette, highContrast: Boolean): ColorScheme {
    val onText = if (highContrast) Color.Black else TextPrimaryLight
    val onSec = if (highContrast) Color(0xFF1E293B) else TextSecondaryLight
    val (primary, _, container, onContainer, secondary) = when (accent) {
        AccentPalette.EMERALD, AccentPalette.DYNAMIC -> AccentTuple(
            EmeraldPrimaryLight, Color.White, EmeraldContainerLight, EmeraldOnContainerLight, CobaltPrimaryLight
        )
        AccentPalette.COBALT -> AccentTuple(
            CobaltPrimaryLight, Color.White, CobaltContainerLight, CobaltOnContainerLight, EmeraldPrimaryLight
        )
        AccentPalette.AMBER -> AccentTuple(
            AmberPrimaryLight, Color.White, AmberContainerLight, AmberOnContainerLight, CobaltPrimaryLight
        )
        AccentPalette.VIOLET -> AccentTuple(
            VioletPrimaryLight, Color.White, VioletContainerLight, VioletOnContainerLight, EmeraldPrimaryLight
        )
    }

    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = secondary,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD9ECFF),
        onSecondaryContainer = Color(0xFF001E36),
        tertiary = VioletPrimaryLight,
        onTertiary = Color.White,
        tertiaryContainer = VioletContainerLight,
        onTertiaryContainer = VioletOnContainerLight,
        background = FrostBackground,
        onBackground = onText,
        surface = FrostSurface,
        onSurface = onText,
        surfaceVariant = FrostSurfaceHigh,
        onSurfaceVariant = onSec,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFAFBFD),
        surfaceContainer = FrostSurface,
        surfaceContainerHigh = FrostSurfaceHigh,
        surfaceContainerHighest = FrostSurfaceHighest,
        outline = if (highContrast) Color(0xFF475569) else SlateOutlineLight,
        outlineVariant = if (highContrast) Color(0xFF94A3B8) else Color(0xFFD6E0EC),
        inverseSurface = DeepNavySurface,
        inverseOnSurface = TextPrimaryDark,
        inversePrimary = EmeraldPrimaryDark,
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002)
    )
}

private data class AccentTuple(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color,
    val secondary: Color
)

@Composable
fun VoxLineTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentPalette: AccentPalette = AccentPalette.EMERALD,
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
    }
    val useAmoled = themeMode == ThemeMode.AMOLED

    val context = LocalContext.current
    val colorScheme = if (
        accentPalette == AccentPalette.DYNAMIC &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        !highContrast
    ) {
        val dynamic = if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        if (useAmoled) {
            dynamic.copy(
                background = ObsidianBlack,
                surface = Color(0xFF06080D),
                surfaceContainerLowest = ObsidianBlack
            )
        } else {
            dynamic
        }
    } else if (useDark) {
        buildDarkScheme(accentPalette, useAmoled, highContrast)
    } else {
        buildLightScheme(accentPalette, highContrast)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VoxLineTypography,
        content = content
    )
}
