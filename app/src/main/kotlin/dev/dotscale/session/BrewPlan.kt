package dev.dotscale.session

/** La dosis y el ratio de Preparar (siempre en gramos) para una preparación sin receta. */
data class BrewPlan(val doseG: Double, val ratio: Double)

/**
 * El agua objetivo del aro (D26): con receta, la de su curva (ya escalada por el factor); sin receta, la dosis
 * capturada (o, si se saltó, la de [plan]) por el ratio.
 */
fun targetWaterG(target: ActiveTarget?, capturedDoseG: Double?, plan: BrewPlan): Double =
  target?.curve?.finalWaterG ?: ((capturedDoseG ?: plan.doseG) * plan.ratio)
