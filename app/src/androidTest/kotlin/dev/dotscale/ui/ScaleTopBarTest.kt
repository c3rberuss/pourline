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
  fun aLongTitleIsEllipsizedAndTheBackArrowAndBatteryStayVisible() {
    var back = 0
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        Box(Modifier.width(360.dp)) {
          ScaleTopBar(
            title = title,
            subtitle = null,
            batteryPercent = 80,
            onBack = { back++ },
          )
        }
      }
    }
    assertEquals(40, title.length)

    val layouts = mutableListOf<TextLayoutResult>()
    rule.onNodeWithText(title).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    assertTrue("el título debería recortarse con elipsis", layouts.single().hasVisualOverflow)

    rule.onNodeWithContentDescription(context.getString(R.string.scale_battery, 80)).assertIsDisplayed()
    // Las secciones viven en la barra inferior: la barra de la sesión solo tiene la flecha y la batería.
    SectionIconDescriptions.forEach { rule.onNodeWithContentDescription(it).assertDoesNotExist() }
    rule.onNodeWithContentDescription(context.getString(R.string.settings_open)).assertDoesNotExist()
    rule.onNodeWithContentDescription(context.getString(R.string.scale_back)).assertIsDisplayed().performClick()
    assertEquals(1, back)
  }
}
