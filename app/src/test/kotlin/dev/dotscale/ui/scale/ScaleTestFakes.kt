package dev.dotscale.ui.scale

import dev.dotscale.brew.BrewSample
import dev.dotscale.brew.BrewSummary
import dev.dotscale.brew.FinishReason
import dev.dotscale.brew.FinishedBrew
import dev.dotscale.brew.Pour
import dev.dotscale.brew.StageNotifier
import dev.dotscale.brew.TargetStage
import dev.dotscale.history.BrewContext
import dev.dotscale.history.BrewEntity
import dev.dotscale.history.BrewMethod
import dev.dotscale.history.BrewRecord
import dev.dotscale.history.BrewSaver
import dev.dotscale.protocol.Frame
import dev.dotscale.protocol.ScaleMessage
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.recipe.Recipe
import dev.dotscale.scale.ConnectionState
import dev.dotscale.scale.ScaleConnection
import dev.dotscale.settings.ReferenceKey
import dev.dotscale.settings.ReferenceStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeScaleConnection : ScaleConnection {
  override val state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
  override val messages = MutableSharedFlow<ScaleMessage>(extraBufferCapacity = 64)
  val sent = mutableListOf<Frame>()

  /** Tramas cuyo envío falla (después de registrarse en [sent]). */
  val failing = mutableSetOf<Frame>()

  override suspend fun connect() {
    state.value = ConnectionState.Ready
  }

  override suspend fun send(frame: Frame) {
    sent += frame
    if (frame in failing) throw IllegalStateException("fallo simulado")
  }

  override fun disconnect() = Unit
}

class SavedCall(val brew: FinishedBrew, val context: BrewContext)

/** Registra las llamadas; puede suspender con [gate] o fallar con [failure]. */
class FakeBrewSaver : BrewSaver {
  val calls = mutableListOf<SavedCall>()
  var gate: CompletableDeferred<Unit>? = null
  var failure: Exception? = null
  var nextId = 1L

  override suspend fun save(finished: FinishedBrew, context: BrewContext): Long {
    calls += SavedCall(finished, context)
    gate?.await()
    failure?.let { throw it }
    return nextId++
  }
}

class FakeReferenceStore(initial: ReferenceKey? = null) : ReferenceStore {
  private val _reference = MutableStateFlow(initial)
  override val reference: StateFlow<ReferenceKey?> = _reference.asStateFlow()

  /** Las llamadas a `setReference`, en orden. */
  val sets = mutableListOf<ReferenceKey?>()

  override fun setReference(key: ReferenceKey?) {
    sets += key
    _reference.value = key
  }
}

/** Origenes que se pueden cambiar o borrar durante el test; un id sin poner emite `null` (borrado). */
class FakeTargetReferences : TargetReferences {
  private val recipes = mutableMapOf<Long, MutableStateFlow<Recipe?>>()
  private val brews = mutableMapOf<Long, MutableStateFlow<BrewRecord?>>()

  fun putRecipe(id: Long, recipe: Recipe?) {
    recipes.getOrPut(id) { MutableStateFlow(null) }.value = recipe
  }

  fun putBrew(id: Long, record: BrewRecord?) {
    brews.getOrPut(id) { MutableStateFlow(null) }.value = record
  }

  override fun recipe(id: Long): Flow<Recipe?> = recipes.getOrPut(id) { MutableStateFlow(null) }

  override fun brew(id: Long): Flow<BrewRecord?> = brews.getOrPut(id) { MutableStateFlow(null) }
}

class FakeNotifier : StageNotifier {
  val started = mutableListOf<TargetStage>()
  var released = 0

  override fun stageStarted(stage: TargetStage) {
    started += stage
  }

  override fun release() {
    released++
  }
}

/** Una preparación guardada como referencia: tres vertidos de 50 g, con la curva que se indique. */
fun brewRecord(
  id: Long = 1,
  name: String? = "Mañana",
  coffee: String? = "Etiopía",
  doseG: Double? = 15.0,
  samples: List<BrewSample> = rampSamples(),
  curveError: Boolean = false,
): BrewRecord {
  val total = samples.lastOrNull()?.tMs ?: 0L
  val water = samples.lastOrNull()?.waterG ?: 0.0
  val pours = if (samples.isEmpty()) emptyList() else listOf(Pour(1, 0, total, water))
  val summary = BrewSummary(
    totalTimeMs = total,
    totalWaterG = water,
    avgFlowGps = 3.0,
    maxFlowGps = 4.0,
    stabilityScore = null,
    stabilityLevel = null,
    levelShare = emptyMap(),
    pours = pours,
    finalRatio = doseG?.let { water / it },
    reason = FinishReason.MANUAL,
    interrupted = false,
  )
  val entity = BrewEntity(
    id = id,
    startedAtEpochMs = 1_700_000_000_000,
    name = name,
    coffee = coffee,
    method = BrewMethod.V60,
    grind = "Media",
    waterTempC = 94.0,
    notes = "Notas de cata",
    totalTimeMs = total,
    totalWaterG = water,
    avgFlowGps = 3.0,
    maxFlowGps = 4.0,
    stabilityScore = null,
    stabilityLevel = null,
    shareStable = 0.0,
    shareIrregular = 0.0,
    shareTurbulent = 0.0,
    doseG = doseG,
    finalRatio = summary.finalRatio,
    finishReason = FinishReason.MANUAL,
    interrupted = false,
    unit = WeightUnit.GRAMS,
    scaleModel = null,
    simulated = false,
    metricVersion = 1,
    preview = ByteArray(48),
  )
  return BrewRecord(entity, samples, summary, curveError)
}

/** 250 g en 100 s, a ritmo constante: una curva válida cualquiera. */
fun rampSamples(totalMs: Long = 100_000, totalG: Double = 250.0): List<BrewSample> =
  (0..totalMs step 500).map { t -> BrewSample(t, totalG * t / totalMs, totalG / (totalMs / 1000.0), null, valid = true) }
