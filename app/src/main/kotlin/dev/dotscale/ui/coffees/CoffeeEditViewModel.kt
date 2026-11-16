package dev.dotscale.ui.coffees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dotscale.coffee.CoffeeDraft
import dev.dotscale.coffee.CoffeeError
import dev.dotscale.coffee.CoffeeRepository
import dev.dotscale.coffee.CoffeeSuggestions
import dev.dotscale.coffee.canonicalize
import dev.dotscale.coffee.label.LabelOcr
import dev.dotscale.coffee.label.LabelParser
import dev.dotscale.coffee.newBagFrom
import dev.dotscale.coffee.photo.CoffeePhotoStore
import dev.dotscale.coffee.photo.PhotoImportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.time.LocalDate

sealed interface CoffeeEditState {
  data object Loading : CoffeeEditState

  data object Ready : CoffeeEditState

  data object Saving : CoffeeEditState

  data class Saved(val id: Long) : CoffeeEditState

  /** El café que se editaba ya no existe: la pantalla vuelve atrás. */
  data object Gone : CoffeeEditState
}

sealed interface CoffeeEditEvent {
  /** La lectura de la etiqueta rellenó [count] campos. */
  data class OcrFilled(val count: Int) : CoffeeEditEvent

  /** La lectura no rellenó nada o falló. */
  data object OcrEmpty : CoffeeEditEvent

  data object PhotoFailed : CoffeeEditEvent
}

/**
 * Editor del café (alta, edición y "Nueva bolsa"). Con [fromCoffeeId], el formulario parte de [newBagFrom]. Las
 * fotos nuevas se escriben al añadirlas; las sustituidas o quitadas del café se borran al guardar (repositorio) y
 * las que nunca se guardaron, al guardar, al descartar o en [onCleared] (D26).
 */
class CoffeeEditViewModel(
  private val repository: CoffeeRepository,
  private val photos: CoffeePhotoStore,
  private val ocr: LabelOcr,
  private val id: Long?,
  private val fromCoffeeId: Long?,
  private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
  private val _state = MutableStateFlow<CoffeeEditState>(CoffeeEditState.Loading)
  val state: StateFlow<CoffeeEditState> = _state.asStateFlow()

  private val _form = MutableStateFlow(CoffeeForm())
  val form: StateFlow<CoffeeForm> = _form.asStateFlow()

  /** El formulario tal como se cargó, para saber si hay cambios sin guardar. */
  private val _initial = MutableStateFlow(CoffeeForm())
  val initial: StateFlow<CoffeeForm> = _initial.asStateFlow()

  val suggestions: StateFlow<CoffeeSuggestions> =
    repository.suggestions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CoffeeSuggestions())

  /** Huecos de foto (1 o 2) que se están reduciendo y guardando. */
  private val _photoBusy = MutableStateFlow<Set<Int>>(emptySet())
  val photoBusy: StateFlow<Set<Int>> = _photoBusy.asStateFlow()

  private val _ocrRunning = MutableStateFlow(false)
  val ocrRunning: StateFlow<Boolean> = _ocrRunning.asStateFlow()

  /** Campos que rellenó la última lectura con resultados (el aviso "Se leyeron N campos"); `null` sin aviso. */
  private val _ocrAlert = MutableStateFlow<Int?>(null)
  val ocrAlert: StateFlow<Int?> = _ocrAlert.asStateFlow()

  private val _events = Channel<CoffeeEditEvent>(Channel.BUFFERED)
  val events: Flow<CoffeeEditEvent> = _events.receiveAsFlow()

  /** `true` en "Nueva bolsa": al guardar, la pantalla enseña la bolsa nueva. */
  val isNewBag: Boolean get() = fromCoffeeId != null

  /** Fotos escritas en esta sesión de edición: si no llegan a guardarse, se borran. */
  private val addedPhotos = mutableSetOf<String>()
  private val ocrLock = Mutex()
  private var finished = false

  init {
    viewModelScope.launch {
      val source = id ?: fromCoffeeId
      val draft = if (source == null) {
        CoffeeDraft()
      } else {
        val entity = repository.get(source)
        if (entity == null) {
          _state.value = CoffeeEditState.Gone
          return@launch
        }
        if (id != null) entity.toDraft() else newBagFrom(entity.toDraft())
      }
      val loaded = CoffeeForm.from(draft)
      _initial.value = loaded
      _form.value = loaded
      _state.value = CoffeeEditState.Ready
    }
  }

  /** Errores de validación del formulario actual, por campo. */
  fun errors(): Map<CoffeeField, CoffeeError> = _form.value.errors(today())

  /** Una edición del usuario sobre [field]: aplica [transform] y le quita la marca del OCR. */
  fun update(field: CoffeeField, transform: CoffeeForm.() -> CoffeeForm) = _form.update { it.edit(field, transform) }

  /** Añade una variedad (recortada, sin repetir sin distinguir mayúsculas) y vacía el texto pendiente. */
  fun addVariety(text: String) {
    val clean = text.trim()
    _form.update { current ->
      val added = if (clean.isEmpty() || current.varieties.any { it.equals(clean, ignoreCase = true) }) {
        current.varieties
      } else {
        current.varieties + clean
      }
      current.edit(CoffeeField.VARIETIES) { copy(varieties = added, varietyText = "") }
    }
  }

  fun removeVariety(text: String) {
    _form.update { current -> current.edit(CoffeeField.VARIETIES) { copy(varieties = varieties - text) } }
  }

  /** Importa la foto que abre [open] en el hueco [slot] (1 o 2) y, si va bien, lee la etiqueta. */
  fun addPhoto(slot: Int, open: () -> InputStream) {
    require(slot == 1 || slot == 2) { "slot debe ser 1 o 2" }
    viewModelScope.launch {
      _photoBusy.update { it + slot }
      try {
        val name = photos.import(open)
        addedPhotos += name
        replacePhoto(slot, name)
      } catch (e: PhotoImportException) {
        _events.trySend(CoffeeEditEvent.PhotoFailed)
        return@launch
      } finally {
        _photoBusy.update { it - slot }
      }
      readLabel()
    }
  }

  fun removePhoto(slot: Int) {
    require(slot == 1 || slot == 2) { "slot debe ser 1 o 2" }
    replacePhoto(slot, null)
  }

  private fun replacePhoto(slot: Int, name: String?) {
    val old = if (slot == 1) _form.value.photo1 else _form.value.photo2
    _form.update { if (slot == 1) it.copy(photo1 = name) else it.copy(photo2 = name) }
    // Una foto de esta sesión que se sustituye nunca se guardará: se borra ya. Las del café, al guardar.
    if (old != null && old != name && addedPhotos.remove(old)) photos.delete(listOf(old))
  }

  /** Lee la foto 1 y luego la 2: lo ya escrito no se pisa, así que manda la primera (§3.3 del spec). */
  fun readLabel() {
    viewModelScope.launch {
      ocrLock.withLock {
        val names = listOfNotNull(_form.value.photo1, _form.value.photo2)
        if (names.isEmpty()) return@withLock
        _ocrRunning.value = true
        try {
          var total = 0
          for (name in names) {
            try {
              val fields = LabelParser.parse(ocr.read(photos.file(name)), today())
              _form.update { current -> current.applyOcr(fields).also { total += it.filled.size }.form }
            } catch (e: CancellationException) {
              throw e
            } catch (_: Exception) {
              // Una foto que el motor no pudo leer cuenta como sin resultados.
            }
          }
          if (total > 0) {
            _ocrAlert.value = total
            _events.trySend(CoffeeEditEvent.OcrFilled(total))
          } else {
            _events.trySend(CoffeeEditEvent.OcrEmpty)
          }
        } finally {
          _ocrRunning.value = false
        }
      }
    }
  }

  fun dismissOcrAlert() {
    _ocrAlert.value = null
  }

  fun save() {
    val current = _form.value
    if (_state.value != CoffeeEditState.Ready || current.errors(today()).isNotEmpty()) return
    _state.value = CoffeeEditState.Saving
    viewModelScope.launch {
      val draft = canonicalize(current.toDraft(), repository.suggestions().first())
      val savedId = repository.save(draft)
      finished = true
      val kept = listOfNotNull(draft.photo1, draft.photo2).toSet()
      val unused = addedPhotos - kept
      if (unused.isNotEmpty()) photos.delete(unused)
      addedPhotos.clear()
      _state.value = CoffeeEditState.Saved(savedId)
    }
  }

  /** Salir sin guardar: borra las fotos nuevas que no se guardaron. */
  fun discard() {
    if (finished) return
    finished = true
    if (addedPhotos.isNotEmpty()) photos.delete(addedPhotos.toSet())
    addedPhotos.clear()
  }

  override fun onCleared() {
    discard()
  }

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
