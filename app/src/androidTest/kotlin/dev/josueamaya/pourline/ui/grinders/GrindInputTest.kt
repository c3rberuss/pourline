package dev.josueamaya.pourline.ui.grinders

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasContentDescription
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.grinder.TestGrinders
import dev.josueamaya.pourline.ui.theme.PourlineTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GrindInputTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private var value by mutableStateOf<Double?>(null)
  private var start = 0.0

  private fun show(grinder: Grinder, initial: Double?, start: Double = 0.0, clampToScale: Boolean = true) {
    value = initial
    this.start = start
    rule.setContent {
      PourlineTheme(darkTheme = false) {
        GrindInput(value, grinder, { value = it }, start = this.start, clampToScale = clampToScale)
      }
    }
  }

  private fun tap(tag: String) = rule.onNodeWithTag(tag).performClick()

  // Acme Tres: rotaciones, números (4 por rotación) y clics (10 por número), 0–200.
  private val tres = TestGrinders.tres

  @Test
  fun carryAndBorrowWorkInBothDirections() {
    show(tres, 39.0)
    rule.onNodeWithTag("grind_input_2_value").assertTextEquals("9")
    rule.onNodeWithTag("grind_input_1_value").assertTextEquals("3")

    tap("grind_input_2_inc") // 39 + 1 = 40: los clics y los números se reinician y la rotación sube
    rule.runOnIdle { assertEquals(40.0, value!!, 0.0) }
    rule.onNodeWithTag("grind_input_0_value").assertTextEquals("1")
    rule.onNodeWithTag("grind_input_1_value").assertTextEquals("0")
    rule.onNodeWithTag("grind_input_2_value").assertTextEquals("0")

    tap("grind_input_2_dec") // préstamo: 40 − 1 = 39
    rule.runOnIdle { assertEquals(39.0, value!!, 0.0) }
    rule.onNodeWithTag("grind_input_0_value").assertTextEquals("0")

    tap("grind_input_1_inc") // un número son 10 clics
    rule.runOnIdle { assertEquals(49.0, value!!, 0.0) }
    tap("grind_input_0_dec")
    rule.runOnIdle { assertEquals(9.0, value!!, 0.0) }
  }

  @Test
  fun theFirstTapSetsTheStartValueWithoutAddingAndTheEmptyPartsShowADash() {
    show(tres, null, start = 60.0)
    rule.onNodeWithTag("grind_input_0_value").assertTextEquals("—")
    tap("grind_input_2_inc")
    rule.runOnIdle { assertEquals(60.0, value!!, 0.0) }
    rule.onNodeWithTag("grind_input_0_value").assertTextEquals("1")
  }

  @Test
  fun theScaleLimitDisablesTheButton() {
    show(tres, 200.0)
    rule.onNodeWithTag("grind_input_2_inc").assertIsNotEnabled()
    rule.onNodeWithTag("grind_input_0_inc").assertIsNotEnabled()
    rule.onNodeWithTag("grind_input_2_dec").assertIsEnabled()
    tap("grind_input_2_inc")
    rule.runOnIdle { assertEquals(200.0, value!!, 0.0) }

    value = 0.0
    rule.waitForIdle()
    rule.onNodeWithTag("grind_input_2_dec").assertIsNotEnabled()
    rule.onNodeWithTag("grind_input_2_inc").assertIsEnabled()
  }

  @Test
  fun withoutClampingTheScaleOnlyLimitsAtZero() {
    show(tres, 200.0, clampToScale = false)
    rule.onNodeWithTag("grind_input_2_inc").assertIsEnabled()
    tap("grind_input_2_inc")
    rule.runOnIdle { assertEquals(201.0, value!!, 0.0) }
    value = 0.0
    rule.waitForIdle()
    rule.onNodeWithTag("grind_input_2_dec").assertIsNotEnabled()
  }

  @Test
  fun halfStepsShowTheDecimalOfTheStep() {
    show(TestGrinders.dos, 10.0)
    rule.onNodeWithTag("grind_input_0_value").assertTextEquals(java.text.DecimalFormatSymbols.getInstance().let { "10${it.decimalSeparator}0" })
    tap("grind_input_0_inc")
    rule.runOnIdle { assertEquals(10.5, value!!, 0.0) }
  }

  @Test
  fun theLongFormIsInTheSemanticsAndTheButtonsAreNamedByUnit() {
    show(tres, 39.0)
    // La forma larga del ajuste: "0 rotations, 3 numbers, 9 clicks".
    rule.onNode(hasContentDescription("0 rotations, 3 numbers, 9 clicks")).assertExists()
    rule.onNode(hasContentDescription("Increase Clicks")).assertExists()
    rule.onNode(hasContentDescription("Decrease Rotations")).assertExists()
  }
}
