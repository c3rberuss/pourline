package dev.josueamaya.pourline.ui.tour

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.history.BrewDetails
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.history.TestBrews
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import dev.josueamaya.pourline.ui.session.SessionDriver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Los recorridos de Recetas, Historial, el detalle de una preparación y la comparación, con la aplicación entera. */
class RecipesHistoryTourTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val env = TestAppDependencies(context)
  private val driver = SessionDriver(rule, env)

  @After
  fun tearDown() = env.close()

  private fun show() = rule.setContent { TestApp(env) }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun waitFor(tag: String) = rule.waitUntil(5_000) { exists(tag) }

  private fun openTab(tab: String, content: String) {
    waitFor(tab)
    rule.onNodeWithTag(tab).performClick()
    waitFor(content)
  }

  private fun openPrepare() {
    rule.onNodeWithTag("tab_prepare").performClick()
    waitFor("prepare_start")
  }

  private fun goTo(counter: String) {
    var guard = 0
    while (rule.tourCounter() != counter && guard++ < 30) {
      val before = rule.tourCounter()
      rule.tapTourNext()
      rule.waitUntil(5_000) { rule.tourCounter() != before }
    }
    assertEquals(counter, rule.tourCounter())
  }

  /** El ancla del paso actual quedó a la vista: sus límites caben en la raíz (el recorrido la reveló desplazando). */
  private fun assertOnScreen(tag: String) {
    val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
    assertTrue("$tag $bounds no está a la vista en $root", bounds.top >= 0f && bounds.bottom <= root.bottom && bounds.height > 0f)
  }

  private fun titleIs(resource: Int) = assertEquals(context.getString(resource), rule.textOf("tour_title"))

  /** Guarda una preparación con báscula [daysAgo] días atrás; [method] la deja con detalles (la tarjeta "Detalles"). */
  private fun saveBrew(daysAgo: Int = 0, method: BrewMethod? = null): Long = runBlocking {
    env.brews.save(
      TestBrews.finished(totalMs = 60_000, doseG = 15.0, seed = daysAgo + 1L),
      TestBrews.context(
        startedAtEpochMs = 1_700_000_000_000 - daysAgo * 86_400_000L,
        details = method?.let { BrewDetails(method = it) },
      ),
    )
  }

  private fun saveScalelessBrew(daysAgo: Int): Long = runBlocking {
    env.brews.save(
      TestBrews.scalelessFinished(),
      TestBrews.scalelessContext(startedAtEpochMs = 1_700_000_000_000 - daysAgo * 86_400_000L),
    )
  }

  // --- Recetas ---

  @Test
  fun theRecipesTourShowsTheCatalogAndAddWithoutAnActiveRecipe() {
    env.skipOnboarding(showTours = listOf(Tours.recipes))
    show()
    openTab("tab_recipes", "recipe_row")

    rule.waitForTour("1/2", env.tourSettings.seen.value)
    // El primer paso señala la opción "Recomendadas" del selector.
    titleIs(R.string.tour_recipes_catalog_title)
    assertOnScreen("recipes_segment_catalog")
    goTo("2/2")
    titleIs(R.string.tour_recipes_add_title)
  }

  @Test
  fun theActiveRecipeStepShowsWhenARecipeIsChosen() {
    env.skipOnboarding(showTours = listOf(Tours.recipes))
    show()
    driver.selectRecipe()
    openTab("tab_recipes", "recipe_row")

    rule.waitForTour("1/3", env.tourSettings.seen.value)
    titleIs(R.string.tour_recipes_catalog_title)
    goTo("2/3")
    titleIs(R.string.tour_recipes_active_title)
    assertOnScreen("recipe_active_tag")
  }

  @Test
  fun theRecipeEditTourHasFourStepsInBasic() {
    env.skipOnboarding(ExperienceMode.BASIC, showTours = listOf(Tours.recipeEdit))
    show()
    openTab("tab_recipes", "recipe_row")
    rule.onAllNodesWithTag("recipe_row").onFirst().performClick()
    waitFor("recipe_edit_method")

    rule.waitForTour("1/4", env.tourSettings.seen.value)
    titleIs(R.string.tour_recipe_edit_method_title)
    goTo("4/4")
    titleIs(R.string.tour_recipe_edit_totals_title)
  }

  @Test
  fun theRecipeEditTourHasFiveStepsInExpert() {
    env.skipOnboarding(ExperienceMode.EXPERT, showTours = listOf(Tours.recipeEdit))
    show()
    openTab("tab_recipes", "recipe_row")
    rule.onAllNodesWithTag("recipe_row").onFirst().performClick()
    waitFor("recipe_edit_method")

    rule.waitForTour("1/5", env.tourSettings.seen.value)
    goTo("5/5")
    titleIs(R.string.tour_recipe_edit_preview_title)
  }

  // --- Historial ---

  @Test
  fun anEmptyHistoryShowsOnlyTheFilters() {
    env.skipOnboarding(showTours = listOf(Tours.history))
    show()
    openTab("tab_history", "history_filter_coffee")

    rule.waitForTour("1/1", env.tourSettings.seen.value)
    titleIs(R.string.tour_history_filters_title)
    rule.completeTour()
  }

  @Test
  fun theRowStepShowsInALaterOpening() {
    env.skipOnboarding(showTours = listOf(Tours.history))
    show()
    openTab("tab_history", "history_filter_coffee")
    rule.waitForTour("1/1", env.tourSettings.seen.value)
    rule.completeTour()

    saveBrew()
    openPrepare()
    openTab("tab_history", "history_row")

    rule.waitForTour("1/1", env.tourSettings.seen.value)
    titleIs(R.string.tour_history_row_title)
  }

  @Test
  fun theScalelessStepPointsToTheFirstScalelessRow() {
    // Diez con báscula, y una sin báscula al final de la lista: el paso la busca desplazando.
    repeat(10) { saveBrew(daysAgo = it) }
    saveScalelessBrew(daysAgo = 30)
    env.skipOnboarding(showTours = listOf(Tours.history))
    show()
    openTab("tab_history", "history_row")

    rule.waitForTour("1/3", env.tourSettings.seen.value)
    goTo("3/3")
    titleIs(R.string.tour_history_scaleless_title)
    assertOnScreen("history_row_scaleless")
  }

  @Test
  fun theProgressStepIsExpertOnly() {
    repeat(3) { saveBrew(daysAgo = it) }
    env.skipOnboarding(ExperienceMode.EXPERT, showTours = listOf(Tours.history))
    show()
    openTab("tab_history", "history_row")

    rule.waitForTour("1/3", env.tourSettings.seen.value)
    goTo("2/3")
    titleIs(R.string.tour_history_progress_title)
  }

  @Test
  fun theProgressStepIsNotShownInBasic() {
    repeat(3) { saveBrew(daysAgo = it) }
    env.skipOnboarding(ExperienceMode.BASIC, showTours = listOf(Tours.history))
    show()
    openTab("tab_history", "history_row")

    rule.waitForTour("1/2", env.tourSettings.seen.value)
    goTo("2/2")
    titleIs(R.string.tour_history_row_title)
  }

  // --- Detalle y comparación ---

  @Test
  fun theBrewDetailTourHasThreeStepsInBasic() {
    saveBrew(method = BrewMethod.V60)
    env.skipOnboarding(ExperienceMode.BASIC, showTours = listOf(Tours.brewDetail))
    show()
    openTab("tab_history", "history_row")
    rule.onAllNodesWithTag("history_row").onFirst().performClick()

    rule.waitForTour("1/3", env.tourSettings.seen.value)
    titleIs(R.string.tour_brew_detail_details_title)
    goTo("3/3")
    titleIs(R.string.tour_brew_detail_share_title)
  }

  @Test
  fun theBrewDetailTourHasSixStepsInExpert() {
    saveBrew(method = BrewMethod.V60)
    env.skipOnboarding(ExperienceMode.EXPERT, showTours = listOf(Tours.brewDetail))
    show()
    openTab("tab_history", "history_row")
    rule.onAllNodesWithTag("history_row").onFirst().performClick()

    rule.waitForTour("1/6", env.tourSettings.seen.value)
    titleIs(R.string.tour_brew_detail_chart_title)
    goTo("6/6")
    assertTrue(exists("brew_detail_compare"))
  }

  @Test
  fun theCompareTourShowsItsThreeSteps() {
    saveBrew(daysAgo = 0)
    saveBrew(daysAgo = 1)
    env.skipOnboarding(ExperienceMode.EXPERT, showTours = listOf(Tours.compare))
    show()
    openTab("tab_history", "history_row")
    rule.onAllNodesWithTag("history_row").onFirst().performClick()
    waitFor("brew_detail_compare")
    rule.onNodeWithTag("brew_detail_compare").performScrollTo().performClick()
    waitFor("compare_pick_list")
    rule.onAllNodesWithTag("history_row").onFirst().performClick()

    rule.waitForTour("1/3", env.tourSettings.seen.value)
    titleIs(R.string.tour_compare_chart_title)
    goTo("3/3")
    titleIs(R.string.tour_compare_table_title)
  }
}
