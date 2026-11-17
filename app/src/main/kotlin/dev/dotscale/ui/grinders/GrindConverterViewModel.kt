package dev.dotscale.ui.grinders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dotscale.grinder.ConverterRow
import dev.dotscale.grinder.Grinder
import dev.dotscale.grinder.GrinderRepository
import dev.dotscale.grinder.converterRows
import dev.dotscale.history.BrewMethod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** Lo que muestra el convertidor suelto (D26). [source] es `null` hasta que se elige un origen. */
data class GrindConverterUi(
  val source: Grinder?,
  val method: BrewMethod?,
  val value: Double?,
  /** Mis molinos: determina el estado vacío. */
  val owned: List<Grinder>,
  /** Mis molinos y el catálogo sin retirados, para el selector de origen. */
  val pickerOwned: List<Grinder>,
  val pickerCatalog: List<Grinder>,
  /** Una fila por cada molino mío distinto del origen; vacía sin origen o sin ajuste. */
  val rows: List<ConverterRow>,
)

/** El convertidor suelto: parte de los argumentos de la clave y se puede cambiar todo (D26). */
@OptIn(ExperimentalCoroutinesApi::class)
class GrindConverterViewModel(
  repository: GrinderRepository,
  sourceId: Long?,
  method: BrewMethod?,
  value: Double?,
) : ViewModel() {
  private val sourceId = MutableStateFlow(sourceId)
  private val method = MutableStateFlow(method)
  private val value = MutableStateFlow(value)

  val ui: StateFlow<GrindConverterUi?> = combine(
    this.sourceId.flatMapLatest { repository.grinder(it) },
    this.method,
    this.value,
    repository.owned(),
    repository.catalog(),
  ) { source, method, value, owned, catalog ->
    val rows = if (source != null && value != null) converterRows(source, method, value, owned) else emptyList()
    GrindConverterUi(
      source = source,
      method = method,
      value = value,
      owned = owned,
      pickerOwned = owned.filterNot { it.retired },
      pickerCatalog = catalog.filterNot { it.retired },
      rows = rows,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /** Cambiar el origen deja el ajuste en `null`: un valor de otro molino no tiene sentido en el nuevo. */
  fun setSource(id: Long) {
    if (sourceId.value == id) return
    sourceId.value = id
    value.value = null
  }

  fun setMethod(method: BrewMethod?) {
    this.method.value = method
  }

  fun setValue(value: Double) {
    this.value.value = value
  }
}
