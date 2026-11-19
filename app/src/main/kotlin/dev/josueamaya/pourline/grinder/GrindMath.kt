package dev.josueamaya.pourline.grinder

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong

/** Tolerancia para comparar valores en coma flotante. */
const val EPS = 1e-9

/**
 * El múltiplo de `step` más cercano, contado desde `scaleMin`, redondeado a 6 decimales para quitar el ruido de la
 * coma flotante (0,1 × 3). No limita a la escala.
 */
fun snap(value: Double, grinder: Grinder): Double {
  val steps = floor((value - grinder.scaleMin) / grinder.step + 0.5)
  return roundTo6(grinder.scaleMin + steps * grinder.step)
}

fun clampToScale(value: Double, grinder: Grinder): Double = value.coerceIn(grinder.scaleMin, grinder.scaleMax)

/** Decimales con los que se muestra un valor de este molino: los del paso (1 → 0; 0,5 y 0,1 → 1; 0,25 → 2; máximo 3). */
fun decimalsOf(step: Double): Int {
  for (d in 0..2) {
    val scaled = step * 10.0.pow(d)
    if (abs(scaled - scaled.roundToLong()) < 1e-6) return d
  }
  return 3
}

/**
 * Cuántas unidades base tiene una unidad de cada nivel: el producto de los `perParent` de los niveles siguientes.
 * La última (la base) vale 1.
 */
fun multipliers(units: List<GrindUnitLevel>): List<Long> {
  val result = LongArray(units.size) { 1L }
  for (i in units.size - 2 downTo 0) result[i] = result[i + 1] * (units[i + 1].perParent ?: 1)
  return result.toList()
}

internal fun roundTo6(value: Double): Double = (value * 1e6).roundToLong() / 1e6

/** Interpola linealmente por tramos sobre [points] (ordenados por x) y extrapola por los tramos de los extremos. */
internal fun interpolate(points: List<Pair<Double, Double>>, x: Double): Double? {
  if (points.size < 2) return null
  // Con x anterior al primer punto se usa el primer tramo; con x posterior al último, el último.
  val i = points.indexOfLast { it.first <= x }.coerceIn(0, points.size - 2)
  val (x0, y0) = points[i]
  val (x1, y1) = points[i + 1]
  if (abs(x1 - x0) < EPS) return null
  return y0 + (x - x0) / (x1 - x0) * (y1 - y0)
}
