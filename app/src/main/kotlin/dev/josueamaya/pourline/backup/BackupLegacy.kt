package dev.josueamaya.pourline.backup

/**
 * Las copias con `formatVersion` 1 se hicieron con el esquema 7, donde las recetas de fábrica eran filas con `builtInKey`
 * y nombres traducidos al vuelo. La base 8 ya no las siembra, las traduce ni las usa (SPEC-012), así que cada una pasa a
 * receta propia: `catalogKey` toma la clave de fábrica, `builtInKey` queda en `null` y `localizedNames` en `false`. Los
 * textos no se traducen: el guardado es el del idioma en que se sembró, el mismo que el usuario veía.
 */
internal fun BackupRows.withLegacyBuiltInsConverted(): BackupRows = copy(
  recipes = recipes.map { recipe ->
    if (recipe.builtInKey == null) {
      recipe
    } else {
      recipe.copy(catalogKey = recipe.builtInKey, builtInKey = null, localizedNames = false)
    }
  },
)
