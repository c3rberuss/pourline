package dev.josueamaya.pourline.ui.grinders

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.grinder.TestGrinders
import dev.josueamaya.pourline.grinder.addGrinder
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Eliminar un molino propio y usado: el diálogo avisa, la receta conserva su copia de texto y el predeterminado se limpia (D16-D18). */
class GrinderDeleteTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var env: TestAppDependencies
  private lateinit var ui: GrindersUi

  @Before
  fun setUp() {
    env = TestAppDependencies(context)
    env.skipOnboarding()
    ui = GrindersUi(rule, env)
  }

  @After
  fun tearDown() = env.close()

  @Test
  fun deletingAnOwnUsedGrinderKeepsTheRecipeTextAndClearsTheDefault() {
    val id = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val recipeId = runBlocking {
      env.recipes.save(
        Recipe(name = "R1", method = BrewMethod.V60, grind = GrindSetting(id, 20.0, "x"), doseG = 15.0, stages = listOf(RecipeStage("Bloom", 0, 40.0, 10_000))),
      )
    }
    env.grinderSettings.setDefaultGrinderId(id)
    rule.setContent { TestApp(env) }
    ui.openGrinders()
    ui.waitFor("grinder_row_$id")
    ui.click("grinder_row_$id")
    ui.waitFor("grinder_detail_ruler")

    ui.click("grinder_detail_delete")
    ui.waitFor("grinder_delete_dialog")
    rule.onNodeWithText("It is used in 1 recipe", substring = true).assertIsDisplayed()
    ui.click("ant_modal_confirm")
    ui.waitFor("grinders_empty")

    val recipe = runBlocking { env.recipes.get(recipeId) }!!
    assertNull(recipe.grind?.grinderId)
    assertEquals(20.0, recipe.grind?.value)
    assertEquals(true, recipe.grind?.label?.contains("Uno"))
    assertNull(env.grinderSettings.defaultGrinderId.value)
  }
}
