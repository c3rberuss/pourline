package dev.dotscale.grinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Valida el archivo del catálogo real (`assets/grinders/catalog.json`): lo que impide publicar uno inválido. */
class GrinderCatalogFileTest {
  private val file = File("src/main/assets/grinders/catalog.json")

  private companion object {
    /** Los molinos aprobados por el usuario en la puerta de F2 (2026-11-16), en el orden del archivo. */
    val APPROVED_KEYS = listOf(
      "timemore-chestnut-c2",
      "timemore-chestnut-c3s",
      "timemore-chestnut-s3",
      "comandante-c40",
      "1zpresso-jx-pro",
      "1zpresso-k-ultra",
      "1zpresso-q2",
      "kingrinder-k6",
      "baratza-encore",
      "fellow-ode-gen-2",
    )
  }

  private fun load(): CatalogData {
    assertTrue("falta ${file.path}", file.exists())
    return GrinderCatalogFile.parse(file.readText()).getOrElse { throw AssertionError("catálogo inválido: ${it.message}") }
  }

  @Test
  fun theFileIsReadAndEveryGrinderPassesTheValidator() {
    val data = load()
    assertTrue(data.version >= 1)
    assertTrue(data.grinders.isNotEmpty())
    data.grinders.forEach { assertEquals(it.catalogKey, emptyList<GrinderError>(), GrinderValidator.validate(it)) }
  }

  @Test
  fun theCatalogHoldsExactlyTheApprovedGrinders() {
    val data = load()
    assertEquals(1, data.version)
    assertEquals(APPROVED_KEYS, data.grinders.map { it.catalogKey })
  }

  @Test
  fun keysAreUniqueAndEveryGrinderNamesItsSource() {
    val data = load()
    val keys = data.grinders.map { it.catalogKey }
    assertEquals(keys.distinct(), keys)
    data.grinders.forEach {
      assertNotNull(it.catalogKey, it.catalogKey)
      assertTrue(it.catalogKey, it.sourceUrl!!.startsWith("https://"))
      assertTrue(it.catalogKey, !it.sourceName.isNullOrBlank())
    }
  }

  @Test
  fun rangesFitTheScaleAndAnchorsAreMonotonic() {
    val data = load()
    data.grinders.forEach { g ->
      g.ranges.forEach { (method, r) ->
        assertTrue("${g.catalogKey} $method", r.min < r.max && r.min >= g.scaleMin && r.max <= g.scaleMax)
      }
      val values = GrindLevel.entries.mapNotNull { g.anchors[it] }
      assertEquals(g.catalogKey, values.sorted(), values)
      assertEquals(g.catalogKey, values.distinct(), values)
    }
  }

  @Test
  fun anchorsFollowTheLinearFormulaUnlessAdjustedByHand() {
    val bands = mapOf(
      GrindLevel.EXTRA_FINE to 100.0,
      GrindLevel.FINE to 300.0,
      GrindLevel.MEDIUM_FINE to 500.0,
      GrindLevel.MEDIUM to 700.0,
      GrindLevel.MEDIUM_COARSE to 900.0,
      GrindLevel.COARSE to 1100.0,
      GrindLevel.EXTRA_COARSE to 1300.0,
    )
    val data = load()
    data.entries.filter { !it.anchorsManual }.forEach { e ->
      val g = e.grinder
      val microns = e.micronRange ?: return@forEach
      bands.forEach { (level, mid) ->
        val anchor = g.anchors[level]
        if (mid < microns.min || mid > microns.max) {
          assertEquals("${g.catalogKey} $level debe omitirse", null, anchor)
        } else {
          val expected = g.scaleMin + (mid - microns.min) / (microns.max - microns.min) * (g.scaleMax - g.scaleMin)
          assertNotNull("${g.catalogKey} $level", anchor)
          assertTrue("${g.catalogKey} $level: $anchor frente a $expected", abs(anchor!! - expected) <= 0.05)
        }
      }
    }
  }
}
