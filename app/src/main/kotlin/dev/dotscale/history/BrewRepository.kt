package dev.dotscale.history

import dev.dotscale.brew.BrewConfig
import dev.dotscale.brew.BrewSample
import dev.dotscale.brew.BrewSummary
import dev.dotscale.brew.FinishedBrew
import dev.dotscale.brew.Pour
import dev.dotscale.brew.StabilityLevel
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

/** Historial de preparaciones. Singleton en `DotScaleApp`. */
class BrewRepository(db: HistoryDatabase) : BrewSaver {
  private val dao = db.brewDao()

  private val _pendingUndo = MutableStateFlow<DeletedBrew?>(null)

  /** Último borrado que se puede deshacer. Vive aquí porque el detalle y la lista tienen ViewModels distintos. */
  val pendingUndo: StateFlow<DeletedBrew?> = _pendingUndo.asStateFlow()

  /** Guarda una preparación terminada y devuelve su id. Escribe `brews` y `brew_curves` en una transacción. */
  override suspend fun save(finished: FinishedBrew, context: BrewContext): Long {
    val summary = finished.summary
    val (curve, preview) = withContext(Dispatchers.Default) {
      BrewCurveCodec.encode(finished.samples, summary.pours) to
        BrewPreview.compute(finished.samples, summary.totalTimeMs, summary.totalWaterG)
    }
    val entity = BrewEntity(
      startedAtEpochMs = context.startedAtEpochMs,
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
    )
    return withContext(Dispatchers.IO) { dao.insertBrew(entity, curve) }
  }

  /** Más reciente primero. */
  fun list(filter: BrewFilter): Flow<List<BrewListItem>> =
    dao.list(filter.coffee?.trim(), filter.method)
      .map { rows -> rows.map(::toItem) }
      .flowOn(Dispatchers.Default)

  /** Solo preparaciones reales con puntuación, en orden cronológico. */
  fun progress(filter: BrewFilter): Flow<List<ProgressPoint>> = dao.progress(filter.coffee?.trim(), filter.method)

  /** Valores distintos, por uso más reciente. */
  fun coffees(): Flow<List<String>> = dao.coffees()

  /** Métodos presentes, en el orden del enum. */
  fun methods(): Flow<List<BrewMethod>> = dao.methods().map { present -> BrewMethod.entries.filter { it in present } }

  suspend fun get(id: Long): BrewRecord? = withContext(Dispatchers.IO) {
    val entity = dao.brew(id) ?: return@withContext null
    toRecord(entity, loadCurve(id))
  }

  /**
   * Observa la fila y emite `null` si desaparece. La curva se decodifica una sola vez por observación,
   * aunque la fila cambie (p. ej. al editar los datos manuales).
   */
  fun observe(id: Long): Flow<BrewRecord?> = flow {
    var curve: Curve? = null
    dao.observeBrew(id).collect { entity ->
      if (entity == null) {
        curve = null
        emit(null)
      } else {
        val loaded = curve ?: loadCurve(id).also { curve = it }
        emit(toRecord(entity, loaded))
      }
    }
  }.flowOn(Dispatchers.IO)

  /** Café de otras preparaciones, para las sugerencias de la edición. */
  suspend fun coffeesExcept(id: Long): List<String> = withContext(Dispatchers.IO) { dao.coffeesExcept(id) }

  // Las escrituras siguientes no se cancelan: salir de la pantalla no puede cortarlas.

  suspend fun updateDetails(id: Long, details: BrewDetails) {
    withContext(Dispatchers.IO + NonCancellable) { dao.applyDetails(id, details) }
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
  )

  private fun toRecord(entity: BrewEntity, curve: Curve) = BrewRecord(
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
  )
}
