package dev.dotscale.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dotscale.ble.ScaleScanner
import dev.dotscale.ble.ScannedDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class ScanUiState(
  val devices: List<ScannedDevice> = emptyList(),
  val showAll: Boolean = false,
  val scanning: Boolean = false,
  val error: String? = null,
)

class ScanViewModel(private val scanner: ScaleScanner) : ViewModel() {

  private val found = MutableStateFlow<Map<String, ScannedDevice>>(emptyMap())
  private val _ui = MutableStateFlow(ScanUiState())
  val ui: StateFlow<ScanUiState> = _ui.asStateFlow()

  private var job: Job? = null

  val isBluetoothEnabled: Boolean get() = scanner.isBluetoothEnabled

  init {
    viewModelScope.launch { found.collect { refresh() } }
  }

  fun start() {
    job?.cancel()
    found.value = emptyMap()
    _ui.update { it.copy(scanning = true, error = null) }
    job = viewModelScope.launch {
      withTimeoutOrNull(SCAN_DURATION_MS) {
        scanner.scan()
          .catch { e -> _ui.update { it.copy(error = e.message) } }
          .onCompletion { _ui.update { it.copy(scanning = false) } }
          .collect { d -> found.update { it + (d.address to d) } }
      }
      _ui.update { it.copy(scanning = false) }
    }
  }

  fun stop() {
    job?.cancel()
    _ui.update { it.copy(scanning = false) }
  }

  fun setShowAll(showAll: Boolean) {
    _ui.update { it.copy(showAll = showAll) }
    refresh()
  }

  private fun refresh() {
    _ui.update { state ->
      state.copy(
        devices = found.value.values
          .filter { state.showAll || it.looksLikeScale }
          .sortedWith(compareByDescending<ScannedDevice> { it.looksLikeScale }.thenByDescending { it.rssi }),
      )
    }
  }

  private companion object {
    const val SCAN_DURATION_MS = 20_000L
  }
}
