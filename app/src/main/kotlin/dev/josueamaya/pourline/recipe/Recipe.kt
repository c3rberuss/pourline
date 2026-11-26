package dev.josueamaya.pourline.recipe

import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.history.BrewMethod

/** Todas las magnitudes van en gramos, milisegundos y °C. */
data class Recipe(
  val id: Long = 0,
  val name: String,
  val method: BrewMethod? = null,
  /** Solo con `method == OTHER`. */
  val methodOther: String? = null,
  /** El molino y el ajuste de molienda, o `null` sin molienda (SPEC-006). */
  val grind: GrindSetting? = null,
  val waterTempC: Double? = null,
  /** Instrucciones, no notas de cata. */
  val notes: String? = null,
  /** Dosis nominal. */
  val doseG: Double,
  /** Ordenadas por `startMs`. */
  val stages: List<RecipeStage>,
  /** `"v60_classic"`, `"four_six"` o `null` si la creó el usuario. */
  val builtInKey: String? = null,
  /** `true`: los nombres se muestran traducidos (solo predefinidas sin renombrar). */
  val localizedNames: Boolean = false,
  /** Autor o barista de la receta, sin traducir (SPEC-012). */
  val author: String? = null,
  /** Enlace `http(s)://` a la fuente original (SPEC-012). */
  val sourceUrl: String? = null,
  /** Clave de la receta del catálogo de la que salió la copia, o `null` (SPEC-012). */
  val catalogKey: String? = null,
) {
  /** El agua acumulada del último paso `POUR`. */
  val totalWaterG: Double get() = stages.lastOrNull { it.type == StageType.POUR }?.targetWaterG ?: 0.0
  val ratio: Double get() = totalWaterG / doseG

  /**
   * Lo que dura la preparación: el `endMs` mayor de los pasos (0 sin pasos). Incluye los huecos entre pasos y la espera
   * o el drawdown final. No es `TargetCurve.endMs`, que termina en el último vertido, sino
   * `TargetCurve.lastStepEndMs`.
   */
  val totalDurationMs: Long get() = stages.maxOfOrNull { it.endMs } ?: 0L
}

/**
 * Qué hay que hacer en un paso: verter agua, remover, esperar o una acción genérica ([ACTION], por ejemplo "Pon la tapa",
 * que se describe en la instrucción). Solo `POUR` añade agua.
 */
enum class StageType { POUR, SWIRL, WAIT, ACTION }

data class RecipeStage(
  /** Opcional ("Bloom"). */
  val name: String?,
  /** Instante de inicio relativo a t0. */
  val startMs: Long,
  /**
   * Agua ACUMULADA al terminar el paso. En `SWIRL`, `WAIT` y `ACTION` no añaden agua y valen lo mismo que el `POUR`
   * anterior (0 si no hay): ver [withDerivedWater].
   */
  val targetWaterG: Double,
  /** Duración del paso. */
  val durationMs: Long,
  val type: StageType = StageType.POUR,
  /** Texto libre de hasta 200 caracteres que la sesión muestra mientras el paso está en curso. */
  val instruction: String? = null,
) {
  val endMs: Long get() = startMs + durationMs
}

/** Fija el agua de cada `SWIRL`, `WAIT` y `ACTION` al acumulado del `POUR` anterior (0 si no hay). */
fun List<RecipeStage>.withDerivedWater(): List<RecipeStage> {
  var accumulated = 0.0
  return map { stage ->
    if (stage.type == StageType.POUR) {
      accumulated = stage.targetWaterG
      stage
    } else {
      stage.copy(targetWaterG = accumulated)
    }
  }
}
