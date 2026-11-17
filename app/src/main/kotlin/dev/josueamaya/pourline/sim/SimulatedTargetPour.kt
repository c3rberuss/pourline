package dev.josueamaya.pourline.sim

import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.recipe.StageType
import kotlin.random.Random

enum class FollowMode {
  /** Sigue la curva: agua dentro de ±3 %, tiempo dentro de ±5 s y ajuste ≥ 90. */
  FAITHFUL,

  /** Desde el 2.º vertido, cada uno empieza 8 s tarde, y todas vierten el 80 % de su agua. Ajuste ≤ 60. */
  DEVIATED,
}

/**
 * Vertido simulado que sigue una [TargetCurve] (Kotlin puro y determinista, ticks de 100 ms).
 *
 * Por cada etapa vierte el agua de la etapa durante su ventana con un flujo de base constante
 * (`agua de la etapa / duración`), al que aplica un factor aleatorio de ±10 % que cambia cada
 * [FACTOR_TICKS] ticks y el suavizado de [SimulatedPour]. Solo cuentan los pasos `POUR` (D41): no vierte en
 * los `SWIRL` ni en los `WAIT`. Termina tras el último vertido, cuando el flujo baja de [END_FLOW_GPS].
 *
 * El tiempo del guion empieza en el inicio del primer vertido: el primer tick ya vierte, porque la
 * sesión marca su t0 con el primer vertido.
 */
class SimulatedTargetPour(target: TargetCurve, private val mode: FollowMode, seed: Int = 42) : PourScript {
  private class Window(val startMs: Long, val endMs: Long, val flowGps: Double)

  private val random = Random(seed)
  private val windows: List<Window>
  private val endMs: Long
  private var tick = 0
  private var flow = 0.0
  private var factor = 1.0

  init {
    val pours = target.stages.filter { it.type == StageType.POUR }
    val origin = pours.first().startMs
    val share = if (mode == FollowMode.DEVIATED) DEVIATED_WATER_SHARE else 1.0
    var previousWater = 0.0
    windows = pours.mapIndexed { i, stage ->
      val water = (stage.targetWaterG - previousWater).coerceAtLeast(0.0) * share
      previousWater = stage.targetWaterG
      val durationMs = (stage.endMs - stage.startMs).coerceAtLeast(TICK_MS)
      val delayMs = if (mode == FollowMode.DEVIATED && i > 0) DEVIATED_DELAY_MS else 0L
      val start = stage.startMs - origin + delayMs
      Window(start, start + durationMs, water / (durationMs / 1000.0))
    }
    endMs = windows.maxOf { it.endMs }
  }

  override fun next(): Double? {
    val tMs = tick * TICK_MS
    if (tMs >= endMs && flow < END_FLOW_GPS) return null
    val base = windows.firstOrNull { tMs >= it.startMs && tMs < it.endMs }?.flowGps ?: 0.0
    if (tick % FACTOR_TICKS == 0) factor = random.nextDouble(FACTOR_MIN, FACTOR_MAX)
    flow += (base * factor - flow) * SMOOTHING
    tick++
    return flow
  }

  private companion object {
    const val TICK_MS = 100L
    const val SMOOTHING = 0.3
    const val FACTOR_TICKS = 5
    const val FACTOR_MIN = 0.9
    const val FACTOR_MAX = 1.1
    const val END_FLOW_GPS = 0.05
    const val DEVIATED_DELAY_MS = 8_000L
    const val DEVIATED_WATER_SHARE = 0.8
  }
}
