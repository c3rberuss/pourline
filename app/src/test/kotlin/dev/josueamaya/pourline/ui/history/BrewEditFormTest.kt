package dev.josueamaya.pourline.ui.history

import dev.josueamaya.pourline.brew.PlannedAmounts
import dev.josueamaya.pourline.coffee.PickedCoffee
import dev.josueamaya.pourline.grinder.GrindChoice
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.history.BrewDetails
import dev.josueamaya.pourline.history.BrewMethod
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
      grind = GrindChoice.None,
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
    val details = BrewDetails("A", null, BrewMethod.V60, null, GrindSetting(1, 21.0, "Acme · 21 clics"), 92.0, "ok", rating = 4)
    val form = BrewEditForm.from(details)
    assertEquals("92", form.waterTemp)
    assertEquals(GrindChoice.Live(1, 21.0), form.grind)
    // La copia de texto la regenera quien conoce el molino (F6): aquí sale sin ella.
    assertEquals(details.copy(grind = GrindSetting(1, 21.0, null)), form.toDetails())
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

  // --- Molienda (SPEC-006) ---

  @Test
  fun `choosing a grinder leaves the setting null and no grinder clears it`() {
    val form = BrewEditForm(grind = GrindChoice.Live(1, 21.0))
    assertEquals(GrindChoice.Live(2, null), form.withGrinder(2).grind)
    assertEquals(GrindChoice.Live(1, 21.0), form.withGrinder(1).grind)
    assertEquals(GrindChoice.None, form.withGrinder(null).grind)
    assertNull(BrewEditForm(grind = GrindChoice.Live(1, 21.0)).withGrinder(null).toDetails().grind)
  }

  @Test
  fun `an orphan is kept as is until a grinder is chosen`() {
    val orphan = GrindSetting(null, 30.0, "Chestnut S3 · 30 clics")
    val form = BrewEditForm.from(BrewDetails(grind = orphan))
    assertEquals(GrindChoice.Orphan(orphan), form.grind)
    assertEquals(orphan, form.toDetails().grind)
    assertEquals(GrindChoice.Live(3, null), form.withGrinder(3).grind)
    assertEquals(form, form.withGrindValue(5.0))
  }

  @Test
  fun `setting a grind value on a live grinder and saving without a label`() {
    val form = BrewEditForm().withGrinder(7).withGrindValue(12.5)
    assertEquals(GrindSetting(7, 12.5, null), form.toDetails().grind)
  }

  // --- Sin báscula (SPEC-007) ---

  private val scaleless = BrewEditForm(scaleless = true, dose = "15", water = "250")

  @Test
  fun `a scale brew form ignores dose and water`() {
    val form = BrewEditForm(dose = "abc", water = "-1")
    assertTrue(form.isValid)
    assertNull(form.amounts())
    assertFalse(form.doseInvalid)
    assertFalse(form.waterInvalid)
  }

  @Test
  fun `a scaleless form is valid with a dose from 5 to 60 g and water above 0 up to 1000 g`() {
    assertTrue(scaleless.isValid)
    for (dose in listOf("5", "60", "15,5", "9.9")) assertTrue(dose, scaleless.copy(dose = dose).isValid)
    for (water in listOf("0,1", "1000", "250.5")) assertTrue(water, scaleless.copy(water = water).isValid)
  }

  @Test
  fun `an empty dose is valid and gives a null ratio`() {
    val form = scaleless.copy(dose = "")
    assertTrue(form.isValid)
    assertEquals(PlannedAmounts(doseG = null, waterG = 250.0), form.amounts())
    assertNull(form.amounts()!!.ratio)
  }

  @Test
  fun `an invalid dose or water makes the form invalid`() {
    for (dose in listOf("0", "4,9", "61", "15,55", "abc")) {
      assertFalse(dose, scaleless.copy(dose = dose).isValid)
      assertTrue(dose, scaleless.copy(dose = dose).doseInvalid)
    }
    for (water in listOf("", "0", "-5", "1000,1", "250,55", "abc")) {
      assertFalse(water, scaleless.copy(water = water).isValid)
      assertTrue(water, scaleless.copy(water = water).waterInvalid)
    }
    assertNull(scaleless.copy(dose = "61").amounts())
  }

  @Test
  fun `amounts round trip through from and amounts`() {
    val form = BrewEditForm.from(BrewDetails(), amounts = PlannedAmounts(15.5, 250.0))
    assertTrue(form.scaleless)
    assertEquals("15.5", form.dose)
    assertEquals("250", form.water)
    assertEquals(PlannedAmounts(15.5, 250.0), form.amounts())

    val noDose = BrewEditForm.from(BrewDetails(), amounts = PlannedAmounts(null, 240.0))
    assertEquals("", noDose.dose)
    assertEquals(PlannedAmounts(null, 240.0), noDose.amounts())

    assertFalse(BrewEditForm.from(BrewDetails()).scaleless)
  }

  @Test
  fun `changing dose or water makes the form dirty`() {
    assertTrue(scaleless.copy(dose = "16").isDirty(scaleless))
    assertTrue(scaleless.copy(water = "260").isDirty(scaleless))
    assertFalse(scaleless.isDirty(scaleless))
  }
}
