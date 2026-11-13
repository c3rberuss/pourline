package dev.dotscale.ui.history

import dev.dotscale.brew.fromGrams
import dev.dotscale.history.BrewRecord

enum class CompareMetric { TOTAL_TIME, TOTAL_WATER, DOSE, RATIO, AVG_FLOW, MAX_FLOW, STABILITY, POURS }

/**
 * Una fila de la tabla de comparación. Los valores van en la unidad de A: el tiempo en ms, el agua, la dosis y
 * los flujos en la unidad guardada de A (gramos u onzas, y por segundo), y el resto sin unidad.
 * Un dato que falta es `null`, y la diferencia B−A también.
 */
data class CompareRow(val metric: CompareMetric, val a: Double?, val b: Double?) {
  val diff: Double? get() = if (a != null && b != null) b - a else null
}

/**
 * Las filas de la comparación, en el orden en que se muestran. Todo se expresa en la unidad guardada de A, aunque B
 * se guardara en otra. El número de vertidos sale de la curva, así que con una curva ilegible es `null`.
 */
fun compareRows(a: BrewRecord, b: BrewRecord): List<CompareRow> {
  val unit = a.entity.unit

  fun inUnit(grams: Double?): Double? = grams?.let { fromGrams(it, unit) }

  fun row(metric: CompareMetric, valueOf: (BrewRecord) -> Double?) = CompareRow(metric, valueOf(a), valueOf(b))

  return listOf(
    row(CompareMetric.TOTAL_TIME) { it.summary.totalTimeMs.toDouble() },
    row(CompareMetric.TOTAL_WATER) { inUnit(it.summary.totalWaterG) },
    row(CompareMetric.DOSE) { inUnit(it.entity.doseG) },
    row(CompareMetric.RATIO) { it.summary.finalRatio },
    row(CompareMetric.AVG_FLOW) { inUnit(it.summary.avgFlowGps) },
    row(CompareMetric.MAX_FLOW) { inUnit(it.summary.maxFlowGps) },
    row(CompareMetric.STABILITY) { it.summary.stabilityScore?.toDouble() },
    row(CompareMetric.POURS) { if (it.curveError) null else it.summary.pours.size.toDouble() },
  )
}
