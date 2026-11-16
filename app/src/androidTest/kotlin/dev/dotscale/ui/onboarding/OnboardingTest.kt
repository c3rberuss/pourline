package dev.dotscale.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.settings.ExperienceMode
import dev.dotscale.ui.TestApp
import dev.dotscale.ui.TestAppDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

  private fun waitFor(tag: String) = rule.waitUntil(5_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

  private fun assertPrepareIsShown() {
    waitFor("prepare_start")
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
  fun basicSavesTheModeAndOpensPrepareAfterTheGrinderStep() {
    show()

    rule.onNodeWithTag("onboarding_basic").performClick()
    waitFor("onboarding_grinder_skip")
    // Elegir el modo no guarda nada todavía.
    assertFalse(env.experienceSettings.onboardingDone.value)
    rule.onNodeWithTag("onboarding_grinder_skip").performClick()

    assertEquals(ExperienceMode.BASIC, env.experienceSettings.mode.value)
    assertTrue(env.experienceSettings.onboardingDone.value)
    assertPrepareIsShown()
  }

  @Test
  fun expertSavesTheModeAndOpensPrepareAfterTheGrinderStep() {
    show()

    rule.onNodeWithTag("onboarding_expert").performClick()
    waitFor("onboarding_grinder_skip")
    rule.onNodeWithTag("onboarding_grinder_skip").performClick()

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

  // --- El paso "¿Qué molino usas?" (SPEC-006) ---

  @Test
  fun choosingAGrinderLeavesItInMyGrindersAsTheDefaultAndFinishes() {
    env.seedGrinders()
    show()

    rule.onNodeWithTag("onboarding_expert").performClick()
    waitFor("onboarding_grinder_acme-uno")
    rule.onNodeWithTag("onboarding_grinder_acme-dos").assertIsDisplayed()
    rule.onNodeWithTag("onboarding_grinder_acme-uno").performClick()

    assertPrepareIsShown()
    assertEquals(ExperienceMode.EXPERT, env.experienceSettings.mode.value)
    val mine = runBlocking { env.grinders.owned().first() }
    assertEquals(listOf("Uno"), mine.map { it.model })
    assertEquals(mine.single().id, env.grinderSettings.defaultGrinderId.value)
  }

  @Test
  fun skippingTheGrinderStepFinishesWithTheModeAndNoGrinder() {
    env.seedGrinders()
    show()

    rule.onNodeWithTag("onboarding_expert").performClick()
    waitFor("onboarding_grinder_skip")
    rule.onNodeWithTag("onboarding_grinder_skip").performClick()

    assertPrepareIsShown()
    assertEquals(ExperienceMode.EXPERT, env.experienceSettings.mode.value)
    assertTrue(runBlocking { env.grinders.owned().first() }.isEmpty())
    assertNull(env.grinderSettings.defaultGrinderId.value)
  }

  @Test
  fun skipOnTheFirstStepFinishesInBasicWithoutAskingForTheGrinder() {
    env.seedGrinders()
    show()

    rule.onNodeWithTag("onboarding_skip").performClick()

    assertPrepareIsShown()
    rule.onNodeWithTag("onboarding_grinder_skip").assertDoesNotExist()
    assertEquals(ExperienceMode.BASIC, env.experienceSettings.mode.value)
    assertTrue(runBlocking { env.grinders.owned().first() }.isEmpty())
  }

  @Test
  fun backOnTheGrinderStepReturnsToTheModeChoice() {
    show()

    rule.onNodeWithTag("onboarding_basic").performClick()
    waitFor("onboarding_grinder_back")
    rule.onNodeWithTag("onboarding_grinder_back").performClick()

    waitFor("onboarding_basic")
    assertFalse(env.experienceSettings.onboardingDone.value)
  }

  @Test
  fun anEmptyCatalogShowsTheEmptyStateAndSkip() {
    show()
    rule.onNodeWithTag("onboarding_basic").performClick()
    waitFor("onboarding_grinder_empty")
    rule.onNodeWithTag("onboarding_grinder_skip").assertIsDisplayed()
  }

  @Test
  fun searchNarrowsTheList() {
    env.seedGrinders()
    show()
    rule.onNodeWithTag("onboarding_basic").performClick()
    waitFor("onboarding_grinder_acme-uno")

    rule.onNodeWithTag("onboarding_grinder_search").performTextInput("inverso")

    rule.waitUntil(5_000) { rule.onAllNodesWithTag("onboarding_grinder_acme-uno").fetchSemanticsNodes().isEmpty() }
    rule.onNodeWithTag("onboarding_grinder_acme-inverso").assertIsDisplayed()
  }
}
