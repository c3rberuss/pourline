package dev.josueamaya.pourline.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * El marcador de una importación en curso (`backup_import/import.json`, SPEC-011 §3.5): lo que hace falta para terminarla
 * si la app muere a mitad. [APPLYING] se escribe antes de la transacción de Room y [DB_DONE] justo después; en
 * [APPLYING], la huella dice si la transacción llegó a confirmarse. [settings] son los ajustes que se van a restaurar,
 * ya con `onboarding_done = true`.
 */
@Serializable
internal data class ImportMarker(
  val state: State,
  val settings: SettingsDto,
  val fingerprint: BackupFingerprint,
) {
  @Serializable
  enum class State { APPLYING, DB_DONE }

  companion object {
    const val FILE_NAME = "import.json"
    private const val TEMP_NAME = "import.json.tmp"

    /** Escritura atómica: archivo temporal, `fsync` y renombrado. Un temporal que sobra no se lee nunca. */
    @OptIn(ExperimentalSerializationApi::class)
    fun write(dir: File, marker: ImportMarker) {
      dir.mkdirs()
      val temp = File(dir, TEMP_NAME)
      FileOutputStream(temp).use { out ->
        BackupJson.encodeToStream(serializer(), marker, out)
        out.flush()
        out.fd.sync()
      }
      val target = File(dir, FILE_NAME)
      if (!temp.renameTo(target)) {
        // Algunos sistemas de archivos no sustituyen al renombrar: se borra el anterior y se reintenta.
        target.delete()
        if (!temp.renameTo(target)) throw IOException("No se pudo guardar el marcador de importación")
      }
    }

    /** `null` si no existe o no se puede leer. */
    @OptIn(ExperimentalSerializationApi::class)
    fun read(dir: File): ImportMarker? =
      try {
        File(dir, FILE_NAME).takeIf { it.isFile }?.inputStream()?.use { BackupJson.decodeFromStream(serializer(), it) }
      } catch (e: Exception) {
        null
      }

    fun delete(dir: File) {
      File(dir, FILE_NAME).delete()
      File(dir, TEMP_NAME).delete()
    }
  }
}
