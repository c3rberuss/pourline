package dev.josueamaya.pourline.protocol

import kotlin.math.roundToInt

/** Comandos app → báscula. Todos devuelven la [Frame]; usa [Frame.encode] para obtener los bytes. */
object ScaleCommands {

  fun read(cmd: Int): Frame = Frame(Op.READ, cmd)

  fun write(cmd: Int, payload: ByteArray): Frame = Frame(Op.WRITE, cmd, payload)

  /**
   * Lecturas que hace la app original al terminar de conectar (`BleService.a0()`), en el mismo
   * orden, más la unidad y el firmware que lee en otras pantallas.
   */
  val handshake: List<Frame> = listOf(
    Cmd.BATTERY,
    Cmd.MODEL,
    Cmd.BREW_MODE,
    Cmd.TIMER,
    Cmd.PRECISION,
    Cmd.DEVICE_NAME,
    Cmd.WEIGHT_UNIT,
    Cmd.FIRMWARE_VERSION,
  ).map(::read)

  fun tare(): Frame = write(Cmd.TARE, byteArrayOf(0, 0))

  fun startTimer(): Frame = write(Cmd.TIMER, byteArrayOf(TimerStatus.RUNNING.code.toByte()))

  fun pauseTimer(): Frame = write(Cmd.TIMER, byteArrayOf(TimerStatus.PAUSED.code.toByte()))

  fun resetTimer(): Frame = write(Cmd.TIMER, byteArrayOf(TimerStatus.RESET.code.toByte()))

  fun setWeightUnit(unit: WeightUnit): Frame = write(Cmd.WEIGHT_UNIT, byteArrayOf(unit.code.toByte()))

  fun setSound(enabled: Boolean): Frame = write(Cmd.SOUND, byteArrayOf(if (enabled) 1 else 0))

  fun setDeviceName(name: String): Frame = write(Cmd.DEVICE_NAME, name.encodeToByteArray())

  fun setBrewMode(mode: BrewMode, stage: Int): Frame =
    write(Cmd.BREW_MODE, byteArrayOf(mode.code.toByte(), stage.toByte()))

  /** @param level 0-based, igual que en la pantalla de ajustes de la app original. */
  fun setSensitivity(level: Int): Frame = write(Cmd.SENSITIVITY, byteArrayOf(5, (level + 1).toByte()))

  /** @param value 1 o 10, según lo que reporte [Cmd.PRECISION]. */
  fun setPrecision(value: Int): Frame = write(Cmd.PRECISION, byteArrayOf(value.toByte()))

  /**
   * Experimental: fija la dosis objetivo para el ratio café:agua (`Ble2025DeviceUtils.a(float)`).
   * Con dosis ≤ 0 se envía el modo 2, que parece servir para borrarla.
   */
  fun setTargetDose(grams: Float): Frame {
    val raw = (grams * 10).roundToInt()
    val mode: Byte = if (grams <= 0f) 2 else 4
    return write(
      Cmd.RATIO_CONTROL,
      byteArrayOf(mode, (raw ushr 24).toByte(), (raw ushr 16).toByte(), (raw ushr 8).toByte(), raw.toByte()),
    )
  }

  fun openRatioControl(): Frame = write(Cmd.RATIO_CONTROL, byteArrayOf(-1, 0, 0, 0, 0))

  fun closeRatioControl(): Frame = write(Cmd.RATIO_CONTROL, byteArrayOf(1, 0, 0, 0, 0))
}
