package dev.josueamaya.pourline.grinder

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong

/** Una marca de la regla. [label] solo la llevan las marcas mayores. */
data class RulerTick(val value: Double, val major: Boolean, val label: String?)

/** La geometría de la regla de molienda, sin dibujo: la posición de un valor y sus marcas. */
object GrindRulerModel {
  private const val MAX_LABELS = 8

  /** Los pasos de las marcas mayores en los molinos simples, de menor a mayor. */
  private val Candidates = (-1..6).flatMap { e -> listOf(1.0, 2.0, 5.0).map { it * 10.0.pow(e) } }

  /**
   * La posición de [value] en la regla, de 0 a 1 de izquierda a derecha: **0 es el extremo grueso**, en las dos
   * direcciones del molino. Un valor fuera de la escala sale fuera de [0, 1].
   */
  fun fraction(value: Double, grinder: Grinder): Float {
    val span = grinder.scaleMax - grinder.scaleMin
    val fromMin = (value - grinder.scaleMin) / span
    return (if (grinder.direction == GrindDirection.HIGHER_IS_COARSER) 1.0 - fromMin else fromMin).toFloat()
  }

  /**
   * Las marcas de la escala. En un molino compuesto, las mayores caen en cada unidad primaria (cada 2, 5 o 10 si
   * salen más de 8) y llevan el número de esa unidad; en uno simple, cada 1, 2 o 5 × 10ⁿ. Entre dos mayores hay
   * hasta 4 menores. Nunca hay más de 8 etiquetas.
   */
  fun ticks(grinder: Grinder): List<RulerTick> {
    val multiplier = if (grinder.units.size > 1) multipliers(grinder.units)[0].toDouble() else 1.0
    val majorStep = pickMajorStep(grinder, multiplier)
    val minorStep = listOf(5, 2).map { majorStep / it }.firstOrNull { it >= grinder.step - EPS }
    val unitStep = minorStep ?: majorStep
    val first = ceil((grinder.scaleMin - EPS) / unitStep).toLong()
    val last = floor((grinder.scaleMax + EPS) / unitStep).toLong()
    return (first..last).map { n ->
      val value = roundTo6(n * unitStep)
      val ratio = value / majorStep
      val major = abs(ratio - ratio.roundToLong()) < 1e-6
      RulerTick(value, major, if (major) label(value / multiplier) else null)
    }
  }

  private fun pickMajorStep(grinder: Grinder, multiplier: Double): Double {
    val span = grinder.scaleMax - grinder.scaleMin
    val candidates = if (multiplier > 1.0) {
      // Compuesto: múltiplos enteros de la unidad primaria.
      listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1000.0).map { it * multiplier }
    } else {
      Candidates.filter { it >= grinder.step - EPS }
    }
    return candidates.firstOrNull { countMajors(grinder, it) <= MAX_LABELS } ?: maxOf(candidates.last(), span)
  }

  private fun countMajors(grinder: Grinder, step: Double): Int =
    (floor((grinder.scaleMax + EPS) / step) - ceil((grinder.scaleMin - EPS) / step)).toInt() + 1

  private fun label(value: Double): String {
    val rounded = value.roundToLong()
    return if (abs(value - rounded) < 1e-6) rounded.toString() else roundTo6(value).toString()
  }
}
