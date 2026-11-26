package dev.josueamaya.pourline.recipe.catalog

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Los campos desconocidos se ignoran: permite añadir campos opcionales sin subir `schemaVersion` (§3.1). */
internal val CatalogJson = Json {
  ignoreUnknownKeys = true
  explicitNulls = false
}

/** Un texto localizado; otras claves de idioma se ignoran. */
@Serializable
internal data class RecipeCatalogLocalizedDto(val es: String? = null, val en: String? = null)

@Serializable
internal data class RecipeCatalogGrinderRefDto(val catalogKey: String, val value: Double)

@Serializable
internal data class RecipeCatalogGrindDto(
  val text: RecipeCatalogLocalizedDto? = null,
  val grinder: RecipeCatalogGrinderRefDto? = null,
)

@Serializable
internal data class RecipeCatalogStageDto(
  val type: String,
  val startS: Int,
  val durationS: Int,
  val waterG: Double? = null,
  val name: RecipeCatalogLocalizedDto? = null,
  val instruction: RecipeCatalogLocalizedDto? = null,
)

@Serializable
internal data class RecipeCatalogRecipeDto(
  val key: String,
  val name: RecipeCatalogLocalizedDto,
  val author: String? = null,
  val context: RecipeCatalogLocalizedDto? = null,
  val method: String,
  val methodOther: RecipeCatalogLocalizedDto? = null,
  val doseG: Double,
  val waterTempC: Double? = null,
  val grind: RecipeCatalogGrindDto? = null,
  val notes: RecipeCatalogLocalizedDto? = null,
  val sourceUrl: String? = null,
  val stages: List<RecipeCatalogStageDto>,
)
