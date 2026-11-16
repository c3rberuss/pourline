package dev.dotscale.settings

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/** La dosis y el ratio de Preparar: valores por defecto, persistencia y rangos (D27). */
class FreeBrewDefaultsTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val prefs get() = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

  @Before
  @After
  fun clear() {
    prefs.edit().clear().commit()
  }

  @Test
  fun aFreshInstallUses15gAndOneToSixteen() {
    val settings = BrewSettings(context, NAME)
    assertEquals(15.0, settings.defaultDoseG.value, 0.0)
    assertEquals(16.0, settings.defaultRatio.value, 0.0)
  }

  @Test
  fun theValuesPersistAcrossInstancesWithOneDecimal() {
    BrewSettings(context, NAME).apply {
      setDefaultDoseG(18.3)
      setDefaultRatio(15.7)
    }

    val again = BrewSettings(context, NAME)
    assertEquals(18.3, again.defaultDoseG.value, 0.0)
    assertEquals(15.7, again.defaultRatio.value, 0.0)
  }

  @Test
  fun valuesOutOfRangeAreRejected() {
    val settings = BrewSettings(context, NAME)
    assertThrows(IllegalArgumentException::class.java) { settings.setDefaultDoseG(4.9) }
    assertThrows(IllegalArgumentException::class.java) { settings.setDefaultDoseG(60.1) }
    assertThrows(IllegalArgumentException::class.java) { settings.setDefaultRatio(9.9) }
    assertThrows(IllegalArgumentException::class.java) { settings.setDefaultRatio(20.1) }
    assertEquals(15.0, settings.defaultDoseG.value, 0.0)
  }

  @Test
  fun aStoredValueOutOfRangeReadsAsTheDefault() {
    prefs.edit().putFloat(BrewSettings.KEY_DEFAULT_DOSE, 500f).putFloat(BrewSettings.KEY_DEFAULT_RATIO, 1f).commit()

    val settings = BrewSettings(context, NAME)
    assertEquals(15.0, settings.defaultDoseG.value, 0.0)
    assertEquals(16.0, settings.defaultRatio.value, 0.0)
  }

  private companion object {
    const val NAME = "free_brew_defaults_test"
  }
}
