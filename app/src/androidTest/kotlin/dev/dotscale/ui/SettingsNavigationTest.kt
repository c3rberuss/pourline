package dev.dotscale.ui

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.MainActivity
import dev.dotscale.R
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

  private val compose = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val chain: RuleChain = RuleChain.outerRule(grantBlePermissions).around(compose)

  private fun openSettings() {
    compose.onNodeWithContentDescription(context.getString(R.string.settings_open)).performClick()
    compose.onNodeWithText(context.getString(R.string.settings_auto_finish_title)).assertIsDisplayed()
  }

  @Test
  fun gearOpensSettingsFromScan() = openSettings()

  @Test
  fun gearOpensSettingsFromScale() {
    compose.onNodeWithText(context.getString(R.string.scan_simulated)).performClick()
    openSettings()
  }
}
