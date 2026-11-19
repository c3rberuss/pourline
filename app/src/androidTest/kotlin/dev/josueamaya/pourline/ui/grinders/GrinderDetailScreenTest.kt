package dev.josueamaya.pourline.ui.grinders

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.GrindLevel
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.grinder.TestGrinders
import dev.josueamaya.pourline.grinder.addCalibration
import dev.josueamaya.pourline.grinder.addGrinder
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GrinderDetailScreenTest {
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

  private fun openDetail(id: Long) {
    rule.setContent { TestApp(env) }
    ui.openGrinders()
    ui.waitFor("grinder_row_$id")
    ui.click("grinder_row_$id")
    ui.waitFor("grinder_detail_ruler")
  }

  private fun recipeUsing(id: Long, name: String) = runBlocking {
    env.recipes.save(
      Recipe(name = name, method = BrewMethod.V60, grind = GrindSetting(id, 20.0, "x"), doseG = 15.0, stages = listOf(RecipeStage("Bloom", 0, 40.0, 10_000))),
    )
  }

  @Test
  fun aPredefinedGrinderHasNoEditOrDeleteAndShowsItsSource() {
    env.seedGrinders()
    val id = runBlocking { env.grinders.catalog().first().single { it.model == "Uno" }.id }
    runBlocking { env.grinders.setOwned(id, true) }
    openDetail(id)

    assertFalse(ui.exists("grinder_detail_edit"))
    assertFalse(ui.exists("grinder_detail_delete"))
    assertTrue(ui.exists("grinder_attribution"))
    ui.node("grinder_attribution").assertTextContains("Acme Guide", substring = true)
    assertTrue(ui.exists("grinder_attribution_link_acme-uno"))
    assertTrue(ui.exists("grinder_detail_duplicate"))
    assertTrue(ui.exists("grinder_detail_unown"))
    assertTrue(ui.exists("grinder_detail_default"))
    ui.node("grinder_detail_scale").assertTextContains("40", substring = true)
  }

  @Test
  fun aPredefinedGrinderIHaveNotOffersIHaveItAndNoDefault() {
    env.seedGrinders()
    val id = runBlocking { env.grinders.catalog().first().single { it.model == "Dos" }.id }
    rule.setContent { TestApp(env) }
    ui.openGrinders()
    ui.click("grinders_add")
    ui.waitFor("grinder_catalog_acme-dos")
    ui.click("grinder_catalog_acme-dos")
    ui.waitFor("grinder_detail_own")
    assertFalse(ui.exists("grinder_detail_default"))
    assertFalse(ui.exists("grinder_detail_unown"))
    ui.click("grinder_detail_own")
    ui.waitFor("grinder_detail_unown")
    assertTrue(ui.exists("grinder_detail_default"))
    assertEquals(listOf(id), ui.owned().map { it.id })
  }

  @Test
  fun duplicatingOpensTheEditorWithACopyAndSavingLeavesItEditableInMyGrinders() {
    env.seedGrinders()
    val original = runBlocking { env.grinders.catalog().first().single { it.model == "Uno" } }
    runBlocking { env.grinders.setOwned(original.id, true) }
    openDetail(original.id)

    ui.click("grinder_detail_duplicate")
    ui.waitFor("grinder_edit_model")
    ui.node("grinder_edit_model").assertTextContains(env.grindTexts.copyName("Uno"))
    ui.click("grinder_edit_save")
    ui.waitFor("grinder_detail_duplicate") // vuelve al detalle del original

    val copy = ui.owned().single { !it.isPredefined }
    assertEquals(env.grindTexts.copyName("Uno"), copy.model)
    assertEquals("acme-uno", copy.duplicatedFromKey)
    // El original sigue sin "Editar".
    assertFalse(ui.exists("grinder_detail_edit"))
    rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
    ui.waitFor("grinder_row_${copy.id}")
    ui.click("grinder_row_${copy.id}")
    ui.waitFor("grinder_detail_edit")
    assertTrue(ui.exists("grinder_detail_delete"))
  }

  @Test
  fun anOwnGrinderCanBeEditedAndDeletingShowsHowManyRecipesUseIt() {
    val id = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    recipeUsing(id, "R1")
    recipeUsing(id, "R2")
    openDetail(id)

    assertTrue(ui.exists("grinder_detail_edit"))
    ui.click("grinder_detail_delete")
    ui.waitFor("grinder_delete_dialog")
    rule.onNodeWithText("It is used in 2 recipes", substring = true).assertIsDisplayed()
  }

  @Test
  fun deletingAnUnusedGrinderSaysSoAndLeavesTheList() {
    val id = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    openDetail(id)
    ui.click("grinder_detail_delete")
    ui.waitFor("grinder_delete_dialog")
    rule.onNodeWithText(context.getString(R.string.grinder_delete_unused)).assertIsDisplayed()
    ui.click("ant_modal_confirm")
    ui.waitFor("grinders_empty")
    assertEquals(emptyList<Long>(), ui.owned().map { it.id })
  }

  @Test
  fun removingTheDefaultFromMyGrindersClearsIt() {
    env.seedGrinders()
    val id = runBlocking { env.grinders.catalog().first().single { it.model == "Uno" }.id }
    runBlocking { env.grinders.setOwned(id, true) }
    env.grinderSettings.setDefaultGrinderId(id)
    openDetail(id)

    assertTrue(ui.exists("grinder_detail_is_default"))
    assertFalse(ui.exists("grinder_detail_default"))
    ui.click("grinder_detail_unown")
    rule.waitUntil(5_000) { env.grinderSettings.defaultGrinderId.value == null }
    assertNull(env.grinderSettings.defaultGrinderId.value)
    ui.waitFor("grinder_detail_own")
    assertTrue(ui.owned().isEmpty())
    assertFalse(rule.onAllNodesWithTagExists("grinder_detail_is_default"))
  }

  private fun pairs(grinder: Long) =
    runBlocking { env.grinders.calibrations(grinder).first() }.map { it.valueOf(grinder) to it.valueOf(it.otherId(grinder)) }.sortedBy { it.first }

  private fun top(tag: String) = ui.node(tag, unmerged = true).fetchSemanticsNode().positionInRoot.y

  @Test
  fun anOwnGrinderWithoutPairsShowsTheEmptyText() {
    val id = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    openDetail(id)
    ui.waitFor("grinder_calibrations")
    assertTrue(ui.exists("grinder_calibrations_empty"))
  }

  @Test
  fun aPredefinedIDoNotHaveWithoutPairsHasNoSection() {
    env.seedGrinders()
    rule.setContent { TestApp(env) }
    ui.openGrinders()
    ui.click("grinders_add")
    ui.waitFor("grinder_catalog_acme-dos")
    ui.click("grinder_catalog_acme-dos")
    ui.waitFor("grinder_detail_own")
    assertFalse(ui.exists("grinder_calibrations"))
  }

  @Test
  fun addingAPairListsItGroupedByGrinderAndSortedByThisGrinder() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val dos = runBlocking { env.db.addGrinder(TestGrinders.dos, 2_000L) }
    val tres = runBlocking { env.db.addGrinder(TestGrinders.tres, 3_000L) }
    val high = runBlocking { env.db.addCalibration(uno, 30.0, dos, 50.0) }
    runBlocking { env.db.addCalibration(uno, 20.0, tres, 80.0) }
    openDetail(uno)
    ui.waitFor("grinder_calibrations_add")

    ui.click("grinder_calibrations_add")
    ui.waitFor("calibration_sheet")
    ui.choose("calibration_grinder", "Acme Dos")
    ui.click("calibration_value_0_inc") // el primer toque fija el centro de la escala: 20
    ui.click("calibration_other_value_0_inc") // y en Dos, 30
    ui.click("calibration_save")
    ui.waitGone("calibration_sheet")

    assertEquals(setOf(20.0 to 30.0, 20.0 to 80.0, 30.0 to 50.0), pairs(uno).toSet())
    ui.waitFor("grinder_calibrations_group_$dos")
    assertTrue(ui.exists("grinder_calibrations_group_$tres"))
    val low = runBlocking { env.grinders.calibrations(uno).first() }.single { it.otherId(uno) == dos && it.valueOf(uno) == 20.0 }.id
    ui.waitFor("grinder_calibration_$low")
    // Dentro del grupo de Dos, el par con Uno 20 va antes que el de Uno 30.
    assertTrue(top("grinder_calibration_$low") < top("grinder_calibration_$high"))
  }

  @Test
  fun editingAPairChangesItsValues() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val dos = runBlocking { env.db.addGrinder(TestGrinders.dos, 2_000L) }
    val id = runBlocking { env.db.addCalibration(uno, 20.0, dos, 31.0) }
    openDetail(uno)
    ui.waitFor("grinder_calibration_edit_$id")
    ui.click("grinder_calibration_edit_$id")
    ui.waitFor("calibration_sheet")
    // Al editar, el otro molino queda fijo y los dos ajustes se pueden cambiar.
    assertFalse(ui.exists("calibration_grinder"))
    assertTrue(ui.exists("calibration_value_0_inc"))
    ui.click("calibration_other_value_0_inc") // 31 → 31,5
    ui.click("calibration_value_0_inc") // 20 → 21
    ui.click("calibration_save")
    ui.waitGone("calibration_sheet")

    assertEquals(listOf(21.0 to 31.5), pairs(uno))
  }

  @Test
  fun deletingAPairAsksAndRemovesIt() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val dos = runBlocking { env.db.addGrinder(TestGrinders.dos, 2_000L) }
    val id = runBlocking { env.db.addCalibration(uno, 20.0, dos, 31.0) }
    openDetail(uno)
    ui.waitFor("grinder_calibration_delete_$id")
    ui.click("grinder_calibration_delete_$id")
    ui.waitFor("grinder_calibration_delete_dialog")
    // Cancelar no borra.
    ui.click("ant_modal_cancel")
    ui.waitGone("grinder_calibration_delete_dialog")
    assertEquals(listOf(20.0 to 31.0), pairs(uno))

    ui.click("grinder_calibration_delete_$id")
    ui.waitFor("grinder_calibration_delete_dialog")
    ui.click("ant_modal_confirm")
    ui.waitGone("grinder_calibration_$id")
    assertEquals(emptyList<Pair<Double, Double>>(), pairs(uno))
    assertTrue(ui.exists("grinder_calibrations_empty"))
  }

  @Test
  fun aContradictingPairShowsTheConflictAndReplaceSavesIt() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val tres = runBlocking { env.db.addGrinder(TestGrinders.tres, 3_000L) }
    runBlocking {
      env.db.addCalibration(uno, 10.0, tres, 40.0)
      env.db.addCalibration(uno, 30.0, tres, 120.0)
    }
    openDetail(uno)
    ui.waitFor("grinder_calibrations_add")
    ui.click("grinder_calibrations_add")
    ui.waitFor("calibration_sheet")
    // Un solo molino con el que emparejar: ya viene elegido.
    assertTrue(ui.exists("calibration_other_value_0_inc"))
    ui.click("calibration_value_0_inc") // 20
    ui.click("calibration_other_value_0_inc") // el centro de Tres: 100
    ui.click("calibration_other_value_0_inc") // una rotación más (40 clics): 140, por encima de Uno 30 = Tres 120
    ui.click("calibration_save")
    ui.waitFor("calibration_conflict")
    assertEquals(listOf(10.0 to 40.0, 30.0 to 120.0), pairs(uno))

    ui.click("calibration_replace")
    ui.waitGone("calibration_sheet")
    assertEquals(listOf(10.0 to 40.0, 20.0 to 140.0), pairs(uno))
  }

  @Test
  fun aRepeatedValueShowsEquivalenceUpdated() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val dos = runBlocking { env.db.addGrinder(TestGrinders.dos, 2_000L) }
    runBlocking { env.db.addCalibration(uno, 20.0, dos, 31.0) }
    openDetail(uno)
    ui.waitFor("grinder_calibrations_add")
    ui.click("grinder_calibrations_add")
    ui.waitFor("calibration_sheet")
    ui.click("calibration_value_0_inc") // 20, el mismo valor del par que ya hay
    repeat(4) { ui.click("calibration_other_value_0_inc") } // 30, 30,5, 31 y 31,5
    ui.click("calibration_save")
    ui.waitGone("calibration_sheet")

    rule.waitUntil(5_000) {
      rule.onAllNodesWithText(context.getString(R.string.grind_calibration_updated)).fetchSemanticsNodes().isNotEmpty()
    }
    assertEquals(listOf(20.0 to 31.5), pairs(uno))
  }

  @Test
  fun aPairWithAGrinderThatIsNoLongerMineCanOnlyBeDeleted() {
    env.seedGrinders()
    val catalogDos = runBlocking { env.grinders.catalog().first().single { it.model == "Dos" }.id }
    runBlocking { env.grinders.setOwned(catalogDos, true) }
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val id = runBlocking { env.db.addCalibration(uno, 20.0, catalogDos, 30.0) }
    runBlocking { env.grinders.setOwned(catalogDos, false) }
    openDetail(uno)
    ui.waitFor("grinder_calibration_$id")
    assertTrue(ui.exists("grinder_calibration_delete_$id"))
    assertFalse(ui.exists("grinder_calibration_edit_$id"))
  }

  @Test
  fun aValueOutsideTheEditedScaleShowsOutOfScale() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val dos = runBlocking { env.db.addGrinder(TestGrinders.dos, 2_000L) }
    val id = runBlocking { env.db.addCalibration(uno, 30.0, dos, 50.0) }
    // Dos se edita a 0–40: el 50 del par queda fuera de su escala.
    runBlocking {
      val edited = env.grinders.get(dos)!!
      env.grinders.save(edited.copy(scaleMax = 40.0, anchors = edited.anchors + (GrindLevel.COARSE to 40.0)))
    }
    openDetail(uno)
    ui.waitFor("grinder_calibration_$id")
    assertTrue(ui.exists("grinder_calibration_out_of_scale_$id"))
  }

  @Test
  fun noAddWithoutAnotherOwnedGrinder() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    openDetail(uno)
    ui.waitFor("grinder_calibrations")
    assertFalse(ui.exists("grinder_calibrations_add"))
  }

  private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagExists(tag: String) =
    onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
}
