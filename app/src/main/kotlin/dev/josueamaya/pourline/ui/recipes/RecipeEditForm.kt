package dev.josueamaya.pourline.ui.recipes

import dev.josueamaya.pourline.grinder.GrindChoice
import dev.josueamaya.pourline.grinder.toChoice
import dev.josueamaya.pourline.grinder.toSettingUnlabeled
import dev.josueamaya.pourline.grinder.withGrinder
import dev.josueamaya.pourline.grinder.withValue
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.Adjustment
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeDraft
import dev.josueamaya.pourline.recipe.RecipeError
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.RecipeValidator
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.withDerivedWater
import dev.josueamaya.pourline.ui.history.TempResult
import dev.josueamaya.pourline.ui.history.parseWaterTemp

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
  INSTRUCTION_TOO_LONG,

  /** Menos de 1 o más de 20 pasos. */
  STAGE_COUNT,

  /** Ningún paso es de tipo Verter. */
  NO_POUR,
}

data class StageFieldErrors(
  val start: FieldError? = null,
  val water: FieldError? = null,
  val duration: FieldError? = null,
  val instruction: FieldError? = null,
) {
  val any: Boolean get() = start != null || water != null || duration != null || instruction != null
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

/**
 * Una fila de paso tal como se escribe: inicio `m:ss` o segundos, "Hasta" en g (solo en `POUR`) y duración en s.
 * En `SWIRL`, `WAIT` y `ACTION`, [water] va vacío: el agua es la del vertido anterior y no se pide.
 */
data class StageForm(
  val name: String = "",
  val start: String = "",
  val water: String = "",
  val duration: String = "",
  val type: StageType = StageType.POUR,
  val instruction: String = "",
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
  val grind: GrindChoice = GrindChoice.None,
  val waterTemp: String = "",
  val notes: String = "",
  val dose: String = "",
  val stages: List<StageForm> = listOf(StageForm(start = "0:00", water = "50", duration = "10")),
) {
  val canAddStage: Boolean get() = stages.size < RecipeValidator.MAX_STAGES
  val canRemoveStage: Boolean get() = stages.size > 1

  fun isDirty(initial: RecipeEditForm): Boolean = this != initial

  /** Elegir otro molino deja el ajuste en `null` (D16); `null` es "Sin molino". */
  fun withGrinder(grinderId: Long?): RecipeEditForm = copy(grind = grind.withGrinder(grinderId))

  fun withGrindValue(value: Double): RecipeEditForm = copy(grind = grind.withValue(value))

  /** Un `POUR` que empieza donde acaba el paso anterior, con el agua del último `POUR` más 50 g y 10 s. */
  fun addStage(): RecipeEditForm {
    if (!canAddStage) return this
    val previous = stages.lastOrNull()
    val previousStart = previous?.let { parseStart(it.start) }
    val previousDuration = previous?.let { parseSeconds(it.duration) }
    val next = StageForm(
      start = if (previousStart != null && previousDuration != null) formatStart(previousStart + previousDuration) else "",
      water = proposedWater(stages.size).orEmpty(),
      duration = "10",
    )
    return copy(stages = stages + next)
  }

  /**
   * Cambia el tipo de un paso. A `SWIRL`, `WAIT` y `ACTION` vacía el agua (no se pide); a `POUR` propone el agua del último
   * `POUR` anterior más 50 g. Si el tipo no cambia, no toca nada.
   */
  fun setStageType(index: Int, type: StageType): RecipeEditForm {
    val stage = stages.getOrNull(index) ?: return this
    if (stage.type == type) return this
    val water = if (type == StageType.POUR) proposedWater(index).orEmpty() else ""
    return updateStage(index, stage.copy(type = type, water = water))
  }

  /** El último `POUR` antes de [index] más 50 g, o `null` si no se puede leer; 50 g si no hay ninguno. */
  private fun proposedWater(index: Int): String? {
    val previousPour = stages.take(index).lastOrNull { it.type == StageType.POUR } ?: return formatNumber(50.0)
    return parseGrams(previousPour.water)?.let { formatNumber(it + 50) }
  }

  fun removeStage(index: Int): RecipeEditForm =
    if (!canRemoveStage || index !in stages.indices) this else copy(stages = stages.filterIndexed { i, _ -> i != index })

  fun canMoveStageUp(index: Int): Boolean = index in 1..stages.lastIndex

  fun canMoveStageDown(index: Int): Boolean = index in 0 until stages.lastIndex

  fun moveStageUp(index: Int): RecipeEditForm = if (canMoveStageUp(index)) moveStage(index, index - 1) else this

  fun moveStageDown(index: Int): RecipeEditForm = if (canMoveStageDown(index)) moveStage(index, index + 1) else this

  /**
   * Mueve un paso de [from] a [to] y rehace la línea de tiempo y el agua para que la receta siga siendo coherente:
   * - Cada paso conserva su duración y los huecos entre pasos se quedan en su posición (el ritmo de la receta no
   *   cambia); el primer paso empieza donde empezaba el primero de antes. Con algún inicio o duración ilegible, los
   *   inicios no se tocan.
   * - Cada vertido conserva los gramos que vierte (no el "Hasta") y el acumulado se recalcula en el nuevo orden, así el
   *   agua total no cambia. Con algún agua ilegible, el agua no se toca.
   */
  private fun moveStage(from: Int, to: Int): RecipeEditForm {
    fun <T> List<T>.moved(): List<T> = toMutableList().apply { add(to, removeAt(from)) }
    val poured = pouredGrams()?.moved()
    val moved = stages.moved().retimed(stages)
    return copy(stages = if (poured == null) moved else moved.withAccumulatedWater(poured))
  }

  private fun List<StageForm>.retimed(old: List<StageForm>): List<StageForm> {
    val oldStarts = old.map { parseStart(it.start) ?: return this }
    val oldDurations = old.map { parseSeconds(it.duration) ?: return this }
    val durations = map { parseSeconds(it.duration) ?: return this }
    val gaps = oldStarts.indices.map { i -> if (i == 0) oldStarts[0] else oldStarts[i] - (oldStarts[i - 1] + oldDurations[i - 1]) }
    var start = 0L
    return mapIndexed { i, stage ->
      start = if (i == 0) gaps[0] else start + durations[i - 1] + gaps[i]
      stage.copy(start = formatStart(start.coerceAtLeast(0)))
    }
  }

  /** Los gramos que vierte cada paso (`null` en los que no son `POUR`), o `null` si algún vertido es ilegible. */
  private fun pouredGrams(): List<Double?>? {
    var previous = 0.0
    return stages.map { stage ->
      if (stage.type != StageType.POUR) return@map null
      val water = parseGrams(stage.water) ?: return null
      (water - previous).also { previous = water }
    }
  }

  private fun List<StageForm>.withAccumulatedWater(poured: List<Double?>): List<StageForm> {
    var total = 0.0
    return mapIndexed { i, stage ->
      val grams = poured[i] ?: return@mapIndexed stage
      total = Math.round((total + grams) * 10) / 10.0
      stage.copy(water = formatNumber(total))
    }
  }

  /**
   * Reemplaza un paso. Si cambia su duración, los inicios de los pasos siguientes se desplazan lo mismo, de modo que
   * los huecos entre pasos se conservan. Una duración vacía cuenta como 0 s para que borrar y reescribir la cifra
   * desplace en total la diferencia real; con una duración ilegible, o en un inicio ilegible, no se desplaza nada.
   */
  fun updateStage(index: Int, stage: StageForm): RecipeEditForm {
    if (index !in stages.indices) return this
    val shiftMs = durationShiftMs(stages[index].duration, stage.duration)
    return copy(
      stages = stages.mapIndexed { i, old ->
        when {
          i == index -> stage
          i > index && shiftMs != 0L -> old.shiftedBy(shiftMs)
          else -> old
        }
      },
    )
  }

  private fun durationShiftMs(old: String, new: String): Long {
    if (old == new) return 0
    val before = durationForShift(old) ?: return 0
    val after = durationForShift(new) ?: return 0
    return after - before
  }

  private fun durationForShift(text: String): Long? = if (text.isBlank()) 0 else parseSeconds(text)

  private fun StageForm.shiftedBy(shiftMs: Long): StageForm {
    val startMs = parseStart(start) ?: return this
    return copy(start = formatStart((startMs + shiftMs).coerceAtLeast(0)))
  }

  /** Errores por campo: de formato y de [RecipeValidator]. */
  fun errors(): RecipeFormErrors {
    val parsed = parse()
    val validator = RecipeValidator.validate(parsed.recipe)

    fun has(error: RecipeError) = error in validator
    val stageErrors = stages.mapIndexed { i, form ->
      val p = parsed.stages[i]
      val prev = parsed.stages.getOrNull(i - 1)
      val prevPourWater = (0 until i).lastOrNull { stages[it].type == StageType.POUR }?.let { parsed.stages[it].water }
      val isPour = form.type == StageType.POUR
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
          !isPour -> null
          p.water == null -> FieldError.WATER_INVALID
          has(RecipeError.WaterOutOfRange(i)) -> FieldError.WATER_INVALID
          prevPourWater != null && has(RecipeError.WaterNotIncreasing(i)) -> FieldError.WATER_NOT_INCREASING
          else -> null
        },
        duration = when {
          p.duration == null -> FieldError.DURATION_INVALID
          has(RecipeError.DurationOutOfRange(i)) -> FieldError.DURATION_INVALID
          else -> null
        },
        instruction = FieldError.INSTRUCTION_TOO_LONG.takeIf { has(RecipeError.InstructionTooLong(i)) },
      )
    }
    return RecipeFormErrors(
      name = FieldError.NAME_REQUIRED.takeIf { has(RecipeError.NameRequired) },
      dose = FieldError.DOSE_INVALID.takeIf { parsed.dose == null || has(RecipeError.DoseInvalid) },
      temp = FieldError.TEMP_INVALID.takeIf { tempResult == TempResult.Invalid || has(RecipeError.TempInvalid) },
      stages = stageErrors,
      general = when {
        has(RecipeError.StageCountInvalid) -> FieldError.STAGE_COUNT
        has(RecipeError.NoPour) -> FieldError.NO_POUR
        else -> null
      },
    )
  }

  val isValid: Boolean get() = !errors().any

  /** La receta, o `null` si algún campo no es válido. */
  fun toRecipe(id: Long = 0, catalogKey: String? = null): Recipe? {
    if (errors().any) return null
    val recipe = parse().recipe
    return recipe.copy(id = id, catalogKey = catalogKey, stages = recipe.stages.withDerivedWater())
  }

  /** Agua total y ratio en vivo; `null` si el último vertido o la dosis no se pueden leer. */
  fun totalWaterG(): Double? = stages.lastOrNull { it.type == StageType.POUR }?.let { parseGrams(it.water) }

  fun ratio(): Double? {
    val water = totalWaterG() ?: return null
    val dose = parseGrams(dose)?.takeIf { it > 0 } ?: return null
    return water / dose
  }

  /**
   * El tiempo total en vivo: el mayor inicio más duración de los pasos. `null` si algún inicio o alguna duración no se
   * puede leer (una duración vacía tampoco). No mira el tipo ni el agua.
   */
  fun totalDurationMs(): Long? {
    var total = 0L
    for (stage in stages) {
      val start = parseStart(stage.start) ?: return null
      val duration = parseSeconds(stage.duration) ?: return null
      total = maxOf(total, start + duration)
    }
    return total
  }

  private val tempResult: TempResult get() = parseWaterTemp(waterTemp)

  private class ParsedStage(val start: Long?, val water: Double?, val duration: Long?)

  private class Parsed(val recipe: Recipe, val dose: Double?, val stages: List<ParsedStage>)

  /** Los campos que no se leen valen 0 en la receta de trabajo, y sus errores de [RecipeValidator] se descartan. */
  private fun parse(): Parsed {
    val parsedStages = stages.map {
      val water = if (it.type == StageType.POUR) parseGrams(it.water) else 0.0
      ParsedStage(parseStart(it.start), water, parseSeconds(it.duration))
    }
    val dose = parseGrams(dose)
    val method = method
    val recipe = Recipe(
      name = name.trim(),
      method = method,
      methodOther = if (method == BrewMethod.OTHER) methodOther.cleaned() else null,
      grind = grind.toSettingUnlabeled(),
      waterTempC = (tempResult as? TempResult.Valid)?.celsius,
      notes = notes.cleaned(),
      doseG = dose ?: 0.0,
      stages = stages.mapIndexed { i, s ->
        val p = parsedStages[i]
        RecipeStage(s.name.cleaned(), p.start ?: 0, p.water ?: 0.0, p.duration ?: 0, s.type, s.instruction.cleaned())
      },
    )
    return Parsed(recipe, dose, parsedStages)
  }

  companion object {
    fun from(recipe: Recipe) = RecipeEditForm(
      name = recipe.name,
      method = recipe.method,
      methodOther = recipe.methodOther.orEmpty(),
      grind = recipe.grind.toChoice(),
      waterTemp = recipe.waterTempC?.let(::formatNumber).orEmpty(),
      notes = recipe.notes.orEmpty(),
      dose = formatNumber(recipe.doseG),
      stages = recipe.stages.map {
        StageForm(
          name = it.name.orEmpty(),
          start = formatStart(it.startMs),
          water = if (it.type == StageType.POUR) formatNumber(it.targetWaterG) else "",
          duration = Math.round(it.durationMs / 1000.0).toString(),
          type = it.type,
          instruction = it.instruction.orEmpty(),
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
