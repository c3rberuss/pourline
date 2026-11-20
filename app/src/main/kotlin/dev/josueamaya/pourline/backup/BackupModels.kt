package dev.josueamaya.pourline.backup

import dev.josueamaya.pourline.coffee.CoffeeEntity
import dev.josueamaya.pourline.grinder.GrindCalibrationEntity
import dev.josueamaya.pourline.grinder.GrinderAnchorEntity
import dev.josueamaya.pourline.grinder.GrinderEntity
import dev.josueamaya.pourline.grinder.GrinderMethodRangeEntity
import dev.josueamaya.pourline.grinder.GrinderUnitEntity
import dev.josueamaya.pourline.history.BrewCurveEntity
import dev.josueamaya.pourline.history.BrewEntity
import dev.josueamaya.pourline.history.BrewTargetEntity
import dev.josueamaya.pourline.recipe.RecipeEntity
import dev.josueamaya.pourline.recipe.RecipeStageEntity
import dev.josueamaya.pourline.settings.SettingsSnapshot
import kotlinx.serialization.Serializable
import java.io.File
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicReference

/** Los recuentos de una copia, para el diálogo y los mensajes (SPEC-011 §3.1). */
data class BackupCounts(
  val coffees: Int,
  /** Solo "Mis molinos" (`owned = true`), no los predefinidos del catálogo. */
  val grinders: Int,
  val grindCalibrations: Int,
  /** También las predefinidas. */
  val recipes: Int,
  val brews: Int,
  /** Las fotos principales distintas, sin miniaturas. */
  val photos: Int,
)

/** Resumen de una copia leída y validada, para el diálogo de confirmación. */
data class BackupSummary(
  val createdAt: OffsetDateTime,
  val appVersionName: String,
  val counts: BackupCounts,
)

enum class BackupError {
  /** No es ZIP, falta `manifest.json` o `app` no es `"pourline"`. */
  NOT_A_BACKUP,

  /** `formatVersion` mayor que el soportado. */
  NEWER_FORMAT,

  /** JSON inválido, campos obligatorios ausentes, claves repetidas, entradas inesperadas o rutas inseguras. */
  CORRUPT,

  /** Una fila referencia otra que no está en la copia. */
  BROKEN_REFERENCES,

  /** Supera los límites de tamaño (al importar o al exportar). */
  TOO_LARGE,

  /** Fallo de lectura o escritura (proveedor, espacio). */
  IO,
}

sealed interface ExportResult {
  data class Success(val counts: BackupCounts) : ExportResult

  data class Failed(val reason: BackupError) : ExportResult
}

sealed interface PrepareResult {
  data class Ready(val import: PreparedImport) : PrepareResult

  data class Invalid(val reason: BackupError) : PrepareResult
}

sealed interface ImportResult {
  data class Success(val counts: BackupCounts, val missingPhotos: Int) : ImportResult

  /** Los datos no cambiaron. */
  data class Failed(val reason: BackupError) : ImportResult
}

/** Las filas de las 11 tablas de Room. Las entidades con `ByteArray` comparan por referencia en `equals`. */
data class BackupRows(
  val brews: List<BrewEntity> = emptyList(),
  val brewCurves: List<BrewCurveEntity> = emptyList(),
  val brewTargets: List<BrewTargetEntity> = emptyList(),
  val recipes: List<RecipeEntity> = emptyList(),
  val recipeStages: List<RecipeStageEntity> = emptyList(),
  val coffees: List<CoffeeEntity> = emptyList(),
  val grinders: List<GrinderEntity> = emptyList(),
  val grinderUnits: List<GrinderUnitEntity> = emptyList(),
  val grinderMethodRanges: List<GrinderMethodRangeEntity> = emptyList(),
  val grinderAnchors: List<GrinderAnchorEntity> = emptyList(),
  val grindCalibrations: List<GrindCalibrationEntity> = emptyList(),
)

/**
 * Lo que distingue una base ya importada de una que no: los recuentos de las 11 tablas (en el orden de [BackupRows]),
 * el mayor id de `brews`, `recipes`, `coffees`, `grinders` y `grind_calibrations`, y los nombres de foto
 * referenciados, ordenados (SPEC-011 §3.5).
 */
@Serializable
data class BackupFingerprint(
  val counts: List<Int>,
  val maxIds: List<Long>,
  val photoNames: List<String>,
)

/** La versión de la app que crea la copia. */
data class AppVersion(val name: String, val code: Long)

/**
 * Copia validada y lista para aplicarse; no ha modificado nada. Se aplica una sola vez ([state]) y, si el usuario
 * cancela, se descarta.
 */
class PreparedImport internal constructor(
  val summary: BackupSummary,
  internal val rows: BackupRows,
  internal val settings: SettingsSnapshot,
  internal val staging: File,
  internal val missingPhotos: Int,
) {
  internal enum class State { OPEN, APPLIED, DISCARDED }

  internal val state = AtomicReference(State.OPEN)
}

/** Fallo interno de la copia, con el [BackupError] que se informa. */
internal class BackupFailure(val reason: BackupError, cause: Throwable? = null) : Exception(reason.name, cause)
