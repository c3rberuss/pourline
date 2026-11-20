package dev.josueamaya.pourline.ui.settings

import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.backup.BackupError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** El mensaje de cada fallo de la copia (SPEC-011 §3.7 y D30). */
class BackupTextsTest {
  @Test
  fun `each backup error has its message`() {
    BackupError.entries.forEach { reason ->
      listOf(true, false).forEach { export ->
        assertNotEquals("$reason (export=$export)", 0, backupErrorText(reason, export))
      }
    }
    // Una copia dañada y una con referencias rotas dicen lo mismo.
    assertEquals(backupErrorText(BackupError.CORRUPT, export = false), backupErrorText(BackupError.BROKEN_REFERENCES, export = false))
    // "Demasiado grande" cambia entre exportar e importar.
    assertNotEquals(backupErrorText(BackupError.TOO_LARGE, export = true), backupErrorText(BackupError.TOO_LARGE, export = false))
    assertEquals(R.string.backup_error_export_too_large, backupErrorText(BackupError.TOO_LARGE, export = true))
    assertEquals(R.string.backup_error_too_large, backupErrorText(BackupError.TOO_LARGE, export = false))
    // El resto no depende del sentido.
    listOf(BackupError.NOT_A_BACKUP, BackupError.NEWER_FORMAT, BackupError.CORRUPT, BackupError.IO).forEach { reason ->
      assertEquals(backupErrorText(reason, export = true), backupErrorText(reason, export = false))
    }
  }
}
