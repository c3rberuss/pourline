package dev.dotscale

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/**
 * Concede el permiso de notificaciones antes de cualquier test: "Empezar" lo pide en Android 13+ y el diálogo del
 * sistema taparía la pantalla en los tests que abren una sesión.
 */
class DotScaleTestRunner : AndroidJUnitRunner() {
  override fun onCreate(arguments: Bundle?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      uiAutomation.grantRuntimePermission(targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }
    super.onCreate(arguments)
  }
}
