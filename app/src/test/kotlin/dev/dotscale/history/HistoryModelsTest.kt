package dev.dotscale.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryModelsTest {

  @Test
  fun `canonicalCoffee reuses the existing spelling ignoring case and accents`() {
    assertEquals("Etiopía", canonicalCoffee("etiopía ", listOf("Kenia", "Etiopía")))
    assertEquals("Etiopía", canonicalCoffee("ETIOPÍA", listOf("Etiopía")))
  }

  @Test
  fun `canonicalCoffee without a match keeps the trimmed input`() {
    assertEquals("Colombia", canonicalCoffee("  Colombia ", listOf("Etiopía")))
    assertEquals("Etiopia", canonicalCoffee("Etiopia", listOf("Etiopía")))
  }

  @Test
  fun `canonicalCoffee of blank text is null`() {
    assertNull(canonicalCoffee("   ", listOf("Etiopía")))
    assertNull(canonicalCoffee(null, emptyList()))
  }

  @Test
  fun `normalized trims texts and turns blanks into null`() {
    val details = BrewDetails(
      name = "  Mañana ",
      coffee = "",
      method = BrewMethod.V60,
      methodOther = "Otro método",
      grind = " 21 clics ",
      waterTempC = 92.5,
      notes = "   ",
    ).normalized()
    assertEquals(BrewDetails("Mañana", null, BrewMethod.V60, null, "21 clics", 92.5, null), details)
  }

  @Test
  fun `normalized keeps methodOther only with OTHER`() {
    assertEquals("Sifón", BrewDetails(method = BrewMethod.OTHER, methodOther = " Sifón ").normalized().methodOther)
    assertNull(BrewDetails(method = null, methodOther = "Sifón").normalized().methodOther)
  }

  @Test
  fun `normalized keeps a rating between 1 and 5 and drops any other`() {
    assertEquals(1, BrewDetails(rating = 1).normalized().rating)
    assertEquals(5, BrewDetails(rating = 5).normalized().rating)
    assertNull(BrewDetails(rating = 0).normalized().rating)
    assertNull(BrewDetails(rating = 6).normalized().rating)
    assertNull(BrewDetails(rating = -3).normalized().rating)
    assertNull(BrewDetails(rating = null).normalized().rating)
  }
}
