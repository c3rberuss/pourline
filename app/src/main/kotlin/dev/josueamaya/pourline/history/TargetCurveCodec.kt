package dev.josueamaya.pourline.history

import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.brew.TargetPoint
import dev.josueamaya.pourline.brew.TargetStage
import dev.josueamaya.pourline.recipe.StageType
import java.io.ByteArrayOutputStream

/** El BLOB de la curva objetivo está corrupto, truncado o tiene una versión de formato desconocida. */
class TargetCurveFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Codificación compacta de una [TargetCurve] (Kotlin puro, mismo estilo que [BrewCurveCodec]).
 *
 * Formato: un byte de versión (1) y el cuerpo comprimido con `Deflater`. Cuerpo, con enteros en varint:
 * - número de puntos y, por cada uno, Δ`tMs` y Δ(`waterG`×100) en zigzag + varint.
 * - número de etapas y, por cada una, `startMs`, `endMs`, `targetWaterG`×100 (zigzag), la longitud del
 *   nombre en UTF-8 (0 = `null`) y sus bytes. Un nombre vacío se guarda como `null`.
 *
 * El formato v1 no cambió con los pasos con tipo (D21): `encode` solo escribe las etapas `POUR`, reindexadas, y
 * `decode` las lee como `POUR`. La meseta de las esperas ya está en los puntos.
 */
object TargetCurveCodec {
  private const val VERSION = 1
  private const val SCALE = 100.0
  private const val MIN_POINT_BYTES = 2
  private const val MIN_STAGE_BYTES = 4

  fun encode(curve: TargetCurve): ByteArray {
    val body = ByteArrayOutputStream(curve.points.size * 4 + curve.stages.size * 16 + 16)
    writeVarint(body, curve.points.size.toLong())
    var prevT = 0L
    var prevWater = 0L
    for (point in curve.points) {
      val water = quantize(point.waterG)
      writeSigned(body, point.tMs - prevT)
      writeSigned(body, water - prevWater)
      prevT = point.tMs
      prevWater = water
    }
    val pours = curve.stages.filter { it.type == StageType.POUR }
    writeVarint(body, pours.size.toLong())
    for (stage in pours) {
      writeVarint(body, stage.startMs)
      writeVarint(body, stage.endMs)
      writeSigned(body, quantize(stage.targetWaterG))
      val name = stage.name?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
      writeVarint(body, name.size.toLong())
      body.write(name)
    }
    return deflateBody(VERSION, body.toByteArray())
  }

  /** @throws TargetCurveFormatException si el BLOB no se puede leer. */
  fun decode(bytes: ByteArray): TargetCurve {
    if (bytes.isEmpty()) throw TargetCurveFormatException("Empty curve")
    if (bytes[0].toInt() != VERSION) throw TargetCurveFormatException("Unknown curve format version ${bytes[0]}")
    val reader = CodecReader(inflateBody(bytes, FAIL), FAIL)
    try {
      val pointCount = reader.count(MIN_POINT_BYTES)
      if (pointCount == 0) throw FAIL("No points", null)
      val points = ArrayList<TargetPoint>(pointCount)
      var t = 0L
      var water = 0L
      repeat(pointCount) { i ->
        val dt = reader.signed()
        t += dt
        water += reader.signed()
        if (t < 0 || (i > 0 && dt <= 0)) throw FAIL("Unsorted points", null)
        points += TargetPoint(t, water / SCALE)
      }
      val stageCount = reader.count(MIN_STAGE_BYTES)
      if (stageCount == 0) throw FAIL("No stages", null)
      val stages = ArrayList<TargetStage>(stageCount)
      repeat(stageCount) { i ->
        val start = reader.varint()
        val end = reader.varint()
        val target = reader.signed() / SCALE
        val nameLength = reader.varint()
        if (nameLength < 0 || nameLength > Int.MAX_VALUE) throw FAIL("Invalid name length", null)
        val name = if (nameLength == 0L) null else String(reader.bytes(nameLength.toInt()), Charsets.UTF_8)
        if (start < 0 || end < start) throw FAIL("Invalid stage", null)
        stages += TargetStage(i, name, start, end, target)
      }
      if (!reader.atEnd) throw FAIL("Trailing bytes", null)
      return TargetCurve(points, stages)
    } catch (e: IndexOutOfBoundsException) {
      throw TargetCurveFormatException("Truncated curve", e)
    }
  }

  private val FAIL: CodecFailure = { message, cause -> TargetCurveFormatException(message, cause) }

  private fun quantize(value: Double): Long = if (value.isFinite()) Math.round(value * SCALE) else 0L
}
