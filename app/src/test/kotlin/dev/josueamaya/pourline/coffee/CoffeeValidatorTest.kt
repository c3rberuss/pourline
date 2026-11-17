package dev.josueamaya.pourline.coffee

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoffeeValidatorTest {
  private val today = LocalDate.of(2026, 11, 13)
  private val valid = CoffeeDraft(name = "Etiopía Guji")

  private fun errors(draft: CoffeeDraft) = CoffeeValidator.validate(draft, today)

  @Test
  fun `a name is required`() {
    assertTrue(errors(valid).isEmpty())
    assertEquals(setOf(CoffeeError.NAME_REQUIRED), errors(valid.copy(name = "")))
    assertEquals(setOf(CoffeeError.NAME_REQUIRED), errors(valid.copy(name = "   ")))
  }

  @Test
  fun `weight price and altitude must be positive`() {
    assertEquals(setOf(CoffeeError.BAG_WEIGHT_INVALID), errors(valid.copy(bagWeightG = 0.0)))
    assertEquals(setOf(CoffeeError.BAG_WEIGHT_INVALID), errors(valid.copy(bagWeightG = -5.0)))
    assertEquals(setOf(CoffeeError.PRICE_INVALID), errors(valid.copy(price = 0.0)))
    assertEquals(setOf(CoffeeError.ALTITUDE_INVALID), errors(valid.copy(altitudeMinM = 0, altitudeMaxM = 100)))
    assertEquals(setOf(CoffeeError.ALTITUDE_INVALID), errors(valid.copy(altitudeMinM = 1800, altitudeMaxM = -2)))
  }

  @Test
  fun `from must not exceed to`() {
    assertEquals(setOf(CoffeeError.ALTITUDE_RANGE), errors(valid.copy(altitudeMinM = 2000, altitudeMaxM = 1800)))
    assertTrue(errors(valid.copy(altitudeMinM = 1800, altitudeMaxM = 1800)).isEmpty())
    assertTrue(errors(valid.copy(altitudeMinM = 1800, altitudeMaxM = 2000)).isEmpty())
  }

  @Test
  fun `a single altitude value is valid`() {
    assertTrue(errors(valid.copy(altitudeMinM = 1900)).isEmpty())
    assertTrue(errors(valid.copy(altitudeMaxM = 1900)).isEmpty())
  }

  @Test
  fun `future dates are rejected and today is valid`() {
    assertEquals(setOf(CoffeeError.ROAST_DATE_FUTURE), errors(valid.copy(roastDate = today.plusDays(1))))
    assertEquals(setOf(CoffeeError.OPENED_DATE_FUTURE), errors(valid.copy(openedDate = today.plusDays(1))))
    assertTrue(errors(valid.copy(roastDate = today, openedDate = today)).isEmpty())
  }

  @Test
  fun `normalized duplicates a single altitude and trims`() {
    val n = CoffeeDraft(name = " Guji ", roaster = "  ", altitudeMinM = 1900, varieties = listOf(" Heirloom", "", "heirloom"))
      .normalized()
    assertEquals("Guji", n.name)
    assertEquals(null, n.roaster)
    assertEquals(1900, n.altitudeMinM)
    assertEquals(1900, n.altitudeMaxM)
    assertEquals(listOf("Heirloom"), n.varieties)
  }

  @Test
  fun `processOther only survives with OTHER`() {
    assertEquals(null, CoffeeDraft(name = "x", process = CoffeeProcess.NATURAL, processOther = "Láctica").normalized().processOther)
    assertEquals(
      "Láctica",
      CoffeeDraft(name = "x", process = CoffeeProcess.OTHER, processOther = " Láctica ").normalized().processOther,
    )
  }
}
