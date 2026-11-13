package dev.dotscale.history

import dev.dotscale.brew.BrewFit
import dev.dotscale.brew.FinishedBrew
import dev.dotscale.brew.TargetCurve
import dev.dotscale.protocol.ScaleModel
import dev.dotscale.protocol.WeightUnit

enum class BrewMethod { V60, KALITA_WAVE, CHEMEX, ORIGAMI, AEROPRESS, OTHER }

data class BrewFilter(val coffee: String? = null, val method: BrewMethod? = null)

/** Punto de la gráfica de progreso. */
data class ProgressPoint(val brewId: Long, val startedAtEpochMs: Long, val score: Int)

/** Tipo de la referencia (objetivo) con la que se hizo una preparación. */
enum class ReferenceType { RECIPE, BREW }

/** La referencia con la que se hizo una preparación, tal como se guarda. */
data class UsedTarget(
  val type: ReferenceType,
  val id: Long,
  /** Nombre mostrado al empezar; no se traduce después. */
  val name: String,
  /** La curva escalada y congelada al empezar. */
  val curve: TargetCurve,
  /** Calculado una sola vez en el ViewModel y guardado sin recalcular. */
  val fit: BrewFit,
)

/** Lo que aporta el ViewModel y la sesión no conoce. */
data class BrewContext(
  val startedAtEpochMs: Long,
  val unit: WeightUnit,
  val scaleModel: ScaleModel?,
  val simulated: Boolean,
  /** `null` sin referencia. */
  val target: UsedTarget? = null,
  /** Datos manuales prellenados desde la referencia (sin nombre ni notas); `null` sin referencia. */
  val details: BrewDetails? = null,
)

/** Datos manuales de una preparación. */
data class BrewDetails(
  val name: String? = null,
  val coffee: String? = null,
  val method: BrewMethod? = null,
  val methodOther: String? = null,
  val grind: String? = null,
  val waterTempC: Double? = null,
  val notes: String? = null,
) {
  /** Recorta los textos, convierte los vacíos en `null` y conserva `methodOther` solo con `OTHER`. */
  fun normalized(): BrewDetails = BrewDetails(
    name = name.cleaned(),
    coffee = coffee.cleaned(),
    method = method,
    methodOther = if (method == BrewMethod.OTHER) methodOther.cleaned() else null,
    grind = grind.cleaned(),
    waterTempC = waterTempC,
    notes = notes.cleaned(),
  )
}

private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/** Quien guarda una sesión terminada. Lo implementa `BrewRepository`; en los tests, un falso. */
fun interface BrewSaver {
  suspend fun save(finished: FinishedBrew, context: BrewContext): Long
}

/**
 * Grafía con la que se guarda un café: recortado y, si ya existe uno igual sin distinguir
 * mayúsculas (`lowercase()` pliega también las vocales acentuadas), el existente. Vacío → `null`.
 */
fun canonicalCoffee(input: String?, existing: Collection<String>): String? {
  val trimmed = input.cleaned() ?: return null
  val key = trimmed.lowercase()
  return existing.firstOrNull { it.lowercase() == key } ?: trimmed
}
