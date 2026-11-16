package dev.dotscale.settings

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExperienceSettingsTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val prefs get() = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

  @Before
  @After
  fun clear() {
    prefs.edit().clear().commit()
  }

  @Test
  fun aFreshInstallIsBasicAndNotOnboarded() {
    val settings = ExperienceSettings(context, NAME)
    assertEquals(ExperienceMode.BASIC, settings.mode.value)
    assertFalse(settings.onboardingDone.value)
  }

  @Test
  fun anUnknownStoredValueReadsAsBasic() {
    prefs.edit().putString(ExperienceSettings.KEY_MODE, "GURU").commit()
    assertEquals(ExperienceMode.BASIC, ExperienceSettings(context, NAME).mode.value)
  }

  @Test
  fun setModePersistsAcrossInstancesWithoutMarkingTheOnboarding() {
    ExperienceSettings(context, NAME).setMode(ExperienceMode.EXPERT)
    val again = ExperienceSettings(context, NAME)
    assertEquals(ExperienceMode.EXPERT, again.mode.value)
    assertFalse(again.onboardingDone.value)
  }

  @Test
  fun completingTheOnboardingSavesTheModeAndTheFlag() {
    val settings = ExperienceSettings(context, NAME)
    settings.completeOnboarding(ExperienceMode.EXPERT)
    assertEquals(ExperienceMode.EXPERT, settings.mode.value)
    assertTrue(settings.onboardingDone.value)
    val again = ExperienceSettings(context, NAME)
    assertEquals(ExperienceMode.EXPERT, again.mode.value)
    assertTrue(again.onboardingDone.value)
  }

  @Test
  fun skippingKeepsBasicAndMarksTheOnboarding() {
    val settings = ExperienceSettings(context, NAME)
    settings.completeOnboarding(ExperienceMode.BASIC)
    assertEquals(ExperienceMode.BASIC, ExperienceSettings(context, NAME).mode.value)
    assertTrue(ExperienceSettings(context, NAME).onboardingDone.value)
  }

  private companion object {
    const val NAME = "experience_settings_test"
  }
}
