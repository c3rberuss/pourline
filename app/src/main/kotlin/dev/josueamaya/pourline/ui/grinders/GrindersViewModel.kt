package dev.josueamaya.pourline.ui.grinders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josueamaya.pourline.coffee.fold
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.grinder.GrinderRepository
import dev.josueamaya.pourline.settings.DefaultGrinderStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "Marca Modelo": el nombre con el que se muestra un molino en las listas. */
val Grinder.displayName: String get() = "$brand $model"

data class GrindersState(val owned: List<Grinder>, val defaultId: Long?)

/** Mis molinos y cuál es el predeterminado; `null` mientras carga. */
class GrindersViewModel(repository: GrinderRepository, defaults: DefaultGrinderStore) : ViewModel() {
  val state: StateFlow<GrindersState?> = combine(repository.owned(), defaults.defaultGrinderId, ::GrindersState)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class CatalogRow(val grinder: Grinder, val owned: Boolean)

/** El catálogo de predefinidos (sin los retirados), con la búsqueda por marca y modelo (D28). */
class GrinderCatalogViewModel(private val repository: GrinderRepository) : ViewModel() {
  private val query = MutableStateFlow("")

  val rows: StateFlow<List<CatalogRow>?> = combine(repository.catalog(), repository.owned(), query) { catalog, owned, q ->
    val ownedIds = owned.mapTo(HashSet()) { it.id }
    val needle = fold(q.trim())
    catalog
      .filter { needle.isEmpty() || fold(it.displayName).contains(needle) }
      .map { CatalogRow(it, it.id in ownedIds) }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  fun setQuery(text: String) {
    query.value = text
  }

  fun own(id: Long) {
    viewModelScope.launch { repository.setOwned(id, true) }
  }
}

sealed interface GrinderDetailUi {
  data object Loading : GrinderDetailUi

  /** El molino no existe (o se acaba de borrar): la pantalla vuelve atrás. */
  data object Gone : GrinderDetailUi

  data class Ready(val grinder: Grinder, val isDefault: Boolean, val recipeCount: Int) : GrinderDetailUi
}

class GrinderDetailViewModel(
  private val repository: GrinderRepository,
  private val defaults: DefaultGrinderStore,
  private val id: Long,
) : ViewModel() {
  val ui: StateFlow<GrinderDetailUi> = combine(repository.grinder(id), defaults.defaultGrinderId, repository.recipeCount(id)) { grinder, default, count ->
    if (grinder == null) GrinderDetailUi.Gone else GrinderDetailUi.Ready(grinder, default == grinder.id, count)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrinderDetailUi.Loading)

  fun setOwned(owned: Boolean) {
    viewModelScope.launch { repository.setOwned(id, owned) }
  }

  fun setDefault() = defaults.setDefaultGrinderId(id)

  fun delete() {
    viewModelScope.launch { repository.delete(id) }
  }
}
