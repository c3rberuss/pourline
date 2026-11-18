package dev.josueamaya.pourline.history

import dev.josueamaya.pourline.recipe.StageType
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
  fun `stage types round trip by name and an unknown one is read as a pour`() {
    for (type in StageType.entries) {
      assertEquals(type, converters.stringToStageType(converters.stageTypeToString(type)))
    }
    assertEquals("ACTION", converters.stageTypeToString(StageType.ACTION))
    assertEquals(4, StageType.entries.size)
    assertEquals(StageType.POUR, converters.stringToStageType("OTRO"))
  }

  @Test
  fun anUnknownNameIsReadAsOther() {
    assertEquals(BrewMethod.OTHER, converters.stringToBrewMethod("TURKISH"))
  }
}
