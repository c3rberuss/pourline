package dev.josueamaya.pourline.recipe

import dev.josueamaya.pourline.brew.BrewSample
import dev.josueamaya.pourline.brew.Pour
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.history.BrewDetails
import dev.josueamaya.pourline.history.BrewMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeFromBrewTest {
  private val details = BrewDetails(name = "Etiopía", coffee = "Etiopía", method = BrewMethod.V60, grind = GrindSetting(3, 12.0, "Acme · 12 clics"), waterTempC = 93.0, notes = "Muy floral")

  /** Muestras cada 100 ms; el agua sube 5 g/s dentro de cada vertido y se queda quieta fuera. */
  private fun brew(pours: List<Pour>, totalMs: Long): List<BrewSample> {
    var carried = 0.0
    var previousT = 0L
    return (0..totalMs / 100).map { i ->
      val t = i * 100
      val dt = t - previousT
      previousT = t
      val inside = pours.any { t > it.startMs && t <= it.endMs }
      if (inside) carried += 5.0 * dt / 1000
      BrewSample(t, carried, if (inside) 5.0 else 0.0, null, true)
    }
  }

  private fun convert(pours: List<Pour>, totalMs: Long, dose: Double? = 15.0, samples: List<BrewSample> = brew(pours, totalMs)) =
    RecipeFromBrew.convert(
      name = "Etiopía (receta)",
      details = details,
      doseG = dose,
      samples = samples,
      pours = pours,
      totalTimeMs = totalMs,
      totalWaterG = samples.lastOrNull()?.waterG ?: 0.0,
    )

  @Test
  fun `one stage per pour with accumulated water, rounded`() {
    val pours = listOf(Pour(1, 0, 8_000, 40.0), Pour(2, 45_400, 65_300, 100.0), Pour(3, 90_600, 115_000, 120.0))
    val draft = convert(pours, 115_000)
    // Tres vertidos con dos huecos: 5 pasos, y los huecos son esperas con el agua anterior (D40).
    assertEquals(5, draft.recipe.stages.size)
    assertEquals(
      listOf(StageType.POUR, StageType.WAIT, StageType.POUR, StageType.WAIT, StageType.POUR),
      draft.recipe.stages.map { it.type },
    )
    val stages = draft.recipe.stages.filter { it.type == StageType.POUR }
    assertEquals(listOf(0L, 45_000L, 91_000L), stages.map { it.startMs })
    assertEquals(stages[0].targetWaterG, draft.recipe.stages[1].targetWaterG, 0.0)
    assertEquals(stages[1].targetWaterG, draft.recipe.stages[3].targetWaterG, 0.0)
    assertEquals(listOf(8_000L, 37_000L, 20_000L, 26_000L, 24_000L), draft.recipe.stages.map { it.durationMs })
    assertTrue(RecipeValidator.validate(draft.recipe).isEmpty())
    // Duraciones: 8,0 s → 8; 19,9 s → 20; 24,4 s → 24.
    assertEquals(listOf(8_000L, 20_000L, 24_000L), stages.map { it.durationMs })
    // Acumulada: 40, 139,5 y 261,5 g (5 g/s durante 8, 19,9 y 24,4 s); redondeada a 1 g.
    assertTrue(stages.all { it.targetWaterG == Math.round(it.targetWaterG).toDouble() })
    assertTrue(stages[0].targetWaterG in 39.0..41.0)
    assertTrue(stages[1].targetWaterG in 139.0..141.0)
    assertTrue(stages[2].targetWaterG in 261.0..263.0)
    assertTrue(draft.adjustments.isEmpty())
  }

  @Test
  fun `the data comes from the brew but the tasting notes do not`() {
    val draft = convert(listOf(Pour(1, 0, 10_000, 50.0)), 10_000)
    val recipe = draft.recipe
    assertEquals("Etiopía (receta)", recipe.name)
    assertEquals(BrewMethod.V60, recipe.method)
    assertEquals(GrindSetting(3, 12.0, "Acme · 12 clics"), recipe.grind)
    assertEquals(93.0, recipe.waterTempC!!, 0.0)
    assertNull(recipe.notes)
    assertEquals(15.0, recipe.doseG, 0.0)
    assertNull(recipe.builtInKey)
    assertEquals(false, recipe.localizedNames)
    assertTrue(recipe.stages.all { it.name == null && it.instruction == null })
  }

  @Test
  fun `a pour that starts after zero gets a wait from zero`() {
    val draft = convert(listOf(Pour(1, 5_000, 15_000, 50.0)), 15_000)
    val stages = draft.recipe.stages
    assertEquals(listOf(StageType.WAIT, StageType.POUR), stages.map { it.type })
    assertEquals(0L, stages[0].startMs)
    assertEquals(5_000L, stages[0].durationMs)
    assertEquals(0.0, stages[0].targetWaterG, 0.0)
  }

  @Test
  fun `more than ten pours merge the two separated by the shortest pause`() {
    // 11 vertidos cada 30 s de 5 s; los 6.º y 7.º, con una pausa de solo 3 s.
    val starts = (0 until 11).map { it * 30_000L }.toMutableList()
    val pours = starts.mapIndexed { i, s -> Pour(i + 1, s, s + 5_000, 25.0) }.toMutableList()
    pours[6] = Pour(7, pours[5].endMs + 3_000, pours[5].endMs + 8_000, 25.0)
    val draft = convert(pours, pours.last().endMs)
    val mergedPours = draft.recipe.stages.filter { it.type == StageType.POUR }
    assertEquals(10, mergedPours.size)
    assertEquals(19, draft.recipe.stages.size) // 10 vertidos y 9 esperas
    assertTrue(Adjustment.MergedPours(1) in draft.adjustments)
    // La etapa fusionada empieza en el inicio del 6.º y termina en el fin del 7.º.
    val merged = mergedPours[5]
    assertEquals(150_000L, merged.startMs)
    assertEquals(13_000L, merged.durationMs) // 5 s + 3 s de pausa + 5 s
    assertTrue(RecipeValidator.validate(draft.recipe).none { it is RecipeError.StageCountInvalid })
  }

  @Test
  fun `durations over 120 s are clamped and reported`() {
    val draft = convert(listOf(Pour(1, 0, 150_000, 100.0)), 150_000)
    assertEquals(120_000L, draft.recipe.stages.single().durationMs)
    assertTrue(Adjustment.ClampedDurations in draft.adjustments)
  }

  @Test
  fun `a dose out of 5 to 60 g, or missing, leaves the dose empty`() {
    val pours = listOf(Pour(1, 0, 10_000, 50.0))
    for (dose in listOf(null, 4.9, 60.1, 0.0)) {
      val draft = convert(pours, 10_000, dose = dose)
      assertTrue("dose $dose", Adjustment.MissingDose in draft.adjustments)
      assertEquals(0.0, draft.recipe.doseG, 0.0)
    }
    assertTrue(Adjustment.MissingDose !in convert(pours, 10_000, dose = 5.0).adjustments)
    assertTrue(Adjustment.MissingDose !in convert(pours, 10_000, dose = 60.0).adjustments)
  }

  @Test
  fun `the dose is rounded to one decimal before checking the limits`() {
    val pours = listOf(Pour(1, 0, 10_000, 50.0))
    assertEquals(16.4, convert(pours, 10_000, dose = 16.43).recipe.doseG, 1e-9)
    assertTrue(Adjustment.MissingDose !in convert(pours, 10_000, dose = 4.96).adjustments) // 5,0
    assertTrue(Adjustment.MissingDose in convert(pours, 10_000, dose = 4.94).adjustments) // 4,9
  }

  @Test
  fun `without pours there is a single stage from 0 with the total time and water`() {
    val samples = brew(listOf(Pour(1, 0, 20_000, 100.0)), 20_000)
    val draft = convert(pours = emptyList(), totalMs = 20_000, samples = samples)
    val stage = draft.recipe.stages.single()
    assertEquals(0L, stage.startMs)
    assertEquals(20_000L, stage.durationMs)
    assertEquals(100.0, stage.targetWaterG, 0.5)
  }

  @Test
  fun `a very short pour lasts at least one second`() {
    val draft = convert(listOf(Pour(1, 0, 300, 1.5)), 300)
    assertEquals(1_000L, draft.recipe.stages.single().durationMs)
  }

  @Test
  fun `more than 1000 g stays as a normal validation error`() {
    val pours = listOf(Pour(1, 0, 100_000, 1_200.0))
    val samples = (0..1000).map { BrewSample(it * 100L, it * 1.2, 12.0, null, true) }
    val draft = convert(pours, 100_000, samples = samples)
    assertTrue(RecipeValidator.validate(draft.recipe).any { it is RecipeError.WaterOutOfRange })
  }

  @Test
  fun `without valid samples the accumulated water comes from the pours`() {
    val pours = listOf(Pour(1, 0, 10_000, 40.0), Pour(2, 30_000, 40_000, 60.0))
    val draft = RecipeFromBrew.convert("x (receta)", details, 15.0, emptyList(), pours, 40_000, 100.0)
    assertEquals(listOf(40.0, 40.0, 100.0), draft.recipe.stages.map { it.targetWaterG })
    assertEquals(listOf(StageType.POUR, StageType.WAIT, StageType.POUR), draft.recipe.stages.map { it.type })
  }
}
