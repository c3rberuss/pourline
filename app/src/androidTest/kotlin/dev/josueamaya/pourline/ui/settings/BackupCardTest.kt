package dev.josueamaya.pourline.ui.settings

import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.backup.BackupError
import dev.josueamaya.pourline.backup.ExportResult
import dev.josueamaya.pourline.backup.ImportResult
import dev.josueamaya.pourline.backup.PrepareResult
import dev.josueamaya.pourline.grinder.GrinderRepository
import dev.josueamaya.pourline.grinder.ResourcesGrindTexts
import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.settings.BrewSettings
import dev.josueamaya.pourline.settings.ConnectionSettings
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.settings.ExperienceSettings
import dev.josueamaya.pourline.settings.GrinderSettings
import dev.josueamaya.pourline.settings.TargetSettings
import dev.josueamaya.pourline.settings.ThemeSettings
import dev.josueamaya.pourline.ui.history.formatBrewDateTime
import dev.josueamaya.pourline.ui.theme.PourlineTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

/** La tarjeta "Copia de seguridad" de Ajustes con una [FakeBackupOperations] y selectores de documentos falsos. */
class BackupCardTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val resources get() = context.resources
  private val fake = FakeBackupOperations()
  private val registry = FakeDocumentRegistry()
  private val sessionBusy = MutableStateFlow(false)

  /** Lo que el ViewModel vuelve a preguntar al confirmar; un test lo cambia sin pasar por [sessionBusy]. */
  @Volatile private var busyAtConfirm = false

  private val brew = BrewSettings(context, PREFS)
  private val target = TargetSettings(context, PREFS)
  private val experience = ExperienceSettings(context, PREFS)
  private val theme = ThemeSettings(context, PREFS)
  private val connection = ConnectionSettings(context, PREFS)
  private val grinderSettings = GrinderSettings(context, PREFS)
  private lateinit var db: HistoryDatabase

  @Before
  fun setUp() {
    context.deleteSharedPreferences(PREFS)
    experience.setMode(ExperienceMode.BASIC)
    connection.setUseScale(true)
    db = HistoryDatabase.inMemory(context)
    val grinders = GrinderRepository(db, grinderSettings, ResourcesGrindTexts(context))
    val viewModel = BackupViewModel(
      fake,
      sessionBusy = sessionBusy,
      isSessionBusy = { busyAtConfirm || sessionBusy.value },
      now = { LocalDateTime.of(2026, 11, 19, 18, 45, 7) },
    )
    rule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry.owner) {
        PourlineTheme(darkTheme = false) {
          SettingsScreen(
            brew,
            target,
            experience,
            theme,
            grinderRepository = grinders,
            grinderDefaults = grinderSettings,
            scaleMode = connection,
            onOpenGrinders = {},
            onReplayTour = {},
            onBack = {},
            backup = viewModel,
          )
        }
      }
    }
  }

  @After
  fun tearDown() {
    db.close()
    context.deleteSharedPreferences(PREFS)
  }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  private fun waitFor(tag: String) = rule.waitUntil(5_000) { exists(tag) }

  private fun waitGone(tag: String) = rule.waitUntil(5_000) { !exists(tag) }

  private fun hasText(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

  private fun waitForText(text: String) = rule.waitUntil(10_000) { hasText(text) }

  private fun click(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()

  private fun counts() = resources.backupCountsText(FakeBackupOperations.COUNTS)

  @Test
  fun theCardIsShownInBasicAndExpertWithBothButtons() {
    listOf(ExperienceMode.BASIC, ExperienceMode.EXPERT).forEach { mode ->
      experience.setMode(mode)
      rule.waitForIdle()
      rule.onNodeWithTag("settings_backup_export").performScrollTo().assertIsDisplayed().assertIsEnabled()
      rule.onNodeWithTag("settings_backup_import").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }
    assertTrue(hasText(context.getString(R.string.backup_title)))
    assertTrue(hasText(context.getString(R.string.backup_export)))
    assertTrue(hasText(context.getString(R.string.backup_import)))
  }

  @Test
  fun exportOffersTheDatedFileNameAndReportsTheCounts() {
    val uri = Uri.parse("content://test/pourline-backup.zip")
    registry.createResult = uri
    val gate = CompletableDeferred<Unit>().also { fake.exportGate = it }

    click("settings_backup_export")

    assertEquals("pourline-backup-2026-11-19-1845.zip", registry.createInput)
    waitFor("backup_progress")
    rule.onNodeWithTag("settings_backup_export").assertIsNotEnabled()
    rule.onNodeWithTag("settings_backup_import").assertIsNotEnabled()
    assertEquals(listOf(uri), fake.exports.toList())

    gate.complete(Unit)
    waitForText(context.getString(R.string.backup_export_done, counts()))
    waitGone("backup_progress")
    rule.onNodeWithTag("settings_backup_export").assertIsEnabled()
  }

  @Test
  fun cancellingThePickerDoesNothing() {
    registry.createResult = null
    registry.openResult = null

    click("settings_backup_export")
    click("settings_backup_import")
    rule.waitForIdle()

    assertTrue(fake.exports.isEmpty())
    assertTrue(fake.prepares.isEmpty())
    assertTrue(!exists("backup_progress"))
    assertTrue(!exists("backup_confirm"))
    rule.onNodeWithTag("settings_backup_export").assertIsEnabled()
    rule.onNodeWithTag("settings_backup_import").assertIsEnabled()
  }

  @Test
  fun importShowsTheDateVersionAndCountsBeforeReplacing() {
    registry.openResult = Uri.parse("content://test/pourline-backup.zip")

    click("settings_backup_import")

    waitFor("backup_confirm")
    assertEquals(
      listOf("application/zip", "application/x-zip-compressed", "application/octet-stream"),
      registry.openInput!!.toList(),
    )
    val date = formatBrewDateTime(java.time.OffsetDateTime.parse("2026-11-19T18:45:12-06:00").toInstant().toEpochMilli())
    rule.onNodeWithTag("backup_confirm").assertIsDisplayed()
    assertTrue(hasText(context.getString(R.string.backup_confirm_text, date, "1.0", counts())))
    assertTrue(hasText(context.getString(R.string.backup_confirm_title)))
    // Nada se reemplaza hasta confirmar.
    assertTrue(fake.applied.isEmpty())
  }

  @Test
  fun cancellingTheConfirmationDiscardsTheBackup() {
    registry.openResult = Uri.parse("content://test/pourline-backup.zip")
    click("settings_backup_import")
    waitFor("backup_confirm")

    rule.onNodeWithTag("ant_modal_cancel").performClick()

    waitGone("backup_confirm")
    assertEquals(1, fake.discarded.size)
    assertTrue(fake.applied.isEmpty())
    rule.onNodeWithTag("settings_backup_import").performScrollTo().assertIsEnabled()
  }

  @Test
  fun replacingShowsABlockingProgressAndThenTheResult() {
    registry.openResult = Uri.parse("content://test/pourline-backup.zip")
    fake.prepareResult = PrepareResult.Ready(FakeBackupOperations.prepared(missingPhotos = 1))
    fake.applyResult = ImportResult.Success(FakeBackupOperations.COUNTS, missingPhotos = 1)
    val gate = CompletableDeferred<Unit>().also { fake.applyGate = it }
    click("settings_backup_import")
    waitFor("backup_confirm")

    rule.onNodeWithTag("ant_modal_confirm").performClick()

    waitFor("backup_apply_progress")
    assertTrue(!exists("backup_confirm"))
    // "Atrás" no cierra el diálogo de progreso.
    Espresso.pressBack()
    rule.waitForIdle()
    rule.onNodeWithTag("backup_apply_progress").assertIsDisplayed()
    assertEquals(1, fake.applied.size)

    gate.complete(Unit)
    waitGone("backup_apply_progress")
    waitForText(context.getString(R.string.backup_import_done, counts()))
    waitForText(resources.getQuantityString(R.plurals.backup_import_missing_photos, 1, 1))
  }

  @Test
  fun eachErrorShowsItsMessage() {
    registry.createResult = Uri.parse("content://test/export.zip")
    registry.openResult = Uri.parse("content://test/import.zip")
    BackupError.entries.forEach { reason ->
      fake.exportResult = ExportResult.Failed(reason)
      click("settings_backup_export")
      waitForText(context.getString(BACKUP_ERROR_TEXT_EXPORT.getValue(reason)))
      rule.mainClock.advanceTimeBy(8_000)

      fake.prepareResult = PrepareResult.Invalid(reason)
      click("settings_backup_import")
      waitForText(context.getString(BACKUP_ERROR_TEXT_IMPORT.getValue(reason)))
      rule.mainClock.advanceTimeBy(8_000)
    }
  }

  @Test
  fun importIsDisabledWithItsExplanationDuringABrew() {
    sessionBusy.value = true

    rule.onNodeWithTag("settings_backup_import").performScrollTo().assertIsNotEnabled()
    rule.onNodeWithTag("settings_backup_export").assertIsEnabled()
    waitFor("settings_backup_import_blocked")
    assertTrue(hasText(context.getString(R.string.backup_import_blocked)))

    sessionBusy.value = false
    waitGone("settings_backup_import_blocked")
    rule.onNodeWithTag("settings_backup_import").assertIsEnabled()
  }

  @Test
  fun aBrewStartedBeforeReplacingCancelsTheImport() {
    registry.openResult = Uri.parse("content://test/pourline-backup.zip")
    click("settings_backup_import")
    waitFor("backup_confirm")

    // Una preparación empieza mientras el diálogo está abierto.
    busyAtConfirm = true
    rule.onNodeWithTag("ant_modal_confirm").performClick()

    waitGone("backup_confirm")
    waitForText(context.getString(R.string.backup_import_blocked))
    assertTrue(fake.applied.isEmpty())
    assertEquals(1, fake.discarded.size)
  }

  private companion object {
    const val PREFS = "settings_test_backup_card"

    val BACKUP_ERROR_TEXT_EXPORT = BackupError.entries.associateWith { backupErrorText(it, export = true) }
    val BACKUP_ERROR_TEXT_IMPORT = BackupError.entries.associateWith { backupErrorText(it, export = false) }
  }
}

/** Los selectores de documentos del sistema, sustituidos: apunta lo que se le pide y contesta con lo programado. */
internal class FakeDocumentRegistry : ActivityResultRegistry() {
  var createInput: String? = null
  var openInput: Array<String>? = null
  var createResult: Uri? = null
  var openResult: Uri? = null

  val owner = object : ActivityResultRegistryOwner {
    override val activityResultRegistry: ActivityResultRegistry = this@FakeDocumentRegistry
  }

  @Suppress("UNCHECKED_CAST")
  override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
    when (contract) {
      is ActivityResultContracts.CreateDocument -> {
        createInput = input as String
        dispatchResult(requestCode, createResult as O)
      }
      is ActivityResultContracts.OpenDocument -> {
        openInput = input as Array<String>
        dispatchResult(requestCode, openResult as O)
      }
      else -> error("Contrato no esperado: $contract")
    }
  }
}
