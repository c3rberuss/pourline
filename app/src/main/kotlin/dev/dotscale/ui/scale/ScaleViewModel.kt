package dev.dotscale.ui.scale

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dotscale.protocol.Frame
import dev.dotscale.protocol.ScaleCommands
import dev.dotscale.protocol.TimerStatus
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.scale.ScaleConnection
import dev.dotscale.sim.SimulatedScaleConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScaleViewModel(private val connection: ScaleConnection) : ViewModel() {

  private val _ui = MutableStateFlow(ScaleUiState(isSimulated = connection is SimulatedScaleConnection))
  val ui: StateFlow<ScaleUiState> = _ui.asStateFlow()

  private val _errors = Channel<String>(Channel.BUFFERED)
  val errors = _errors.receiveAsFlow()

  init {
    viewModelScope.launch { connection.state.collect { s -> _ui.update { it.copy(connection = s) } } }
    viewModelScope.launch { connection.messages.collect { m -> _ui.update { it.reduce(m) } } }
    connect()
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
    _ui.update { it.copy(dose = current) }
    tare()
  }

  fun clearDose() = _ui.update { it.copy(dose = null) }

  fun simulateAdd(grams: Double) {
    val sim = connection as? SimulatedScaleConnection ?: return
    viewModelScope.launch { sim.addWeight(grams) }
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
  }
}
