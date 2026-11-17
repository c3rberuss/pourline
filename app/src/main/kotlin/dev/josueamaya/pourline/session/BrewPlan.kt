package dev.josueamaya.pourline.session

import dev.josueamaya.pourline.brew.PlannedAmounts

/** La dosis y el ratio de Preparar (siempre en gramos) para una preparación sin receta. */
data class BrewPlan(val doseG: Double, val ratio: Double)

/**
 * El agua objetivo del aro (D26): con receta, la de su curva (ya escalada por el factor); sin receta, la dosis
 * capturada (o, si se saltó, la de [plan]) por el ratio.
 */
fun targetWaterG(target: ActiveTarget?, capturedDoseG: Double?, plan: BrewPlan): Double =
  target?.curve?.finalWaterG ?: ((capturedDoseG ?: plan.doseG) * plan.ratio)

/**
 * Las cantidades de una preparación sin báscula (SPEC-007 §3.5). Con receta, la dosis escalada (o la nominal) y el agua
 * final de la curva ya escalada; sin receta, [plan]. La dosis que haya capturado la báscula no interviene.
 */
fun plannedAmounts(target: ActiveTarget?, plan: BrewPlan): PlannedAmounts =
  if (target != null) {
    PlannedAmounts(doseG = target.scaledDoseG ?: target.nominalDoseG, waterG = target.curve.finalWaterG)
  } else {
    PlannedAmounts(doseG = plan.doseG, waterG = plan.doseG * plan.ratio)
  }
