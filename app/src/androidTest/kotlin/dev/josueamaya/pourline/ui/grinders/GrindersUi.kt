package dev.josueamaya.pourline.ui.grinders

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.ui.TestAppDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Atajos para los tests de las pantallas de molinos: abrir Ajustes → Molinos y esperar a una pantalla por su tag. */
class GrindersUi(
  private val rule: AndroidComposeTestRule<*, ComponentActivity>,
  val env: TestAppDependencies,
) {
  fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  fun waitFor(tag: String) = rule.waitUntil(5_000) { exists(tag) }

  fun waitGone(tag: String) = rule.waitUntil(5_000) { !exists(tag) }

  fun click(tag: String) {
    val node = rule.onNodeWithTag(tag, useUnmergedTree = true)
    // Fuera de un contenedor con desplazamiento (barra superior, pestañas) no hay nada que desplazar.
    runCatching { node.performScrollTo() }
    node.performClick()
  }

  /** El nodo fusionado (con el texto de sus descendientes); con [unmerged], el propio. */
  fun node(tag: String, unmerged: Boolean = false): SemanticsNodeInteraction = rule.onNodeWithTag(tag, useUnmergedTree = unmerged)

  /** Preparar → engranaje → Molinos. */
  fun openGrinders() {
    rule.onNodeWithTag("prepare_settings").performClick()
    waitFor("settings_grinders")
    click("settings_grinders")
    waitFor("grinders_list")
  }

  /** Abre el desplegable [tag] y elige la opción de texto [label]. */
  fun choose(tag: String, label: String) {
    rule.onNodeWithTag(tag).performScrollTo().performClick()
    rule.onNode(hasText(label) and hasAnyAncestor(isPopup())).performClick()
  }

  fun owned(): List<Grinder> = runBlocking { env.grinders.owned().first() }
}
