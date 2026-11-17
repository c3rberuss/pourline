package dev.josueamaya.pourline.ui.history

import dev.josueamaya.pourline.grinder.GrindChoice
import dev.josueamaya.pourline.grinder.toChoice
import dev.josueamaya.pourline.grinder.toSettingUnlabeled
import dev.josueamaya.pourline.grinder.withGrinder
import dev.josueamaya.pourline.grinder.withValue
import dev.josueamaya.pourline.coffee.PickedCoffee
import dev.josueamaya.pourline.history.BrewDetails
import dev.josueamaya.pourline.history.BrewMethod

sealed interface TempResult {
  data object Empty : TempResult

  data class Valid(val celsius: Double) : TempResult

  data object Invalid : TempResult
}

private val TEMP_FORMAT = Regex("""\d{1,3}(\.\d)?""")

/** Acepta `,` y `.`, de 0 a 100 y un decimal como máximo. */
fun parseWaterTemp(text: String): TempResult {
  val trimmed = text.trim()
  if (trimmed.isEmpty()) return TempResult.Empty
  val normalized = trimmed.replace(',', '.')
  if (!TEMP_FORMAT.matches(normalized)) return TempResult.Invalid
  val value = normalized.toDouble()
  return if (value in 0.0..100.0) TempResult.Valid(value) else TempResult.Invalid
}

/**
 * El café de la preparación en la edición (D22): ninguno, uno del catálogo o, en una preparación antigua, el
 * texto sin vínculo. `Legacy` se conserva mientras no se elija otra cosa.
 */
sealed interface CoffeeChoice {
  data object None : CoffeeChoice

  data class Linked(val coffee: PickedCoffee) : CoffeeChoice

  data class Legacy(val name: String) : CoffeeChoice
}

/** El id del café que hay que vincular, o `null` para desvincular. */
val CoffeeChoice.linkedId: Long? get() = (this as? CoffeeChoice.Linked)?.coffee?.id

/**
 * `true` si guardar [this] frente a [initial] obliga a escribir el vínculo. `Legacy` solo existe si ya era el
 * inicial (no se puede elegir): conservarlo no escribe nada.
 */
fun CoffeeChoice.needsLinkWrite(initial: CoffeeChoice): Boolean = when (this) {
  is CoffeeChoice.Legacy -> false
  is CoffeeChoice.None -> initial !is CoffeeChoice.None
  is CoffeeChoice.Linked -> linkedId != initial.linkedId
}

/** Los campos de la edición tal como se escriben. */
data class BrewEditForm(
  val name: String = "",
  val coffee: CoffeeChoice = CoffeeChoice.None,
  val method: BrewMethod? = null,
  val methodOther: String = "",
  val grind: GrindChoice = GrindChoice.None,
  val waterTemp: String = "",
  val notes: String = "",
  /** Valoración de 1 a 5; `null` sin valorar. */
  val rating: Int? = null,
) {
  val tempResult: TempResult get() = parseWaterTemp(waterTemp)

  val isValid: Boolean get() = tempResult != TempResult.Invalid

  fun isDirty(initial: BrewEditForm): Boolean = this != initial

  /** Elegir otro molino deja el ajuste en `null` (D16); `null` es "Sin molino". */
  fun withGrinder(grinderId: Long?): BrewEditForm = copy(grind = grind.withGrinder(grinderId))

  fun withGrindValue(value: Double): BrewEditForm = copy(grind = grind.withValue(value))

  fun toDetails(): BrewDetails = BrewDetails(
    name = name,
    // El café no viaja aquí: se escribe con `setCoffee` (D22).
    method = method,
    methodOther = methodOther,
    grind = grind.toSettingUnlabeled(),
    waterTempC = (tempResult as? TempResult.Valid)?.celsius,
    notes = notes,
    rating = rating,
  ).normalized()

  companion object {
    fun from(details: BrewDetails, coffee: CoffeeChoice = CoffeeChoice.None) = BrewEditForm(
      name = details.name.orEmpty(),
      coffee = coffee,
      method = details.method,
      methodOther = details.methodOther.orEmpty(),
      grind = details.grind.toChoice(),
      waterTemp = details.waterTempC?.let(::formatTemp).orEmpty(),
      notes = details.notes.orEmpty(),
      rating = details.rating,
    )

    private fun formatTemp(value: Double): String =
      if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
  }
}
