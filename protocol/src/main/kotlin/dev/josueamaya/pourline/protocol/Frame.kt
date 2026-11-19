package dev.josueamaya.pourline.protocol

/**
 * Trama del protocolo BLE "2025" de TIMEMORE (DOT, BASIC3, ESPRO).
 *
 * ```
 * | A5 5A | op | cmd | len (u16 BE) | payload (len bytes) | CRC16-MODBUS (u16 BE) |
 * ```
 * El CRC se calcula sobre todos los bytes anteriores (cabecera incluida).
 */
class Frame(
  val op: Int,
  val cmd: Int,
  val payload: ByteArray = ByteArray(0),
  /** Solo para tramas recibidas: si el CRC coincide. La DOT puede mandar CRC inválido. */
  val crcValid: Boolean = true,
) {
  fun encode(): ByteArray {
    val out = ByteArray(HEADER_SIZE + payload.size + CRC_SIZE)
    out[0] = MAGIC_0.toByte()
    out[1] = MAGIC_1.toByte()
    out[2] = op.toByte()
    out[3] = cmd.toByte()
    out[4] = (payload.size ushr 8).toByte()
    out[5] = payload.size.toByte()
    payload.copyInto(out, HEADER_SIZE)
    val crc = Crc16Modbus.compute(out, HEADER_SIZE + payload.size)
    out[out.size - 2] = (crc ushr 8).toByte()
    out[out.size - 1] = crc.toByte()
    return out
  }

  override fun equals(other: Any?): Boolean =
    other is Frame && op == other.op && cmd == other.cmd && payload.contentEquals(other.payload)

  override fun hashCode(): Int = 31 * (31 * op + cmd) + payload.contentHashCode()

  override fun toString(): String =
    "Frame(op=0x%02X, cmd=0x%02X, payload=%s, crcValid=%s)".format(op, cmd, payload.toHex(), crcValid)

  companion object {
    const val MAGIC_0 = 0xA5
    const val MAGIC_1 = 0x5A
    const val HEADER_SIZE = 6
    const val CRC_SIZE = 2
    const val MAX_PAYLOAD = 1024
  }
}

/** Tipo de operación (byte 2 de la trama). */
object Op {
  /** Notificación espontánea de la báscula. Supuesto: la app original no la nombra. */
  const val NOTIFY = 0x01

  /** App → báscula: pedir un valor (payload vacío). Báscula → app: respuesta con el valor. */
  const val READ = 0x02

  /** App → báscula: escribir un valor. Báscula → app: ACK con 1 byte (1 = OK). */
  const val WRITE = 0x03
}

/**
 * Comandos (byte 3 de la trama), extraídos de `Scale2025DataParser` y `Ble2025DeviceUtils`
 * de la app oficial v2.6.0.
 *
 * Ojo: `Commands$Scales` de la app original pertenece al protocolo antiguo (TES08) y NO aplica aquí.
 */
object Cmd {
  /** Peso en vivo. Ver [ScaleMessage.Weight]. */
  const val WEIGHT = 0x01

  /** Temporizador. Escritura: 1 = iniciar, 2 = pausar, 3 = reiniciar. Lectura: mismo código de estado. */
  const val TIMER = 0x02

  /** Ratio café:agua (u16). Escala sin confirmar. */
  const val POWDER_WATER_RATIO = 0x03

  /** Control de dosis/ratio: `[modo, i32 peso*10]`. 0xFF = abrir, 1 = cerrar. Experimental. */
  const val RATIO_CONTROL = 0x04

  /** Batería: `[estado, porcentaje]`. */
  const val BATTERY = 0x05

  /** Unidad de peso: 0 = gramos, 1 = onzas. */
  const val WEIGHT_UNIT = 0x06

  /** Sonido de botones: 0 = apagado, 1 = encendido. */
  const val SOUND = 0x07

  /** Modo de preparación y etapa: `[modo, etapa, (i32 parámetro)]`. */
  const val BREW_MODE = 0x08

  /** Solo ESPRO: tiempo de preinfusión `[etapa 1..3, u16 segundos]`. */
  const val ESPRESSO_SATURATION = 0x09

  /** Apagado. */
  const val POWER_OFF = 0x0B

  /** Nombre del dispositivo (UTF-8). */
  const val DEVICE_NAME = 0x0C

  /** Tara. Escritura: payload `[0, 0]`. Notificación: `[1]` si se hizo tara. */
  const val TARE = 0x0D

  /** Versión de firmware (texto). */
  const val FIRMWARE_VERSION = 0x10

  /** Número de serie (texto). */
  const val SERIAL_NUMBER = 0x12

  /** Modelo: nombre de firmware ASCII, p. ej. "TES017" para la DOT. */
  const val MODEL = 0x13

  /** Auto-apagado/espera: `[u16, u16]`. */
  const val STANDBY_TIME = 0x16

  /** Sensibilidad. Escritura: `[5, nivel+1]`. */
  const val SENSITIVITY = 0x17

  /** Precisión de pantalla: 1 o 10. */
  const val PRECISION = 0x18

  /** Restablecer a valores de fábrica. */
  const val FACTORY_RESET = 0x19

  /** Olvidar el dispositivo (desvincular). */
  const val FORGET_DEVICE = 0x1A

  /** Solo ESPRO: brillo de pantalla 0..100. */
  const val BRIGHTNESS = 0x25
}

internal fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
