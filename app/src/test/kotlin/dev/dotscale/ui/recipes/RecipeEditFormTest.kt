package dev.dotscale.ui.recipes

import dev.dotscale.history.BrewMethod
import dev.dotscale.recipe.Adjustment
import dev.dotscale.recipe.Recipe
import dev.dotscale.recipe.RecipeDraft
import dev.dotscale.recipe.v60Classic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeEditFormTest {

  private fun filled() = RecipeEditForm.from(v60Classic())

  @Test
  fun `start accepts m ss and whole seconds`() {
    assertEquals(90_000L, parseStart("1:30"))
    assertEquals(90_000L, parseStart("90"))
    assertEquals(0L, parseStart("0:00"))
    assertEquals(5_000L, parseStart("0:05"))
    assertEquals(600_000L, parseStart("10:00"))
    assertNull(parseStart("1:75"))
    assertNull(parseStart("abc"))
    assertNull(parseStart(""))
    assertNull(parseStart("1:5"))
    assertNull(parseStart("1.5"))
    assertNull(parseStart("-1"))
  }

  @Test
  fun `grams accept comma or point and one decimal at most`() {
    assertEquals(16.4, parseGrams("16,4")!!, 0.0)
    assertEquals(16.4, parseGrams("16.4")!!, 0.0)
    assertEquals(150.0, parseGrams("150")!!, 0.0)
    assertNull(parseGrams("16,45"))
    assertNull(parseGrams("abc"))
    assertNull(parseGrams(""))
    assertNull(parseGrams("-5"))
  }

  @Test
  fun `the pour accepts whole seconds only`() {
    assertEquals(10_000L, parseSeconds("10"))
    assertNull(parseSeconds("10,5"))
    assertNull(parseSeconds("abc"))
  }

  @Test
  fun `a new recipe starts with one stage and no dose`() {
    val form = RecipeEditForm()
    assertEquals(listOf(StageForm(start = "0:00", water = "50", duration = "10")), form.stages)
    assertEquals("", form.dose)
  }

  @Test
  fun `a loaded recipe is valid and converts back to the same quantities`() {
    val recipe = v60Classic()
    val form = RecipeEditForm.from(recipe)
    assertTrue(form.isValid)
    val back = form.toRecipe(id = 7, builtInKey = recipe.builtInKey, localizedNames = true)!!
    assertEquals(recipe.copy(id = 7), back)
  }

  @Test
  fun `field errors are reported where they happen`() {
    val form = filled().copy(name = " ", dose = "4", waterTemp = "120")
      .updateStage(1, StageForm("", "1:75", "abc", "0"))
    val errors = form.errors()
    assertEquals(FieldError.NAME_REQUIRED, errors.name)
    assertEquals(FieldError.DOSE_INVALID, errors.dose)
    assertEquals(FieldError.TEMP_INVALID, errors.temp)
    assertEquals(FieldError.START_INVALID, errors.stages[1].start)
    assertEquals(FieldError.WATER_INVALID, errors.stages[1].water)
    assertEquals(FieldError.DURATION_INVALID, errors.stages[1].duration)
    assertFalse(errors.stages[0].any)
    assertNull(form.toRecipe())
    assertFalse(form.isValid)
  }

  @Test
  fun `an unreadable field does not cause spurious errors in its neighbours`() {
    val form = filled().updateStage(1, StageForm("", "abc", "abc", "abc"))
    val errors = form.errors()
    assertFalse(errors.stages[2].any)
    assertFalse(errors.stages[0].any)
  }

  @Test
  fun `the rules of the validator show on the right field`() {
    val overlapping = filled().updateStage(1, StageForm("", "0:05", "150", "20"))
    assertEquals(FieldError.START_OVERLAPS, overlapping.errors().stages[1].start)
    val flat = filled().updateStage(1, StageForm("", "0:45", "40", "20"))
    assertEquals(FieldError.WATER_NOT_INCREASING, flat.errors().stages[1].water)
    val late = filled().updateStage(2, StageForm("", "10:00", "250", "25"))
    assertEquals(FieldError.START_TOO_LATE, late.errors().stages[2].start)
    val tooHigh = filled().updateStage(2, StageForm("", "1:30", "1001", "25"))
    assertEquals(FieldError.WATER_INVALID, tooHigh.errors().stages[2].water)
  }

  @Test
  fun `add stage proposes the end of the previous plus 30 s, plus 50 g and 10 s`() {
    val added = filled().addStage()
    assertEquals(4, added.stages.size)
    // La 3.ª termina a 1:30 + 25 s = 1:55; +30 s → 2:25. Agua 250 + 50.
    assertEquals(StageForm("", "2:25", "300", "10"), added.stages.last())
    assertTrue(added.isValid)
  }

  @Test
  fun `add stage is disabled with ten stages`() {
    var form = RecipeEditForm()
    repeat(9) { form = form.addStage() }
    assertEquals(10, form.stages.size)
    assertFalse(form.canAddStage)
    assertEquals(form, form.addStage())
    assertEquals(FieldError.STAGE_COUNT.takeIf { false }, form.errors().general)
  }

  @Test
  fun `remove stage is disabled with a single stage`() {
    val one = RecipeEditForm()
    assertFalse(one.canRemoveStage)
    assertEquals(one, one.removeStage(0))
    val three = filled()
    assertEquals(2, three.removeStage(1).stages.size)
    assertEquals(listOf("Bloom", "3.er vertido"), three.removeStage(1).stages.map { it.name })
  }

  @Test
  fun `localized names are kept only if the name and the stage names do not change`() {
    val initial = filled()
    assertTrue(initial.keepsLocalizedNames(initial, wasLocalized = true))
    // Solo cantidades.
    val quantities = initial.copy(dose = "16", grind = "Fina").updateStage(0, initial.stages[0].copy(water = "45", start = "0:02"))
    assertTrue(quantities.keepsLocalizedNames(initial, wasLocalized = true))
    // El nombre de la receta.
    assertFalse(initial.copy(name = "Mi V60").keepsLocalizedNames(initial, wasLocalized = true))
    // El nombre de una etapa.
    assertFalse(initial.updateStage(1, initial.stages[1].copy(name = "Segundo")).keepsLocalizedNames(initial, wasLocalized = true))
    // Una etapa de más o de menos.
    assertFalse(initial.addStage().keepsLocalizedNames(initial, wasLocalized = true))
    assertFalse(initial.removeStage(2).keepsLocalizedNames(initial, wasLocalized = true))
    // Si ya no lo era, no vuelve a serlo.
    assertFalse(initial.keepsLocalizedNames(initial, wasLocalized = false))
  }

  @Test
  fun `dirty compares with the initial form`() {
    val initial = filled()
    assertFalse(initial.isDirty(initial))
    assertTrue(initial.copy(notes = "x").isDirty(initial))
  }

  @Test
  fun `texts are trimmed and empty ones become null, other keeps its text only with method other`() {
    val form = filled().copy(name = "  Mi receta ", grind = "  ", notes = " Vierte despacio ", method = BrewMethod.V60, methodOther = "x")
    val recipe = form.toRecipe()!!
    assertEquals("Mi receta", recipe.name)
    assertNull(recipe.grind)
    assertEquals("Vierte despacio", recipe.notes)
    assertNull(recipe.methodOther)
    val other = form.copy(method = BrewMethod.OTHER, methodOther = " Clever ").toRecipe()!!
    assertEquals("Clever", other.methodOther)
  }

  @Test
  fun `live water and ratio`() {
    val form = filled()
    assertEquals(250.0, form.totalWaterG()!!, 0.0)
    assertEquals(250.0 / 15, form.ratio()!!, 1e-9)
    assertNull(form.copy(dose = "").ratio())
    assertNull(form.updateStage(2, form.stages[2].copy(water = "x")).ratio())
  }

  @Test
  fun `a draft without a dose leaves the field empty`() {
    val recipe: Recipe = v60Classic().copy(doseG = 0.0, localizedNames = false, builtInKey = null)
    val form = RecipeEditForm.from(RecipeDraft(recipe, listOf(Adjustment.MissingDose)))
    assertEquals("", form.dose)
    assertEquals(FieldError.DOSE_INVALID, form.errors().dose)
    assertNotNull(RecipeEditForm.from(RecipeDraft(recipe.copy(doseG = 15.0), emptyList())).toRecipe())
  }

  @Test
  fun `temperature is optional`() {
    assertNull(filled().errors().temp)
    assertEquals(93.5, filled().copy(waterTemp = "93,5").toRecipe()!!.waterTempC!!, 0.0)
    assertNull(filled().copy(waterTemp = "").toRecipe()!!.waterTempC)
  }
}
