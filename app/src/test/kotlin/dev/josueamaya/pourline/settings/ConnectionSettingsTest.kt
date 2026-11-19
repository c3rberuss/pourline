package dev.josueamaya.pourline.settings

import dev.josueamaya.pourline.session.RememberedScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El modo de báscula: lo elegido manda y, sin elegir, depende de si hay una báscula recordada. */
class ConnectionSettingsTest {

  private val prefs = FakeSharedPreferences()

  @Test
  fun `without a choice nor a remembered scale it brews without a scale`() {
    assertFalse(ConnectionSettings(prefs).useScale.value)
  }

  @Test
  fun `without a choice a remembered scale means brewing with it`() {
    ConnectionSettings(prefs).remember(RememberedScale("AA:BB", "DOT"))

    assertTrue(ConnectionSettings(prefs).useScale.value)
  }

  @Test
  fun `the choice is published, saved and wins over the remembered scale`() {
    val settings = ConnectionSettings(prefs)
    settings.remember(RememberedScale("AA:BB", "DOT"))

    settings.setUseScale(false)

    assertFalse(settings.useScale.value)
    assertEquals(false, prefs.values[ConnectionSettings.KEY_USE_SCALE])
    assertFalse(ConnectionSettings(prefs).useScale.value)
  }
}
