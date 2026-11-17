package dev.josueamaya.pourline.grinder

/** Un par de calibración orientado de un molino de origen a uno de destino (§3.3.1 de la enmienda 1). */
data class CalibrationPoint(val fromValue: Double, val toValue: Double)

/**
 * Un par guardado: [valueA] en [grinderAId] muele igual que [valueB] en [grinderBId] (unidades base). En el orden
 * canónico `grinderAId < grinderBId`, que aplica el DAO al escribir.
 */
data class GrindCalibration(val id: Long, val grinderAId: Long, val valueA: Double, val grinderBId: Long, val valueB: Double) {
  fun involves(id: Long): Boolean = id == grinderAId || id == grinderBId

  /** El otro molino del par. */
  fun otherId(id: Long): Long = if (id == grinderAId) grinderBId else grinderAId

  /** El ajuste del par en el molino [id]. */
  fun valueOf(id: Long): Double = if (id == grinderAId) valueA else valueB

  /** El par orientado desde [fromId] hacia el otro molino. */
  fun pointFrom(fromId: Long): CalibrationPoint = CalibrationPoint(valueOf(fromId), valueOf(otherId(fromId)))
}

/** Los pares que unen [fromId] y [toId], orientados de [fromId] a [toId]. */
fun List<GrindCalibration>.pointsBetween(fromId: Long, toId: Long): List<CalibrationPoint> =
  filter { it.involves(fromId) && it.involves(toId) && fromId != toId }.map { it.pointFrom(fromId) }

/** Lo que devuelve guardar un par. */
sealed interface CalibrationSaveResult {
  /** Un par nuevo. */
  data class Saved(val id: Long) : CalibrationSaveResult

  /** Había un par con el mismo valor en un lado: se actualizó. */
  data class Updated(val id: Long) : CalibrationSaveResult

  /** El par contradice a [conflicts]; no se guardó nada. */
  data class Conflict(val conflicts: List<GrindCalibration>) : CalibrationSaveResult

  /** El par no es válido; no se guardó nada. */
  data class Invalid(val errors: List<CalibrationError>) : CalibrationSaveResult
}
