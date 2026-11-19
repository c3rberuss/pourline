package dev.josueamaya.pourline.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.GrinderRepository
import dev.josueamaya.pourline.grinder.ResourcesGrindTexts
import dev.josueamaya.pourline.grinder.TestGrinders
import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.recipe.BuiltInRecipes
import dev.josueamaya.pourline.recipe.RecipeRepository
import dev.josueamaya.pourline.recipe.ResourcesRecipeTexts
import dev.josueamaya.pourline.settings.BrewSettings
import dev.josueamaya.pourline.settings.ConnectionSettings
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.settings.ExperienceSettings
import dev.josueamaya.pourline.settings.GrinderSettings
import dev.josueamaya.pourline.settings.TargetSettings
import dev.josueamaya.pourline.settings.ThemeMode
import dev.josueamaya.pourline.settings.ThemeSettings
import dev.josueamaya.pourline.ui.settings.SettingsScreen
import dev.josueamaya.pourline.ui.theme.PourlineTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val settings = BrewSettings(context)
  private val targetSettings = TargetSettings(context)
  private val experience = ExperienceSettings(context, TEST_PREFS)
  private val theme = ThemeSettings(context, TEST_PREFS)
  private val previous = settings.autoFinishSeconds.value
  private val previousVibration = targetSettings.stageVibration.value
  private val previousSound = targetSettings.stageSound.value
  private lateinit var db: HistoryDatabase
  private lateinit var recipes: RecipeRepository
  private var restoreCalls = 0
  private var grindersOpened = 0
  private var replayCalls = 0
  private var scaleOffCalls = 0
  private lateinit var scaleMode: ConnectionSettings
  private lateinit var grinderSettings: GrinderSettings
  private lateinit var grinders: GrinderRepository

  @Before
  fun setUp() {
    settings.setAutoFinishSeconds(60) // así el clic en "30 s" siempre es un cambio
    targetSettings.setStageVibration(true)
    targetSettings.setStageSound(true)
    context.getSharedPreferences(TEST_PREFS, android.content.Context.MODE_PRIVATE).edit().clear().commit()
    experience.setMode(ExperienceMode.EXPERT)
    scaleMode = ConnectionSettings(context, TEST_PREFS).also { it.setUseScale(true) }
    theme.setMode(ThemeMode.SYSTEM)
    db = HistoryDatabase.inMemory(context)
    recipes = RecipeRepository(db, ResourcesRecipeTexts(context))
    grinderSettings = GrinderSettings(context, TEST_PREFS)
    grinders = GrinderRepository(db, grinderSettings, ResourcesGrindTexts(context))
    rule.setContent {
      PourlineTheme(darkTheme = false) {
        SettingsScreen(
          settings,
          targetSettings,
          experience,
          theme,
          grinderRepository = grinders,
          grinderDefaults = grinderSettings,
          scaleMode = scaleMode,
          onScaleOff = { scaleOffCalls++ },
          onOpenGrinders = { grindersOpened++ },
          onRestoreRecipes = {
            restoreCalls++
            recipes.restoreBuiltIns()
          },
          onReplayTour = { replayCalls++ },
          onBack = {},
        )
      }
    }
  }

  @After
  fun tearDown() {
    settings.setAutoFinishSeconds(previous)
    targetSettings.setStageVibration(previousVibration)
    targetSettings.setStageSound(previousSound)
    db.close()
    context.getSharedPreferences(TEST_PREFS, android.content.Context.MODE_PRIVATE).edit().clear().commit()
  }

  private fun testPrefs() = context.getSharedPreferences(TEST_PREFS, android.content.Context.MODE_PRIVATE)

  private fun prefs() = context.getSharedPreferences(BrewSettings.PREFS_NAME, android.content.Context.MODE_PRIVATE)

  @Test
  fun selectingAValueSavesIt() {
    rule.onNodeWithText(context.getString(R.string.settings_auto_finish_value, 30)).performClick()

    assertEquals(30, settings.autoFinishSeconds.value)
    val stored = prefs().getInt(BrewSettings.KEY_AUTO_FINISH, -1)
    assertEquals(30, stored)
  }

  @Test
  fun theModeSegmentedSavesTheExperienceMode() {
    rule.onNodeWithText(context.getString(R.string.mode_basic)).performClick()
    assertEquals(ExperienceMode.BASIC, experience.mode.value)
    assertEquals("BASIC", testPrefs().getString(ExperienceSettings.KEY_MODE, null))

    rule.onNodeWithText(context.getString(R.string.mode_expert)).performClick()
    assertEquals(ExperienceMode.EXPERT, experience.mode.value)
    assertEquals("EXPERT", testPrefs().getString(ExperienceSettings.KEY_MODE, null))
  }

  @Test
  fun theThemeSegmentedSavesTheThemeMode() {
    rule.onNodeWithText(context.getString(R.string.theme_dark)).performScrollTo().performClick()
    assertEquals(ThemeMode.DARK, theme.mode.value)
    assertEquals("DARK", testPrefs().getString(ThemeSettings.KEY_MODE, null))

    rule.onNodeWithText(context.getString(R.string.theme_light)).performClick()
    assertEquals("LIGHT", testPrefs().getString(ThemeSettings.KEY_MODE, null))
  }

  @Test
  fun theScaleSegmentedSavesTheModeAndClosesTheConnectionWhenLeavingIt() {
    rule.onNodeWithText(context.getString(R.string.settings_scale_with_hint)).assertExists()

    rule.onNodeWithText(context.getString(R.string.settings_scale_without)).performScrollTo().performClick()

    assertEquals(false, scaleMode.useScale.value)
    assertEquals(false, testPrefs().getBoolean(ConnectionSettings.KEY_USE_SCALE, true))
    assertEquals(1, scaleOffCalls)
    rule.onNodeWithText(context.getString(R.string.settings_scale_without_hint)).assertExists()

    rule.onNodeWithText(context.getString(R.string.settings_scale_with)).performClick()
    assertEquals(true, scaleMode.useScale.value)
    assertEquals(1, scaleOffCalls)
  }

  @Test
  fun theReplayRowCallsBack() {
    rule.onNodeWithTag("settings_tour_replay").performScrollTo().performClick()
    assertEquals(1, replayCalls)
  }

  @Test
  fun theGrindersRowShowsItsSummaryAndOpensTheList() {
    rule.onNodeWithTag("settings_grinders").assertExists()
    rule.onNodeWithText(context.getString(R.string.settings_grinders_none)).assertExists()

    runBlocking { grinders.save(TestGrinders.uno.copy(model = "Uno")) }
    rule.waitUntil(5_000) { rule.onAllNodesWithText("1", substring = true).fetchSemanticsNodes().isNotEmpty() }
    val id = runBlocking { grinders.owned().first().single().id }
    grinderSettings.setDefaultGrinderId(id)
    rule.onNodeWithText("Uno", substring = true).assertExists()

    rule.onNodeWithTag("settings_grinders").performClick()
    assertEquals(1, grindersOpened)
  }

  @Test
  fun theRestoreButtonOnlyShowsInExpertMode() {
    rule.onNodeWithTag("settings_recipes_restore").performScrollTo().assertExists()

    rule.onNodeWithText(context.getString(R.string.mode_basic)).performScrollTo().performClick()
    rule.onAllNodesWithTag("settings_recipes_restore").assertCountEquals(0)

    rule.onNodeWithText(context.getString(R.string.mode_expert)).performScrollTo().performClick()
    rule.onNodeWithTag("settings_recipes_restore").performScrollTo().assertExists()
  }

  @Test
  fun theTwoStageSwitchesPersist() {
    assertTrue(targetSettings.stageVibration.value)
    rule.onNodeWithTag("settings_stage_vibration").performScrollTo().performClick()
    assertFalse(targetSettings.stageVibration.value)
    assertFalse(prefs().getBoolean(TargetSettings.KEY_STAGE_VIBRATION, true))

    rule.onNodeWithTag("settings_stage_sound").performScrollTo().performClick()
    assertFalse(targetSettings.stageSound.value)
    assertFalse(prefs().getBoolean(TargetSettings.KEY_STAGE_SOUND, true))

    rule.onNodeWithTag("settings_stage_sound").performClick()
    assertTrue(targetSettings.stageSound.value)
    assertTrue(prefs().getBoolean(TargetSettings.KEY_STAGE_SOUND, false))
  }

  @Test
  fun cancellingTheRestoreDoesNothing() {
    rule.onNodeWithTag("settings_recipes_restore").performScrollTo().performClick()
    rule.onNodeWithTag("ant_modal").assertExists()
    rule.onNodeWithTag("ant_modal_cancel").performClick()
    rule.onAllNodesWithText(context.getString(R.string.settings_recipes_restored)).assertCountEquals(0)
    assertEquals(0, restoreCalls)
  }

  @Test
  fun restoringBringsAnEditedBuiltInBackToFactoryValuesAndConfirms() {
    val texts = ResourcesRecipeTexts(context)
    val id = runBlocking {
      val v60 = recipes.list().first().single { it.name == texts.builtInName(BuiltInRecipes.V60_CLASSIC) }
      val edited = recipes.get(v60.id)!!.copy(name = "Mi V60", doseG = 18.0, localizedNames = false)
      recipes.save(edited)
      v60.id
    }
    assertEquals("Mi V60", runBlocking { recipes.get(id)!!.name })

    rule.onNodeWithTag("settings_recipes_restore").performScrollTo().performClick()
    rule.onNodeWithTag("ant_modal_confirm").performClick()

    rule.waitUntil(5_000) { rule.onAllNodesWithText(context.getString(R.string.settings_recipes_restored)).fetchSemanticsNodes().isNotEmpty() }
    assertEquals(1, restoreCalls)
    val restored = runBlocking { recipes.get(id)!! }
    assertEquals(texts.builtInName(BuiltInRecipes.V60_CLASSIC), restored.name)
    assertEquals(15.0, restored.doseG, 0.0)
    assertTrue(restored.localizedNames)
  }

  private companion object {
    const val TEST_PREFS = "settings_test"
  }
}
