package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.history.BrewMethod
import kotlin.math.abs

/** Por qué un resultado no es exacto. */
enum class ConversionReason {
  /** Se calculó con las anclas de grosor, no con el rango del método. */
  ANCHORS,

  /** El valor se redondeó al paso del molino de destino. */
  ROUNDED,

  /** El valor salía de la escala del destino y se limitó al extremo. */
  AT_LIMIT,
}

sealed interface Conversion {
  data class Exact(val value: Double) : Conversion

  data class Approximate(val value: Double, val reasons: Set<ConversionReason>) : Conversion

  data object NoEquivalent : Conversion
}

/** El valor convertido, o `null` si no hay equivalencia. */
val Conversion.valueOrNull: Double?
  get() = when (this) {
    is Conversion.Exact -> value
    is Conversion.Approximate -> value
    Conversion.NoEquivalent -> null
  }

/** La conversión de un ajuste entre molinos por posición dentro del rango del método (§3.3). Todo es aritmética local. */
object GrindConverter {
  /**
   * El ajuste equivalente de [value] (en unidades base de [from]) en [to].
   *
   * 1. Mismo molino: `Exact(value)`.
   * 2. Si los dos tienen rango para [method], el mismo porcentaje de posición dentro del rango (extrapola fuera de él).
   * 3. Si no, interpola por tramos con las anclas definidas en los dos (al menos 2), con la razón `ANCHORS`.
   * 4. Si tampoco, `NoEquivalent`.
   *
   * El resultado se redondea al paso del destino (`ROUNDED`) y se limita a su escala (`AT_LIMIT`).
   */
  fun convert(value: Double, method: BrewMethod?, from: Grinder, to: Grinder): Conversion {
    if (from.id != 0L && from.id == to.id) return Conversion.Exact(value)

    val fromRange = method?.let { from.ranges[it] }
    val toRange = method?.let { to.ranges[it] }
    val usesAnchors = fromRange == null || toRange == null
    val raw = if (!usesAnchors) {
      byRange(value, fromRange!!, from.direction, toRange!!, to.direction)
    } else {
      byAnchors(value, from, to) ?: return Conversion.NoEquivalent
    }

    val reasons = mutableSetOf<ConversionReason>()
    if (usesAnchors) reasons += ConversionReason.ANCHORS
    var result = snap(raw, to)
    if (abs(result - raw) > 1e-6) reasons += ConversionReason.ROUNDED
    if (result < to.scaleMin - EPS || result > to.scaleMax + EPS) {
      result = clampToScale(result, to)
      reasons += ConversionReason.AT_LIMIT
    }
    return if (reasons.isEmpty()) Conversion.Exact(result) else Conversion.Approximate(result, reasons)
  }

  /** El punto medio del rango de [method] en [grinder], ajustado al paso, o `null` sin rango (receta sin ajuste). */
  fun suggested(method: BrewMethod?, grinder: Grinder): Double? {
    val range = method?.let { grinder.ranges[it] } ?: return null
    return snap((range.min + range.max) / 2, grinder)
  }

  /** El extremo fino de un rango: `min` si subir es más grueso y `max` si subir es más fino. */
  private fun fineEnd(range: GrindRange, direction: GrindDirection) =
    if (direction == GrindDirection.HIGHER_IS_COARSER) range.min else range.max

  private fun coarseEnd(range: GrindRange, direction: GrindDirection) =
    if (direction == GrindDirection.HIGHER_IS_COARSER) range.max else range.min

  private fun byRange(value: Double, from: GrindRange, fromDirection: GrindDirection, to: GrindRange, toDirection: GrindDirection): Double {
    val fromFine = fineEnd(from, fromDirection)
    val toFine = fineEnd(to, toDirection)
    val p = (value - fromFine) / (coarseEnd(from, fromDirection) - fromFine)
    return toFine + p * (coarseEnd(to, toDirection) - toFine)
  }

  private fun byAnchors(value: Double, from: Grinder, to: Grinder): Double? {
    val common = GrindLevel.entries.filter { it in from.anchors && it in to.anchors }
    if (common.size < 2) return null
    // Valor de origen → nivel (0..6), por tramos y extrapolando por el tramo extremo.
    val toLevel = common.map { from.anchors.getValue(it) to it.ordinal.toDouble() }.sortedBy { it.first }
    val level = interpolate(toLevel, value) ?: return null
    // Nivel → valor de destino.
    val toValue = common.map { it.ordinal.toDouble() to to.anchors.getValue(it) }
    return interpolate(toValue, level)
  }

  /** Interpola linealmente por tramos sobre [points] (ordenados por x) y extrapola por los tramos de los extremos. */
  private fun interpolate(points: List<Pair<Double, Double>>, x: Double): Double? {
    if (points.size < 2) return null
    // Con x anterior al primer punto se usa el primer tramo; con x posterior al último, el último.
    val i = points.indexOfLast { it.first <= x }.coerceIn(0, points.size - 2)
    val (x0, y0) = points[i]
    val (x1, y1) = points[i + 1]
    if (abs(x1 - x0) < EPS) return null
    return y0 + (x - x0) / (x1 - x0) * (y1 - y0)
  }
}
