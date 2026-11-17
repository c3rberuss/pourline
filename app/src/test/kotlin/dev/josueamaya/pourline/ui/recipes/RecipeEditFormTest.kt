package dev.josueamaya.pourline.ui.recipes

import dev.josueamaya.pourline.grinder.GrindChoice
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.Adjustment
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeDraft
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.v60Classic
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
      .updateStage(2, StageForm("", "1:75", "abc", "0"))
    val errors = form.errors()
    assertEquals(FieldError.NAME_REQUIRED, errors.name)
    assertEquals(FieldError.DOSE_INVALID, errors.dose)
    assertEquals(FieldError.TEMP_INVALID, errors.temp)
    assertEquals(FieldError.START_INVALID, errors.stages[2].start)
    assertEquals(FieldError.WATER_INVALID, errors.stages[2].water)
    assertEquals(FieldError.DURATION_INVALID, errors.stages[2].duration)
    assertFalse(errors.stages[0].any)
    assertNull(form.toRecipe())
    assertFalse(form.isValid)
  }

  @Test
  fun `an unreadable field does not cause spurious errors in its neighbours`() {
    val form = filled().updateStage(2, StageForm("", "abc", "abc", "abc"))
    val errors = form.errors()
    assertFalse(errors.stages[3].any)
    assertFalse(errors.stages[0].any)
  }

  @Test
  fun `the rules of the validator show on the right field`() {
    // La V60 tiene 5 pasos: vertido, espera, vertido, espera, vertido.
    val overlapping = filled().updateStage(2, StageForm("", "0:05", "150", "20"))
    assertEquals(FieldError.START_OVERLAPS, overlapping.errors().stages[2].start)
    val flat = filled().updateStage(2, StageForm("", "0:45", "40", "20"))
    assertEquals(FieldError.WATER_NOT_INCREASING, flat.errors().stages[2].water)
    val late = filled().updateStage(4, StageForm("", "10:00", "250", "25"))
    assertEquals(FieldError.START_TOO_LATE, late.errors().stages[4].start)
    val tooHigh = filled().updateStage(4, StageForm("", "1:30", "1001", "25"))
    assertEquals(FieldError.WATER_INVALID, tooHigh.errors().stages[4].water)
  }

  @Test
  fun `add stage starts where the previous step ends, with the last pour plus 50 g and 10 s`() {
    val added = filled().addStage()
    assertEquals(6, added.stages.size)
    // El último paso termina a 1:30 + 25 s = 1:55. Agua 250 + 50.
    assertEquals(StageForm("", "1:55", "300", "10"), added.stages.last())
    assertTrue(added.isValid)
  }

  @Test
  fun `add stage after a wait takes the water of the last pour`() {
    val form = RecipeEditForm(dose = "15", name = "x").copy(
      stages = listOf(
        StageForm(start = "0:00", water = "40", duration = "10"),
        StageForm(start = "0:10", duration = "30", type = StageType.WAIT),
      ),
    )
    assertEquals(StageForm("", "0:40", "90", "10"), form.addStage().stages.last())
  }

  @Test
  fun `changing a step to a wait or a swirl empties the water and back to a pour proposes the last pour plus 50 g`() {
    val form = filled() // vertido 40, espera, vertido 150, espera, vertido 250
    val asWait = form.setStageType(2, StageType.WAIT)
    assertEquals(StageType.WAIT, asWait.stages[2].type)
    assertEquals("", asWait.stages[2].water)
    assertNull(asWait.errors().stages[2].water)
    val asSwirl = form.setStageType(2, StageType.SWIRL)
    assertEquals("", asSwirl.stages[2].water)
    // De vuelta a vertido: el último vertido anterior (40 g) más 50 g.
    assertEquals("90", asWait.setStageType(2, StageType.POUR).stages[2].water)
    assertEquals(StageType.POUR, asWait.setStageType(2, StageType.POUR).stages[2].type)
  }

  @Test
  fun `changing the type keeps the other fields and an unchanged type touches nothing`() {
    val form = filled()
    val changed = form.setStageType(0, StageType.SWIRL)
    assertEquals(form.stages[0].copy(type = StageType.SWIRL, water = ""), changed.stages[0])
    assertEquals(form, form.setStageType(0, StageType.POUR))
    assertEquals(form, form.setStageType(99, StageType.WAIT))
  }

  @Test
  fun `turning the first step into a pour proposes 50 g when no pour precedes it`() {
    val form = RecipeEditForm(dose = "15", name = "x").copy(
      stages = listOf(StageForm(start = "0:00", duration = "5", type = StageType.SWIRL), StageForm(start = "0:05", water = "40", duration = "10")),
    )
    assertEquals("50", form.setStageType(0, StageType.POUR).stages[0].water)
  }

  @Test
  fun `turning the only pour into a wait reports NO_POUR`() {
    val form = RecipeEditForm(dose = "15", name = "x").setStageType(0, StageType.WAIT)
    assertEquals(FieldError.NO_POUR, form.errors().general)
    assertFalse(form.isValid)
  }

  @Test
  fun `add stage is disabled with twenty stages`() {
    var form = RecipeEditForm()
    repeat(19) { form = form.addStage() }
    assertEquals(20, form.stages.size)
    assertFalse(form.canAddStage)
    assertEquals(form, form.addStage())
    assertEquals(FieldError.STAGE_COUNT.takeIf { false }, form.errors().general)
  }

  @Test
  fun `remove stage is disabled with a single stage`() {
    val one = RecipeEditForm()
    assertFalse(one.canRemoveStage)
    assertEquals(one, one.removeStage(0))
    val five = filled()
    assertEquals(4, five.removeStage(1).stages.size)
    assertEquals(listOf("Bloom", "2.º vertido", "", "3.er vertido"), five.removeStage(1).stages.map { it.name })
  }

  @Test
  fun `localized names are kept only if the name and the stage names do not change`() {
    val initial = filled()
    assertTrue(initial.keepsLocalizedNames(initial, wasLocalized = true))
    // Solo cantidades.
    val quantities = initial.copy(dose = "16", grind = GrindChoice.Live(1, 20.0)).updateStage(0, initial.stages[0].copy(water = "45", start = "0:02"))
    assertTrue(quantities.keepsLocalizedNames(initial, wasLocalized = true))
    // El nombre de la receta.
    assertFalse(initial.copy(name = "Mi V60").keepsLocalizedNames(initial, wasLocalized = true))
    // El nombre de una etapa.
    assertFalse(initial.updateStage(1, initial.stages[1].copy(name = "Segundo")).keepsLocalizedNames(initial, wasLocalized = true))
    // Una etapa de más o de menos.
    assertFalse(initial.addStage().keepsLocalizedNames(initial, wasLocalized = true))
    assertFalse(initial.removeStage(2).keepsLocalizedNames(initial, wasLocalized = true))
    // El tipo y la instrucción de un paso.
    assertFalse(initial.updateStage(0, initial.stages[0].copy(type = StageType.SWIRL)).keepsLocalizedNames(initial, wasLocalized = true))
    assertFalse(initial.updateStage(0, initial.stages[0].copy(instruction = "Otra")).keepsLocalizedNames(initial, wasLocalized = true))
    // Si ya no lo era, no vuelve a serlo.
    assertFalse(initial.keepsLocalizedNames(initial, wasLocalized = false))
  }

  @Test
  fun `type and instruction survive the round trip and a wait leaves the water empty`() {
    val form = filled()
    assertEquals(listOf(StageType.POUR, StageType.WAIT, StageType.POUR, StageType.WAIT, StageType.POUR), form.stages.map { it.type })
    assertEquals(listOf("40", "", "150", "", "250"), form.stages.map { it.water })
    assertEquals(listOf("i1", "i2", "i3", "i4", "i5"), form.stages.map { it.instruction })
    val recipe = form.toRecipe()!!
    assertEquals(listOf("i1", "i2", "i3", "i4", "i5"), recipe.stages.map { it.instruction })
    assertEquals(listOf(40.0, 40.0, 150.0, 150.0, 250.0), recipe.stages.map { it.targetWaterG })
  }

  @Test
  fun `a wait with the water field empty is valid and the water is not asked`() {
    val form = RecipeEditForm(dose = "15", name = "x").let {
      it.copy(stages = listOf(StageForm(start = "0:00", water = "40", duration = "10"), StageForm(start = "0:10", water = "", duration = "30", type = StageType.WAIT)))
    }
    assertNull(form.errors().stages[1].water)
    assertTrue(form.isValid)
    assertEquals(40.0, form.toRecipe()!!.stages[1].targetWaterG, 0.0)
  }

  @Test
  fun `changing a pour to a swirl derives its water from the previous pour`() {
    val form = RecipeEditForm(dose = "15", name = "x").let {
      it.copy(stages = listOf(StageForm(start = "0:00", water = "40", duration = "10"), StageForm(start = "0:10", water = "999", duration = "5", type = StageType.SWIRL)))
    }
    assertNull(form.errors().stages[1].water)
    assertEquals(40.0, form.toRecipe()!!.stages[1].targetWaterG, 0.0)
  }

  @Test
  fun `a form with no pour reports NO_POUR and an instruction of 201 characters reports its field`() {
    val noPour = RecipeEditForm(dose = "15", name = "x").copy(
      stages = listOf(StageForm(start = "0:00", duration = "10", type = StageType.WAIT)),
    )
    assertEquals(FieldError.NO_POUR, noPour.errors().general)
    assertNull(noPour.toRecipe())
    val long = filled().let { it.updateStage(0, it.stages[0].copy(instruction = "a".repeat(201))) }
    assertEquals(FieldError.INSTRUCTION_TOO_LONG, long.errors().stages[0].instruction)
    val ok = filled().let { it.updateStage(0, it.stages[0].copy(instruction = "a".repeat(200))) }
    assertNull(ok.errors().stages[0].instruction)
  }

  @Test
  fun `dirty compares with the initial form`() {
    val initial = filled()
    assertFalse(initial.isDirty(initial))
    assertTrue(initial.copy(notes = "x").isDirty(initial))
  }

  @Test
  fun `texts are trimmed and empty ones become null, other keeps its text only with method other`() {
    val form = filled().copy(name = "  Mi receta ", grind = GrindChoice.None, notes = " Vierte despacio ", method = BrewMethod.V60, methodOther = "x")
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
    assertNull(form.updateStage(4, form.stages[4].copy(water = "x")).ratio())
    // Un paso de espera al final no cuenta: el total es el del último vertido.
    val endsInWait = form.addStage().let { it.updateStage(5, it.stages[5].copy(type = StageType.WAIT, water = "")) }
    assertEquals(250.0, endsInWait.totalWaterG()!!, 0.0)
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

  // --- Molienda (SPEC-006) ---

  private val orphan = GrindSetting(null, 30.0, "Chestnut S3 · 30 clics")

  @Test
  fun `choosing a grinder leaves the setting null and the same grinder keeps it`() {
    val form = filled().copy(grind = GrindChoice.Live(1, 20.0))
    assertEquals(GrindChoice.Live(2, null), form.withGrinder(2).grind)
    assertEquals(GrindChoice.Live(1, 20.0), form.withGrinder(1).grind)
    assertEquals(GrindChoice.Live(3, null), filled().withGrinder(3).grind)
  }

  @Test
  fun `no grinder clears the choice`() {
    assertEquals(GrindChoice.None, filled().copy(grind = GrindChoice.Live(1, 20.0)).withGrinder(null).grind)
  }

  @Test
  fun `an orphan is kept until a grinder is chosen and then disappears`() {
    val form = RecipeEditForm.from(v60Classic().copy(grind = orphan))
    assertEquals(GrindChoice.Orphan(orphan), form.grind)
    assertEquals(orphan, form.toRecipe(id = 1)!!.grind)
    assertEquals(GrindChoice.Live(4, null), form.withGrinder(4).grind)
    assertEquals(GrindChoice.None, form.withGrinder(null).grind)
    // Fijar un valor sin molino vivo no hace nada.
    assertEquals(form, form.withGrindValue(10.0))
  }

  @Test
  fun `the grind value is set on the live grinder and round trips with its id`() {
    val form = filled().withGrinder(5).withGrindValue(21.0)
    assertEquals(GrindChoice.Live(5, 21.0), form.grind)
    assertEquals(GrindSetting(5, 21.0, null), form.toRecipe(id = 1)!!.grind)
    assertEquals(GrindChoice.Live(5, 21.0), RecipeEditForm.from(form.toRecipe(id = 1)!!.copy(grind = GrindSetting(5, 21.0, "x"))).grind)
  }
}
