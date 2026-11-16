package dev.dotscale.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.settings.ExperienceMode
import dev.dotscale.ui.TestApp
import dev.dotscale.ui.TestAppDependencies
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** La elección de modo del primer arranque (D11). */
class OnboardingTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var env: TestAppDependencies

  @Before
  fun setUp() {
    env = TestAppDependencies(context)
  }

  @After
  fun tearDown() = env.close()

  private fun show() = rule.setContent { TestApp(env) }

  private fun assertPrepareIsShown() {
    rule.onNodeWithTag("tab_prepare").assertIsDisplayed()
    rule.onNodeWithTag("prepare_start").assertIsDisplayed()
    rule.onNodeWithTag("onboarding_basic").assertDoesNotExist()
  }

  @Test
  fun theFirstTimeTheChoiceIsShownWithoutTheTabBar() {
    show()

    rule.onNodeWithTag("onboarding_basic").assertIsDisplayed()
    rule.onNodeWithTag("onboarding_expert").assertIsDisplayed()
    rule.onNodeWithTag("onboarding_skip").assertIsDisplayed()
    rule.onNodeWithTag("tab_prepare").assertDoesNotExist()
    assertFalse(env.experienceSettings.onboardingDone.value)
  }

  @Test
  fun basicSavesTheModeAndOpensPrepare() {
    show()

    rule.onNodeWithTag("onboarding_basic").performClick()

    assertEquals(ExperienceMode.BASIC, env.experienceSettings.mode.value)
    assertTrue(env.experienceSettings.onboardingDone.value)
    assertPrepareIsShown()
  }

  @Test
  fun expertSavesTheModeAndOpensPrepare() {
    show()

    rule.onNodeWithTag("onboarding_expert").performClick()

    assertEquals(ExperienceMode.EXPERT, env.experienceSettings.mode.value)
    assertTrue(env.experienceSettings.onboardingDone.value)
    assertPrepareIsShown()
  }

  @Test
  fun skippingKeepsBasicAndOpensPrepare() {
    show()

    rule.onNodeWithTag("onboarding_skip").performClick()

    assertEquals(ExperienceMode.BASIC, env.experienceSettings.mode.value)
    assertTrue(env.experienceSettings.onboardingDone.value)
    assertPrepareIsShown()
  }

  @Test
  fun onceDoneTheChoiceDoesNotAppearAgain() {
    env.skipOnboarding(ExperienceMode.EXPERT)

    show()

    assertPrepareIsShown()
    assertEquals(ExperienceMode.EXPERT, env.experienceSettings.mode.value)
  }
}
