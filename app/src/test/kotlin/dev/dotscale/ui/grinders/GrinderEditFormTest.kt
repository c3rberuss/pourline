package dev.dotscale.ui.grinders

import dev.dotscale.grinder.GrindDirection
import dev.dotscale.grinder.GrindLevel
import dev.dotscale.grinder.GrindRange
import dev.dotscale.grinder.GrindUnit
import dev.dotscale.grinder.GrindUnitLevel
import dev.dotscale.grinder.Grinder
import dev.dotscale.grinder.GrinderError
import dev.dotscale.history.BrewMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrinderEditFormTest {
  private val simple = GrinderEditForm(brand = " Acme ", model = "Uno", scaleMin = 0.0, scaleMax = 40.0)

  private val compound = GrinderEditForm(
    brand = "Acme",
    model = "Tres",
    units = listOf(UnitForm(GrindUnit.ROTATIONS), UnitForm(GrindUnit.NUMBERS, "4"), UnitForm(GrindUnit.CLICKS, "10")),
    scaleMin = 0.0,
    scaleMax = 200.0,
    ranges = mapOf(BrewMethod.V60 to RangeForm(60.0, 100.0)),
    anchors = mapOf(GrindLevel.FINE to 40.0, GrindLevel.COARSE to 160.0),
  )

  @Test
  fun aSimpleFormBuildsATrimmedOwnedGrinder() {
    val grinder = simple.toGrinder()!!
    assertEquals("Acme", grinder.brand)
    assertEquals(listOf(GrindUnitLevel(GrindUnit.CLICKS)), grinder.units)
    assertEquals(40.0, grinder.scaleMax, 0.0)
    assertEquals(1.0, grinder.step, 0.0)
    assertTrue(grinder.owned)
    assertNull(grinder.catalogKey)
    assertTrue(simple.errors().isEmpty())
  }

  @Test
  fun aCompoundFormBuildsItsHierarchyRangesAndAnchors() {
    val grinder = compound.toGrinder()!!
    assertEquals(listOf(null, 4, 10), grinder.units.map { it.perParent })
    assertEquals(mapOf(BrewMethod.V60 to GrindRange(60.0, 100.0)), grinder.ranges)
    assertEquals(mapOf(GrindLevel.FINE to 40.0, GrindLevel.COARSE to 160.0), grinder.anchors)
    assertTrue(compound.errors().isEmpty())
  }

  @Test
  fun theStepAcceptsACommaAndKeepsTheDirection() {
    val grinder = simple.copy(step = "0,5", direction = GrindDirection.HIGHER_IS_FINER).toGrinder()!!
    assertEquals(0.5, grinder.step, 0.0)
    assertEquals(GrindDirection.HIGHER_IS_FINER, grinder.direction)
  }

  @Test
  fun baseKeepsWhatTheFormDoesNotEdit() {
    val base = Grinder(
      id = 7, brand = "x", model = "y", units = listOf(GrindUnitLevel(GrindUnit.CLICKS)), scaleMin = 0.0, scaleMax = 10.0,
      owned = true, duplicatedFromKey = "acme-uno", sourceUrl = null,
    )
    val grinder = simple.toGrinder(id = 7, base = base)!!
    assertEquals(7L, grinder.id)
    assertEquals("acme-uno", grinder.duplicatedFromKey)
  }

  @Test
  fun eachErrorLandsOnItsField() {
    val errors = simple.copy(brand = "", model = " ", step = "0").errors()
    assertEquals(GrinderError.BlankBrand, errors[GrinderField.Brand])
    assertEquals(GrinderError.BlankModel, errors[GrinderField.Model])
    assertEquals(GrinderError.StepNotPositive, errors[GrinderField.Step])

    assertEquals(GrinderError.ScaleOrder, simple.copy(scaleMin = 10.0, scaleMax = 5.0).errors()[GrinderField.Scale])
    assertEquals(GrinderError.MissingValue, simple.copy(scaleMax = null).errors()[GrinderField.Scale])
    assertEquals(GrinderError.ScaleNotMultipleOfStep, simple.copy(scaleMax = 40.5).errors()[GrinderField.Scale])
    assertEquals(GrinderError.NegativeCompoundScale, compound.copy(scaleMin = -4.0).errors()[GrinderField.Scale])

    val dup = compound.setUnit(2, GrindUnit.ROTATIONS).errors()
    assertEquals(GrinderError.DuplicateUnit(2), dup[GrinderField.Unit(2)])
    assertEquals(GrinderError.PerParentTooSmall(1), compound.setPerParent(1, "1").errors()[GrinderField.PerParent(1)])
    assertEquals(GrinderError.MissingValue, compound.setPerParent(2, "").errors()[GrinderField.PerParent(2)])

    assertEquals(GrinderError.RangeOrder(BrewMethod.V60), compound.setRange(BrewMethod.V60, RangeForm(100.0, 60.0)).errors()[GrinderField.Range(BrewMethod.V60)])
    assertEquals(GrinderError.RangeOutsideScale(BrewMethod.V60), compound.setRange(BrewMethod.V60, RangeForm(60.0, 300.0)).errors()[GrinderField.Range(BrewMethod.V60)])
    assertEquals(GrinderError.MissingValue, compound.setRange(BrewMethod.CHEMEX, RangeForm(10.0, null)).errors()[GrinderField.Range(BrewMethod.CHEMEX)])
    assertEquals(GrinderError.MissingValue, compound.setAnchor(GrindLevel.MEDIUM, null).errors()[GrinderField.Anchor(GrindLevel.MEDIUM)])
    assertEquals(GrinderError.AnchorOutsideScale(GrindLevel.FINE), compound.setAnchor(GrindLevel.FINE, 500.0).errors()[GrinderField.Anchor(GrindLevel.FINE)])
    assertTrue(GrinderField.Anchor(GrindLevel.COARSE) in compound.setAnchor(GrindLevel.COARSE, 10.0).errors())
  }

  @Test
  fun anIncompleteFormHasNoGrinderButTheErrorsCoverWhatIsMissing() {
    val form = simple.copy(scaleMax = null, step = "")
    assertNull(form.toGrinder())
    assertEquals(setOf<GrinderField>(GrinderField.Scale, GrinderField.Step), form.errors().keys)
    assertTrue(form.hasErrors)
    assertFalse(simple.hasErrors)
  }

  @Test
  fun addingAndRemovingUnits() {
    val added = simple.addUnit()
    assertEquals(2, added.units.size)
    assertEquals("10", added.units[1].perParent)
    assertTrue(added.units.map { it.unit }.distinct().size == 2)

    var full = simple
    repeat(10) { full = full.addUnit() }
    assertEquals(GrinderEditForm.MAX_UNITS, full.units.size)
    assertFalse(full.canAddUnit)

    // Quitar la primaria hace primaria a la siguiente, sin `perParent`.
    val removed = compound.removeUnit(0)
    assertEquals(listOf(GrindUnit.NUMBERS, GrindUnit.CLICKS), removed.units.map { it.unit })
    assertEquals("", removed.units[0].perParent)
    assertEquals("10", removed.units[1].perParent)
    // Siempre queda una.
    assertEquals(simple, simple.removeUnit(0))
  }

  @Test
  fun changingUnitsOrTheScaleDoesNotTouchTheSavedRangesAndAnchors() {
    val changed = compound.removeUnit(2).copy(scaleMax = 20.0).setUnit(0, GrindUnit.NOTCHES)
    assertEquals(compound.ranges, changed.ranges)
    assertEquals(compound.anchors, changed.anchors)
    // El validador sí avisa de lo que queda fuera de la nueva escala.
    assertNotNull(changed.errors()[GrinderField.Range(BrewMethod.V60)])
  }

  @Test
  fun fromAGrinderRoundTripsAndIsNotDirtyUntilEdited() {
    val grinder = compound.toGrinder()!!.copy(id = 3)
    val form = GrinderEditForm.from(grinder)
    assertEquals(grinder.copy(owned = true), form.toGrinder(id = 3, base = grinder))
    assertFalse(form.isDirty(GrinderEditForm.from(grinder)))
    assertTrue(form.copy(model = "Otro").isDirty(GrinderEditForm.from(grinder)))
    assertEquals("0.5", GrinderEditForm.from(grinder.copy(step = 0.5, scaleMax = 200.0)).step)
  }

  @Test
  fun thePreviewNeedsAScaleAndSkipsIncompleteRanges() {
    assertNull(simple.copy(scaleMax = null).previewGrinder())
    val preview = compound.setRange(BrewMethod.CHEMEX, RangeForm(10.0, null)).previewGrinder()!!
    assertEquals(setOf(BrewMethod.V60), preview.ranges.keys)
  }

  @Test
  fun theInputGrinderUsesTheFormScaleOnlyWhenLimited() {
    assertEquals(40.0, simple.inputGrinder(limited = true).scaleMax, 0.0)
    assertTrue(simple.inputGrinder(limited = false).scaleMax > 1_000.0)
    assertTrue(simple.copy(scaleMax = null).inputGrinder(limited = true).scaleMax > 1_000.0)
    assertEquals(listOf(null, 4, 10), compound.inputGrinder(limited = true).units.map { it.perParent })
  }
}
