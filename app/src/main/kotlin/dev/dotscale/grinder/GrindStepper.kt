package dev.dotscale.grinder

/** El acarreo del stepper de la introducción compuesta (§3.2). */
object GrindStepper {
  /**
   * El valor tras pulsar +1 o −1 ([delta]) en la unidad [unitIndex]. Como el valor es absoluto, el acarreo a la
   * unidad superior y el préstamo salen solos. El resultado se ajusta al paso y se limita a la escala.
   *
   * Con [value] `null` el primer toque fija [start] (el valor inicial) sin sumar: así no se parte de un valor que
   * el usuario no ha visto.
   */
  fun step(value: Double?, unitIndex: Int, delta: Int, grinder: Grinder, start: Double): Double {
    if (value == null) return clampToScale(snap(start, grinder), grinder)
    val amount = if (unitIndex == grinder.units.lastIndex) delta * grinder.step else delta * multipliers(grinder.units)[unitIndex].toDouble()
    return clampToScale(snap(value + amount, grinder), grinder)
  }

  /** Si pulsar en [unitIndex] cambiaría el valor (no está ya en el límite de la escala). */
  fun canStep(value: Double?, unitIndex: Int, delta: Int, grinder: Grinder, start: Double): Boolean =
    value == null || step(value, unitIndex, delta, grinder, start) != value
}
