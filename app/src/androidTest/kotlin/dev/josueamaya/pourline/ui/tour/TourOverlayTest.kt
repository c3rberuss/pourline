package dev.josueamaya.pourline.ui.tour

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.settings.ExperienceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** La infraestructura del recorrido (anclas, velo, globo, botones, "Atrás", aviso, hojas y accesibilidad) sobre una pantalla de prueba. */
class TourOverlayTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val three = Tour("t", listOf(testStep("a", "fake_button"), testStep("b", "fake_button"), testStep("c", "fake_button")))

  private fun show(
    store: InMemoryTourStore,
    tour: Tour? = three,
    visible: Boolean = true,
    ready: Boolean = true,
    enabled: Boolean = true,
    mode: ExperienceMode = ExperienceMode.BASIC,
    layout: FakeLayout = FakeLayout.SHORT,
    withScreen: Boolean = true,
  ) = rule.setContent { TourHarness(store, tour, visible, ready, enabled, mode, layout, withScreen) }

  @Test
  fun aPendingTourShowsTheFirstStepWithTheCounter() {
    show(InMemoryTourStore())

    rule.waitForTour("1/3")

    rule.onNodeWithTag("tour_overlay").assertExists()
    assertNotNull(rule.textOf("tour_title"))
    assertEquals(context.getString(R.string.tour_prepare_welcome_body), rule.textOf("tour_body"))
    rule.onAllNodesWithTag("tour_prev").assertCountEquals(0)
  }

  @Test
  fun nextMarksAndDoneCloses() {
    val store = InMemoryTourStore()
    show(store)
    rule.waitForTour("1/3")

    rule.tapTourNext()
    rule.waitForTour("2/3")
    assertEquals(setOf("t.a"), store.seen.value)
    rule.tapTourNext()
    rule.waitForTour("3/3")
    rule.onNodeWithTag("tour_next").assertTextEquals(context.getString(R.string.tour_done))
    rule.tapTourNext()
    rule.waitUntil(5_000) { !rule.tourVisible() }

    assertEquals(setOf("t.a", "t.b", "t.c"), store.seen.value)
  }

  @Test
  fun previousGoesBackWithoutMarking() {
    val store = InMemoryTourStore()
    show(store)
    rule.waitForTour("1/3")
    rule.tapTourNext()
    rule.waitForTour("2/3")

    rule.tapTourPrev()

    rule.waitForTour("1/3")
    assertEquals(setOf("t.a"), store.seen.value)
  }

  @Test
  fun theInvitationDeclinesEveryTourWithTheHint() {
    val tour = Tour(
      "t",
      listOf(
        TourStep("t.ask", null, R.string.tour_prepare_welcome_title, R.string.tour_prepare_welcome_body, invitation = true),
        testStep("a", "fake_button"),
        testStep("b", "fake_button"),
      ),
    )
    val store = InMemoryTourStore()
    show(store, tour)
    rule.waitForInvitation()
    assertEquals(null, rule.tourCounter())

    rule.onNodeWithTag("tour_decline").performClick()

    rule.waitUntil(5_000) { !rule.tourVisible() }
    assertTrue(store.seen.value.containsAll(Tours.allStepIds))
    rule.waitUntil(5_000) { rule.onAllNodesWithText(context.getString(R.string.tour_skipped_hint)).fetchSemanticsNodes().isNotEmpty() }
  }

  @Test
  fun skipMarksOnlyThisOpeningAndShowsTheHint() {
    // `d` es de Experto y `e` tiene su ancla ausente: ni uno ni otro entran en la apertura, así que Saltar no los marca.
    val tour = Tour(
      "t",
      listOf(testStep("a", "fake_button"), testStep("d", "fake_button", expert = true), testStep("e", "missing"), testStep("b", "fake_button")),
    )
    val store = InMemoryTourStore()
    show(store, tour)
    rule.waitForTour("1/2")

    rule.tapTourSkip()

    rule.waitUntil(5_000) { !rule.tourVisible() }
    assertEquals(setOf("t.a", "t.b"), store.seen.value)
    rule.waitUntil(5_000) { rule.onAllNodesWithText(context.getString(R.string.tour_skipped_hint)).fetchSemanticsNodes().isNotEmpty() }
  }

  @Test
  fun systemBackStepsBackAndOnTheFirstStepSkipsWithTheHint() {
    val store = InMemoryTourStore()
    show(store)
    rule.waitForTour("1/3")
    rule.tapTourNext()
    rule.waitForTour("2/3")

    rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
    rule.waitForTour("1/3")
    rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

    rule.waitUntil(5_000) { !rule.tourVisible() }
    assertEquals(setOf("t.a", "t.b", "t.c"), store.seen.value)
    rule.waitUntil(5_000) { rule.onAllNodesWithText(context.getString(R.string.tour_skipped_hint)).fetchSemanticsNodes().isNotEmpty() }
    // El `BackHandler` de la pantalla no se llamó.
    assertEquals("0", rule.textOf("fake_back"))
  }

  @Test
  fun tapsOnTheVeilAndOnTheHighlightedElementDoNothing() {
    show(InMemoryTourStore())
    rule.waitForTour("1/3")

    rule.onNodeWithTag("fake_button").performClick()
    rule.onNodeWithTag("fake_open_modal").performClick()
    rule.onNodeWithTag("fake_clicks").performTouchInput { click(Offset(2f, 2f)) }

    rule.waitForIdle()
    assertEquals("0", rule.textOf("fake_clicks"))
    assertTrue(rule.onAllNodesWithTag("ant_modal").fetchSemanticsNodes().isEmpty())
    assertTrue(rule.tourVisible())
  }

  @Test
  fun aCenteredStepAloneIsNotShown() {
    val tour = Tour("t", listOf(testStep("welcome", null), testStep("a", "fake_button")))
    show(InMemoryTourStore(setOf("t.a")), tour)

    rule.assertNoTourFor()
  }

  @Test
  fun anOpenModalDelaysTheTourUntilItCloses() {
    var modal by mutableStateOf(true)
    val store = InMemoryTourStore()
    rule.setContent { TourHarness(store, three, modal = modal) }
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("ant_modal").fetchSemanticsNodes().isNotEmpty() }

    rule.assertNoTourFor(800)

    modal = false
    rule.waitForTour("1/3")
  }

  @Test
  fun notReadyDelaysTheTour() {
    var ready by mutableStateOf(false)
    val store = InMemoryTourStore()
    rule.setContent { TourHarness(store, three, ready = ready) }

    rule.assertNoTourFor(800)

    ready = true
    rule.waitForTour("1/3")
  }

  @Test
  fun disablingTheHostClosesTheTourWithoutMarking() {
    val store = InMemoryTourStore()
    var enabled by mutableStateOf(true)
    rule.setContent { TourHarness(store, three, enabled = enabled) }
    rule.waitForTour("1/3")
    rule.tapTourNext()
    rule.waitForTour("2/3")

    enabled = false

    rule.waitUntil(5_000) { !rule.tourVisible() }
    assertEquals(setOf("t.a"), store.seen.value)
  }

  @Test
  fun aVanishingAnchorIsSkippedWithoutMarking() {
    val tour = Tour("t", listOf(testStep("a", "fake_button"), testStep("b", "fake_gone"), testStep("c", "fake_button")))
    val store = InMemoryTourStore()
    var extra by mutableStateOf(true)
    rule.setContent { TourHarness(store, tour, extra = extra) }
    rule.waitForTour("1/3")
    rule.tapTourNext()
    rule.waitForTour("2/3")

    // El ancla del paso en pantalla desaparece: el paso se descarta y sigue el siguiente, sin marcar el perdido.
    extra = false

    rule.waitForTour("2/2")
    assertEquals(setOf("t.a"), store.seen.value)
    rule.tapTourNext()
    rule.waitUntil(5_000) { !rule.tourVisible() }
    assertEquals(setOf("t.a", "t.c"), store.seen.value)
  }

  @Test
  fun anAnchorBelowTheFoldIsScrolledIntoView() {
    val tour = Tour("t", listOf(testStep("a", "fake_bottom")))
    show(InMemoryTourStore(), tour, layout = FakeLayout.SCROLL)

    rule.waitForTour("1/1")

    assertInsideRoot("fake_bottom")
  }

  @Test
  fun aLazyAnchorIsFoundByScrolling() {
    val tour = Tour("t", listOf(testStep("a", "fake_lazy_60")))
    show(InMemoryTourStore(), tour, layout = FakeLayout.LAZY)

    rule.waitForTour("1/1")

    assertInsideRoot("fake_item_60")
  }

  @Test
  fun theBalloonStaysInsideTheRoot() {
    val tour = Tour("t", listOf(testStep("a", "fake_button"), testStep("b", null)))
    show(InMemoryTourStore(), tour)
    rule.waitForTour("1/2")

    assertInsideRoot("tour_balloon")
    rule.tapTourNext()
    rule.waitForTour("2/2")
    assertInsideRoot("tour_balloon")
  }

  @Test
  fun theBalloonIsAnnounced() {
    show(InMemoryTourStore())
    rule.waitForTour("1/3")

    val balloon = rule.onNodeWithTag("tour_balloon").fetchSemanticsNode().config
    assertEquals(context.getString(R.string.tour_prepare_welcome_title), balloon.getOrNull(SemanticsProperties.PaneTitle))
    assertNotNull(rule.textOf("tour_title"))
    rule.onNodeWithTag("tour_next").assertTextEquals(context.getString(R.string.tour_next))
    rule.onNodeWithTag("tour_skip").assertTextEquals(context.getString(R.string.tour_skip))
    // El bloque del título y el cuerpo es una región viva.
    val region = rule.onAllNodesWithTag("tour_balloon").fetchSemanticsNodes().single()
    assertTrue(region.children.any { it.config.getOrNull(SemanticsProperties.LiveRegion) != null })
  }

  @Test
  fun theContentStillHasItsNodesForTests() {
    show(InMemoryTourStore())
    rule.waitForTour("1/3")

    rule.onNodeWithTag("fake_button").assertExists()
    rule.onNodeWithTag("fake_clicks").assertExists()
  }

  @Test
  fun withoutTourScreenNothingHappens() {
    show(InMemoryTourStore(), withScreen = false)

    rule.assertNoTourFor()
  }

  @Test
  fun rotationKeepsTheStep() {
    val store = InMemoryTourStore()
    // `StateRestorationTester` guarda y restaura el estado de `rememberSaveable` como lo hace una rotación.
    val restoration = StateRestorationTester(rule)
    restoration.setContent { TourHarness(store, three) }
    rule.waitForTour("1/3")
    rule.tapTourNext()
    rule.waitForTour("2/3")

    restoration.emulateSavedInstanceStateRestore()

    rule.waitForTour("2/3")
    assertEquals(setOf("t.a"), store.seen.value)
  }

  private fun assertInsideRoot(tag: String) {
    val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    val root = rule.onNodeWithTag("tour_overlay").fetchSemanticsNode().boundsInRoot
    assertTrue("$tag $bounds fuera de $root", bounds.left >= root.left && bounds.right <= root.right && bounds.top >= root.top && bounds.bottom <= root.bottom)
  }
}
