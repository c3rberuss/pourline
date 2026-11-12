package dev.dotscale.history

import dev.dotscale.brew.BrewSample
import dev.dotscale.brew.Pour
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/** El BLOB de la curva está corrupto, truncado o tiene una versión de formato desconocida. */
class BrewCurveFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Codificación compacta de las muestras y los vertidos de una preparación (Kotlin puro).
 *
 * Formato: un byte de versión (1) y, a continuación, el cuerpo comprimido con `Deflater`.
 * Cuerpo, con enteros en varint:
 * - número de muestras y, por cada una, Δ`tMs`, Δ(`waterG`×100) y Δ(`flowGps`×100) en zigzag +
 *   varint (se cuantizan los valores absolutos, así que el error no se acumula) y un byte con
 *   `stability` en los bits 0-6 (0-100, o 127 para `null`) y `valid` en el bit 7.
 * - número de vertidos y, por cada uno, `startMs` y `endMs` en varint y `waterG`×100 en zigzag.
 */
object BrewCurveCodec {
  private const val VERSION = 1
  private const val STABILITY_NULL = 0x7F
  private const val VALID_BIT = 0x80
  private const val SCALE = 100.0
  private const val MIN_SAMPLE_BYTES = 4
  private const val MIN_POUR_BYTES = 3
  private const val MAX_BODY_BYTES = 64 * 1024 * 1024

  fun encode(samples: List<BrewSample>, pours: List<Pour>): ByteArray {
    val body = ByteArrayOutputStream(samples.size * 6 + 16)
    writeVarint(body, samples.size.toLong())
    var prevT = 0L
    var prevWater = 0L
    var prevFlow = 0L
    for (sample in samples) {
      val water = quantize(sample.waterG)
      val flow = quantize(sample.flowGps)
      writeSigned(body, sample.tMs - prevT)
      writeSigned(body, water - prevWater)
      writeSigned(body, flow - prevFlow)
      val stability = sample.stability?.coerceIn(0, 100) ?: STABILITY_NULL
      body.write(stability or if (sample.valid) VALID_BIT else 0)
      prevT = sample.tMs
      prevWater = water
      prevFlow = flow
    }
    writeVarint(body, pours.size.toLong())
    for (pour in pours) {
      writeVarint(body, pour.startMs)
      writeVarint(body, pour.endMs)
      writeSigned(body, quantize(pour.waterG))
    }

    val out = ByteArrayOutputStream(body.size() / 2 + 16)
    out.write(VERSION)
    val deflater = Deflater(Deflater.BEST_COMPRESSION)
    try {
      deflater.setInput(body.toByteArray())
      deflater.finish()
      val buffer = ByteArray(4096)
      while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
    } finally {
      deflater.end()
    }
    return out.toByteArray()
  }

  /** @throws BrewCurveFormatException si el BLOB no se puede leer. */
  fun decode(bytes: ByteArray): Pair<List<BrewSample>, List<Pour>> {
    if (bytes.isEmpty()) throw BrewCurveFormatException("Empty curve")
    if (bytes[0].toInt() != VERSION) throw BrewCurveFormatException("Unknown curve format version ${bytes[0]}")
    val body = inflate(bytes)
    val reader = Reader(body)
    try {
      val sampleCount = reader.count(MIN_SAMPLE_BYTES)
      val samples = ArrayList<BrewSample>(sampleCount)
      var t = 0L
      var water = 0L
      var flow = 0L
      repeat(sampleCount) {
        t += reader.signed()
        water += reader.signed()
        flow += reader.signed()
        val flags = reader.byte()
        val stability = flags and 0x7F
        samples += BrewSample(
          tMs = t,
          waterG = water / SCALE,
          flowGps = flow / SCALE,
          stability = if (stability == STABILITY_NULL) null else stability.takeIf { it <= 100 }
            ?: throw BrewCurveFormatException("Invalid stability $stability"),
          valid = flags and VALID_BIT != 0,
        )
      }
      val pourCount = reader.count(MIN_POUR_BYTES)
      val pours = ArrayList<Pour>(pourCount)
      repeat(pourCount) { i ->
        val start = reader.varint()
        val end = reader.varint()
        pours += Pour(i + 1, start, end, reader.signed() / SCALE)
      }
      if (!reader.atEnd) throw BrewCurveFormatException("Trailing bytes")
      return samples to pours
    } catch (e: IndexOutOfBoundsException) {
      throw BrewCurveFormatException("Truncated curve", e)
    }
  }

  private fun quantize(value: Double): Long = if (value.isFinite()) Math.round(value * SCALE) else 0L

  private fun inflate(bytes: ByteArray): ByteArray {
    val inflater = Inflater()
    try {
      inflater.setInput(bytes, 1, bytes.size - 1)
      val out = ByteArrayOutputStream(bytes.size * 4)
      val buffer = ByteArray(4096)
      while (!inflater.finished()) {
        val n = inflater.inflate(buffer)
        if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
          throw BrewCurveFormatException("Truncated curve")
        }
        out.write(buffer, 0, n)
        if (out.size() > MAX_BODY_BYTES) throw BrewCurveFormatException("Curve too large")
      }
      return out.toByteArray()
    } catch (e: DataFormatException) {
      throw BrewCurveFormatException("Corrupt curve", e)
    } finally {
      inflater.end()
    }
  }

  private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
    var v = value
    while (v and 0x7FL.inv() != 0L) {
      out.write(((v and 0x7F) or 0x80).toInt())
      v = v ushr 7
    }
    out.write(v.toInt())
  }

  private fun writeSigned(out: ByteArrayOutputStream, value: Long) = writeVarint(out, (value shl 1) xor (value shr 63))

  private class Reader(private val data: ByteArray) {
    private var pos = 0
    val atEnd: Boolean get() = pos == data.size

    fun byte(): Int = data[pos++].toInt() and 0xFF

    fun varint(): Long {
      var result = 0L
      var shift = 0
      while (true) {
        if (shift > 63) throw BrewCurveFormatException("Varint too long")
        val b = byte()
        result = result or ((b and 0x7F).toLong() shl shift)
        if (b and 0x80 == 0) return result
        shift += 7
      }
    }

    fun signed(): Long {
      val raw = varint()
      return (raw ushr 1) xor -(raw and 1)
    }

    /** Un recuento que no puede ser mayor que los bytes que quedan entre `minBytes`. */
    fun count(minBytes: Int): Int {
      val n = varint()
      if (n < 0 || n > (data.size - pos) / minBytes) throw BrewCurveFormatException("Invalid count $n")
      return n.toInt()
    }
  }
}
