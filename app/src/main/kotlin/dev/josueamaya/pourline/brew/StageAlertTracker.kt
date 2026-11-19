package dev.josueamaya.pourline.brew

/**
 * Decide cuándo avisar de que empieza una etapa (Kotlin puro). Vive en el ViewModel, así que girar la
 * pantalla no repite avisos.
 *
 * - Avisa una sola vez por etapa, cuando `tMs` cruza su `startMs`.
 * - No avisa de una etapa con `startMs = 0`: el usuario ya está vertiendo.
 * - Si un salto de tiempo cruza varias etapas, solo avisa de la última.
 * - Se reinicia cuando cambia `t0MonoMs` (otra sesión) o desaparece (fuera de `Active`).
 *
 * [tick] decide los tics de la cuenta atrás hacia el siguiente paso: uno por cada segundo nuevo de
 * `GuideState.countdownS`, aunque la guía se publique varias veces dentro del mismo segundo.
 */
class StageAlertTracker {
  private var t0MonoMs: Long? = null
  private var lastIndex = -1

  /** La etapa de la que hay que avisar ahora, o `null`. */
  fun update(t0MonoMs: Long?, target: TargetCurve?, tMs: Long?): TargetStage? {
    if (t0MonoMs != this.t0MonoMs) {
      this.t0MonoMs = t0MonoMs
      lastIndex = -1
    }
    if (t0MonoMs == null || target == null || tMs == null) return null
    val current = target.stages.indexOfLast { it.startMs <= tMs }
    if (current <= lastIndex) return null
    lastIndex = current
    return target.stages[current].takeIf { it.startMs > 0 }
  }

  private var lastTick: Triple<Long, Int, Int>? = null

  /** El segundo de la cuenta atrás hacia [next] del que hay que avisar ahora, o `null`. */
  fun tick(t0MonoMs: Long?, next: TargetStage?, countdownS: Int?): Int? {
    if (t0MonoMs == null) lastTick = null
    if (t0MonoMs == null || next == null || countdownS == null) return null
    val key = Triple(t0MonoMs, next.index, countdownS)
    if (key == lastTick) return null
    lastTick = key
    return countdownS
  }
}
