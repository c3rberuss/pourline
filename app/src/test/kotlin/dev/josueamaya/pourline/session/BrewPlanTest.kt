package dev.josueamaya.pourline.session

import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.history.BrewDetails
import dev.josueamaya.pourline.history.ReferenceType
import dev.josueamaya.pourline.recipe.v60Classic
import dev.josueamaya.pourline.settings.ReferenceKey
import org.junit.Assert.assertEquals
import org.junit.Test

/** El agua objetivo del aro (D26). */
class BrewPlanTest {
  private val plan = BrewPlan(doseG = 15.0, ratio = 16.0)

  private fun recipeTarget(factor: Double = 1.0): ActiveTarget {
    val curve = TargetCurve.fromRecipe(v60Classic())
    return ActiveTarget(
      key = ReferenceKey.Recipe(1),
      type = ReferenceType.RECIPE,
      name = "V60 clásica",
      curve = if (factor == 1.0) curve else curve.scaled(factor),
      factor = factor,
      nominalDoseG = 15.0,
      scaledDoseG = null,
      prefill = BrewDetails(),
    )
  }

  @Test
  fun withoutARecipeTheWaterIsThePlannedDoseTimesTheRatio() {
    assertEquals(240.0, targetWaterG(null, null, plan), 1e-9)
  }

  @Test
  fun withoutARecipeACapturedDoseReplacesThePlannedOne() {
    assertEquals(18.0 * 16.0, targetWaterG(null, 18.0, plan), 1e-9)
  }

  @Test
  fun withARecipeTheWaterIsTheCurveFinalWater() {
    assertEquals(250.0, targetWaterG(recipeTarget(), null, plan), 1e-9)
  }

  @Test
  fun withARecipeTheScaledWaterWins() {
    assertEquals(250.0 * 1.2, targetWaterG(recipeTarget(factor = 1.2), 18.0, plan), 1e-9)
  }

  @Test
  fun withARecipeThePlanIsIgnored() {
    assertEquals(250.0, targetWaterG(recipeTarget(), null, BrewPlan(60.0, 20.0)), 1e-9)
  }

  @Test
  fun aSkippedDoseWithoutARecipeUsesThePreparePlan() {
    assertEquals(20.0 * 15.0, targetWaterG(null, null, BrewPlan(20.0, 15.0)), 1e-9)
  }
}
