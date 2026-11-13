package dev.dotscale.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.settings.BrewSettings
import dev.dotscale.ui.settings.SettingsScreen
import dev.dotscale.ui.theme.DotScaleTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val settings = BrewSettings(context)
  private val previous = settings.autoFinishSeconds.value

  @Before
  fun setUp() {
    settings.setAutoFinishSeconds(60) // así el clic en "30 s" siempre es un cambio
    rule.setContent { DotScaleTheme(darkTheme = false) { SettingsScreen(settings, onBack = {}) } }
  }

  @After
  fun tearDown() {
    settings.setAutoFinishSeconds(previous)
  }

  @Test
  fun selectingAValueSavesIt() {
    rule.onNodeWithText(context.getString(R.string.settings_auto_finish_value, 30)).performClick()

    assertEquals(30, settings.autoFinishSeconds.value)
    val stored = context.getSharedPreferences(BrewSettings.PREFS_NAME, android.content.Context.MODE_PRIVATE)
      .getInt(BrewSettings.KEY_AUTO_FINISH, -1)
    assertEquals(30, stored)
  }
}
