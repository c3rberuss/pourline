package dev.josueamaya.pourline.history

import dev.josueamaya.pourline.brew.BrewFit
import dev.josueamaya.pourline.brew.FinishedBrew
import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.protocol.ScaleModel
import dev.josueamaya.pourline.protocol.WeightUnit

enum class BrewMethod { V60, KALITA_WAVE, CHEMEX, ORIGAMI, AEROPRESS, FRENCH_PRESS, ESPRESSO, COLD_BREW, MOKA, OTHER }

/** [coffeeId] es el café del catálogo; las preparaciones antiguas sin vínculo solo salen con "Todos" (D33). */
data class BrewFilter(val coffeeId: Long? = null, val method: BrewMethod? = null)

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

/** El café del catálogo con el que se hace una preparación: el id y el nombre que tenía al elegirlo. */
data class UsedCoffee(val id: Long, val name: String)

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
  /** El café elegido en Scale, congelado al empezar; `null` sin café (SPEC-004). */
  val coffee: UsedCoffee? = null,
  /** Preparada sin báscula (SPEC-007): no hay muestras, y `unit` es siempre gramos. */
  val scaleless: Boolean = false,
)

/** Datos manuales de una preparación. */
data class BrewDetails(
  val name: String? = null,
  val coffee: String? = null,
  val method: BrewMethod? = null,
  val methodOther: String? = null,
  /** El molino y el ajuste de molienda, o `null` sin molienda (SPEC-006). */
  val grind: GrindSetting? = null,
  val waterTempC: Double? = null,
  /** Las notas de cata. */
  val notes: String? = null,
  /** Valoración de 1 a 5; fuera de rango se guarda como `null`. */
  val rating: Int? = null,
) {
  /** Recorta los textos, convierte los vacíos en `null` y conserva `methodOther` solo con `OTHER`. */
  fun normalized(): BrewDetails = BrewDetails(
    name = name.cleaned(),
    coffee = coffee.cleaned(),
    method = method,
    methodOther = if (method == BrewMethod.OTHER) methodOther.cleaned() else null,
    grind = grind?.let { it.copy(label = it.label.cleaned()) }?.takeUnless { it.isEmpty },
    waterTempC = waterTempC,
    notes = notes.cleaned(),
    rating = rating?.takeIf { it in 1..5 },
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
