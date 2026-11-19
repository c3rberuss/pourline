package dev.josueamaya.pourline.protocol

enum class WeightUnit(val code: Int, val symbol: String, val divisor: Double) {
  GRAMS(0, "g", 10.0),
  OUNCES(1, "oz", 100.0);

  companion object {
    fun fromCode(code: Int): WeightUnit = if (code == OUNCES.code) OUNCES else GRAMS
  }
}

enum class TimerStatus(val code: Int) {
  RUNNING(1),
  PAUSED(2),
  RESET(3),
  UNKNOWN(-1);

  companion object {
    fun fromCode(code: Int): TimerStatus = entries.firstOrNull { it.code == code } ?: UNKNOWN
  }
}

enum class BrewMode(val code: Int, val label: String) {
  STANDARD(1, "Estándar"),
  POUR_OVER(2, "Pour-over"),
  ESPRESSO(3, "Espresso"),
  UNKNOWN(-1, "Desconocido");

  companion object {
    fun fromCode(code: Int): BrewMode = entries.firstOrNull { it.code == code } ?: UNKNOWN
  }
}

/** Mensajes que puede mandar la báscula, ya interpretados. */
sealed interface ScaleMessage {
  /**
   * Peso en vivo (cmd 0x01). Payload:
   * `i32 peso | i16 flujo | u16 segundos | (u8 fueraDeRango)`.
   * En gramos los valores vienen ×10; en onzas, ×100.
   */
  data class Weight(
    val rawWeight: Int,
    val rawFlow: Int,
    val seconds: Int,
    val unit: WeightUnit,
    val outOfRange: Boolean,
  ) : ScaleMessage {
    val weight: Double get() = rawWeight / unit.divisor

    /** Flujo en unidad/s. */
    val flowRate: Double get() = rawFlow / unit.divisor
  }

  data class Timer(val status: TimerStatus) : ScaleMessage

  data class Battery(val percent: Int, val status: Int) : ScaleMessage

  data class UnitChanged(val unit: WeightUnit) : ScaleMessage

  data class Sound(val enabled: Boolean) : ScaleMessage

  data class BrewModeStage(val mode: BrewMode, val stage: Int, val parameter: Int) : ScaleMessage {
    val description: String get() = BREW_STAGES[mode.code to stage] ?: "${mode.label} · etapa $stage"
  }

  data class PowderWaterRatio(val raw: Int) : ScaleMessage

  data class RatioControl(val mode: Int, val value: Int) : ScaleMessage

  data class EspressoSaturation(val stage: Int, val seconds: Int) : ScaleMessage

  data class Tared(val success: Boolean) : ScaleMessage

  data class DeviceName(val name: String) : ScaleMessage

  data class FirmwareVersion(val version: String) : ScaleMessage

  data class SerialNumber(val serial: String) : ScaleMessage

  data class Model(val model: ScaleModel, val firmwareName: String) : ScaleMessage

  data class StandbyTime(val first: Int, val second: Int?) : ScaleMessage

  data class Sensitivity(val level: Int) : ScaleMessage

  data class Precision(val value: Int) : ScaleMessage

  data class Brightness(val percent: Int) : ScaleMessage

  data class PowerOff(val code: Int) : ScaleMessage

  data class FactoryReset(val code: Int) : ScaleMessage

  data class ForgetDevice(val code: Int) : ScaleMessage

  /** Respuesta a una escritura (op 0x03). */
  data class Ack(val cmd: Int, val success: Boolean) : ScaleMessage

  /** Trama válida que no se sabe interpretar o cuyo payload no tiene el formato esperado. */
  data class Unknown(val frame: Frame) : ScaleMessage
}

/** Descripciones de etapas, traducidas de `BrewModeStageBean`. */
private val BREW_STAGES = mapOf(
  (1 to 0) to "Modo estándar",
  (2 to 0) to "Pour-over: inicio",
  (2 to 1) to "Pour-over: preparando dosis de café",
  (2 to 2) to "Pour-over: esperando primer vertido",
  (2 to 3) to "Pour-over: extrayendo (primeros 40 s)",
  (2 to 4) to "Pour-over: extrayendo, esperando retirar la taza",
  (2 to 5) to "Pour-over: taza retirada (pausa)",
  (3 to 0) to "Espresso: inicio",
  (3 to 1) to "Espresso: esperando primeras gotas",
  (3 to 2) to "Espresso: extrayendo",
  (3 to 3) to "Espresso: esperando fin de extracción",
  (3 to 4) to "Espresso: resultado",
  (3 to 5) to "Espresso: mostrando resultado",
)
