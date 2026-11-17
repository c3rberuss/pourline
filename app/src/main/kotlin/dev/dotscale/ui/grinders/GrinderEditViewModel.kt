package dev.dotscale.ui.grinders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dotscale.grinder.Grinder
import dev.dotscale.grinder.GrinderRepository
import dev.dotscale.grinder.InvalidGrinderException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface GrinderEditStatus {
  data object Loading : GrinderEditStatus

  data object Ready : GrinderEditStatus

  data object Saving : GrinderEditStatus

  /** Guardado: la pantalla vuelve atrás. */
  data object Saved : GrinderEditStatus

  /** El molino no existe o no se puede editar (un predefinido): la pantalla vuelve atrás. */
  data object Gone : GrinderEditStatus
}

/**
 * El editor de molinos, con tres modos: crear (`id` y `fromId` nulos), editar un molino propio (`id`) y duplicar
 * (`fromId`, con una copia en memoria que no se guarda hasta pulsar "Guardar", D22).
 */
class GrinderEditViewModel(
  private val repository: GrinderRepository,
  private val id: Long?,
  private val fromId: Long?,
) : ViewModel() {
  private val _status = MutableStateFlow<GrinderEditStatus>(GrinderEditStatus.Loading)
  val status: StateFlow<GrinderEditStatus> = _status.asStateFlow()

  private val _form = MutableStateFlow(GrinderEditForm())
  val form: StateFlow<GrinderEditForm> = _form.asStateFlow()

  /** El formulario tal como se cargó, para saber si hay cambios sin guardar. */
  private val _initial = MutableStateFlow(GrinderEditForm())
  val initial: StateFlow<GrinderEditForm> = _initial.asStateFlow()

  val isExisting: Boolean = id != null

  /** Lo que el formulario no edita (clave, duplicado de, "tengo"…); `null` en un molino nuevo. */
  private var base: Grinder? = null

  init {
    viewModelScope.launch { load() }
  }

  private suspend fun load() {
    val source = when {
      id != null -> repository.get(id)?.takeUnless { it.isPredefined }
      fromId != null -> repository.duplicateDraft(fromId)
      else -> null
    }
    if ((id != null || fromId != null) && source == null) {
      _status.value = GrinderEditStatus.Gone
      return
    }
    base = source
    val form = source?.let(GrinderEditForm::from) ?: GrinderEditForm()
    _initial.value = form
    _form.value = form
    _status.value = GrinderEditStatus.Ready
  }

  fun update(transform: (GrinderEditForm) -> GrinderEditForm) = _form.update(transform)

  fun save() {
    if (_status.value != GrinderEditStatus.Ready) return
    val grinder = _form.value.toGrinder(id = id ?: 0, base = base) ?: return
    _status.value = GrinderEditStatus.Saving
    viewModelScope.launch {
      _status.value = try {
        repository.save(grinder)
        GrinderEditStatus.Saved
      } catch (_: InvalidGrinderException) {
        GrinderEditStatus.Ready
      }
    }
  }
}
