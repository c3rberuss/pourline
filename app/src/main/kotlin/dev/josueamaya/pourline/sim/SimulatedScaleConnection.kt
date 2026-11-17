package dev.josueamaya.pourline.sim

import dev.josueamaya.pourline.brew.GRAMS_PER_OUNCE
import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.protocol.BrewMode
import dev.josueamaya.pourline.protocol.Cmd
import dev.josueamaya.pourline.protocol.Frame
import dev.josueamaya.pourline.protocol.FrameDecoder
import dev.josueamaya.pourline.protocol.Op
import dev.josueamaya.pourline.protocol.ScaleMessage
import dev.josueamaya.pourline.protocol.ScaleMessageParser
import dev.josueamaya.pourline.protocol.ScaleModel
import dev.josueamaya.pourline.protocol.TimerStatus
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.scale.ConnectionState
import dev.josueamaya.pourline.scale.ConnectionStep
import dev.josueamaya.pourline.scale.ScaleConnection
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
  private var pour: PourScript? = null
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
      // Plazo fijo por tick: con `delay(TICK_MS)` suelto, el trabajo de cada tick retrasa el guion (~4 %) y los
      // vertidos guionizados salen más largos que su curva objetivo.
      var deadlineNs = System.nanoTime()
      while (isActive) {
        deadlineNs += TICK_MS * NANOS_PER_MS
        delay(((deadlineNs - System.nanoTime()) / NANOS_PER_MS).coerceAtLeast(0))
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

  /**
   * Solo existe en la simulación: vierte siguiendo [target] (con la dosis ya aplicada) de forma fiel o
   * desviada. Se ignora si ya hay un vertido en curso.
   */
  suspend fun simulatePour(target: TargetCurve, mode: FollowMode) {
    lock.withLock { if (pour == null) pour = SimulatedTargetPour(target, mode) }
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
    const val NANOS_PER_MS = 1_000_000L
  }
}
