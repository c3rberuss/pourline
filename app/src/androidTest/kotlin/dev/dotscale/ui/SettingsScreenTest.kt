package dev.dotscale.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.recipe.BuiltInRecipes
import dev.dotscale.recipe.RecipeRepository
import dev.dotscale.recipe.ResourcesRecipeTexts
import dev.dotscale.settings.BrewSettings
import dev.dotscale.settings.TargetSettings
import dev.dotscale.ui.settings.SettingsScreen
import dev.dotscale.ui.theme.DotScaleTheme
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
  private val previous = settings.autoFinishSeconds.value
  private val previousVibration = targetSettings.stageVibration.value
  private val previousSound = targetSettings.stageSound.value
  private lateinit var db: HistoryDatabase
  private lateinit var recipes: RecipeRepository
  private var restoreCalls = 0

  @Before
  fun setUp() {
    settings.setAutoFinishSeconds(60) // así el clic en "30 s" siempre es un cambio
    targetSettings.setStageVibration(true)
    targetSettings.setStageSound(true)
    db = HistoryDatabase.inMemory(context)
    recipes = RecipeRepository(db, ResourcesRecipeTexts(context))
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        SettingsScreen(
          settings,
          targetSettings,
          onRestoreRecipes = {
            restoreCalls++
            recipes.restoreBuiltIns()
          },
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
  }

  private fun prefs() = context.getSharedPreferences(BrewSettings.PREFS_NAME, android.content.Context.MODE_PRIVATE)

  @Test
  fun selectingAValueSavesIt() {
    rule.onNodeWithText(context.getString(R.string.settings_auto_finish_value, 30)).performClick()

    assertEquals(30, settings.autoFinishSeconds.value)
    val stored = prefs().getInt(BrewSettings.KEY_AUTO_FINISH, -1)
    assertEquals(30, stored)
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
}
