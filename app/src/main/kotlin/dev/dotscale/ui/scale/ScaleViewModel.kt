package dev.dotscale.ui.scale

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dotscale.brew.BrewEffect
import dev.dotscale.brew.BrewSession
import dev.dotscale.brew.toGrams
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * @param autoFinishSeconds segundos sin verter tras los que la preparación termina; se lee en cada tick,
 * así que un cambio en ajustes se aplica a la sesión en curso.
 * @param clock reloj monotónico en ms; se inyecta para poder probar la sesión sin reloj real.
 */
class ScaleViewModel(
  private val connection: ScaleConnection,
  private val autoFinishSeconds: StateFlow<Int>,
  private val clock: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {

  // Solo se toca desde el hilo principal (colectores y tick de `viewModelScope`).
  private val session = BrewSession()

  private val _ui = MutableStateFlow(ScaleUiState(isSimulated = connection is SimulatedScaleConnection))
  val ui: StateFlow<ScaleUiState> = _ui.asStateFlow()

  private val _errors = Channel<String>(Channel.BUFFERED)
  val errors = _errors.receiveAsFlow()

  init {
    viewModelScope.launch {
      var previous: ConnectionState = ConnectionState.Disconnected
      connection.state.collect { s ->
        val wasReady = previous == ConnectionState.Ready
        previous = s
        if (wasReady && (s is ConnectionState.Disconnected || s is ConnectionState.Failed)) session.onDisconnected(clock())
        _ui.update { it.copy(connection = s, brew = session.state) }
      }
    }
    viewModelScope.launch { connection.messages.collect(::onMessage) }
    viewModelScope.launch {
      while (isActive) {
        delay(TICK_MS)
        session.onTick(clock(), autoFinishSeconds.value * 1_000L)
        _ui.update { it.copy(brew = session.state) }
      }
    }
    connect()
  }

  private fun onMessage(message: ScaleMessage) {
    val now = clock()
    val effects = when (message) {
      is ScaleMessage.Weight -> session.onWeight(message, now, _ui.value.timer)
      is ScaleMessage.Timer -> {
        session.onTimer(message.status, now)
        emptyList()
      }
      is ScaleMessage.Tared -> {
        if (message.success) session.onTared(now)
        emptyList()
      }
      else -> emptyList()
    }
    _ui.update { it.reduce(message).copy(brew = session.state) }
    effects.forEach(::execute)
  }

  private fun execute(effect: BrewEffect) = when (effect) {
    BrewEffect.StartTimer -> send(ScaleCommands.startTimer())
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
    session.finish(clock())
    _ui.update { it.copy(brew = session.state) }
  }

  fun simulateAdd(grams: Double) {
    val sim = connection as? SimulatedScaleConnection ?: return
    viewModelScope.launch { sim.addWeight(grams) }
  }

  fun simulatePour(profile: PourProfile) {
    val sim = connection as? SimulatedScaleConnection ?: return
    viewModelScope.launch { sim.simulatePour(profile) }
  }

  private fun send(frame: Frame) {
    viewModelScope.launch {
      try {
        connection.send(frame)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _errors.send(e.message ?: "Error al enviar el comando")
      }
    }
  }

  override fun onCleared() {
    connection.disconnect()
  }

  private companion object {
    const val TAG = "ScaleViewModel"
    const val TICK_MS = 1_000L
  }
}
