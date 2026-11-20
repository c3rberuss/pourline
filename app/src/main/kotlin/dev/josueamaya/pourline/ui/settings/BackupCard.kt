package dev.josueamaya.pourline.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.backup.BackupSummary
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntCard
import dev.josueamaya.pourline.ui.components.AntModal
import dev.josueamaya.pourline.ui.components.AntProgress
import dev.josueamaya.pourline.ui.components.AntSpin
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.components.ReportModalPresence
import dev.josueamaya.pourline.ui.components.showAntMessage
import dev.josueamaya.pourline.ui.history.formatBrewDateTime
import dev.josueamaya.pourline.ui.theme.AntTheme

/** Los tipos que se ofrecen al elegir la copia: algunos proveedores devuelven un ZIP con otro tipo (P3). */
private val IMPORT_MIME_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

/**
 * La tarjeta "Copia de seguridad" de Ajustes (SPEC-011 §2): exportar a un archivo e importar desde uno, con la
 * confirmación antes de reemplazar y el progreso. Los mensajes salen en [snackbar].
 */
@Composable
fun BackupCard(viewModel: BackupViewModel, snackbar: SnackbarHostState) {
  val state by viewModel.ui.collectAsStateWithLifecycle()
  val resources = LocalResources.current
  val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
    viewModel.onExportTarget(uri)
  }
  val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    viewModel.onImportSource(uri)
  }

  LaunchedEffect(viewModel) {
    viewModel.messages.collect { message ->
      when (message) {
        is BackupMessage.Exported ->
          snackbar.showAntMessage(resources.getString(R.string.backup_export_done, resources.backupCountsText(message.counts)), AntStatus.Success)
        is BackupMessage.Imported -> {
          snackbar.showAntMessage(resources.getString(R.string.backup_import_done, resources.backupCountsText(message.counts)), AntStatus.Success)
          if (message.missingPhotos > 0) {
            snackbar.showAntMessage(
              resources.getQuantityString(R.plurals.backup_import_missing_photos, message.missingPhotos, message.missingPhotos),
              AntStatus.Warning,
            )
          }
        }
        is BackupMessage.ExportFailed ->
          snackbar.showAntMessage(resources.getString(backupErrorText(message.reason, export = true)), AntStatus.Error)
        is BackupMessage.ImportFailed ->
          snackbar.showAntMessage(resources.getString(backupErrorText(message.reason, export = false)), AntStatus.Error)
        BackupMessage.ImportBlocked ->
          snackbar.showAntMessage(resources.getString(R.string.backup_import_blocked), AntStatus.Warning)
      }
    }
  }

  val idle = state.task == null
  AntCard(Modifier.fillMaxWidth(), title = stringResource(R.string.backup_title)) {
    AntText(stringResource(R.string.backup_hint), type = AntTextType.Secondary)
    AntButton(
      text = stringResource(R.string.backup_export),
      onClick = { exportPicker.launch(viewModel.suggestedFileName()) },
      modifier = Modifier.fillMaxWidth().testTag("settings_backup_export"),
      enabled = idle,
    )
    AntButton(
      text = stringResource(R.string.backup_import),
      onClick = { importPicker.launch(IMPORT_MIME_TYPES) },
      modifier = Modifier.fillMaxWidth().testTag("settings_backup_import"),
      enabled = idle && !state.importBlocked,
    )
    if (state.importBlocked) {
      AntText(
        stringResource(R.string.backup_import_blocked),
        Modifier.testTag("settings_backup_import_blocked"),
        type = AntTextType.Secondary,
      )
    }
    if (state.task == BackupTask.EXPORT || state.task == BackupTask.READ) {
      val label = if (state.task == BackupTask.EXPORT) R.string.backup_exporting else R.string.backup_reading
      Column(Modifier.fillMaxWidth().testTag("backup_progress"), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
        AntText(stringResource(label), type = AntTextType.Secondary)
        ProgressBar(state.progress)
      }
    }
  }

  state.confirm?.let { summary ->
    ConfirmReplaceDialog(summary, onConfirm = viewModel::confirmReplace, onDismiss = viewModel::cancelReplace)
  }
  if (state.task == BackupTask.APPLY) ApplyingDialog(state.progress)
}

/** La barra de progreso, o el indicador giratorio si no se conoce el avance. */
@Composable
private fun ProgressBar(progress: Float?) {
  if (progress == null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { AntSpin() }
  } else {
    AntProgress(percent = progress * 100f, modifier = Modifier.fillMaxWidth())
  }
}

@Composable
private fun ConfirmReplaceDialog(summary: BackupSummary, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  val resources = LocalResources.current
  AntModal(
    title = resources.getString(R.string.backup_confirm_title),
    text = resources.getString(
      R.string.backup_confirm_text,
      formatBrewDateTime(summary.createdAt.toInstant().toEpochMilli()),
      summary.appVersionName,
      resources.backupCountsText(summary.counts),
    ),
    confirmText = resources.getString(R.string.backup_confirm_replace),
    cancelText = resources.getString(R.string.backup_confirm_cancel),
    onConfirm = onConfirm,
    onDismiss = onDismiss,
    danger = true,
    testTag = "backup_confirm",
  )
}

/** Mientras se reemplazan los datos no se puede cerrar ni salir de Ajustes: el diálogo se traga "Atrás" y los toques de fuera. */
@Composable
private fun ApplyingDialog(progress: Float?) {
  ReportModalPresence()
  Dialog(
    onDismissRequest = {},
    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
  ) {
    AntCard(Modifier.testTag("backup_apply_progress")) {
      Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
        AntText(stringResource(R.string.backup_applying))
        ProgressBar(progress)
      }
    }
  }
}
