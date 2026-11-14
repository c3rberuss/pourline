package dev.dotscale.ui.scale

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.coffee.CoffeeRepository
import dev.dotscale.history.BrewFilter
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.history.TestBrews
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.recipe.ResourcesRecipeTexts
import dev.dotscale.session.RepositoryScaleCoffees
import dev.dotscale.session.RepositoryTargetReferences
import dev.dotscale.session.ScaleSessionManager
import dev.dotscale.session.ScaleTarget
import dev.dotscale.sim.SimulatedScaleConnection
import dev.dotscale.ui.coffees.CoffeePickerViewModel
import dev.dotscale.ui.theme.DotScaleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Selector de café con la báscula simulada y Room en memoria: elegir, congelar en `Active` y guardar vinculado. */
class CoffeeSelectorTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val texts = ResourcesRecipeTexts(context)
  private val connection = SimulatedScaleConnection()
  private lateinit var db: HistoryDatabase
  private lateinit var coffees: CoffeeRepository
  private lateinit var brews: BrewRepository
  private var coffeeId = 0L
  private var newCoffee = 0

  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(context, texts)
    val today = LocalDate.now()
    coffees = CoffeeRepository(db, { }, { today }, { ZoneId.systemDefault() })
    brews = BrewRepository(db)
    coffeeId = runBlocking {
      coffees.save(TestBrews.coffeeDraft(name = "Etiopía Guji", roastDate = today.minusDays(9), bagWeightG = 250.0))
    }
    val store = MemoryCoffeeStore()
    lateinit var viewModel: ScaleSessionManager
    rule.runOnUiThread {
      viewModel = ScaleSessionManager(
        connections = { connection },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        autoFinishSeconds = MutableStateFlow(60),
        saver = brews,
        applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        references = MemoryReferenceStore(),
        targets = RepositoryTargetReferences(dev.dotscale.recipe.RecipeRepository(db, texts), brews),
        notifier = SilentNotifier,
        coffeeStore = store,
        coffees = RepositoryScaleCoffees(coffees),
      )
      viewModel.connect(ScaleTarget.Simulated("Test"))
    }
    val picker = TargetPickerViewModel(dev.dotscale.recipe.RecipeRepository(db, texts), brews)
    val coffeePicker = CoffeePickerViewModel(coffees)
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        ScaleScreen(
          viewModel,
          picker,
          title = "Test",
          onBack = {},
          onAddDetails = {},
          onManageRecipes = {},
          coffeePickerViewModel = coffeePicker,
          onNewCoffee = { newCoffee++ },
        )
      }
    }
  }

  @After
  fun tearDown() {
    connection.disconnect()
    db.close()
  }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  @Test
  fun pickingACoffeeFreezesItDuringThePourAndSavesTheBrewLinkedToIt() {
    rule.onNodeWithTag("coffee_trigger").performScrollTo().performClick()
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("coffee_picker_row").fetchSemanticsNodes().size == 1 }
    rule.onNodeWithTag("coffee_picker_row").performClick()
    rule.waitUntil(5_000) { !exists("coffee_picker") }
    rule.onNodeWithText(context.getString(R.string.brew_setup_coffee_value, "Etiopía Guji", 9)).performScrollTo().assertIsDisplayed()

    rule.onNodeWithText(context.getString(R.string.scale_sim_add, 15)).performScrollTo().performClick()
    // La dosis se ignora con peso 0: hay que esperar a que la báscula simulada publique los 15 g (con el host cargado tarda).
    val weight = formatNumber(15.0, WeightUnit.GRAMS)
    rule.waitUntil(10_000) { rule.onAllNodes(hasText(weight)).fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithText(context.getString(R.string.scale_capture_dose)).performScrollTo().performClick()
    rule.onNodeWithText(context.getString(R.string.sim_pour_stable)).performScrollTo().performClick()
    rule.waitUntil(10_000) { exists("brew_chart") }
    rule.onNodeWithTag("coffee_trigger").performScrollTo().assertIsNotEnabled()

    rule.onNodeWithTag("brew_finish").performScrollTo().performClick()
    rule.onNodeWithTag("coffee_trigger").performScrollTo().assertIsEnabled()
    rule.waitUntil(10_000) {
      runBlocking { brews.list(BrewFilter()).first() }.isNotEmpty()
    }
    val saved = runBlocking { brews.list(BrewFilter()).first() }.single()
    assertEquals("Etiopía Guji", saved.coffee)
    val detail = runBlocking { coffees.coffee(coffeeId).first() }!!
    assertEquals(1, detail.brews.size)
    assertEquals(saved.id, detail.brews.single().brewId)
    assertNotNull(detail.stockG)
    assertEquals(235.0, detail.stockG!!, 0.5)
  }

  @Test
  fun newCoffeeFromThePickerClosesItAndOpensTheEditor() {
    rule.onNodeWithTag("coffee_trigger").performScrollTo().performClick()
    rule.waitUntil(5_000) { exists("coffee_picker_new") }
    rule.onNodeWithTag("coffee_picker_new").performClick()
    rule.waitUntil(5_000) { newCoffee == 1 }
    rule.waitUntil(5_000) { !exists("coffee_picker") }
  }
}
