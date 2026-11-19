package dev.josueamaya.pourline.ui.scale

import dev.josueamaya.pourline.brew.fromGrams
import dev.josueamaya.pourline.protocol.WeightUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private const val MINUS = '−'

/** Cantidad de agua de la guía: gramos enteros u onzas con 2 decimales, con su unidad. */
internal fun formatGuideAmount(grams: Double, unit: WeightUnit): String {
  val value = fromGrams(grams, unit)
  val number = if (unit == WeightUnit.OUNCES) String.format(Locale.getDefault(), "%.2f", value) else value.roundToLong().toString()
  return "$number ${unit.symbol}"
}

/** Gramos enteros, sin el símbolo: las cadenas de la sesión sin báscula ya llevan la "g". */
internal fun guideGrams(grams: Double): String = grams.roundToLong().toString()

/**
 * Desvío con "+" o "−" (U+2212), en gramos enteros o en onzas con 2 decimales. Un desvío que se redondea
 * a cero se escribe sin signo.
 */
internal fun formatDeviation(grams: Double, unit: WeightUnit): String {
  val value = fromGrams(grams, unit)
  val magnitude = abs(value)
  val number = if (unit == WeightUnit.OUNCES) {
    String.format(Locale.getDefault(), "%.2f", magnitude)
  } else {
    magnitude.roundToLong().toString()
  }
  val isZero = number.all { it == '0' || it == '.' || it == ',' }
  val sign = when {
    isZero -> ""
    value > 0 -> "+"
    else -> MINUS.toString()
  }
  return "$sign$number ${unit.symbol}"
}
