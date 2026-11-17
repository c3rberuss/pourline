package dev.josueamaya.pourline.history

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryConvertersTest {
  private val converters = HistoryConverters()

  @Test
  fun everyMethodRoundTripsByName() {
    for (method in BrewMethod.entries) {
      assertEquals(method, converters.stringToBrewMethod(converters.brewMethodToString(method)))
    }
  }

  @Test
  fun theNewMethodsExist() {
    val names = BrewMethod.entries.map { it.name }
    assertEquals(true, listOf("FRENCH_PRESS", "ESPRESSO", "COLD_BREW", "MOKA").all { it in names })
  }

  @Test
  fun anUnknownNameIsReadAsOther() {
    assertEquals(BrewMethod.OTHER, converters.stringToBrewMethod("TURKISH"))
  }
}
