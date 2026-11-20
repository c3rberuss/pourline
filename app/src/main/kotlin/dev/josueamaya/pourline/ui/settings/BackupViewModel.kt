package dev.josueamaya.pourline.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josueamaya.pourline.backup.BackupCounts
import dev.josueamaya.pourline.backup.BackupError
import dev.josueamaya.pourline.backup.BackupOperations
import dev.josueamaya.pourline.backup.BackupSummary
import dev.josueamaya.pourline.backup.ExportResult
import dev.josueamaya.pourline.backup.ImportResult
import dev.josueamaya.pourline.backup.PrepareResult
import dev.josueamaya.pourline.backup.PreparedImport
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Lo que la tarjeta está haciendo: exportar, leer la copia elegida o reemplazar los datos con ella. */
enum class BackupTask { EXPORT, READ, APPLY }

/**
 * [progress] va de 0 a 1 y es `null` si no se conoce (indicador indeterminado). [confirm] es la copia leída que espera
 * la confirmación del usuario.
 */
data class BackupUiState(
  val task: BackupTask? = null,
  val progress: Float? = null,
  val importBlocked: Boolean = false,
  val confirm: BackupSummary? = null,
)

/** Los mensajes que la tarjeta muestra una sola vez. */
sealed interface BackupMessage {
  data class Exported(val counts: BackupCounts) : BackupMessage

  data class Imported(val counts: BackupCounts, val missingPhotos: Int) : BackupMessage

  data class ExportFailed(val reason: BackupError) : BackupMessage

  data class ImportFailed(val reason: BackupError) : BackupMessage

  /** Una preparación empezó mientras el diálogo de confirmación estaba abierto. */
  data object ImportBlocked : BackupMessage
}

/**
 * Los dos flujos de la tarjeta "Copia de seguridad" (SPEC-011 §2). [sessionBusy] dice si hay una preparación en curso (no se
 * puede importar) e [isSessionBusy] lo vuelve a preguntar al confirmar. Exportar y aplicar terminan aunque se salga de
 * Ajustes; leer una copia sin confirmar se cancela y su staging se borra.
 */
class BackupViewModel(
  private val backup: BackupOperations,
  sessionBusy: Flow<Boolean>,
  private val isSessionBusy: () -> Boolean,
  private val now: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {
  private data class Local(val task: BackupTask? = null, val progress: Float? = null, val confirm: BackupSummary? = null)

  private val local = MutableStateFlow(Local())
  private val messageChannel = Channel<BackupMessage>(Channel.BUFFERED)

  /** La copia leída que espera confirmación; solo una a la vez. */
  private var prepared: PreparedImport? = null

  val ui: StateFlow<BackupUiState> = combine(local, sessionBusy, backup.applying) { local, busy, applying ->
    BackupUiState(
      // `applying` es global: una pantalla recreada durante la importación sigue viendo el diálogo de progreso.
      task = local.task ?: if (applying) BackupTask.APPLY else null,
      progress = local.progress,
      importBlocked = busy,
      confirm = local.confirm,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BackupUiState(importBlocked = isSessionBusy()))

  val messages: Flow<BackupMessage> = messageChannel.receiveAsFlow()

  /** `pourline-backup-2026-11-19-1845.zip`. */
  fun suggestedFileName(): String = "pourline-backup-${now().format(FILE_STAMP)}.zip"

  /** El destino elegido en el selector; `null` es que se canceló y no pasa nada. */
  fun onExportTarget(target: Uri?) {
    if (target == null || local.value.task != null) return
    local.value = Local(task = BackupTask.EXPORT, progress = 0f)
    viewModelScope.launch {
      val result = withContext(NonCancellable) { backup.export(target, ::report) }
      local.value = Local()
      messageChannel.send(
        when (result) {
          is ExportResult.Success -> BackupMessage.Exported(result.counts)
          is ExportResult.Failed -> BackupMessage.ExportFailed(result.reason)
        },
      )
    }
  }

  /** La copia elegida en el selector; `null` es que se canceló y no pasa nada. */
  fun onImportSource(source: Uri?) {
    if (source == null || local.value.task != null) return
    local.value = Local(task = BackupTask.READ, progress = null)
    viewModelScope.launch {
      try {
        when (val result = backup.prepareImport(source, ::report)) {
          is PrepareResult.Ready -> {
            prepared = result.import
            local.value = Local(confirm = result.import.summary)
          }
          is PrepareResult.Invalid -> {
            local.value = Local()
            messageChannel.send(BackupMessage.ImportFailed(result.reason))
          }
        }
      } finally {
        // Una lectura cancelada (se salió de Ajustes) no deja la tarjeta ocupada ni un staging sin dueño.
        if (local.value.task == BackupTask.READ) local.value = Local()
      }
    }
  }

  /** El usuario confirmó reemplazar. Si entretanto empezó una preparación, se descarta la copia y se avisa. */
  fun confirmReplace() {
    val import = prepared ?: return
    prepared = null
    if (isSessionBusy()) {
      backup.discard(import)
      local.value = Local()
      messageChannel.trySend(BackupMessage.ImportBlocked)
      return
    }
    local.value = Local(task = BackupTask.APPLY, progress = 0f)
    viewModelScope.launch {
      val result = withContext(NonCancellable) { backup.apply(import, ::report) }
      local.value = Local()
      messageChannel.send(
        when (result) {
          is ImportResult.Success -> BackupMessage.Imported(result.counts, result.missingPhotos)
          is ImportResult.Failed -> BackupMessage.ImportFailed(result.reason)
        },
      )
    }
  }

  /** El usuario no confirmó: se borra lo que se preparó. */
  fun cancelReplace() {
    prepared?.let(backup::discard)
    prepared = null
    local.value = Local()
  }

  override fun onCleared() {
    prepared?.let(backup::discard)
    prepared = null
  }

  private fun report(progress: Float) {
    local.update { if (it.task == null) it else it.copy(progress = progress) }
  }

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
    val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
  }
}
