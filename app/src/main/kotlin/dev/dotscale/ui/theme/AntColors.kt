package dev.dotscale.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Seed tokens de color de Ant Design v5. Cambiar `primary` re-genera toda la paleta de marca. */
@Immutable
data class AntSeedColors(
  val primary: Color = Color(0xFF1677FF),
  val success: Color = Color(0xFF52C41A),
  val warning: Color = Color(0xFFFAAD14),
  val error: Color = Color(0xFFFF4D4F),
  val info: Color = primary,
)

/**
 * Tonos derivados de un color semilla. Equivalen a los map tokens de antd
 * (`colorPrimaryBg`, `colorPrimaryBgHover`, `colorPrimaryBorder`, `colorPrimaryBorderHover`,
 * `colorPrimaryHover`, `colorPrimary`, `colorPrimaryActive`).
 */
@Immutable
data class AntColorRole(
  val bg: Color,
  val bgHover: Color,
  val border: Color,
  val borderHover: Color,
  val hover: Color,
  val base: Color,
  val active: Color,
  /**
   * Texto con el color del rol (links, tags, textos de estado): el tono más cercano a [base] que cumple
   * WCAG AA (≥ 4.5:1) sobre [bg] y sobre `colorBgContainer`. antd usa [base] directamente.
   */
  val text: Color,
  /** Relleno para texto blanco (botón primario, tag marcado): el tono más cercano a [base] con ≥ 4.5:1. */
  val solid: Color,
  /** Estado pulsado de [solid]: el siguiente tono con más contraste. */
  val solidActive: Color,
)

/** Map tokens de color de Ant Design v5 (algoritmos `defaultAlgorithm` y `darkAlgorithm`). */
@Immutable
data class AntColors(
  val isDark: Boolean,
  val primary: AntColorRole,
  val success: AntColorRole,
  val warning: AntColorRole,
  val error: AntColorRole,
  val info: AntColorRole,
  val text: Color,
  val textSecondary: Color,
  val textTertiary: Color,
  val textQuaternary: Color,
  val textLightSolid: Color,
  val border: Color,
  val borderSecondary: Color,
  val split: Color,
  val fill: Color,
  val fillSecondary: Color,
  val fillTertiary: Color,
  val fillQuaternary: Color,
  val bgLayout: Color,
  val bgContainer: Color,
  val bgElevated: Color,
  val bgSpotlight: Color,
  val bgMask: Color,
) {
  val textDisabled: Color get() = textQuaternary
  val textPlaceholder: Color get() = textQuaternary
  val bgContainerDisabled: Color get() = fillTertiary
}

fun antLightColors(seeds: AntSeedColors = AntSeedColors()): AntColors = AntColors(
  isDark = false,
  primary = seeds.primary.toRole(dark = false, container = LIGHT_CONTAINER),
  success = seeds.success.toRole(dark = false, container = LIGHT_CONTAINER),
  warning = seeds.warning.toRole(dark = false, container = LIGHT_CONTAINER),
  error = seeds.error.toRole(dark = false, container = LIGHT_CONTAINER),
  info = seeds.info.toRole(dark = false, container = LIGHT_CONTAINER),
  text = Color.Black.copy(alpha = 0.88f),
  textSecondary = Color.Black.copy(alpha = 0.65f),
  textTertiary = Color.Black.copy(alpha = 0.45f),
  textQuaternary = Color.Black.copy(alpha = 0.25f),
  textLightSolid = Color.White,
  border = Color(0xFFD9D9D9),
  borderSecondary = Color(0xFFF0F0F0),
  split = Color(0x0F050505),
  fill = Color.Black.copy(alpha = 0.15f),
  fillSecondary = Color.Black.copy(alpha = 0.06f),
  fillTertiary = Color.Black.copy(alpha = 0.04f),
  fillQuaternary = Color.Black.copy(alpha = 0.02f),
  bgLayout = Color(0xFFF5F5F5),
  bgContainer = Color(LIGHT_CONTAINER),
  bgElevated = Color.White,
  bgSpotlight = Color.Black.copy(alpha = 0.85f),
  bgMask = Color.Black.copy(alpha = 0.45f),
)

fun antDarkColors(seeds: AntSeedColors = AntSeedColors()): AntColors = AntColors(
  isDark = true,
  primary = seeds.primary.toRole(dark = true, container = DARK_CONTAINER),
  success = seeds.success.toRole(dark = true, container = DARK_CONTAINER),
  warning = seeds.warning.toRole(dark = true, container = DARK_CONTAINER),
  error = seeds.error.toRole(dark = true, container = DARK_CONTAINER),
  info = seeds.info.toRole(dark = true, container = DARK_CONTAINER),
  text = Color.White.copy(alpha = 0.85f),
  textSecondary = Color.White.copy(alpha = 0.65f),
  textTertiary = Color.White.copy(alpha = 0.45f),
  textQuaternary = Color.White.copy(alpha = 0.25f),
  textLightSolid = Color.White,
  border = Color(0xFF424242),
  borderSecondary = Color(0xFF303030),
  split = Color(0x1FFDFDFD),
  fill = Color.White.copy(alpha = 0.18f),
  fillSecondary = Color.White.copy(alpha = 0.12f),
  fillTertiary = Color.White.copy(alpha = 0.08f),
  fillQuaternary = Color.White.copy(alpha = 0.04f),
  bgLayout = Color.Black,
  bgContainer = Color(DARK_CONTAINER),
  bgElevated = Color(0xFF1F1F1F),
  bgSpotlight = Color(0xFF424242),
  bgMask = Color.Black.copy(alpha = 0.45f),
)

private const val LIGHT_CONTAINER = 0xFFFFFFFF.toInt()
private const val DARK_CONTAINER = 0xFF141414.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

private fun Color.toRole(dark: Boolean, container: Int): AntColorRole {
  val tones = AntPalette.generate(toArgb(), dark)
  val p = tones.map(::Color)
  val solidIndex = AntPalette.nearestAccessible(tones, listOf(WHITE))
  val activeIndex = listOf(solidIndex - 1, solidIndex + 1)
    .filter { it in tones.indices }
    .maxBy { AntPalette.contrast(tones[it], WHITE) }
  return AntColorRole(
    bg = p[0],
    bgHover = p[1],
    border = p[2],
    borderHover = p[3],
    hover = p[4],
    base = p[5],
    active = p[6],
    text = p[AntPalette.nearestAccessible(tones, listOf(tones[0], container))],
    solid = p[solidIndex],
    solidActive = p[activeIndex],
  )
}
