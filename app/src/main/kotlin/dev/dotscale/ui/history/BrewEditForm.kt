package dev.dotscale.ui.history

import dev.dotscale.history.BrewDetails
import dev.dotscale.history.BrewMethod

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

/** Los campos de la edición tal como se escriben. */
data class BrewEditForm(
  val name: String = "",
  val coffee: String = "",
  val method: BrewMethod? = null,
  val methodOther: String = "",
  val grind: String = "",
  val waterTemp: String = "",
  val notes: String = "",
) {
  val tempResult: TempResult get() = parseWaterTemp(waterTemp)

  val isValid: Boolean get() = tempResult != TempResult.Invalid

  fun isDirty(initial: BrewEditForm): Boolean = this != initial

  fun toDetails(): BrewDetails = BrewDetails(
    name = name,
    coffee = coffee,
    method = method,
    methodOther = methodOther,
    grind = grind,
    waterTempC = (tempResult as? TempResult.Valid)?.celsius,
    notes = notes,
  ).normalized()

  companion object {
    fun from(details: BrewDetails) = BrewEditForm(
      name = details.name.orEmpty(),
      coffee = details.coffee.orEmpty(),
      method = details.method,
      methodOther = details.methodOther.orEmpty(),
      grind = details.grind.orEmpty(),
      waterTemp = details.waterTempC?.let(::formatTemp).orEmpty(),
      notes = details.notes.orEmpty(),
    )

    private fun formatTemp(value: Double): String =
      if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
  }
}
