package com.devekene.kalenda.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import com.devekene.kalenda.domain.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = OrchidPrimaryDark,
    onPrimary = OrchidOnPrimaryDark,
    primaryContainer = OrchidPrimaryContainerDark,
    onPrimaryContainer = OrchidOnPrimaryContainerDark,
    secondary = LavenderSecondaryDark,
    onSecondary = LavenderOnSecondaryDark,
    secondaryContainer = LavenderSecondaryContainerDark,
    onSecondaryContainer = LavenderOnSecondaryContainerDark,
    tertiary = PinkTertiaryDark,
    onTertiary = PinkOnTertiaryDark,
    tertiaryContainer = PinkTertiaryContainerDark,
    onTertiaryContainer = PinkOnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    onError = OnErrorDark
)

private val LightColorScheme = lightColorScheme(
    primary = PurplePrimaryLight,
    onPrimary = PurpleOnPrimaryLight,
    primaryContainer = PurplePrimaryContainerLight,
    onPrimaryContainer = PurpleOnPrimaryContainerLight,
    secondary = OrchidSecondaryLight,
    onSecondary = OrchidOnSecondaryLight,
    secondaryContainer = OrchidSecondaryContainerLight,
    onSecondaryContainer = OrchidOnSecondaryContainerLight,
    tertiary = RoseTertiaryLight,
    onTertiary = RoseOnTertiaryLight,
    tertiaryContainer = RoseTertiaryContainerLight,
    onTertiaryContainer = RoseOnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    onError = OnErrorLight
)

val KalendaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val LocalIsDarkTheme = staticCompositionLocalOf { true }

@Composable
fun KalendaTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalIsDarkTheme provides useDark) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KalendaTypography,
            shapes = KalendaShapes,
            content = content
        )
    }
}
