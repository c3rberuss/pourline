package dev.josueamaya.pourline.recipe.catalog

import dev.josueamaya.pourline.grinder.GrindPick
import dev.josueamaya.pourline.grinder.GrindResolution
import dev.josueamaya.pourline.grinder.GrindTexts
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.grinder.settingFor
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.share.uniqueName

/**
 * La receta propia que se guarda desde [recipe] (SPEC-012 §3.6): `id = 0`, con el nombre sin repetir entre
 * [existingNames] (" (2)", " (3)"…), el autor, la fuente y la clave de catálogo, y la molienda que da [resolution] con
 * la elección [pick]. [owned] son los molinos de ahora: uno elegido que ya no está se guarda como texto.
 */
fun catalogCopy(
  recipe: CatalogRecipe,
  resolution: GrindResolution,
  pick: GrindPick?,
  owned: List<Grinder>,
  existingNames: Collection<String>,
  texts: GrindTexts,
): Recipe = recipe.toRecipe().copy(
  name = uniqueName(recipe.name, existingNames),
  grind = resolution.settingFor(pick, owned, texts),
)

/** Si ya hay una receta guardada con la clave [key] (la etiqueta "Guardada"). */
fun hasCopy(key: String, catalogKeys: Set<String>): Boolean = key in catalogKeys
