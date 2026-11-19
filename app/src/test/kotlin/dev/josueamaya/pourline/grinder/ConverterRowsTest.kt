package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.grinder.GrinderFixtures.dos
import dev.josueamaya.pourline.grinder.GrinderFixtures.sinCurva
import dev.josueamaya.pourline.grinder.GrinderFixtures.tres
import dev.josueamaya.pourline.grinder.GrinderFixtures.uno
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConverterRowsTest {
  private val noPairs = emptyList<GrindCalibration>()

  private fun pair(id: Long, a: Grinder, valueA: Double, b: Grinder, valueB: Double) =
    GrindCalibration(id, minOf(a.id, b.id), if (a.id < b.id) valueA else valueB, maxOf(a.id, b.id), if (a.id < b.id) valueB else valueA)

  @Test
  fun `one row per grinder of mine except the source in the order of mine`() {
    val rows = converterRows(uno, 20.0, listOf(tres, uno, dos), noPairs)
    assertEquals(listOf(3L, 2L), rows.map { it.grinder.id })
  }

  @Test
  fun `without pairs each row comes from the curves with no fix`() {
    val row = converterRows(uno, 20.0, listOf(uno, dos), noPairs).single()
    assertEquals(Conversion.Approximate(30.0, setOf(ConversionReason.ANCHORS)), row.conversion)
    assertNull(row.fix)
  }

  @Test
  fun `a pair between the source and a row calibrates that row only`() {
    val pairs = listOf(pair(1, uno, 20.0, dos, 31.0))
    val rows = converterRows(uno, 20.0, listOf(dos, tres), pairs)
    assertEquals(Conversion.Exact(31.0), rows.first { it.grinder.id == 2L }.conversion)
    // Tres no tiene pares con Uno: sale de las curvas, como sin el par.
    assertEquals(converterRows(uno, 20.0, listOf(tres), noPairs).single().conversion, rows.first { it.grinder.id == 3L }.conversion)
  }

  @Test
  fun `two pairs give the value according to the calibration`() {
    val pairs = listOf(pair(1, uno, 10.0, dos, 14.0), pair(2, uno, 30.0, dos, 50.0))
    val row = converterRows(uno, 20.0, listOf(dos), pairs).single()
    assertEquals(Conversion.Approximate(32.0, setOf(ConversionReason.CALIBRATED)), row.conversion)
  }

  @Test
  fun `a pair that belongs to another grinder does not count`() {
    val pairs = listOf(pair(1, dos, 10.0, tres, 40.0))
    val row = converterRows(uno, 20.0, listOf(dos), pairs).single()
    assertEquals(Conversion.Approximate(30.0, setOf(ConversionReason.ANCHORS)), row.conversion)
  }

  @Test
  fun `only a source of mine can be corrected`() {
    assertTrue(converterRows(uno, 20.0, listOf(dos), noPairs).single().correctable)
    val predefined = uno.copy(owned = false, catalogKey = "acme-uno")
    assertFalse(converterRows(predefined, 20.0, listOf(dos), noPairs).single().correctable)
  }

  @Test
  fun `no equivalence offers to complete the grinder without a curve or to duplicate a predefined one`() {
    val own = converterRows(uno, 20.0, listOf(sinCurva), noPairs).single()
    assertEquals(Conversion.NoEquivalent, own.conversion)
    assertEquals(FixTarget(GrindFix.COMPLETE_RANGES, sinCurva.id), own.fix)
    val predefined = converterRows(uno, 20.0, listOf(sinCurva.copy(catalogKey = "acme-sin-curva")), noPairs).single()
    assertEquals(FixTarget(GrindFix.DUPLICATE, sinCurva.id), predefined.fix)
  }

  @Test
  fun `with a single pair and no curve the fix is to add another equivalence on the row`() {
    val pairs = listOf(pair(1, uno, 20.0, sinCurva, 20.0))
    val row = converterRows(uno, 20.0, listOf(sinCurva), pairs).single()
    assertEquals(Conversion.NoEquivalent, row.conversion)
    assertEquals(FixTarget(GrindFix.ADD_CALIBRATION, sinCurva.id), row.fix)
  }

  @Test
  fun `without grinders of mine or with only the source the list is empty`() {
    assertTrue(converterRows(uno, 20.0, emptyList(), noPairs).isEmpty())
    assertTrue(converterRows(uno, 20.0, listOf(uno), noPairs).isEmpty())
  }
}
