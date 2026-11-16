package dev.dotscale.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dotscale.grinder.toGrindSetting
import dev.dotscale.history.BrewDetails
import dev.dotscale.history.BrewRepository
import dev.dotscale.recipe.Adjustment
import dev.dotscale.recipe.Recipe
import dev.dotscale.recipe.RecipeFromBrew
import dev.dotscale.recipe.RecipeRepository
import dev.dotscale.recipe.RecipeTexts
import dev.dotscale.ui.history.formatBrewDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * Editor de recetas, con tres modos: crear (`id` y `fromBrewId` nulos), editar (`id`) y crear desde una
 * preparación (`fromBrewId`, con [RecipeFromBrew] y sin guardar nada hasta pulsar "Guardar").
 */
class RecipeEditViewModel(
  private val repository: RecipeRepository,
  private val brewRepository: BrewRepository,
  private val texts: RecipeTexts,
  private val id: Long?,
  private val fromBrewId: Long?,
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
        val draft = RecipeFromBrew.convert(
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
        _adjustments.value = draft.adjustments
        start(RecipeEditForm.from(draft))
      }
      else -> start(RecipeEditForm())
    }
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
    val recipe = current.toRecipe(id = id ?: 0, builtInKey = loaded?.builtInKey, localizedNames = keep) ?: return
    _status.value = RecipeEditStatus.Saving
    viewModelScope.launch {
      repository.save(recipe)
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
