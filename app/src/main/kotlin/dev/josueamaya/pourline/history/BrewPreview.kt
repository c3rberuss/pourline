package dev.josueamaya.pourline.history

import dev.josueamaya.pourline.brew.BrewSample
import kotlin.math.roundToInt

/** Minicurva decorativa de una preparación: el agua acumulada en 48 tramos (Kotlin puro). */
object BrewPreview {
  const val SIZE = 48

  /**
   * Máximo del agua de cada uno de los [SIZE] tramos iguales de `[0, totalTimeMs]`, normalizado
   * a 0-255 respecto a [totalWaterG]. Las muestras posteriores a `totalTimeMs` caen en el último
   * tramo. Un tramo sin muestras válidas repite el valor anterior (0 al principio).
   */
  fun compute(samples: List<BrewSample>, totalTimeMs: Long, totalWaterG: Double): ByteArray {
    val result = ByteArray(SIZE)
    if (totalWaterG <= 0.0) return result
    val maxima = DoubleArray(SIZE) { Double.NaN }
    for (sample in samples) {
      if (!sample.valid) continue
      val bin = if (totalTimeMs <= 0) 0 else (sample.tMs.coerceAtLeast(0) * SIZE / totalTimeMs).coerceIn(0, SIZE - 1L).toInt()
      if (maxima[bin].isNaN() || sample.waterG > maxima[bin]) maxima[bin] = sample.waterG
    }
    var previous = 0.0
    for (i in 0 until SIZE) {
      if (!maxima[i].isNaN()) previous = maxima[i]
      result[i] = (previous / totalWaterG * 255).roundToInt().coerceIn(0, 255).toByte()
    }
    return result
  }

  /** Valores de 0 a 1 para dibujar la línea. */
  fun decode(bytes: ByteArray): List<Float> = bytes.map { (it.toInt() and 0xFF) / 255f }
}
