package dev.josueamaya.pourline

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El manifiesto de la app instalada (SPEC-009 D29, SPEC-012 D43): con red solo para el catálogo y sin cámara, sin ningún
 * componente de datatransport (la telemetría de ML Kit) y con las entradas de recetas en `MainActivity`.
 */
class ManifestPermissionsTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val pm = context.packageManager
  private val main = "dev.josueamaya.pourline.MainActivity"

  @Suppress("DEPRECATION")
  private fun resolved(intent: Intent) = pm.queryIntentActivities(intent.setPackage(context.packageName), PackageManager.MATCH_DEFAULT_ONLY)
    .map { it.activityInfo.name }

  @Test
  fun theAppRequestsInternetButNotCamera() {
    @Suppress("DEPRECATION")
    val requested = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
    assertTrue("INTERNET: $requested", "android.permission.INTERNET" in requested)
    assertFalse("CAMERA: $requested", "android.permission.CAMERA" in requested)
  }

  @Test
  fun noDataTransportComponentIsDeclared() {
    @Suppress("DEPRECATION")
    val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS)
    val names = info.services.orEmpty().map { it.name } + info.receivers.orEmpty().map { it.name }
    val transport = names.filter { it.startsWith("com.google.android.datatransport") }
    assertTrue("datatransport: $transport", transport.isEmpty())
  }

  @Test
  fun aRecipeLinkResolvesToMainActivity() {
    val view = Intent(Intent.ACTION_VIEW, Uri.parse("pourline://recipe?v=1&d=AAAA")).addCategory(Intent.CATEGORY_BROWSABLE)
    assertEquals(listOf(main), resolved(view))
  }

  @Test
  fun anImageShareResolvesToMainActivity() {
    val send = Intent(Intent.ACTION_SEND).setType("image/png")
    assertTrue(main in resolved(send))
  }

  @Test
  fun mainActivityIsSingleTask() {
    val info = pm.getActivityInfo(android.content.ComponentName(context.packageName, main), 0)
    assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, info.launchMode)
  }
}
