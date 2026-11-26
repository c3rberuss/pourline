package dev.josueamaya.pourline.recipe.catalog

import dev.josueamaya.pourline.grinder.CatalogSource
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.grinder.GrinderCatalogFile

/**
 * Los molinos del catálogo de molinos por `catalogKey`, para validar `grind.grinder` de las recetas. Vacío si el archivo
 * falta o es inválido (entonces se descartan las recetas que dependen de un molino).
 */
fun grinderCatalogByKey(source: CatalogSource): Map<String, Grinder> {
  val text = source.read() ?: return emptyMap()
  val data = GrinderCatalogFile.parse(text).getOrNull() ?: return emptyMap()
  return data.grinders.mapNotNull { grinder -> grinder.catalogKey?.let { it to grinder } }.toMap()
}
