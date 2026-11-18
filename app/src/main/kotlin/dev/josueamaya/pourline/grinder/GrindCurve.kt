package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.history.BrewMethod
import kotlin.math.abs

/**
 * El nivel de grosor (0 = `EXTRA_FINE` … 6 = `EXTRA_COARSE`) de cada método (§3.6 de la enmienda 1). Se calcula con los
 * datos de `catalog.json` al preparar la enmienda, no en la app: `MethodLevelsTest` lo recalcula y falla si el
 * catálogo cambia. `OTHER` no tiene nivel.
 */
val METHOD_LEVELS: Map<BrewMethod, Double> = mapOf(
  BrewMethod.V60 to 2.25,
  BrewMethod.KALITA_WAVE to 2.81,
  BrewMethod.CHEMEX to 2.81,
  BrewMethod.ORIGAMI to 2.81,
  BrewMethod.AEROPRESS to 2.64,
  BrewMethod.FRENCH_PRESS to 3.93,
  BrewMethod.ESPRESSO to 0.97,
  BrewMethod.COLD_BREW to 4.33,
  BrewMethod.MOKA to 2.06,
)

/**
 * Una función monótona entre el ajuste de un molino (unidades base) y el nivel de grosor `L` (coordenada continua de 0
 * a 6), invertible y extrapolable (§3.3.2). Pura: no sabe nada de Android.
 */
sealed interface GrindCurve {
  /** El nivel de grosor de [value]. */
  fun level(value: Double): Double

  /** El ajuste del molino para el nivel [level]. */
  fun value(level: Double): Double

  /**
   * La curva de un molino con 2 o más anclas: [points] son `(valor, ordinal del nivel)` de todas ellas, ordenados por
   * valor. Interpola por tramos y extrapola con el tramo extremo.
   */
  data class Explicit(val points: List<Pair<Double, Double>>) : GrindCurve {
    private val byLevel = points.map { it.second to it.first }.sortedBy { it.first }

    override fun level(value: Double): Double = interpolate(points, value) ?: Double.NaN

    override fun value(level: Double): Double = interpolate(byLevel, level) ?: Double.NaN
  }

  /** La recta por mínimos cuadrados de los rangos de un molino: `valor = a + b × L`. */
  data class Linear(val a: Double, val b: Double) : GrindCurve {
    override fun level(value: Double): Double = (value - a) / b

    override fun value(level: Double): Double = a + b * level
  }

  companion object {
    /**
     * La curva de [grinder]: explícita con 2 o más anclas; si no, la recta de los puntos medios de sus rangos con el
     * nivel de cada método ([METHOD_LEVELS]); `null` si no hay datos suficientes o la pendiente contradice la
     * dirección. Un ancla suelta no se usa.
     */
    fun of(grinder: Grinder): GrindCurve? =
      if (grinder.anchors.size >= 2) explicit(grinder) else implicit(grinder)

    private fun explicit(grinder: Grinder): GrindCurve? {
      val points = grinder.anchors.map { (level, value) -> value to level.ordinal.toDouble() }.sortedBy { it.first }
      // Las anclas válidas son estrictamente monótonas; un molino sin validar con valores repetidos no tiene curva.
      val increasing = points.zipWithNext().all { (p, q) -> abs(q.first - p.first) > EPS }
      return if (increasing) Explicit(points) else null
    }

    private fun implicit(grinder: Grinder): GrindCurve? {
      val points = mutableListOf<Pair<Double, Double>>()
      for ((method, range) in grinder.ranges) {
        val level = METHOD_LEVELS[method] ?: continue
        val point = level to (range.min + range.max) / 2
        // Los repetidos (Kalita Wave, Chemex y Origami con el mismo rango) cuentan una vez.
        if (points.none { abs(it.first - point.first) < EPS && abs(it.second - point.second) < EPS }) points += point
      }
      if (points.map { it.first }.distinctBy { (it / EPS).toLong() }.size < 2) return null
      val meanLevel = points.sumOf { it.first } / points.size
      val meanValue = points.sumOf { it.second } / points.size
      val denominator = points.sumOf { (it.first - meanLevel) * (it.first - meanLevel) }
      if (denominator < EPS) return null
      val b = points.sumOf { (it.first - meanLevel) * (it.second - meanValue) } / denominator
      val coherent = if (grinder.direction == GrindDirection.HIGHER_IS_COARSER) b > 0 else b < 0
      return if (coherent) Linear(a = meanValue - b * meanLevel, b = b) else null
    }
  }
}
