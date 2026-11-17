package dev.josueamaya.pourline.history

import dev.josueamaya.pourline.brew.BrewConfig
import dev.josueamaya.pourline.brew.BrewSample
import dev.josueamaya.pourline.brew.BrewSummary
import dev.josueamaya.pourline.brew.FinishedBrew
import dev.josueamaya.pourline.brew.Pour
import dev.josueamaya.pourline.brew.StabilityLevel
import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.coffee.CoffeeOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.zip.DataFormatException

/** Historial de preparaciones. Singleton en `PourlineApp`. */
class BrewRepository(db: HistoryDatabase) : BrewSaver {
  private val dao = db.brewDao()

  private val _pendingUndo = MutableStateFlow<DeletedBrew?>(null)

  /** Último borrado que se puede deshacer. Vive aquí porque el detalle y la lista tienen ViewModels distintos. */
  val pendingUndo: StateFlow<DeletedBrew?> = _pendingUndo.asStateFlow()

  /** Guarda una preparación terminada y devuelve su id. Escribe `brews`, `brew_curves` y, con referencia, `brew_targets` en una transacción. */
  override suspend fun save(finished: FinishedBrew, context: BrewContext): Long {
    val summary = finished.summary
    val target = context.target
    val details = context.details?.normalized()
    val (curve, preview, targetData) = withContext(Dispatchers.Default) {
      Triple(
        BrewCurveCodec.encode(finished.samples, summary.pours),
        BrewPreview.compute(finished.samples, summary.totalTimeMs, summary.totalWaterG),
        target?.let { TargetCurveCodec.encode(it.curve) },
      )
    }
    val entity = BrewEntity(
      startedAtEpochMs = context.startedAtEpochMs,
      // Prellenado desde la referencia. El café ya es una grafía existente, así que no pasa por `canonicalCoffee`.
      name = details?.name,
      coffee = details?.coffee,
      method = details?.method,
      methodOther = details?.methodOther,
      grinderId = details?.grind?.grinderId,
      grindValue = details?.grind?.value,
      grindLabel = details?.grind?.label,
      waterTempC = details?.waterTempC,
      notes = details?.notes,
      rating = details?.rating,
      totalTimeMs = summary.totalTimeMs,
      totalWaterG = summary.totalWaterG,
      avgFlowGps = summary.avgFlowGps,
      maxFlowGps = summary.maxFlowGps,
      stabilityScore = summary.stabilityScore,
      stabilityLevel = summary.stabilityLevel,
      shareStable = summary.levelShare[StabilityLevel.STABLE] ?: 0.0,
      shareIrregular = summary.levelShare[StabilityLevel.IRREGULAR] ?: 0.0,
      shareTurbulent = summary.levelShare[StabilityLevel.TURBULENT] ?: 0.0,
      doseG = finished.doseG,
      finalRatio = summary.finalRatio,
      finishReason = summary.reason,
      interrupted = summary.interrupted,
      unit = context.unit,
      scaleModel = context.scaleModel,
      simulated = context.simulated,
      metricVersion = BrewConfig.METRIC_VERSION,
      preview = preview,
      referenceType = target?.type,
      referenceId = target?.id,
      referenceName = target?.name,
      targetWaterG = target?.fit?.targetWaterG,
      targetEndMs = target?.fit?.targetEndMs,
      fitScore = target?.fit?.score,
    )
    return withContext(Dispatchers.IO) { dao.insertBrew(entity, curve, targetData, context.coffee) }
  }

  /** Más reciente primero. */
  fun list(filter: BrewFilter): Flow<List<BrewListItem>> =
    dao.list(filter.coffeeId, filter.method)
      .map { rows -> rows.map(::toItem) }
      .flowOn(Dispatchers.Default)

  /** Solo preparaciones reales con puntuación, en orden cronológico. */
  fun progress(filter: BrewFilter): Flow<List<ProgressPoint>> = dao.progress(filter.coffeeId, filter.method)

  /** Cafés del catálogo con preparaciones vinculadas (también archivados), para el filtro (D33). */
  fun coffeeOptions(): Flow<List<CoffeeOption>> = dao.coffeeOptions()

  /** Métodos presentes, en el orden del enum. */
  fun methods(): Flow<List<BrewMethod>> = dao.methods().map { present -> BrewMethod.entries.filter { it in present } }

  suspend fun get(id: Long): BrewRecord? = withContext(Dispatchers.IO) {
    val entity = dao.brew(id) ?: return@withContext null
    val loaded = load(id)
    toRecord(entity, loaded.curve, loaded.target)
  }

  /**
   * Observa la fila y emite `null` si desaparece. La curva se decodifica una sola vez por observación,
   * aunque la fila cambie (p. ej. al editar los datos manuales).
   */
  fun observe(id: Long): Flow<BrewRecord?> = flow {
    var curve: Loaded? = null
    dao.observeBrew(id).collect { entity ->
      if (entity == null) {
        curve = null
        emit(null)
      } else {
        val loaded = curve ?: load(id).also { curve = it }
        emit(toRecord(entity, loaded.curve, loaded.target))
      }
    }
  }.flowOn(Dispatchers.IO)

  /** Número de preparaciones guardadas. */
  fun count(): Flow<Int> = dao.count()

  // Las escrituras siguientes no se cancelan: salir de la pantalla no puede cortarlas.

  suspend fun updateDetails(id: Long, details: BrewDetails) {
    withContext(Dispatchers.IO + NonCancellable) { dao.applyDetails(id, details) }
  }

  /** Vincula la preparación a un café, o la desvincula con `null` (quita también el nombre). Actualiza `lastUsedAtEpochMs`. */
  suspend fun setCoffee(brewId: Long, coffeeId: Long?) {
    withContext(Dispatchers.IO + NonCancellable) { dao.linkCoffee(brewId, coffeeId) }
  }

  /** Borra la preparación y deja la fila y la curva en [pendingUndo]. */
  suspend fun delete(id: Long) {
    withContext(Dispatchers.IO + NonCancellable) {
      dao.deleteBrew(id)?.let { _pendingUndo.value = it }
    }
  }

  /** Reinserta con el mismo id y vacía [pendingUndo] si sigue siendo el mismo borrado. */
  suspend fun restore(deleted: DeletedBrew) {
    withContext(Dispatchers.IO + NonCancellable) {
      dao.restore(deleted)
      _pendingUndo.compareAndSet(deleted, null)
    }
  }

  /** Vacía [pendingUndo]. Con [expected], solo si sigue siendo ese borrado (un aviso que expira no debe borrar otro posterior). */
  fun clearPendingUndo(expected: DeletedBrew? = null) {
    if (expected == null) _pendingUndo.value = null else _pendingUndo.compareAndSet(expected, null)
  }

  private class Curve(val samples: List<BrewSample>, val pours: List<Pour>, val error: Boolean)

  /** La copia de la curva objetivo: `curve == null` y `error == false` si la preparación no tiene referencia. */
  private class Target(val curve: TargetCurve?, val error: Boolean)

  private class Loaded(val curve: Curve, val target: Target)

  private suspend fun load(id: Long): Loaded = Loaded(loadCurve(id), loadTarget(id))

  private suspend fun loadTarget(id: Long): Target {
    val bytes = dao.target(id) ?: return Target(null, error = false)
    return withContext(Dispatchers.Default) {
      try {
        Target(TargetCurveCodec.decode(bytes), error = false)
      } catch (_: TargetCurveFormatException) {
        Target(null, error = true)
      } catch (_: DataFormatException) {
        Target(null, error = true)
      }
    }
  }

  private suspend fun loadCurve(id: Long): Curve {
    val bytes = dao.curve(id)
    if (bytes == null || bytes.isEmpty()) return Curve(emptyList(), emptyList(), error = true)
    return withContext(Dispatchers.Default) {
      try {
        val (samples, pours) = BrewCurveCodec.decode(bytes)
        Curve(samples, pours, error = false)
      } catch (_: BrewCurveFormatException) {
        Curve(emptyList(), emptyList(), error = true)
      } catch (_: DataFormatException) {
        Curve(emptyList(), emptyList(), error = true)
      }
    }
  }

  private fun toItem(row: BrewListRow) = BrewListItem(
    id = row.id,
    startedAtEpochMs = row.startedAtEpochMs,
    name = row.name,
    coffee = row.coffee,
    totalTimeMs = row.totalTimeMs,
    totalWaterG = row.totalWaterG,
    finalRatio = row.finalRatio,
    stabilityScore = row.stabilityScore,
    stabilityLevel = row.stabilityLevel,
    unit = row.unit,
    simulated = row.simulated,
    preview = BrewPreview.decode(row.preview),
    fitScore = row.fitScore,
    rating = row.rating,
  )

  private fun toRecord(entity: BrewEntity, curve: Curve, target: Target) = BrewRecord(
    entity = entity,
    samples = curve.samples,
    summary = BrewSummary(
      totalTimeMs = entity.totalTimeMs,
      totalWaterG = entity.totalWaterG,
      avgFlowGps = entity.avgFlowGps,
      maxFlowGps = entity.maxFlowGps,
      stabilityScore = entity.stabilityScore,
      stabilityLevel = entity.stabilityLevel,
      levelShare = mapOf(
        StabilityLevel.STABLE to entity.shareStable,
        StabilityLevel.IRREGULAR to entity.shareIrregular,
        StabilityLevel.TURBULENT to entity.shareTurbulent,
      ),
      pours = curve.pours,
      finalRatio = entity.finalRatio,
      reason = entity.finishReason,
      interrupted = entity.interrupted,
    ),
    curveError = curve.error,
    target = target.curve,
    targetError = target.error,
  )
}
