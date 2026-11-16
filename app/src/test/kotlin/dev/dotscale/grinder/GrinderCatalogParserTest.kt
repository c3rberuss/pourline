package dev.dotscale.grinder

import dev.dotscale.history.BrewMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrinderCatalogParserTest {
  private fun grinderJson(
    key: String = "acme-uno",
    scale: String = """{"min": 0, "max": 40}""",
    ranges: String = """{"V60": {"min": 16, "max": 24, "source": "16 – 24"}}""",
    sourceUrl: String = "https://honestcoffeeguide.com/acme-uno-grind-settings/",
    sourceName: String = "Honest Coffee Guide",
    units: String = """[{"unit": "CLICKS"}]""",
    anchors: String = """{"FINE": 8.0, "MEDIUM": 20.0}""",
  ) = """
    {"key": "$key", "brand": "Acme", "model": "Uno", "units": $units, "scale": $scale, "step": 1.0,
     "direction": "HIGHER_IS_COARSER", "micronRange": {"min": 0, "max": 950}, "ranges": $ranges,
     "anchors": $anchors, "anchorsManual": false, "sourceUrl": "$sourceUrl", "sourceName": "$sourceName", "missing": ["COLD_BREW"], "notes": "n"}
  """

  private fun file(vararg grinders: String) = """{"version": 3, "grinders": [${grinders.joinToString(",")}]}"""

  @Test
  fun aValidFileIsParsedIntoPredefinedGrinders() {
    val data = GrinderCatalogFile.parse(file(grinderJson())).getOrThrow()
    assertEquals(3, data.version)
    val entry = data.entries.single()
    val g = entry.grinder
    assertEquals("acme-uno", g.catalogKey)
    assertTrue(g.isPredefined)
    assertEquals(false, g.owned)
    assertEquals(GrindRange(16.0, 24.0), g.ranges[BrewMethod.V60])
    assertEquals(mapOf(GrindLevel.FINE to 8.0, GrindLevel.MEDIUM to 20.0), g.anchors)
    assertEquals(GrindRange(0.0, 950.0), entry.micronRange)
    assertEquals(listOf("COLD_BREW"), entry.missing)
    assertEquals("https://honestcoffeeguide.com/acme-uno-grind-settings/", g.sourceUrl)
    assertEquals("Honest Coffee Guide", g.sourceName)
  }

  @Test
  fun compoundUnitsAndBaseValuesAreRead() {
    val units = """[{"unit": "ROTATIONS"}, {"unit": "NUMBERS", "perParent": 10}, {"unit": "CLICKS", "perParent": 4}]"""
    val data = GrinderCatalogFile.parse(file(grinderJson(units = units, scale = """{"min": 0, "max": 200}""", ranges = "{}", anchors = "{}"))).getOrThrow()
    val g = data.grinders.single()
    assertEquals(listOf(GrindUnitLevel(GrindUnit.ROTATIONS), GrindUnitLevel(GrindUnit.NUMBERS, 10), GrindUnitLevel(GrindUnit.CLICKS, 4)), g.units)
    assertEquals(200.0, g.scaleMax, 0.0)
  }

  @Test
  fun brokenJsonIsRejected() {
    val result = GrinderCatalogFile.parse("{ esto no es json")
    assertTrue(result.isFailure)
    assertTrue((result.exceptionOrNull() as CatalogException).errors.single() is CatalogError.Malformed)
  }

  @Test
  fun anUnknownMethodIsRejectedAndSoIsOther() {
    assertTrue(GrinderCatalogFile.parse(file(grinderJson(ranges = """{"TURKISH": {"min": 1, "max": 2}}"""))).isFailure)
    assertTrue(GrinderCatalogFile.parse(file(grinderJson(ranges = """{"OTHER": {"min": 1, "max": 2}}"""))).isFailure)
  }

  @Test
  fun oneInvalidGrinderRejectsTheWholeFile() {
    val bad = grinderJson(key = "bad", scale = """{"min": 40, "max": 0}""")
    val result = GrinderCatalogFile.parse(file(grinderJson(), bad))
    assertTrue(result.isFailure)
    val errors = (result.exceptionOrNull() as CatalogException).errors
    assertTrue(errors.single() is CatalogError.Invalid)
  }

  @Test
  fun aRepeatedKeyIsRejected() {
    val result = GrinderCatalogFile.parse(file(grinderJson(), grinderJson()))
    assertTrue((result.exceptionOrNull() as CatalogException).errors.any { it is CatalogError.DuplicateKey })
  }

  @Test
  fun anEmptySourceUrlIsRejected() {
    val result = GrinderCatalogFile.parse(file(grinderJson(sourceUrl = "")))
    assertTrue((result.exceptionOrNull() as CatalogException).errors.any { it is CatalogError.MissingSource })
  }

  @Test
  fun aBlankSourceNameIsRejected() {
    val result = GrinderCatalogFile.parse(file(grinderJson(sourceName = " ")))
    assertTrue((result.exceptionOrNull() as CatalogException).errors.any { it is CatalogError.MissingSourceName })
  }

  @Test
  fun unknownFieldsAreRejected() {
    assertTrue(GrinderCatalogFile.parse("""{"version": 1, "grinders": [], "extra": 1}""").isFailure)
  }
}
