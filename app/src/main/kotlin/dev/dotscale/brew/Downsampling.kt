package dev.dotscale.brew

import kotlin.math.floor

/** Punto ya reducido para dibujar. `breakBefore`: hay un hueco entre el punto anterior y este. */
data class ChartPoint(val tMs: Long, val value: Double, val stability: Int?, val breakBefore: Boolean)

/**
 * Reduce las muestras a como máximo `2 × widthPx` puntos: el mínimo y el máximo de cada columna de
 * píxeles, en orden temporal. Las muestras inválidas no generan puntos; el siguiente punto válido
 * lleva `breakBefore = true`. Solo sirve para dibujar: las métricas usan todas las muestras.
 */
fun downsample(
  samples: List<BrewSample>,
  widthPx: Int,
  xTopMs: Long,
  value: (BrewSample) -> Double,
): List<ChartPoint> {
  if (widthPx <= 0 || xTopMs <= 0 || samples.isEmpty()) return emptyList()

  val out = ArrayList<ChartPoint>(minOf(samples.size, widthPx * 2))
  var column = -1
  var columnBreak = false
  var min: ChartPoint? = null
  var max: ChartPoint? = null
  var pendingBreak = false

  fun flush() {
    val lo = min ?: return
    val hi = max ?: return
    // La primera salida de la columna hereda el corte; el orden temporal lo marca `tMs`.
    val (first, second) = if (lo.tMs <= hi.tMs) lo to hi else hi to lo
    out.add(first.copy(breakBefore = columnBreak))
    if (second !== first) out.add(second.copy(breakBefore = false))
    min = null
    max = null
  }

  for (sample in samples) {
    if (!sample.valid) {
      pendingBreak = true
      continue
    }
    val col = floor(sample.tMs.toDouble() / xTopMs * widthPx).toInt().coerceIn(0, widthPx - 1)
    val point = ChartPoint(sample.tMs, value(sample), sample.stability, breakBefore = false)
    if (col != column) {
      flush()
      column = col
      columnBreak = pendingBreak && out.isNotEmpty()
      min = point
      max = point
    } else {
      if (point.value < min!!.value) min = point
      if (point.value > max!!.value) max = point
    }
    pendingBreak = false
  }
  flush()
  return out
}
