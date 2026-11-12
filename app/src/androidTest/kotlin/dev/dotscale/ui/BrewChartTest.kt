package dev.dotscale.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.sim.SimulatedScaleConnection
import dev.dotscale.ui.scale.ScaleScreen
import dev.dotscale.ui.scale.ScaleViewModel
import dev.dotscale.ui.theme.DotScaleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Báscula simulada y sin permisos BLE: no hace falta hardware. Localizadores por `testTag` y textos. */
class BrewChartTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val connection = SimulatedScaleConnection()

  @Before
  fun setUp() {
    val viewModel = ScaleViewModel(
      connection,
      MutableStateFlow(60),
      saver = { _, _ -> 1L },
      applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        ScaleScreen(viewModel, title = "Test", onBack = {}, onOpenSettings = {}, onOpenHistory = {}, onAddDetails = {})
      }
    }
  }

  @After
  fun tearDown() {
    connection.disconnect()
  }

  private fun startStablePour() {
    rule.onNodeWithText(context.getString(R.string.sim_pour_stable)).performScrollTo().performClick()
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("brew_chart").fetchSemanticsNodes().isNotEmpty() }
  }

  @Test
  fun chartAppearsWhenAPourIsSimulated() {
    rule.onNodeWithText(context.getString(R.string.brew_chart_empty)).assertIsDisplayed()
    startStablePour()
    rule.onNodeWithTag("brew_chart").assertIsDisplayed()
    rule.onNodeWithTag("brew_level").assertIsDisplayed()
  }

  @Test
  fun finishShowsTheSummary() {
    startStablePour()
    rule.onNodeWithTag("brew_finish").performClick()
    rule.onNodeWithTag("brew_summary").performScrollTo().assertIsDisplayed()
    rule.onNodeWithText(context.getString(R.string.brew_reason_manual)).assertIsDisplayed()
  }

  @Test
  fun aPourAfterFinishingShowsTheIgnoredNoticeAndKeepsTheSummary() {
    startStablePour()
    // El guion del simulador sigue vertiendo (bloom) tras "Terminar": sin tara ni reinicio se ignora.
    rule.onNodeWithTag("brew_finish").performClick()
    val notice = context.getString(R.string.brew_chart_ignored)
    rule.waitUntil(5_000) { rule.onAllNodesWithText(notice).fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithText(notice).performScrollTo().assertIsDisplayed()
    rule.onNodeWithTag("brew_summary").assertExists()
  }
}
