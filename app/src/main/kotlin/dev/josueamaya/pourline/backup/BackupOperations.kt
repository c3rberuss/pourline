package dev.josueamaya.pourline.backup

import android.net.Uri

/**
 * Lo que la interfaz usa de la copia de seguridad (SPEC-011 §3.3). Lo implementa [BackupManager]; los tests de
 * pantalla usan uno falso.
 */
interface BackupOperations {
  /** Escribe todos los datos y los ajustes en [target]. [onProgress] va de 0 a 1. */
  suspend fun export(target: Uri, onProgress: (Float) -> Unit): ExportResult

  /** Lee y valida la copia de [source] sin modificar nada; las fotos quedan en un directorio de staging. */
  suspend fun prepareImport(source: Uri, onProgress: (Float) -> Unit): PrepareResult

  /** Borra el staging de una copia preparada que el usuario no confirmó. */
  fun discard(prepared: PreparedImport)
}
