package dev.josueamaya.pourline.sim

import kotlin.random.Random

/** Guion de un vertido simulado: cada [next] avanza un tick de 100 ms y devuelve el flujo en g/s, o `null` al terminar. */
fun interface PourScript {
  fun next(): Double?
}

enum class PourProfile {
  /** Flujo suave y constante. */
  STABLE,

  /** Flujo con variaciones aleatorias que cambian cada pocos ticks. */
  IRREGULAR,
}

/**
 * Guion de un pour-over simulado: bloom de 9 s a 5 g/s (~45 g), pausa de 20 s y vertido principal
 * a 4 g/s hasta ~250 g. Es Kotlin puro y determinista (semilla fija) para poder probarlo en la JVM.
 *
 * Cada [next] avanza un tick de 100 ms y devuelve el flujo suavizado en g/s, o `null` cuando el
 * guion termina.
 */
class SimulatedPour(private val profile: PourProfile, seed: Int = 42) : PourScript {
  private val random = Random(seed)
  private var tick = 0
  private var flow = 0.0
  private var poured = 0.0
  private var factor = 1.0

  override fun next(): Double? {
    if (poured >= TOTAL_WATER_G) return null
    val target = when {
      tick < BLOOM_TICKS -> BLOOM_GPS
      tick < BLOOM_TICKS + PAUSE_TICKS -> 0.0
      else -> MAIN_GPS
    }
    // El factor mínimo (0,35) deja el flujo por encima del umbral de vertido, así que no se parte.
    if (profile == PourProfile.IRREGULAR && tick % FACTOR_TICKS == 0) {
      factor = random.nextDouble(FACTOR_MIN, FACTOR_MAX)
    }
    flow += (target * factor - flow) * SMOOTHING
    poured += flow * TICK_S
    tick++
    return flow
  }

  private companion object {
    const val TICK_S = 0.1
    const val BLOOM_TICKS = 90
    const val PAUSE_TICKS = 200
    const val BLOOM_GPS = 5.0
    const val MAIN_GPS = 4.0
    const val TOTAL_WATER_G = 250.0
    const val SMOOTHING = 0.3
    const val FACTOR_TICKS = 5
    const val FACTOR_MIN = 0.35
    const val FACTOR_MAX = 1.65
  }
}
