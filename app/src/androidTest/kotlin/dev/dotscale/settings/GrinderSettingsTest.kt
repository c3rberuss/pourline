package dev.dotscale.settings

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class GrinderSettingsTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val prefs get() = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

  @Before
  @After
  fun clear() {
    prefs.edit().clear().commit()
  }

  @Test
  fun aFreshInstallHasNoDefaultGrinderAndCatalogVersionZero() {
    val settings = GrinderSettings(context, NAME)
    assertNull(settings.defaultGrinderId.value)
    assertEquals(0, settings.catalogVersion)
  }

  @Test
  fun theDefaultGrinderAndTheVersionPersistAcrossInstances() {
    val settings = GrinderSettings(context, NAME)
    settings.setDefaultGrinderId(7)
    settings.setCatalogVersion(3)
    assertEquals(7L, settings.defaultGrinderId.value)

    val again = GrinderSettings(context, NAME)
    assertEquals(7L, again.defaultGrinderId.value)
    assertEquals(3, again.catalogVersion)
  }

  @Test
  fun settingTheDefaultToNullRemovesTheKey() {
    val settings = GrinderSettings(context, NAME)
    settings.setDefaultGrinderId(7)
    settings.setDefaultGrinderId(null)
    assertNull(settings.defaultGrinderId.value)
    assertFalse(prefs.contains(GrinderSettings.KEY_DEFAULT_GRINDER))
    assertNull(GrinderSettings(context, NAME).defaultGrinderId.value)
  }

  @Test
  fun anIdOfZeroIsAValidDefault() {
    GrinderSettings(context, NAME).setDefaultGrinderId(0)
    assertEquals(0L, GrinderSettings(context, NAME).defaultGrinderId.value)
  }

  private companion object {
    const val NAME = "grinder_settings_test"
  }
}
