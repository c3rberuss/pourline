package dev.dotscale.ui.coffees

import dev.dotscale.coffee.CoffeeDraft
import dev.dotscale.coffee.CoffeeError
import dev.dotscale.coffee.CoffeeProcess
import dev.dotscale.coffee.label.LabelFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoffeeFormTest {
  private val today = LocalDate.of(2026, 11, 13)

  @Test
  fun `the OCR fills empty fields and marks them`() {
    val result = CoffeeForm().applyOcr(
      LabelFields(name = "Guji", roastDate = LocalDate.of(2026, 10, 12), process = CoffeeProcess.NATURAL),
    )
    assertEquals("Guji", result.form.name)
    assertEquals(LocalDate.of(2026, 10, 12), result.form.roastDate)
    assertEquals(CoffeeProcess.NATURAL, result.form.process)
    assertEquals(setOf(CoffeeField.NAME, CoffeeField.ROAST_DATE, CoffeeField.PROCESS), result.filled)
    assertEquals(result.filled, result.form.ocrFilled)
  }

  @Test
  fun `the OCR never overwrites what was typed`() {
    val form = CoffeeForm(name = "Mi café", roaster = "Yo")
    val result = form.applyOcr(LabelFields(name = "Guji", roaster = "Otro", originCountry = "Etiopía"))
    assertEquals("Mi café", result.form.name)
    assertEquals("Yo", result.form.roaster)
    assertEquals("Etiopía", result.form.country)
    assertEquals(setOf(CoffeeField.COUNTRY), result.filled)
  }

  @Test
  fun `editing a marked field removes its mark`() {
    val form = CoffeeForm().applyOcr(LabelFields(name = "Guji", originCountry = "Etiopía")).form
    val edited = form.edit(CoffeeField.NAME) { copy(name = "Guji 2") }
    assertEquals(setOf(CoffeeField.COUNTRY), edited.ocrFilled)
  }

  @Test
  fun `a second reading only fills what is still empty`() {
    val first = CoffeeForm().applyOcr(LabelFields(name = "Guji")).form
    val second = first.applyOcr(LabelFields(name = "Otro", roaster = "Tostador"))
    assertEquals("Guji", second.form.name)
    assertEquals("Tostador", second.form.roaster)
    assertEquals(setOf(CoffeeField.ROASTER), second.filled)
    assertEquals(setOf(CoffeeField.NAME, CoffeeField.ROASTER), second.form.ocrFilled)
  }

  @Test
  fun `a single altitude fills both fields as one mark`() {
    val result = CoffeeForm().applyOcr(LabelFields(altitudeMinM = 1900, altitudeMaxM = 1900))
    assertEquals("1900", result.form.altitudeMin)
    assertEquals("1900", result.form.altitudeMax)
    assertEquals(setOf(CoffeeField.ALTITUDE_MIN), result.filled)
    val oneOnly = CoffeeForm().applyOcr(LabelFields(altitudeMinM = 1900)).form
    assertEquals("1900", oneOnly.altitudeMax)
    // Editar "hasta" quita la marca de la altitud.
    assertTrue(result.form.edit(CoffeeField.ALTITUDE_MAX) { copy(altitudeMax = "2000") }.ocrFilled.isEmpty())
  }

  @Test
  fun `OTHER fills the process and its text as a single mark`() {
    val result = CoffeeForm().applyOcr(
      LabelFields(process = CoffeeProcess.OTHER, processOther = "Fermentación láctica", originRegion = "Guji", farm = "Hambela"),
    )
    assertEquals(CoffeeProcess.OTHER, result.form.process)
    assertEquals("Fermentación láctica", result.form.processOther)
    assertEquals("Guji", result.form.region)
    assertEquals("Hambela", result.form.farm)
    assertEquals(setOf(CoffeeField.PROCESS, CoffeeField.REGION, CoffeeField.FARM), result.filled)
  }

  @Test
  fun `numbers follow D34`() {
    fun errors(form: CoffeeForm) = form.errors(today)
    val base = CoffeeForm(name = "x")
    assertTrue(errors(base.copy(price = "18,50")).isEmpty())
    assertTrue(errors(base.copy(price = "18.50")).isEmpty())
    assertEquals(CoffeeError.PRICE_INVALID, errors(base.copy(price = "18,505"))[CoffeeField.PRICE])
    assertTrue(errors(base.copy(bagWeight = "250,5")).isEmpty())
    assertEquals(CoffeeError.BAG_WEIGHT_INVALID, errors(base.copy(bagWeight = "250,55"))[CoffeeField.BAG_WEIGHT])
    assertEquals(CoffeeError.BAG_WEIGHT_INVALID, errors(base.copy(bagWeight = "0"))[CoffeeField.BAG_WEIGHT])
    assertEquals(CoffeeError.ALTITUDE_INVALID, errors(base.copy(altitudeMin = "1800,5"))[CoffeeField.ALTITUDE_MIN])
  }

  @Test
  fun `validation errors are keyed by field`() {
    val errors = CoffeeForm(
      name = " ",
      altitudeMin = "2000",
      altitudeMax = "1800",
      roastDate = today.plusDays(1),
    ).errors(today)
    assertEquals(CoffeeError.NAME_REQUIRED, errors[CoffeeField.NAME])
    assertEquals(CoffeeError.ALTITUDE_RANGE, errors[CoffeeField.ALTITUDE_MIN])
    assertEquals(CoffeeError.ROAST_DATE_FUTURE, errors[CoffeeField.ROAST_DATE])
  }

  @Test
  fun `toDraft with a single altitude field duplicates it and adds the pending variety`() {
    val draft = CoffeeForm(
      name = " Guji ",
      altitudeMax = "1900",
      varieties = listOf("Heirloom"),
      varietyText = " 74110 ",
      price = "18,5",
      bagWeight = "250",
    ).toDraft()
    assertEquals("Guji", draft.name)
    assertEquals(1900, draft.altitudeMinM)
    assertEquals(1900, draft.altitudeMaxM)
    assertEquals(listOf("Heirloom", "74110"), draft.varieties)
    assertEquals(18.5, draft.price!!, 0.0)
    assertEquals(250.0, draft.bagWeightG!!, 0.0)
  }

  @Test
  fun `dirty ignores the OCR marks`() {
    val initial = CoffeeForm(name = "Guji")
    assertFalse(initial.isDirty(initial.copy(ocrFilled = setOf(CoffeeField.NAME))))
    assertTrue(initial.copy(name = "Guji 2").isDirty(initial))
    assertTrue(initial.copy(varietyText = "x").isDirty(initial))
  }

  @Test
  fun `from a draft round trips the numbers`() {
    val draft = CoffeeDraft(name = "Guji", bagWeightG = 250.0, price = 18.5, altitudeMinM = 1800, altitudeMaxM = 2000)
    val form = CoffeeForm.from(draft)
    assertEquals("250", form.bagWeight)
    assertEquals("18.50", form.price)
    assertEquals(draft, form.toDraft())
    assertNull(CoffeeForm.from(CoffeeDraft(name = "x")).toDraft().bagWeightG)
  }
}
