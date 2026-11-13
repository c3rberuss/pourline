package dev.dotscale.ui.scale

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dotscale.brew.BrewEffect
import dev.dotscale.brew.BrewPhase
import dev.dotscale.brew.BrewSession
import dev.dotscale.brew.FinishedBrew
import dev.dotscale.brew.StageAlertTracker
import dev.dotscale.brew.StageNotifier
import dev.dotscale.brew.TargetConfig
import dev.dotscale.brew.TargetCurve
import dev.dotscale.brew.TargetGuide
import dev.dotscale.brew.brewFit
import dev.dotscale.brew.toGrams
import dev.dotscale.coffee.PickedCoffee
import dev.dotscale.history.BrewContext
import dev.dotscale.history.BrewDetails
import dev.dotscale.history.BrewRecord
import dev.dotscale.history.BrewSaver
import dev.dotscale.history.ReferenceType
import dev.dotscale.history.UsedCoffee
import dev.dotscale.history.UsedTarget
import dev.dotscale.protocol.Frame
import dev.dotscale.protocol.ScaleCommands
import dev.dotscale.protocol.ScaleMessage
import dev.dotscale.protocol.TimerStatus
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.recipe.Recipe
import dev.dotscale.scale.ConnectionState
import dev.dotscale.scale.ScaleConnection
import dev.dotscale.settings.CoffeeStore
import dev.dotscale.settings.NoCoffeeStore
import dev.dotscale.settings.ReferenceKey
import dev.dotscale.settings.ReferenceStore
import dev.dotscale.sim.FollowMode
import dev.dotscale.sim.PourProfile
import dev.dotscale.sim.SimulatedScaleConnection
import dev.dotscale.ui.history.formatBrewDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * @param autoFinishSeconds segundos sin verter tras los que la preparación termina; se lee en cada tick,
 * así que un cambio en ajustes se aplica a la sesión en curso.
 * @param saver guarda las sesiones terminadas.
 * @param applicationScope ámbito donde se guarda: no se cancela al destruir el ViewModel, así que salir de
 * Scale no corta un guardado en curso.
 * @param references dónde se recuerda la referencia (objetivo) activa.
 * @param targets de dónde sale la receta o la preparación elegidas como objetivo.
 * @param notifier avisa de que empieza una etapa; se libera en `onCleared`.
 * @param coffeeStore dónde se recuerda el último café elegido.
 * @param coffees de dónde salen el café elegido y las altas nuevas (SPEC-004).
 * @param clock reloj monotónico en ms; se inyecta para poder probar la sesión sin reloj real.
 * @param wallClock hora de pared en ms desde la época, para fechar las preparaciones.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScaleViewModel(
  private val connection: ScaleConnection,
  private val autoFinishSeconds: StateFlow<Int>,
  private val saver: BrewSaver,
  private val applicationScope: CoroutineScope,
  private val references: ReferenceStore,
  private val targets: TargetReferences,
  private val notifier: StageNotifier,
  private val coffeeStore: CoffeeStore = NoCoffeeStore,
  private val coffees: ScaleCoffees = NoScaleCoffees,
  private val clock: () -> Long = SystemClock::elapsedRealtime,
  private val wallClock: () -> Long = System::currentTimeMillis,
  private val targetConfig: TargetConfig = TargetConfig(),
) : ViewModel() {

  // Solo se toca desde el hilo principal (colectores y tick de `viewModelScope`).
  private val session = BrewSession()

  private val _ui = MutableStateFlow(ScaleUiState(isSimulated = connection is SimulatedScaleConnection))
  val ui: StateFlow<ScaleUiState> = _ui.asStateFlow()

  private val _errors = Channel<ScaleError>(Channel.BUFFERED)
  val errors = _errors.receiveAsFlow()

  /** Se incrementa con cada guardado y al empezar una sesión: solo el guardado vigente publica su resultado. */
  private val saveGeneration = AtomicLong()

  /** La referencia resuelta, sin escalar. `null` sin referencia. */
  private var base: TargetBase? = null

  // Memoria del objetivo escalado: la misma instancia mientras no cambien la base ni la dosis.
  private var memoBase: TargetBase? = null
  private var memoDoseG: Double? = null
  private var memoTarget: ActiveTarget? = null

  /** Cuántas veces se ha construido el objetivo escalado. Lo lee el test de rendimiento. */
  internal var targetBuilds = 0
    private set

  /** El objetivo congelado al empezar la sesión cuyo t0 es [frozenFor]; el ajuste se calcula con él. */
  private var sessionTarget: ActiveTarget? = null
  private var frozenFor: Long? = null

  private val alerts = StageAlertTracker()

  /** El café elegido para la siguiente preparación; en `Active`, la pantalla ve el congelado ([sessionCoffee]). */
  private var selectedCoffee: PickedCoffee? = null

  /** El café congelado al empezar la sesión cuyo t0 es [coffeeFrozenFor]; es el que se guarda. */
  private var sessionCoffee: PickedCoffee? = null
  private var coffeeFrozenFor: Long? = null

  /** `true` entre "Nuevo café" y el alta que se elige sola (D23). */
  private var awaitingNewCoffee = false

  init {
    viewModelScope.launch {
      var previous: ConnectionState = ConnectionState.Disconnected
      connection.state.collect { s ->
        val wasReady = previous == ConnectionState.Ready
        previous = s
        val effects = if (wasReady && (s is ConnectionState.Disconnected || s is ConnectionState.Failed)) {
          session.onDisconnected(clock())
        } else {
          emptyList()
        }
        publish { it.copy(connection = s) }
        handle(effects)
      }
    }
    viewModelScope.launch { connection.messages.collect(::onMessage) }
    viewModelScope.launch {
      while (isActive) {
        delay(TICK_MS)
        val effects = session.onTick(clock(), autoFinishSeconds.value * 1_000L)
        publish()
        handle(effects)
      }
    }
    viewModelScope.launch {
      references.reference
        .flatMapLatest { key ->
          when (key) {
            null -> flowOf(Resolution.None)
            is ReferenceKey.Recipe -> targets.recipe(key.id).map { recipe -> resolveRecipe(key, recipe) }
            is ReferenceKey.Brew -> targets.brew(key.id).map { record -> resolveBrew(key, record) }
          }
        }
        .distinctUntilChanged()
        .collect(::onResolution)
    }
    viewModelScope.launch {
      coffeeStore.lastCoffeeId
        .flatMapLatest { id -> coffees.coffee(id) }
        .distinctUntilChanged()
        .collect { picked ->
          selectedCoffee = picked
          publish()
        }
    }
    viewModelScope.launch {
      coffees.created.collect { id ->
        if (!awaitingNewCoffee) return@collect
        awaitingNewCoffee = false
        coffeeStore.setLastCoffeeId(id)
      }
    }
    connect()
  }

  private fun onMessage(message: ScaleMessage) {
    val now = clock()
    // Cambiar de unidad borra la dosis de la pantalla (ver `reduce`) y también la de la sesión.
    if (message is ScaleMessage.UnitChanged && message.unit != _ui.value.unit) session.setDoseGrams(null)
    val effects = when (message) {
      is ScaleMessage.Weight -> session.onWeight(message, now, _ui.value.timer)
      is ScaleMessage.Timer -> session.onTimer(message.status, now)
      is ScaleMessage.Tared -> if (message.success) session.onTared(now) else emptyList()
      else -> emptyList()
    }
    publish { it.reduce(message) }
    handle(effects)
  }

  private fun onResolution(resolution: Resolution) {
    when (resolution) {
      Resolution.None -> base = null
      is Resolution.Resolved -> base = resolution.base
      Resolution.Removed, Resolution.Unreadable -> {
        base = null
        references.setReference(null)
        _errors.trySend(if (resolution == Resolution.Removed) ScaleError.TargetRemoved else ScaleError.TargetUnreadable)
      }
    }
    publish()
  }

  /**
   * Publica el estado de la sesión. Al empezar una sesión nueva, el guardado anterior y el último ajuste
   * dejan de ser relevantes. Los avisos de etapa salen después de actualizar el estado, nunca dentro del
   * bloque de `update`, que puede reintentarse.
   */
  private fun publish(transform: (ScaleUiState) -> ScaleUiState = { it }) {
    _ui.update { current ->
      val next = transform(current).copy(brew = session.state).withTarget(current).withCoffee(current)
      if (next.brew.phase is BrewPhase.Active && (next.savedBrew != null || next.lastFit != null || next.lastBrew != null)) {
        if (next.savedBrew != null) saveGeneration.incrementAndGet()
        next.copy(savedBrew = null, lastFit = null, lastBrew = null)
      } else {
        next
      }
    }
    alertStage()
  }

  /**
   * El objetivo y la guía para este estado. Fuera de `Active` el objetivo sigue a la referencia y a la dosis;
   * en `Active` es el que había al empezar. La guía usa un reloj fijo: `tMs = clock() − t0`.
   */
  private fun ScaleUiState.withTarget(previous: ScaleUiState): ScaleUiState {
    val phase = brew.phase
    val t0 = brew.t0MonoMs
    val target = if (phase is BrewPhase.Active) {
      if (frozenFor != t0) {
        frozenFor = t0
        sessionTarget = previous.target
      }
      sessionTarget
    } else {
      scaledTarget(dose, unit)
    }
    val guide = target?.let {
      val tMs = if (phase is BrewPhase.Active && t0 != null) clock() - t0 else null
      val waterG = brew.samples.lastOrNull()?.takeIf { s -> s.valid }?.waterG
      TargetGuide.at(it.curve, phase, tMs, waterG, targetConfig)
    }
    return copy(target = target, guide = guide)
  }

  /** El café para este estado: fuera de `Active` sigue a la elección; en `Active` es el que había al empezar. */
  private fun ScaleUiState.withCoffee(previous: ScaleUiState): ScaleUiState {
    val t0 = brew.t0MonoMs
    val picked = if (brew.phase is BrewPhase.Active) {
      if (coffeeFrozenFor != t0) {
        coffeeFrozenFor = t0
        sessionCoffee = previous.coffee
      }
      sessionCoffee
    } else {
      selectedCoffee
    }
    return copy(coffee = picked)
  }

  private fun scaledTarget(dose: Double?, unit: WeightUnit): ActiveTarget? {
    val base = base ?: return null
    val doseG = dose?.let { toGrams(it, unit) }
    if (memoBase === base && memoDoseG == doseG) return memoTarget
    val nominal = base.nominalDoseG
    val factor = if (doseG != null && doseG > 0 && nominal != null && nominal > 0) doseG / nominal else 1.0
    targetBuilds++
    val target = ActiveTarget(
      key = base.key,
      type = base.type,
      name = base.name,
      curve = if (factor == 1.0) base.curve else base.curve.scaled(factor),
      factor = factor,
      scaledDoseG = doseG.takeIf { factor != 1.0 },
      prefill = base.prefill,
    )
    memoBase = base
    memoDoseG = doseG
    memoTarget = target
    return target
  }

  private fun alertStage() {
    val state = _ui.value
    val t0 = state.brew.t0MonoMs.takeIf { state.brew.phase is BrewPhase.Active }
    val stage = alerts.update(t0, state.target?.curve, t0?.let { clock() - it })
    if (stage != null) notifier.stageStarted(stage)
  }

  /**
   * Ejecuta los efectos de la sesión. Los comandos del temporizador salen en una sola corrutina y en
   * el orden de la lista, para que `resetTimer` llegue siempre antes que `startTimer`.
   */
  private fun handle(effects: List<BrewEffect>) {
    if (effects.isEmpty()) return
    val frames = effects.mapNotNull { effect ->
      when (effect) {
        BrewEffect.StartTimer -> ScaleCommands.startTimer()
        BrewEffect.PauseTimer -> ScaleCommands.pauseTimer()
        BrewEffect.ResetTimer -> ScaleCommands.resetTimer()
        is BrewEffect.Finished -> null
      }
    }
    effects.filterIsInstance<BrewEffect.Finished>().forEach { save(it.brew) }
    if (frames.isNotEmpty()) send(frames)
  }

  private fun save(brew: FinishedBrew) {
    val ui = _ui.value
    // El objetivo congelado de esta sesión, no el que `ui.target` ya sigue desde `Finished`.
    val frozen = sessionTarget?.takeIf { frozenFor == brew.t0MonoMs }
    val used = frozen?.let {
      UsedTarget(it.type, it.key.id, it.name, it.curve, brewFit(brew.samples, brew.summary.totalTimeMs, it.curve, targetConfig))
    }
    val usedCoffee = sessionCoffee?.takeIf { coffeeFrozenFor == brew.t0MonoMs }
    val startedAt = wallClock() - (clock() - brew.t0MonoMs)
    val context = BrewContext(
      startedAtEpochMs = startedAt,
      unit = ui.unit,
      scaleModel = ui.model,
      simulated = ui.isSimulated,
      target = used,
      details = frozen?.prefill,
      coffee = usedCoffee?.let { UsedCoffee(it.id, it.name) },
    )
    val generation = saveGeneration.incrementAndGet()
    _ui.update {
      it.copy(
        savedBrew = SavedBrew.Saving,
        lastFit = used?.let { u -> SessionFit(u.name, u.curve, u.fit) },
        lastBrew = LastBrew(startedAt, usedCoffee),
      )
    }
    applicationScope.launch {
      val result = try {
        SavedBrew.Saved(saver.save(brew, context))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "save failed", e)
        _errors.trySend(ScaleError.SaveFailed)
        SavedBrew.Failed
      }
      _ui.update { if (saveGeneration.get() == generation) it.copy(savedBrew = result) else it }
    }
  }

  fun connect() {
    viewModelScope.launch {
      try {
        connection.connect()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "connect failed", e) // el estado Failed ya lo muestra la UI
      }
    }
  }

  fun tare() = send(ScaleCommands.tare())

  fun toggleTimer() = send(
    if (_ui.value.timer == TimerStatus.RUNNING) ScaleCommands.pauseTimer() else ScaleCommands.startTimer(),
  )

  fun resetTimer() = send(ScaleCommands.resetTimer())

  fun toggleUnit() = send(
    ScaleCommands.setWeightUnit(if (_ui.value.unit == WeightUnit.GRAMS) WeightUnit.OUNCES else WeightUnit.GRAMS),
  )

  /** Guarda el peso actual como dosis de café y hace tara para empezar a verter. */
  fun captureDose() {
    val current = _ui.value.weight
    if (current <= 0) return
    session.setDoseGrams(toGrams(current, _ui.value.unit))
    publish { it.copy(dose = current) }
    tare()
  }

  fun clearDose() {
    session.setDoseGrams(null)
    publish { it.copy(dose = null) }
  }

  /** Botón "Terminar" de la gráfica. */
  fun finishBrew() {
    val effects = session.finish(clock())
    publish()
    handle(effects)
  }

  /** Elige (o quita, con `null`) el objetivo de la siguiente preparación. Se ignora con la sesión activa. */
  fun selectTarget(key: ReferenceKey?) {
    if (_ui.value.brew.phase is BrewPhase.Active) return
    references.setReference(key)
  }

  /** Elige (o quita, con `null`) el café de la siguiente preparación. Se ignora con la sesión activa. */
  fun selectCoffee(id: Long?) {
    if (_ui.value.brew.phase is BrewPhase.Active) return
    coffeeStore.setLastCoffeeId(id)
  }

  /** "Nuevo café": el próximo alta se elige solo al volver (D23). Con la sesión activa, queda para la siguiente. */
  fun requestNewCoffee() {
    awaitingNewCoffee = true
  }

  fun simulateAdd(grams: Double) {
    val sim = connection as? SimulatedScaleConnection ?: return
    viewModelScope.launch { sim.addWeight(grams) }
  }

  fun simulatePour(profile: PourProfile) {
    val sim = connection as? SimulatedScaleConnection ?: return
    viewModelScope.launch { sim.simulatePour(profile) }
  }

  /** Vierte siguiendo el objetivo actual (ya escalado a la dosis). Sin objetivo no hace nada. */
  fun simulateTargetPour(mode: FollowMode) {
    val sim = connection as? SimulatedScaleConnection ?: return
    val curve = _ui.value.target?.curve ?: return
    viewModelScope.launch { sim.simulatePour(curve, mode) }
  }

  private fun send(frame: Frame) = send(listOf(frame))

  /** Envía las tramas en orden. Si una falla, el error va a [errors] y se sigue con la siguiente. */
  private fun send(frames: List<Frame>) {
    viewModelScope.launch {
      for (frame in frames) {
        try {
          connection.send(frame)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          _errors.send(ScaleError.Command(e.message))
        }
      }
    }
  }

  override fun onCleared() {
    // Salir de Scale con la sesión activa la termina y la guarda. Los comandos del temporizador no se
    // envían: la conexión se cierra justo después.
    session.finish(clock()).filterIsInstance<BrewEffect.Finished>().forEach { save(it.brew) }
    connection.disconnect()
    notifier.release()
  }

  /** La referencia resuelta, antes de escalarla por la dosis. */
  private data class TargetBase(
    val key: ReferenceKey,
    val type: ReferenceType,
    val name: String,
    val curve: TargetCurve,
    /** Dosis con la que se hizo la curva; `null` en una preparación sin dosis. */
    val nominalDoseG: Double?,
    val prefill: BrewDetails,
  )

  private sealed interface Resolution {
    data object None : Resolution

    data class Resolved(val base: TargetBase) : Resolution

    /** El origen se borró. */
    data object Removed : Resolution

    /** La preparación existe pero su curva no se puede leer o no tiene muestras válidas. */
    data object Unreadable : Resolution
  }

  private fun resolveRecipe(key: ReferenceKey.Recipe, recipe: Recipe?): Resolution {
    if (recipe == null) return Resolution.Removed
    val prefill = BrewDetails(
      method = recipe.method,
      methodOther = recipe.methodOther,
      grind = recipe.grind,
      waterTempC = recipe.waterTempC,
    )
    return Resolution.Resolved(
      TargetBase(key, ReferenceType.RECIPE, recipe.name, TargetCurve.fromRecipe(recipe), recipe.doseG, prefill),
    )
  }

  private fun resolveBrew(key: ReferenceKey.Brew, record: BrewRecord?): Resolution {
    if (record == null) return Resolution.Removed
    val curve = if (record.curveError) {
      null
    } else {
      TargetCurve.fromBrew(record.samples, record.summary.pours, record.summary.totalTimeMs)
    }
    if (curve == null) return Resolution.Unreadable
    val entity = record.entity
    // El café sale solo del selector de Scale (D21): copiar el texto de la referencia crearía preparaciones sin vínculo.
    val prefill = BrewDetails(
      method = entity.method,
      methodOther = entity.methodOther,
      grind = entity.grind,
      waterTempC = entity.waterTempC,
    )
    val name = entity.name ?: formatBrewDateTime(entity.startedAtEpochMs)
    return Resolution.Resolved(TargetBase(key, ReferenceType.BREW, name, curve, entity.doseG, prefill))
  }

  private companion object {
    const val TAG = "ScaleViewModel"
    const val TICK_MS = 1_000L
  }
}

private val ReferenceKey.id: Long
  get() = when (this) {
    is ReferenceKey.Recipe -> id
    is ReferenceKey.Brew -> id
  }
