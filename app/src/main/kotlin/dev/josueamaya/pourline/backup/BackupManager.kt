package dev.josueamaya.pourline.backup

import android.net.Uri
import android.util.Log
import dev.josueamaya.pourline.coffee.photo.thumbNameOf
import dev.josueamaya.pourline.grinder.GrinderCatalogSeeder
import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.settings.BackupableSettings
import dev.josueamaya.pourline.settings.SettingsSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Exporta e importa todos los datos de la app (SPEC-011). Un `Mutex` serializa `export`, `prepareImport`, `discard` y
 * la recuperación, y todo corre en [io]. [workDir] (`filesDir/backup_import`) está en el mismo volumen que
 * [photosDir] para que renombrar un directorio sea atómico; allí van el staging de las fotos de la copia y, al
 * aplicarla, el marcador y el directorio de fotos anterior.
 */
class BackupManager internal constructor(
  private val db: HistoryDatabase,
  private val photosDir: File,
  private val workDir: File,
  private val settings: List<BackupableSettings>,
  private val seeder: GrinderCatalogSeeder,
  private val streams: BackupStreams,
  private val thumbnails: ThumbnailMaker,
  private val appVersion: AppVersion,
  private val scope: CoroutineScope,
  private val invalidateCaches: () -> Unit,
  private val clock: Clock = Clock.systemDefaultZone(),
  private val limits: BackupLimits = BackupLimits(),
  private val io: CoroutineDispatcher = Dispatchers.IO,
) : BackupOperations {
  private val mutex = Mutex()

  /** La copia preparada que sigue abierta: solo una a la vez, y la anterior se descarta al preparar otra. */
  @Volatile private var current: PreparedImport? = null

  private val staging get() = File(workDir, STAGING_DIR)
  private val oldPhotos get() = File(workDir, OLD_PHOTOS_DIR)

  private val _applying = MutableStateFlow(false)
  override val applying: StateFlow<Boolean> = _applying.asStateFlow()

  private val _imported = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  override val imported: SharedFlow<Unit> = _imported.asSharedFlow()

  override suspend fun export(target: Uri, onProgress: (Float) -> Unit): ExportResult = mutex.withLock {
    withContext(io) {
      try {
        ExportResult.Success(writeBackup(target, ThrottledProgress(onProgress)))
      } catch (e: CancellationException) {
        streams.deletePartial(target)
        throw e
      } catch (e: Throwable) {
        streams.deletePartial(target)
        ExportResult.Failed(failureReason(e) ?: throw e)
      }
    }
  }

  private suspend fun writeBackup(target: Uri, progress: ThrottledProgress): BackupCounts {
    val rows = readRowsForExport()
    val snapshot = settings.fold(SettingsSnapshot()) { snapshot, item -> item.exportInto(snapshot) }
    val photos = photoEntries(rows)
    val presentPhotos = photos.count { !it.name.endsWith(THUMB_SUFFIX) }
    val counts = rows.counts(presentPhotos)
    val manifest = ManifestDto(
      app = APP_ID,
      formatVersion = FORMAT_VERSION,
      schemaVersion = HistoryDatabase.VERSION,
      appVersionName = appVersion.name,
      appVersionCode = appVersion.code,
      createdAt = formatCreatedAt(OffsetDateTime.now(clock)),
      counts = CountsDto(counts.coffees, counts.grinders, counts.grindCalibrations, counts.recipes, counts.brews, counts.photos),
    )
    val total = (photos.sumOf { it.file.length() } + estimatedJsonBytes(rows)).coerceAtLeast(1)
    val context = currentCoroutineContext()
    progress.update(0f)
    streams.openOutput(target).buffered().use { out ->
      BackupWriter(limits).write(out, manifest, rows.toDto(), snapshot.toDto(), photos) { written ->
        context.ensureActive()
        progress.update((written.toFloat() / total).coerceAtMost(MAX_RUNNING_PROGRESS))
      }
    }
    progress.update(1f)
    return counts
  }

  /** Todas las filas, con los nombres de foto que no son nombres de archivo simples quitados para no escribir una copia que se rechazaría. */
  private suspend fun readRowsForExport(): BackupRows {
    val rows = db.backupDao().readAll()
    return rows.copy(
      coffees = rows.coffees.map { coffee ->
        coffee.copy(
          photo1 = coffee.photo1?.takeIf { MAIN_PHOTO_NAME.matches(it) },
          photo2 = coffee.photo2?.takeIf { MAIN_PHOTO_NAME.matches(it) },
        )
      },
    )
  }

  /** Las fotos referenciadas que existen, con su miniatura si existe. Una foto sin archivo no se incluye (R18). */
  private fun photoEntries(rows: BackupRows): List<PhotoEntry> =
    referencedPhotos(rows).sorted().flatMap { name ->
      val photo = File(photosDir, name)
      val thumb = File(photosDir, thumbNameOf(name))
      when {
        !photo.isFile -> emptyList()
        thumb.isFile -> listOf(PhotoEntry(name, photo), PhotoEntry(thumbNameOf(name), thumb))
        else -> listOf(PhotoEntry(name, photo))
      }
    }

  /** Una estimación del tamaño de `data.json` para el progreso: los BLOB en Base64 más unos cientos de bytes por fila. */
  private fun estimatedJsonBytes(rows: BackupRows): Long {
    val blobs = rows.brewCurves.sumOf { it.data.size.toLong() } + rows.brewTargets.sumOf { it.data.size.toLong() } +
      rows.brews.sumOf { it.preview.size.toLong() }
    val rowCount = rows.brews.size + rows.coffees.size + rows.recipes.size + rows.recipeStages.size + rows.grinders.size
    return blobs * 4 / 3 + rowCount * ROW_BYTES
  }

  override suspend fun prepareImport(source: Uri, onProgress: (Float) -> Unit): PrepareResult = mutex.withLock {
    withContext(io) {
      current?.state?.compareAndSet(PreparedImport.State.OPEN, PreparedImport.State.DISCARDED)
      current = null
      staging.deleteRecursively()
      try {
        PrepareResult.Ready(readBackup(source, ThrottledProgress(onProgress)).also { current = it })
      } catch (e: CancellationException) {
        staging.deleteRecursively()
        throw e
      } catch (e: Throwable) {
        staging.deleteRecursively()
        PrepareResult.Invalid(failureReason(e) ?: throw e)
      }
    }
  }

  private suspend fun readBackup(source: Uri, progress: ThrottledProgress): PreparedImport {
    val context = currentCoroutineContext()
    val size = streams.size(source)?.takeIf { it > 0 }
    progress.update(0f)
    val archive = streams.openInput(source).use { input ->
      BackupReader(limits).read(input, staging) { read ->
        context.ensureActive()
        if (size != null) progress.update((read.toFloat() / size).coerceAtMost(MAX_RUNNING_PROGRESS))
      }
    }
    // Una copia con formato 1 trae las recetas de fábrica como filas de fábrica: aquí pasan a recetas propias (SPEC-012).
    val rows = archive.data.toRows().let { if (archive.manifest.formatVersion == 1) it.withLegacyBuiltInsConverted() else it }
    validate(rows)?.let { throw BackupFailure(it) }
    val createdAt = try {
      OffsetDateTime.parse(archive.manifest.createdAt)
    } catch (e: DateTimeParseException) {
      throw BackupFailure(BackupError.CORRUPT, e)
    }
    val plan = planPhotos(rows, archive.staged)
    applyPhotoPlan(plan, staging, thumbnails)
    context.ensureActive()
    progress.update(1f)
    val summary = BackupSummary(createdAt, archive.manifest.appVersionName, plan.rows.counts(plan.present.size))
    return PreparedImport(summary, plan.rows, archive.settings.toSnapshot(), staging, plan.missing)
  }

  /**
   * El orden de SPEC-011 §3.5, con el marcador de D17: 1) marcador `APPLYING`; 2) la transacción de Room; 3) marcador
   * `DB_DONE`; 4) [finishImport]. Hasta el paso 2 inclusive un fallo es `Failed` y nada cambia; después, nunca.
   */
  override suspend fun apply(prepared: PreparedImport, onProgress: (Float) -> Unit): ImportResult = mutex.withLock {
    withContext(io + NonCancellable) {
      if (!prepared.state.compareAndSet(PreparedImport.State.OPEN, PreparedImport.State.APPLIED)) {
        return@withContext ImportResult.Failed(BackupError.IO)
      }
      _applying.value = true
      try {
        val progress = ThrottledProgress(onProgress)
        progress.update(0.1f)
        val marker = ImportMarker(
          state = ImportMarker.State.APPLYING,
          // Quien importa desde Ajustes ya completó el onboarding.
          settings = prepared.settings.copy(onboardingDone = true).toDto(),
          fingerprint = prepared.rows.fingerprint(),
        )
        try {
          check(prepared.staging.isDirectory) { "La copia preparada ya no está" }
          ImportMarker.write(workDir, marker)
          db.backupDao().replaceAll(prepared.rows)
        } catch (e: Throwable) {
          Log.e(TAG, "Import failed before the data changed", e)
          abandon(prepared)
          return@withContext ImportResult.Failed(if (e is OutOfMemoryError) BackupError.TOO_LARGE else BackupError.IO)
        }
        progress.update(0.7f)
        // Desde aquí los datos ya cambiaron: no se informa un fallo; el siguiente arranque termina lo que falte.
        val done = marker.copy(state = ImportMarker.State.DB_DONE)
        try {
          ImportMarker.write(workDir, done)
          finishImport(done, notify = true) { progress.update(it) }
        } catch (e: Throwable) {
          Log.e(TAG, "Import finished after the data changed; the next start completes the rest", e)
        }
        ImportResult.Success(prepared.summary.counts, prepared.missingPhotos)
      } finally {
        _applying.value = false
      }
    }
  }

  /** Borra todo lo que dejó una importación que no llegó a cambiar los datos. */
  private fun abandon(prepared: PreparedImport) {
    ImportMarker.delete(workDir)
    prepared.staging.deleteRecursively()
    if (current === prepared) current = null
  }

  /**
   * Los pasos 2 a 4 de §3.5, repetibles desde cualquier estado intermedio: cambia las fotos, restaura los ajustes a través
   * de sus clases, sincroniza el catálogo de molinos y borra el marcador y el directorio de trabajo. Con [notify], además
   * descarta lo que Room no invalida (deshacer pendientes, "Moler con", caché de imágenes) y emite [imported]; la
   * recuperación al arrancar no lo necesita, porque aún no hay nada en memoria.
   */
  private suspend fun finishImport(marker: ImportMarker, notify: Boolean, onProgress: (Float) -> Unit = {}) {
    swapPhotos(photosDir, staging, oldPhotos)
    onProgress(0.8f)
    val snapshot = marker.settings.toSnapshot()
    settings.forEach { it.restoreFrom(snapshot) }
    onProgress(0.85f)
    Log.i(TAG, "Catalog sync after import: ${seeder.sync()}")
    onProgress(1f)
    if (notify) invalidateCaches()
    ImportMarker.delete(workDir)
    workDir.deleteRecursively()
    current = null
    if (notify) _imported.tryEmit(Unit)
  }

  /**
   * Se llama al arrancar, antes del barrido de fotos huérfanas y de la sincronización del catálogo (SPEC-011 §3.5). La
   * base ya importada es la fuente de verdad: con el marcador en `DB_DONE`, o en `APPLYING` con la huella de la base
   * igual a la de la copia, se terminan los pasos pendientes; con otra huella, la transacción no llegó a confirmarse y se
   * borra todo. Sin marcador, se borra el directorio de trabajo huérfano.
   */
  suspend fun recoverInterruptedImport() {
    mutex.withLock {
      withContext(io + NonCancellable) {
        if (!workDir.exists()) return@withContext
        try {
          val marker = ImportMarker.read(workDir)
          val committed = when (marker?.state) {
            null -> null
            ImportMarker.State.DB_DONE -> marker
            ImportMarker.State.APPLYING -> marker.takeIf { currentFingerprint() == it.fingerprint }
          }
          if (committed != null) {
            Log.w(TAG, "Finishing an interrupted import (${committed.state})")
            finishImport(committed.copy(state = ImportMarker.State.DB_DONE), notify = false)
          } else {
            // La transacción no se confirmó, o no hay marcador: lo que haya es un staging sin dueño. Si una sustitución de
            // fotos quedó a medias, no se pierden las fotos de siempre.
            if (!photosDir.exists() && oldPhotos.exists()) oldPhotos.renameTo(photosDir)
            workDir.deleteRecursively()
          }
        } catch (e: Exception) {
          Log.e(TAG, "Could not recover an interrupted import; it will be retried at the next start", e)
        }
      }
    }
  }

  private suspend fun currentFingerprint(): BackupFingerprint =
    db.backupDao().fingerprintRow().fingerprint(db.coffeeDao().photoNames())

  override fun discard(prepared: PreparedImport) {
    if (!prepared.state.compareAndSet(PreparedImport.State.OPEN, PreparedImport.State.DISCARDED)) return
    scope.launch(io) {
      mutex.withLock {
        // Si ya se preparó otra copia, el staging es suyo.
        if (current === prepared || current == null) {
          current = null
          prepared.staging.deleteRecursively()
        }
      }
    }
  }

  /** El [BackupError] de [e], o `null` si no es un fallo de la copia y se debe propagar. */
  private fun failureReason(e: Throwable): BackupError? = when (e) {
    is BackupFailure -> e.reason
    is OutOfMemoryError -> BackupError.TOO_LARGE
    is IOException, is SecurityException, is IllegalStateException, is android.database.SQLException -> BackupError.IO
    else -> null
  }

  /** Avisa solo cuando el porcentaje sube: la interfaz no se recompone en cada bloque. */
  private class ThrottledProgress(private val report: (Float) -> Unit) {
    private var last = -1

    fun update(fraction: Float) {
      val percent = (fraction.coerceIn(0f, 1f) * 100).toInt()
      if (percent > last) {
        last = percent
        report(percent / 100f)
      }
    }
  }

  internal companion object {
    const val TAG = "BackupManager"
    const val STAGING_DIR = "staging"
    const val OLD_PHOTOS_DIR = "old_photos"
    const val THUMB_SUFFIX = "_thumb.jpg"
    private const val MAX_RUNNING_PROGRESS = 0.99f
    private const val ROW_BYTES = 600L
  }
}
