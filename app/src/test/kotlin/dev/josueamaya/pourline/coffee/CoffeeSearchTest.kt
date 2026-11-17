package dev.josueamaya.pourline.coffee

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoffeeSearchTest {
  private val item = CoffeeListItem(
    id = 1, name = "Guji Hambela", roaster = "Café de Altura", originCountry = "Etiopía", originRegion = "Sidama",
    roastDate = null, daysSinceRoast = null, stockG = null, soldOut = false, photo1 = null, archived = false,
  )

  @Test
  fun `matches name roaster country and region`() {
    assertTrue(CoffeeSearch.matches(item, "hambela"))
    assertTrue(CoffeeSearch.matches(item, "altura"))
    assertTrue(CoffeeSearch.matches(item, "etiop"))
    assertTrue(CoffeeSearch.matches(item, "sidama"))
  }

  @Test
  fun `ignores case and accents`() {
    assertTrue(CoffeeSearch.matches(item, "ETIOPIA"))
    assertTrue(CoffeeSearch.matches(item, "cafe de"))
  }

  @Test
  fun `an empty query matches everything`() {
    assertTrue(CoffeeSearch.matches(item, ""))
    assertTrue(CoffeeSearch.matches(item, "   "))
  }

  @Test
  fun `does not look at anything else`() {
    assertFalse(CoffeeSearch.matches(item, "jazmín"))
    assertFalse(CoffeeSearch.matches(item, "kenia"))
  }
}
