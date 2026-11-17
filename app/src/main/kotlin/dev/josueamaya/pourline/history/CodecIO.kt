package dev.josueamaya.pourline.history

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Piezas compartidas por [BrewCurveCodec] y [TargetCurveCodec] (Kotlin puro): varint, zigzag y la
 * compresión con `Deflater`/`Inflater`. Cada códec pasa la fábrica de su propia excepción para que los
 * errores sigan siendo de su tipo.
 */
internal typealias CodecFailure = (message: String, cause: Throwable?) -> Exception

internal const val MAX_BODY_BYTES = 64 * 1024 * 1024

internal fun writeVarint(out: ByteArrayOutputStream, value: Long) {
  var v = value
  while (v and 0x7FL.inv() != 0L) {
    out.write(((v and 0x7F) or 0x80).toInt())
    v = v ushr 7
  }
  out.write(v.toInt())
}

internal fun writeSigned(out: ByteArrayOutputStream, value: Long) = writeVarint(out, (value shl 1) xor (value shr 63))

/** Antepone el byte de versión y comprime el cuerpo con `Deflater`. */
internal fun deflateBody(version: Int, body: ByteArray): ByteArray {
  val out = ByteArrayOutputStream(body.size / 2 + 16)
  out.write(version)
  val deflater = Deflater(Deflater.BEST_COMPRESSION)
  try {
    deflater.setInput(body)
    deflater.finish()
    val buffer = ByteArray(4096)
    while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
  } finally {
    deflater.end()
  }
  return out.toByteArray()
}

/** Descomprime lo que hay tras el byte de versión. */
internal fun inflateBody(bytes: ByteArray, fail: CodecFailure): ByteArray {
  val inflater = Inflater()
  try {
    inflater.setInput(bytes, 1, bytes.size - 1)
    val out = ByteArrayOutputStream(bytes.size * 4)
    val buffer = ByteArray(4096)
    while (!inflater.finished()) {
      val n = inflater.inflate(buffer)
      if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) throw fail("Truncated curve", null)
      out.write(buffer, 0, n)
      if (out.size() > MAX_BODY_BYTES) throw fail("Curve too large", null)
    }
    return out.toByteArray()
  } catch (e: DataFormatException) {
    throw fail("Corrupt curve", e)
  } finally {
    inflater.end()
  }
}

internal class CodecReader(private val data: ByteArray, private val fail: CodecFailure) {
  private var pos = 0
  val atEnd: Boolean get() = pos == data.size

  fun byte(): Int = data[pos++].toInt() and 0xFF

  fun varint(): Long {
    var result = 0L
    var shift = 0
    while (true) {
      if (shift > 63) throw fail("Varint too long", null)
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
    if (n < 0 || n > (data.size - pos) / minBytes) throw fail("Invalid count $n", null)
    return n.toInt()
  }

  /** `n` bytes seguidos; falla si no quedan tantos. */
  fun bytes(n: Int): ByteArray {
    if (n < 0 || n > data.size - pos) throw fail("Truncated curve", null)
    return data.copyOfRange(pos, pos + n).also { pos += n }
  }
}
