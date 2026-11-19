package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.grinder.GrinderFixtures.dos
import dev.josueamaya.pourline.grinder.GrinderFixtures.inverso
import dev.josueamaya.pourline.grinder.GrinderFixtures.sinCurva
import dev.josueamaya.pourline.grinder.GrinderFixtures.soloRangos
import dev.josueamaya.pourline.grinder.GrinderFixtures.tres
import dev.josueamaya.pourline.grinder.GrinderFixtures.uno
import dev.josueamaya.pourline.history.BrewMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrindCurveTest {
  private fun curve(grinder: Grinder) = GrindCurve.of(grinder)!!

  @Test
  fun `an explicit curve interpolates every anchor of the grinder`() {
    val c = curve(uno)
    assertTrue(c is GrindCurve.Explicit)
    // Uno: FINE 8 (nivel 1), MEDIUM 20 (nivel 3), COARSE 32 (nivel 5).
    assertEquals(1.0, c.level(8.0), 1e-9)
    assertEquals(2.0, c.level(14.0), 1e-9)
    assertEquals(3.0, c.level(20.0), 1e-9)
    assertEquals(5.0, c.level(32.0), 1e-9)
    assertEquals(14.0, c.value(2.0), 1e-9)
  }

  @Test
  fun `an explicit curve extrapolates with the end segments`() {
    val c = curve(uno)
    assertEquals(0.0, c.level(2.0), 1e-9)
    assertEquals(6.0, c.level(38.0), 1e-9)
    assertEquals(38.0, c.value(6.0), 1e-9)
  }

  @Test
  fun `an inverted grinder gives growing levels for lower settings`() {
    val c = curve(inverso)
    assertEquals(1.0, c.level(40.0), 1e-9)
    assertEquals(3.0, c.level(25.0), 1e-9)
    assertEquals(5.0, c.level(10.0), 1e-9)
    assertTrue(c.level(20.0) > c.level(30.0))
  }

  @Test
  fun `a single anchor is ignored and the ranges decide`() {
    val oneAnchor = soloRangos.copy(anchors = mapOf(GrindLevel.MEDIUM to 70.0))
    assertEquals(curve(soloRangos), curve(oneAnchor))
    // Sin rangos suficientes, un ancla no da curva.
    assertNull(GrindCurve.of(sinCurva.copy(anchors = mapOf(GrindLevel.MEDIUM to 20.0))))
  }

  @Test
  fun `an implicit curve is the least squares line of the range midpoints`() {
    val c = curve(soloRangos)
    assertTrue(c is GrindCurve.Linear)
    // Puntos (nivel del método, punto medio): V60 (2,25; 65), AeroPress (2,64; 45), Prensa francesa (3,93; 100).
    val xs = listOf(2.25, 2.64, 3.93)
    val ys = listOf(65.0, 45.0, 100.0)
    val mx = xs.average()
    val my = ys.average()
    val b = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / xs.sumOf { (it - mx) * (it - mx) }
    val a = my - b * mx
    val linear = c as GrindCurve.Linear
    assertEquals(a, linear.a, 1e-9)
    assertEquals(b, linear.b, 1e-9)
    assertEquals(a + 3 * b, c.value(3.0), 1e-9)
  }

  @Test
  fun `repeated points count once`() {
    val kalita = GrindRange(13.0, 29.0)
    val one = uno.copy(
      anchors = emptyMap(),
      ranges = mapOf(BrewMethod.KALITA_WAVE to kalita, BrewMethod.FRENCH_PRESS to GrindRange(22.0, 30.0)),
    )
    val three = one.copy(ranges = one.ranges + mapOf(BrewMethod.CHEMEX to kalita, BrewMethod.ORIGAMI to kalita))
    assertEquals(curve(one), curve(three))
  }

  @Test
  fun `no curve with fewer than two distinct levels`() {
    assertNull(GrindCurve.of(sinCurva))
    val sameLevel = sinCurva.copy(
      ranges = mapOf(BrewMethod.KALITA_WAVE to GrindRange(10.0, 30.0), BrewMethod.CHEMEX to GrindRange(10.0, 30.0)),
    )
    assertNull(GrindCurve.of(sameLevel))
    assertNull(GrindCurve.of(sinCurva.copy(ranges = emptyMap())))
    // OTHER no tiene nivel: un rango que no cuenta.
    assertNull(GrindCurve.of(sinCurva.copy(ranges = mapOf(BrewMethod.OTHER to GrindRange(1.0, 2.0)))))
  }

  @Test
  fun `no curve when the slope contradicts the direction`() {
    // Rangos que bajan al engrosar el método, en un molino donde subir es más grueso.
    val falling = soloRangos.copy(
      ranges = mapOf(BrewMethod.V60 to GrindRange(100.0, 120.0), BrewMethod.FRENCH_PRESS to GrindRange(20.0, 40.0)),
    )
    assertNull(GrindCurve.of(falling))
    // Y rangos que suben al engrosar el método, en un molino donde subir es más fino.
    val rising = falling.copy(
      direction = GrindDirection.HIGHER_IS_FINER,
      ranges = mapOf(BrewMethod.V60 to GrindRange(20.0, 40.0), BrewMethod.FRENCH_PRESS to GrindRange(100.0, 120.0)),
    )
    assertNull(GrindCurve.of(rising))
    // Con la dirección correcta sí hay curva.
    assertNotNull(GrindCurve.of(rising.copy(ranges = falling.ranges)))
  }

  @Test
  fun `level and value are inverse`() {
    for (grinder in listOf(uno, dos, tres, inverso, soloRangos)) {
      val c = curve(grinder)
      var v = grinder.scaleMin - 5
      while (v <= grinder.scaleMax + 5) {
        assertEquals("${grinder.model} $v", v, c.value(c.level(v)), 1e-9)
        v += 0.5
      }
    }
  }
}
