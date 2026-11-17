package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.grinder.GrinderFixtures.dos
import dev.josueamaya.pourline.grinder.GrinderFixtures.inverso
import dev.josueamaya.pourline.grinder.GrinderFixtures.uno
import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationValidatorTest {
  /** Un par Uno (A) con Dos (B). */
  private fun pair(id: Long, uno: Double, dos: Double) = GrindCalibration(id, 1, uno, 2, dos)

  private fun check(value: Double, otherValue: Double, existing: List<GrindCalibration>, editingId: Long? = null) =
    CalibrationValidator.check(uno, value, dos, otherValue, existing, editingId)

  @Test
  fun `a valid pair is ok`() {
    assertEquals(CalibrationCheck.Ok, check(20.0, 30.0, emptyList()))
    assertEquals(CalibrationCheck.Ok, check(30.0, 40.0, listOf(pair(1, 20.0, 30.0))))
  }

  @Test
  fun `the same value on one side updates that pair`() {
    val existing = pair(1, 20.0, 30.0)
    assertEquals(CalibrationCheck.Update(existing), check(20.0, 35.0, listOf(existing)))
    assertEquals(CalibrationCheck.Update(existing), check(25.0, 30.0, listOf(existing)))
  }

  @Test
  fun `the same value works from the other grinder too`() {
    val existing = pair(1, 20.0, 30.0)
    assertEquals(CalibrationCheck.Update(existing), CalibrationValidator.check(dos, 30.0, uno, 25.0, listOf(existing)))
  }

  @Test
  fun `an exact duplicate is an update`() {
    val existing = pair(1, 20.0, 30.0)
    assertEquals(CalibrationCheck.Update(existing), check(20.0, 30.0, listOf(existing)))
  }

  @Test
  fun `a pair that breaks monotonicity is a conflict listing the pair`() {
    val existing = pair(1, 20.0, 30.0)
    // Uno 25 = Dos 25 contradice Uno 20 = Dos 30: más grueso en Uno, más fino en Dos.
    assertEquals(CalibrationCheck.Conflict(listOf(existing)), check(25.0, 25.0, listOf(existing)))
  }

  @Test
  fun `a pair that contradicts several pairs lists all of them`() {
    val existing = listOf(pair(1, 10.0, 10.0), pair(2, 20.0, 20.0), pair(3, 30.0, 30.0))
    assertEquals(CalibrationCheck.Conflict(existing), check(5.0, 35.0, existing))
    // Solo contradice los dos primeros.
    assertEquals(CalibrationCheck.Conflict(existing.take(2)), check(25.0, 5.0, existing))
  }

  @Test
  fun `an update that contradicts another pair is a conflict`() {
    val first = pair(1, 20.0, 30.0)
    val second = pair(2, 30.0, 40.0)
    // El mismo valor de Uno actualiza el primero, pero Dos 45 pasa por encima del segundo (Dos 40 para Uno 30).
    assertEquals(CalibrationCheck.Conflict(listOf(second)), check(20.0, 45.0, listOf(first, second)))
  }

  @Test
  fun `editing a pair to the value of another pair is a conflict`() {
    val first = pair(1, 20.0, 30.0)
    val second = pair(2, 30.0, 40.0)
    assertEquals(CalibrationCheck.Conflict(listOf(first)), check(20.0, 45.0, listOf(first, second), editingId = 2))
  }

  @Test
  fun `the edited pair itself is ignored`() {
    val existing = pair(1, 20.0, 30.0)
    assertEquals(CalibrationCheck.Ok, check(22.0, 31.0, listOf(existing), editingId = 1))
    assertEquals(CalibrationCheck.Ok, check(20.0, 30.0, listOf(existing), editingId = 1))
  }

  @Test
  fun `the same grinder twice is invalid`() {
    assertEquals(
      CalibrationCheck.Invalid(listOf(CalibrationError.SameGrinder)),
      CalibrationValidator.check(uno, 20.0, uno, 22.0, emptyList()),
    )
  }

  @Test
  fun `a grinder that is not owned is invalid`() {
    assertEquals(
      CalibrationCheck.Invalid(listOf(CalibrationError.NotOwned(dos.id))),
      CalibrationValidator.check(uno, 20.0, dos.copy(owned = false), 30.0, emptyList()),
    )
  }

  @Test
  fun `a value outside the scale is invalid`() {
    assertEquals(CalibrationCheck.Invalid(listOf(CalibrationError.OutOfScale(uno.id))), check(45.0, 30.0, emptyList()))
    assertEquals(CalibrationCheck.Invalid(listOf(CalibrationError.OutOfScale(dos.id))), check(20.0, -1.0, emptyList()))
  }

  @Test
  fun `a value off the step is invalid`() {
    assertEquals(CalibrationCheck.Invalid(listOf(CalibrationError.OffStep(dos.id))), check(20.0, 30.25, emptyList()))
    assertEquals(CalibrationCheck.Invalid(listOf(CalibrationError.OffStep(uno.id))), check(20.5, 30.0, emptyList()))
  }

  @Test
  fun `opposite directions are consistent when coarser matches coarser`() {
    // Uno (A) con Inverso (B, subir afina): Uno 10 = Inverso 40 y Uno 30 = Inverso 10 son coherentes.
    fun inv(id: Long, uno: Double, inverso: Double) = GrindCalibration(id, 1, uno, 4, inverso)
    val existing = listOf(inv(1, 10.0, 40.0))
    assertEquals(CalibrationCheck.Ok, CalibrationValidator.check(uno, 30.0, inverso, 10.0, existing))
    // Uno 10 = Inverso 10 con Uno 30 = Inverso 40: engrosar Uno y afinar Inverso, contradictorio.
    val other = listOf(inv(2, 30.0, 40.0))
    assertEquals(CalibrationCheck.Conflict(other), CalibrationValidator.check(uno, 10.0, inverso, 10.0, other))
  }
}
