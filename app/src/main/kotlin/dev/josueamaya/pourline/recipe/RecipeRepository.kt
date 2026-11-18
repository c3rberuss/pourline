package dev.josueamaya.pourline.recipe

import dev.josueamaya.pourline.grinder.toGrindSetting
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.history.HistoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

data class RecipeListItem(
  val id: Long,
  val name: String,
  val method: BrewMethod?,
  val methodOther: String?,
  val doseG: Double,
  val totalWaterG: Double,
  /** Todos los pasos de la receta, de cualquier tipo (verter, remover, esperar y acción), no solo los vertidos. */
  val stageCount: Int,
  val builtIn: Boolean,
  /** El fin del último paso, como [Recipe.totalDurationMs]. */
  val totalDurationMs: Long,
)

/**
 * Recetas. Singleton en `PourlineApp`.
 *
 * Mientras `localizedNames` sea `true`, el nombre de la receta y los de sus etapas salen de [texts] en el
 * idioma actual y no de las columnas (que guardan el texto de la inserción como respaldo).
 */
class RecipeRepository(
  db: HistoryDatabase,
  private val texts: RecipeTexts,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  private val dao = db.recipeDao()

  private val _pendingUndo = MutableStateFlow<DeletedRecipe?>(null)

  /** Último borrado que se puede deshacer. */
  val pendingUndo: StateFlow<DeletedRecipe?> = _pendingUndo.asStateFlow()

  /** Por nombre mostrado, sin distinguir mayúsculas ni acentos, y después por id. */
  fun list(): Flow<List<RecipeListItem>> = dao.observeAll()
    .map { all ->
      val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }
      all.map { it to displayName(it) }
        .sortedWith { (a, nameA), (b, nameB) ->
          collator.compare(nameA, nameB).takeIf { it != 0 } ?: a.recipe.id.compareTo(b.recipe.id)
        }
        .map { (with, name) ->
          val recipe = with.recipe
          RecipeListItem(
            id = recipe.id,
            name = name,
            method = recipe.method,
            methodOther = recipe.methodOther,
            doseG = recipe.doseG,
            totalWaterG = with.stages.filter { it.type == StageType.POUR }.maxByOrNull { it.position }?.targetWaterG ?: 0.0,
            stageCount = with.stages.size,
            builtIn = recipe.builtInKey != null,
            totalDurationMs = with.stages.maxOfOrNull { it.startMs + it.durationMs } ?: 0L,
          )
        }
    }
    .flowOn(Dispatchers.Default)

  /** Todas las recetas con los nombres mostrados, para comparar con una importada. */
  suspend fun all(): List<Recipe> = withContext(Dispatchers.IO) { dao.observeAll().first().map(::toRecipe) }

  /** Emite `null` si la receta desaparece. */
  fun observe(id: Long): Flow<Recipe?> = dao.observe(id).map { it?.let(::toRecipe) }.flowOn(Dispatchers.Default)

  suspend fun get(id: Long): Recipe? = withContext(Dispatchers.IO) { dao.get(id)?.let(::toRecipe) }

  /**
   * Inserta (`id = 0`) o reemplaza la receta y sus etapas en una transacción, y actualiza
   * `updatedAtEpochMs`. Devuelve el id. Las etapas se guardan con los nombres que lleva la [recipe]
   * (los mostrados).
   */
  suspend fun save(recipe: Recipe): Long = withContext(Dispatchers.IO + NonCancellable) {
    dao.save(toEntity(recipe), toStageEntities(recipe))
  }

  /** Copia "<nombre> (copia)" sin `builtInKey` ni nombres traducidos. `null` si la receta ya no existe. */
  suspend fun duplicate(id: Long): Long? {
    val original = get(id) ?: return null
    return save(original.copy(id = 0, name = texts.copyName(original.name), builtInKey = null, localizedNames = false))
  }

  /** Borra la receta y la deja en [pendingUndo]. */
  suspend fun delete(id: Long) {
    withContext(Dispatchers.IO + NonCancellable) {
      dao.deleteRecipe(id)?.let { _pendingUndo.value = it }
    }
  }

  /** Reinserta con el mismo id y vacía [pendingUndo] si sigue siendo el mismo borrado. */
  suspend fun restore(deleted: DeletedRecipe) {
    withContext(Dispatchers.IO + NonCancellable) {
      dao.restore(deleted)
      _pendingUndo.compareAndSet(deleted, null)
    }
  }

  /** Vacía [pendingUndo]. Con [expected], solo si sigue siendo ese borrado. */
  fun clearPendingUndo(expected: DeletedRecipe? = null) {
    if (expected == null) _pendingUndo.value = null else _pendingUndo.compareAndSet(expected, null)
  }

  /** Devuelve las predefinidas a fábrica (con `localizedNames = true`) y crea las borradas. No toca las del usuario. */
  suspend fun restoreBuiltIns() {
    withContext(Dispatchers.IO + NonCancellable) {
      dao.restoreBuiltIns(BuiltInRecipes.all(texts).map { toEntity(it) to toStageEntities(it) })
    }
  }

  private fun displayName(with: RecipeWithStages): String = with.recipe.let {
    if (it.localizedNames && it.builtInKey != null) texts.builtInName(it.builtInKey) else it.name
  }

  private fun toRecipe(with: RecipeWithStages): Recipe {
    val entity = with.recipe
    val translated = entity.localizedNames && entity.builtInKey != null
    val stageNames = if (translated) texts.builtInStageNames(entity.builtInKey!!) else emptyList()
    val stageInstructions = if (translated) texts.builtInStageInstructions(entity.builtInKey!!) else emptyList()
    return Recipe(
      id = entity.id,
      name = displayName(with),
      method = entity.method,
      methodOther = entity.methodOther,
      grind = entity.toGrindSetting(),
      waterTempC = entity.waterTempC,
      notes = entity.notes,
      doseG = entity.doseG,
      stages = with.stages.sortedBy { it.position }.mapIndexed { i, stage ->
        RecipeStage(
          name = if (translated) stageNames.getOrNull(i) ?: stage.name else stage.name,
          startMs = stage.startMs,
          targetWaterG = stage.targetWaterG,
          durationMs = stage.durationMs,
          type = stage.type,
          instruction = if (translated) stageInstructions.getOrNull(i) ?: stage.instruction else stage.instruction,
        )
      },
      builtInKey = entity.builtInKey,
      localizedNames = entity.localizedNames,
    )
  }

  private fun toEntity(recipe: Recipe) = RecipeEntity(
    id = recipe.id,
    name = recipe.name.trim(),
    method = recipe.method,
    methodOther = if (recipe.method == BrewMethod.OTHER) recipe.methodOther.cleaned() else null,
    grinderId = recipe.grind?.grinderId,
    grindValue = recipe.grind?.value,
    grindLabel = recipe.grind?.label.cleaned(),
    waterTempC = recipe.waterTempC,
    notes = recipe.notes.cleaned(),
    doseG = recipe.doseG,
    builtInKey = recipe.builtInKey,
    localizedNames = recipe.localizedNames,
    updatedAtEpochMs = clock(),
  )

  private fun toStageEntities(recipe: Recipe) = recipe.stages.withDerivedWater().mapIndexed { i, stage ->
    RecipeStageEntity(
      recipeId = recipe.id,
      position = i,
      name = stage.name.cleaned(),
      startMs = stage.startMs,
      targetWaterG = stage.targetWaterG,
      durationMs = stage.durationMs,
      type = stage.type,
      instruction = stage.instruction.cleaned(),
    )
  }
}

private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
