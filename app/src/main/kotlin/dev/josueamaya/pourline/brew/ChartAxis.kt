package dev.josueamaya.pourline.brew

import dev.josueamaya.pourline.protocol.WeightUnit

/**
 * Topes de los ejes de la gráfica. Cada función devuelve el menor escalón `s` que cumple
 * `max ≤ 0,85 × s`. Como el máximo acumulado de una sesión nunca baja, el tope tampoco: no hace
 * falta guardar estado y se reinicia solo con la sesión nueva.
 */
object ChartAxis {
  private const val FILL_LIMIT_NUM = 17
  private const val FILL_LIMIT_DEN = 20
  private const val EPS = 1e-9

  private const val TIME_BASE_MS = 60_000L
  private const val TIME_STEP_MS = 30_000L

  private val WATER_G = listOf(60.0, 120.0, 200.0, 300.0, 400.0, 600.0, 800.0)
  private val WATER_OZ = listOf(2.0, 4.0, 8.0, 12.0, 16.0, 24.0, 32.0)
  private val FLOW_G = listOf(8.0, 12.0, 16.0, 20.0)
  private val FLOW_OZ = listOf(0.25, 0.5, 0.75, 1.0)

  fun timeTopMs(maxTMs: Long): Long {
    var top = TIME_BASE_MS
    while (!fits(maxTMs.toDouble(), top.toDouble())) top += TIME_STEP_MS
    return top
  }

  /** @param max agua máxima, en la unidad indicada. */
  fun waterTop(max: Double, unit: WeightUnit): Double =
    if (unit == WeightUnit.OUNCES) top(max, WATER_OZ, 8.0) else top(max, WATER_G, 200.0)

  /** @param max flujo máximo, en la unidad indicada por segundo. */
  fun flowTop(max: Double, unit: WeightUnit): Double =
    if (unit == WeightUnit.OUNCES) top(max, FLOW_OZ, 0.25) else top(max, FLOW_G, 4.0)

  private fun fits(max: Double, top: Double) = max * FILL_LIMIT_DEN <= top * FILL_LIMIT_NUM + EPS

  private fun top(max: Double, steps: List<Double>, increment: Double): Double {
    steps.firstOrNull { fits(max, it) }?.let { return it }
    var top = steps.last()
    while (!fits(max, top)) top += increment
    return top
  }
}
