package dev.josueamaya.pourline.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.ui.theme.PourlineTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class AntComponentsTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  @Test
  fun interactiveRateSelectsAndClearsWithTheSameStar() {
    var value by mutableStateOf<Int?>(null)
    rule.setContent { PourlineTheme(darkTheme = false) { AntRate(value, { value = it }) } }

    rule.onNodeWithTag("ant_rate_4").performClick()
    rule.runOnIdle { assertEquals(4, value) }
    rule.onNodeWithTag("ant_rate_4").performClick()
    rule.runOnIdle { assertNull(value) }
  }

  @Test
  fun readOnlyRateDescribesItself() {
    rule.setContent {
      PourlineTheme(darkTheme = false) {
        AntRate(4, null, testTag = "rated")
        AntRate(null, null, testTag = "unrated")
      }
    }
    rule.onNodeWithTag("rated").assert(hasContentDescription(context.getString(R.string.rate_description, 4, 5)))
    rule.onNodeWithTag("unrated").assert(hasContentDescription(context.getString(R.string.rate_none)))
  }

  @Test
  fun formItemShowsErrorAndExtra() {
    rule.setContent {
      PourlineTheme(darkTheme = false) {
        AntFormItem("Nombre", error = "Obligatorio", errorTag = "name_error", extra = { AntTag("Leído", Modifier.testTag("mark")) }) {
          AntText("contenido")
        }
      }
    }
    rule.onNodeWithTag("name_error").assertTextEquals("Obligatorio")
    rule.onNodeWithTag("mark").assertIsDisplayed()
  }

  @Test
  fun selectTriggerClicksOnlyWhenEnabled() {
    var clicks = 0
    var enabled by mutableStateOf(true)
    rule.setContent {
      PourlineTheme(darkTheme = false) {
        AntSelectTrigger("Etiopía", { clicks++ }, label = "Café", enabled = enabled, testTag = "trigger")
      }
    }
    rule.onNodeWithTag("trigger").assert(hasContentDescription("Café: Etiopía")).performClick()
    rule.runOnIdle { assertEquals(1, clicks) }
    enabled = false
    rule.onNodeWithTag("trigger").assertIsNotEnabled()
    rule.runOnIdle { assertEquals(1, clicks) }
  }

  @Test
  fun datePickerOpensTheDialogAndClearsTheValue() {
    var date by mutableStateOf<LocalDate?>(LocalDate.of(2026, 10, 12))
    rule.setContent {
      PourlineTheme(darkTheme = false) {
        AntDatePicker(date, { date = it }, maxDate = LocalDate.of(2026, 11, 13), testTag = "date")
      }
    }
    rule.onNodeWithTag("date").performClick()
    rule.onNodeWithTag("date_picker_accept").assertIsDisplayed()
    rule.onNodeWithText(context.getString(R.string.date_picker_cancel)).performClick()
    rule.onNodeWithTag("date_picker_accept").assertDoesNotExist()

    rule.onNodeWithTag("date_clear").performClick()
    rule.runOnIdle { assertNull(date) }
    rule.onNodeWithTag("date_clear").assertDoesNotExist()
  }

  @Test
  fun autoCompleteDeliversTheSuggestionToOnSelect() {
    val selected = mutableListOf<String>()
    val typed = mutableListOf<String>()
    var text by mutableStateOf("")
    rule.setContent {
      PourlineTheme(darkTheme = false) {
        AntAutoComplete(
          text,
          { text = it; typed += it },
          listOf("Heirloom", "Caturra"),
          placeholder = "Variedad",
          onSelect = { selected += it },
        )
      }
    }
    rule.onNodeWithText("Variedad").performClick()
    rule.onNodeWithText("Variedad").performTextInput("hei")
    rule.onNodeWithText("Heirloom").performClick()
    rule.runOnIdle {
      assertEquals(listOf("Heirloom"), selected)
      assertEquals("hei", text)
    }
  }

  @Test
  fun collapseHidesItsContentUntilExpanded() {
    var expanded by mutableStateOf(false)
    rule.setContent {
      PourlineTheme(darkTheme = false) {
        AntCollapse("Datos", expanded, { expanded = it }, extra = "Natural") { AntText("dentro") }
      }
    }
    rule.onNodeWithTag("ant_collapse_content").assertDoesNotExist()
    rule.onNodeWithTag("ant_collapse_header")
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.collapse_collapsed)))
      .performClick()
    rule.runOnIdle { assertEquals(true, expanded) }
    rule.onNodeWithTag("ant_collapse_content").assertIsDisplayed()
    rule.onNodeWithTag("ant_collapse_header")
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.collapse_expanded)))
  }
  @Test
  fun tabBarSelectsAndMarksTheActiveTab() {
    var selected by mutableStateOf("a")
    rule.setContent {
      PourlineTheme(darkTheme = false) {
        AntTabBar(
          items = listOf(
            AntTabBarItem("a", "Preparar", AntIcons.Dripper, "tab_a"),
            AntTabBarItem("b", "Cafés", AntIcons.Coffee, "tab_b"),
          ),
          selected = selected,
          onSelect = { selected = it },
        )
      }
    }
    rule.onNodeWithTag("tab_a").assertIsSelected()
    rule.onNodeWithTag("tab_b").assertIsNotSelected().performClick()
    rule.runOnIdle { assertEquals("b", selected) }
    rule.onNodeWithTag("tab_b").assertIsSelected()
    rule.onNodeWithText("Cafés").assertIsDisplayed()
  }

  @Test
  fun choiceModalCallsEachChoiceAndDismissesOutside() {
    val clicked = mutableListOf<String>()
    var dismissed = 0
    rule.setContent {
      PourlineTheme(darkTheme = false) {
        AntChoiceModal(
          title = "Salir",
          text = "Texto",
          choices = listOf(
            AntModalChoice("Guardar", { clicked += "save" }, "choice_save", AntButtonType.Primary),
            AntModalChoice("Descartar", { clicked += "discard" }, "choice_discard", danger = true),
          ),
          onDismiss = { dismissed++ },
        )
      }
    }
    rule.onNodeWithTag("choice_save").performClick()
    rule.onNodeWithTag("choice_discard").performClick()
    rule.runOnIdle { assertEquals(listOf("save", "discard"), clicked) }
    Espresso.pressBack()
    rule.runOnIdle { assertEquals(1, dismissed) }
  }

  @Test
  fun progressCircleExposesItsRange() {
    rule.setContent {
      PourlineTheme(darkTheme = false) {
        AntProgressCircle(0.61f, Modifier.size(120.dp).testTag("circle"), segments = listOf(0.4f)) { AntText("61") }
      }
    }
    rule.onNodeWithTag("circle").assert(
      SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0.61f, 0f..1f)),
    )
  }

  @Test
  fun stepperCallsEachActionAndNotWhenDisabled() {
    var up = 0
    var down = 0
    rule.setContent {
      PourlineTheme(darkTheme = false) {
        AntStepper("5", "Clics", { up++ }, { down++ }, "Subir", "Bajar", decrementEnabled = false, testTag = "st")
      }
    }
    rule.onNodeWithTag("st_value").assertTextEquals("5")
    rule.onNodeWithTag("st_inc").performClick()
    rule.onNodeWithTag("st_dec").performClick()
    rule.runOnIdle {
      assertEquals(1, up)
      assertEquals(0, down)
    }
  }

  @Test
  fun stepperRepeatsWhileHeld() {
    var up = 0
    rule.setContent {
      PourlineTheme(darkTheme = false) {
        AntStepper("5", "Clics", { up++ }, {}, "Subir", "Bajar", testTag = "st")
      }
    }
    rule.onNodeWithTag("st_inc").performTouchInput { down(center) }
    rule.mainClock.advanceTimeBy(1_000)
    rule.onNodeWithTag("st_inc").performTouchInput { up() }
    rule.runOnIdle { assertTrue("sumó $up veces", up >= 4) }
  }

  @Test
  fun tabsSelectAndMarkTheSelectedOne() {
    var selected by mutableStateOf("a")
    rule.setContent {
      PourlineTheme(darkTheme = false) {
        AntTabs(
          listOf(AntTabItem("a", "Primera", "tab_a"), AntTabItem("b", "Segunda", "tab_b")),
          selected = selected,
          onSelect = { selected = it },
        )
      }
    }
    rule.onNodeWithTag("tab_a").assertIsSelected()
    rule.onNodeWithTag("tab_b").assertIsNotSelected().performClick()
    rule.runOnIdle { assertEquals("b", selected) }
    rule.onNodeWithTag("tab_b").assertIsSelected()
  }
}
