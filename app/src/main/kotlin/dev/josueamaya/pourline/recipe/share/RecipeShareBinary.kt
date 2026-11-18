package dev.josueamaya.pourline.recipe.share

import dev.josueamaya.pourline.history.BrewMethod
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** El payload binario no se puede leer: truncado, `uvar` demasiado largo, UTF-8 inválido o un valor fuera de rango. */
internal class MalformedPayload(message: String) : Exception(message)

/** Escribe las primitivas del §3.1: `uvar` (LEB128 sin signo), `svar` (zigzag) y `str` (longitud y UTF-8). */
internal class ByteWriter {
  private val out = ByteArrayOutputStream()

  /** [value] se trata como entero de 64 bits sin signo. */
  fun uvar(value: Long) {
    var rest = value
    while (rest.toULong() >= 0x80UL) {
      out.write(((rest and 0x7F) or 0x80).toInt())
      rest = rest ushr 7
    }
    out.write(rest.toInt())
  }

  fun svar(value: Long) = uvar((value shl 1) xor (value shr 63))

  fun str(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    uvar(bytes.size.toLong())
    out.write(bytes)
  }

  fun toByteArray(): ByteArray = out.toByteArray()
}

/** Lee lo que escribe [ByteWriter]. Cualquier error de formato lanza [MalformedPayload]. */
internal class ByteReader(private val bytes: ByteArray) {
  private var position = 0

  val remaining: Int get() = bytes.size - position

  /** Entero de 64 bits sin signo: puede salir negativo si pasa de `Long.MAX_VALUE`. */
  fun uvar(): Long {
    var result = 0L
    var shift = 0
    while (true) {
      if (position >= bytes.size) throw MalformedPayload("truncado")
      val b = bytes[position++].toInt() and 0xFF
      if (shift == 63 && (b and 0x7E) != 0) throw MalformedPayload("uvar de más de 64 bits")
      result = result or ((b and 0x7F).toLong() shl shift)
      if (b and 0x80 == 0) return result
      shift += 7
      if (shift > 63) throw MalformedPayload("uvar de más de diez bytes")
    }
  }

  /** Un `uvar` que tiene que caber en un `Long` positivo. */
  fun uvarNonNegative(): Long = uvar().also { if (it < 0) throw MalformedPayload("valor demasiado grande") }

  fun svar(): Long {
    val raw = uvar()
    return (raw ushr 1) xor -(raw and 1)
  }

  fun str(): String {
    val length = uvarNonNegative()
    if (length > remaining) throw MalformedPayload("cadena truncada")
    val slice = ByteBuffer.wrap(bytes, position, length.toInt())
    position += length.toInt()
    return try {
      Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(slice)
        .toString()
    } catch (_: CharacterCodingException) {
      throw MalformedPayload("UTF-8 inválido")
    }
  }
}

/** Las constantes del formato `v=1` (§3.1): bits, unidades de tiempo y códigos de método. */
internal object ShareFormat {
  const val F_METHOD = 1 shl 0
  const val F_METHOD_OTHER = 1 shl 1
  const val F_TEMP = 1 shl 2
  const val F_NOTES = 1 shl 3
  const val F_GRIND = 1 shl 4
  const val F_TRIMMED_NOTES = 1 shl 5
  const val F_TRIMMED_INSTRUCTIONS = 1 shl 6
  const val F_TRIMMED_STAGE_NAMES = 1 shl 7
  const val TIME_UNIT_SHIFT = 8
  const val FLAGS_DEFINED = 0x3FF

  const val G_CATALOG_KEY = 1 shl 0
  const val G_BRAND_MODEL = 1 shl 1
  const val G_VALUE = 1 shl 2
  const val G_LABEL = 1 shl 3
  const val GFLAGS_DEFINED = 0xF

  const val S_TYPE_MASK = 0b11
  const val S_NAME = 1 shl 2
  const val S_INSTRUCTION = 1 shl 3
  const val SFLAGS_DEFINED = 0xF

  /** La unidad de tiempo: su código en `flags` y los milisegundos que vale. */
  enum class TimeUnit(val code: Int, val ms: Long) {
    SECONDS(0, 1_000),
    TENTHS(1, 100),
    MILLIS(2, 1),
    ;

    companion object {
      fun fromCode(code: Int): TimeUnit? = entries.firstOrNull { it.code == code }
    }
  }

  /** Los códigos de `BrewMethod` son fijos, no dependen del orden del enum. */
  private val methodCodes = mapOf(
    BrewMethod.V60 to 0,
    BrewMethod.KALITA_WAVE to 1,
    BrewMethod.CHEMEX to 2,
    BrewMethod.ORIGAMI to 3,
    BrewMethod.AEROPRESS to 4,
    BrewMethod.FRENCH_PRESS to 5,
    BrewMethod.ESPRESSO to 6,
    BrewMethod.COLD_BREW to 7,
    BrewMethod.MOKA to 8,
    BrewMethod.OTHER to 9,
  )

  fun methodCode(method: BrewMethod): Int = methodCodes.getValue(method)

  fun methodFromCode(code: Long): BrewMethod? = methodCodes.entries.firstOrNull { it.value.toLong() == code }?.key
}
