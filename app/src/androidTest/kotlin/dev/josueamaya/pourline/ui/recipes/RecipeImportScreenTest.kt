package dev.josueamaya.pourline.ui.recipes

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.grinder.GrinderCatalogSeeder
import dev.josueamaya.pourline.grinder.GrinderRepository
import dev.josueamaya.pourline.grinder.ResourcesGrindTexts
import dev.josueamaya.pourline.grinder.TestGrinderCatalog
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeRepository
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.ResourcesRecipeTexts
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.aeroPressWithActions
import dev.josueamaya.pourline.recipe.share.ImportError
import dev.josueamaya.pourline.recipe.share.ImportNotice
import dev.josueamaya.pourline.recipe.share.PendingRecipeImport
import dev.josueamaya.pourline.recipe.share.RecipeShareCodec
import dev.josueamaya.pourline.settings.GrinderSettings
import dev.josueamaya.pourline.ui.theme.PourlineTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Random

/**
 * La vista previa de una receta importada (F6): contenido, avisos, molienda en sus tres casos, nombre único y guardado.
 * Room en memoria con el catálogo de prueba (`acme-uno`, `acme-dos`, `acme-tres`) sembrado con el sembrador real.
 */
class RecipeImportScreenTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val texts = ResourcesRecipeTexts(context)
  private val grindTexts = ResourcesGrindTexts(context)
  private lateinit var db: HistoryDatabase
  private lateinit var settings: GrinderSettings
  private lateinit var recipes: RecipeRepository
  private lateinit var grinders: GrinderRepository
  private val imports = PendingRecipeImport()
  private var done = 0

  @Before
  fun setUp() {
    context.deleteSharedPreferences(PREFS)
    db = HistoryDatabase.inMemory(context, texts)
    settings = GrinderSettings(context, PREFS)
    recipes = RecipeRepository(db, texts)
    grinders = GrinderRepository(db, settings, grindTexts)
    runBlocking { GrinderCatalogSeeder(db.grinderDao(), { TestGrinderCatalog.JSON }, settings, settings).sync() }
  }

  @After
  fun tearDown() {
    db.close()
    context.deleteSharedPreferences(PREFS)
  }

  private fun catalog(key: String): Grinder = runBlocking { grinders.byCatalogKey(key)!! }

  private fun own(key: String): Grinder = catalog(key).also { runBlocking { grinders.setOwned(it.id, true) } }

  private fun stages(withInstruction: Boolean = false): List<RecipeStage> = listOf(
    RecipeStage("Bloom", 0, 40.0, 30_000, StageType.POUR, if (withInstruction) "Vierte en círculos" else null),
    RecipeStage(null, 30_000, 40.0, 15_000, StageType.WAIT),
    RecipeStage("Segundo", 45_000, 250.0, 60_000, StageType.POUR, if (withInstruction) "Sin prisa" else null),
  )

  private fun recipe(
    name: String = "Mi receta",
    dose: Double = 15.0,
    notes: String? = null,
    grind: GrindSetting? = null,
    withInstruction: Boolean = false,
    stages: List<RecipeStage> = stages(withInstruction),
  ) = Recipe(name = name, method = BrewMethod.V60, waterTempC = 93.0, notes = notes, doseG = dose, grind = grind, stages = stages)

  private fun uriOf(recipe: Recipe, grinder: Grinder? = null): String = RecipeShareCodec.encode(recipe, grinder)!!.uri

  private fun viewModel(uri: String) = RecipeImportViewModel(uri, recipes, grinders, grindTexts, imports)

  private fun show(uri: String, viewModel: RecipeImportViewModel = viewModel(uri)): RecipeImportViewModel {
    rule.setContent { PourlineTheme(darkTheme = false) { RecipeImportScreen(viewModel, onDone = { done++ }) } }
    rule.waitUntil(5_000) { exists("recipe_import_save") || done > 0 }
    return viewModel
  }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  private fun save() {
    rule.onNodeWithTag("recipe_import_save").performScrollTo().performClick()
    rule.waitUntil(5_000) { done > 0 }
  }

  private fun saved(): List<Recipe> = runBlocking { recipes.all() }.filter { it.builtInKey == null }

  private fun randomText(length: Int, seed: Long): String {
    val letters = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    val random = Random(seed)
    return String(CharArray(length) { letters[random.nextInt(letters.size)] })
  }

  @Test
  fun showsTheTotalTimeNextToTheWaterAndTheRatio() {
    show(uriOf(recipe()))

    // Los pasos acaban en 1:45 (45 s de inicio y 60 s de duración del último).
    rule.onNodeWithTag("recipe_import_duration").performScrollTo().assertIsDisplayed().assertTextEquals("1:45")
      .assertContentDescriptionEquals(context.getString(R.string.recipe_total_time_description, "1:45"))
  }

  @Test
  fun showsTheRecipeWithNotesAndStepInstructions() {
    show(uriOf(recipe(notes = "Agua filtrada y molienda media", withInstruction = true)))

    rule.onNodeWithTag("recipe_import_name").assertIsDisplayed()
    rule.onNodeWithTag("recipe_import_notes").performScrollTo().assertIsDisplayed()
    rule.onNode(hasText("Agua filtrada y molienda media")).assertExists()
    rule.onNodeWithTag("recipe_import_step_instruction_0").performScrollTo().assertIsDisplayed()
    rule.onNode(hasText("Vierte en círculos")).assertExists()
    assertTrue(exists("recipe_import_step_2"))
    assertFalse(exists("recipe_import_trimmed"))
    assertFalse(exists("recipe_import_identical"))
  }

  @Test
  fun showsTheTrimmedWarning() {
    val long = recipe(
      notes = randomText(600, 99),
      stages = stages().map { it.copy(instruction = randomText(100, it.startMs)) },
    )
    val encoded = RecipeShareCodec.encode(long, null)!!
    assertTrue("la receta debía recortarse", encoded.trimmed.isNotEmpty())
    show(encoded.uri)

    rule.onNodeWithTag("recipe_import_trimmed").assertIsDisplayed()
    assertFalse(exists("recipe_import_notes"))
  }

  /** [count] pasos que alternan `POUR` y `WAIT`, todos con una instrucción aleatoria de [instructionLength] caracteres. */
  private fun stagesWithInstructions(count: Int, instructionLength: Int): List<RecipeStage> {
    var startMs = 0L
    var water = 0.0
    return List(count) { i ->
      val pour = i % 2 == 0
      if (pour) water += 20.0
      RecipeStage(
        name = if (pour) "Vertido ${i + 1}" else null,
        startMs = startMs,
        targetWaterG = water,
        durationMs = 20_000,
        type = if (pour) StageType.POUR else StageType.WAIT,
        instruction = randomText(instructionLength, 10L + i),
      ).also { startMs += 25_000 }
    }
  }

  private fun trimmedMessage(vararg parts: Int): String {
    val names = parts.map { context.getString(it) }
    val list = if (names.size == 1) names[0] else context.getString(R.string.recipe_trimmed_list_two, names[0], names[1])
    return context.getString(R.string.recipe_import_trimmed, list)
  }

  @Test
  fun showsActionStepsWithTheirTypeAndInstructionAndSavesThem() {
    val action = context.getString(R.string.step_type_action)
    show(uriOf(aeroPressWithActions()))

    for (i in listOf(2, 4, 5)) {
      rule.onNode(hasTestTag("recipe_import_step_$i") and hasAnyDescendant(hasText(action))).performScrollTo().assertIsDisplayed()
    }
    rule.onNodeWithTag("recipe_import_step_instruction_2").performScrollTo().assertTextEquals("Pon la tapa")
    rule.onNodeWithTag("recipe_import_step_instruction_4").performScrollTo().assertTextEquals("Invierte")
    assertFalse(exists("recipe_import_step_instruction_5"))
    rule.onNodeWithTag("recipe_import_duration").performScrollTo().assertTextEquals("2:00")

    save()
    val stages = saved().single().stages
    assertEquals(
      listOf(StageType.POUR, StageType.SWIRL, StageType.ACTION, StageType.WAIT, StageType.ACTION, StageType.ACTION),
      stages.map { it.type },
    )
    assertEquals(listOf(null, null, "Pon la tapa", null, "Invierte", null), stages.map { it.instruction })
  }

  @Test
  fun aTrimmedActionShowsItsTypeAndThePartialWarning() {
    // Como `showsThePartialInstructionsWarning`, pero con una Acción en lugar de cada espera.
    val stages = stagesWithInstructions(10, 60).map { if (it.type == StageType.WAIT) it.copy(type = StageType.ACTION) else it }
    val long = recipe(notes = randomText(300, 99), stages = stages)
    val encoded = RecipeShareCodec.encode(long, null)!!
    assertTrue("el recorte debía ser parcial: ${encoded.droppedInstructions} quitadas y ${encoded.keptInstructions} conservadas", encoded.partialInstructions)
    show(encoded.uri)

    rule.onNodeWithTag("recipe_import_trimmed").assertIsDisplayed()
    rule.onNode(hasText(trimmedMessage(R.string.recipe_trimmed_notes, R.string.recipe_trimmed_some_instructions))).assertExists()
    // Las Acciones siguen siendo Acciones; la del final se muestra sin instrucción y la primera con ella.
    val action = context.getString(R.string.step_type_action)
    rule.onNode(hasTestTag("recipe_import_step_9") and hasAnyDescendant(hasText(action))).performScrollTo().assertIsDisplayed()
    assertFalse(exists("recipe_import_step_instruction_9"))
    rule.onNodeWithTag("recipe_import_step_instruction_1").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun showsThePartialInstructionsWarning() {
    val long = recipe(notes = randomText(300, 99), stages = stagesWithInstructions(10, 60))
    val encoded = RecipeShareCodec.encode(long, null)!!
    assertTrue("el recorte debía ser parcial: ${encoded.droppedInstructions} quitadas y ${encoded.keptInstructions} conservadas", encoded.partialInstructions)
    show(encoded.uri)

    rule.onNodeWithTag("recipe_import_trimmed").assertIsDisplayed()
    rule.onNode(hasText(trimmedMessage(R.string.recipe_trimmed_notes, R.string.recipe_trimmed_some_instructions))).assertExists()
    // Las instrucciones que cupieron siguen en la vista previa; las del final no.
    rule.onNodeWithTag("recipe_import_step_instruction_0").performScrollTo().assertIsDisplayed()
    assertFalse(exists("recipe_import_step_instruction_9"))
  }

  @Test
  fun aQrWithEveryInstructionRemovedSaysTheStepInstructions() {
    val long = recipe(name = randomText(400, 5), stages = stagesWithInstructions(3, 200))
    val encoded = RecipeShareCodec.encode(long, null)!!
    assertEquals(3 to 0, encoded.droppedInstructions to encoded.keptInstructions)
    show(encoded.uri)

    rule.onNodeWithTag("recipe_import_trimmed").assertIsDisplayed()
    rule.onNode(hasText(trimmedMessage(R.string.recipe_trimmed_instructions))).assertExists()
    assertFalse(exists("recipe_import_step_instruction_0"))
  }

  @Test
  fun anIdenticalRecipeWarnsAndStillSavesWithASuffix() {
    val builtIn = runBlocking { recipes.all() }.first { it.builtInKey == "v60_classic" }
    show(uriOf(builtIn))

    rule.onNodeWithTag("recipe_import_identical").assertIsDisplayed()
    save()

    val names = saved().map { it.name }
    assertEquals(listOf(texts.builtInName("v60_classic") + " (2)"), names)
    assertEquals(null, saved().single().builtInKey)
  }

  @Test
  fun sameNameDifferentContentSavesWithASuffixWithoutWarning() {
    runBlocking { recipes.save(recipe(name = "Mi receta", dose = 20.0)) }
    show(uriOf(recipe(name = "mi receta ", dose = 15.0)))

    assertFalse(exists("recipe_import_identical"))
    save()

    assertEquals(listOf("Mi receta", "mi receta (2)").sorted(), saved().map { it.name }.sorted())
  }

  @Test
  fun theSameGrinderIsLinked() {
    val uno = own(TestGrinderCatalog.KEY_UNO)
    show(uriOf(recipe(grind = GrindSetting(uno.id, 20.0, "x")), uno))

    assertTrue(exists("recipe_import_grind_text"))
    save()

    val grind = saved().single().grind!!
    assertEquals(uno.id, grind.grinderId)
    assertEquals(20.0, grind.value!!, 1e-9)
    assertTrue("copia de texto: ${grind.label}", grind.label!!.contains("Uno"))
  }

  @Test
  fun aConvertibleGrindOffersEveryOwnedGrinderAndPreselectsTheFirst() {
    val uno = catalog(TestGrinderCatalog.KEY_UNO)
    val dos = own(TestGrinderCatalog.KEY_DOS)
    own(TestGrinderCatalog.KEY_TRES)
    show(uriOf(recipe(grind = GrindSetting(uno.id, 20.0, "x")), uno))

    rule.onNodeWithTag("recipe_import_grind").performScrollTo().performClick()
    rule.onNode(hasText("Acme Dos", substring = true) and hasAnyAncestor(isPopup())).assertExists()
    rule.onNode(hasText("Acme Tres", substring = true) and hasAnyAncestor(isPopup())).assertExists()
    rule.onNode(hasText(context.getString(R.string.recipe_import_grind_as_text)) and hasAnyAncestor(isPopup())).assertExists()
    // Se cierra el menú eligiendo el primero, que es el preseleccionado.
    rule.onNode(hasText("Acme Dos", substring = true) and hasAnyAncestor(isPopup())).performClick()
    save()

    val grind = saved().single().grind!!
    assertEquals(dos.id, grind.grinderId)
    assertEquals(30.0, grind.value!!, 1e-9)
  }

  @Test
  fun choosingSaveAsTextSavesTheLabel() {
    val uno = catalog(TestGrinderCatalog.KEY_UNO)
    own(TestGrinderCatalog.KEY_DOS)
    show(uriOf(recipe(grind = GrindSetting(uno.id, 20.0, "x")), uno))

    rule.onNodeWithTag("recipe_import_grind").performScrollTo().performClick()
    rule.onNode(hasText(context.getString(R.string.recipe_import_grind_as_text)) and hasAnyAncestor(isPopup())).performClick()
    save()

    val grind = saved().single().grind!!
    assertNull(grind.grinderId)
    assertNull(grind.value)
    assertTrue("etiqueta: ${grind.label}", grind.label!!.startsWith("Acme Uno"))
  }

  @Test
  fun aGrinderRetiredBeforeSavingIsSavedAsText() {
    val uno = catalog(TestGrinderCatalog.KEY_UNO)
    val dos = own(TestGrinderCatalog.KEY_DOS)
    show(uriOf(recipe(grind = GrindSetting(uno.id, 20.0, "x")), uno))
    rule.onNodeWithTag("recipe_import_grind").performScrollTo()

    // Mientras se mira la vista previa, el molino elegido deja de estar entre los propios.
    runBlocking { grinders.setOwned(dos.id, false) }
    save()

    val grind = saved().single().grind!!
    assertNull(grind.grinderId)
    assertTrue(grind.label!!.startsWith("Acme Uno"))
  }

  @Test
  fun noGrindSavesWithoutGrind() {
    show(uriOf(recipe()))

    assertFalse(exists("recipe_import_grind_text"))
    assertFalse(exists("recipe_import_grind"))
    save()

    assertNull(saved().single().grind)
  }

  @Test
  fun cancelSavesNothing() {
    show(uriOf(recipe()))

    rule.onNodeWithTag("recipe_import_cancel").performScrollTo().performClick()
    rule.waitUntil(5_000) { done > 0 }

    assertTrue(saved().isEmpty())
  }

  @Test
  fun aViewModelRebuiltFromTheSameUriShowsTheSameRecipe() {
    val uri = uriOf(recipe(notes = "Notas", withInstruction = true))
    val first = runBlocking { withTimeout(5_000) { viewModel(uri).ui.first { it is RecipeImportUi.Ready } } } as RecipeImportUi.Ready
    val second = runBlocking { withTimeout(5_000) { viewModel(uri).ui.first { it is RecipeImportUi.Ready } } } as RecipeImportUi.Ready

    assertEquals(first.shared, second.shared)
    assertEquals(first.identical, second.identical)
  }

  @Test
  fun aUriThatNoLongerDecodesNotifiesTheErrorAndCloses() {
    show("pourline://recipe?v=1&d=AAAA")

    assertTrue(done > 0)
    val notice = runBlocking { withTimeout(5_000) { imports.notices.first() } }
    assertEquals(ImportNotice.Failed(ImportError.CORRUPT), notice)
  }

  @Test
  fun theStartTimesAreNotCutWithALargeFontScale() {
    val uri = uriOf(recipe(withInstruction = true))
    rule.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
        PourlineTheme(darkTheme = false) { RecipeImportScreen(viewModel(uri), onDone = { done++ }) }
      }
    }
    rule.waitUntil(5_000) { exists("recipe_import_save") }

    // "0:45" es la más ancha de las tres salidas (0:00, 0:30, 0:45); si la columna fuera de `dp` fijos perdería el último dígito.
    val start = rule.onNode(hasText("0:45") and hasAnyAncestor(hasTestTag("recipe_import_step_2"))).performScrollTo()
    val layouts = mutableListOf<TextLayoutResult>()
    start.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
    assertFalse("la hora de inicio se corta", layouts.single().hasVisualOverflow)
  }

  private companion object {
    const val PREFS = "recipe_import_test"
  }
}
