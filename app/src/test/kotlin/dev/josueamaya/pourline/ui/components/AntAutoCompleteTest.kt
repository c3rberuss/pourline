package dev.josueamaya.pourline.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AntAutoCompleteTest {
  private val suggestions = listOf("Etiopía", "Etiopía Guji", "Kenia")

  @Test
  fun `filters by prefix ignoring case`() {
    assertEquals(listOf("Etiopía", "Etiopía Guji"), autoCompleteMatches("eti", suggestions))
    assertEquals(listOf("Kenia"), autoCompleteMatches(" KEN", suggestions))
  }

  @Test
  fun `an empty text offers every suggestion`() {
    assertEquals(suggestions, autoCompleteMatches("", suggestions))
  }

  @Test
  fun `hides the menu when the text already matches a suggestion or nothing matches`() {
    assertTrue(autoCompleteMatches("etiopía", suggestions).isEmpty())
    assertTrue(autoCompleteMatches("Colombia", suggestions).isEmpty())
  }
}
