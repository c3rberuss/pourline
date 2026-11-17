package dev.josueamaya.pourline.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Tamaño de los controles (`size` de Button, Input, Segmented…). */
enum class AntSize { Small, Middle, Large }

/** `borderRadiusXS/SM/borderRadius/LG`. */
@Immutable
data class AntRadius(
  val xs: Dp = 2.dp,
  val sm: Dp = 4.dp,
  val base: Dp = 6.dp,
  val lg: Dp = 8.dp,
) {
  fun of(size: AntSize): Dp = when (size) {
    AntSize.Small -> sm
    AntSize.Middle -> base
    AntSize.Large -> lg
  }
}

/** `sizeXXS…sizeXXL`: escala de espaciado en múltiplos de 4. */
@Immutable
data class AntSpacing(
  val xxs: Dp = 4.dp,
  val xs: Dp = 8.dp,
  val sm: Dp = 12.dp,
  val base: Dp = 16.dp,
  val md: Dp = 20.dp,
  val lg: Dp = 24.dp,
  val xl: Dp = 32.dp,
  val xxl: Dp = 48.dp,
)

/**
 * `controlHeightSM/controlHeight/controlHeightLG`. En web antd usa 24/32/40; aquí se suben a 32/40/48
 * para respetar el área táctil mínima de Android (48 dp, completada con `minimumInteractiveComponentSize`).
 */
@Immutable
data class AntControlHeight(
  val sm: Dp = 32.dp,
  val base: Dp = 40.dp,
  val lg: Dp = 48.dp,
) {
  fun of(size: AntSize): Dp = when (size) {
    AntSize.Small -> sm
    AntSize.Middle -> base
    AntSize.Large -> lg
  }
}

/** `motionDurationFast/Mid/Slow` y curvas `motionEaseInOut`/`motionEaseOut`. */
@Immutable
data class AntMotion(
  val durationFast: Int = 100,
  val durationMid: Int = 200,
  val durationSlow: Int = 300,
  val easeInOut: Easing = CubicBezierEasing(0.645f, 0.045f, 0.355f, 1f),
  val easeOut: Easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f),
)
