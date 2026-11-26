package dev.josueamaya.pourline.recipe.catalog

import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.withDerivedWater

/**
 * La receta del catálogo como [Recipe] (§3.4): `id = 0`, sin clave de fábrica y sin molienda (la pone quien la usa, D29 y
 * D30). `context` no pasa a [Recipe]: solo se muestra en el detalle.
 */
fun CatalogRecipe.toRecipe(): Recipe = Recipe(
  name = name,
  method = method,
  methodOther = methodOther,
  grind = null,
  waterTempC = waterTempC,
  notes = notes,
  doseG = doseG,
  stages = stages.sortedBy { it.startMs }.withDerivedWater(),
  builtInKey = null,
  localizedNames = false,
  author = author,
  sourceUrl = sourceUrl,
  catalogKey = key,
)

/** Construye la receta de [dto] en [language]; los segundos pasan a milisegundos y los pasos se ordenan por inicio. */
internal fun RecipeCatalogRecipeDto.toCatalogRecipe(language: CatalogLanguage): CatalogRecipe {
  fun RecipeCatalogLocalizedDto.pick(): String = (if (language == CatalogLanguage.EN) en else es).orEmpty().trim()
  val steps = stages.map { stage ->
    RecipeStage(
      name = stage.name?.pick(),
      startMs = stage.startS * 1000L,
      targetWaterG = stage.waterG ?: 0.0,
      durationMs = stage.durationS * 1000L,
      type = stageTypeOf(stage.type)!!,
      instruction = stage.instruction?.pick(),
    )
  }.sortedBy { it.startMs }.withDerivedWater()
  return CatalogRecipe(
    key = key,
    name = name.pick(),
    author = author?.trim()?.takeIf { it.isNotEmpty() },
    context = context?.pick(),
    method = methodOf(method)!!,
    methodOther = methodOther?.pick(),
    doseG = doseG,
    waterTempC = waterTempC,
    grindText = grind?.text?.pick(),
    grinderRef = grind?.grinder?.let { CatalogGrinderRef(it.catalogKey, it.value) },
    notes = notes?.pick(),
    sourceUrl = sourceUrl?.trim(),
    stages = steps,
  )
}
