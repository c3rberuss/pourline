package dev.josueamaya.pourline.recipe.share

import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.v60Classic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeImportRulesTest {
  private val base = v60Classic()

  private fun withStage(index: Int, change: (RecipeStage) -> RecipeStage) =
    base.copy(stages = base.stages.mapIndexed { i, stage -> if (i == index) change(stage) else stage })

  @Test
  fun `the same recipe is identical`() {
    assertTrue(isIdentical(base, base.copy(id = 9, builtInKey = null, localizedNames = false)))
  }

  @Test
  fun `a name that differs only in case or outer spaces is still identical`() {
    assertTrue(isIdentical(base, base.copy(name = "  V60 CLÁSICA ")))
    assertFalse(isIdentical(base, base.copy(name = "V60 clasica")))
  }

  @Test
  fun `a different dose, method or temperature is not identical`() {
    assertFalse(isIdentical(base, base.copy(doseG = 16.0)))
    assertFalse(isIdentical(base, base.copy(method = BrewMethod.CHEMEX)))
    assertFalse(isIdentical(base, base.copy(method = null)))
    assertFalse(isIdentical(base, base.copy(waterTempC = 93.0)))
  }

  @Test
  fun `a different stage type, name, start, duration, pour water or instruction is not identical`() {
    assertFalse(isIdentical(base, withStage(0) { it.copy(type = StageType.SWIRL) }))
    assertFalse(isIdentical(base, withStage(0) { it.copy(name = "Otro") }))
    assertFalse(isIdentical(base, withStage(2) { it.copy(startMs = it.startMs + 1_000) }))
    assertFalse(isIdentical(base, withStage(0) { it.copy(durationMs = it.durationMs + 1_000) }))
    assertFalse(isIdentical(base, withStage(0) { it.copy(targetWaterG = 41.0) }))
    assertFalse(isIdentical(base, withStage(0) { it.copy(instruction = "otra") }))
    assertFalse(isIdentical(base, base.copy(stages = base.stages.dropLast(1))))
  }

  @Test
  fun `swirl and wait water does not count`() {
    assertTrue(isIdentical(base, withStage(1) { it.copy(targetWaterG = 99.0) }))
  }

  @Test
  fun `notes, method other and grind do not count`() {
    val other = base.copy(notes = "algo", methodOther = "x", grind = GrindSetting(1, 20.0, "Acme Uno · 20"))
    assertTrue(isIdentical(base, other))
  }

  @Test
  fun `a blank stage name equals no name`() {
    assertTrue(isIdentical(base, withStage(1) { it.copy(name = "   ", instruction = " i2 ") }))
    assertTrue(isIdentical(withStage(1) { it.copy(name = null) }, withStage(1) { it.copy(name = "") }))
  }

  @Test
  fun `a free name is kept trimmed`() {
    assertEquals("Mi receta", uniqueName("  Mi receta ", listOf("Otra")))
    assertEquals("Mi receta", uniqueName("Mi receta", emptyList()))
  }

  @Test
  fun `a taken name gets (2)`() {
    assertEquals("V60 (2)", uniqueName("V60", listOf("V60", "Otra")))
  }

  @Test
  fun `(2) taken gives (3)`() {
    assertEquals("V60 (3)", uniqueName("V60", listOf("V60", "V60 (2)")))
    assertEquals("V60 (4)", uniqueName("V60", listOf("V60", "v60 (2)", " V60 (3) ")))
  }

  @Test
  fun `the clash ignores case and outer spaces`() {
    assertEquals("V60 (2)", uniqueName(" V60 ", listOf("v60  ".trim().uppercase())))
    assertEquals("Café (2)", uniqueName("Café", listOf("  CAFÉ ")))
  }
}
