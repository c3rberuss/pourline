package dev.dotscale.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.ui.scale.ScaleTopBar
import dev.dotscale.ui.theme.DotScaleTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScaleTopBarTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val title = "Báscula de cocina con nombre largo ".padEnd(40, 'x')

  @Test
  fun aLongTitleIsEllipsizedAndTheActionsStayVisibleAndClickable() {
    var coffees = 0
    var history = 0
    var settings = 0
    var back = 0
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        Box(Modifier.width(360.dp)) {
          ScaleTopBar(
            title = title,
            subtitle = null,
            batteryPercent = 80,
            onBack = { back++ },
            onOpenHistory = { history++ },
            onOpenCoffees = { coffees++ },
            onOpenSettings = { settings++ },
          )
        }
      }
    }
    assertEquals(40, title.length)

    val layouts = mutableListOf<TextLayoutResult>()
    rule.onNodeWithText(title).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    assertTrue("el título debería recortarse con elipsis", layouts.single().hasVisualOverflow)

    rule.onNodeWithContentDescription(context.getString(R.string.scale_battery, 80)).assertIsDisplayed()
    rule.onNodeWithContentDescription(context.getString(R.string.coffees_open)).assertIsDisplayed().performClick()
    rule.onNodeWithContentDescription(context.getString(R.string.history_open)).assertIsDisplayed().performClick()
    rule.onNodeWithContentDescription(context.getString(R.string.settings_open)).assertIsDisplayed().performClick()
    rule.onNodeWithContentDescription(context.getString(R.string.scale_back)).assertIsDisplayed().performClick()
    assertEquals(1, coffees)
    assertEquals(1, history)
    assertEquals(1, settings)
    assertEquals(1, back)
  }
}
