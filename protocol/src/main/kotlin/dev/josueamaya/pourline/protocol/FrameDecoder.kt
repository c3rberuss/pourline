package dev.josueamaya.pourline.protocol

/**
 * Separa el flujo de bytes recibido por notificaciones BLE en tramas completas.
 *
 * Una notificación puede traer varias tramas o una trama partida, así que guarda en
 * un buffer los bytes pendientes hasta completar la trama. La app original no hace esto
 * y descarta las tramas incompletas.
 *
 * No es thread-safe: llámalo siempre desde el mismo hilo o corrutina.
 */
class FrameDecoder(private val maxBufferSize: Int = 4096) {
  private var buffer = ByteArray(0)

  fun feed(bytes: ByteArray): List<Frame> {
    buffer += bytes
    val frames = mutableListOf<Frame>()
    var i = 0
    while (i + 1 < buffer.size) {
      if (!isMagicAt(i)) {
        i++
        continue
      }
      if (i + Frame.HEADER_SIZE > buffer.size) break // cabecera incompleta

      val len = buffer.u16(i + 4)
      if (len > Frame.MAX_PAYLOAD) {
        i++ // longitud imposible: falso positivo de cabecera
        continue
      }
      val total = Frame.HEADER_SIZE + len + Frame.CRC_SIZE
      if (i + total > buffer.size) break // falta el resto de la trama

      val received = buffer.u16(i + total - Frame.CRC_SIZE)
      val computed = Crc16Modbus.compute(buffer.copyOfRange(i, i + total - Frame.CRC_SIZE))
      frames += Frame(
        op = buffer[i + 2].toInt() and 0xFF,
        cmd = buffer[i + 3].toInt() and 0xFF,
        payload = buffer.copyOfRange(i + Frame.HEADER_SIZE, i + Frame.HEADER_SIZE + len),
        crcValid = received == computed,
      )
      i += total
    }
    buffer = buffer.copyOfRange(i, buffer.size)
    if (buffer.size > maxBufferSize) buffer = ByteArray(0)
    return frames
  }

  fun reset() {
    buffer = ByteArray(0)
  }

  private fun isMagicAt(i: Int): Boolean =
    (buffer[i].toInt() and 0xFF) == Frame.MAGIC_0 && (buffer[i + 1].toInt() and 0xFF) == Frame.MAGIC_1
}

internal fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

internal fun ByteArray.u16(index: Int): Int = (u8(index) shl 8) or u8(index + 1)

internal fun ByteArray.s16(index: Int): Int = u16(index).toShort().toInt()

internal fun ByteArray.s32(index: Int): Int =
  (u8(index) shl 24) or (u8(index + 1) shl 16) or (u8(index + 2) shl 8) or u8(index + 3)
