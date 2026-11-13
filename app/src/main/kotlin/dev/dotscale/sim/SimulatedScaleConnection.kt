package dev.dotscale.sim

import dev.dotscale.brew.GRAMS_PER_OUNCE
import dev.dotscale.protocol.BrewMode
import dev.dotscale.protocol.Cmd
import dev.dotscale.protocol.Frame
import dev.dotscale.protocol.FrameDecoder
import dev.dotscale.protocol.Op
import dev.dotscale.protocol.ScaleMessage
import dev.dotscale.protocol.ScaleMessageParser
import dev.dotscale.protocol.ScaleModel
import dev.dotscale.protocol.TimerStatus
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.scale.ConnectionState
import dev.dotscale.scale.ConnectionStep
import dev.dotscale.scale.ScaleConnection
import java.nio.ByteBuffer
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Báscula DOT simulada para desarrollar sin hardware.
 *
 * Genera tramas binarias reales del protocolo y las pasa por [FrameDecoder] y
 * [ScaleMessageParser], así que la app recorre el mismo camino que con la báscula física.
 *
 * El vertido ya no depende del temporizador: [simulatePour] lanza un pour-over guionizado
 * ([SimulatedPour]) con el perfil elegido. El temporizador simulado sigue respondiendo a
 * `startTimer`, `pauseTimer` y `resetTimer`.
 */
class SimulatedScaleConnection : ScaleConnection {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val lock = Mutex()
  private val decoder = FrameDecoder()
  private val parser = ScaleMessageParser()

  private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
  override val state: StateFlow<ConnectionState> = _state.asStateFlow()

  private val _messages = MutableSharedFlow<ScaleMessage>(extraBufferCapacity = 64)
  override val messages: SharedFlow<ScaleMessage> = _messages.asSharedFlow()

  // Estado interno de la "báscula", en gramos.
  private var grossGrams = 0.0
  private var tareGrams = 0.0
  private var flowGps = 0.0
  private var pour: SimulatedPour? = null
  private var unit = WeightUnit.GRAMS
  private var timer = TimerStatus.RESET
  private var tenths = 0

  override suspend fun connect() {
    _state.value = ConnectionState.Connecting(ConnectionStep.CONNECTING)
    delay(400)
    _state.value = ConnectionState.Connecting(ConnectionStep.HANDSHAKE)
    delay(300)
    _state.value = ConnectionState.Ready
    listOf(Cmd.BATTERY, Cmd.MODEL, Cmd.BREW_MODE, Cmd.TIMER, Cmd.DEVICE_NAME, Cmd.WEIGHT_UNIT, Cmd.FIRMWARE_VERSION)
      .forEach { send(Frame(Op.READ, it)) }
    scope.launch {
      while (isActive) {
        delay(TICK_MS)
        lock.withLock { tick() }
      }
    }
  }

  override suspend fun send(frame: Frame) {
    lock.withLock {
      if (frame.op == Op.READ) respondRead(frame.cmd) else handleWrite(frame)
    }
  }

  override fun disconnect() {
    scope.cancel()
    _state.value = ConnectionState.Disconnected
  }

  /** Solo existe en la simulación: pone algo encima de la báscula. */
  suspend fun addWeight(grams: Double) {
    lock.withLock { grossGrams += grams }
  }

  /**
   * Solo existe en la simulación: lanza un pour-over (bloom, pausa y vertido principal) sin
   * depender del temporizador. Se ignora si ya hay uno en curso.
   */
  suspend fun simulatePour(profile: PourProfile) {
    lock.withLock { if (pour == null) pour = SimulatedPour(profile) }
  }

  private suspend fun tick() {
    if (timer == TimerStatus.RUNNING) tenths++
    val next = pour?.next()
    if (next != null) {
      flowGps = next
      grossGrams += (flowGps * TICK_MS / 1000).coerceAtLeast(0.0)
    } else {
      pour = null
      flowGps *= 0.5
    }
    emitFrame(Frame(Op.NOTIFY, Cmd.WEIGHT, weightPayload()))
  }

  private fun weightPayload(): ByteArray {
    val noise = Random.nextDouble(-0.05, 0.05)
    val net = grossGrams - tareGrams + noise
    val factor = if (unit == WeightUnit.OUNCES) 1 / GRAMS_PER_OUNCE else 1.0
    return ByteBuffer.allocate(9)
      .putInt((net * factor * unit.divisor).roundToInt())
      .putShort((flowGps * factor * unit.divisor).roundToInt().toShort())
      .putShort((tenths / 10).toShort())
      .put(0)
      .array()
  }

  private suspend fun respondRead(cmd: Int) {
    val payload = when (cmd) {
      Cmd.BATTERY -> byteArrayOf(0, 87)
      Cmd.MODEL -> ScaleModel.DOT.firmwareName!!.encodeToByteArray()
      Cmd.BREW_MODE -> byteArrayOf(BrewMode.STANDARD.code.toByte(), 0)
      Cmd.TIMER -> byteArrayOf(timer.code.toByte())
      Cmd.DEVICE_NAME -> "DOT simulada".encodeToByteArray()
      Cmd.WEIGHT_UNIT -> byteArrayOf(unit.code.toByte())
      Cmd.FIRMWARE_VERSION -> "SIM-1.0".encodeToByteArray()
      Cmd.PRECISION -> byteArrayOf(10)
      else -> return
    }
    emitFrame(Frame(Op.READ, cmd, payload))
  }

  private suspend fun handleWrite(frame: Frame) {
    when (frame.cmd) {
      Cmd.TARE -> {
        tareGrams = grossGrams
        emitFrame(Frame(Op.NOTIFY, Cmd.TARE, byteArrayOf(1)))
      }
      Cmd.TIMER -> {
        timer = TimerStatus.fromCode(frame.payload.first().toInt())
        if (timer == TimerStatus.RESET) tenths = 0
        emitFrame(Frame(Op.NOTIFY, Cmd.TIMER, byteArrayOf(timer.code.toByte())))
      }
      Cmd.WEIGHT_UNIT -> {
        unit = WeightUnit.fromCode(frame.payload.first().toInt())
        emitFrame(Frame(Op.NOTIFY, Cmd.WEIGHT_UNIT, byteArrayOf(unit.code.toByte())))
      }
    }
    emitFrame(Frame(Op.WRITE, frame.cmd, byteArrayOf(1))) // ACK
  }

  private suspend fun emitFrame(frame: Frame) {
    decoder.feed(frame.encode()).forEach { _messages.emit(parser.parse(it)) }
  }

  private companion object {
    const val TICK_MS = 100L
  }
}
