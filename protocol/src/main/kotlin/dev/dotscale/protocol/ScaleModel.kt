package dev.dotscale.protocol

/**
 * Modelos de báscula TIMEMORE.
 *
 * @property advertisementCode byte 1 de los manufacturer data del anuncio BLE.
 * @property firmwareName lo que responde el comando [Cmd.MODEL].
 * @property usesProtocol2025 si habla el protocolo implementado en este módulo.
 */
enum class ScaleModel(
  val displayName: String,
  val advertisementCode: Int?,
  val firmwareName: String?,
  val usesProtocol2025: Boolean,
) {
  DOT("Black Mirror DOT", 1, "TES017", true),
  BASIC3("Black Mirror Basic 3", 2, "TES016", true),
  ESPRO("Black Mirror ESPRO", 3, "TES015", true),
  OLD_DOUBLE("Black Mirror (doble, antigua)", null, "TES08", false),
  UNKNOWN("Desconocida", null, null, false);

  /** La app original solo valida el CRC en BASIC3/ESPRO; la DOT puede mandarlo mal. */
  val validatesCrc: Boolean get() = this == BASIC3 || this == ESPRO

  companion object {
    fun fromAdvertisementCode(code: Int): ScaleModel =
      entries.firstOrNull { it.advertisementCode == code } ?: UNKNOWN

    fun fromFirmwareName(name: String): ScaleModel =
      entries.firstOrNull { it.firmwareName == name.trim() } ?: UNKNOWN
  }
}

/**
 * Datos del anuncio BLE.
 *
 * El fabricante publica manufacturer data con company ID 0xFFFF; Android devuelve el payload
 * sin esos 2 bytes. Formato: `[tipoAnuncio (1|2), códigoModelo, versiónProtocolo?]`.
 */
data class AdvertisementInfo(
  val broadcastType: Int,
  val model: ScaleModel,
  val protocolVersion: Int?,
) {
  companion object {
    const val MANUFACTURER_ID = 0xFFFF

    fun parse(manufacturerData: ByteArray?): AdvertisementInfo? {
      if (manufacturerData == null || manufacturerData.size < 2) return null
      val type = manufacturerData.u8(0)
      if (type != 1 && type != 2) return null
      return AdvertisementInfo(
        broadcastType = type,
        model = ScaleModel.fromAdvertisementCode(manufacturerData.u8(1)),
        protocolVersion = manufacturerData.getOrNull(2)?.let { it.toInt() and 0xFF },
      )
    }
  }
}

/** UUID GATT (como String para no depender de Android). */
object ScaleGatt {
  const val SERVICE = "0000fff0-0000-1000-8000-00805f9b34fb"

  /** Notify: báscula → app. */
  const val NOTIFY_CHARACTERISTIC = "0000fff1-0000-1000-8000-00805f9b34fb"

  /** Write: app → báscula. */
  const val WRITE_CHARACTERISTIC = "0000fff2-0000-1000-8000-00805f9b34fb"

  const val CLIENT_CONFIG_DESCRIPTOR = "00002902-0000-1000-8000-00805f9b34fb"

  /** MTU que pide la app original tras el emparejamiento. */
  const val PREFERRED_MTU = 247
}
