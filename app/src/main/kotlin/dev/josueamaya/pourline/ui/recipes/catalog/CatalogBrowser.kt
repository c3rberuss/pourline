package dev.josueamaya.pourline.ui.recipes.catalog

import dev.josueamaya.pourline.coffee.RoastLevel
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.catalog.CatalogError
import dev.josueamaya.pourline.recipe.catalog.CatalogFilters
import dev.josueamaya.pourline.recipe.catalog.CatalogLanguageSource
import dev.josueamaya.pourline.recipe.catalog.CatalogListItem
import dev.josueamaya.pourline.recipe.catalog.CatalogState
import dev.josueamaya.pourline.recipe.catalog.RecipeCatalogRepository
import dev.josueamaya.pourline.recipe.catalog.catalogMethods
import dev.josueamaya.pourline.recipe.catalog.filterCatalog
import dev.josueamaya.pourline.recipe.catalog.toListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

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
  val roast: RoastLevel? = null,
  val savedKeys: Set<String> = emptySet(),
  val notice: CatalogNotice? = null,
  val lastDownloadMs: Long? = null,
  val refreshing: Boolean = false,
  val loaded: Boolean = false,
) {
  val filtered: Boolean get() = query.isNotBlank() || method != null || roast != null
}

/**
 * La lista de recomendadas con su búsqueda y sus filtros por método y por tueste (D33, D53). La comparten la pestaña Recetas y el selector de
 * Preparar. Los textos salen en el idioma de [language] en el momento de [onShown] (una rotación o un cambio de idioma
 * recrea la pantalla y vuelve a llamarla). [savedKeys] son las claves de las recetas que el usuario ya guardó.
 */
class CatalogBrowser(
  private val catalog: RecipeCatalogRepository,
  savedKeys: Flow<Set<String>>,
  private val language: CatalogLanguageSource,
  scope: CoroutineScope,
) {
  private val filters = MutableStateFlow(CatalogFilters())
  private val currentLanguage = MutableStateFlow(language.current())

  val ui: StateFlow<CatalogListUi> = combine(catalog.state, savedKeys, currentLanguage, filters) { state, saved, lang, current ->
    val all = state.entries.map { it.inLanguage(lang).toListItem() }
    CatalogListUi(
      items = filterCatalog(all, current.query, current.method, current.roast),
      total = all.size,
      methods = catalogMethods(all),
      query = current.query,
      method = current.method,
      roast = current.roast,
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
    filters.update { it.copy(query = text) }
  }

  fun setMethod(value: BrewMethod?) {
    filters.update { it.copy(method = value) }
  }

  fun setRoast(value: RoastLevel?) {
    filters.update { it.copy(roast = value) }
  }

  fun clearFilters() {
    filters.value = CatalogFilters()
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
