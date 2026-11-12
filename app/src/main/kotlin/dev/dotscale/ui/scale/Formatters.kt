package dev.dotscale.ui.scale

import dev.dotscale.protocol.WeightUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Gramos con 1 decimal y onzas con 2 (resolución de la báscula). Evita mostrar "-0,0". */
internal fun formatNumber(value: Double, unit: WeightUnit): String {
  val decimals = if (unit == WeightUnit.OUNCES) 2 else 1
  val shown = if (abs(value) < 0.5 / unit.divisor) 0.0 else value
  return String.format(Locale.getDefault(), "%.${decimals}f", shown)
}

/** `mm:ss` a partir de milisegundos (se trunca al segundo). */
internal fun formatDuration(ms: Long): String {
  val seconds = (ms / 1000).coerceAtLeast(0)
  return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}

/** Etiqueta de un eje: entero si lo es y, si no, hasta 2 decimales sin ceros sobrantes. */
internal fun formatAxisValue(value: Double): String {
  val rounded = value.roundToInt()
  if (abs(value - rounded) < 1e-9) return rounded.toString()
  return String.format(Locale.getDefault(), "%.2f", value).trimEnd('0').trimEnd('.', ',')
}
