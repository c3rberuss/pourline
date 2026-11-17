package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.grinder.GrinderFixtures.cinco
import dev.josueamaya.pourline.grinder.GrinderFixtures.dos
import dev.josueamaya.pourline.grinder.GrinderFixtures.seis
import dev.josueamaya.pourline.grinder.GrinderFixtures.tres
import dev.josueamaya.pourline.grinder.GrinderFixtures.uno
import org.junit.Assert.assertEquals
import org.junit.Test

class GrindFormatTest {
  private val texts = FakeGrindTexts

  @Test
  fun compoundSettingComposesTo63ClicksAndBack() {
    assertEquals(63.0, GrindFormat.compose(listOf(1.0, 2.0, 3.0), tres.units), 0.0)
    assertEquals(listOf(1.0, 2.0, 3.0), GrindFormat.decompose(63.0, tres.units))
    assertEquals("1 · 2 · 3", GrindFormat.format(63.0, tres, texts))
  }

  @Test
  fun roundTripOfEveryMultipleOfTheStep() {
    for (g in GrinderFixtures.all) {
      val steps = Math.round((g.scaleMax - g.scaleMin) / g.step).toInt()
      for (n in 0..steps) {
        val v = snap(g.scaleMin + n * g.step, g)
        val back = GrindFormat.compose(GrindFormat.decompose(v, g.units), g.units)
        assertEquals("${g.model} en $v", v, back, 1e-9)
      }
    }
  }

  @Test
  fun singleUnitShowsTheNumberWithItsUnitAndPlural() {
    assertEquals("30 clics", GrindFormat.format(30.0, uno, texts))
    assertEquals("1 clic", GrindFormat.format(1.0, uno, texts))
    assertEquals("15,5 clics", GrindFormat.format(15.5, dos, texts))
    assertEquals("0,3 números", GrindFormat.format(0.3, cinco, texts))
  }

  @Test
  fun longFormIsUsedForAccessibility() {
    assertEquals("1 rotación, 2 números, 3 clics", GrindFormat.describe(63.0, tres, texts))
    assertEquals("30 clics", GrindFormat.describe(30.0, uno, texts))
  }

  @Test
  fun labelHasTheModelAndTheSetting() {
    assertEquals("Uno · 30 clics", GrindFormat.label(uno, 30.0, texts))
    assertEquals("Uno", GrindFormat.label(uno, null, texts))
    assertEquals("Tres · 1 · 2 · 3", GrindFormat.label(tres, 63.0, texts))
  }

  @Test
  fun aScaleThatStartsAtThreeDecomposesAbsolutely() {
    // Números de 3 puntos: el valor 3 (el mínimo de la escala) es "1 · 0".
    assertEquals("1 · 0,0", GrindFormat.format(3.0, seis, texts))
    assertEquals(listOf(1.0, 0.0), GrindFormat.decompose(3.0, seis.units))
    assertEquals("2 · 1,5", GrindFormat.format(7.5, seis, texts))
  }

  @Test
  fun stepOfOneTenthHasNoFloatingNoise() {
    val v = snap(0.1 * 3, cinco)
    assertEquals(0.3, v, 0.0)
    assertEquals(1, decimalsOf(cinco.step))
    assertEquals(0, decimalsOf(1.0))
    assertEquals(2, decimalsOf(0.25))
    assertEquals(3, decimalsOf(0.0125))
  }
}
