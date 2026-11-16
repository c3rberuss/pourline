package dev.dotscale.coffee

import org.junit.Assert.assertEquals
import org.junit.Test

class CoffeeSpellingTest {
  private val existing = CoffeeSuggestions(
    roasters = listOf("Café de Altura"),
    countries = listOf("Etiopía"),
    regions = listOf("Guji"),
    varieties = listOf("Heirloom"),
  )

  @Test
  fun `reuses the existing spelling`() {
    val draft = CoffeeDraft(name = "x", roaster = "café de altura", originCountry = "etiopía", originRegion = "GUJI")
    val result = canonicalize(draft, existing)
    assertEquals("Café de Altura", result.roaster)
    assertEquals("Etiopía", result.originCountry)
    assertEquals("Guji", result.originRegion)
  }

  @Test
  fun `a new variety keeps what was typed`() {
    val result = canonicalize(CoffeeDraft(name = "x", varieties = listOf("heirloom", "Sidra")), existing)
    assertEquals(listOf("Heirloom", "Sidra"), result.varieties)
  }

  @Test
  fun `repeated varieties are merged`() {
    val result = canonicalize(CoffeeDraft(name = "x", varieties = listOf("sidra", "SIDRA", "Sidra")), existing)
    assertEquals(listOf("sidra"), result.varieties)
  }
}
