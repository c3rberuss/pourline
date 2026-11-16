package dev.dotscale.settings

import androidx.compose.runtime.compositionLocalOf

/** Lo que cada pantalla muestra: Básico, lo esencial; Experto, además las gráficas y las métricas. */
enum class ExperienceMode {
  BASIC,
  EXPERT,
  ;

  /** Todas las [ExpertFeature] son de Experto: en Básico no se muestra ninguna. */
  fun shows(@Suppress("UNUSED_PARAMETER") feature: ExpertFeature): Boolean = this == EXPERT
}

/** Cada cosa que solo se muestra en modo Experto (§3.2 y §3.6 del spec). Las reglas viven en [ExperienceMode.shows]. */
enum class ExpertFeature {
  /** Segunda página del dial de la sesión, con la gráfica en vivo. */
  SESSION_CHART_PAGE,

  /** El dial de la sesión con el aro coloreado por estabilidad, la marca de la curva objetivo y la minigráfica del flujo. */
  SESSION_DIAL_INSIGHTS,

  /** Indicador de estabilidad en la sesión. */
  SESSION_STABILITY,

  /** Desvío en gramos contra la curva objetivo en la sesión. */
  SESSION_DEVIATION,

  /** Elegir una preparación guardada como objetivo (pestaña Historial de `TargetPicker`). */
  TARGET_FROM_BREW,

  /** Flujo medio y máximo en el resumen. */
  SUMMARY_FLOW,

  /** `StabilitySection` en el resumen. */
  SUMMARY_STABILITY,

  /** `FitBlock` en el resumen. */
  SUMMARY_FIT,

  /** Motivo de fin en el resumen. */
  SUMMARY_FINISH_REASON,

  /** Gráfica del detalle de una preparación. */
  BREW_DETAIL_CHART,

  /** Tarjeta de ajuste y de estabilidad del detalle. */
  BREW_DETAIL_FIT,

  /** "Comparar" en el detalle. */
  BREW_COMPARE,

  /** "Guardar como receta" en el detalle. */
  BREW_SAVE_AS_RECIPE,

  /** Gráfica de progreso de estabilidad del historial. */
  HISTORY_STABILITY_PROGRESS,

  /** Etiquetas de estabilidad y de ajuste en las filas del historial. */
  HISTORY_ROW_METRICS,

  /** Vista previa de la curva en el editor de recetas. */
  RECIPE_CURVE_PREVIEW,

  /** "Restaurar recetas predefinidas" en Ajustes. */
  RESTORE_BUILT_INS,
}

/**
 * El modo activo. Lo provee `MainActivity` desde `ExperienceSettings.mode`; el valor por defecto es `EXPERT`
 * para que las pantallas que no lo proveen (previews y tests) lo muestren todo.
 */
val LocalExperienceMode = compositionLocalOf { ExperienceMode.EXPERT }
