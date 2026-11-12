package dev.dotscale.protocol

/**
 * CRC-16/MODBUS (poly 0xA001 reflejado, init 0xFFFF, sin XOR final).
 *
 * Es el checksum que la báscula usa al final de cada trama, en big-endian.
 */
object Crc16Modbus {
  private const val POLY = 0xA001
  private const val INIT = 0xFFFF

  fun compute(data: ByteArray, length: Int = data.size): Int {
    var crc = INIT
    for (i in 0 until length) {
      crc = crc xor (data[i].toInt() and 0xFF)
      repeat(8) {
        crc = if (crc and 1 != 0) (crc ushr 1) xor POLY else crc ushr 1
      }
    }
    return crc and 0xFFFF
  }
}
