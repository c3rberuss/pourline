package dev.dotscale.ui.recipes

import dev.dotscale.history.BrewMethod
import dev.dotscale.recipe.Adjustment
import dev.dotscale.recipe.Recipe
import dev.dotscale.recipe.RecipeDraft
import dev.dotscale.recipe.RecipeError
import dev.dotscale.recipe.RecipeStage
import dev.dotscale.recipe.RecipeValidator
import dev.dotscale.ui.history.TempResult
import dev.dotscale.ui.history.parseWaterTemp

/** Qué le pasa a un campo del editor. La pantalla lo traduce a un texto. */
enum class FieldError {
  NAME_REQUIRED,
  DOSE_INVALID,
  TEMP_INVALID,

  /** No es `m:ss` ni segundos enteros. */
  START_INVALID,
  START_OVERLAPS,
  START_TOO_LATE,

  /** No es un número con un decimal como máximo, o está fuera de 0-1000 g. */
  WATER_INVALID,
  WATER_NOT_INCREASING,
  DURATION_INVALID,
  STAGE_COUNT,
}

data class StageFieldErrors(
  val start: FieldError? = null,
  val water: FieldError? = null,
  val duration: FieldError? = null,
) {
  val any: Boolean get() = start != null || water != null || duration != null
}

data class RecipeFormErrors(
  val name: FieldError? = null,
  val dose: FieldError? = null,
  val temp: FieldError? = null,
  val stages: List<StageFieldErrors> = emptyList(),
  val general: FieldError? = null,
) {
  val any: Boolean get() = name != null || dose != null || temp != null || general != null || stages.any { it.any }
}

/** Una fila de etapa tal como se escribe: inicio `m:ss` o segundos, "Hasta" en g y vertido en s. */
data class StageForm(
  val name: String = "",
  val start: String = "",
  val water: String = "",
  val duration: String = "",
)

private val START_CLOCK = Regex("""(\d{1,2}):([0-5]\d)""")
private val START_SECONDS = Regex("""\d{1,5}""")
private val GRAMS = Regex("""\d{1,4}([.,]\d)?""")
private val SECONDS = Regex("""\d{1,4}""")

/** `1:30` → 90 000 ms y `90` → 90 000 ms. `null` si no es ninguno de los dos formatos. */
fun parseStart(text: String): Long? {
  val trimmed = text.trim()
  START_CLOCK.matchEntire(trimmed)?.let { return (it.groupValues[1].toLong() * 60 + it.groupValues[2].toLong()) * 1_000 }
  if (START_SECONDS.matches(trimmed)) return trimmed.toLong() * 1_000
  return null
}

/** Acepta `,` y `.` y un decimal como máximo. */
fun parseGrams(text: String): Double? {
  val trimmed = text.trim()
  return if (GRAMS.matches(trimmed)) trimmed.replace(',', '.').toDouble() else null
}

/** Segundos enteros → ms. */
fun parseSeconds(text: String): Long? {
  val trimmed = text.trim()
  return if (SECONDS.matches(trimmed)) trimmed.toLong() * 1_000 else null
}

fun formatStart(ms: Long): String {
  val totalSeconds = Math.round(ms / 1000.0)
  return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatNumber(value: Double): String =
  if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/** Los campos del editor de recetas tal como se escriben. Puro: no importa `android.*`. */
data class RecipeEditForm(
  val name: String = "",
  val method: BrewMethod? = null,
  val methodOther: String = "",
  val grind: String = "",
  val waterTemp: String = "",
  val notes: String = "",
  val dose: String = "",
  val stages: List<StageForm> = listOf(StageForm(start = "0:00", water = "50", duration = "10")),
) {
  val canAddStage: Boolean get() = stages.size < RecipeValidator.MAX_STAGES
  val canRemoveStage: Boolean get() = stages.size > 1

  fun isDirty(initial: RecipeEditForm): Boolean = this != initial

  /** Inicio en el fin de la anterior más 30 s, agua anterior más 50 g y 10 s de vertido. */
  fun addStage(): RecipeEditForm {
    if (!canAddStage) return this
    val previous = stages.lastOrNull()
    val previousStart = previous?.let { parseStart(it.start) }
    val previousDuration = previous?.let { parseSeconds(it.duration) }
    val previousWater = previous?.let { parseGrams(it.water) }
    val next = StageForm(
      start = if (previousStart != null && previousDuration != null) formatStart(previousStart + previousDuration + 30_000) else "",
      water = previousWater?.let { formatNumber(it + 50) }.orEmpty(),
      duration = "10",
    )
    return copy(stages = stages + next)
  }

  fun removeStage(index: Int): RecipeEditForm =
    if (!canRemoveStage || index !in stages.indices) this else copy(stages = stages.filterIndexed { i, _ -> i != index })

  fun updateStage(index: Int, stage: StageForm): RecipeEditForm =
    if (index !in stages.indices) this else copy(stages = stages.mapIndexed { i, old -> if (i == index) stage else old })

  /**
   * `true` si la receta sigue con los nombres traducidos: venía así y ni el nombre ni la lista de
   * nombres de etapa cambian respecto a [initial] (añadir o quitar una etapa también la cambia).
   */
  fun keepsLocalizedNames(initial: RecipeEditForm, wasLocalized: Boolean): Boolean =
    wasLocalized && name.trim() == initial.name.trim() &&
      stages.map { it.name.trim() } == initial.stages.map { it.name.trim() }

  /** Errores por campo: de formato y de [RecipeValidator]. */
  fun errors(): RecipeFormErrors {
    val parsed = parse()
    val validator = RecipeValidator.validate(parsed.recipe)

    fun has(error: RecipeError) = error in validator
    val stageErrors = stages.mapIndexed { i, _ ->
      val p = parsed.stages[i]
      val prev = parsed.stages.getOrNull(i - 1)
      val last = i == stages.lastIndex
      StageFieldErrors(
        start = when {
          p.start == null -> FieldError.START_INVALID
          prev != null && prev.start != null && prev.duration != null && has(RecipeError.StartOverlaps(i)) -> FieldError.START_OVERLAPS
          last && has(RecipeError.LastStartTooLate) -> FieldError.START_TOO_LATE
          has(RecipeError.StartNegative(i)) -> FieldError.START_INVALID
          else -> null
        },
        water = when {
          p.water == null -> FieldError.WATER_INVALID
          has(RecipeError.WaterOutOfRange(i)) -> FieldError.WATER_INVALID
          prev?.water != null && has(RecipeError.WaterNotIncreasing(i)) -> FieldError.WATER_NOT_INCREASING
          else -> null
        },
        duration = when {
          p.duration == null -> FieldError.DURATION_INVALID
          has(RecipeError.DurationOutOfRange(i)) -> FieldError.DURATION_INVALID
          else -> null
        },
      )
    }
    return RecipeFormErrors(
      name = FieldError.NAME_REQUIRED.takeIf { has(RecipeError.NameRequired) },
      dose = FieldError.DOSE_INVALID.takeIf { parsed.dose == null || has(RecipeError.DoseInvalid) },
      temp = FieldError.TEMP_INVALID.takeIf { tempResult == TempResult.Invalid || has(RecipeError.TempInvalid) },
      stages = stageErrors,
      general = FieldError.STAGE_COUNT.takeIf { has(RecipeError.StageCountInvalid) },
    )
  }

  val isValid: Boolean get() = !errors().any

  /** La receta, o `null` si algún campo no es válido. */
  fun toRecipe(id: Long = 0, builtInKey: String? = null, localizedNames: Boolean = false): Recipe? {
    if (errors().any) return null
    return parse().recipe.copy(id = id, builtInKey = builtInKey, localizedNames = localizedNames)
  }

  /** Agua total y ratio en vivo; `null` si la última etapa o la dosis no se pueden leer. */
  fun totalWaterG(): Double? = stages.lastOrNull()?.let { parseGrams(it.water) }

  fun ratio(): Double? {
    val water = totalWaterG() ?: return null
    val dose = parseGrams(dose)?.takeIf { it > 0 } ?: return null
    return water / dose
  }

  private val tempResult: TempResult get() = parseWaterTemp(waterTemp)

  private class ParsedStage(val start: Long?, val water: Double?, val duration: Long?)

  private class Parsed(val recipe: Recipe, val dose: Double?, val stages: List<ParsedStage>)

  /** Los campos que no se leen valen 0 en la receta de trabajo, y sus errores de [RecipeValidator] se descartan. */
  private fun parse(): Parsed {
    val parsedStages = stages.map { ParsedStage(parseStart(it.start), parseGrams(it.water), parseSeconds(it.duration)) }
    val dose = parseGrams(dose)
    val method = method
    val recipe = Recipe(
      name = name.trim(),
      method = method,
      methodOther = if (method == BrewMethod.OTHER) methodOther.cleaned() else null,
      grind = grind.cleaned(),
      waterTempC = (tempResult as? TempResult.Valid)?.celsius,
      notes = notes.cleaned(),
      doseG = dose ?: 0.0,
      stages = stages.mapIndexed { i, s ->
        val p = parsedStages[i]
        RecipeStage(s.name.cleaned(), p.start ?: 0, p.water ?: 0.0, p.duration ?: 0)
      },
    )
    return Parsed(recipe, dose, parsedStages)
  }

  companion object {
    fun from(recipe: Recipe) = RecipeEditForm(
      name = recipe.name,
      method = recipe.method,
      methodOther = recipe.methodOther.orEmpty(),
      grind = recipe.grind.orEmpty(),
      waterTemp = recipe.waterTempC?.let(::formatNumber).orEmpty(),
      notes = recipe.notes.orEmpty(),
      dose = formatNumber(recipe.doseG),
      stages = recipe.stages.map {
        StageForm(
          name = it.name.orEmpty(),
          start = formatStart(it.startMs),
          water = formatNumber(it.targetWaterG),
          duration = Math.round(it.durationMs / 1000.0).toString(),
        )
      },
    )

    /** Con [Adjustment.MissingDose], la dosis queda vacía. */
    fun from(draft: RecipeDraft): RecipeEditForm {
      val form = from(draft.recipe)
      return if (Adjustment.MissingDose in draft.adjustments) form.copy(dose = "") else form
    }
  }
}

private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
