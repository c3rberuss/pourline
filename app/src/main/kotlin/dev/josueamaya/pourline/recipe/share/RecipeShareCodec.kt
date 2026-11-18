package dev.josueamaya.pourline.recipe.share

import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeValidator
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URISyntaxException
import java.util.Base64
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * El formato del QR de una receta (SPEC-009 §3.1): `pourline://recipe?v=<versión>&d=<payload>`, con el payload binario
 * comprimido con `deflate` (raw, nivel 9) y escrito en `base64url` sin relleno. Es JVM puro.
 */
object RecipeShareCodec {
  const val VERSION = 1
  const val SCHEME = "pourline"
  const val HOST = "recipe"
  const val URI_PREFIX = "pourline://recipe"

  /**
   * Longitud máxima del URI del QR (D8). Medida en F0 con `QrLegibilityTest`, el lector de Play Services y el QR de
   * 432 px: es la capacidad en modo byte con corrección M de la versión 13. La regla del plan daría 362 (versión 14),
   * pero con 362 la lectura tras reducir al 50 % y JPEG 70 falla en 1 de 20 muestras; con 331 acierta 20 de 20.
   */
  const val MAX_URI_LENGTH = 331

  /** Tope del payload ya inflado: un QR legítimo ocupa unos cientos de bytes. Evita una bomba de descompresión. */
  internal const val MAX_INFLATED_BYTES = 65_536

  /**
   * El URI de [recipe] y lo que hubo que quitar para que quepa en [MAX_URI_LENGTH]. [grinder] es el molino vivo de
   * `recipe.grind` (`null` si no hay o ya no existe). `null` si ni recortando cabe o si la receta no es válida.
   */
  fun encode(recipe: Recipe, grinder: Grinder?): EncodedRecipe? = encode(recipe, grinder, MAX_URI_LENGTH)

  internal fun encode(recipe: Recipe, grinder: Grinder?, maxLength: Int): EncodedRecipe? {
    val ordered = recipe.copy(stages = recipe.stages.sortedBy { it.startMs })
    if (RecipeValidator.validate(ordered).isNotEmpty()) return null
    for (level in TrimLevel.entries) {
      val payload = encodePayload(ordered, grinder, level)
      val uri = uriFor(payload.bytes)
      if (uri.length <= maxLength) return EncodedRecipe(uri, payload.trimmed)
    }
    return null
  }

  fun decode(text: String): DecodeResult {
    val uri = try {
      URI(text.trim())
    } catch (_: URISyntaxException) {
      return DecodeResult.NotPourline
    }
    if (!SCHEME.equals(uri.scheme, ignoreCase = true) || !HOST.equals(uri.host, ignoreCase = true)) return DecodeResult.NotPourline
    val params = (uri.rawQuery ?: "").split('&').mapNotNull {
      val i = it.indexOf('=')
      if (i < 0) null else it.substring(0, i) to it.substring(i + 1)
    }.toMap()
    val data = params["d"]?.takeIf { it.isNotEmpty() } ?: return DecodeResult.NotPourline
    val version = params["v"]?.toIntOrNull()?.takeIf { it >= 1 } ?: return DecodeResult.Corrupt
    if (version > VERSION) return DecodeResult.NewerVersion
    val compressed = try {
      Base64.getUrlDecoder().decode(data)
    } catch (_: IllegalArgumentException) {
      return DecodeResult.Corrupt
    }
    val payload = inflate(compressed) ?: return DecodeResult.Corrupt
    return decodePayload(payload)
  }

  /** El URI completo de un payload ya serializado: `deflate`, `base64url` y el prefijo con la versión. */
  internal fun uriFor(payload: ByteArray): String {
    val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
    val compressed = try {
      deflater.setInput(payload)
      deflater.finish()
      val out = ByteArrayOutputStream()
      val buffer = ByteArray(256)
      while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
      out.toByteArray()
    } finally {
      deflater.end()
    }
    return "$URI_PREFIX?v=$VERSION&d=" + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
  }

  /** El payload inflado, o `null` si el flujo está dañado, truncado o pasa de [MAX_INFLATED_BYTES]. */
  private fun inflate(compressed: ByteArray): ByteArray? {
    // Con `nowrap`, zlib pide un byte más al final del flujo.
    val inflater = Inflater(true)
    return try {
      inflater.setInput(compressed + byteArrayOf(0))
      val out = ByteArrayOutputStream()
      val buffer = ByteArray(1024)
      while (!inflater.finished()) {
        val read = inflater.inflate(buffer)
        if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null
        out.write(buffer, 0, read)
        if (out.size() > MAX_INFLATED_BYTES) return null
      }
      out.toByteArray()
    } catch (_: DataFormatException) {
      null
    } finally {
      inflater.end()
    }
  }
}
