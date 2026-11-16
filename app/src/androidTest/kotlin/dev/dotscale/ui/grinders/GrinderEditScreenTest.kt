package dev.dotscale.ui.grinders

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.grinder.GrindLevel
import dev.dotscale.grinder.GrindUnit
import dev.dotscale.history.BrewMethod
import dev.dotscale.ui.TestApp
import dev.dotscale.ui.TestAppDependencies
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GrinderEditScreenTest {
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

  private fun openEditor() {
    rule.setContent { TestApp(env) }
    ui.openGrinders()
    ui.click("grinders_add")
    ui.waitFor("grinder_catalog_create")
    ui.click("grinder_catalog_create")
    ui.waitFor("grinder_edit_brand")
  }

  private fun unitName(unit: GrindUnit) = context.getString(
    when (unit) {
      GrindUnit.ROTATIONS -> R.string.grind_unit_name_rotations
      GrindUnit.NUMBERS -> R.string.grind_unit_name_numbers
      GrindUnit.CLICKS -> R.string.grind_unit_name_clicks
      else -> error("sin uso en el test")
    },
  )

  private fun tap(tag: String) = ui.click(tag)

  @Test
  fun createsACompoundGrinderWithARangeAndTwoAnchorsAndShowsItInMyGrinders() {
    openEditor()
    rule.onNodeWithTag("grinder_edit_brand").performTextInput("Acme")
    rule.onNodeWithTag("grinder_edit_model").performTextInput("Prueba")
    ui.choose("grinder_edit_unit_0", unitName(GrindUnit.ROTATIONS))
    tap("grinder_edit_add_unit")
    ui.choose("grinder_edit_unit_1", unitName(GrindUnit.NUMBERS))
    rule.onNodeWithTag("grinder_edit_per_parent_1").performScrollTo().performTextReplacement("4")
    tap("grinder_edit_add_unit")
    // La tercera unidad (clics) queda con 10 por número.
    rule.onNodeWithTag("grinder_edit_save").assertIsNotEnabled() // falta la escala

    tap("grinder_edit_tab_ranges")
    ui.waitFor("grinder_edit_scale_max_0_inc")
    tap("grinder_edit_scale_max_0_inc") // el primer toque fija 4 rotaciones: 160
    rule.onNodeWithTag("grinder_edit_save").assertIsEnabled()

    tap("grinder_edit_range_add_${BrewMethod.V60.name}")
    tap("grinder_edit_range_${BrewMethod.V60.name}_min_0_inc")
    tap("grinder_edit_range_${BrewMethod.V60.name}_max_0_inc")
    tap("grinder_edit_anchor_add_${GrindLevel.FINE.name}")
    tap("grinder_edit_anchor_${GrindLevel.FINE.name}_0_inc")
    tap("grinder_edit_anchor_add_${GrindLevel.COARSE.name}")
    tap("grinder_edit_anchor_${GrindLevel.COARSE.name}_0_inc")
    rule.onNodeWithTag("grinder_edit_save").assertIsEnabled()
    tap("grinder_edit_save")

    // Atrás desde el catálogo vuelve a mis molinos, donde ya está.
    ui.waitFor("grinder_catalog_create")
    rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
    ui.waitFor("grinders_list")

    val saved = ui.owned().single()
    assertEquals("Acme Prueba", "${saved.brand} ${saved.model}")
    assertEquals(listOf(GrindUnit.ROTATIONS, GrindUnit.NUMBERS, GrindUnit.CLICKS), saved.units.map { it.unit })
    assertEquals(listOf(null, 4, 10), saved.units.map { it.perParent })
    assertEquals(160.0, saved.scaleMax, 0.0)
    assertEquals(setOf(BrewMethod.V60), saved.ranges.keys)
    assertEquals(setOf(GrindLevel.FINE, GrindLevel.COARSE), saved.anchors.keys)
    assertTrue(saved.ranges.getValue(BrewMethod.V60).min < saved.ranges.getValue(BrewMethod.V60).max)
    assertTrue(saved.anchors.getValue(GrindLevel.FINE) < saved.anchors.getValue(GrindLevel.COARSE))
    assertTrue(ui.exists("grinder_row_${saved.id}"))
  }

  @Test
  fun errorsShowNextToTheFieldAndSaveDoesNotSaveWithThem() {
    openEditor()
    ui.waitFor("grinder_edit_error_brand")
    ui.waitFor("grinder_edit_error_model")
    rule.onNodeWithTag("grinder_edit_save").assertIsNotEnabled()

    rule.onNodeWithTag("grinder_edit_brand").performTextInput("Acme")
    rule.onNodeWithTag("grinder_edit_model").performTextInput("Uno")
    rule.waitUntil(5_000) { !ui.exists("grinder_edit_error_brand") }
    rule.onNodeWithTag("grinder_edit_step").performScrollTo().performTextClearance()
    ui.waitFor("grinder_edit_error_step")
    rule.onNodeWithTag("grinder_edit_save").assertIsNotEnabled()
    assertEquals(emptyList<Long>(), ui.owned().map { it.id })

    // Una unidad repetida y una cantidad por unidad menor que 2 también se marcan.
    rule.onNodeWithTag("grinder_edit_step").performTextReplacement("1")
    tap("grinder_edit_add_unit")
    ui.choose("grinder_edit_unit_1", unitName(GrindUnit.CLICKS))
    ui.waitFor("grinder_edit_error_unit_1")
    ui.choose("grinder_edit_unit_1", unitName(GrindUnit.NUMBERS))
    rule.onNodeWithTag("grinder_edit_per_parent_1").performScrollTo().performTextReplacement("1")
    ui.waitFor("grinder_edit_error_per_parent_1")
    rule.onNodeWithTag("grinder_edit_save").assertIsNotEnabled()
  }

  @Test
  fun thePreviewShowsTheRangeOfTheTouchedMethod() {
    openEditor()
    tap("grinder_edit_tab_ranges")
    ui.waitFor("grinder_edit_scale_max_0_inc")
    tap("grinder_edit_scale_max_0_inc")
    ui.waitFor("grinder_edit_preview")
    val chemex = "grinder_edit_range_${BrewMethod.CHEMEX.name}"
    val v60 = "grinder_edit_range_${BrewMethod.V60.name}"
    tap("grinder_edit_range_add_${BrewMethod.CHEMEX.name}")
    tap("${chemex}_min_0_inc")
    tap("${chemex}_max_0_inc")
    ui.node("grinder_edit_preview").assertContentDescriptionContains("Chemex", substring = true)

    tap("grinder_edit_range_add_${BrewMethod.V60.name}")
    tap("${v60}_min_0_inc")
    tap("${v60}_max_0_inc")
    ui.node("grinder_edit_preview").assertContentDescriptionContains("V60", substring = true)
    // Tocar la fila de otro método lo selecciona.
    tap(chemex)
    ui.node("grinder_edit_preview").assertContentDescriptionContains("Chemex", substring = true)
    assertFalse(ui.exists("grinder_edit_error_range_${BrewMethod.CHEMEX.name}"))
  }
}
