package dev.josueamaya.pourline.session

import dev.josueamaya.pourline.brew.BrewSample
import dev.josueamaya.pourline.brew.BrewSummary
import dev.josueamaya.pourline.brew.FinishReason
import dev.josueamaya.pourline.brew.FinishedBrew
import dev.josueamaya.pourline.brew.Pour
import dev.josueamaya.pourline.brew.StageNotifier
import dev.josueamaya.pourline.brew.TargetStage
import dev.josueamaya.pourline.coffee.PickedCoffee
import dev.josueamaya.pourline.history.BrewContext
import dev.josueamaya.pourline.history.BrewEntity
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.history.BrewRecord
import dev.josueamaya.pourline.history.BrewSaver
import dev.josueamaya.pourline.protocol.Frame
import dev.josueamaya.pourline.protocol.ScaleMessage
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.scale.ConnectionState
import dev.josueamaya.pourline.scale.ConnectionStep
import dev.josueamaya.pourline.scale.ScaleConnection
import dev.josueamaya.pourline.settings.CoffeeStore
import dev.josueamaya.pourline.settings.ReferenceKey
import dev.josueamaya.pourline.settings.ReferenceStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class FakeScaleConnection : ScaleConnection {
  override val state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
  override val messages = MutableSharedFlow<ScaleMessage>(extraBufferCapacity = 64)
  val sent = mutableListOf<Frame>()

  /** Tramas cuyo envío falla (después de registrarse en [sent]). */
  val failing = mutableSetOf<Frame>()

  /** Si no es `null`, `connect()` se queda en `Connecting` hasta que se complete. */
  var connectGate: CompletableDeferred<Unit>? = null

  /** Cuántas veces se llamó a `disconnect()`. */
  var disconnects = 0
    private set

  override suspend fun connect() {
    connectGate?.let {
      state.value = ConnectionState.Connecting(ConnectionStep.CONNECTING)
      it.await()
    }
    state.value = ConnectionState.Ready
  }

  override suspend fun send(frame: Frame) {
    sent += frame
    if (frame in failing) throw IllegalStateException("fallo simulado")
  }

  override fun disconnect() {
    disconnects++
  }
}

/** Da una [FakeScaleConnection] nueva por cada destino y guarda los destinos pedidos. */
class RecordingConnectionFactory : ScaleConnectionFactory {
  val targets = mutableListOf<ScaleTarget>()
  val connections = mutableListOf<FakeScaleConnection>()

  /** Se aplica a cada conexión nueva antes de devolverla (por ejemplo, para poner un `connectGate`). */
  var configure: (FakeScaleConnection) -> Unit = {}

  override fun create(target: ScaleTarget): ScaleConnection {
    targets += target
    return FakeScaleConnection().also { configure(it); connections += it }
  }
}

/** Recuerda la última báscula real; [remembered] registra las llamadas a `remember`. */
class FakeScaleMemory(initial: RememberedScale? = null) : ScaleMemory {
  private val _lastScale = MutableStateFlow(initial)
  override val lastScale: StateFlow<RememberedScale?> = _lastScale.asStateFlow()
  val remembered = mutableListOf<RememberedScale>()

  override fun remember(scale: RememberedScale) {
    remembered += scale
    _lastScale.value = scale
  }
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

/** Recuerda el último café; [sets] registra las llamadas a `setLastCoffeeId`. */
class FakeCoffeeStore(initial: Long? = null) : CoffeeStore {
  private val _lastCoffeeId = MutableStateFlow(initial)
  override val lastCoffeeId: StateFlow<Long?> = _lastCoffeeId.asStateFlow()
  val sets = mutableListOf<Long?>()

  override fun setLastCoffeeId(id: Long?) {
    sets += id
    _lastCoffeeId.value = id
  }
}

/** Cafés que se pueden poner o quitar durante el test; un id sin café (o archivado) emite `null`. */
class FakeScaleCoffees : ScaleCoffees {
  private val coffees = MutableStateFlow<Map<Long, PickedCoffee>>(emptyMap())
  private val _created = MutableSharedFlow<Long>(extraBufferCapacity = 8)
  override val created: Flow<Long> = _created

  fun put(coffee: PickedCoffee) {
    coffees.value = coffees.value + (coffee.id to coffee)
  }

  fun remove(id: Long) {
    coffees.value = coffees.value - id
  }

  fun emitCreated(id: Long) {
    _created.tryEmit(id)
  }

  override fun coffee(id: Long?): Flow<PickedCoffee?> = coffees.map { map -> id?.let(map::get) }.distinctUntilChanged()
}

class FakeNotifier : StageNotifier {
  val started = mutableListOf<TargetStage>()
  val countdown = mutableListOf<Int>()
  val nextStepTicks = mutableListOf<Int>()
  var released = 0

  override fun stageStarted(stage: TargetStage) {
    started += stage
  }

  override fun countdownTick(remainingS: Int) {
    countdown += remainingS
  }

  override fun nextStepTick(remainingS: Int) {
    nextStepTicks += remainingS
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
    grinderId = null,
    grindValue = 21.0,
    grindLabel = "Acme · 21 clics",
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

/**
 * Abre otra sesión guiada y la lleva hasta armada saltando los pasos previos. Desde `Finished` la compuerta está
 * cerrada (D16), así que una segunda sesión empieza así y no con una tara.
 */
fun ScaleSessionManager.beginArmedSession(plan: BrewPlan = BrewPlan(doseG = 15.0, ratio = 16.0)) {
  startSession(plan)
  prepSkip()
  prepSkipDose()
  prepSkip()
}

/** Un [StageNotifier] que anota cada aviso con el índice del paso y el instante de [clock]. */
class TimestampedNotifier(private val clock: () -> Long) : StageNotifier {
  val started = mutableListOf<Pair<Int, Long>>()

  override fun stageStarted(stage: TargetStage) {
    started += stage.index to clock()
  }

  override fun release() = Unit
}
