package dev.josueamaya.pourline.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josueamaya.pourline.grinder.GrindChoice
import dev.josueamaya.pourline.grinder.GrindTexts
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.grinder.GrinderRepository
import dev.josueamaya.pourline.grinder.liveGrinderId
import dev.josueamaya.pourline.grinder.toGrindSetting
import dev.josueamaya.pourline.grinder.toSetting
import dev.josueamaya.pourline.history.BrewDetails
import dev.josueamaya.pourline.history.BrewRepository
import dev.josueamaya.pourline.recipe.Adjustment
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeFromBrew
import dev.josueamaya.pourline.recipe.RecipeRepository
import dev.josueamaya.pourline.recipe.RecipeTexts
import dev.josueamaya.pourline.settings.DefaultGrinderStore
import dev.josueamaya.pourline.ui.history.formatBrewDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RecipeEditStatus {
  data object Loading : RecipeEditStatus

  data object Ready : RecipeEditStatus

  data object Saving : RecipeEditStatus

  /** Guardada: la pantalla vuelve atrás. */
  data object Saved : RecipeEditStatus

  /** Duplicada: la pantalla abre la copia en lugar de esta. */
  data class Duplicated(val id: Long) : RecipeEditStatus

  /** La receta (o la preparación de origen) ya no existe, o se acaba de borrar: la pantalla vuelve atrás. */
  data object Gone : RecipeEditStatus
}

/**
 * Editor de recetas, con cuatro modos: crear (`id`, `fromBrewId` y `draft` nulos), editar (`id`), crear desde una
 * preparación (`fromBrewId`, con [RecipeFromBrew]) y crear desde un borrador ya hecho (`draft`, el del generador
 * 4:6). Al crear no se guarda nada hasta pulsar "Guardar".
 */
class RecipeEditViewModel(
  private val repository: RecipeRepository,
  private val brewRepository: BrewRepository,
  private val texts: RecipeTexts,
  private val grinderRepository: GrinderRepository,
  private val defaultGrinder: DefaultGrinderStore,
  private val grindTexts: GrindTexts,
  private val id: Long?,
  private val fromBrewId: Long?,
  private val draft: Recipe? = null,
) : ViewModel() {
  private val _status = MutableStateFlow<RecipeEditStatus>(RecipeEditStatus.Loading)
  val status: StateFlow<RecipeEditStatus> = _status.asStateFlow()

  private val _form = MutableStateFlow(RecipeEditForm())
  val form: StateFlow<RecipeEditForm> = _form.asStateFlow()

  /** El formulario tal como se cargó, para saber si hay cambios sin guardar. */
  private val _initial = MutableStateFlow(RecipeEditForm())
  val initial: StateFlow<RecipeEditForm> = _initial.asStateFlow()

  /** Lo que se ajustó al convertir una preparación en receta; vacío en los otros modos. */
  private val _adjustments = MutableStateFlow<List<Adjustment>>(emptyList())
  val adjustments: StateFlow<List<Adjustment>> = _adjustments.asStateFlow()

  /** Mis molinos, para el selector. */
  val grinders: StateFlow<List<Grinder>> = grinderRepository.owned()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  /** El molino vivo de la elección, aunque ya no sea de mis molinos; `null` sin molino o si se borró. */
  @OptIn(ExperimentalCoroutinesApi::class)
  val currentGrinder: StateFlow<Grinder?> = _form.map { it.grind.liveGrinderId }.distinctUntilChanged()
    .flatMapLatest { grinderRepository.grinder(it) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /** Receta existente (se puede duplicar y borrar). */
  val isExisting: Boolean = id != null

  private var loaded: Recipe? = null

  init {
    viewModelScope.launch { load() }
  }

  private suspend fun load() {
    when {
      id != null -> {
        val recipe = repository.get(id)
        if (recipe == null) {
          _status.value = RecipeEditStatus.Gone
          return
        }
        loaded = recipe
        start(RecipeEditForm.from(recipe))
        // Si la receta desaparece mientras se edita (o se borra aquí), la pantalla vuelve atrás.
        viewModelScope.launch {
          repository.observe(id).collect { current ->
            if (current == null && _status.value.let { it == RecipeEditStatus.Ready || it == RecipeEditStatus.Saving }) {
              _status.value = RecipeEditStatus.Gone
            }
          }
        }
      }
      fromBrewId != null -> {
        val record = brewRepository.get(fromBrewId)
        if (record == null || record.curveError) {
          _status.value = RecipeEditStatus.Gone
          return
        }
        val entity = record.entity
        val base = entity.name ?: formatBrewDateTime(entity.startedAtEpochMs)
        val converted = RecipeFromBrew.convert(
          name = texts.fromBrewName(base),
          details = BrewDetails(
            method = entity.method,
            methodOther = entity.methodOther,
            grind = entity.toGrindSetting(),
            waterTempC = entity.waterTempC,
          ).normalized(),
          doseG = entity.doseG,
          samples = record.samples,
          pours = record.summary.pours,
          totalTimeMs = record.summary.totalTimeMs,
          totalWaterG = record.summary.totalWaterG,
        )
        _adjustments.value = converted.adjustments
        start(RecipeEditForm.from(converted))
      }
      draft != null -> {
        val form = RecipeEditForm.from(draft)
        start(if (draft.grind == null) form.copy(grind = preselectedGrind()) else form)
      }
      else -> start(RecipeEditForm(grind = preselectedGrind()))
    }
  }

  /** Una receta nueva preselecciona el molino predeterminado, sin ajuste: lo fija el primer toque (D21). */
  private suspend fun preselectedGrind(): GrindChoice {
    val preselected = defaultGrinder.defaultGrinderId.value?.let { grinderRepository.get(it) }
    return if (preselected != null) GrindChoice.Live(preselected.id, null) else GrindChoice.None
  }

  private fun start(form: RecipeEditForm) {
    _initial.value = form
    _form.value = form
    _status.value = RecipeEditStatus.Ready
  }

  fun update(transform: (RecipeEditForm) -> RecipeEditForm) = _form.update(transform)

  fun save() {
    val current = _form.value
    if (_status.value != RecipeEditStatus.Ready) return
    val keep = current.keepsLocalizedNames(_initial.value, wasLocalized = loaded?.localizedNames == true)
    val draft = current.toRecipe(id = id ?: 0, builtInKey = loaded?.builtInKey, localizedNames = keep) ?: return
    _status.value = RecipeEditStatus.Saving
    viewModelScope.launch {
      // La copia de texto del ajuste se regenera con el molino vivo (D17).
      val grinder = current.grind.liveGrinderId?.let { grinderRepository.get(it) }
      repository.save(draft.copy(grind = current.grind.toSetting(grinder, grindTexts)))
      _status.value = RecipeEditStatus.Saved
    }
  }

  /** Solo sin cambios sin guardar: la copia sale de lo guardado. */
  fun duplicate() {
    val recipeId = id ?: return
    if (_status.value != RecipeEditStatus.Ready || _form.value.isDirty(_initial.value)) return
    _status.value = RecipeEditStatus.Saving
    viewModelScope.launch {
      val copy = repository.duplicate(recipeId)
      _status.value = if (copy == null) RecipeEditStatus.Gone else RecipeEditStatus.Duplicated(copy)
    }
  }

  /** Borra sin confirmar: la lista ofrece "Deshacer". */
  fun delete() {
    val recipeId = id ?: return
    if (_status.value != RecipeEditStatus.Ready) return
    _status.value = RecipeEditStatus.Saving
    viewModelScope.launch {
      repository.delete(recipeId)
      _status.value = RecipeEditStatus.Gone
    }
  }
}
