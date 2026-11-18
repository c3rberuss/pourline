package dev.josueamaya.pourline.grinder

import kotlin.math.abs

/** Por qué un par no se puede guardar. */
sealed interface CalibrationError {
  /** Los dos molinos son el mismo. */
  data object SameGrinder : CalibrationError

  /** El molino [grinderId] no está en mis molinos. */
  data class NotOwned(val grinderId: Long) : CalibrationError

  /** El valor del molino [grinderId] sale de su escala. */
  data class OutOfScale(val grinderId: Long) : CalibrationError

  /** El valor del molino [grinderId] no es múltiplo de su paso. */
  data class OffStep(val grinderId: Long) : CalibrationError
}

/** El resultado de comprobar un par nuevo frente a los existentes. */
sealed interface CalibrationCheck {
  /** Un par nuevo y consistente. */
  data object Ok : CalibrationCheck

  /** Ya hay un par con el mismo valor en un lado: se actualiza [existing]. */
  data class Update(val existing: GrindCalibration) : CalibrationCheck

  /** Rompe la monotonía con [conflicts]. */
  data class Conflict(val conflicts: List<GrindCalibration>) : CalibrationCheck

  data class Invalid(val errors: List<CalibrationError>) : CalibrationCheck
}

/** Comprueba un par de calibración nuevo o editado (§3.3.1 y §3.5). Puro. */
object CalibrationValidator {
  /**
   * [existing] son los pares entre [grinder] y [other]; [editingId] es el par que se está editando, que no cuenta
   * como existente (si el valor nuevo coincide con otro par, es un conflicto y no una actualización).
   */
  fun check(
    grinder: Grinder,
    value: Double,
    other: Grinder,
    otherValue: Double,
    existing: List<GrindCalibration>,
    editingId: Long? = null,
  ): CalibrationCheck {
    val errors = invalid(grinder, value, other, otherValue)
    if (errors.isNotEmpty()) return CalibrationCheck.Invalid(errors)

    val others = existing.filter { it.id != editingId && it.involves(grinder.id) && it.involves(other.id) }
    val target = if (editingId != null) {
      null
    } else {
      others.firstOrNull { abs(it.valueOf(grinder.id) - value) < EPS } ?: others.firstOrNull { abs(it.valueOf(other.id) - otherValue) < EPS }
    }
    val candidate = Candidate(grinder, value, other, otherValue)
    val conflicts = others.filter { c ->
      // El par que se actualiza no cuenta contra sí mismo: sus valores se sustituyen por los nuevos.
      c.id != target?.id && breaks(candidate, c)
    }
    return when {
      conflicts.isNotEmpty() -> CalibrationCheck.Conflict(conflicts)
      target != null -> CalibrationCheck.Update(target)
      else -> CalibrationCheck.Ok
    }
  }

  private class Candidate(val grinder: Grinder, val value: Double, val other: Grinder, val otherValue: Double)

  private fun invalid(grinder: Grinder, value: Double, other: Grinder, otherValue: Double): List<CalibrationError> = buildList {
    if (grinder.id == other.id) add(CalibrationError.SameGrinder)
    for (g in listOf(grinder, other)) if (!g.owned) add(CalibrationError.NotOwned(g.id))
    for ((g, v) in listOf(grinder to value, other to otherValue)) {
      if (v < g.scaleMin - EPS || v > g.scaleMax + EPS) {
        add(CalibrationError.OutOfScale(g.id))
      } else if (abs(snap(v, g) - v) > 1e-6) {
        add(CalibrationError.OffStep(g.id))
      }
    }
  }

  /** La coordenada de grosor: crece de fino a grueso en los dos sentidos del molino. */
  private fun coarseness(grinder: Grinder, value: Double) = if (grinder.direction == GrindDirection.HIGHER_IS_COARSER) value else -value

  /** Dos pares son consistentes si crecen en las dos coordenadas; el producto de las diferencias ≤ 0 lo rompe. */
  private fun breaks(p: Candidate, c: GrindCalibration): Boolean {
    val dg = coarseness(p.grinder, p.value) - coarseness(p.grinder, c.valueOf(p.grinder.id))
    val dh = coarseness(p.other, p.otherValue) - coarseness(p.other, c.valueOf(p.other.id))
    return dg * dh <= 0
  }
}
