package dev.josueamaya.pourline.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** El formato del archivo de copia (SPEC-011 §3.1). */
internal const val APP_ID = "pourline"

/**
 * Versión de este formato, independiente de la versión del esquema de Room. La 2 añade los campos del catálogo de recetas
 * (SPEC-012); el lector sigue aceptando la 1.
 */
internal const val FORMAT_VERSION = 2

internal const val MANIFEST_ENTRY = "manifest.json"
internal const val DATA_ENTRY = "data.json"
internal const val SETTINGS_ENTRY = "settings.json"
internal const val PHOTOS_PREFIX = "photos/"

/** El nombre de una foto (principal o miniatura) dentro de `photos/`: sin separadores ni `..`, así que no hay "zip slip". */
internal val PHOTO_NAME = Regex("^[A-Za-z0-9-]{1,64}(_thumb)?\\.jpg$")

/** El nombre de una foto principal, el que guardan `photo1` y `photo2`. */
internal val MAIN_PHOTO_NAME = Regex("^[A-Za-z0-9-]{1,64}\\.jpg$")

/**
 * `explicitNulls = false`: un `null` no se escribe y una clave anulable ausente se lee como `null`; una no anulable
 * ausente es un error. `ignoreUnknownKeys = false`: con la misma `formatVersion`, los mismos campos, así nunca se
 * descarta un dato en silencio (SPEC-011 §3.8).
 */
@OptIn(ExperimentalSerializationApi::class)
internal val BackupJson = Json {
  explicitNulls = false
  ignoreUnknownKeys = false
  allowSpecialFloatingPointValues = true
}

private val FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm", Locale.ROOT)

/** `pourline-backup-2026-11-19-1845.zip`, con la hora local. */
internal fun backupFileName(now: LocalDateTime): String = "pourline-backup-${FILE_NAME_FORMAT.format(now)}.zip"

/** ISO 8601 con desfase y segundos, sin fracción: `2026-11-19T18:45:12-06:00`. */
internal fun formatCreatedAt(at: OffsetDateTime): String =
  DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(at.truncatedTo(ChronoUnit.SECONDS))
