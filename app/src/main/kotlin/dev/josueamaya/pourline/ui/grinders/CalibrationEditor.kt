package dev.josueamaya.pourline.ui.grinders

import dev.josueamaya.pourline.grinder.CalibrationSaveResult
import dev.josueamaya.pourline.grinder.GrindCalibration
import dev.josueamaya.pourline.grinder.GrinderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * El par que se está escribiendo en la hoja (D53): [value] en [grinderId] = [otherValue] en [otherId]. [editingId] es
 * el par que se edita (`null` si es nuevo) y [lockOther] fija el otro molino (el convertidor lo elige por la fila).
 * [conflicts] y [invalid] son el último rechazo del guardado; cambiar cualquier campo los limpia.
 */
data class CalibrationDraft(
  val grinderId: Long,
  val value: Double?,
  val otherId: Long?,
  val otherValue: Double?,
  val editingId: Long? = null,
  val lockOther: Boolean,
  val conflicts: List<GrindCalibration> = emptyList(),
  val invalid: Boolean = false,
) {
  /** Hay los dos valores y el otro molino: se puede guardar. */
  val complete: Boolean get() = value != null && otherId != null && otherValue != null
}

/** Lo que el editor avisa a la pantalla fuera del borrador. */
sealed interface CalibrationEvent {
  /** El par se guardó sobre uno que ya tenía el mismo valor: "Equivalencia actualizada". */
  data object Updated : CalibrationEvent
}

/**
 * La máquina de estados para guardar un par de calibración, compartida por el convertidor y el detalle del molino
 * (D53). `Saved` cierra la hoja sin mensaje; `Updated` la cierra y emite [CalibrationEvent.Updated]; `Conflict` la deja
 * abierta con los pares contradichos y la opción de reemplazarlos; `Invalid` la deja abierta con el aviso.
 */
class CalibrationEditor(private val repository: GrinderRepository, private val scope: CoroutineScope) {
  private val _draft = MutableStateFlow<CalibrationDraft?>(null)
  val draft: StateFlow<CalibrationDraft?> = _draft.asStateFlow()

  private val _events = Channel<CalibrationEvent>(Channel.BUFFERED)
  val events: Flow<CalibrationEvent> = _events.receiveAsFlow()

  private var saving = false

  fun open(draft: CalibrationDraft) {
    _draft.value = draft
  }

  fun update(transform: (CalibrationDraft) -> CalibrationDraft) {
    _draft.update { current -> current?.let { transform(it).copy(conflicts = emptyList(), invalid = false) } }
  }

  fun save() = submit(replace = false)

  /** Borra los pares que contradicen al nuevo y lo guarda (D53). */
  fun replace() = submit(replace = true)

  fun dismiss() {
    _draft.value = null
  }

  private fun submit(replace: Boolean) {
    val draft = _draft.value ?: return
    val value = draft.value
    val otherId = draft.otherId
    val otherValue = draft.otherValue
    if (saving || value == null || otherId == null || otherValue == null) return
    saving = true
    scope.launch {
      try {
        val result = if (replace) {
          repository.replaceCalibration(draft.grinderId, value, otherId, otherValue, draft.editingId)
        } else {
          repository.saveCalibration(draft.grinderId, value, otherId, otherValue, draft.editingId)
        }
        when (result) {
          is CalibrationSaveResult.Saved -> _draft.value = null
          is CalibrationSaveResult.Updated -> {
            _draft.value = null
            _events.trySend(CalibrationEvent.Updated)
          }
          is CalibrationSaveResult.Conflict -> _draft.update { it?.copy(conflicts = result.conflicts, invalid = false) }
          is CalibrationSaveResult.Invalid -> _draft.update { it?.copy(conflicts = emptyList(), invalid = true) }
        }
      } finally {
        saving = false
      }
    }
  }
}
