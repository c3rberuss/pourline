package dev.dotscale.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RatioCalculatorTest {
  private val es = Locale.forLanguageTag("es-ES")

  private fun start(dose: Double = 15.0, ratio: Double = 16.0) = RatioCalculator.initial(dose, ratio, es)

  private fun RatioCalculatorState.edit(field: RatioField, text: String) = RatioCalculator.edit(this, field, text, es)

  @Test
  fun theInitialStateComputesTheWater() {
    assertEquals(RatioCalculatorState("15", "16", "240"), start())
  }

  @Test
  fun changingTheCoffeeRecomputesTheWater() {
    val state = start().edit(RatioField.DOSE, "20")
    assertEquals("20", state.dose)
    assertEquals("16", state.ratio)
    assertEquals("320", state.water)
    assertTrue(state.invalid.isEmpty())
  }

  @Test
  fun changingTheRatioRecomputesTheWater() {
    val state = start().edit(RatioField.RATIO, "15,5")
    assertEquals("15", state.dose)
    assertEquals("232,5", state.water)
  }

  @Test
  fun changingTheWaterRecomputesTheCoffeeAndKeepsTheRatio() {
    val state = start().edit(RatioField.WATER, "250")
    assertEquals("15,6", state.dose)
    assertEquals("16", state.ratio)
    assertEquals("250", state.water)
  }

  @Test
  fun aDotWorksAsDecimalSeparatorToo() {
    assertEquals("247,5", start().edit(RatioField.RATIO, "16.5").water)
  }

  @Test
  fun anInvalidValueIsKeptAsTypedAndChangesNothingElse() {
    val state = start().edit(RatioField.DOSE, "abc")
    assertEquals("abc", state.dose)
    assertEquals("240", state.water)
    assertEquals(setOf(RatioField.DOSE), state.invalid)
  }

  @Test
  fun anEmptyFieldIsInvalid() {
    assertEquals(setOf(RatioField.WATER), start().edit(RatioField.WATER, "").invalid)
  }

  @Test
  fun fixingAnInvalidValueClearsItsError() {
    val state = start().edit(RatioField.DOSE, "").edit(RatioField.DOSE, "18")
    assertTrue(state.invalid.isEmpty())
    assertEquals("288", state.water)
  }

  @Test
  fun withoutAValidRatioTheCoffeeAndWaterGiveTheRatio() {
    val noRatio = start().edit(RatioField.RATIO, "")
    val state = noRatio.edit(RatioField.WATER, "300")
    assertEquals("20", state.ratio)
    assertTrue(state.invalid.isEmpty())

    val byDose = noRatio.edit(RatioField.DOSE, "12")
    assertEquals("20", byDose.ratio)
  }

  @Test
  fun withoutAValidCoffeeTheRatioAndWaterGiveTheCoffee() {
    val state = start().edit(RatioField.DOSE, "").edit(RatioField.RATIO, "15")
    assertEquals("16", state.dose)
    assertEquals("240", state.water)
    assertTrue(state.invalid.isEmpty())
  }

  @Test
  fun theRatioMustBeBetweenOneAndFifty() {
    assertEquals(setOf(RatioField.RATIO), start().edit(RatioField.RATIO, "0,5").invalid)
    assertEquals(setOf(RatioField.RATIO), start().edit(RatioField.RATIO, "51").invalid)
    assertTrue(start().edit(RatioField.RATIO, "2").invalid.isEmpty())
  }

  @Test
  fun zeroAndNegativeAmountsAreInvalid() {
    assertEquals(setOf(RatioField.DOSE), start().edit(RatioField.DOSE, "0").invalid)
    assertEquals(setOf(RatioField.WATER), start().edit(RatioField.WATER, "-5").invalid)
  }

  @Test
  fun aComputedValueOutOfRangeIsMarked() {
    // 1 g de agua a 1:16 son 0,06 g de café: se muestra "0,1" y vale.
    assertTrue(start().edit(RatioField.WATER, "1").invalid.isEmpty())
    // 0,4 g de agua a 1:16 dan 0,025 g de café, que se muestra como 0.
    val state = start().edit(RatioField.WATER, "0,4")
    assertEquals("0", state.dose)
    assertEquals(setOf(RatioField.DOSE), state.invalid)
  }

  @Test
  fun parseRejectsNonNumbers() {
    assertNull(RatioCalculator.parse(RatioField.DOSE, "1,2,3"))
    assertNull(RatioCalculator.parse(RatioField.DOSE, "NaN"))
    assertEquals(16.5, RatioCalculator.parse(RatioField.RATIO, " 16,5 ")!!, 1e-9)
  }

  @Test
  fun formatDropsTheTrailingZero() {
    assertEquals("15", RatioCalculator.format(15.0, es))
    assertEquals("15", RatioCalculator.format(14.96, es))
    assertEquals("16,7", RatioCalculator.format(50.0 / 3, es))
    assertEquals("16.7", RatioCalculator.format(50.0 / 3, Locale.US))
  }
}
