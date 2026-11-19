package dev.josueamaya.pourline.ui.grinders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josueamaya.pourline.grinder.ConverterRow
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.grinder.GrinderRepository
import dev.josueamaya.pourline.grinder.converterRows
import dev.josueamaya.pourline.grinder.valueOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** Lo que muestra el convertidor suelto (D26). [source] es `null` hasta que se elige un origen. */
data class GrindConverterUi(
  val source: Grinder?,
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
  value: Double?,
) : ViewModel() {
  private val sourceId = MutableStateFlow(sourceId)
  private val value = MutableStateFlow(value)

  /** La hoja de "Corregir" y "Añadir equivalencia" (D53). */
  val editor = CalibrationEditor(repository, viewModelScope)

  val ui: StateFlow<GrindConverterUi?> = combine(
    this.sourceId.flatMapLatest { repository.grinder(it) },
    this.value,
    repository.owned(),
    repository.catalog(),
    this.sourceId.flatMapLatest { id -> id?.let { repository.calibrations(it) } ?: flowOf(emptyList()) },
  ) { source, value, owned, catalog, pairs ->
    val rows = if (source != null && value != null) converterRows(source, value, owned, pairs) else emptyList()
    GrindConverterUi(
      source = source,
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

  fun setValue(value: Double) {
    this.value.value = value
  }

  /**
   * "Corregir" o "Añadir equivalencia" en la fila [row] (D52): abre la hoja con el molino y el ajuste de origen fijos y
   * el molino de la fila; el ajuste del otro parte del valor mostrado o, sin equivalencia, del centro de su escala.
   */
  fun correct(row: ConverterRow) {
    val source = ui.value?.source ?: return
    val value = ui.value?.value ?: return
    editor.open(
      CalibrationDraft(
        grinderId = source.id,
        value = value,
        otherId = row.grinder.id,
        otherValue = row.conversion.valueOrNull ?: startValue(row.grinder, null),
        lockOther = true,
      ),
    )
  }
}
