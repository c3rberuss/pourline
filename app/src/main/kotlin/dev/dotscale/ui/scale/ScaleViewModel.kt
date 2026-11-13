package dev.dotscale.ui.scale

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dotscale.brew.BrewEffect
import dev.dotscale.brew.BrewPhase
import dev.dotscale.brew.BrewSession
import dev.dotscale.brew.FinishedBrew
import dev.dotscale.brew.toGrams
import dev.dotscale.history.BrewContext
import dev.dotscale.history.BrewSaver
import dev.dotscale.protocol.Frame
import dev.dotscale.protocol.ScaleCommands
import dev.dotscale.protocol.ScaleMessage
import dev.dotscale.protocol.TimerStatus
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.scale.ConnectionState
import dev.dotscale.scale.ScaleConnection
import dev.dotscale.sim.PourProfile
import dev.dotscale.sim.SimulatedScaleConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * @param clock reloj monotónico en ms; se inyecta para poder probar la sesión sin reloj real.
 * @param wallClock hora de pared en ms desde la época, para fechar las preparaciones.
 */
class ScaleViewModel(
  private val connection: ScaleConnection,
  private val autoFinishSeconds: StateFlow<Int>,
  private val saver: BrewSaver,
  private val applicationScope: CoroutineScope,
  private val clock: () -> Long = SystemClock::elapsedRealtime,
  private val wallClock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

  // Solo se toca desde el hilo principal (colectores y tick de `viewModelScope`).
  private val session = BrewSession()

  private val _ui = MutableStateFlow(ScaleUiState(isSimulated = connection is SimulatedScaleConnection))
  val ui: StateFlow<ScaleUiState> = _ui.asStateFlow()

  private val _errors = Channel<ScaleError>(Channel.BUFFERED)
  val errors = _errors.receiveAsFlow()

  /** Se incrementa con cada guardado y al empezar una sesión: solo el guardado vigente publica su resultado. */
  private val saveGeneration = AtomicLong()

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
    connect()
  }

  private fun onMessage(message: ScaleMessage) {
    val now = clock()
    val effects = when (message) {
      is ScaleMessage.Weight -> session.onWeight(message, now, _ui.value.timer)
      is ScaleMessage.Timer -> session.onTimer(message.status, now)
      is ScaleMessage.Tared -> if (message.success) session.onTared(now) else emptyList()
      else -> emptyList()
    }
    publish { it.reduce(message) }
    handle(effects)
  }

  /** Publica el estado de la sesión. Al empezar una sesión nueva, el guardado anterior deja de ser relevante. */
  private fun publish(transform: (ScaleUiState) -> ScaleUiState = { it }) {
    _ui.update { current ->
      val next = transform(current).copy(brew = session.state)
      if (next.brew.phase is BrewPhase.Active && next.savedBrew != null) {
        saveGeneration.incrementAndGet()
        next.copy(savedBrew = null)
      } else {
        next
      }
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
    effects.filterIsInstance<BrewEffect.Finished>().forEach { save(it.brew) }
    if (frames.isNotEmpty()) send(frames)
  }

  private fun save(brew: FinishedBrew) {
    val ui = _ui.value
    val context = BrewContext(
      startedAtEpochMs = wallClock() - (clock() - brew.t0MonoMs),
      unit = ui.unit,
      scaleModel = ui.model,
      simulated = ui.isSimulated,
    )
    val generation = saveGeneration.incrementAndGet()
    _ui.update { it.copy(savedBrew = SavedBrew.Saving) }
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
    _ui.update { it.copy(dose = current) }
    tare()
  }

  fun clearDose() {
    session.setDoseGrams(null)
    _ui.update { it.copy(dose = null) }
  }

  /** Botón "Terminar" de la gráfica. */
  fun finishBrew() {
    val effects = session.finish(clock())
    publish()
    handle(effects)
  }

  fun simulateAdd(grams: Double) {
    val sim = connection as? SimulatedScaleConnection ?: return
    viewModelScope.launch { sim.addWeight(grams) }
  }

  fun simulatePour(profile: PourProfile) {
    val sim = connection as? SimulatedScaleConnection ?: return
    viewModelScope.launch { sim.simulatePour(profile) }
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
  }

  private companion object {
    const val TAG = "ScaleViewModel"
    const val TICK_MS = 1_000L
  }
}
