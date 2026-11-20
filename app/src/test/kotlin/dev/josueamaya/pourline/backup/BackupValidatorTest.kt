package dev.josueamaya.pourline.backup

import dev.josueamaya.pourline.grinder.GrindLevel
import dev.josueamaya.pourline.history.BrewCurveEntity
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.history.BrewTargetEntity
import dev.josueamaya.pourline.grinder.GrinderAnchorEntity
import dev.josueamaya.pourline.grinder.GrinderMethodRangeEntity
import dev.josueamaya.pourline.grinder.GrinderUnitEntity
import dev.josueamaya.pourline.grinder.GrindUnit
import dev.josueamaya.pourline.recipe.RecipeStageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupValidatorTest {

  private fun check(expected: BackupError?, message: String, change: (BackupRows) -> BackupRows) =
    assertEquals(message, expected, validate(change(fullRows())))

  @Test
  fun `a valid data set passes`() {
    assertNull(validate(fullRows()))
    assertNull(validate(BackupRows()))
    assertEquals(setOf(PHOTO_A, PHOTO_B, PHOTO_C), referencedPhotos(fullRows()))
  }

  @Test
  fun `each foreign key must point to an existing row`() {
    val broken = BackupError.BROKEN_REFERENCES
    check(broken, "brews.coffeeId") { r -> r.copy(brews = r.brews.map { if (it.id == 1L) it.copy(coffeeId = 404) else it }) }
    check(broken, "brews.grinderId") { r -> r.copy(brews = r.brews.map { if (it.id == 1L) it.copy(grinderId = 404) else it }) }
    check(broken, "brew_curves.brewId") { r -> r.copy(brewCurves = r.brewCurves + BrewCurveEntity(404, ByteArray(1))) }
    check(broken, "brew_targets.brewId") { r -> r.copy(brewTargets = r.brewTargets + BrewTargetEntity(404, ByteArray(1))) }
    check(broken, "recipes.grinderId") { r -> r.copy(recipes = r.recipes.map { if (it.id == 1L) it.copy(grinderId = 404) else it }) }
    check(broken, "recipe_stages.recipeId") { r -> r.copy(recipeStages = r.recipeStages + RecipeStageEntity(404, 0, null, 0, 1.0, 1)) }
    check(broken, "grinder_units.grinderId") { r -> r.copy(grinderUnits = r.grinderUnits + GrinderUnitEntity(404, 0, GrindUnit.CLICKS, null)) }
    check(broken, "grinder_method_ranges.grinderId") { r ->
      r.copy(grinderMethodRanges = r.grinderMethodRanges + GrinderMethodRangeEntity(404, BrewMethod.V60, 1.0, 2.0))
    }
    check(broken, "grinder_anchors.grinderId") { r -> r.copy(grinderAnchors = r.grinderAnchors + GrinderAnchorEntity(404, GrindLevel.FINE, 1.0)) }
    check(broken, "grind_calibrations.grinderAId") { r ->
      r.copy(grindCalibrations = r.grindCalibrations.map { if (it.id == 1L) it.copy(grinderAId = 404) else it })
    }
    check(broken, "grind_calibrations.grinderBId") { r ->
      r.copy(grindCalibrations = r.grindCalibrations.map { if (it.id == 1L) it.copy(grinderBId = 404) else it })
    }
    check(broken, "a coffee that was deleted") { r -> r.copy(coffees = r.coffees.filter { it.id != 1L }) }
  }

  @Test
  fun `null references are not checked`() {
    check(null, "brews without coffee or grinder") { r -> r.copy(brews = r.brews.map { it.copy(coffeeId = null, grinderId = null) }) }
    check(null, "recipes without grinder") { r -> r.copy(recipes = r.recipes.map { it.copy(grinderId = null) }) }
    check(null, "no rows at all in the child tables") { r ->
      r.copy(brewCurves = emptyList(), brewTargets = emptyList(), recipeStages = emptyList(), grinderUnits = emptyList())
    }
  }

  @Test
  fun `reference ids of brews and setting ids are not foreign keys`() {
    // `brews.referenceId` puede apuntar a una receta o preparación ya borrada (el fixture tiene el 99).
    check(null, "reference id of a deleted origin") { r -> r.copy(brews = r.brews.map { it.copy(referenceId = 12345) }) }
    assertNull(validate(fullRows().copy(recipes = emptyList(), recipeStages = emptyList(), brews = fullRows().brews.map { it.copy(grinderId = null, coffeeId = null) })))
  }

  @Test
  fun `duplicate primary or unique keys are corrupt`() {
    val corrupt = BackupError.CORRUPT
    check(corrupt, "brews.id") { r -> r.copy(brews = r.brews + r.brews[0]) }
    check(corrupt, "brew_curves.brewId") { r -> r.copy(brewCurves = r.brewCurves + r.brewCurves[0]) }
    check(corrupt, "brew_targets.brewId") { r -> r.copy(brewTargets = r.brewTargets + r.brewTargets[0]) }
    check(corrupt, "recipes.id") { r -> r.copy(recipes = r.recipes + r.recipes[0].copy(builtInKey = null)) }
    check(corrupt, "recipe_stages (recipeId, position)") { r -> r.copy(recipeStages = r.recipeStages + r.recipeStages[0]) }
    check(corrupt, "coffees.id") { r -> r.copy(coffees = r.coffees + r.coffees[0]) }
    check(corrupt, "grinders.id") { r -> r.copy(grinders = r.grinders + r.grinders[0].copy(catalogKey = null)) }
    check(corrupt, "grinder_units (grinderId, position)") { r -> r.copy(grinderUnits = r.grinderUnits + r.grinderUnits[0]) }
    check(corrupt, "grinder_method_ranges (grinderId, method)") { r -> r.copy(grinderMethodRanges = r.grinderMethodRanges + r.grinderMethodRanges[0]) }
    check(corrupt, "grinder_anchors (grinderId, level)") { r -> r.copy(grinderAnchors = r.grinderAnchors + r.grinderAnchors[0]) }
    check(corrupt, "grind_calibrations.id") { r -> r.copy(grindCalibrations = r.grindCalibrations + r.grindCalibrations[0]) }
    check(corrupt, "recipes.builtInKey") { r -> r.copy(recipes = r.recipes.map { it.copy(builtInKey = "V60_CLASSIC") }) }
    check(corrupt, "grinders.catalogKey") { r -> r.copy(grinders = r.grinders.map { it.copy(catalogKey = "timemore-c2") }) }
    check(corrupt, "grind_calibrations (a, b, valueA)") { r ->
      r.copy(grindCalibrations = r.grindCalibrations.map { it.copy(valueA = 10.0) })
    }
    check(corrupt, "grind_calibrations (a, b, valueB)") { r ->
      r.copy(grindCalibrations = r.grindCalibrations.map { it.copy(valueB = 20.0) })
    }
    // Varias filas con clave única `null` son válidas.
    check(null, "several null unique keys") { r -> r.copy(recipes = r.recipes.map { it.copy(builtInKey = null) }) }
    // Una clave repetida gana a una referencia rota: se comprueba antes.
    check(corrupt, "duplicates before references") { r ->
      r.copy(brews = r.brews.map { it.copy(coffeeId = 404) } + r.brews[0])
    }
  }

  @Test
  fun `ids must be positive`() {
    val corrupt = BackupError.CORRUPT
    check(corrupt, "brews.id 0") { r -> r.copy(brews = r.brews.map { if (it.id == 1L) it.copy(id = 0) else it }, brewCurves = emptyList(), brewTargets = emptyList()) }
    check(corrupt, "recipes.id negative") { r -> r.copy(recipes = r.recipes.map { if (it.id == 2L) it.copy(id = -5) else it }, recipeStages = r.recipeStages.filter { it.recipeId != 2L }) }
    check(corrupt, "coffees.id 0") { r -> r.copy(coffees = r.coffees.map { if (it.id == 2L) it.copy(id = 0) else it }) }
    check(corrupt, "grinders.id 0") { r -> r.copy(grinders = listOf(r.grinders[0].copy(id = 0))) }
    check(corrupt, "grind_calibrations.id 0") { r -> r.copy(grindCalibrations = r.grindCalibrations.map { it.copy(id = 0) }.take(1)) }
  }

  @Test
  fun `photo names must be plain file names`() {
    val corrupt = BackupError.CORRUPT
    listOf("../evil.jpg", "/abs.jpg", "a/b.jpg", "foto.png", "a b.jpg", "", PHOTO_A.removeSuffix(".jpg") + "_thumb.jpg", "x".repeat(70) + ".jpg").forEach { name ->
      check(corrupt, "photo1 '$name'") { r -> r.copy(coffees = r.coffees.map { if (it.id == 1L) it.copy(photo1 = name) else it }) }
      check(corrupt, "photo2 '$name'") { r -> r.copy(coffees = r.coffees.map { if (it.id == 2L) it.copy(photo2 = name) else it }) }
    }
  }
}
