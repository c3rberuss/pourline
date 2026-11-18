package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.history.BrewMethod
import kotlin.math.abs

/** Por qué un resultado no es exacto. */
enum class ConversionReason {
  /** Se calculó con los pares de calibración del usuario ("según tu calibración"). */
  CALIBRATED,

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

/** La conversión de un ajuste entre molinos por pares de calibración y curvas de grosor (§3.3). Todo es aritmética local. */
object GrindConverter {
  /**
   * El ajuste equivalente de [value] (en unidades base de [from]) en [to], sin depender del método de la receta.
   * [pairs] son los pares de calibración entre los dos molinos, orientados de [from] a [to].
   *
   * 1. Mismo molino: `Exact(value)`.
   * 2. Con 2 o más pares: interpolación por tramos sobre ellos (`CALIBRATED`, o el valor del par si coincide).
   * 3. Con 1 par: la conversión por curvas desplazada para que pase por el par (`CALIBRATED`); sin alguna curva,
   *    `NoEquivalent`.
   * 4. Sin pares: la conversión por curvas de grosor (`ANCHORS`).
   * 5. Si no hay curvas, `NoEquivalent`.
   *
   * El resultado se redondea al paso del destino (`ROUNDED`) y se limita a su escala (`AT_LIMIT`).
   */
  fun convert(value: Double, from: Grinder, to: Grinder, pairs: List<CalibrationPoint>): Conversion {
    if (from.id != 0L && from.id == to.id) return Conversion.Exact(value)

    val calc = when {
      pairs.size >= 2 -> byPairs(value, pairs)
      pairs.size == 1 -> byOnePair(value, from, to, pairs.first())
      else -> byCurves(value, from, to)?.let { Calc(it, ConversionReason.ANCHORS) }
    } ?: return Conversion.NoEquivalent
    return finish(calc, to)
  }

  /** El valor sin redondear y la razón que lo explica; `null` si coincide con un par (el valor del usuario, sin razón). */
  private class Calc(val raw: Double, val reason: ConversionReason?)

  private fun byPairs(value: Double, pairs: List<CalibrationPoint>): Calc? {
    val sorted = pairs.sortedBy { it.fromValue }
    sorted.firstOrNull { abs(value - it.fromValue) < EPS }?.let { return Calc(it.toValue, null) }
    return interpolate(sorted.map { it.fromValue to it.toValue }, value)?.let { Calc(it, ConversionReason.CALIBRATED) }
  }

  private fun byOnePair(value: Double, from: Grinder, to: Grinder, pair: CalibrationPoint): Calc? {
    // Sin la curva de alguno de los dos no hay equivalencia, ni siquiera en el valor del par.
    val atPair = byCurves(pair.fromValue, from, to) ?: return null
    if (abs(value - pair.fromValue) < EPS) return Calc(pair.toValue, null)
    val shifted = byCurves(value, from, to) ?: return null
    return Calc(shifted + (pair.toValue - atPair), ConversionReason.CALIBRATED)
  }

  /** Valor de origen → nivel con la curva del origen → valor de destino con la curva del destino; `null` sin alguna curva. */
  private fun byCurves(value: Double, from: Grinder, to: Grinder): Double? {
    val fromCurve = GrindCurve.of(from) ?: return null
    val toCurve = GrindCurve.of(to) ?: return null
    return toCurve.value(fromCurve.level(value)).takeUnless { it.isNaN() }
  }

  /**
   * Redondea al paso del destino y limita a su escala, con sus razones. Un valor que viene de un par pero que el destino
   * no admite (la escala se editó después) sale `CALIBRATED` con `ROUNDED` o `AT_LIMIT`.
   */
  private fun finish(calc: Calc, to: Grinder): Conversion {
    val reasons = mutableSetOf<ConversionReason>()
    calc.reason?.let { reasons += it }
    var result = snap(calc.raw, to)
    if (abs(result - calc.raw) > 1e-6) reasons += ConversionReason.ROUNDED
    if (result < to.scaleMin - EPS || result > to.scaleMax + EPS) {
      result = clampToScale(result, to)
      reasons += ConversionReason.AT_LIMIT
    }
    if (calc.reason == null && reasons.isNotEmpty()) reasons += ConversionReason.CALIBRATED
    return if (reasons.isEmpty()) Conversion.Exact(result) else Conversion.Approximate(result, reasons)
  }

  /** El punto medio del rango de [method] en [grinder], ajustado al paso, o `null` sin rango (receta sin ajuste). */
  fun suggested(method: BrewMethod?, grinder: Grinder): Double? {
    val range = method?.let { grinder.ranges[it] } ?: return null
    return snap((range.min + range.max) / 2, grinder)
  }
}
