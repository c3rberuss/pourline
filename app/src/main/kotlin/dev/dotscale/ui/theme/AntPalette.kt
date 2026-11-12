package dev.dotscale.ui.theme

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Port del algoritmo `generate` de `@ant-design/colors`: a partir de un color semilla genera la paleta de
 * 10 tonos de la que Ant Design deriva sus tokens (`colorXxxBg`, `colorXxxBorder`, `colorXxx`, `colorXxxActive`…).
 *
 * En modo oscuro cada tono se mezcla con el fondo oscuro igual que hace `theme.darkAlgorithm` de antd.
 * Trabaja con ARGB en `Int` para poder probarse en la JVM sin Android.
 */
internal object AntPalette {
  private const val HUE_STEP = 2
  private const val SATURATION_STEP = 0.16
  private const val SATURATION_STEP_2 = 0.05
  private const val BRIGHTNESS_STEP_1 = 0.05
  private const val BRIGHTNESS_STEP_2 = 0.15
  private const val LIGHT_COLOR_COUNT = 5
  private const val DARK_COLOR_COUNT = 4

  /** Fondo con el que se mezclan los tonos en modo oscuro (`#141414`, el `colorBgContainer` oscuro). */
  val DARK_BACKGROUND: Int = 0xFF141414.toInt()

  /** Para cada posición de la paleta oscura: índice del tono claro y porcentaje de mezcla sobre el fondo. */
  private val DARK_COLOR_MAP = listOf(
    7 to 15, 6 to 25, 5 to 30, 5 to 45, 5 to 65,
    5 to 85, 4 to 90, 3 to 95, 2 to 97, 1 to 98,
  )

  fun generate(seed: Int, dark: Boolean = false, background: Int = DARK_BACKGROUND): List<Int> {
    val hsv = Hsv.of(seed)
    val light = buildList {
      for (i in LIGHT_COLOR_COUNT downTo 1) add(hsvToArgb(hue(hsv, i, light = true), saturation(hsv, i, light = true), value(hsv, i, light = true)))
      add(seed or OPAQUE)
      for (i in 1..DARK_COLOR_COUNT) add(hsvToArgb(hue(hsv, i, light = false), saturation(hsv, i, light = false), value(hsv, i, light = false)))
    }
    if (!dark) return light
    return DARK_COLOR_MAP.map { (index, amount) -> mix(background, light[index], amount / 100.0) }
  }

  private class Hsv(val h: Double, val s: Double, val v: Double) {
    companion object {
      fun of(argb: Int): Hsv {
        val r = channel(argb, 16) / 255.0
        val g = channel(argb, 8) / 255.0
        val b = channel(argb, 0) / 255.0
        val max = maxOf(r, g, b)
        val d = max - minOf(r, g, b)
        val h = when {
          d == 0.0 -> 0.0
          max == r -> (g - b) / d + if (g < b) 6 else 0
          max == g -> (b - r) / d + 2
          else -> (r - g) / d + 4
        } / 6
        return Hsv(h = h * 360, s = if (max == 0.0) 0.0 else d / max, v = max)
      }
    }
  }

  private fun hue(hsv: Hsv, i: Int, light: Boolean): Double {
    val h = hsv.h.roundToInt()
    val shift = HUE_STEP * i
    // Los tonos fríos (60°–240°) giran hacia el azul al oscurecer; los cálidos, hacia el rojo.
    var hue = if (h in 60..240) {
      if (light) h - shift else h + shift
    } else {
      if (light) h + shift else h - shift
    }
    if (hue < 0) hue += 360 else if (hue >= 360) hue -= 360
    return hue.toDouble()
  }

  private fun saturation(hsv: Hsv, i: Int, light: Boolean): Double {
    if (hsv.h == 0.0 && hsv.s == 0.0) return hsv.s
    var s = when {
      light -> hsv.s - SATURATION_STEP * i
      i == DARK_COLOR_COUNT -> hsv.s + SATURATION_STEP
      else -> hsv.s + SATURATION_STEP_2 * i
    }
    if (s > 1) s = 1.0
    if (light && i == LIGHT_COLOR_COUNT && s > 0.1) s = 0.1
    if (s < 0.06) s = 0.06
    return round2(s)
  }

  private fun value(hsv: Hsv, i: Int, light: Boolean): Double {
    val v = if (light) hsv.v + BRIGHTNESS_STEP_1 * i else hsv.v - BRIGHTNESS_STEP_2 * i
    return round2(v.coerceIn(0.0, 1.0))
  }

  private fun hsvToArgb(h: Double, s: Double, v: Double): Int {
    val h6 = (h % 360) / 360 * 6
    val i = floor(h6).toInt()
    val f = h6 - i
    val p = v * (1 - s)
    val q = v * (1 - f * s)
    val t = v * (1 - (1 - f) * s)
    val (r, g, b) = when (i % 6) {
      0 -> Triple(v, t, p)
      1 -> Triple(q, v, p)
      2 -> Triple(p, v, t)
      3 -> Triple(p, q, v)
      4 -> Triple(t, p, v)
      else -> Triple(v, p, q)
    }
    return argb(r * 255, g * 255, b * 255)
  }

  private fun mix(background: Int, color: Int, amount: Double): Int {
    fun blend(shift: Int): Double {
      val from = channel(background, shift)
      return (channel(color, shift) - from) * amount + from
    }
    return argb(blend(16), blend(8), blend(0))
  }

  private const val OPAQUE = 0xFF shl 24

  private fun channel(argb: Int, shift: Int): Int = (argb shr shift) and 0xFF

  private fun argb(r: Double, g: Double, b: Double): Int {
    fun c(x: Double) = x.roundToInt().coerceIn(0, 255)
    return OPAQUE or (c(r) shl 16) or (c(g) shl 8) or c(b)
  }

  private fun round2(x: Double): Double = (x * 100).roundToInt() / 100.0
}
