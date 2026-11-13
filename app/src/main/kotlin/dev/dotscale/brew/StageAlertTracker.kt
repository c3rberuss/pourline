package dev.dotscale.brew

/**
 * Decide cuándo avisar de que empieza una etapa (Kotlin puro). Vive en el ViewModel, así que girar la
 * pantalla no repite avisos.
 *
 * - Avisa una sola vez por etapa, cuando `tMs` cruza su `startMs`.
 * - No avisa de una etapa con `startMs = 0`: el usuario ya está vertiendo.
 * - Si un salto de tiempo cruza varias etapas, solo avisa de la última.
 * - Se reinicia cuando cambia `t0MonoMs` (otra sesión) o desaparece (fuera de `Active`).
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
}
