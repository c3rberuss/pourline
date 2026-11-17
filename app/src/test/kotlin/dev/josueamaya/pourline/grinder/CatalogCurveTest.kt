package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.history.BrewMethod
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La puerta del Femobook A2 (§3.6 de la enmienda 1): las curvas del catálogo real y la conversión Chestnut S3 ↔ A2. */
class CatalogCurveTest {
  private val catalog = GrinderCatalogFile.parse(File("src/main/assets/grinders/catalog.json").readText()).getOrThrow()
  private val s3 = catalog.grinders.first { it.catalogKey == "timemore-chestnut-s3" }.copy(id = 101)
  private val a2 = catalog.grinders.first { it.catalogKey == "femobook-a2" }.copy(id = 102)
  private val a2WithoutMoka = a2.copy(ranges = a2.ranges - BrewMethod.MOKA)

  private fun convert(value: Double, from: Grinder, to: Grinder) = GrindConverter.convert(value, from, to, emptyList())

  private fun describe(conversion: Conversion, to: Grinder): String = when (conversion) {
    is Conversion.Exact -> GrindFormat.format(conversion.value, to, FakeGrindTexts) + " [" + conversion.value + "]"
    is Conversion.Approximate ->
      GrindFormat.format(conversion.value, to, FakeGrindTexts) + " [" + conversion.value + "] " + conversion.reasons.joinToString("+")
    Conversion.NoEquivalent -> "sin equivalencia"
  }

  @Test
  fun `gate report`() {
    println("METHOD_LEVELS")
    for ((method, level) in METHOD_LEVELS) println("  $method = $level")
    for ((name, grinder) in listOf("con moka" to a2, "sin moka" to a2WithoutMoka)) {
      val curve = GrindCurve.of(grinder) as GrindCurve.Linear
      println("A2 $name: a=${"%.4f".format(curve.a)} b=${"%.4f".format(curve.b)}")
      println("  anclas equivalentes: " + GrindLevel.entries.joinToString(" ") { "${it.name}=${"%.1f".format(curve.value(it.ordinal.toDouble()))}" })
      println("S3 -> A2 ($name)")
      for (clics in 0..90 step 10) println("  S3 $clics -> A2 " + describe(convert(clics.toDouble(), s3, grinder), grinder))
      println("A2 -> S3 ($name)")
      for (clics in 0..120 step 20) println("  A2 $clics -> S3 " + describe(convert(clics.toDouble(), grinder, s3), s3))
    }
  }

  @Test
  fun `the A2 has a growing curve with and without moka`() {
    for (grinder in listOf(a2, a2WithoutMoka)) {
      val curve = GrindCurve.of(grinder)
      assertNotNull(curve)
      val levels = (0..6).map { curve!!.value(it.toDouble()) }
      assertTrue("$levels", levels.zipWithNext().all { (a, b) -> b > a })
    }
    assertNotNull(GrindCurve.of(s3))
  }
}
