package dev.josueamaya.pourline.ui.tour

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.rules.ActivityScenarioRule
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.settings.LocalExperienceMode
import dev.josueamaya.pourline.settings.TourStore
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import dev.josueamaya.pourline.ui.components.AntModal
import dev.josueamaya.pourline.ui.theme.PourlineTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Un [TourStore] en memoria para los tests que no necesitan las preferencias. */
class InMemoryTourStore(initial: Set<String> = emptySet()) : TourStore {
  private val _seen = MutableStateFlow(initial)
  override val seen: StateFlow<Set<String>> = _seen.asStateFlow()

  override fun markSeen(ids: Collection<String>) {
    _seen.value = _seen.value + ids
  }

  override fun reset() {
    _seen.value = emptySet()
  }
}

typealias TourRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

private fun ComposeTestRule.exists(tag: String) = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

/** El texto del primer nodo con [tag] (en el árbol sin fusionar), o `null` si no está. */
fun ComposeTestRule.textOf(tag: String): String? =
  onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
    ?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }

/** El contador "n/N" del globo, o `null` si no hay recorrido. */
fun ComposeTestRule.tourCounter(): String? = textOf("tour_counter")

fun ComposeTestRule.tourVisible(): Boolean = exists("tour_overlay")

/**
 * Espera hasta 5 s a que aparezca un recorrido (con el contador [counter], si se da). Si no sale, el fallo dice qué se
 * esperaba y qué hay: lo más habitual es que el test olvide `showTours` en `skipOnboarding` (R15).
 */
fun ComposeTestRule.waitForTour(counter: String? = null, seen: Set<String>? = null) {
  try {
    waitUntil(5_000) { tourVisible() && (counter == null || tourCounter() == counter) }
  } catch (e: ComposeTimeoutException) {
    throw AssertionError("No aparece el recorrido (contador esperado: $counter, contador actual: ${tourCounter()}). Pasos vistos: $seen", e)
  }
}

fun ComposeTestRule.tapTourNext() {
  onNodeWithTag("tour_next").performClick()
}

fun ComposeTestRule.tapTourPrev() {
  onNodeWithTag("tour_prev").performClick()
}

fun ComposeTestRule.tapTourSkip() {
  onNodeWithTag("tour_skip").performClick()
}

/** Pulsa Siguiente hasta que el recorrido se cierra (el último paso es "Listo"). */
fun ComposeTestRule.completeTour() {
  var guard = 0
  while (tourVisible() && guard++ < 40) {
    val before = tourCounter()
    tapTourNext()
    waitUntil(5_000) { !tourVisible() || tourCounter() != before }
  }
  check(!tourVisible()) { "El recorrido no termina: sigue en ${tourCounter()}" }
}

/** Comprueba cada 50 ms, durante [ms], que no hay recorrido: cubre de sobra los 300 ms de estabilización. */
fun ComposeTestRule.assertNoTourFor(ms: Long = 1_500) {
  val end = System.currentTimeMillis() + ms
  while (System.currentTimeMillis() < end) {
    mainClock.advanceTimeBy(50)
    waitForIdle()
    check(!tourVisible()) { "Aparece un recorrido (${tourCounter()}) y no debía" }
    Thread.sleep(50)
  }
}

/** Recrea la actividad (como una rotación) y vuelve a poner [content]. */
fun TourRule.recreateWith(content: @Composable () -> Unit) {
  activityRule.scenario.recreate()
  runOnUiThread { activity.setContent(content = content) }
}

fun TourRule.recreateWith(env: TestAppDependencies) = recreateWith { TestApp(env) }

/** Un paso de prueba con los textos del recorrido general. */
fun testStep(id: String, anchor: String?, expert: Boolean = false) =
  TourStep("t.$id", anchor, R.string.tour_prepare_welcome_title, R.string.tour_prepare_welcome_body, expert)

/** Una pantalla de prueba con anclas de varios tipos. */
enum class FakeLayout { SHORT, SCROLL, LAZY }

/**
 * `TourRoot` > `TourScreen` > una pantalla de prueba y un `TourHost`. La pantalla tiene un botón anclado (`fake_button`,
 * con contador `fake_clicks`), un `BackHandler` con contador (`fake_back`), un interruptor que abre un `AntModal`
 * (`fake_open_modal`; con [modal] el diálogo está abierto desde el principio), con [extra] un ancla `fake_gone` que un test hace desaparecer y, según [layout], un ancla al final de una columna larga (`fake_bottom`) o en el elemento 60 de
 * una `LazyColumn` (`fake_lazy_60`, declarada con `TourLazyAnchor`).
 */
@Composable
fun TourHarness(
  store: TourStore,
  tour: Tour?,
  visible: Boolean = true,
  ready: Boolean = true,
  enabled: Boolean = true,
  mode: ExperienceMode = ExperienceMode.BASIC,
  layout: FakeLayout = FakeLayout.SHORT,
  withScreen: Boolean = true,
  extra: Boolean = false,
  modal: Boolean = false,
) {
  CompositionLocalProvider(LocalExperienceMode provides mode) {
    PourlineTheme(darkTheme = false) {
      TourRoot(store) {
        if (withScreen) {
          TourScreen(visible) {
            FakeScreen(layout, extra, modal)
            TourHost(tour, ready, enabled)
          }
        } else {
          FakeScreen(layout, extra, modal)
          TourHost(tour, ready, enabled)
        }
      }
    }
  }
}

@Composable
fun FakeScreen(layout: FakeLayout, extra: Boolean = false, forceModal: Boolean = false) {
  var clicks by remember { mutableIntStateOf(0) }
  var backs by remember { mutableIntStateOf(0) }
  var modal by remember { mutableStateOf(false) }
  BackHandler { backs++ }
  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(Modifier.testTag("fake_button").tourAnchor("fake_button").clickable { clicks++ }.padding(16.dp)) { Text("Botón") }
      Box(Modifier.testTag("fake_open_modal").clickable { modal = true }.padding(16.dp)) { Text("Modal") }
      Text("$clicks", Modifier.testTag("fake_clicks"))
      Text("$backs", Modifier.testTag("fake_back"))
    }
    if (extra) Text("Extra", Modifier.testTag("fake_gone").tourAnchor("fake_gone"))
    when (layout) {
      FakeLayout.SHORT -> Unit
      FakeLayout.SCROLL -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(3_000.dp))
        Text("Final", Modifier.testTag("fake_bottom").tourAnchor("fake_bottom"))
        Spacer(Modifier.height(32.dp))
      }
      FakeLayout.LAZY -> {
        val state = rememberLazyListState()
        TourLazyAnchor("fake_lazy_60", state, present = true)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = state) {
          items(100) { i ->
            val base = Modifier.fillMaxWidth().height(48.dp).testTag("fake_item_$i")
            Text("Elemento $i", if (i == 60) base.tourAnchor("fake_lazy_60") else base)
          }
        }
      }
    }
  }
  if (modal || forceModal) AntModal("Título", "Texto", "Sí", "No", onConfirm = { modal = false }, onDismiss = { modal = false })
}
