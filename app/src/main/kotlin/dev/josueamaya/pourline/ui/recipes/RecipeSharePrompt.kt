package dev.josueamaya.pourline.ui.recipes

import androidx.annotation.StringRes
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.share.EncodeResult
import dev.josueamaya.pourline.recipe.share.RecipeShareCodec
import dev.josueamaya.pourline.recipe.share.TrimmedPart
import dev.josueamaya.pourline.share.RecipeShareData
import dev.josueamaya.pourline.share.qrMatrix

/** Por qué la tarjeta de una receta va sin QR (SPEC-009 §3.4 y §4, enmienda 1). */
enum class NoQrReason {
  /** Ni con todo el recorte cabe en el QR. */
  TOO_LONG,

  /** La receta guardada no pasa el validador. */
  INVALID,
}

/**
 * Lo que hay que enseñar antes de compartir la receta [id] como imagen: la tarjeta ya preparada ([data]), lo que el
 * QR no pudo llevar ([trimmed]), si de las instrucciones solo faltan algunas ([partialInstructions]) y, si la tarjeta va
 * sin QR, por qué ([noQr]).
 */
data class RecipeSharePrompt(
  val id: Long,
  val data: RecipeShareData,
  val trimmed: Set<TrimmedPart>,
  val partialInstructions: Boolean = false,
  val noQr: NoQrReason? = null,
) {
  val withoutQr: Boolean get() = noQr != null

  /** Con recorte o sin QR hay que avisar y esperar a que el usuario acepte; si no, se comparte directamente. */
  val needsConfirmation: Boolean get() = withoutQr || trimmed.isNotEmpty()
}

/**
 * El aviso de compartir [recipe]: codifica con [RecipeShareCodec.encodeResult] y dibuja el QR si hay URI. [grindText] es el
 * texto de molienda de la tarjeta. Es pura (sin acceso a la base de datos), para probarla en la JVM.
 */
internal fun recipeSharePrompt(id: Long, recipe: Recipe, grinder: Grinder?, grindText: String?): RecipeSharePrompt {
  fun withoutQr(reason: NoQrReason) =
    RecipeSharePrompt(id, RecipeShareData(recipe, grindText, WeightUnit.GRAMS, qr = null), emptySet(), noQr = reason)
  return when (val result = RecipeShareCodec.encodeResult(recipe, grinder)) {
    is EncodeResult.Encoded -> {
      val encoded = result.encoded
      val data = RecipeShareData(recipe, grindText, WeightUnit.GRAMS, qrMatrix(encoded.uri))
      RecipeSharePrompt(id, data, encoded.trimmed, encoded.partialInstructions)
    }
    EncodeResult.TooLong -> withoutQr(NoQrReason.TOO_LONG)
    EncodeResult.Invalid -> withoutQr(NoQrReason.INVALID)
  }
}

/** El texto del diálogo de compartir: sin QR por longitud, sin QR por datos no válidos, o con recorte (con `%1$s` por rellenar). */
@StringRes
internal fun RecipeSharePrompt.dialogTextRes(): Int = when (noQr) {
  NoQrReason.TOO_LONG -> R.string.recipe_share_too_long_text
  NoQrReason.INVALID -> R.string.recipe_share_invalid_text
  null -> R.string.recipe_share_trimmed_text
}
