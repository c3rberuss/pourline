package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.grinder.GrinderFixtures.tres
import dev.josueamaya.pourline.grinder.GrinderFixtures.uno
import dev.josueamaya.pourline.history.BrewMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrinderValidatorTest {
  private fun errors(g: Grinder) = GrinderValidator.validate(g)

  @Test
  fun aValidGrinderHasNoErrors() {
    for (g in GrinderFixtures.all) assertEquals("${g.model}: ${errors(g)}", emptyList<GrinderError>(), errors(g))
  }

  @Test
  fun blankBrandAndModelAreRejected() {
    assertTrue(GrinderError.BlankBrand in errors(uno.copy(brand = "  ")))
    assertTrue(GrinderError.BlankModel in errors(uno.copy(model = "")))
  }

  @Test
  fun noUnitsIsRejected() {
    assertTrue(GrinderError.NoUnits in errors(uno.copy(units = emptyList())))
  }

  @Test
  fun scaleMustGoFromMinToMax() {
    assertTrue(GrinderError.ScaleOrder in errors(uno.copy(scaleMin = 40.0, scaleMax = 40.0)))
    assertTrue(GrinderError.ScaleOrder in errors(uno.copy(scaleMin = 50.0, scaleMax = 40.0)))
  }

  @Test
  fun stepMustBePositive() {
    assertTrue(GrinderError.StepNotPositive in errors(uno.copy(step = 0.0)))
    assertTrue(GrinderError.StepNotPositive in errors(uno.copy(step = -1.0)))
  }

  @Test
  fun scaleMustBeAMultipleOfTheStep() {
    assertTrue(GrinderError.ScaleNotMultipleOfStep in errors(uno.copy(scaleMax = 40.5)))
    assertTrue(GrinderError.ScaleNotMultipleOfStep !in errors(uno.copy(scaleMax = 40.5, step = 0.5)))
    assertTrue(GrinderError.ScaleNotMultipleOfStep !in errors(GrinderFixtures.cinco))
  }

  @Test
  fun compoundGrindersCannotHaveANegativeScale() {
    assertTrue(GrinderError.NegativeCompoundScale in errors(tres.copy(scaleMin = -10.0)))
    // Un molino simple sí puede.
    assertTrue(GrinderError.NegativeCompoundScale !in errors(uno.copy(scaleMin = -10.0)))
  }

  @Test
  fun unitsCannotRepeat() {
    val repeated = tres.copy(units = tres.units.take(2) + GrindUnitLevel(GrindUnit.ROTATIONS, perParent = 10))
    assertTrue(GrinderError.DuplicateUnit(2) in errors(repeated))
  }

  @Test
  fun primaryHasNoPerParentAndSecondariesNeedAtLeastTwo() {
    assertTrue(GrinderError.PrimaryWithPerParent in errors(uno.copy(units = listOf(GrindUnitLevel(GrindUnit.CLICKS, perParent = 4)))))
    assertTrue(GrinderError.PerParentTooSmall(1) in errors(tres.copy(units = tres.units.mapIndexed { i, u -> if (i == 1) u.copy(perParent = 1) else u })))
    assertTrue(GrinderError.PerParentTooSmall(1) in errors(tres.copy(units = tres.units.mapIndexed { i, u -> if (i == 1) u.copy(perParent = null) else u })))
  }

  @Test
  fun rangesNeedMinBelowMaxAndInsideTheScale() {
    assertTrue(GrinderError.RangeOrder(BrewMethod.V60) in errors(uno.copy(ranges = mapOf(BrewMethod.V60 to GrindRange(20.0, 20.0)))))
    assertTrue(GrinderError.RangeOutsideScale(BrewMethod.V60) in errors(uno.copy(ranges = mapOf(BrewMethod.V60 to GrindRange(20.0, 41.0)))))
    assertTrue(GrinderError.RangeOutsideScale(BrewMethod.V60) in errors(uno.copy(ranges = mapOf(BrewMethod.V60 to GrindRange(-1.0, 10.0)))))
  }

  @Test
  fun otherHasNoRange() {
    assertTrue(GrinderError.RangeForOther in errors(uno.copy(ranges = mapOf(BrewMethod.OTHER to GrindRange(1.0, 2.0)))))
  }

  @Test
  fun anchorsStayInsideTheScale() {
    assertTrue(GrinderError.AnchorOutsideScale(GrindLevel.FINE) in errors(uno.copy(anchors = mapOf(GrindLevel.FINE to 41.0))))
  }

  @Test
  fun anchorsAreStrictlyMonotonicAccordingToTheDirection() {
    val equal = uno.copy(anchors = mapOf(GrindLevel.FINE to 10.0, GrindLevel.MEDIUM to 10.0))
    assertTrue(GrinderError.AnchorsNotMonotonic(GrindLevel.MEDIUM) in errors(equal))
    val reversed = uno.copy(anchors = mapOf(GrindLevel.FINE to 20.0, GrindLevel.COARSE to 10.0))
    assertTrue(GrinderError.AnchorsNotMonotonic(GrindLevel.COARSE) in errors(reversed))
    // En un molino invertido, los valores deben bajar con el grosor.
    val inverted = GrinderFixtures.inverso.copy(anchors = mapOf(GrindLevel.FINE to 10.0, GrindLevel.COARSE to 40.0))
    assertTrue(GrinderError.AnchorsNotMonotonic(GrindLevel.COARSE) in errors(inverted))
  }
}
