package dev.josueamaya.pourline.brew

/**
 * Constantes de la curva objetivo, la guía y la métrica de ajuste. Son valores iniciales que se
 * calibrarán con la báscula física.
 *
 * Si cambia cualquiera que afecte al ajuste (`fitZeroErrorShare`, `fitGridMs`), hay que incrementar
 * [BrewConfig.METRIC_VERSION] para que el historial sepa con qué versión se puntuó cada preparación.
 */
data class TargetConfig(
  /** Hasta esta fracción del agua final objetivo, el desvío es `OK`. */
  val deviationWarnShare: Double = 0.05,
  /** Por encima de esta fracción, el desvío es `ERROR`; entre las dos, `WARNING`. */
  val deviationErrorShare: Double = 0.10,
  /** Ventana previa a cada etapa en la que se muestra la cuenta atrás. */
  val countdownMs: Long = 5_000,
  /** Error medio, como fracción del agua final objetivo, con el que el ajuste llega a 0. */
  val fitZeroErrorShare: Double = 0.10,
  /** Paso de la rejilla con la que se compara la preparación con el objetivo. */
  val fitGridMs: Long = 100,
  /** Separación mínima entre puntos de una curva objetivo sacada de una preparación. */
  val brewTargetMinStepMs: Long = 250,
)
