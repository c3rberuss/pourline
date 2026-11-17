package dev.josueamaya.pourline.scale

import dev.josueamaya.pourline.protocol.Frame
import dev.josueamaya.pourline.protocol.ScaleMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Conexión con una báscula: real por BLE o simulada. */
interface ScaleConnection {
  val state: StateFlow<ConnectionState>
  val messages: Flow<ScaleMessage>

  /** Conecta y hace el handshake. Lanza excepción si falla. */
  suspend fun connect()

  suspend fun send(frame: Frame)

  fun disconnect()
}

sealed interface ConnectionState {
  data object Disconnected : ConnectionState

  data class Connecting(val step: ConnectionStep) : ConnectionState

  data object Ready : ConnectionState

  data class Failed(val reason: String) : ConnectionState
}

enum class ConnectionStep {
  CONNECTING,
  DISCOVERING,
  BONDING,
  CONFIGURING,
  HANDSHAKE,
}
