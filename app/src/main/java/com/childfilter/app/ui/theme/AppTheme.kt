package com.childfilter.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Nude / warm-earth palette ──────────────────────────────────────────────

// Light mode
val NudePrimary          = Color(0xFFC4956A)  // warm sand
val NudeOnPrimary        = Color(0xFFFFFFFF)
val NudePrimaryContainer = Color(0xFFF5E0C8)  // pale blush
val NudeOnPrimaryContainer = Color(0xFF3D220A)

val NudeSecondary          = Color(0xFFB5896A)  // dusty terracotta
val NudeOnSecondary        = Color(0xFFFFFFFF)
val NudeSecondaryContainer = Color(0xFFF0D9C8)
val NudeOnSecondaryContainer = Color(0xFF3A1E0C)

val NudeTertiary          = Color(0xFFA88B9E)  // dusty mauve
val NudeOnTertiary        = Color(0xFFFFFFFF)
val NudeTertiaryContainer = Color(0xFFEBDCE8)
val NudeOnTertiaryContainer = Color(0xFF2E1A2C)

val NudeBackground   = Color(0xFFFFF8F2)  // warm ivory
val NudeOnBackground = Color(0xFF2C1A0E)

val NudeSurface         = Color(0xFFFFFAF6)
val NudeSurfaceVariant  = Color(0xFFF2E4D5)
val NudeOnSurface       = Color(0xFF2C1A0E)
val NudeOnSurfaceVariant = Color(0xFF7A5C4A)

val NudeOutline       = Color(0xFFBFA48C)
val NudeError         = Color(0xFFB55A4A)
val NudeErrorContainer = Color(0xFFFDEAE5)
val NudeOnError       = Color(0xFFFFFFFF)
val NudeOnErrorContainer = Color(0xFF410002)

// Dark mode
val NudeDarkPrimary          = Color(0xFFD4AA80)  // golden sand
val NudeDarkOnPrimary        = Color(0xFF3D220A)
val NudeDarkPrimaryContainer = Color(0xFF5C3A1A)
val NudeDarkOnPrimaryContainer = Color(0xFFF5D9B5)

val NudeDarkSecondary          = Color(0xFFC8997A)
val NudeDarkOnSecondary        = Color(0xFF3A1E0C)
val NudeDarkSecondaryContainer = Color(0xFF55321A)
val NudeDarkOnSecondaryContainer = Color(0xFFF0CDB0)

val NudeDarkTertiary          = Color(0xFFCDB0C4)
val NudeDarkOnTertiary        = Color(0xFF2E1A2C)
val NudeDarkTertiaryContainer = Color(0xFF4A2E46)
val NudeDarkOnTertiaryContainer = Color(0xFFEBD8E6)

val NudeDarkBackground   = Color(0xFF1C1008)  // deep warm brown
val NudeDarkOnBackground = Color(0xFFF5E6D5)

val NudeDarkSurface         = Color(0xFF241810)
val NudeDarkSurfaceVariant  = Color(0xFF3D2C20)
val NudeDarkOnSurface       = Color(0xFFF5E6D5)
val NudeDarkOnSurfaceVariant = Color(0xFFCFB49A)

val NudeDarkOutline       = Color(0xFF8C6E58)
val NudeDarkError         = Color(0xFFCF8A7E)
val NudeDarkErrorContainer = Color(0xFF6B2218)
val NudeDarkOnError       = Color(0xFF2C0A06)
val NudeDarkOnErrorContainer = Color(0xFFF9BFB5)

// ── Color schemes ───────────────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary             = NudePrimary,
    onPrimary           = NudeOnPrimary,
    primaryContainer    = NudePrimaryContainer,
    onPrimaryContainer  = NudeOnPrimaryContainer,
    secondary           = NudeSecondary,
    onSecondary         = NudeOnSecondary,
    secondaryContainer  = NudeSecondaryContainer,
    onSecondaryContainer = NudeOnSecondaryContainer,
    tertiary            = NudeTertiary,
    onTertiary          = NudeOnTertiary,
    tertiaryContainer   = NudeTertiaryContainer,
    onTertiaryContainer = NudeOnTertiaryContainer,
    background          = NudeBackground,
    onBackground        = NudeOnBackground,
    surface             = NudeSurface,
    surfaceVariant      = NudeSurfaceVariant,
    onSurface           = NudeOnSurface,
    onSurfaceVariant    = NudeOnSurfaceVariant,
    outline             = NudeOutline,
    error               = NudeError,
    errorContainer      = NudeErrorContainer,
    onError             = NudeOnError,
    onErrorContainer    = NudeOnErrorContainer,
)

private val DarkColorScheme = darkColorScheme(
    primary             = NudeDarkPrimary,
    onPrimary           = NudeDarkOnPrimary,
    primaryContainer    = NudeDarkPrimaryContainer,
    onPrimaryContainer  = NudeDarkOnPrimaryContainer,
    secondary           = NudeDarkSecondary,
    onSecondary         = NudeDarkOnSecondary,
    secondaryContainer  = NudeDarkSecondaryContainer,
    onSecondaryContainer = NudeDarkOnSecondaryContainer,
    tertiary            = NudeDarkTertiary,
    onTertiary          = NudeDarkOnTertiary,
    tertiaryContainer   = NudeDarkTertiaryContainer,
    onTertiaryContainer = NudeDarkOnTertiaryContainer,
    background          = NudeDarkBackground,
    onBackground        = NudeDarkOnBackground,
    surface             = NudeDarkSurface,
    surfaceVariant      = NudeDarkSurfaceVariant,
    onSurface           = NudeDarkOnSurface,
    onSurfaceVariant    = NudeDarkOnSurfaceVariant,
    outline             = NudeDarkOutline,
    error               = NudeDarkError,
    errorContainer      = NudeDarkErrorContainer,
    onError             = NudeDarkOnError,
    onErrorContainer    = NudeDarkOnErrorContainer,
)

// ── Theme composable ────────────────────────────────────────────────────────

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Dynamic color disabled — always use our nude palette
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
