package dev.josueamaya.pourline.session

import dev.josueamaya.pourline.brew.BrewPhase
import dev.josueamaya.pourline.brew.fromGrams
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.scale.ConnectionState
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
 * @param scaleless sesión sin báscula (SPEC-007): no hay agua vertida ni desconexión; la notificación dice el paso.
 * @param step el paso en curso de una sesión sin báscula con receta; `null` antes del primer paso y con todos hechos.
 * @param stepsDone sin báscula y en curso: ya se completaron todos los pasos de la receta.
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
  val scaleless: Boolean = false,
  val step: BrewNoticeStep? = null,
  val stepsDone: Boolean = false,
)

/**
 * El paso de la receta que muestra la notificación de una sesión sin báscula. [targetWaterG] solo va en un vertido
 * ([StageType.POUR]), redondeado al gramo; cambia solo al cambiar de paso, así que la notificación no se reconstruye
 * con cada tick del reloj.
 */
data class BrewNoticeStep(val number: Int, val count: Int, val type: StageType, val name: String?, val targetWaterG: Double?)

fun ScaleUiState.toBrewNotice(): BrewNotice {
  if (scaleless) return toScalelessNotice()
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

/** La notificación de la sesión sin báscula: el tiempo y el paso, sin agua vertida y siempre "conectada" (SPEC-007 §3.3). */
private fun ScaleUiState.toScalelessNotice(): BrewNotice {
  val phase = brew.phase
  val stage = when {
    phase is BrewPhase.Finished -> BrewNoticeStage.SAVING
    phase is BrewPhase.Active -> BrewNoticeStage.POURING
    else -> BrewNoticeStage.PREP
  }
  val pouring = stage == BrewNoticeStage.POURING
  val done = pouring && guide?.allStagesDone == true
  val stages = target?.curve?.stages
  val current = guide?.stage?.takeIf { pouring && !done && stages != null }
  val step = current?.let {
    BrewNoticeStep(
      number = it.index + 1,
      count = stages!!.size,
      type = it.type,
      name = it.name?.takeIf { name -> name.isNotBlank() },
      targetWaterG = it.targetWaterG.takeIf { _ -> it.type == StageType.POUR }?.let { water -> roundForNotice(water, WeightUnit.GRAMS) },
    )
  }
  return BrewNotice(
    title = target?.name,
    stage = stage,
    prepStep = null,
    water = null,
    targetWater = targetWaterG?.let { roundForNotice(it, WeightUnit.GRAMS) },
    unit = WeightUnit.GRAMS,
    t0MonoMs = brew.t0MonoMs.takeIf { pouring },
    disconnected = false,
    scaleless = true,
    step = step,
    stepsDone = done,
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
