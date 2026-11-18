package dev.josueamaya.pourline.settings

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TourSettingsPersistenceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val prefs get() = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

  @Before
  @After
  fun clear() {
    context.deleteSharedPreferences(NAME)
  }

  @Test
  fun aSecondInstanceReadsTheSeenSteps() {
    TourSettings(context, NAME).markSeen(listOf("prepare.scale", "coffees.add"))

    assertEquals(setOf("prepare.scale", "coffees.add"), TourSettings(context, NAME).seen.value)
  }

  @Test
  fun aCorruptValueReadsAsEmpty() {
    prefs.edit().putString(TourSettings.KEY_SEEN, "corrupto").commit()

    val settings = TourSettings(context, NAME)

    assertTrue(settings.seen.value.isEmpty())
    settings.markSeen(listOf("prepare.scale"))
    assertEquals(setOf("prepare.scale"), TourSettings(context, NAME).seen.value)
  }

  @Test
  fun completingOnboardingDoesNotTouchTheTours() {
    val tours = TourSettings(context, NAME)
    tours.markSeen(listOf("prepare.scale"))

    ExperienceSettings(context, NAME).completeOnboarding(ExperienceMode.EXPERT)

    assertEquals(setOf("prepare.scale"), TourSettings(context, NAME).seen.value)
    assertTrue(ExperienceSettings(context, NAME).onboardingDone.value)
  }

  private companion object {
    const val NAME = "tour_settings_persistence_test"
  }
}
