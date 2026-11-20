package dev.josueamaya.pourline.ui.settings

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.backup.BackupCounts
import dev.josueamaya.pourline.backup.BackupError

/**
 * El mensaje de cada fallo de la copia (SPEC-011 §3.7). Una copia dañada y una con referencias rotas dicen lo mismo, y
 * "demasiado grande" cambia según se exporte o se importe.
 */
@StringRes
fun backupErrorText(reason: BackupError, export: Boolean): Int = when (reason) {
  BackupError.NOT_A_BACKUP -> R.string.backup_error_not_a_backup
  BackupError.NEWER_FORMAT -> R.string.backup_error_newer_format
  BackupError.CORRUPT, BackupError.BROKEN_REFERENCES -> R.string.backup_error_corrupt
  BackupError.TOO_LARGE -> if (export) R.string.backup_error_export_too_large else R.string.backup_error_too_large
  BackupError.IO -> R.string.backup_error_io
}

/** "3 cafés, 2 molinos, 4 recetas, 12 preparaciones, 3 fotos": las calibraciones no salen en el texto. */
fun Resources.backupCountsText(counts: BackupCounts): String = getString(
  R.string.backup_counts,
  getQuantityString(R.plurals.backup_count_coffees, counts.coffees, counts.coffees),
  getQuantityString(R.plurals.backup_count_grinders, counts.grinders, counts.grinders),
  getQuantityString(R.plurals.backup_count_recipes, counts.recipes, counts.recipes),
  getQuantityString(R.plurals.backup_count_brews, counts.brews, counts.brews),
  getQuantityString(R.plurals.backup_count_photos, counts.photos, counts.photos),
)

@Composable
fun backupCountsText(counts: BackupCounts): String = LocalResources.current.backupCountsText(counts)
