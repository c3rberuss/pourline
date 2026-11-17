package dev.dotscale.session

import dev.dotscale.brew.BrewPhase
import dev.dotscale.brew.fromGrams
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.scale.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/**
 * El proceso tiene que seguir vivo aunque la app pase a segundo plano: hay una sesión abierta que no ha terminado, o
 * la que acaba de terminar aún se está guardando. Sin un servicio en primer plano, Android congela el proceso al poco
 * de salir de la app (el tick y los pesos de la báscula se detienen) y puede matarlo.
 */
fun ScaleUiState.needsKeepAlive(): Boolean =
  hasSession && (brew.phase !is BrewPhase.Finished || savedBrew == SavedBrew.Saving)

/** En qué punto de la sesión está la notificación. */
enum class BrewNoticeStage { PREP, POURING, SAVING }

/**
 * Lo que muestra la notificación de la sesión. Solo cambia cuando cambia algo visible (el agua va redondeada a la
 * resolución que se muestra), así que no se reconstruye en cada tick.
 *
 * @param title el nombre del objetivo; `null` en una preparación libre.
 * @param prepStep el paso previo en curso; solo en [BrewNoticeStage.PREP].
 * @param water el agua vertida en [unit], redondeada; solo en [BrewNoticeStage.POURING].
 * @param targetWater el agua objetivo en [unit], redondeada; `null` si no la hay.
 * @param t0MonoMs el inicio de la sesión en el reloj monotónico, para el cronómetro; solo en [BrewNoticeStage.POURING].
 */
data class BrewNotice(
  val title: String?,
  val stage: BrewNoticeStage,
  val prepStep: PrepStep?,
  val water: Double?,
  val targetWater: Double?,
  val unit: WeightUnit,
  val t0MonoMs: Long?,
  val disconnected: Boolean,
)

fun ScaleUiState.toBrewNotice(): BrewNotice {
  val phase = brew.phase
  val stage = when {
    phase is BrewPhase.Finished -> BrewNoticeStage.SAVING
    phase is BrewPhase.Active -> BrewNoticeStage.POURING
    else -> BrewNoticeStage.PREP
  }
  val pouring = stage == BrewNoticeStage.POURING
  return BrewNotice(
    title = target?.name,
    stage = stage,
    prepStep = prep?.step?.takeIf { stage == BrewNoticeStage.PREP },
    water = if (pouring) roundForNotice(weight, unit) else null,
    targetWater = targetWaterG?.let { roundForNotice(fromGrams(it, unit), unit) },
    unit = unit,
    t0MonoMs = brew.t0MonoMs.takeIf { pouring },
    disconnected = stage != BrewNoticeStage.SAVING && connection != ConnectionState.Ready,
  )
}

/** Gramos enteros y décimas de onza: lo bastante para seguir el vertido sin actualizar la notificación a cada lectura. */
private fun roundForNotice(value: Double, unit: WeightUnit): Double = when (unit) {
  WeightUnit.OUNCES -> (value * 10).roundToLong() / 10.0
  else -> value.roundToLong().toDouble()
}

/**
 * Arranca el servicio en primer plano cuando se abre una sesión. Solo arranca: el servicio se detiene solo cuando
 * [needsKeepAlive] deja de cumplirse (así nunca se para antes de haber llamado a `startForeground`).
 *
 * Las sesiones se abren con la app en primer plano ("Empezar", "Reiniciar"), que es cuando Android deja arrancar
 * un servicio en primer plano.
 */
class BrewKeepAlive(ui: StateFlow<ScaleUiState>, scope: CoroutineScope, start: () -> Unit) {
  init {
    scope.launch {
      ui.map { it.needsKeepAlive() }.distinctUntilChanged().filter { it }.collect { start() }
    }
  }
}
