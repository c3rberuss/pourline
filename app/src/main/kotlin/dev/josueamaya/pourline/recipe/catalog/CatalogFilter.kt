package dev.josueamaya.pourline.recipe.catalog

import dev.josueamaya.pourline.coffee.RoastLevel
import dev.josueamaya.pourline.coffee.fold
import dev.josueamaya.pourline.history.BrewMethod

/** Lo que la lista de recomendadas muestra de una receta, ya en un idioma (SPEC-012 §3.8). */
data class CatalogListItem(
  val key: String,
  val name: String,
  val author: String?,
  val method: BrewMethod,
  val methodOther: String?,
  val doseG: Double,
  val totalWaterG: Double,
  val ratio: Double,
  val totalDurationMs: Long,
  val stageCount: Int,
  /** Los tuestes para los que sirve; vacío, cualquiera. */
  val roasts: Set<RoastLevel> = emptySet(),
  /** El nombre y el autor sin mayúsculas ni acentos, para buscar con [catalogSearchKey]. */
  val searchKey: String,
)

fun CatalogRecipe.toListItem(): CatalogListItem {
  val recipe = toRecipe()
  val water = recipe.totalWaterG
  return CatalogListItem(
    key = key,
    name = name,
    author = author,
    method = method,
    methodOther = methodOther,
    doseG = doseG,
    totalWaterG = water,
    ratio = if (doseG > 0) water / doseG else 0.0,
    totalDurationMs = recipe.totalDurationMs,
    stageCount = recipe.stages.size,
    roasts = roasts,
    searchKey = catalogSearchKey(name) + SEPARATOR + catalogSearchKey(author.orEmpty()),
  )
}

/** Separa el nombre del autor dentro de la clave: no puede salir de lo que escribe el usuario. */
private const val SEPARATOR = '\u0000'

/** Letras que no son un acento combinable y que `fold` no cambia: "Bøen" se encuentra escribiendo "boen" (D33). */
private val SpecialLetters = mapOf('ø' to "o", 'æ' to "ae", 'œ' to "oe", 'ß' to "ss", 'ł' to "l", 'đ' to "d")

/** El texto sin mayúsculas ni acentos y con las letras especiales de [SpecialLetters] sustituidas. */
fun catalogSearchKey(text: String): String = buildString {
  fold(text).forEach { char -> append(SpecialLetters[char] ?: char) }
}

/** Sirve para [roast]: sin tueste elegido, sin `roasts` (cualquiera) o con ese tueste entre los suyos (D52). */
fun CatalogListItem.suitsRoast(roast: RoastLevel?): Boolean = roast == null || roasts.isEmpty() || roast in roasts

/**
 * Las recetas cuyo nombre o autor contiene [query] (sin mayúsculas ni acentos) y, con [method], de ese método y, con
 * [roast], aptas para ese tueste.
 */
fun filterCatalog(
  items: List<CatalogListItem>,
  query: String,
  method: BrewMethod?,
  roast: RoastLevel? = null,
): List<CatalogListItem> {
  val wanted = catalogSearchKey(query).trim()
  return items.filter { item ->
    (method == null || item.method == method) &&
      item.suitsRoast(roast) &&
      (wanted.isEmpty() || item.searchKey.contains(wanted))
  }
}

/** Los tres filtros de la lista de recomendadas; [CatalogFilters] sin tocar es "Todos" en todo (D52). */
data class CatalogFilters(
  val query: String = "",
  val method: BrewMethod? = null,
  val roast: RoastLevel? = null,
) {
  /** Algún filtro quita recetas. */
  val active: Boolean get() = query.isNotBlank() || method != null || roast != null
}

/** Los métodos que hay en [items], en el orden de [BrewMethod]. */
fun catalogMethods(items: List<CatalogListItem>): List<BrewMethod> {
  val present = items.mapTo(hashSetOf()) { it.method }
  return BrewMethod.entries.filter { it in present }
}
