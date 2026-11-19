package dev.josueamaya.pourline.recipe.share

import dev.josueamaya.pourline.recipe.Recipe

/**
 * El resultado de codificar: el URI, lo que hubo que quitar para que cupiera y cuántas instrucciones de paso se quitaron
 * ([droppedInstructions]) y se conservaron ([keptInstructions]). [TrimmedPart.INSTRUCTIONS] indica que se quitó al menos una.
 */
data class EncodedRecipe(
  val uri: String,
  val trimmed: Set<TrimmedPart>,
  val droppedInstructions: Int = 0,
  val keptInstructions: Int = 0,
) {
  /** Se quitaron algunas instrucciones pero no todas. */
  val partialInstructions: Boolean get() = droppedInstructions > 0 && keptInstructions > 0
}

/** Lo que da codificar: el URI, o por qué no hay (D38, enmienda 1 de SPEC-009). */
sealed interface EncodeResult {
  data class Encoded(val encoded: EncodedRecipe) : EncodeResult

  /** Ni con todo el recorte cabe en `MAX_URI_LENGTH`. */
  data object TooLong : EncodeResult

  /** La receta no pasa `RecipeValidator`. */
  data object Invalid : EncodeResult
}

enum class TrimmedPart { NOTES, INSTRUCTIONS, STAGE_NAMES }

/** Lo decodificado: la receta sin id ni molino local, y la molienda tal como viajó. */
data class SharedRecipe(val recipe: Recipe, val grind: SharedGrind?, val trimmed: Set<TrimmedPart>) {
  /**
   * Faltan algunas instrucciones de paso pero no todas: el bit de instrucciones recortadas y al menos un paso con
   * instrucción. Un QR de la app anterior (todas fuera) no es parcial.
   */
  val partialInstructions: Boolean
    get() = TrimmedPart.INSTRUCTIONS in trimmed && recipe.stages.any { !it.instruction.isNullOrBlank() }
}

/** La molienda de quien compartió. Con un molino eliminado solo hay [label]. */
data class SharedGrind(
  val catalogKey: String?,
  val brand: String?,
  val model: String?,
  val value: Double?,
  val label: String?,
)

sealed interface DecodeResult {
  data class Ok(val shared: SharedRecipe) : DecodeResult

  /** Otro esquema, otro host o sin `d`. */
  data object NotPourline : DecodeResult

  /** `v` mayor que la versión soportada. */
  data object NewerVersion : DecodeResult

  /** Base64, deflate o binario inválidos. */
  data object Corrupt : DecodeResult

  /** No pasa `RecipeValidator`. */
  data object Invalid : DecodeResult
}
