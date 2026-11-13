package dev.dotscale.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dotscale.history.BrewDetails
import dev.dotscale.history.BrewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BrewEditStatus { Loading, Ready, Saving, Saved, Gone }

class BrewEditViewModel(private val repository: BrewRepository, private val id: Long) : ViewModel() {
  private val _status = MutableStateFlow(BrewEditStatus.Loading)
  val status: StateFlow<BrewEditStatus> = _status.asStateFlow()

  private val _form = MutableStateFlow(BrewEditForm())
  val form: StateFlow<BrewEditForm> = _form.asStateFlow()

  /** El formulario tal como se cargó, para saber si hay cambios sin guardar. */
  private val _initial = MutableStateFlow(BrewEditForm())
  val initial: StateFlow<BrewEditForm> = _initial.asStateFlow()

  /** Hora de inicio de la preparación: es el placeholder del nombre. */
  private val _startedAtEpochMs = MutableStateFlow(0L)
  val startedAtEpochMs: StateFlow<Long> = _startedAtEpochMs.asStateFlow()

  /** Cafés de las demás preparaciones, para las sugerencias. */
  private val _coffees = MutableStateFlow<List<String>>(emptyList())
  val coffees: StateFlow<List<String>> = _coffees.asStateFlow()

  init {
    viewModelScope.launch {
      val entity = repository.get(id)?.entity
      if (entity == null) {
        _status.value = BrewEditStatus.Gone
        return@launch
      }
      val loaded = BrewEditForm.from(
        BrewDetails(entity.name, entity.coffee, entity.method, entity.methodOther, entity.grind, entity.waterTempC, entity.notes),
      )
      _initial.value = loaded
      _form.value = loaded
      _startedAtEpochMs.value = entity.startedAtEpochMs
      _coffees.value = repository.coffeesExcept(id)
      _status.value = BrewEditStatus.Ready
    }
  }

  fun update(transform: (BrewEditForm) -> BrewEditForm) = _form.update(transform)

  fun save() {
    val current = _form.value
    if (_status.value != BrewEditStatus.Ready || !current.isValid) return
    _status.value = BrewEditStatus.Saving
    viewModelScope.launch {
      repository.updateDetails(id, current.toDetails())
      _status.value = BrewEditStatus.Saved
    }
  }
}
