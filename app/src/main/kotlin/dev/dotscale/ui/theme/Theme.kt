package dev.dotscale.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight

private val LightAntColors = antLightColors()
private val DarkAntColors = antDarkColors()
private val DefaultAntTypography = antTypography()
private val LightMaterialScheme = LightAntColors.toMaterialColorScheme()
private val DarkMaterialScheme = DarkAntColors.toMaterialColorScheme()

private val LocalAntColors = staticCompositionLocalOf { LightAntColors }
private val LocalAntTypography = staticCompositionLocalOf { DefaultAntTypography }
private val LocalAntRadius = staticCompositionLocalOf { AntRadius() }
private val LocalAntSpacing = staticCompositionLocalOf { AntSpacing() }
private val LocalAntControlHeight = staticCompositionLocalOf { AntControlHeight() }
private val LocalAntMotion = staticCompositionLocalOf { AntMotion() }

/** Acceso a los tokens de Ant Design del tema actual, al estilo de `MaterialTheme`. */
object AntTheme {
  val colors: AntColors
    @Composable @ReadOnlyComposable get() = LocalAntColors.current
  val typography: AntTypography
    @Composable @ReadOnlyComposable get() = LocalAntTypography.current
  val radius: AntRadius
    @Composable @ReadOnlyComposable get() = LocalAntRadius.current
  val spacing: AntSpacing
    @Composable @ReadOnlyComposable get() = LocalAntSpacing.current
  val controlHeight: AntControlHeight
    @Composable @ReadOnlyComposable get() = LocalAntControlHeight.current
  val motion: AntMotion
    @Composable @ReadOnlyComposable get() = LocalAntMotion.current
}

/**
 * Tema de la app basado en Ant Design v5. Provee los tokens de [AntTheme] y además configura
 * `MaterialTheme` con colores equivalentes para que los componentes de Material que se sigan usando
 * (Scaffold, Snackbar, bottom sheet…) encajen visualmente.
 */
@Composable
fun DotScaleTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colors = if (darkTheme) DarkAntColors else LightAntColors
  val typography = DefaultAntTypography
  val radius = AntRadius()
  CompositionLocalProvider(
    LocalAntColors provides colors,
    LocalAntTypography provides typography,
    LocalAntRadius provides radius,
  ) {
    MaterialTheme(
      colorScheme = if (darkTheme) DarkMaterialScheme else LightMaterialScheme,
      typography = typography.toMaterialTypography(),
      shapes = radius.toMaterialShapes(),
    ) {
      CompositionLocalProvider(
        LocalContentColor provides colors.text,
        LocalTextStyle provides typography.text,
        content = content,
      )
    }
  }
}

private fun AntColors.toMaterialColorScheme(): ColorScheme {
  fun Color.solid(over: Color = bgContainer) = compositeOver(over)
  val base = if (isDark) darkColorScheme() else lightColorScheme()
  return base.copy(
    primary = primary.solid,
    onPrimary = textLightSolid,
    primaryContainer = primary.bg,
    onPrimaryContainer = primary.text,
    inversePrimary = primary.hover,
    secondary = primary.active,
    onSecondary = textLightSolid,
    secondaryContainer = primary.bg,
    onSecondaryContainer = primary.text,
    tertiary = info.base,
    onTertiary = textLightSolid,
    tertiaryContainer = info.bg,
    onTertiaryContainer = info.text,
    background = bgLayout,
    onBackground = text.solid(bgLayout),
    surface = bgContainer,
    onSurface = text.solid(),
    surfaceVariant = fillSecondary.solid(),
    onSurfaceVariant = textSecondary.solid(),
    surfaceTint = primary.base,
    inverseSurface = bgSpotlight.solid(),
    inverseOnSurface = textLightSolid,
    error = error.solid,
    onError = textLightSolid,
    errorContainer = error.bg,
    onErrorContainer = error.text,
    outline = border,
    outlineVariant = borderSecondary,
    scrim = Color.Black,
    surfaceBright = bgElevated,
    surfaceDim = bgLayout,
    surfaceContainerLowest = bgContainer,
    surfaceContainerLow = bgContainer,
    surfaceContainer = bgElevated,
    surfaceContainerHigh = bgElevated,
    surfaceContainerHighest = fillTertiary.solid(bgElevated),
  )
}

private fun AntTypography.toMaterialTypography(): Typography = Typography(
  displayLarge = heading1,
  displayMedium = heading2,
  displaySmall = heading3,
  headlineLarge = heading2,
  headlineMedium = heading3,
  headlineSmall = heading4,
  titleLarge = heading4,
  titleMedium = heading5,
  titleSmall = text.copy(fontWeight = FontWeight.SemiBold),
  bodyLarge = textLG,
  bodyMedium = text,
  bodySmall = textSM,
  labelLarge = text.copy(fontWeight = FontWeight.Medium),
  labelMedium = textSM.copy(fontWeight = FontWeight.Medium),
  labelSmall = textSM,
)

private fun AntRadius.toMaterialShapes(): Shapes = Shapes(
  extraSmall = RoundedCornerShape(sm),
  small = RoundedCornerShape(base),
  medium = RoundedCornerShape(lg),
  large = RoundedCornerShape(lg),
  extraLarge = RoundedCornerShape(lg * 2),
)
