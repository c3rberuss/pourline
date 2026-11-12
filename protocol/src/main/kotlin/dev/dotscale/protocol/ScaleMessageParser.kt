package dev.dotscale.protocol

import dev.dotscale.protocol.ScaleMessage as Msg

/**
 * Convierte tramas en [ScaleMessage]. Port de `Scale2025DataParser` de la app oficial.
 *
 * Guarda la unidad de peso actual porque la escala del valor de peso depende de ella
 * (gramos ×10, onzas ×100). Se actualiza sola al recibir [Cmd.WEIGHT_UNIT].
 */
class ScaleMessageParser(var unit: WeightUnit = WeightUnit.GRAMS) {

  fun parse(frame: Frame): Msg {
    val p = frame.payload
    if (frame.op == Op.WRITE) {
      return Msg.Ack(frame.cmd, success = p.isNotEmpty() && p.u8(0) == 1)
    }
    val message = when (frame.cmd) {
      Cmd.WEIGHT -> parseWeight(p)
      Cmd.TIMER -> p.firstOrNull()?.let { Msg.Timer(TimerStatus.fromCode(p.u8(0))) }
      Cmd.POWDER_WATER_RATIO -> if (p.size == 2) Msg.PowderWaterRatio(p.u16(0)) else null
      Cmd.RATIO_CONTROL -> if (p.size == 5) Msg.RatioControl(p.u8(0), p.s32(1)) else null
      Cmd.BATTERY -> if (p.size >= 2) Msg.Battery(percent = p.u8(1).coerceIn(0, 100), status = p.u8(0)) else null
      Cmd.WEIGHT_UNIT -> p.firstOrNull()?.let {
        Msg.UnitChanged(WeightUnit.fromCode(p.u8(0))).also { unit = it.unit }
      }
      Cmd.SOUND -> p.firstOrNull()?.let { Msg.Sound(p.u8(0) != 0) }
      Cmd.BREW_MODE -> parseBrewMode(p)
      Cmd.ESPRESSO_SATURATION -> parseSaturation(p)
      Cmd.POWER_OFF -> p.firstOrNull()?.let { Msg.PowerOff(p.u8(0)) }
      Cmd.DEVICE_NAME -> Msg.DeviceName(p.decodeToString())
      Cmd.TARE -> Msg.Tared(p.size == 1 && p.u8(0) == 1)
      Cmd.FIRMWARE_VERSION -> Msg.FirmwareVersion(p.decodeToString())
      Cmd.SERIAL_NUMBER -> if (p.isNotEmpty()) Msg.SerialNumber(p.decodeToString()) else null
      Cmd.MODEL -> if (p.isNotEmpty()) parseModel(p) else null
      Cmd.STANDBY_TIME -> if (p.size >= 2) Msg.StandbyTime(p.u16(0), if (p.size >= 4) p.u16(2) else null) else null
      Cmd.SENSITIVITY -> when {
        p.size == 1 -> Msg.Sensitivity(p.u8(0))
        p.size >= 2 -> Msg.Sensitivity(p.u8(1))
        else -> null
      }
      Cmd.PRECISION -> p.firstOrNull()?.let { Msg.Precision(p.u8(0)) }
      Cmd.FACTORY_RESET -> p.firstOrNull()?.let { Msg.FactoryReset(p.u8(0)) }
      Cmd.FORGET_DEVICE -> p.firstOrNull()?.let { Msg.ForgetDevice(p.u8(0)) }
      Cmd.BRIGHTNESS -> p.firstOrNull()?.let { p.u8(0) }?.takeIf { it <= 100 }?.let { Msg.Brightness(it) }
      else -> null
    }
    return message ?: Msg.Unknown(frame)
  }

  private fun parseWeight(p: ByteArray): Msg? {
    if (p.size < 8) return null
    return Msg.Weight(
      rawWeight = p.s32(0),
      rawFlow = p.s16(4),
      seconds = p.u16(6),
      unit = unit,
      outOfRange = p.size >= 9 && p.u8(8) == 1,
    )
  }

  private fun parseBrewMode(p: ByteArray): Msg? {
    if (p.size < 2) return null
    val mode = BrewMode.fromCode(p.u8(0))
    if (mode == BrewMode.UNKNOWN) return null
    return Msg.BrewModeStage(mode, stage = p.u8(1), parameter = if (p.size >= 6) p.s32(2) else 0)
  }

  private fun parseSaturation(p: ByteArray): Msg? {
    if (p.size < 3) return null
    val stage = p.u8(0)
    val seconds = p.u16(1)
    return if (stage in 1..3 && seconds <= 599) Msg.EspressoSaturation(stage, seconds) else null
  }

  private fun parseModel(p: ByteArray): Msg {
    val name = String(p, Charsets.US_ASCII).replace("\u0000", "").trim()
    return Msg.Model(ScaleModel.fromFirmwareName(name), name)
  }
}
