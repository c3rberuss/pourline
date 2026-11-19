package dev.josueamaya.pourline.recipe.share

/** De dónde llega el texto de un QR. */
enum class ImportSource { SCANNER, IMAGE, LINK }

/** Por qué no se pudo importar (§4). */
enum class ImportError {
  NOT_POURLINE,
  NO_RECIPE_IN_IMAGE,
  NEWER_VERSION,
  CORRUPT,
  INVALID,
  SCANNER_UNAVAILABLE,

  /** El modelo de lectura de QR de Play Services aún no está descargado. */
  READER_NOT_READY,
}

/** Qué hacer con un texto de QR. */
sealed interface ImportOutcome {
  /** Abre la vista previa de [uri]. */
  data class Open(val uri: String) : ImportOutcome

  /** Hay una sesión en curso: la vista previa de [uri] espera a que termine. */
  data class Defer(val uri: String) : ImportOutcome

  data class Fail(val error: ImportError) : ImportOutcome
}

/**
 * Decide qué se hace con [text], el texto que dio una entrada. `null` es que no hubo texto (una imagen sin QR de Pourline).
 * Desde una imagen, cualquier QR ajeno o ausente es "no hay receta en la imagen".
 */
fun routeImport(text: String?, source: ImportSource, hasSession: Boolean): ImportOutcome {
  if (text == null) return ImportOutcome.Fail(ImportError.NO_RECIPE_IN_IMAGE)
  return when (RecipeShareCodec.decode(text)) {
    is DecodeResult.Ok -> if (hasSession) ImportOutcome.Defer(text.trim()) else ImportOutcome.Open(text.trim())
    DecodeResult.NotPourline ->
      ImportOutcome.Fail(if (source == ImportSource.IMAGE) ImportError.NO_RECIPE_IN_IMAGE else ImportError.NOT_POURLINE)
    DecodeResult.NewerVersion -> ImportOutcome.Fail(ImportError.NEWER_VERSION)
    DecodeResult.Corrupt -> ImportOutcome.Fail(ImportError.CORRUPT)
    DecodeResult.Invalid -> ImportOutcome.Fail(ImportError.INVALID)
  }
}

/** Lo que da la lectura del QR de una imagen. */
sealed interface QrReadResult {
  /** El texto del primer QR que empieza por `pourline://recipe`. */
  data class Found(val text: String) : QrReadResult

  /** No hay QR de Pourline en la imagen, o no se pudo leer. */
  data object NoRecipe : QrReadResult

  /** El modelo de lectura de QR de Play Services aún no está descargado. */
  data object ModelNotReady : QrReadResult
}

/** Lo mismo que [routeImport] para una imagen; con el lector sin preparar no abre ni aplaza nada. */
fun routeImageRead(result: QrReadResult, hasSession: Boolean): ImportOutcome = when (result) {
  is QrReadResult.Found -> routeImport(result.text, ImportSource.IMAGE, hasSession)
  QrReadResult.NoRecipe -> routeImport(null, ImportSource.IMAGE, hasSession)
  QrReadResult.ModelNotReady -> ImportOutcome.Fail(ImportError.READER_NOT_READY)
}
