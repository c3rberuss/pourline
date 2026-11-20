package dev.josueamaya.pourline.backup

import android.net.Uri
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lo que la interfaz usa de la copia de seguridad (SPEC-011 §3.3). Lo implementa [BackupManager]; los tests de
 * pantalla usan uno falso.
 */
interface BackupOperations {
  /** Escribe todos los datos y los ajustes en [target]. [onProgress] va de 0 a 1. */
  suspend fun export(target: Uri, onProgress: (Float) -> Unit): ExportResult

  /** Lee y valida la copia de [source] sin modificar nada; las fotos quedan en un directorio de staging. */
  suspend fun prepareImport(source: Uri, onProgress: (Float) -> Unit): PrepareResult

  /**
   * Reemplaza todos los datos y los ajustes por los de [prepared] (SPEC-011 §3.5). Una vez confirmada la transacción
   * de Room, nunca devuelve `Failed`: los datos ya cambiaron y el siguiente arranque termina lo pendiente.
   */
  suspend fun apply(prepared: PreparedImport, onProgress: (Float) -> Unit): ImportResult

  /** Verdadero mientras dura [apply]. Es global para que una pantalla recreada vea el mismo estado. */
  val applying: StateFlow<Boolean>

  /** Emite al terminar una importación con éxito: la interfaz vuelve las pestañas a su raíz. */
  val imported: SharedFlow<Unit>

  /** Borra el staging de una copia preparada que el usuario no confirmó. */
  fun discard(prepared: PreparedImport)
}
