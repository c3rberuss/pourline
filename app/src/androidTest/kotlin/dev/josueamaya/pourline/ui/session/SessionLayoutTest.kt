package dev.josueamaya.pourline.ui.session

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * En 1080 × 2400 el timer, el agua, el paso actual y el siguiente caben a la vez, sin desplazar, con una receta de
 * 19 pasos, en los dos modos. Se salta en una pantalla de otro tamaño.
 */
class SessionLayoutTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val env = TestAppDependencies(context)
  private val driver = SessionDriver(rule, env)

  @After
  fun tearDown() = env.close()

  /** 10 vertidos de 10 s con 9 esperas entre medias y una instrucción larga (el límite de 20 pasos menos uno). */
  private fun nineteenSteps(): Recipe {
    val instruction = "Vierte en espiral desde el centro hacia fuera, despacio y sin tocar el filtro, para mojar todo el café. ".repeat(2).take(200)
    val stages = (0 until 19).map { i ->
      val pour = i % 2 == 0
      RecipeStage(
        name = if (pour) "Vertido ${i / 2 + 1}" else null,
        startMs = i * 20_000L,
        targetWaterG = if (pour) 25.0 * (i / 2 + 1) else 0.0,
        durationMs = 10_000L,
        type = if (pour) StageType.POUR else StageType.WAIT,
        instruction = instruction,
      )
    }
    return Recipe(name = "Receta de 19 pasos", method = BrewMethod.V60, doseG = 15.0, stages = stages)
  }

  private fun run(mode: ExperienceMode) {
    val metrics = context.resources.displayMetrics
    val screen = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
    assumeTrue("hace falta 1080 × 2400 (hay ${screen.width()} × ${screen.height()})", screen.width() == 1080 && screen.height() == 2400)
    runBlocking { env.recipes.save(nineteenSteps()) }
    env.skipOnboarding(mode)
    rule.setContent { TestApp(env) }
    driver.connect()
    driver.selectRecipe("19 pasos")
    driver.reachPourStep()
    driver.node("prep_start_timer").performClick()
    driver.waitFor("session_timer")
    rule.waitUntil(5_000) { driver.exists("session_next_step") }

    val window = rule.onRoot().fetchSemanticsNode().boundsInRoot
    listOf("session_timer", "session_water", "session_current_step", "session_next_step", "session_finish").forEach { tag ->
      driver.node(tag).assertIsDisplayed()
      val bounds = driver.node(tag).fetchSemanticsNode().boundsInRoot
      assertTrue("$tag fuera de la ventana: $bounds en $window", inside(bounds, window))
    }
    // La lista tiene alto propio (menos que sus 19 filas de 40 dp): se desplaza dentro, no la pantalla.
    val list = driver.node("session_steps").fetchSemanticsNode().boundsInRoot
    val rowsHeight = 19 * 40 * metrics.density
    assertTrue("la lista no debería necesitar toda su altura (${list.height} de $rowsHeight)", list.height < rowsHeight)
    assertTrue("la lista debería tener sitio para al menos cuatro filas", list.height >= 4 * 40 * metrics.density - 1)
  }

  private fun inside(bounds: Rect, window: Rect) =
    bounds.top >= window.top - 1 && bounds.bottom <= window.bottom + 1 && bounds.left >= window.left - 1 && bounds.right <= window.right + 1

  @Test
  fun nineteenStepsFitWithoutScrollingInBasic() = run(ExperienceMode.BASIC)

  @Test
  fun nineteenStepsFitWithoutScrollingInExpert() = run(ExperienceMode.EXPERT)
}
