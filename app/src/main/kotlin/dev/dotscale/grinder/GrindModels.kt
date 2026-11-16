package dev.dotscale.grinder

import dev.dotscale.history.BrewMethod

/** Unidades en las que un molino expresa su ajuste. En la interfaz: Clics, Puntos, Muescas, Números, Rotaciones y Pasos. */
enum class GrindUnit { CLICKS, POINTS, NOTCHES, NUMBERS, ROTATIONS, STEPS }

/** Qué significa subir el ajuste: más grueso (lo habitual) o más fino. */
enum class GrindDirection { HIGHER_IS_COARSER, HIGHER_IS_FINER }

/** Las siete bandas de grosor de Honest Coffee Guide, de la más fina a la más gruesa. */
enum class GrindLevel { EXTRA_FINE, FINE, MEDIUM_FINE, MEDIUM, MEDIUM_COARSE, COARSE, EXTRA_COARSE }

/**
 * Una unidad de la jerarquía de un molino. [perParent] es cuántas unidades suyas forman una de la anterior (más
 * gruesa); la unidad primaria (posición 0) no lo lleva.
 */
data class GrindUnitLevel(val unit: GrindUnit, val perParent: Int? = null)

/** Rango `[min, max]` en unidades base. */
data class GrindRange(val min: Double, val max: Double)

/**
 * Un molino. Todos los valores (escala, rangos, anclas y ajustes) van en **unidades base**, las de la última unidad
 * de [units]. [units] va de la unidad más gruesa (la primaria) a la más fina (la base).
 *
 * Un molino predefinido tiene [catalogKey] y [sourceUrl] y es de solo lectura; uno propio tiene `catalogKey = null`.
 */
data class Grinder(
  val id: Long = 0,
  val catalogKey: String? = null,
  val brand: String,
  val model: String,
  val units: List<GrindUnitLevel>,
  val scaleMin: Double,
  val scaleMax: Double,
  val step: Double = 1.0,
  val direction: GrindDirection = GrindDirection.HIGHER_IS_COARSER,
  val ranges: Map<BrewMethod, GrindRange> = emptyMap(),
  val anchors: Map<GrindLevel, Double> = emptyMap(),
  val owned: Boolean = false,
  val retired: Boolean = false,
  val sourceUrl: String? = null,
  val duplicatedFromKey: String? = null,
) {
  val isPredefined: Boolean get() = catalogKey != null
  val primaryUnit: GrindUnit get() = units.first().unit
  val baseUnit: GrindUnit get() = units.last().unit
}
