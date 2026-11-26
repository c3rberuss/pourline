package dev.josueamaya.pourline.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.Tab
import dev.josueamaya.pourline.session.BrewPlan
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.share.ImportSource
import dev.josueamaya.pourline.recipe.share.RecipeShareCodec
import dev.josueamaya.pourline.settings.ExperienceMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Cualquier texto que llega a `PendingRecipeImport` abre la vista previa en Recetas, o espera a la sesión (F6). */
class RecipeImportNavigationTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var env: TestAppDependencies
  private val imports get() = env.dependencies.recipeImports

  @Before
  fun setUp() {
    env = TestAppDependencies(context, seedRecipes = false)
  }

  @After
  fun tearDown() = env.close()

  private fun show(skipOnboarding: Boolean = true) {
    if (skipOnboarding) env.skipOnboarding()
    rule.setContent { TestApp(env) }
  }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  private fun waitFor(tag: String) = rule.waitUntil(5_000) { exists(tag) }

  private fun showsText(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

  private fun stages() = listOf(
    RecipeStage("Bloom", 0, 40.0, 30_000, StageType.POUR),
    RecipeStage("Segundo", 45_000, 250.0, 60_000, StageType.POUR),
  )

  private fun uri(name: String = "Receta importada"): String =
    RecipeShareCodec.encode(Recipe(name = name, doseG = 15.0, stages = stages()), grinder = null)!!.uri

  private fun savedNames() = runBlocking { env.recipes.all() }.filter { it.builtInKey == null }.map { it.name }

  @Test
  fun aSubmittedImportOpensThePreviewInTheRecipesTabWithoutBottomBar() {
    show()
    waitFor("prepare_start")

    imports.submit(uri(), ImportSource.LINK, hasSession = false)

    waitFor("recipe_import_save")
    rule.onNodeWithTag("recipe_import_name").assertIsDisplayed()
    // La barra inferior no está: no se puede dejar una vista previa a medias cambiando de pestaña.
    Tab.entries.forEach { assertFalse("${it.name} no debería verse", exists(it.testTag)) }
    assertNull("el pendiente se consume al abrir la vista previa", imports.pending.value)
  }

  @Test
  fun savingReturnsToTheListWithRecipeAdded() {
    show()
    waitFor("prepare_start")
    imports.submit(uri(), ImportSource.LINK, hasSession = false)
    waitFor("recipe_import_save")

    rule.onNodeWithTag("recipe_import_save").performScrollTo().performClick()

    waitFor("recipes_add")
    rule.waitUntil(5_000) { showsText(context.getString(R.string.recipe_import_added)) }
    assertEquals(listOf("Receta importada"), savedNames())
    assertFalse(exists("recipe_import_save"))
    Tab.entries.forEach { assertTrue(exists(it.testTag)) }
  }

  @Test
  fun cancelReturnsToTheListWithoutSaving() {
    show()
    waitFor("prepare_start")
    imports.submit(uri(), ImportSource.LINK, hasSession = false)
    waitFor("recipe_import_save")

    rule.onNodeWithTag("recipe_import_cancel").performScrollTo().performClick()

    waitFor("recipes_add")
    assertTrue(savedNames().isEmpty())
    assertFalse(showsText(context.getString(R.string.recipe_import_added)))
  }

  @Test
  fun withASessionTheImportWaitsAndOpensWhenTheSessionEnds() {
    show()
    waitFor("prepare_start")
    rule.runOnUiThread { env.scaleSession.startScalelessSession(BrewPlan(doseG = 15.0, ratio = 16.0)) }

    imports.submit(uri(), ImportSource.LINK, hasSession = true)

    rule.waitUntil(5_000) { showsText(context.getString(R.string.recipe_import_deferred)) }
    assertFalse("sin vista previa durante la sesión", exists("recipe_import_save"))
    assertTrue(imports.pending.value != null)

    rule.runOnUiThread { env.scaleSession.discardSession() }

    waitFor("recipe_import_save")
    assertNull(imports.pending.value)
  }

  @Test
  fun aSecondImportReplacesTheOpenPreview() {
    show()
    waitFor("prepare_start")
    imports.submit(uri("Primera"), ImportSource.LINK, hasSession = false)
    waitFor("recipe_import_save")

    imports.submit(uri("Segunda"), ImportSource.LINK, hasSession = false)
    rule.waitUntil(5_000) { showsText("Segunda") }

    assertFalse(showsText("Primera"))
    rule.onNodeWithTag("recipe_import_save").performScrollTo().performClick()
    waitFor("recipes_add")
    // Una sola vista previa: tras guardar se vuelve a la lista, no a la primera.
    assertFalse(exists("recipe_import_save"))
    assertEquals(listOf("Segunda"), savedNames())
  }

  @Test
  fun anImportBeforeOnboardingOpensAfterIt() {
    show(skipOnboarding = false)
    imports.submit(uri(), ImportSource.LINK, hasSession = false)
    rule.waitForIdle()
    assertFalse(exists("recipe_import_save"))
    assertTrue(imports.pending.value != null)

    rule.runOnUiThread {
      env.skipOnboarding(ExperienceMode.BASIC)
    }

    waitFor("recipe_import_save")
  }

  @Test
  fun aFailedImportShowsItsMessage() {
    show()
    waitFor("prepare_start")

    imports.submit("https://example.com/not-a-recipe", ImportSource.SCANNER, hasSession = false)

    rule.waitUntil(5_000) { showsText(context.getString(R.string.recipe_import_not_pourline)) }
    assertFalse(exists("recipe_import_save"))
  }
}
