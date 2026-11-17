package dev.josueamaya.pourline.grinder

import kotlin.math.floor

/** Funciones puras para descomponer, componer y mostrar un ajuste según la jerarquía de unidades de un molino. */
object GrindFormat {
  /** Separador de las partes en la forma corta. */
  const val SEPARATOR = " · "

  /**
   * Parte [value] (en unidades base) en una cantidad por unidad, de la más gruesa a la más fina. Todas son enteras
   * salvo la última, que puede llevar el decimal del paso.
   */
  fun decompose(value: Double, units: List<GrindUnitLevel>): List<Double> {
    val mult = multipliers(units)
    var rest = value
    return units.indices.map { i ->
      if (i == units.lastIndex) {
        roundTo6(rest)
      } else {
        val part = floor(rest / mult[i] + EPS)
        rest -= part * mult[i]
        part
      }
    }
  }

  /** Lo inverso de [decompose]: el valor en unidades base de [parts]. */
  fun compose(parts: List<Double>, units: List<GrindUnitLevel>): Double {
    val mult = multipliers(units)
    return roundTo6(parts.indices.sumOf { parts[it] * mult[it] })
  }

  /** Forma corta: "30 clics" en un molino de una unidad y "1 · 2 · 3" en uno compuesto. */
  fun format(value: Double, grinder: Grinder, texts: GrindTexts): String {
    if (grinder.units.size == 1) return texts.quantity(grinder.units[0].unit, value)
    val parts = decompose(value, grinder.units)
    val decimals = decimalsOf(grinder.step)
    return parts.indices.joinToString(SEPARATOR) { i ->
      texts.number(parts[i], if (i == parts.lastIndex) decimals else 0)
    }
  }

  /** Forma larga, para accesibilidad: "1 rotación, 2 números, 3 clics". */
  fun describe(value: Double, grinder: Grinder, texts: GrindTexts): String {
    val parts = decompose(value, grinder.units)
    return grinder.units.indices.joinToString(", ") { i -> texts.quantity(grinder.units[i].unit, parts[i]) }
  }

  /** "<modelo> · <ajuste>" ("Chestnut S3 · 30 clics"), o solo el modelo sin valor. */
  fun label(grinder: Grinder, value: Double?, texts: GrindTexts): String =
    if (value == null) grinder.model else grinder.model + SEPARATOR + format(value, grinder, texts)
}
