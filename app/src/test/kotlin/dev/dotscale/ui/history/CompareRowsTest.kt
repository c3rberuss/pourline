package dev.dotscale.ui.history

import dev.dotscale.brew.GRAMS_PER_OUNCE
import dev.dotscale.history.BrewRecord
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.session.brewRecord
import dev.dotscale.session.rampSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompareRowsTest {
  private val a = brewRecord(id = 1, doseG = 15.0, samples = rampSamples(totalMs = 100_000, totalG = 250.0))
  private val b = brewRecord(id = 2, doseG = 20.0, samples = rampSamples(totalMs = 120_000, totalG = 300.0))

  private fun List<CompareRow>.of(metric: CompareMetric) = single { it.metric == metric }

  @Test
  fun `rows come in the displayed order`() {
    assertEquals(CompareMetric.entries, compareRows(a, b).map { it.metric })
  }

  @Test
  fun `the difference is B minus A`() {
    val rows = compareRows(a, b)

    rows.of(CompareMetric.TOTAL_TIME).let {
      assertEquals(100_000.0, it.a!!, 0.0)
      assertEquals(120_000.0, it.b!!, 0.0)
      assertEquals(20_000.0, it.diff!!, 0.0)
    }
    rows.of(CompareMetric.TOTAL_WATER).let {
      assertEquals(250.0, it.a!!, 1e-9)
      assertEquals(300.0, it.b!!, 1e-9)
      assertEquals(50.0, it.diff!!, 1e-9)
    }
    assertEquals(5.0, rows.of(CompareMetric.DOSE).diff!!, 1e-9)
    // Ratio de A: 250 / 15; de B: 300 / 20.
    assertEquals(300.0 / 20 - 250.0 / 15, rows.of(CompareMetric.RATIO).diff!!, 1e-9)
    assertEquals(0.0, rows.of(CompareMetric.POURS).diff!!, 0.0)
  }

  @Test
  fun `a decrease gives a negative difference`() {
    assertEquals(-20_000.0, compareRows(b, a).of(CompareMetric.TOTAL_TIME).diff!!, 0.0)
  }

  @Test
  fun `everything is in the unit of A even if B was saved in another`() {
    val inOunces = a.copy(entity = a.entity.copy(unit = WeightUnit.OUNCES))
    val rows = compareRows(inOunces, b) // B se guardó en gramos

    rows.of(CompareMetric.TOTAL_WATER).let {
      assertEquals(250.0 / GRAMS_PER_OUNCE, it.a!!, 1e-9)
      assertEquals(300.0 / GRAMS_PER_OUNCE, it.b!!, 1e-9)
    }
    assertEquals(20.0 / GRAMS_PER_OUNCE, rows.of(CompareMetric.DOSE).b!!, 1e-9)
    assertEquals(4.0 / GRAMS_PER_OUNCE, rows.of(CompareMetric.MAX_FLOW).a!!, 1e-9)
    // El tiempo y el ratio no llevan unidad.
    assertEquals(100_000.0, rows.of(CompareMetric.TOTAL_TIME).a!!, 0.0)
  }

  @Test
  fun `a missing value gives a null difference`() {
    val noDose: BrewRecord = brewRecord(id = 3, doseG = null)
    val rows = compareRows(a, noDose)

    rows.of(CompareMetric.DOSE).let {
      assertEquals(15.0, it.a!!, 1e-9)
      assertNull(it.b)
      assertNull(it.diff)
    }
    assertNull(rows.of(CompareMetric.RATIO).diff)
    // Ninguna de las dos tiene puntuación de estabilidad.
    rows.of(CompareMetric.STABILITY).let {
      assertNull(it.a)
      assertNull(it.b)
      assertNull(it.diff)
    }
  }

  @Test
  fun `the number of pours is null when the curve is unreadable`() {
    val unreadable = b.copy(curveError = true)
    val rows = compareRows(a, unreadable)

    rows.of(CompareMetric.POURS).let {
      assertEquals(1.0, it.a!!, 0.0)
      assertNull(it.b)
      assertNull(it.diff)
    }
    // El resto sale de las columnas y sigue ahí.
    assertEquals(120_000.0, rows.of(CompareMetric.TOTAL_TIME).b!!, 0.0)
  }
}
