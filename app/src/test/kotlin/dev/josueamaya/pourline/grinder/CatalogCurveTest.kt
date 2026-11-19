package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.history.BrewMethod
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La conversión Chestnut S3 ↔ Femobook A2 con las curvas del catálogo real (§3.6 de la enmienda 1), tal como la aprobó
 * el usuario en la puerta del 2026-11-17: el A2 sin rango de moka (catálogo versión 2). Un cambio del catálogo o de la
 * curva que mueva esta tabla hace fallar el test.
 */
class CatalogCurveTest {
  private val catalog = GrinderCatalogFile.parse(File("src/main/assets/grinders/catalog.json").readText()).getOrThrow()
  private val s3 = catalog.grinders.first { it.catalogKey == "timemore-chestnut-s3" }.copy(id = 101)
  private val a2 = catalog.grinders.first { it.catalogKey == "femobook-a2" }.copy(id = 102)

  private fun convert(value: Double, from: Grinder, to: Grinder) = GrindConverter.convert(value, from, to, emptyList())

  /** Aserta el valor y que la conversión es por curvas (`ANCHORS`, nunca `CALIBRATED`), con `AT_LIMIT` solo donde se indica. */
  private fun assertCurve(expected: Double, atLimit: Boolean, actual: Conversion) {
    assertTrue("$actual", actual is Conversion.Approximate)
    actual as Conversion.Approximate
    assertEquals(expected, actual.value, 0.0)
    assertTrue("$actual", ConversionReason.ANCHORS in actual.reasons)
    assertFalse("$actual", ConversionReason.CALIBRATED in actual.reasons)
    assertEquals("$actual", atLimit, ConversionReason.AT_LIMIT in actual.reasons)
  }

  @Test
  fun `S3 and A2 convert both ways with the approved curves`() {
    // S3 → A2: 0, 10 … 90 clics del S3. Desde S3 90 el A2 se queda sin margen (120).
    val toA2 = listOf(0 to 22.0, 10 to 34.0, 20 to 45.0, 30 to 57.0, 40 to 68.0, 50 to 80.0, 60 to 91.0, 70 to 102.0, 80 to 114.0, 90 to 120.0)
    for ((clics, expected) in toA2) assertCurve(expected, atLimit = clics == 90, convert(clics.toDouble(), s3, a2))
    // A2 → S3: 0, 20 … 120 clics del A2. Hasta A2 20 el S3 se queda sin margen por abajo (0).
    val toS3 = listOf(0 to 0.0, 20 to 0.0, 40 to 15.0, 60 to 33.0, 80 to 50.0, 100 to 68.0, 120 to 85.0)
    for ((clics, expected) in toS3) assertCurve(expected, atLimit = clics <= 20, convert(clics.toDouble(), a2, s3))
  }

  @Test
  fun `the A2 has a growing curve and no moka range`() {
    assertFalse(BrewMethod.MOKA in a2.ranges)
    val curve = GrindCurve.of(a2)
    assertNotNull(curve)
    val levels = (0..6).map { curve!!.value(it.toDouble()) }
    assertTrue("$levels", levels.zipWithNext().all { (a, b) -> b > a })
    assertNotNull(GrindCurve.of(s3))
  }
}
