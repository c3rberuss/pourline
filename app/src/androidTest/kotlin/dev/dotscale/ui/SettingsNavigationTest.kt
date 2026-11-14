package dev.dotscale.ui

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.DotScaleApp
import dev.dotscale.MainActivity
import dev.dotscale.R
import dev.dotscale.Tab
import dev.dotscale.settings.ExperienceMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class SettingsNavigationTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext

  /** Concede los permisos BLE antes de lanzar la `Activity`, para que no aparezca el diálogo del sistema. */
  private val grantBlePermissions = TestRule { base: Statement, _: Description ->
    object : Statement() {
      override fun evaluate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT).forEach {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it)
          }
        }
        base.evaluate()
      }
    }
  }

  /** El primer arranque ya está hecho: la actividad abre Preparar en lugar de la elección de modo. */
  private val skipOnboarding = TestRule { base: Statement, _: Description ->
    object : Statement() {
      override fun evaluate() {
        (context.applicationContext as DotScaleApp).experienceSettings.completeOnboarding(ExperienceMode.BASIC)
        base.evaluate()
      }
    }
  }

  private val compose = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val chain: RuleChain = RuleChain.outerRule(grantBlePermissions).around(skipOnboarding).around(compose)

  private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  @Test
  fun gearOpensSettingsFromPrepare() {
    compose.onNodeWithTag("prepare_settings").performClick()
    compose.onNodeWithText(context.getString(R.string.settings_auto_finish_title)).assertIsDisplayed()
  }

  @Test
  fun noTopBarHasSectionIcons() {
    Tab.entries.forEach { tab ->
      compose.onNodeWithTag(tab.testTag).performClick()
      compose.waitForIdle()
      SectionIconDescriptions.forEach { compose.onNodeWithContentDescription(it).assertDoesNotExist() }
      assertTrue("la barra superior de ${tab.name} no debería tener el botón de tema", !exists("theme_change"))
    }
  }
}
