package dev.josueamaya.pourline.ui.settings

import android.net.Uri
import dev.josueamaya.pourline.backup.BackupCounts
import dev.josueamaya.pourline.backup.BackupOperations
import dev.josueamaya.pourline.backup.BackupRows
import dev.josueamaya.pourline.backup.BackupSummary
import dev.josueamaya.pourline.backup.ExportResult
import dev.josueamaya.pourline.backup.ImportResult
import dev.josueamaya.pourline.backup.PrepareResult
import dev.josueamaya.pourline.backup.PreparedImport
import dev.josueamaya.pourline.settings.SettingsSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.OffsetDateTime
import java.util.Collections

/**
 * Una [BackupOperations] programable para los tests de pantalla: resultados a elegir, compuertas ([CompletableDeferred])
 * para ver el progreso mientras la operación espera, las llamadas apuntadas y `applying`/`imported` controlables.
 */
class FakeBackupOperations : BackupOperations {
  @Volatile var exportResult: ExportResult = ExportResult.Success(COUNTS)
  @Volatile var prepareResult: PrepareResult = PrepareResult.Ready(prepared())
  @Volatile var applyResult: ImportResult = ImportResult.Success(COUNTS, 0)

  /** Con una compuerta puesta, la operación avisa de su progreso y espera a que el test la complete. */
  @Volatile var exportGate: CompletableDeferred<Unit>? = null
  @Volatile var prepareGate: CompletableDeferred<Unit>? = null
  @Volatile var applyGate: CompletableDeferred<Unit>? = null

  val exports: MutableList<Uri> = Collections.synchronizedList(mutableListOf())
  val prepares: MutableList<Uri> = Collections.synchronizedList(mutableListOf())
  val applied: MutableList<PreparedImport> = Collections.synchronizedList(mutableListOf())
  val discarded: MutableList<PreparedImport> = Collections.synchronizedList(mutableListOf())

  private val _applying = MutableStateFlow(false)
  override val applying: StateFlow<Boolean> = _applying

  private val _imported = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  override val imported: SharedFlow<Unit> = _imported

  /** Para los tests de navegación, que simulan una importación en curso sin pasar por [apply]. */
  fun setApplying(value: Boolean) {
    _applying.value = value
  }

  fun emitImported() {
    _imported.tryEmit(Unit)
  }

  override suspend fun export(target: Uri, onProgress: (Float) -> Unit): ExportResult {
    exports += target
    onProgress(0.4f)
    exportGate?.await()
    return exportResult
  }

  override suspend fun prepareImport(source: Uri, onProgress: (Float) -> Unit): PrepareResult {
    prepares += source
    onProgress(0.4f)
    prepareGate?.await()
    return prepareResult
  }

  override suspend fun apply(prepared: PreparedImport, onProgress: (Float) -> Unit): ImportResult {
    applied += prepared
    _applying.value = true
    try {
      onProgress(0.7f)
      applyGate?.await()
      return applyResult
    } finally {
      _applying.value = false
    }
  }

  override fun discard(prepared: PreparedImport) {
    discarded += prepared
  }

  companion object {
    val COUNTS = BackupCounts(coffees = 3, grinders = 2, grindCalibrations = 2, recipes = 4, brews = 12, photos = 5)

    /** Una copia preparada de mentira: el constructor es `internal` y los tests son un módulo amigo del principal. */
    fun prepared(
      counts: BackupCounts = COUNTS,
      createdAt: OffsetDateTime = OffsetDateTime.parse("2026-11-19T18:45:12-06:00"),
      appVersionName: String = "1.0",
      missingPhotos: Int = 0,
    ) = PreparedImport(BackupSummary(createdAt, appVersionName, counts), BackupRows(), SettingsSnapshot(), File("no-staging"), missingPhotos)
  }
}
