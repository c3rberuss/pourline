package dev.josueamaya.pourline.ui.grinders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josueamaya.pourline.coffee.fold
import dev.josueamaya.pourline.grinder.GrindCalibration
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

  /**
   * [groups] son los pares del molino, uno por cada molino con el que está emparejado (D54); [others] son mis otros
   * molinos sin retirados, con los que se puede emparejar; [canAddCalibration] dice si se ofrece "Añadir equivalencia".
   */
  data class Ready(
    val grinder: Grinder,
    val isDefault: Boolean,
    val recipeCount: Int,
    val groups: List<CalibrationGroup>,
    val others: List<Grinder>,
    val canAddCalibration: Boolean,
  ) : GrinderDetailUi
}

/**
 * Los pares de [grinder] agrupados por el otro molino (en el orden de [all]) y ordenados por el ajuste de [grinder].
 * Un grupo es editable si los dos molinos son míos (D54); un par con un molino que ya no es mío solo se puede borrar.
 */
internal fun calibrationGroups(grinder: Grinder, pairs: List<GrindCalibration>, all: List<Grinder>): List<CalibrationGroup> {
  val byOther = pairs.groupBy { it.otherId(grinder.id) }
  return all.mapNotNull { other ->
    val group = byOther[other.id] ?: return@mapNotNull null
    CalibrationGroup(other, group.sortedBy { it.valueOf(grinder.id) }, editable = grinder.owned && other.owned)
  }
}

class GrinderDetailViewModel(
  private val repository: GrinderRepository,
  private val defaults: DefaultGrinderStore,
  private val id: Long,
) : ViewModel() {
  val ui: StateFlow<GrinderDetailUi> = combine(
    repository.grinder(id),
    defaults.defaultGrinderId,
    repository.recipeCount(id),
    repository.calibrations(id),
    repository.all(),
  ) { grinder, default, count, pairs, all ->
    if (grinder == null) {
      GrinderDetailUi.Gone
    } else {
      val others = all.filter { it.owned && !it.retired && it.id != grinder.id }
      GrinderDetailUi.Ready(
        grinder = grinder,
        isDefault = default == grinder.id,
        recipeCount = count,
        groups = calibrationGroups(grinder, pairs, all),
        others = others,
        canAddCalibration = grinder.owned && others.isNotEmpty(),
      )
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrinderDetailUi.Loading)

  /** La hoja de "Añadir equivalencia" y de editar un par (D53). */
  val editor = CalibrationEditor(repository, viewModelScope)

  fun setOwned(owned: Boolean) {
    viewModelScope.launch { repository.setOwned(id, owned) }
  }

  fun setDefault() = defaults.setDefaultGrinderId(id)

  fun delete() {
    viewModelScope.launch { repository.delete(id) }
  }

  /** Abre la hoja vacía; con un solo molino con el que emparejar, ya elegido. */
  fun addCalibration() {
    val ready = ui.value as? GrinderDetailUi.Ready ?: return
    editor.open(
      CalibrationDraft(
        grinderId = id,
        value = null,
        otherId = ready.others.singleOrNull()?.id,
        otherValue = null,
        lockOther = false,
      ),
    )
  }

  /** Abre la hoja con los valores de [pair]; el otro molino queda fijo. */
  fun editCalibration(pair: GrindCalibration) {
    val other = pair.otherId(id)
    editor.open(
      CalibrationDraft(
        grinderId = id,
        value = pair.valueOf(id),
        otherId = other,
        otherValue = pair.valueOf(other),
        editingId = pair.id,
        lockOther = true,
      ),
    )
  }

  fun deleteCalibration(pairId: Long) {
    viewModelScope.launch { repository.deleteCalibration(pairId) }
  }
}
