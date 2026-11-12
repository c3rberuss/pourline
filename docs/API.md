# API para leer y controlar la báscula

Guía de las clases Kotlin del proyecto para conectarse a una báscula TIMEMORE Black Mirror,
leer sus datos y enviarle comandos. El protocolo binario que hay debajo se describe en
[PROTOCOL.md](PROTOCOL.md).

## Contenido

1. [Arquitectura](#1-arquitectura)
2. [Inicio rápido](#2-inicio-rápido)
3. [Módulo `protocol`](#3-módulo-protocol)
4. [Capa de conexión (`app`)](#4-capa-de-conexión-app)
5. [Capa de UI (`app`)](#5-capa-de-ui-app)
6. [Recetas](#6-recetas)
7. [Errores, hilos y ciclo de vida](#7-errores-hilos-y-ciclo-de-vida)
8. [Cómo añadir un comando nuevo](#8-cómo-añadir-un-comando-nuevo)
9. [Tests](#9-tests)

---

## 1. Arquitectura

```
┌───────────────────────────── app ─────────────────────────────┐
│  ui/        ScanScreen ── ScanViewModel ── ScaleScanner        │
│             ScaleScreen ─ ScaleViewModel ─ ScaleUiState        │
│                                 │                              │
│  scale/                  ScaleConnection (interfaz)            │
│                         ┌───────┴────────┐                     │
│  ble/        BleScaleConnection   SimulatedScaleConnection  sim/│
└─────────────────────────┼────────────────┼─────────────────────┘
                          │  bytes         │  bytes
┌──────────────────────── protocol (JVM puro) ──────────────────┐
│  ScaleCommands → Frame.encode()      FrameDecoder.feed()       │
│                                      → ScaleMessageParser      │
│                                      → ScaleMessage            │
│  Crc16Modbus · ScaleModel · AdvertisementInfo · ScaleGatt      │
└────────────────────────────────────────────────────────────────┘
```

| Módulo | Paquete | Depende de Android | Responsabilidad |
|---|---|---|---|
| `protocol` | `dev.dotscale.protocol` | **No** | Codificar y decodificar tramas, interpretar mensajes, construir comandos |
| `app` | `dev.dotscale.ble` | Sí | Escaneo BLE y conexión GATT |
| `app` | `dev.dotscale.sim` | No (solo coroutines) | Báscula simulada |
| `app` | `dev.dotscale.scale` | No | Contrato `ScaleConnection` |
| `app` | `dev.dotscale.ui.*` | Sí | Pantallas Compose y ViewModels |

Como `protocol` es Kotlin puro, se puede reutilizar en otro proyecto (Kotlin Multiplatform,
servidor, herramientas de línea de comandos) y se prueba sin emulador.

## 2. Inicio rápido

```kotlin
val scale: ScaleConnection = BleScaleConnection(context, address = "AA:BB:CC:DD:EE:FF")
// o, sin hardware: val scale: ScaleConnection = SimulatedScaleConnection()

scope.launch {
  // 1. Suscribirse ANTES de conectar: el flujo no repite mensajes antiguos
  launch {
    scale.messages.collect { msg ->
      when (msg) {
        is ScaleMessage.Weight -> println("%.1f %s  %.1f/s  %ds".format(msg.weight, msg.unit.symbol, msg.flowRate, msg.seconds))
        is ScaleMessage.Battery -> println("Batería ${msg.percent}%")
        else -> Unit
      }
    }
  }

  // 2. Conectar (hace el handshake completo)
  scale.connect()

  // 3. Enviar comandos
  scale.send(ScaleCommands.tare())
  scale.send(ScaleCommands.startTimer())
}

// 4. Al terminar
scale.disconnect()
```

---

## 3. Módulo `protocol`

### `Frame`

Una trama del protocolo.

```kotlin
class Frame(
  val op: Int,
  val cmd: Int,
  val payload: ByteArray = ByteArray(0),
  val crcValid: Boolean = true,   // solo tiene sentido en tramas recibidas
) {
  fun encode(): ByteArray         // A5 5A op cmd len payload crc
}
```

- `equals`/`hashCode` comparan `op`, `cmd` y el **contenido** de `payload` (ignoran `crcValid`).
- `toString()` muestra el payload en hexadecimal, útil para el log.

### `Op` y `Cmd`

Constantes `Int` con los valores de los bytes `op` y `cmd`.

```kotlin
Op.NOTIFY  // 0x01 (supuesto)
Op.READ    // 0x02
Op.WRITE   // 0x03 (desde la báscula: ACK)

Cmd.WEIGHT, Cmd.TIMER, Cmd.POWDER_WATER_RATIO, Cmd.RATIO_CONTROL, Cmd.BATTERY,
Cmd.WEIGHT_UNIT, Cmd.SOUND, Cmd.BREW_MODE, Cmd.ESPRESSO_SATURATION, Cmd.POWER_OFF,
Cmd.DEVICE_NAME, Cmd.TARE, Cmd.FIRMWARE_VERSION, Cmd.SERIAL_NUMBER, Cmd.MODEL,
Cmd.STANDBY_TIME, Cmd.SENSITIVITY, Cmd.PRECISION, Cmd.FACTORY_RESET,
Cmd.FORGET_DEVICE, Cmd.BRIGHTNESS
```

El significado de cada uno está en [PROTOCOL.md §9](PROTOCOL.md#9-tabla-de-comandos) y en el KDoc de `Frame.kt`.

### `ScaleCommands`

Construye las tramas que la app envía a la báscula. Todas devuelven `Frame`.

| Función | Trama | Notas |
|---|---|---|
| `read(cmd)` | `READ cmd []` | Lectura genérica de cualquier comando |
| `write(cmd, payload)` | `WRITE cmd payload` | Escritura genérica |
| `handshake` | Lista de `read(...)` | Batería, modelo, modo, temporizador, precisión, nombre, unidad, firmware |
| `tare()` | `WRITE 0x0D [0,0]` | |
| `startTimer()` | `WRITE 0x02 [1]` | |
| `pauseTimer()` | `WRITE 0x02 [2]` | |
| `resetTimer()` | `WRITE 0x02 [3]` | |
| `setWeightUnit(unit)` | `WRITE 0x06 [code]` | `WeightUnit.GRAMS` / `OUNCES` |
| `setSound(enabled)` | `WRITE 0x07 [0/1]` | |
| `setDeviceName(name)` | `WRITE 0x0C utf8` | Ver el límite de MTU en PROTOCOL.md |
| `setBrewMode(mode, stage)` | `WRITE 0x08 [mode, stage]` | |
| `setSensitivity(level)` | `WRITE 0x17 [5, level+1]` | `level` empieza en 0 |
| `setPrecision(value)` | `WRITE 0x18 [value]` | `1` o `10` |
| `setTargetDose(grams)` | `WRITE 0x04 [4, i32]` | **Experimental** |
| `openRatioControl()` | `WRITE 0x04 [FF,0,0,0,0]` | **Experimental** |
| `closeRatioControl()` | `WRITE 0x04 [1,0,0,0,0]` | **Experimental** |

```kotlin
val bytes: ByteArray = ScaleCommands.tare().encode()
// A5 5A 03 0D 00 02 00 00 7B FC
```

### `FrameDecoder`

Convierte el flujo de bytes de las notificaciones en tramas completas: junta las tramas partidas y
separa las que llegan pegadas.

```kotlin
class FrameDecoder(maxBufferSize: Int = 4096) {
  fun feed(bytes: ByteArray): List<Frame>   // 0..n tramas completas
  fun reset()                               // vaciar el buffer (p. ej. al reconectar)
}
```

- **No es thread-safe.** `BleScaleConnection` lo protege con `synchronized`.
- Las tramas con CRC incorrecto se devuelven igualmente, con `crcValid = false`.
- Si el buffer pasa de `maxBufferSize` sin encontrar ninguna trama, se vacía.

### `ScaleMessageParser`

Convierte una `Frame` en un `ScaleMessage` con tipo.

```kotlin
class ScaleMessageParser(var unit: WeightUnit = WeightUnit.GRAMS) {
  fun parse(frame: Frame): ScaleMessage
}
```

- **Tiene estado:** recuerda la unidad para escalar el peso. Al recibir `Cmd.WEIGHT_UNIT` la
  actualiza sola. Usa **una instancia por conexión**.
- Nunca lanza excepciones: si un payload no tiene el formato esperado, devuelve `ScaleMessage.Unknown(frame)`.

### `ScaleMessage`

`sealed interface` con todos los mensajes que puede enviar la báscula.

| Tipo | Campos | Comando |
|---|---|---|
| `Weight` | `rawWeight`, `rawFlow`, `seconds`, `unit`, `outOfRange` + calculados `weight: Double`, `flowRate: Double` | `0x01` |
| `Timer` | `status: TimerStatus` | `0x02` |
| `PowderWaterRatio` | `raw` | `0x03` |
| `RatioControl` | `mode`, `value` | `0x04` |
| `Battery` | `percent`, `status` | `0x05` |
| `UnitChanged` | `unit: WeightUnit` | `0x06` |
| `Sound` | `enabled` | `0x07` |
| `BrewModeStage` | `mode: BrewMode`, `stage`, `parameter` + `description` (texto en español) | `0x08` |
| `EspressoSaturation` | `stage`, `seconds` | `0x09` |
| `PowerOff` | `code` | `0x0B` |
| `DeviceName` | `name` | `0x0C` |
| `Tared` | `success` | `0x0D` |
| `FirmwareVersion` | `version` | `0x10` |
| `SerialNumber` | `serial` | `0x12` |
| `Model` | `model: ScaleModel`, `firmwareName` | `0x13` |
| `StandbyTime` | `first`, `second?` | `0x16` |
| `Sensitivity` | `level` | `0x17` |
| `Precision` | `value` | `0x18` |
| `FactoryReset` | `code` | `0x19` |
| `ForgetDevice` | `code` | `0x1A` |
| `Brightness` | `percent` | `0x25` |
| `Ack` | `cmd`, `success` | Respuesta a cualquier escritura |
| `Unknown` | `frame` | Comando no reconocido o payload con formato inesperado |

`Weight` conserva los valores brutos (`rawWeight`, `rawFlow`) para no perder precisión;
`weight` y `flowRate` los dividen por `unit.divisor` (10 para gramos, 100 para onzas).

### Enums

```kotlin
enum class WeightUnit(val code: Int, val symbol: String, val divisor: Double) { GRAMS, OUNCES }
enum class TimerStatus(val code: Int) { RUNNING, PAUSED, RESET, UNKNOWN }
enum class BrewMode(val code: Int, val label: String) { STANDARD, POUR_OVER, ESPRESSO, UNKNOWN }
```

Todos tienen `fromCode(code)`, que nunca lanza: devuelve un valor por defecto (`GRAMS`) o `UNKNOWN`.

### `ScaleModel` y `AdvertisementInfo`

```kotlin
enum class ScaleModel(displayName, advertisementCode, firmwareName, usesProtocol2025) {
  DOT, BASIC3, ESPRO, OLD_DOUBLE, UNKNOWN;
  val validatesCrc: Boolean
  companion object {
    fun fromAdvertisementCode(code: Int): ScaleModel
    fun fromFirmwareName(name: String): ScaleModel
  }
}

data class AdvertisementInfo(val broadcastType: Int, val model: ScaleModel, val protocolVersion: Int?) {
  companion object {
    const val MANUFACTURER_ID = 0xFFFF
    fun parse(manufacturerData: ByteArray?): AdvertisementInfo?   // null si no es una báscula compatible
  }
}
```

### `ScaleGatt`

UUIDs como `String`, para no depender de `java.util.UUID` ni de Android:
`SERVICE`, `NOTIFY_CHARACTERISTIC`, `WRITE_CHARACTERISTIC`, `CLIENT_CONFIG_DESCRIPTOR`, más `PREFERRED_MTU = 247`.

### `Crc16Modbus`

```kotlin
Crc16Modbus.compute(data: ByteArray, length: Int = data.size): Int
```

---

## 4. Capa de conexión (`app`)

### `ScaleConnection`

Contrato común para la báscula real y la simulada. El resto de la app depende solo de esta interfaz.

```kotlin
interface ScaleConnection {
  val state: StateFlow<ConnectionState>
  val messages: Flow<ScaleMessage>
  suspend fun connect()           // conecta + handshake; lanza excepción si falla
  suspend fun send(frame: Frame)  // espera a que Android confirme la escritura
  fun disconnect()
}
```

### `ConnectionState`

```kotlin
sealed interface ConnectionState {
  data object Disconnected
  data class Connecting(val step: ConnectionStep)   // CONNECTING, DISCOVERING, BONDING, CONFIGURING, HANDSHAKE
  data object Ready
  data class Failed(val reason: String)
}
```

### `BleScaleConnection`

Implementación real por Bluetooth LE.

```kotlin
BleScaleConnection(context: Context, address: String)
```

- Sigue la secuencia de [PROTOCOL.md §5](PROTOCOL.md#5-secuencia-de-conexión).
- Pone en cola todas las operaciones GATT (`Mutex`): se puede llamar a `send()` desde varias corrutinas a la vez.
- Escribe con respuesta si `FFF2` lo permite y, si no, sin respuesta.
- Usa la API nueva de escritura en Android 13+ y la antigua en versiones anteriores.
- Escribe en logcat con el tag **`BleScale`**: tramas enviadas, tramas no reconocidas y fallos de CRC.
- **Requiere** los permisos `BLUETOOTH_SCAN` y `BLUETOOTH_CONNECT` (Android 12+), o
  `ACCESS_FINE_LOCATION` (Android 11 y anteriores), concedidos **antes** de llamar a `connect()`.
  `ScanScreen` ya los pide.

### `SimulatedScaleConnection`

Báscula DOT falsa para desarrollar sin hardware. Genera tramas binarias reales y las pasa por
`FrameDecoder` y `ScaleMessageParser`, así que prueba el mismo camino que la báscula física.

```kotlin
val sim = SimulatedScaleConnection()
sim.connect()
sim.addWeight(15.0)   // exclusivo del simulador: poner algo en la báscula (gramos)
```

Comportamiento:

| Acción | Respuesta del simulador |
|---|---|
| Lecturas del handshake | Batería 87 %, modelo `TES017`, firmware `SIM-1.0`, nombre «DOT simulada», modo estándar |
| `tare()` | Pone el peso a cero, notifica `Tared` y envía el ACK |
| `startTimer()` | Simula un pour-over: 0–3 s nada · 3–12 s bloom a 5 g/s · 12–30 s pausa · después 4 g/s hasta ~250 g |
| `pauseTimer()` / `resetTimer()` | Detiene el vertido; el reinicio pone el tiempo a cero |
| `setWeightUnit()` | Cambia la escala de las tramas de peso |
| Cada 100 ms | Notificación de peso con ±0,05 g de ruido |

### `ScaleScanner`

```kotlin
class ScaleScanner(context: Context) {
  val isBluetoothEnabled: Boolean
  fun scan(): Flow<ScannedDevice>   // escanea mientras dure el collect
}

data class ScannedDevice(address, name, rssi, advertisement: AdvertisementInfo?, advertisesScaleService) {
  val model: ScaleModel?          // null si el anuncio no lo indica
  val looksLikeScale: Boolean     // modelo reconocido, servicio FFF0 o nombre con «TIMEMORE»
}
```

El flujo emite el mismo dispositivo varias veces (cada anuncio recibido): agrupa por `address`.
Si el Bluetooth está apagado o el escaneo falla, el flujo termina con una excepción.

---

## 5. Capa de UI (`app`)

### `ScaleUiState`

Estado inmutable de la pantalla de báscula. Su método `reduce` es una función pura y se prueba sin Android.

```kotlin
data class ScaleUiState(
  connection, weight, flowRate, seconds, unit, outOfRange, timer,
  batteryPercent, model, firmware, deviceName, brewStage,
  dose: Double?,          // función propia: dosis de café fijada por el usuario
  isSimulated: Boolean,
) {
  val ratio: Double?                                  // peso / dosis → 1:ratio
  fun reduce(message: ScaleMessage): ScaleUiState
}
```

Al cambiar de unidad se borra la dosis, porque deja de ser comparable con el peso.

### `ScaleViewModel`

```kotlin
class ScaleViewModel(connection: ScaleConnection) : ViewModel() {
  val ui: StateFlow<ScaleUiState>
  val errors: Flow<String>        // errores al enviar comandos (para un snackbar)
  fun connect()
  fun tare()
  fun toggleTimer()               // iniciar ↔ pausar
  fun resetTimer()
  fun toggleUnit()
  fun captureDose()               // guarda el peso actual como dosis y hace tara
  fun clearDose()
  fun simulateAdd(grams: Double)  // solo hace algo con SimulatedScaleConnection
}
```

Se desconecta automáticamente en `onCleared()`.

---

## 6. Recetas

### Leer un valor y esperar la respuesta

`send()` termina cuando Android confirma la escritura, no cuando llega la respuesta. Para esperar
la respuesta, suscríbete **antes** de enviar. Puedes añadir este helper:

```kotlin
suspend inline fun <reified T : ScaleMessage> ScaleConnection.request(
  frame: Frame,
  timeoutMs: Long = 2_000,
): T = coroutineScope {
  val response = async(start = CoroutineStart.UNDISPATCHED) { messages.filterIsInstance<T>().first() }
  send(frame)
  withTimeout(timeoutMs) { response.await() }
}

// Uso
val battery = scale.request<ScaleMessage.Battery>(ScaleCommands.read(Cmd.BATTERY))
val firmware = scale.request<ScaleMessage.FirmwareVersion>(ScaleCommands.read(Cmd.FIRMWARE_VERSION))
```

### Comprobar que una escritura se aplicó

```kotlin
val ack = scale.request<ScaleMessage.Ack>(ScaleCommands.setSound(false))
check(ack.cmd == Cmd.SOUND && ack.success)
```

### Observar solo el peso

```kotlin
scale.messages
  .filterIsInstance<ScaleMessage.Weight>()
  .sample(250)                      // máx. 4 actualizaciones/s; requiere @OptIn(FlowPreview::class)
  .collect { updateChart(it.seconds, it.weight) }
```

### Detectar el inicio de una preparación

```kotlin
scale.messages
  .filterIsInstance<ScaleMessage.Weight>()
  .first { it.flowRate > 0.5 }      // empieza a caer agua
```

### Enviar un comando que aún no tiene función

```kotlin
scale.send(ScaleCommands.write(Cmd.SENSITIVITY, byteArrayOf(5, 3)))
scale.send(ScaleCommands.read(0x16))
scale.send(Frame(op = 0x03, cmd = 0x1C, payload = byteArrayOf()))   // experimentar con comandos desconocidos
```

### Usar el protocolo sin Android (JVM, scripts)

```kotlin
fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()

val decoder = FrameDecoder()
val parser = ScaleMessageParser()

fun onBytes(bytes: ByteArray) =
  decoder.feed(bytes).map(parser::parse).forEach(::println)

onBytes(hex("A5 5A 01 01 00 08 00 00 00 B9 00 17 00 2A"))
onBytes(hex("8E 50"))   // la trama llega partida: el decoder la junta
```

### Identificar una báscula durante el escaneo

```kotlin
scanner.scan()
  .filter { it.looksLikeScale }
  .collect { println("${it.name} · ${it.model?.displayName} · ${it.rssi} dBm") }
```

### Cambiar entre báscula real y simulada

Como todo depende de `ScaleConnection`, solo cambia la construcción (así lo hace `Navigation.kt`):

```kotlin
val connection = address?.let { BleScaleConnection(appContext, it) } ?: SimulatedScaleConnection()
```

---

## 7. Errores, hilos y ciclo de vida

| Tema | Comportamiento |
|---|---|
| Errores de `connect()` | Lanza `IOException` (o la excepción original) **y** deja `state = Failed(reason)` |
| Errores de `send()` | Lanza `IOException` si no hay conexión, Android rechaza la escritura o termina el tiempo de espera (5 s) |
| Cancelación | Si se cancela la corrutina durante `connect()`, se desconecta y se propaga la `CancellationException` |
| `messages` | `SharedFlow` **sin replay** y con buffer de 64: suscríbete antes de `connect()` para no perder el handshake |
| Hilos | Los callbacks GATT llegan en un hilo binder; `messages` se emite desde ahí. Usa `collect` en el dispatcher que necesites |
| Desconexión inesperada | `state` pasa a `Failed` o `Disconnected`; las operaciones pendientes fallan con `IOException` |
| Reconexión | No es automática. Llama otra vez a `connect()` (la UI tiene el botón «Reintentar») |
| Tiempos de espera | Conexión 15 s · emparejamiento 30 s · descubrir servicios 10 s · MTU 3 s · resto 5 s |

## 8. Cómo añadir un comando nuevo

Ejemplo: soportar la escritura del tiempo de espera (`0x16`).

1. **Documentarlo** en [PROTOCOL.md](PROTOCOL.md) con su nivel de confianza.
2. **Constante** (si no existe) en `Cmd` (`Frame.kt`), con KDoc.
3. **Mensaje** en `ScaleMessage.kt`, si la báscula responde con datos nuevos.
4. **Parser**: añadir el caso en `ScaleMessageParser.parse()`. Si el payload no es válido,
   devolver `null` (se convierte en `Unknown`).
5. **Comando** en `ScaleCommands`:
   ```kotlin
   fun setStandbyTime(minutes: Int): Frame =
     write(Cmd.STANDBY_TIME, byteArrayOf((minutes ushr 8).toByte(), minutes.toByte()))
   ```
6. **Simulador**: responder en `SimulatedScaleConnection.respondRead` / `handleWrite`.
7. **Estado de UI**: añadir el campo en `ScaleUiState` y el caso en `reduce()`.
8. **Tests** en `ProtocolTest` con un vector hex calculado **fuera** del código (por ejemplo con
   el script de Python de [PROTOCOL.md §7](PROTOCOL.md#7-crc-16modbus)).

## 9. Tests

```bash
./gradlew :protocol:test            # protocolo: CRC, tramas, parser, anuncio
./gradlew :app:testDebugUnitTest    # reducer del estado de UI
```

| Suite | Qué cubre |
|---|---|
| `ProtocolTest` | CRC estándar, codificación de comandos, peso en g / oz / negativo, tramas pegadas y partidas, ACK, CRC inválido, ida y vuelta encoder→decoder, anuncio |
| `ScaleUiStateTest` | Peso en vivo, ratio, borrado de la dosis al cambiar de unidad, temporizador |
