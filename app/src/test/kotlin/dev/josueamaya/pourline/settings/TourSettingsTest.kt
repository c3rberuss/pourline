package dev.josueamaya.pourline.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TourSettingsTest {

  private val prefs = FakeSharedPreferences()

  @Test
  fun `starts empty`() {
    assertTrue(TourSettings(prefs).seen.value.isEmpty())
  }

  @Test
  fun `mark seen adds and publishes the ids`() {
    val settings = TourSettings(prefs)

    settings.markSeen(listOf("a.one", "a.two"))

    assertEquals(setOf("a.one", "a.two"), settings.seen.value)
    assertEquals(setOf("a.one", "a.two"), prefs.values[TourSettings.KEY_SEEN])
  }

  @Test
  fun `mark seen twice keeps the union and writes once`() {
    val settings = TourSettings(prefs)

    settings.markSeen(listOf("a.one"))
    settings.markSeen(listOf("a.one"))
    assertEquals(1, prefs.writes)

    settings.markSeen(listOf("a.one", "b.two"))
    assertEquals(setOf("a.one", "b.two"), settings.seen.value)
    assertEquals(2, prefs.writes)
  }

  @Test
  fun `reset empties the set and removes the key`() {
    val settings = TourSettings(prefs)
    settings.markSeen(listOf("a.one"))

    settings.reset()

    assertTrue(settings.seen.value.isEmpty())
    assertFalse(prefs.contains(TourSettings.KEY_SEEN))
  }

  @Test
  fun `a new instance reads what the previous one wrote`() {
    TourSettings(prefs).markSeen(listOf("a.one", "b.two"))

    assertEquals(setOf("a.one", "b.two"), TourSettings(prefs).seen.value)
  }

  @Test
  fun `a value of another type under tour_seen reads as empty`() {
    prefs.values[TourSettings.KEY_SEEN] = "no es un conjunto"

    val settings = TourSettings(prefs)

    assertTrue(settings.seen.value.isEmpty())
    // Y se puede seguir usando: la siguiente escritura lo reemplaza.
    settings.markSeen(listOf("a.one"))
    assertEquals(setOf("a.one"), TourSettings(prefs).seen.value)
  }

  @Test
  fun `unknown ids are kept but do not matter`() {
    val settings = TourSettings(prefs)

    settings.markSeen(listOf("gone.step"))

    assertEquals(setOf("gone.step"), TourSettings(prefs).seen.value)
  }

  @Test
  fun `onboarding_done does not change the seen steps`() {
    val settings = TourSettings(prefs)
    settings.markSeen(listOf("a.one"))

    prefs.edit().putBoolean(ExperienceSettings.KEY_ONBOARDING_DONE, true).putString(ExperienceSettings.KEY_MODE, "EXPERT").apply()

    assertEquals(setOf("a.one"), TourSettings(prefs).seen.value)
    // Y al revés: reiniciar los recorridos no toca el primer arranque.
    settings.reset()
    assertTrue(prefs.getBoolean(ExperienceSettings.KEY_ONBOARDING_DONE, false))
  }
}
