package dev.dotscale.ui.history

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.history.BrewDetails
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.history.TestBrews
import dev.dotscale.ui.theme.DotScaleTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BrewEditScreenTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var db: HistoryDatabase
  private lateinit var repository: BrewRepository
  private var backCount = 0
  private var id = 0L

  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(context)
    repository = BrewRepository(db)
    id = runBlocking {
      repository.save(TestBrews.finished(totalMs = 60_000), TestBrews.context()).also {
        repository.updateDetails(it, BrewDetails(coffee = "Etiopía"))
      }
    }
    val viewModel = BrewEditViewModel(repository, id)
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        BrewEditScreen(viewModel, onBack = { backCount++ })
      }
    }
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("brew_edit_save").fetchSemanticsNodes().isNotEmpty() }
  }

  @After
  fun tearDown() = db.close()

  private fun modalShown() = rule.onAllNodesWithTag("ant_modal").fetchSemanticsNodes().isNotEmpty()

  @Test
  fun loadsTheExistingDetails() {
    rule.onNodeWithTag("brew_edit_coffee").assertExists()
    rule.onNodeWithTag("brew_edit_save").assertIsEnabled()
  }

  @Test
  fun aTemperatureOutOfRangeShowsAnErrorAndDisablesSave() {
    rule.onNodeWithTag("brew_edit_temp").performScrollTo().performTextInput("120")
    rule.onNodeWithTag("brew_edit_temp_error").assertExists()
    rule.onNodeWithTag("brew_edit_save").performScrollTo().assertIsNotEnabled()

    rule.onNodeWithTag("brew_edit_temp").performTextReplacement("92,5")
    rule.onAllNodesWithTag("brew_edit_temp_error").let { assertEquals(0, it.fetchSemanticsNodes().size) }
    rule.onNodeWithTag("brew_edit_save").assertIsEnabled()
  }

  @Test
  fun savePersistsTheDataAndGoesBack() {
    rule.onNodeWithTag("brew_edit_name").performTextInput("Prueba")
    rule.onNodeWithTag("brew_edit_grind").performScrollTo().performTextInput("21 clics")
    rule.onNodeWithTag("brew_edit_temp").performScrollTo().performTextInput("93")
    rule.onNodeWithTag("brew_edit_notes").performScrollTo().performTextInput("Dulce")
    rule.onNodeWithTag("brew_edit_save").performScrollTo().performClick()

    rule.waitUntil(5_000) { backCount > 0 }
    val entity = runBlocking { repository.get(id) }!!.entity
    assertEquals("Prueba", entity.name)
    assertEquals("Etiopía", entity.coffee)
    assertEquals("21 clics", entity.grind)
    assertEquals(93.0, entity.waterTempC!!, 0.0)
    assertEquals("Dulce", entity.notes)
  }

  @Test
  fun backWithChangesAsksForConfirmation() {
    rule.onNodeWithTag("brew_edit_name").performTextInput("Cambio")
    Espresso.pressBack()
    // Con el teclado abierto, el primer "atrás" solo lo cierra: se repite una vez si el diálogo no aparece.
    val shown = try {
      rule.waitUntil(2_000) { modalShown() }
      true
    } catch (_: ComposeTimeoutException) {
      false
    }
    if (!shown) Espresso.pressBack()
    rule.waitUntil(5_000) { modalShown() }
    assertEquals(0, backCount)

    rule.onNodeWithTag("ant_modal_cancel").performClick() // "Seguir editando"
    rule.waitUntil(5_000) { !modalShown() }
    assertEquals(0, backCount)

    rule.onNodeWithContentDescription(context.getString(R.string.scale_back)).performClick()
    rule.waitUntil(5_000) { modalShown() }
    rule.onNodeWithTag("ant_modal_confirm").performClick() // "Descartar"
    rule.waitUntil(5_000) { backCount == 1 }
    assertTrue(runBlocking { repository.get(id) }!!.entity.name == null)
  }

  @Test
  fun backWithoutChangesLeavesDirectly() {
    rule.onNodeWithContentDescription(context.getString(R.string.scale_back)).performClick()
    rule.waitUntil(5_000) { backCount == 1 }
    assertEquals(false, modalShown())
  }
}
