package dev.josueamaya.pourline.session

import android.os.SystemClock
import android.util.Log
import dev.josueamaya.pourline.brew.BrewEffect
import dev.josueamaya.pourline.brew.BrewPhase
import dev.josueamaya.pourline.brew.BrewSession
import dev.josueamaya.pourline.brew.FinishedBrew
import dev.josueamaya.pourline.brew.PlannedAmounts
import dev.josueamaya.pourline.brew.StageAlertTracker
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.grinder.toGrindSetting
import dev.josueamaya.pourline.brew.StageNotifier
import dev.josueamaya.pourline.brew.TargetConfig
import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.brew.TargetGuide
import dev.josueamaya.pourline.brew.TimedSession
import dev.josueamaya.pourline.brew.brewFit
import dev.josueamaya.pourline.brew.fromGrams
import dev.josueamaya.pourline.brew.toGrams
import dev.josueamaya.pourline.coffee.PickedCoffee
import dev.josueamaya.pourline.history.BrewContext
import dev.josueamaya.pourline.history.BrewDetails
import dev.josueamaya.pourline.history.BrewRecord
import dev.josueamaya.pourline.history.BrewSaver
import dev.josueamaya.pourline.history.ReferenceType
import dev.josueamaya.pourline.history.UsedCoffee
import dev.josueamaya.pourline.history.UsedTarget
import dev.josueamaya.pourline.protocol.Frame
import dev.josueamaya.pourline.protocol.ScaleCommands
import dev.josueamaya.pourline.protocol.ScaleMessage
import dev.josueamaya.pourline.protocol.TimerStatus
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.scale.ConnectionState
import dev.josueamaya.pourline.scale.ScaleConnection
import dev.josueamaya.pourline.settings.CoffeeStore
import dev.josueamaya.pourline.settings.NoCoffeeStore
import dev.josueamaya.pourline.settings.ReferenceKey
import dev.josueamaya.pourline.settings.ReferenceStore
import dev.josueamaya.pourline.sim.FollowMode
import dev.josueamaya.pourline.sim.PourProfile
import dev.josueamaya.pourline.sim.SimulatedScaleConnection
import dev.josueamaya.pourline.ui.history.formatBrewDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * La conexión con la báscula y la sesión de preparación, a nivel de app (un objeto por proceso). La conexión se
 * crea al llamar a [connect] con un [ScaleTarget] y se cierra con [disconnect]; cambiar de pestaña no la toca.
 * La `BrewSession` solo recibe pesos mientras el gestor está armado ([arm] y [disarm]). La sesión sin báscula
 * ([startScalelessSession], SPEC-007) usa una [TimedSession] y no recibe nunca pesos.
 *
 * @param connections crea la conexión de cada destino.
 * @param scope ámbito de las corrutinas del gestor (colectores, tick y comandos).
 * @param autoFinishSeconds segundos sin verter tras los que la preparación termina; se lee en cada tick,
 * así que un cambio en ajustes se aplica a la sesión en curso.
 * @param saver guarda las sesiones terminadas.
 * @param applicationScope ámbito donde se guarda: no se cancela con [scope], así que cerrar el gestor no corta un guardado en curso.
 * @param references dónde se recuerda la referencia (objetivo) activa.
 * @param targets de dónde sale la receta o la preparación elegidas como objetivo.
 * @param notifier avisa de que empieza una etapa; se libera en [disconnect].
 * @param coffeeStore dónde se recuerda el último café elegido.
 * @param coffees de dónde salen el café elegido y las altas nuevas (SPEC-004).
 * @param grind el ajuste de molienda de la tarjeta de Preparar; se congela al pasar a `Active` y es el que se guarda (SPEC-006).
 * @param memory dónde se recuerda la última báscula real que llegó a `Ready` (la simulada nunca).
 * @param countdownSeconds segundos de cuenta regresiva antes de que "Empezar timer" arranque el reloj, con báscula o sin
 * ella; 0 la desactiva. Se lee al pulsar el botón.
 * @param clock reloj monotónico en ms; se inyecta para poder probar la sesión sin reloj real.
 * @param wallClock hora de pared en ms desde la época, para fechar las preparaciones.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScaleSessionManager(
  private val connections: ScaleConnectionFactory,
  private val scope: CoroutineScope,
  private val autoFinishSeconds: StateFlow<Int>,
  private val saver: BrewSaver,
  private val applicationScope: CoroutineScope,
  private val references: ReferenceStore,
  private val targets: TargetReferences,
  private val notifier: StageNotifier,
  private val coffeeStore: CoffeeStore = NoCoffeeStore,
  private val coffees: ScaleCoffees = NoScaleCoffees,
  private val grind: SessionGrind = NoSessionGrind,
  private val memory: ScaleMemory = NoScaleMemory,
  private val countdownSeconds: StateFlow<Int> = MutableStateFlow(0),
  private val clock: () -> Long = SystemClock::elapsedRealtime,
  private val wallClock: () -> Long = System::currentTimeMillis,
  private val targetConfig: TargetConfig = TargetConfig(),
) {

  // Solo se toca desde el hilo principal (colectores y tick de `scope`).
  private val session = BrewSession()

  /** La sesión cronometrada, sin báscula (SPEC-007). Solo se publica con [scaleless]. */
  private val timed = TimedSession()

  /** `true` desde [startScalelessSession] hasta [endSession] o [discardSession]: la sesión es la cronometrada. */
  private var scaleless = false

  /** Lo que la sesión sin báscula congela al abrirse (D3). */
  private data class ScalelessFrozen(
    val target: ActiveTarget?,
    val coffee: PickedCoffee?,
    val grind: GrindSetting?,
    val amounts: PlannedAmounts,
  )

  private var scalelessFrozen: ScalelessFrozen? = null

  /**
   * La dosis que el usuario escribió en Preparar para la receta elegida (en gramos), atada a su referencia: al elegir
   * otra deja de aplicar. Escala la receta como una dosis capturada, que manda si la hay.
   */
  private var plannedDose: Pair<ReferenceKey, Double>? = null

  /** El reloj de 100 ms de la sesión sin báscula mientras está en `Active` (D5). */
  private var scalelessTicker: Job? = null

  /** El `t0` de la sesión sin báscula de la que ya se avisó de que no quedan pasos (D6). */
  private var doneAlertedFor: Long? = null

  /** La cuenta regresiva previa a "Empezar timer"; `null` sin cuenta en marcha. */
  private var countdownJob: Job? = null

  /** Los segundos que le quedan a la cuenta regresiva; `null` sin cuenta en marcha. */
  private var countdownRemaining: Int? = null

  private val _ui = MutableStateFlow(ScaleUiState())
  val ui: StateFlow<ScaleUiState> = _ui.asStateFlow()

  /** La conexión activa; `null` sin destino o tras [disconnect]. */
  private val connection = MutableStateFlow<ScaleConnection?>(null)

  private val _target = MutableStateFlow<ScaleTarget?>(null)

  /** El destino elegido en el último [connect]; se conserva tras [disconnect] para poder reconectar. */
  val target: StateFlow<ScaleTarget?> = _target.asStateFlow()

  /** `true` desde que el usuario pulsa "Desconectar" hasta el siguiente [connect]: la reconexión automática lo respeta (D29). */
  var userDisconnected = false
    private set

  /**
   * Compuerta de la sesión (D16): los pesos, taras y estados del temporizador solo llegan a la `BrewSession` armado,
   * desde que [PrepFlow] llega al paso de verter hasta que la sesión pasa a `Finished`, se descarta o se cierra.
   */
  private var armed = false

  val isArmed: Boolean get() = armed

  /** Los pasos previos de la sesión abierta; `null` sin sesión (D42). */
  private var prep: PrepFlow? = null

  /** La dosis y el ratio de Preparar con los que se abrió la sesión; `null` sin sesión abierta. */
  private var sessionPlan: BrewPlan? = null

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

  /** El ajuste de molienda congelado al empezar la sesión cuyo t0 es [grindFrozenFor]; es el que se guarda (D23). */
  private var sessionGrind: GrindSetting? = null
  private var grindFrozenFor: Long? = null

  /** `true` entre "Nuevo café" y el alta que se elige sola (D23). */
  private var awaitingNewCoffee = false

  init {
    scope.launch {
      var previous: ConnectionState = ConnectionState.Disconnected
      connection.flatMapLatest { it?.state ?: flowOf(ConnectionState.Disconnected) }.collect { s ->
        val wasReady = previous == ConnectionState.Ready
        previous = s
        val lost = wasReady && (s is ConnectionState.Disconnected || s is ConnectionState.Failed)
        val effects = if (lost) session.onDisconnected(clock()) else emptyList()
        if (lost) {
          // Antes de `Active` no hay nada que guardar: la sesión vuelve al paso 1 y la compuerta se cierra.
          if (!scaleless) stopCountdown()
          prep?.onDisconnected()
          if (session.state.phase is BrewPhase.Armed) armed = false
        } else if (s == ConnectionState.Ready) {
          prep?.takeIf { it.state.interrupted }?.onReconnected()
        }
        if (s == ConnectionState.Ready) rememberRealScale()
        // "Falló": una conexión que no llegó a `Ready`; perder una ya establecida es "desconectada" (D12).
        val failed = when (s) {
          is ConnectionState.Failed -> !wasReady
          is ConnectionState.Disconnected -> _ui.value.lastFailure
          else -> false
        }
        publish { it.copy(connection = s, lastFailure = failed) }
        handle(effects)
      }
    }
    scope.launch { connection.flatMapLatest { it?.messages ?: emptyFlow() }.collect(::onMessage) }
    scope.launch {
      while (isActive) {
        delay(TICK_MS)
        val effects = session.onTick(clock(), autoFinishSeconds.value * 1_000L)
        publish()
        handle(effects)
      }
    }
    scope.launch {
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
    scope.launch {
      coffeeStore.lastCoffeeId
        .flatMapLatest { id -> coffees.coffee(id) }
        .distinctUntilChanged()
        .collect { picked ->
          selectedCoffee = picked
          publish()
        }
    }
    scope.launch {
      coffees.created.collect { id ->
        if (!awaitingNewCoffee) return@collect
        awaitingNewCoffee = false
        coffeeStore.setLastCoffeeId(id)
      }
    }
  }

  /** Solo una conexión real que llega a `Ready` se recuerda (D28). */
  private fun rememberRealScale() {
    val real = _target.value as? ScaleTarget.Real ?: return
    memory.remember(RememberedScale(real.address, real.name))
  }

  private fun onMessage(message: ScaleMessage) {
    val now = clock()
    // Cambiar de unidad borra la dosis de la pantalla (ver `reduce`) y también la de la sesión.
    if (message is ScaleMessage.UnitChanged && message.unit != _ui.value.unit) {
      session.setDoseGrams(null)
      prep?.forgetDose()
    }
    val effects = if (!armed || scaleless) {
      emptyList()
    } else {
      when (message) {
        is ScaleMessage.Weight -> session.onWeight(message, now, _ui.value.timer)
        is ScaleMessage.Timer -> session.onTimer(message.status, now)
        is ScaleMessage.Tared -> if (message.success) session.onTared(now) else emptyList()
        else -> emptyList()
      }
    }
    // El agua empezó la preparación antes de que acabara la cuenta regresiva: ya no hace falta.
    if (!scaleless && session.state.phase !is BrewPhase.Armed) stopCountdown()
    publish { it.reduce(message) }
    handle(effects)
    applyPrep(prepEffects(message, now))
  }

  /** Los pasos previos ven los pesos y las taras mientras no estén en el paso de verter (D42). */
  private fun prepEffects(message: ScaleMessage, now: Long): List<PrepEffect> {
    val flow = prep ?: return emptyList()
    return when {
      message is ScaleMessage.Weight && flow.state.step != PrepStep.POUR ->
        flow.onWeight(toGrams(message.weight, message.unit), valid = !message.outOfRange, nowMs = now)
      message is ScaleMessage.Tared && message.success -> flow.onTared(now)
      else -> emptyList()
    }
  }

  /** Ejecuta lo que piden los pasos previos y publica su estado. */
  private fun applyPrep(effects: List<PrepEffect>) {
    if (prep == null) return
    var startNow = false
    for (effect in effects) {
      when (effect) {
        PrepEffect.SendTare -> tare()
        is PrepEffect.SetDose -> setSessionDose(effect.grams)
        PrepEffect.Arm -> armed = true
        PrepEffect.StartNow -> startNow = true
      }
    }
    if (startNow) {
      val timer = session.startNow(clock())
      publish()
      handle(timer)
    } else {
      publish()
    }
  }

  /** Fija la dosis de la sesión (en gramos) sin tarar. */
  private fun setSessionDose(grams: Double?) {
    session.setDoseGrams(grams)
    publish { it.copy(dose = grams?.let { g -> fromGrams(g, it.unit) }) }
  }

  private fun onResolution(resolution: Resolution) {
    when (resolution) {
      Resolution.None -> base = null
      is Resolution.Resolved -> {
        base = resolution.base
        // La dosis escrita era para otra receta.
        if (plannedDose?.first != resolution.base.key) plannedDose = null
      }
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
      val brew = if (scaleless) timed.state else session.state
      val next = transform(current).copy(brew = brew).withTarget(current).withCoffee(current).withSession()
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
    val frozenScaleless = scalelessFrozen.takeIf { this@ScaleSessionManager.scaleless }
    val target = if (frozenScaleless != null) {
      // Sin báscula, el objetivo es el de al abrir la sesión (D3), también en `Armed` y en `Finished`.
      if (t0 != null && frozenFor != t0) {
        frozenFor = t0
        sessionTarget = frozenScaleless.target
      }
      frozenScaleless.target
    } else if (phase is BrewPhase.Active) {
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

  /** Lo de la sesión abierta: los pasos previos, el tiempo (D25) y el agua objetivo (D26). */
  private fun ScaleUiState.withSession(): ScaleUiState {
    val phase = brew.phase
    val t0 = brew.t0MonoMs
    val elapsed = when {
      phase is BrewPhase.Active && t0 != null -> clock() - t0
      phase is BrewPhase.Finished -> phase.summary.totalTimeMs
      else -> null
    }
    val plan = sessionPlan
    val frozenScaleless = scalelessFrozen.takeIf { this@ScaleSessionManager.scaleless }
    return copy(
      prep = this@ScaleSessionManager.prep?.state,
      elapsedMs = elapsed,
      plan = plan,
      targetWaterG = frozenScaleless?.amounts?.waterG ?: plan?.let { targetWaterG(target, dose?.let { d -> toGrams(d, unit) }, it) },
      scaleless = frozenScaleless != null,
      plannedDoseG = frozenScaleless?.amounts?.doseG,
      countdownS = countdownRemaining,
    )
  }

  /** El café para este estado: fuera de `Active` sigue a la elección; en `Active` es el que había al empezar. */
  private fun ScaleUiState.withCoffee(previous: ScaleUiState): ScaleUiState {
    val t0 = brew.t0MonoMs
    val frozenScaleless = scalelessFrozen.takeIf { this@ScaleSessionManager.scaleless }
    val picked = if (frozenScaleless != null) {
      // Sin báscula, el café y la molienda son los de al abrir la sesión (D3).
      if (t0 != null) {
        coffeeFrozenFor = t0
        sessionCoffee = frozenScaleless.coffee
        grindFrozenFor = t0
        sessionGrind = frozenScaleless.grind
      }
      frozenScaleless.coffee
    } else if (brew.phase is BrewPhase.Active) {
      if (coffeeFrozenFor != t0) {
        coffeeFrozenFor = t0
        sessionCoffee = previous.coffee
      }
      if (grindFrozenFor != t0) {
        grindFrozenFor = t0
        sessionGrind = grind.current()
      }
      sessionCoffee
    } else {
      selectedCoffee
    }
    return copy(coffee = picked)
  }

  private fun scaledTarget(dose: Double?, unit: WeightUnit): ActiveTarget? {
    val base = base ?: return null
    val doseG = dose?.let { toGrams(it, unit) } ?: plannedDose?.takeIf { it.first == base.key }?.second
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
      nominalDoseG = nominal,
      scaledDoseG = doseG.takeIf { factor != 1.0 },
      prefill = base.prefill,
      sourceGrind = base.sourceGrind,
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
    // Sin báscula, un único aviso más cuando acaba el último paso (D6).
    if (state.scaleless && t0 != null && state.guide?.allStagesDone == true && doneAlertedFor != t0) {
      doneAlertedFor = t0
      state.target?.curve?.stages?.lastOrNull()?.let(notifier::stageStarted)
    }
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
    val finished = effects.filterIsInstance<BrewEffect.Finished>()
    // La sesión terminó: la compuerta se cierra, así una tara o un vertido en el resumen no la rearma (D16).
    if (finished.isNotEmpty()) armed = false
    finished.forEach { save(it.brew) }
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
    val usedGrind = sessionGrind?.takeIf { grindFrozenFor == brew.t0MonoMs }
    val startedAt = wallClock() - (clock() - brew.t0MonoMs)
    val scaleless = scaleless
    val context = BrewContext(
      startedAtEpochMs = startedAt,
      // Sin báscula: gramos, sin modelo y no simulada (D7).
      unit = if (scaleless) WeightUnit.GRAMS else ui.unit,
      scaleModel = if (scaleless) null else ui.model,
      simulated = if (scaleless) false else ui.isSimulated,
      target = used,
      // La molienda es la de "Moler con", no la de la referencia (D24).
      details = if (frozen == null && usedGrind == null) null else (frozen?.prefill ?: BrewDetails()).copy(grind = usedGrind),
      coffee = usedCoffee?.let { UsedCoffee(it.id, it.name) },
      scaleless = scaleless,
    )
    val generation = saveGeneration.incrementAndGet()
    _ui.update {
      it.copy(
        savedBrew = SavedBrew.Saving,
        lastFit = used?.takeUnless { scaleless }?.let { u -> SessionFit(u.name, u.curve, u.fit) },
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

  /**
   * Conecta a [target]. Si había otra conexión, termina y guarda una sesión `Active` y la cierra antes. El estado
   * del dispositivo (peso, batería, modelo…) vuelve a su valor inicial; la sesión, el objetivo y el café siguen.
   */
  fun connect(target: ScaleTarget) {
    closeConnection()
    userDisconnected = false
    val created = connections.create(target)
    _target.value = target
    connection.value = created
    publish { it.resetDevice(isSimulated = target is ScaleTarget.Simulated) }
    scope.launch {
      try {
        created.connect()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "connect failed", e) // el estado Failed ya lo muestra la UI
      }
    }
  }

  /** Vuelve a conectar al último destino ("Reintentar"). */
  fun retry() {
    _target.value?.let(::connect)
  }

  /**
   * Termina y guarda una sesión `Active` (`MANUAL`, como al salir de la pantalla hasta ahora), desarma, cierra la
   * conexión y libera el notificador. El destino se conserva para [retry].
   */
  fun disconnect() {
    closeConnection()
    userDisconnected = true
    armed = false
    notifier.release()
  }

  /** Cierra la conexión actual, si hay; los comandos del temporizador no se envían: la conexión se cierra justo después. */
  private fun closeConnection() {
    val current = connection.value ?: return
    val finished = session.finish(clock()).filterIsInstance<BrewEffect.Finished>()
    if (finished.isNotEmpty()) armed = false
    finished.forEach { save(it.brew) }
    connection.value = null
    current.disconnect()
    if (!scaleless) stopCountdown()
    publish { it.copy(connection = ConnectionState.Disconnected) }
  }

  /** La `BrewSession` empieza a recibir pesos, taras y estados del temporizador. La sesión guiada la arma con [PrepEffect.Arm]. */
  internal fun arm() {
    armed = true
  }

  /** La `BrewSession` deja de recibir pesos; la pantalla sigue viéndolos. */
  internal fun disarm() {
    armed = false
  }

  /**
   * "Empezar": abre la sesión guiada con los pasos previos en el paso 1. Si ya había una dosis capturada, el paso de
   * la dosis se da por hecho (D24). Se ignora con una sesión `Active`.
   */
  fun startSession(plan: BrewPlan) {
    if (session.state.phase is BrewPhase.Active || timed.state.phase is BrewPhase.Active) return
    stopCountdown()
    clearScaleless()
    session.reset()
    armed = false
    forgetFrozen()
    sessionPlan = plan
    val objective = _ui.value.target?.let { it.scaledDoseG ?: it.nominalDoseG } ?: plan.doseG
    prep = PrepFlow(objective).also { flow -> flow.start(_ui.value.dose?.let { toGrams(it, _ui.value.unit) }) }
    if (_ui.value.savedBrew != null) saveGeneration.incrementAndGet()
    publish { it.copy(savedBrew = null, lastFit = null, lastBrew = null) }
  }

  /**
   * Cierra la sesión: "Listo" (desde `Finished`) o la X antes de `Active`. Desde `Finished` la dosis se borra; si se sale
   * antes de `Active` se conserva (D24). La compuerta ya estaba cerrada desde `Finished`.
   */
  fun endSession() {
    stopCountdown()
    if (scaleless) {
      endScalelessSession()
      return
    }
    if (session.state.phase is BrewPhase.Active) handle(session.finish(clock()))
    val finished = session.state.phase is BrewPhase.Finished
    armed = false
    prep = null
    sessionPlan = null
    if (finished) {
      session.setDoseGrams(null)
      publish { it.copy(dose = null) }
    } else {
      publish()
    }
  }

  /**
   * "Descartar": termina la sesión sin guardarla (D22). La `BrewSession` vuelve a armada y limpia sin emitir
   * `Finished`; la dosis, el objetivo y el café congelados se olvidan.
   */
  fun discardSession() {
    stopCountdown()
    if (scaleless) {
      discardScalelessSession()
      return
    }
    val effects = session.discard(clock())
    armed = false
    prep = null
    sessionPlan = null
    forgetFrozen()
    session.setDoseGrams(null)
    publish { it.copy(dose = null) }
    handle(effects)
  }

  /**
   * "Reiniciar" (D43): antes de `Active`, vuelve al paso 1 y olvida la dosis; en `Active` no hace nada; en `Finished`,
   * empieza otra sesión con la misma receta y el mismo café.
   */
  fun restartPrep() {
    val plan = sessionPlan ?: return
    if (scaleless) {
      // Sin báscula solo se reinicia desde `Finished`: en `Armed` no hay nada que reiniciar y en `Active` corre el reloj.
      if (timed.state.phase is BrewPhase.Finished) {
        endSession()
        startScalelessSession(plan)
      }
      return
    }
    when (session.state.phase) {
      is BrewPhase.Active -> return
      is BrewPhase.Finished -> endSession()
      BrewPhase.Armed -> clearDose()
    }
    startSession(plan)
  }

  /**
   * La dosis de Preparar para la receta elegida (en gramos): la receta se escala a ella (agua y pasos) mientras no haya
   * una dosis capturada. `null` vuelve a la dosis de la receta. Sin receta, o con una que no tiene dosis, no hace nada.
   */
  fun setRecipeDose(grams: Double?) {
    val current = base ?: return
    if (current.nominalDoseG == null) return
    plannedDose = grams?.let { current.key to it }
    publish()
  }

  /**
   * "Empezar sin báscula" (SPEC-007): abre la sesión cronometrada en `Armed`, sin pasos previos. Congela el objetivo, el
   * café y la molienda y las cantidades del plan (D3). Se ignora con una sesión `Active`, sea del tipo que sea.
   */
  fun startScalelessSession(plan: BrewPlan) {
    if (session.state.phase is BrewPhase.Active || timed.state.phase is BrewPhase.Active) return
    val state = _ui.value
    stopCountdown()
    stopScalelessTicker()
    session.reset()
    armed = false
    prep = null
    forgetFrozen()
    doneAlertedFor = null
    val target = scaledTarget(state.dose, state.unit)
    val frozen = ScalelessFrozen(target, selectedCoffee, grind.current(), plannedAmounts(target, plan))
    scalelessFrozen = frozen
    timed.arm(frozen.amounts)
    scaleless = true
    sessionPlan = plan
    if (state.savedBrew != null) saveGeneration.incrementAndGet()
    publish { it.copy(savedBrew = null, lastFit = null, lastBrew = null) }
  }

  /** "Empezar timer": tras la cuenta regresiva, la sesión sin báscula pasa a `Active` y arranca su reloj. */
  fun scalelessStartTimer() {
    if (!scaleless || timed.state.phase != BrewPhase.Armed) return
    afterCountdown {
      if (!scaleless || timed.state.phase != BrewPhase.Armed) return@afterCountdown
      val effects = timed.start(clock())
      publish()
      handle(effects)
      startScalelessTicker()
    }
  }

  /** "Cancelar" de la cuenta regresiva: la sesión sigue lista para empezar. */
  fun cancelCountdown() {
    if (stopCountdown()) publish()
  }

  /**
   * Ejecuta [start] tras la cuenta regresiva de [countdownSeconds], con un tic del notificador por segundo; sin cuenta,
   * enseguida. Se ignora si ya hay una en marcha.
   */
  private fun afterCountdown(start: () -> Unit) {
    if (countdownJob != null) return
    val seconds = countdownSeconds.value
    if (seconds <= 0) {
      start()
      return
    }
    countdownRemaining = seconds
    publish()
    notifier.countdownTick(seconds)
    countdownJob = scope.launch {
      for (remaining in seconds - 1 downTo 0) {
        delay(COUNTDOWN_STEP_MS)
        notifier.countdownTick(remaining)
        if (remaining > 0) {
          countdownRemaining = remaining
          publish()
        }
      }
      countdownJob = null
      countdownRemaining = null
      start()
      publish()
    }
  }

  /** Corta la cuenta regresiva sin publicar; `true` si había una. */
  private fun stopCountdown(): Boolean {
    val job = countdownJob ?: return false
    countdownJob = null
    countdownRemaining = null
    job.cancel()
    return true
  }

  /** El reloj de la sesión sin báscula: cada [SCALELESS_TICK_MS] publica la guía y los avisos, y vigila el tope (D5). */
  private fun startScalelessTicker() {
    scalelessTicker?.cancel()
    scalelessTicker = scope.launch {
      while (isActive) {
        delay(SCALELESS_TICK_MS)
        if (!scaleless || timed.state.phase !is BrewPhase.Active) break
        val effects = timed.tick(clock())
        publish()
        handle(effects)
      }
    }
  }

  private fun stopScalelessTicker() {
    scalelessTicker?.cancel()
    scalelessTicker = null
  }

  /** Deja el gestor como si no hubiera habido sesión sin báscula; no publica. */
  private fun clearScaleless() {
    stopScalelessTicker()
    scaleless = false
    scalelessFrozen = null
    doneAlertedFor = null
  }

  /** [endSession] de la sesión sin báscula: desde `Finished` la dosis se borra, como con báscula. */
  private fun endScalelessSession() {
    if (timed.state.phase is BrewPhase.Active) handle(timed.finish(clock()))
    val finished = timed.state.phase is BrewPhase.Finished
    clearScaleless()
    armed = false
    prep = null
    sessionPlan = null
    forgetFrozen()
    if (finished) {
      session.setDoseGrams(null)
      publish { it.copy(dose = null) }
    } else {
      publish()
    }
  }

  /** [discardSession] de la sesión sin báscula: no guarda nada. */
  private fun discardScalelessSession() {
    val effects = timed.discard()
    clearScaleless()
    armed = false
    prep = null
    sessionPlan = null
    forgetFrozen()
    session.setDoseGrams(null)
    publish { it.copy(dose = null) }
    handle(effects)
  }

  private fun forgetFrozen() {
    sessionTarget = null
    frozenFor = null
    sessionCoffee = null
    coffeeFrozenFor = null
    sessionGrind = null
    grindFrozenFor = null
  }

  /** "Tarar" de los pasos previos: envía la tara y espera su confirmación. */
  fun prepTare() = applyPrep(prep?.tare(clock()).orEmpty())

  /** "Saltar" de las taras (pasos 1 y 3). */
  fun prepSkip() = applyPrep(prep?.skip().orEmpty())

  /** "Capturar ahora": el peso actual es la dosis, sin esperar a que sea estable. */
  fun prepCaptureNow() = applyPrep(prep?.captureNow(toGrams(_ui.value.weight, _ui.value.unit)).orEmpty())

  /** "Saltar (sin dosis)". */
  fun prepSkipDose() = applyPrep(prep?.skipDose().orEmpty())

  /** "Empezar timer": tras la cuenta regresiva, la sesión pasa a `Active` sin esperar al agua. */
  fun prepStartTimer() {
    if (prep?.state?.step != PrepStep.POUR) return
    afterCountdown { applyPrep(prep?.startTimer().orEmpty()) }
  }

  fun tare() = send(ScaleCommands.tare())

  fun toggleTimer() = send(
    if (_ui.value.timer == TimerStatus.RUNNING) ScaleCommands.pauseTimer() else ScaleCommands.startTimer(),
  )

  fun resetTimer() = send(ScaleCommands.resetTimer())

  fun toggleUnit() = send(
    ScaleCommands.setWeightUnit(if (_ui.value.unit == WeightUnit.GRAMS) WeightUnit.OUNCES else WeightUnit.GRAMS),
  )

  /** Guarda el peso actual como dosis de café y, con [tare], hace tara para empezar a verter (D18). */
  fun captureDose(tare: Boolean = true) {
    val current = _ui.value.weight
    if (current <= 0) return
    session.setDoseGrams(toGrams(current, _ui.value.unit))
    publish { it.copy(dose = current) }
    if (tare) tare()
  }

  fun clearDose() {
    session.setDoseGrams(null)
    publish { it.copy(dose = null) }
  }

  /** Botón "Terminar" de la gráfica. */
  fun finishBrew() {
    val effects = if (scaleless) timed.finish(clock()) else session.finish(clock())
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
    val sim = connection.value as? SimulatedScaleConnection ?: return
    scope.launch { sim.addWeight(grams) }
  }

  fun simulatePour(profile: PourProfile) {
    val sim = connection.value as? SimulatedScaleConnection ?: return
    scope.launch { sim.simulatePour(profile) }
  }

  /** Vierte siguiendo el objetivo actual (ya escalado a la dosis). Sin objetivo no hace nada. */
  fun simulateTargetPour(mode: FollowMode) {
    val sim = connection.value as? SimulatedScaleConnection ?: return
    val curve = _ui.value.target?.curve ?: return
    scope.launch { sim.simulatePour(curve, mode) }
  }

  private fun send(frame: Frame) = send(listOf(frame))

  /** Envía las tramas en orden. Si una falla, el error va a [errors] y se sigue con la siguiente. */
  private fun send(frames: List<Frame>) {
    scope.launch {
      for (frame in frames) {
        try {
          connection.value?.send(frame)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          _errors.send(ScaleError.Command(e.message))
        }
      }
    }
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
    /** El molino y el ajuste de la referencia (D24); no se copian a la preparación. */
    val sourceGrind: GrindSetting? = null,
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
      waterTempC = recipe.waterTempC,
    )
    return Resolution.Resolved(
      TargetBase(key, ReferenceType.RECIPE, recipe.name, TargetCurve.fromRecipe(recipe), recipe.doseG, prefill, recipe.grind),
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
      waterTempC = entity.waterTempC,
    )
    val name = entity.name ?: formatBrewDateTime(entity.startedAtEpochMs)
    return Resolution.Resolved(TargetBase(key, ReferenceType.BREW, name, curve, entity.doseG, prefill, entity.toGrindSetting()))
  }

  companion object {
    /** Cada cuánto corre el reloj de la sesión sin báscula (D5): el de los pesos de la báscula, para avisar a tiempo. */
    const val SCALELESS_TICK_MS = 100L

    /** Lo que dura cada número de la cuenta regresiva. */
    const val COUNTDOWN_STEP_MS = 1_000L

    private const val TAG = "ScaleSessionManager"
    private const val TICK_MS = 1_000L
  }
}

private val ReferenceKey.id: Long
  get() = when (this) {
    is ReferenceKey.Recipe -> id
    is ReferenceKey.Brew -> id
  }
