package dev.dotscale.ui.history

import dev.dotscale.history.BrewDetails
import dev.dotscale.history.BrewMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewEditFormTest {

  @Test
  fun `valid temperatures`() {
    assertEquals(TempResult.Valid(92.0), parseWaterTemp("92"))
    assertEquals(TempResult.Valid(92.5), parseWaterTemp("92,5"))
    assertEquals(TempResult.Valid(92.5), parseWaterTemp("92.5"))
    assertEquals(TempResult.Valid(100.0), parseWaterTemp("100"))
    assertEquals(TempResult.Valid(0.0), parseWaterTemp("0"))
    assertEquals(TempResult.Valid(93.0), parseWaterTemp(" 93 "))
  }

  @Test
  fun `invalid temperatures`() {
    listOf("100,1", "-1", "92,55", "abc", "1e2", "92.", ".5", "1000", "9 2").forEach {
      assertEquals("'$it'", TempResult.Invalid, parseWaterTemp(it))
    }
  }

  @Test
  fun `blank temperature is empty`() {
    assertEquals(TempResult.Empty, parseWaterTemp(""))
    assertEquals(TempResult.Empty, parseWaterTemp("   "))
  }

  @Test
  fun `isDirty compares with the initial form`() {
    val initial = BrewEditForm(name = "A")
    assertFalse(initial.copy().isDirty(initial))
    assertTrue(initial.copy(notes = "x").isDirty(initial))
  }

  @Test
  fun `toDetails normalizes and parses the temperature`() {
    val details = BrewEditForm(
      name = " Prueba ",
      coffee = "",
      method = BrewMethod.OTHER,
      methodOther = " Sifón ",
      grind = "",
      waterTemp = "92,5",
      notes = " nota\ncon salto ",
    ).toDetails()
    assertEquals(BrewDetails("Prueba", null, BrewMethod.OTHER, "Sifón", null, 92.5, "nota\ncon salto"), details)
  }

  @Test
  fun `an invalid or empty temperature becomes null and the form is flagged`() {
    assertNull(BrewEditForm(waterTemp = "120").toDetails().waterTempC)
    assertFalse(BrewEditForm(waterTemp = "120").isValid)
    assertNull(BrewEditForm(waterTemp = "").toDetails().waterTempC)
    assertTrue(BrewEditForm(waterTemp = "").isValid)
  }

  @Test
  fun `from and toDetails round trip`() {
    val details = BrewDetails("A", "Etiopía", BrewMethod.V60, null, "21", 92.0, "ok")
    val form = BrewEditForm.from(details)
    assertEquals("92", form.waterTemp)
    assertEquals(details, form.toDetails())
    assertEquals("92.5", BrewEditForm.from(details.copy(waterTempC = 92.5)).waterTemp)
  }
}
