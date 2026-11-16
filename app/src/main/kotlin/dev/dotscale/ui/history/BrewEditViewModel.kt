package dev.dotscale.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dotscale.coffee.CoffeeEntity
import dev.dotscale.coffee.CoffeeListItem
import dev.dotscale.coffee.CoffeeRepository
import dev.dotscale.coffee.PickedCoffee
import dev.dotscale.grinder.GrindTexts
import dev.dotscale.grinder.Grinder
import dev.dotscale.grinder.GrinderRepository
import dev.dotscale.grinder.liveGrinderId
import dev.dotscale.grinder.toGrindSetting
import dev.dotscale.grinder.toSetting
import dev.dotscale.history.BrewDetails
import dev.dotscale.history.BrewEntity
import dev.dotscale.history.BrewRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BrewEditStatus { Loading, Ready, Saving, Saved, Gone }

class BrewEditViewModel(
  private val repository: BrewRepository,
  private val coffees: CoffeeRepository,
  private val grinderRepository: GrinderRepository,
  private val grindTexts: GrindTexts,
  private val id: Long,
) : ViewModel() {
  private val _status = MutableStateFlow(BrewEditStatus.Loading)
  val status: StateFlow<BrewEditStatus> = _status.asStateFlow()

  private val _form = MutableStateFlow(BrewEditForm())
  val form: StateFlow<BrewEditForm> = _form.asStateFlow()

  /** El formulario tal como se cargó, para saber si hay cambios sin guardar. */
  private val _initial = MutableStateFlow(BrewEditForm())
  val initial: StateFlow<BrewEditForm> = _initial.asStateFlow()

  /** Hora de inicio de la preparación: es el placeholder del nombre y el día de tueste se cuenta hasta ella. */
  private val _startedAtEpochMs = MutableStateFlow(0L)
  val startedAtEpochMs: StateFlow<Long> = _startedAtEpochMs.asStateFlow()

  /** Mis molinos, para el selector. */
  val grinders: StateFlow<List<Grinder>> = grinderRepository.owned()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  /** El molino vivo de la elección, aunque ya no sea de mis molinos; `null` sin molino o si se borró. */
  @OptIn(ExperimentalCoroutinesApi::class)
  val currentGrinder: StateFlow<Grinder?> = _form.map { it.grind.liveGrinderId }.distinctUntilChanged()
    .flatMapLatest { grinderRepository.grinder(it) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  private val _linkedArchived = MutableStateFlow<CoffeeListItem?>(null)

  /** El café vinculado al abrir si está archivado: el selector lo ofrece aunque ya no esté entre los elegibles. */
  val linkedArchived: StateFlow<CoffeeListItem?> = _linkedArchived.asStateFlow()

  /** `true` entre "Nuevo café" y el alta que se elige sola (D23). */
  private var awaitingNewCoffee = false

  init {
    viewModelScope.launch {
      val entity = repository.get(id)?.entity
      if (entity == null) {
        _status.value = BrewEditStatus.Gone
        return@launch
      }
      val linked = entity.coffeeId?.let { coffees.get(it) }
      val choice = choiceOf(entity, linked)
      val loaded = BrewEditForm.from(
        BrewDetails(entity.name, null, entity.method, entity.methodOther, entity.toGrindSetting(), entity.waterTempC, entity.notes, entity.rating),
        choice,
      )
      _initial.value = loaded
      _form.value = loaded
      _startedAtEpochMs.value = entity.startedAtEpochMs
      if (linked?.archived == true) {
        _linkedArchived.value = coffees.coffees(query = "", archived = true).first().firstOrNull { it.id == linked.id }
      }
      _status.value = BrewEditStatus.Ready
    }
    viewModelScope.launch {
      coffees.created.collect { createdId ->
        if (!awaitingNewCoffee) return@collect
        awaitingNewCoffee = false
        selectCoffee(createdId)
      }
    }
  }

  fun update(transform: (BrewEditForm) -> BrewEditForm) = _form.update(transform)

  /** Elige un café del catálogo. */
  fun selectCoffee(coffeeId: Long) {
    viewModelScope.launch {
      val entity = coffees.get(coffeeId) ?: return@launch
      _form.update { it.copy(coffee = CoffeeChoice.Linked(entity.toPicked())) }
    }
  }

  /** "Sin café": quita el vínculo y el nombre. */
  fun clearCoffee() = _form.update { it.copy(coffee = CoffeeChoice.None) }

  /** "Nuevo café": el próximo alta se elige solo al volver (D23). */
  fun requestNewCoffee() {
    awaitingNewCoffee = true
  }

  fun save() {
    val current = _form.value
    if (_status.value != BrewEditStatus.Ready || !current.isValid) return
    _status.value = BrewEditStatus.Saving
    val initialChoice = _initial.value.coffee
    viewModelScope.launch {
      // La copia de texto del ajuste se regenera con el molino vivo (D17).
      val grinder = current.grind.liveGrinderId?.let { grinderRepository.get(it) }
      repository.updateDetails(id, current.toDetails().copy(grind = current.grind.toSetting(grinder, grindTexts)).normalized())
      if (current.coffee.needsLinkWrite(initialChoice)) repository.setCoffee(id, current.coffee.linkedId)
      _status.value = BrewEditStatus.Saved
    }
  }

  private fun choiceOf(entity: BrewEntity, linked: CoffeeEntity?): CoffeeChoice = when {
    linked != null -> CoffeeChoice.Linked(linked.toPicked())
    entity.coffee != null -> CoffeeChoice.Legacy(entity.coffee)
    else -> CoffeeChoice.None
  }

  private fun CoffeeEntity.toPicked() = PickedCoffee(id, name, roaster, roastDate)
}
