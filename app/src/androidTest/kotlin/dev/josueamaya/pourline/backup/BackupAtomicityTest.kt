package dev.josueamaya.pourline.backup

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Un fallo dentro de la transacción de importar deja datos, fotos y ajustes como estaban (SPEC-011 criterio 6). */
class BackupAtomicityTest {
  private lateinit var h: BackupHarness

  @Before
  fun setUp() {
    h = BackupHarness("settings_test_backup_atomicity")
  }

  @After
  fun tearDown() = h.close()

  @Test
  fun aFailureInsideTheTransactionLeavesDataPhotosAndSettingsAsBefore() = runBlocking {
    seedEverything(h.context, h.db, h.photosDir, h.settings)
    h.exportTo("backup.zip")
    // El estado actual, distinto de la copia.
    h.wipe()
    seedSomethingElse(h.db)
    h.photosDir.mkdirs()
    File(h.photosDir, "previa.jpg").writeBytes(byteArrayOf(1, 2, 3))
    h.scrambleSettings()
    val rowsBefore = h.db.backupDao().readAll()
    val photosBefore = readFiles(h.photosDir)
    val settingsBefore = h.settings.snapshot()
    val tourBefore = h.settings.tour.seen.value

    val manager = h.manager()
    val prepared = (manager.prepareImport(h.uri("backup.zip"), {}) as PrepareResult.Ready).import
    assertTrue(File(h.workDir, "staging").isDirectory)
    // La última tabla falla dentro de la transacción: el punto más tardío posible.
    h.db.openHelper.writableDatabase.execSQL(
      "CREATE TRIGGER backup_test_fail BEFORE INSERT ON brew_targets BEGIN SELECT RAISE(ABORT, 'test'); END",
    )

    val result = manager.apply(prepared) {}

    assertEquals(ImportResult.Failed(BackupError.IO), result)
    assertRowsEqual(rowsBefore, h.db.backupDao().readAll())
    assertFilesEqual(photosBefore, readFiles(h.photosDir))
    assertEquals(settingsBefore, h.settings.snapshot())
    assertEquals(tourBefore, h.settings.tour.seen.value)
    // Sin staging ni marcador: no queda nada por recuperar.
    assertFalse(File(h.workDir, "staging").exists())
    assertFalse(File(h.workDir, ImportMarker.FILE_NAME).exists())
    assertFalse(manager.applying.value)
    assertEquals(0, h.invalidations)

    // La base sigue usable y la misma copia, vuelta a preparar sin el disparador, se aplica.
    h.db.openHelper.writableDatabase.execSQL("DROP TRIGGER backup_test_fail")
    val again = (manager.prepareImport(h.uri("backup.zip"), {}) as PrepareResult.Ready).import
    assertTrue(manager.apply(again) {} is ImportResult.Success)
  }
}
