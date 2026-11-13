package dev.dotscale.sim

import dev.dotscale.brew.TargetCurve
import kotlin.random.Random

enum class FollowMode {
  /** Sigue la curva: agua dentro de ±3 %, tiempo dentro de ±5 s y ajuste ≥ 90. */
  FAITHFUL,

  /** Desde la 2.ª etapa, cada una empieza 8 s tarde, y todas vierten el 80 % de su agua. Ajuste ≤ 60. */
  DEVIATED,
}

/**
 * Vertido simulado que sigue una [TargetCurve] (Kotlin puro y determinista, ticks de 100 ms).
 *
 * Por cada etapa vierte el agua de la etapa durante su ventana con un flujo de base constante
 * (`agua de la etapa / duración`), al que aplica un factor aleatorio de ±10 % que cambia cada
 * [FACTOR_TICKS] ticks y el suavizado de [SimulatedPour]. No vierte en las esperas. Termina tras la última
 * etapa, cuando el flujo baja de [END_FLOW_GPS].
 *
 * El tiempo del guion empieza en el inicio de la primera etapa: el primer tick ya vierte, porque la
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
    val origin = target.stages.first().startMs
    val share = if (mode == FollowMode.DEVIATED) DEVIATED_WATER_SHARE else 1.0
    var previousWater = 0.0
    windows = target.stages.mapIndexed { i, stage ->
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
