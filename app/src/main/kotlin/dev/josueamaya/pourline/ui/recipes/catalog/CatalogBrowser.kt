package dev.josueamaya.pourline.ui.recipes.catalog

import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.RecipeRepository
import dev.josueamaya.pourline.recipe.catalog.CatalogError
import dev.josueamaya.pourline.recipe.catalog.CatalogLanguageSource
import dev.josueamaya.pourline.recipe.catalog.CatalogListItem
import dev.josueamaya.pourline.recipe.catalog.CatalogState
import dev.josueamaya.pourline.recipe.catalog.RecipeCatalogRepository
import dev.josueamaya.pourline.recipe.catalog.catalogMethods
import dev.josueamaya.pourline.recipe.catalog.filterCatalog
import dev.josueamaya.pourline.recipe.catalog.toListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** El aviso discreto de encima de la lista (SPEC-012 §3.8 y §4). */
enum class CatalogNotice {
  /** Falló la última actualización y hubo una descarga correcta antes: "No se pudo actualizar · Última actualización: …". */
  UPDATE_FAILED,

  /** Falló la actualización y nunca hubo una descarga: se usan las recetas del APK. */
  USING_BUNDLED,

  /** El archivo es de un formato más nuevo: hay que actualizar la app. */
  NEWER_SCHEMA,

  /** Todo bien y hubo una descarga: "Última actualización: …". */
  LAST_UPDATE,
}

/**
 * Lo que ve la lista de recomendadas. [items] ya está filtrada; [total] es lo que hay sin filtrar (para distinguir "no hay
 * recetas con estos filtros" de "no hay ninguna") y [savedKeys] las claves de las que el usuario ya tiene una copia.
 */
data class CatalogListUi(
  val items: List<CatalogListItem> = emptyList(),
  val total: Int = 0,
  val methods: List<BrewMethod> = emptyList(),
  val query: String = "",
  val method: BrewMethod? = null,
  val savedKeys: Set<String> = emptySet(),
  val notice: CatalogNotice? = null,
  val lastDownloadMs: Long? = null,
  val refreshing: Boolean = false,
  val loaded: Boolean = false,
) {
  val filtered: Boolean get() = query.isNotBlank() || method != null
}

/**
 * La lista de recomendadas con su búsqueda y su filtro por método (D33). La comparten la pestaña Recetas y el selector de
 * Preparar. Los textos salen en el idioma de [language] en el momento de [onShown] (una rotación o un cambio de idioma
 * recrea la pantalla y vuelve a llamarla).
 */
class CatalogBrowser(
  private val catalog: RecipeCatalogRepository,
  recipes: RecipeRepository,
  private val language: CatalogLanguageSource,
  scope: CoroutineScope,
) {
  private val query = MutableStateFlow("")
  private val method = MutableStateFlow<BrewMethod?>(null)
  private val currentLanguage = MutableStateFlow(language.current())

  val ui: StateFlow<CatalogListUi> = combine(catalog.state, recipes.catalogKeys(), currentLanguage, query, method) { state, saved, lang, text, kind ->
    val all = state.entries.map { it.inLanguage(lang).toListItem() }
    CatalogListUi(
      items = filterCatalog(all, text, kind),
      total = all.size,
      methods = catalogMethods(all),
      query = text,
      method = kind,
      savedKeys = saved,
      notice = noticeOf(state),
      lastDownloadMs = state.lastDownloadMs,
      refreshing = state.refreshing,
      loaded = state.loaded,
    )
  }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CatalogListUi())

  init {
    catalog.ensureLoaded()
  }

  fun setQuery(text: String) {
    query.value = text
  }

  fun setMethod(value: BrewMethod?) {
    method.value = value
  }

  fun clearFilters() {
    query.value = ""
    method.value = null
  }

  /** La lista se muestra: toma el idioma actual y descarga si hace 24 h o más de la última descarga. */
  fun onShown() {
    currentLanguage.value = language.current()
    catalog.refreshIfStale()
  }

  /** Deslizar para actualizar: descarga sin mirar las 24 h. */
  fun refresh() {
    catalog.refresh(force = true)
  }

  private fun noticeOf(state: CatalogState): CatalogNotice? = when (state.error) {
    CatalogError.NEWER_SCHEMA -> CatalogNotice.NEWER_SCHEMA
    CatalogError.UPDATE_FAILED -> if (state.lastDownloadMs != null) CatalogNotice.UPDATE_FAILED else CatalogNotice.USING_BUNDLED
    null -> if (state.lastDownloadMs != null) CatalogNotice.LAST_UPDATE else null
  }

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
