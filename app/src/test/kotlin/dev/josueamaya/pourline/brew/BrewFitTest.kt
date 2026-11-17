package dev.josueamaya.pourline.brew

import dev.josueamaya.pourline.recipe.v60Classic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewFitTest {
  private val config = TargetConfig()
  private val curve = TargetCurve.fromRecipe(v60Classic()) // 250 g, termina a los 115 s

  /** Muestras cada 100 ms hasta [untilMs] con el agua que da [water]. */
  private fun samples(untilMs: Long, water: (Long) -> Double) =
    (0..untilMs / 100).map { BrewSample(it * 100, water(it * 100), 0.0, null, true) }

  @Test
  fun `a brew that traces the curve scores 100`() {
    val fit = brewFit(samples(115_000) { curve.waterAt(it) }, 115_000, curve, config)
    assertEquals(100, fit.score)
    assertEquals(250.0, fit.targetWaterG, 0.0)
    assertEquals(115_000L, fit.targetEndMs)
  }

  @Test
  fun `a mean error of 10 percent of the target water or more scores 0`() {
    val over = brewFit(samples(115_000) { curve.waterAt(it) + 25.0 }, 115_000, curve, config)
    assertEquals(0, over.score)
    val way = brewFit(samples(115_000) { curve.waterAt(it) + 100.0 }, 115_000, curve, config)
    assertEquals(0, way.score)
  }

  @Test
  fun `half the tolerated error scores 50`() {
    val fit = brewFit(samples(115_000) { curve.waterAt(it) + 12.5 }, 115_000, curve, config)
    assertEquals(50, fit.score)
  }

  @Test
  fun `a brew that stops before the recipe is penalised over the whole window`() {
    // Calca la curva hasta los 20 s y se queda en ese agua (40 g) hasta el final de la ventana.
    val stopped = brewFit(samples(20_000) { curve.waterAt(it) }, 20_000, curve, config)
    assertNotNull(stopped.score)
    assertTrue("score ${stopped.score}", stopped.score!! < 50)
    assertEquals(-95_000L, stopped.timeDiffMs(20_000))
  }

  @Test
  fun `the window grows to the real end when the brew keeps going after the last stage`() {
    // Calcada hasta los 115 s, y 60 s más con el agua final: sin error extra.
    val long = brewFit(samples(175_000) { curve.waterAt(it) }, 175_000, curve, config)
    assertEquals(100, long.score)
    // Con 20 g de más a partir de 115 s, la ventana más larga arrastra el error medio.
    val over = brewFit(samples(175_000) { if (it <= 115_000) curve.waterAt(it) else 270.0 }, 175_000, curve, config)
    assertTrue(over.score!! in 1..99)
    assertEquals(60_000L, over.timeDiffMs(175_000))
  }

  @Test
  fun `final differences`() {
    val fit = BrewFit(score = 86, targetWaterG = 250.0, targetEndMs = 115_000)
    assertEquals(-4.0, fit.waterDiffG(246.0), 1e-9)
    assertEquals(-0.016, fit.waterDiffShare(246.0), 1e-9)
    assertEquals(3_000L, fit.timeDiffMs(118_000))
  }

  @Test
  fun `no valid samples gives a null score but keeps the target`() {
    val none = brewFit(emptyList(), 10_000, curve, config)
    assertNull(none.score)
    assertEquals(250.0, none.targetWaterG, 0.0)
    assertEquals(115_000L, none.targetEndMs)
    val invalid = brewFit(listOf(BrewSample(0, 1.0, 0.0, null, valid = false)), 10_000, curve, config)
    assertNull(invalid.score)
  }

  @Test
  fun `invalid samples do not count as real water`() {
    val good = samples(115_000) { curve.waterAt(it) }
    // Hueco en plena meseta (65-90 s): el último agua válido coincide con el objetivo.
    val withHole = good.map { if (it.tMs in 70_000..80_000) it.copy(waterG = 9_999.0, valid = false) else it }
    assertEquals(100, brewFit(withHole, 115_000, curve, config).score)
  }

  @Test
  fun `before the first valid sample the real water is zero`() {
    val late = samples(115_000) { curve.waterAt(it) }.filter { it.tMs >= 5_000 }
    val fit = brewFit(late, 115_000, curve, config)
    assertTrue(fit.score!! in 90..99)
  }
}
