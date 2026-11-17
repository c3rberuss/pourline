package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.grinder.GrinderFixtures.cinco
import dev.josueamaya.pourline.grinder.GrinderFixtures.dos
import dev.josueamaya.pourline.grinder.GrinderFixtures.tres
import dev.josueamaya.pourline.grinder.GrinderFixtures.uno
import dev.josueamaya.pourline.history.BrewMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConverterRowsTest {
  @Test
  fun `one row per grinder of mine except the source in the order of mine`() {
    val rows = converterRows(uno, BrewMethod.V60, 20.0, listOf(tres, uno, dos))
    assertEquals(listOf(3L, 2L), rows.map { it.grinder.id })
  }

  @Test
  fun `each row carries the conversion and no fix when there is an equivalent`() {
    val rows = converterRows(uno, BrewMethod.V60, 20.0, listOf(uno, dos))
    val row = rows.single()
    assertEquals(Conversion.Exact(28.0), row.conversion)
    assertNull(row.fix)
  }

  @Test
  fun `the marks of each case come from the conversion`() {
    val rows = converterRows(uno, BrewMethod.V60, 40.0, listOf(dos)).single()
    assertEquals(Conversion.Approximate(60.0, setOf(ConversionReason.AT_LIMIT)), rows.conversion)
    val anchors = converterRows(uno, BrewMethod.AEROPRESS, 14.0, listOf(dos)).single()
    assertEquals(Conversion.Approximate(20.0, setOf(ConversionReason.ANCHORS)), anchors.conversion)
    val none = converterRows(uno, null, 14.0, listOf(dos)).single()
    assertTrue(none.conversion is Conversion.Approximate)
  }

  @Test
  fun `no equivalence offers to complete my grinder or to duplicate a predefined one`() {
    val own = converterRows(uno, BrewMethod.AEROPRESS, 14.0, listOf(cinco)).single()
    assertEquals(Conversion.NoEquivalent, own.conversion)
    assertEquals(GrindFix.COMPLETE_RANGES, own.fix)
    val predefined = converterRows(uno, BrewMethod.AEROPRESS, 14.0, listOf(cinco.copy(catalogKey = "acme-cinco"))).single()
    assertEquals(GrindFix.DUPLICATE, predefined.fix)
  }

  @Test
  fun `without grinders of mine or with only the source the list is empty`() {
    assertTrue(converterRows(uno, BrewMethod.V60, 20.0, emptyList()).isEmpty())
    assertTrue(converterRows(uno, BrewMethod.V60, 20.0, listOf(uno)).isEmpty())
  }
}
