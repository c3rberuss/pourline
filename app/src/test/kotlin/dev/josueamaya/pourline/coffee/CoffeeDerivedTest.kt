package dev.josueamaya.pourline.coffee

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class CoffeeDerivedTest {
  @Test
  fun `stock is null without a bag weight`() {
    assertNull(stockOf(null, 33.0))
    assertFalse(isSoldOut(null))
  }

  @Test
  fun `stock subtracts the doses`() {
    assertEquals(217.0, stockOf(250.0, 15.0 + 18.0)!!, 0.0)
  }

  @Test
  fun `a sold out bag shows zero`() {
    val over = stockOf(250.0, 260.0)
    assertTrue(isSoldOut(over))
    assertEquals(0.0, displayStock(over!!), 0.0)
    assertTrue(isSoldOut(stockOf(250.0, 250.0)))
    assertFalse(isSoldOut(stockOf(250.0, 249.0)))
  }

  @Test
  fun `days since the roast`() {
    val roast = LocalDate.of(2026, 11, 4)
    assertEquals(0, daysSinceRoast(roast, roast))
    assertEquals(9, daysSinceRoast(roast, roast.plusDays(9)))
    assertNull(daysSinceRoast(null, roast))
    assertNull(daysSinceRoast(roast, roast.minusDays(1)))
  }

  @Test
  fun `a brew uses the local date of its start`() {
    val roast = LocalDate.of(2026, 11, 4)
    // 02:30 UTC del día 13 es el día 12 a las 21:30 en UTC-5.
    val started = LocalDate.of(2026, 11, 13).atTime(2, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
    assertEquals(8, brewDaysSinceRoast(roast, started, ZoneId.of("America/Lima")))
    assertEquals(9, brewDaysSinceRoast(roast, started, ZoneOffset.UTC))
  }

  @Test
  fun `a new bag copies everything but ids dates and photos`() {
    val coffee = CoffeeDraft(
      id = 7, name = "Guji", roaster = "Café de Altura", originCountry = "Etiopía", originRegion = "Guji",
      farm = "Hambela", varieties = listOf("Heirloom"), process = CoffeeProcess.NATURAL,
      altitudeMinM = 1800, altitudeMaxM = 2000, roastLevel = RoastLevel.LIGHT,
      roastDate = LocalDate.of(2026, 10, 12), roasterNotes = "Jazmín", bagWeightG = 250.0, price = 18.5,
      openedDate = LocalDate.of(2026, 10, 14), photo1 = "a.jpg", photo2 = "b.jpg",
    )
    val bag = newBagFrom(coffee)
    assertEquals(coffee.copy(id = null, roastDate = null, openedDate = null, photo1 = null, photo2 = null), bag)
    assertNull(bag.id)
    assertEquals("Hambela", bag.farm)
  }
}
