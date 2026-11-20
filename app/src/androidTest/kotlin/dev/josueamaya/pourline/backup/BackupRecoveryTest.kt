package dev.josueamaya.pourline.backup

import dev.josueamaya.pourline.grinder.TestGrinderCatalog
import dev.josueamaya.pourline.settings.SettingsSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Recuperar al arrancar una importación que murió a medias (SPEC-011 §3.5, D19). */
class BackupRecoveryTest {
  private lateinit var h: BackupHarness
  private lateinit var seeded: Seeded
  private lateinit var rowsOfBackup: BackupRows
  private lateinit var photosOfBackup: Map<String, ByteArray>
  private lateinit var settingsOfBackup: SettingsSnapshot

  @Before
  fun setUp() {
    h = BackupHarness("settings_test_backup_recovery")
    runBlocking {
      seeded = seedEverything(h.context, h.db, h.photosDir, h.settings)
      rowsOfBackup = h.db.backupDao().readAll()
      photosOfBackup = readFiles(h.photosDir)
      settingsOfBackup = h.settings.snapshot()
      h.exportTo("backup.zip")
    }
  }

  @After
  fun tearDown() = h.close()

  /** El estado de la app antes de importar: otros datos, otra foto y otros ajustes. */
  private fun makeCurrentStateDifferent() = runBlocking {
    h.wipe()
    seedSomethingElse(h.db)
    h.photosDir.mkdirs()
    File(h.photosDir, "previa.jpg").writeBytes(byteArrayOf(9, 9))
    h.scrambleSettings()
  }

  private fun prepare(manager: BackupManager = h.manager()): PreparedImport =
    (runBlocking { manager.prepareImport(h.uri("backup.zip"), {}) } as PrepareResult.Ready).import

  private fun marker(state: ImportMarker.State, prepared: PreparedImport) =
    ImportMarker.write(h.workDir, ImportMarker(state, prepared.settings.copy(onboardingDone = true).toDto(), prepared.rows.fingerprint()))

  /** Lo que haría `PourlineApp` en el siguiente arranque: un gestor nuevo sobre los mismos archivos. */
  private fun restart() = runBlocking { h.manager().recoverInterruptedImport() }

  @Test
  fun withoutMarkerALeftoverStagingIsRemoved() = runBlocking {
    makeCurrentStateDifferent()
    prepare()
    assertTrue(File(h.workDir, "staging").isDirectory)
    val rows = h.db.backupDao().readAll()
    val photos = readFiles(h.photosDir)
    val settings = h.settings.snapshot()

    restart()

    assertFalse(h.workDir.exists())
    assertRowsEqual(rows, h.db.backupDao().readAll())
    assertFilesEqual(photos, readFiles(h.photosDir))
    assertEquals(settings, h.settings.snapshot())
  }

  @Test
  fun withoutMarkerAndWithoutPhotosDirTheOldPhotosComeBack() = runBlocking {
    makeCurrentStateDifferent()
    prepare()
    // Una sustitución de fotos que murió entre los dos renombrados, y sin marcador.
    File(h.workDir, OLD_PHOTOS).let { h.photosDir.renameTo(it) }
    assertFalse(h.photosDir.exists())

    restart()

    assertFalse(h.workDir.exists())
    assertEquals(setOf("previa.jpg"), readFiles(h.photosDir).keys)
  }

  @Test
  fun anApplyingMarkerWhoseDataDidNotCommitIsRolledBack() = runBlocking {
    makeCurrentStateDifferent()
    val prepared = prepare()
    marker(ImportMarker.State.APPLYING, prepared)
    val rows = h.db.backupDao().readAll()
    val photos = readFiles(h.photosDir)
    val settings = h.settings.snapshot()

    restart()

    // La transacción no se confirmó: todo sigue como antes y no queda nada pendiente.
    assertFalse(h.workDir.exists())
    assertRowsEqual(rows, h.db.backupDao().readAll())
    assertFilesEqual(photos, readFiles(h.photosDir))
    assertEquals(settings, h.settings.snapshot())
  }

  @Test
  fun anApplyingMarkerWhoseDataCommittedIsFinished() = runBlocking {
    makeCurrentStateDifferent()
    val prepared = prepare()
    marker(ImportMarker.State.APPLYING, prepared)
    // La transacción llegó a confirmarse; el proceso murió antes de escribir DB_DONE.
    h.db.backupDao().replaceAll(prepared.rows)
    val tour = h.settings.tour.seen.value
    h.catalogJson = TestGrinderCatalog.catalog(3, listOf(TestGrinderCatalog.uno()))

    restart()

    assertFalse(h.workDir.exists())
    // La sincronización del catálogo cambia los molinos de catálogo; el resto de las tablas es el de la copia.
    val after = h.db.backupDao().readAll()
    assertRowsEqual(
      rowsOfBackup.copy(
        grinders = after.grinders,
        grinderUnits = after.grinderUnits,
        grinderMethodRanges = after.grinderMethodRanges,
        grinderAnchors = after.grinderAnchors,
      ),
      after,
    )
    assertEquals(rowsOfBackup.grinders.filter { it.owned }, after.grinders.filter { it.owned })
    assertFilesEqual(photosOfBackup, readFiles(h.photosDir))
    // Ajustes restaurados, con el catálogo ya sincronizado (la versión de la copia era 2).
    assertEquals(settingsOfBackup.copy(onboardingDone = true, grinderCatalogVersion = 3), h.settings.snapshot())
    assertEquals(tour, h.settings.tour.seen.value)
    assertTrue(h.db.backupDao().readAll().grinders.any { it.catalogKey == TestGrinderCatalog.KEY_UNO })
    // Recuperar no avisa a la interfaz: aún no hay nada en memoria.
    assertEquals(0, h.invalidations)
  }

  @Test
  fun aDbDoneMarkerFinishesFromEachIntermediatePhotoState() {
    val states = mapOf<String, (File, File, File) -> Unit>(
      // El staging y las fotos de siempre, sin directorio anterior.
      "staging and photos" to { _, _, _ -> },
      // `coffee_photos` ya pasó a `old_photos`, pero el staging aún no se movió.
      "staging and old" to { photos, _, old -> photos.renameTo(old) },
      // Los tres a la vez.
      "all three" to { photos, _, old -> old.mkdirs().also { File(old, "otra.jpg").writeBytes(byteArrayOf(1)) }; File(photos, "x.jpg").writeBytes(byteArrayOf(2)) },
      // El staging ya es `coffee_photos`; falta borrar el anterior.
      "only old pending" to { photos, staging, old ->
        photos.renameTo(old)
        staging.renameTo(photos)
      },
      // Todo hecho salvo borrar el marcador.
      "only marker pending" to { photos, staging, _ ->
        photos.deleteRecursively()
        staging.renameTo(photos)
      },
    )
    states.forEach { (label, arrange) ->
      runBlocking {
        h.photosDir.deleteRecursively()
        h.workDir.deleteRecursively()
        makeCurrentStateDifferent()
        val prepared = prepare()
        h.db.backupDao().replaceAll(prepared.rows)
        marker(ImportMarker.State.DB_DONE, prepared)
        arrange(h.photosDir, File(h.workDir, "staging"), File(h.workDir, OLD_PHOTOS))

        restart()

        assertFalse(label, h.workDir.exists())
        assertFilesEqual(photosOfBackup, readFiles(h.photosDir))
        assertRowsEqual(rowsOfBackup, h.db.backupDao().readAll())
        assertEquals(label, settingsOfBackup.copy(onboardingDone = true), h.settings.snapshot().copy(grinderCatalogVersion = settingsOfBackup.grinderCatalogVersion))
      }
    }
  }

  @Test
  fun withoutWorkDirNothingIsTouched() = runBlocking {
    makeCurrentStateDifferent()
    val rows = h.db.backupDao().readAll()
    val photos = readFiles(h.photosDir)
    val settings = h.settings.snapshot()
    assertFalse(h.workDir.exists())

    restart()

    assertFalse(h.workDir.exists())
    assertRowsEqual(rows, h.db.backupDao().readAll())
    assertFilesEqual(photos, readFiles(h.photosDir))
    assertEquals(settings, h.settings.snapshot())
  }

  private companion object {
    const val OLD_PHOTOS = BackupManager.OLD_PHOTOS_DIR
  }
}
