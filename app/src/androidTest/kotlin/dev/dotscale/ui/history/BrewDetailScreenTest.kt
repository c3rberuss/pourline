package dev.dotscale.ui.history

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.coffee.CoffeeRepository
import dev.dotscale.coffee.PhotoFiles
import dev.dotscale.grinder.GrindSetting
import dev.dotscale.history.BrewDetails
import dev.dotscale.history.BrewMethod
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.history.TestBrews
import dev.dotscale.history.UsedCoffee
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.settings.ExperienceMode
import dev.dotscale.settings.LocalExperienceMode
import dev.dotscale.ui.theme.DotScaleTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BrewDetailScreenTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var db: HistoryDatabase
  private lateinit var repository: BrewRepository
  private var backCount = 0
  private var edited: Long? = null
  private var compared: Long? = null
  private var savedAsRecipe: Long? = null
  private var openedCoffee: Long? = null
  private lateinit var coffees: CoffeeRepository
  private val mode = mutableStateOf(ExperienceMode.EXPERT)

  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(context)
    repository = BrewRepository(db)
    coffees = CoffeeRepository(db, PhotoFiles { })
  }

  @After
  fun tearDown() = db.close()

  private fun show(id: Long) {
    val viewModel = BrewDetailViewModel(repository, coffees, id)
    rule.setContent {
      CompositionLocalProvider(LocalExperienceMode provides mode.value) {
        DotScaleTheme(darkTheme = false) {
          BrewDetailScreen(
            viewModel,
            onBack = { backCount++ },
            onEdit = { edited = it },
            onCompare = { compared = it },
            onSaveAsRecipe = { savedAsRecipe = it },
            onOpenCoffee = { openedCoffee = it },
          )
        }
      }
    }
  }

  private fun seed(unit: WeightUnit = WeightUnit.GRAMS, details: BrewDetails? = null): Long = runBlocking {
    val id = repository.save(TestBrews.finished(totalMs = 60_000), TestBrews.context(unit = unit))
    if (details != null) repository.updateDetails(id, details)
    id
  }

  @Test
  fun showsTheChartTheSummaryAndTheDetails() {
    val id = seed(
      details = BrewDetails(name = "Mañana", method = BrewMethod.V60, grind = GrindSetting(null, 21.0, "21 clics"), waterTempC = 92.5),
    )
    show(id)

    rule.waitUntil(5_000) { rule.onAllNodesWithTag("brew_chart").fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithTag("brew_chart").assertIsDisplayed()
    rule.onNodeWithTag("brew_summary").performScrollTo().assertIsDisplayed()
    rule.onNodeWithTag("brew_detail_details").performScrollTo().assertIsDisplayed()
    for (text in listOf("V60", "21 clics")) {
      rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty().let { assertEquals(text, true, it) }
    }
  }

  // --- Modo Básico (SPEC-005) ---

  @Test
  fun inBasicModeTheDetailHasNoChartFitCompareOrSaveAsRecipeButShowsTheFacts() {
    val guji = catalogCoffee("Etiopía Guji", LocalDate.of(2026, 11, 5))
    val id = seedLinked(guji, details = BrewDetails(notes = "Jazmín y durazno", rating = 4, method = BrewMethod.V60, grind = GrindSetting(null, 21.0, "21 clics")))
    mode.value = ExperienceMode.BASIC
    show(id)

    rule.waitUntil(5_000) { rule.onAllNodesWithTag("brew_detail_facts").fetchSemanticsNodes().isNotEmpty() }
    for (tag in listOf("brew_chart", "brew_summary", "brew_detail_fit", "brew_detail_compare", "brew_detail_save_recipe", "brew_detail_tasting")) {
      rule.onAllNodesWithTag(tag).assertCountEquals(0)
    }
    rule.onNodeWithTag("brew_detail_facts").performScrollTo().assertIsDisplayed()
    for (key in listOf("date", "dose", "water", "ratio", "time")) {
      rule.onNodeWithTag("brew_detail_facts_$key").performScrollTo().assertIsDisplayed()
    }
    rule.onNodeWithTag("brew_detail_rating").performScrollTo().assertIsDisplayed()
    rule.onNodeWithTag("brew_detail_notes").performScrollTo().assertTextEquals("Jazmín y durazno")
    // Los detalles manuales y las acciones de editar y borrar siguen.
    rule.onNodeWithTag("brew_detail_details").performScrollTo().assertIsDisplayed()
    rule.onNodeWithTag("brew_detail_edit").performScrollTo().assertIsDisplayed()
    rule.onNodeWithTag("brew_detail_delete").performScrollTo().assertIsDisplayed()
    // El café es un enlace.
    rule.onNodeWithTag("brew_detail_coffee").performScrollTo().performClick()
    assertEquals(guji, openedCoffee)
  }

  @Test
  fun switchingToExpertInTheSameCompositionShowsTheChartTheStabilityAndTheFit() {
    val id = seed()
    mode.value = ExperienceMode.BASIC
    show(id)
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("brew_detail_facts").fetchSemanticsNodes().isNotEmpty() }
    rule.onAllNodesWithTag("brew_chart").assertCountEquals(0)

    rule.runOnUiThread { mode.value = ExperienceMode.EXPERT }

    rule.waitUntil(5_000) { rule.onAllNodesWithTag("brew_chart").fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithTag("brew_chart").assertIsDisplayed()
    rule.onNodeWithTag("brew_summary").performScrollTo().assertIsDisplayed()
    rule.onNodeWithTag("brew_detail_compare").performScrollTo().assertIsDisplayed()
    rule.onNodeWithTag("brew_detail_save_recipe").performScrollTo().assertIsDisplayed()
    rule.onAllNodesWithTag("brew_detail_facts").assertCountEquals(0)
  }

  // --- Cata (SPEC-004) ---

  private fun catalogCoffee(name: String, roastDate: LocalDate? = null, roaster: String? = "Café de Altura"): Long =
    runBlocking { coffees.save(TestBrews.coffeeDraft(name = name, roaster = roaster).copy(roastDate = roastDate)) }

  private fun seedLinked(coffeeId: Long, startedAt: Long = 1_700_000_000_000, details: BrewDetails? = null): Long = runBlocking {
    val id = repository.save(
      TestBrews.finished(totalMs = 60_000),
      TestBrews.context(startedAtEpochMs = startedAt, coffee = UsedCoffee(coffeeId, "x")),
    )
    if (details != null) repository.updateDetails(id, details)
    id
  }

  @Test
  fun theTastingCardShowsTheCoffeeTheDayTheRatingAndTheNotes() {
    val brewStart = LocalDate.of(2026, 11, 14).atTime(8, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val guji = catalogCoffee("Etiopía Guji", LocalDate.of(2026, 11, 5))
    val id = seedLinked(guji, brewStart, BrewDetails(notes = "Jazmín y durazno", rating = 4))
    show(id)
    waitForDetail()

    rule.onNodeWithTag("brew_detail_tasting").performScrollTo().assertIsDisplayed()
    rule.onNode(hasText("Etiopía Guji") and hasAnyAncestor(hasTestTag("brew_detail_coffee")), useUnmergedTree = true).assertExists()
    rule.onNode(
      hasText("Café de Altura · " + context.getString(R.string.coffee_log_day, 9)) and hasAnyAncestor(hasTestTag("brew_detail_coffee")),
      useUnmergedTree = true,
    ).assertExists()
    rule.onNodeWithTag("brew_detail_rating", useUnmergedTree = true).assertContentDescriptionEquals(context.getString(R.string.rate_description, 4, 5))
    rule.onNodeWithTag("brew_detail_notes").assertTextEquals("Jazmín y durazno")
  }

  @Test
  fun tappingTheCoffeeOpensItsDetail() {
    val guji = catalogCoffee("Guji")
    show(seedLinked(guji))
    waitForDetail()

    rule.onNodeWithTag("brew_detail_coffee").performScrollTo().performClick()
    assertEquals(guji, openedCoffee)
  }

  @Test
  fun detailsNoLongerRepeatTheCoffeeNorTheNotes() {
    val guji = catalogCoffee("Guji")
    val id = seedLinked(guji, details = BrewDetails(method = BrewMethod.V60, notes = "Notas únicas", rating = 3))
    show(id)
    waitForDetail()

    val details = hasAnyAncestor(hasTestTag("brew_detail_details"))
    rule.onNodeWithTag("brew_detail_details").performScrollTo().assertIsDisplayed()
    assertEquals(0, rule.onAllNodes(hasText("Guji") and details, useUnmergedTree = true).fetchSemanticsNodes().size)
    assertEquals(0, rule.onAllNodes(hasText("Notas únicas") and details, useUnmergedTree = true).fetchSemanticsNodes().size)
    assertEquals(1, rule.onAllNodes(hasText("Notas únicas"), useUnmergedTree = true).fetchSemanticsNodes().size)
  }

  @Test
  fun anOldCoffeeTextIsShownWithoutThumbnailOrLinkAndIsNotClickable() {
    val id = seed()
    db.openHelper.writableDatabase.execSQL("UPDATE brews SET coffee = 'Etiopía antigua' WHERE id = $id")
    show(id)
    waitForDetail()

    rule.onNodeWithTag("brew_detail_tasting").performScrollTo().assertIsDisplayed()
    rule.onNode(hasText("Etiopía antigua") and hasAnyAncestor(hasTestTag("brew_detail_coffee")), useUnmergedTree = true).assertExists()
    rule.onNode(hasText(context.getString(R.string.brew_detail_coffee_legacy)), useUnmergedTree = true).assertExists()
    rule.onNodeWithTag("brew_detail_coffee").assertHasNoClickAction()
    rule.onNodeWithTag("brew_detail_coffee").performClick()
    assertNull(openedCoffee)
  }

  @Test
  fun theTastingCardIsOmittedWithoutCoffeeRatingOrNotes() {
    show(seed())
    waitForDetail()
    assertEquals(false, exists("brew_detail_tasting"))
  }

  @Test
  fun aRatingAloneIsEnoughForTheCardAndTheOtherPartsAreOmitted() {
    show(seed(details = BrewDetails(rating = 5)))
    waitForDetail()

    rule.onNodeWithTag("brew_detail_tasting").performScrollTo().assertIsDisplayed()
    assertEquals(false, exists("brew_detail_coffee"))
    assertEquals(false, exists("brew_detail_notes"))
    assertEquals(true, rule.onAllNodesWithTag("brew_detail_rating", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
  }

  @Test
  fun aBrewMadeInOuncesIsShownInOunces() {
    show(seed(unit = WeightUnit.OUNCES))
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("brew_summary").fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithTag("brew_summary").performScrollTo()
    assertEquals(true, rule.onAllNodesWithText("oz", substring = true).fetchSemanticsNodes().isNotEmpty())
  }

  @Test
  fun theDetailsCardIsOmittedWithoutManualData() {
    show(seed())
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("brew_summary").fetchSemanticsNodes().isNotEmpty() }
    assertEquals(0, rule.onAllNodesWithTag("brew_detail_details").fetchSemanticsNodes().size)
  }

  @Test
  fun editOpensTheEditScreenOfThatBrew() {
    val id = seed()
    show(id)
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("brew_detail_edit").fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithTag("brew_detail_edit").performScrollTo().performClick()
    assertEquals(id, edited)
  }

  @Test
  fun deleteGoesBackOnceAndLeavesTheBrewForUndo() {
    val id = seed()
    show(id)
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("brew_detail_delete").fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithTag("brew_detail_delete").performScrollTo().performClick()

    rule.waitUntil(5_000) { backCount > 0 }
    rule.waitForIdle()
    assertEquals(1, backCount)
    assertNull(runBlocking { repository.get(id) })
    assertEquals(id, repository.pendingUndo.value?.entity?.id)
  }

  @Test
  fun aMissingBrewGoesBack() {
    show(404)
    rule.waitUntil(5_000) { backCount > 0 }
  }

  // --- Objetivo (SPEC-003) ---

  private fun seedWithTarget(): Long = runBlocking {
    repository.save(TestBrews.finished(totalMs = 60_000), TestBrews.context(target = TestBrews.usedTarget(score = 86)))
  }

  private fun waitForDetail() = rule.waitUntil(5_000) { rule.onAllNodesWithTag("brew_summary").fetchSemanticsNodes().isNotEmpty() }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  @Test
  fun aBrewWithATargetShowsItsCurveAndTheFitCard() {
    show(seedWithTarget())
    waitForDetail()

    rule.onNodeWithTag("brew_detail_fit").performScrollTo().assertIsDisplayed()
    rule.onNodeWithTag("fit_score").assertIsDisplayed()
    rule.onNodeWithTag("fit_reference").assertIsDisplayed()
    assertEquals(false, exists("brew_detail_target_error"))
    // La gráfica lleva el objetivo en su descripción.
    val targetPrefix = context.getString(R.string.target_description, "", "").substringBefore(":")
    rule.onNode(androidx.compose.ui.test.hasContentDescription(targetPrefix, substring = true)).assertExists()
  }

  @Test
  fun aBrewWithoutATargetHasNoFitCard() {
    show(seed())
    waitForDetail()
    assertEquals(false, exists("brew_detail_fit"))
  }

  @Test
  fun aCorruptTargetShowsTheFitWithoutTheCurveAndAnAlert() {
    val id = seedWithTarget()
    db.openHelper.writableDatabase.execSQL("UPDATE brew_targets SET data = ? WHERE brewId = ?", arrayOf<Any>(byteArrayOf(1, 2, 3), id))
    show(id)
    waitForDetail()

    rule.onNodeWithTag("brew_detail_target_error").performScrollTo().assertIsDisplayed()
    rule.onNodeWithTag("brew_detail_fit").performScrollTo().assertIsDisplayed()
    assertEquals(true, exists("brew_chart")) // la curva real se dibuja igual
  }

  @Test
  fun compareAndSaveAsRecipeOpenTheirScreens() {
    val id = seed()
    show(id)
    waitForDetail()

    rule.onNodeWithTag("brew_detail_compare").performScrollTo().performClick()
    assertEquals(id, compared)
    rule.onNodeWithTag("brew_detail_save_recipe").performScrollTo().performClick()
    assertEquals(id, savedAsRecipe)
  }

  @Test
  fun saveAsRecipeIsDisabledWithAnUnreadableCurve() {
    val id = seed()
    db.openHelper.writableDatabase.execSQL("UPDATE brew_curves SET data = x'0102030405' WHERE brewId = $id")
    show(id)
    rule.waitUntil(5_000) { exists("brew_detail_save_recipe") }

    rule.onNodeWithTag("brew_detail_save_recipe").performScrollTo().assertIsNotEnabled()
    rule.onNodeWithTag("brew_detail_compare").assertIsEnabled()
  }
}
