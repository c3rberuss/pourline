package dev.dotscale.ui.history

import dev.dotscale.coffee.PickedCoffee
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
    val details = BrewDetails("A", null, BrewMethod.V60, null, "21", 92.0, "ok", rating = 4)
    val form = BrewEditForm.from(details)
    assertEquals("92", form.waterTemp)
    assertEquals(details, form.toDetails())
    assertEquals("92.5", BrewEditForm.from(details.copy(waterTempC = 92.5)).waterTemp)
  }

  @Test
  fun `the rating counts as a change and travels to the details`() {
    val initial = BrewEditForm(name = "A")
    assertTrue(initial.copy(rating = 4).isDirty(initial))
    assertFalse(initial.copy(rating = null).isDirty(initial))
    assertEquals(4, initial.copy(rating = 4).toDetails().rating)
    assertNull(initial.toDetails().rating)
  }

  @Test
  fun `the coffee never travels in the details`() {
    val form = BrewEditForm(coffee = CoffeeChoice.Legacy("Etiopía"))
    assertNull(form.toDetails().coffee)
    assertNull(BrewEditForm(coffee = CoffeeChoice.Linked(picked(1))).toDetails().coffee)
  }

  @Test
  fun `an untouched legacy coffee is not a change and writes no link`() {
    val legacy = CoffeeChoice.Legacy("Etiopía")
    val initial = BrewEditForm.from(BrewDetails(), legacy)
    assertFalse(initial.copy().isDirty(initial))
    assertFalse(legacy.needsLinkWrite(initial.coffee))
  }

  @Test
  fun `choosing or clearing a coffee is a change that writes the link`() {
    val none = CoffeeChoice.None
    val legacy = CoffeeChoice.Legacy("Etiopía")
    val one = CoffeeChoice.Linked(picked(1))
    val two = CoffeeChoice.Linked(picked(2))
    assertTrue(one.needsLinkWrite(none))
    assertTrue(one.needsLinkWrite(legacy))
    assertTrue(two.needsLinkWrite(one))
    assertTrue(none.needsLinkWrite(legacy))
    assertTrue(none.needsLinkWrite(one))
    assertFalse(none.needsLinkWrite(none))
    assertFalse(one.needsLinkWrite(CoffeeChoice.Linked(picked(1, name = "Renombrado"))))
    assertEquals(1L, one.linkedId)
    assertNull(legacy.linkedId)
    assertNull(none.linkedId)
  }

  private fun picked(id: Long, name: String = "Café $id") = PickedCoffee(id, name, roaster = null, roastDate = null)
}
