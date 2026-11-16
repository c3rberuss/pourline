package dev.dotscale.ui.session

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.ui.SectionIconDescriptions
import dev.dotscale.ui.theme.DotScaleTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SessionTopBarTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val title = "Receta con un nombre muy largo ".padEnd(40, 'x')

  @Test
  fun aLongTitleIsEllipsizedAndTheCrossAndTheMenuStayVisibleAndClickable() {
    var closed = 0
    var options = 0
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        Box(Modifier.width(360.dp)) {
          SessionTopBar(title = title, onClose = { closed++ }, onOptions = { options++ })
        }
      }
    }
    assertEquals(40, title.length)

    val layouts = mutableListOf<TextLayoutResult>()
    rule.onNodeWithText(title).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    assertTrue("el título debería recortarse con elipsis", layouts.single().hasVisualOverflow)

    val close = rule.onNodeWithContentDescription(context.getString(R.string.session_close))
    val menu = rule.onNodeWithContentDescription(context.getString(R.string.session_options))
    close.assertIsDisplayed().assertIsEnabled().performClick()
    menu.assertIsDisplayed().assertIsEnabled().performClick()
    assertEquals(1, closed)
    assertEquals(1, options)
    // Las secciones viven en la barra inferior: la sesión no lleva sus iconos.
    SectionIconDescriptions.forEach { rule.onNodeWithContentDescription(it).assertDoesNotExist() }
  }
}
