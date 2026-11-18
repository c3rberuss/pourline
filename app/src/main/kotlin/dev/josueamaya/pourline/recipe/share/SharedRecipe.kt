package dev.josueamaya.pourline.recipe.share

import dev.josueamaya.pourline.recipe.Recipe

/** El resultado de codificar: el URI y lo que hubo que quitar para que cupiera. */
data class EncodedRecipe(val uri: String, val trimmed: Set<TrimmedPart>)

enum class TrimmedPart { NOTES, INSTRUCTIONS, STAGE_NAMES }

/** Lo decodificado: la receta sin id ni molino local, y la molienda tal como viajó. */
data class SharedRecipe(val recipe: Recipe, val grind: SharedGrind?, val trimmed: Set<TrimmedPart>)

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
