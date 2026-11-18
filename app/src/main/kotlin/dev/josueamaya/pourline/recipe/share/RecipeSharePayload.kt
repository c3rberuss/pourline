package dev.josueamaya.pourline.recipe.share

import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.RecipeValidator
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.share.ShareFormat.TimeUnit
import dev.josueamaya.pourline.recipe.withDerivedWater

/**
 * Cuánto se quita del payload para que quepa (D6, enmienda 1 de SPEC-009): las notas, las instrucciones de los últimos
 * [droppedInstructions] pasos que la tienen (en orden de `startMs`) y, solo cuando ya no queda ninguna instrucción, los
 * nombres de los pasos.
 */
internal data class TrimPlan(val notes: Boolean = false, val droppedInstructions: Int = 0, val stageNames: Boolean = false) {
  companion object {
    val NONE = TrimPlan()
  }
}

/**
 * El payload binario, lo que se quitó para escribirlo y cuántas instrucciones se quitaron ([droppedInstructions]) y se
 * conservaron ([keptInstructions]).
 */
internal class EncodedPayload(val bytes: ByteArray, val trimmed: Set<TrimmedPart>, val droppedInstructions: Int, val keptInstructions: Int)

/**
 * Los recortes de [recipe] de menos a más, sin repetir: nada, sin notas, sin la instrucción del último paso que la tenga, sin
 * las dos últimas... hasta sin ninguna, y por fin sin los nombres. Un recorte que no quita nada (sin notas, o sin
 * instrucciones o nombres) no aparece.
 */
internal fun trimPlans(recipe: Recipe): Sequence<TrimPlan> = sequence {
  val stages = recipe.stages
  val hasNotes = !recipe.notes.isNullOrBlank()
  val instructions = stages.count { !it.instruction.isNullOrBlank() }
  yield(TrimPlan.NONE)
  if (hasNotes) yield(TrimPlan(notes = true))
  for (k in 1..instructions) yield(TrimPlan(notes = hasNotes, droppedInstructions = k))
  if (stages.any { !it.name.isNullOrBlank() }) yield(TrimPlan(notes = hasNotes, droppedInstructions = instructions, stageNames = true))
}

/** Mayor valor en unidades de tiempo que se acepta al decodificar: evita desbordar al multiplicar. */
private const val MAX_TIME_UNITS = 1_000_000_000L

/**
 * El payload `v=1` del §3.1 de [recipe]. [grinder] es el molino vivo de `recipe.grind` (`null` si ya no existe). No valida
 * la receta: quien llama (`RecipeShareCodec.encode`) lo hace antes.
 */
internal fun encodePayload(recipe: Recipe, grinder: Grinder?, trim: TrimPlan): EncodedPayload {
  val stages = recipe.stages.sortedBy { it.startMs }
  val notes = recipe.notes?.takeIf { it.isNotBlank() }
  val hasNames = stages.any { !it.name.isNullOrBlank() }
  val withInstruction = stages.indices.filter { !stages[it].instruction.isNullOrBlank() }
  val dropped = withInstruction.takeLast(trim.droppedInstructions).toSet()
  val trimNotes = notes != null && trim.notes
  val trimInstructions = dropped.isNotEmpty()
  val trimNames = hasNames && trim.stageNames
  val writeNotes = if (trim.notes) null else notes
  val keepNames = !trim.stageNames
  val methodOther = recipe.methodOther?.takeIf { recipe.method == BrewMethod.OTHER && it.isNotBlank() }
  val grind = sharedGrindOf(recipe.grind, grinder)
  val unit = timeUnitOf(stages)

  var flags = unit.code shl ShareFormat.TIME_UNIT_SHIFT
  if (recipe.method != null) flags = flags or ShareFormat.F_METHOD
  if (methodOther != null) flags = flags or ShareFormat.F_METHOD_OTHER
  if (recipe.waterTempC != null) flags = flags or ShareFormat.F_TEMP
  if (writeNotes != null) flags = flags or ShareFormat.F_NOTES
  if (grind != null) flags = flags or ShareFormat.F_GRIND
  if (trimNotes) flags = flags or ShareFormat.F_TRIMMED_NOTES
  if (trimInstructions) flags = flags or ShareFormat.F_TRIMMED_INSTRUCTIONS
  if (trimNames) flags = flags or ShareFormat.F_TRIMMED_STAGE_NAMES

  val out = ByteWriter()
  out.uvar(flags.toLong())
  out.str(recipe.name)
  out.uvar(tenths(recipe.doseG))
  recipe.method?.let { out.uvar(ShareFormat.methodCode(it).toLong()) }
  methodOther?.let { out.str(it) }
  recipe.waterTempC?.let { out.uvar(tenths(it)) }
  writeNotes?.let { out.str(it) }
  grind?.let { writeGrind(out, it) }
  out.uvar(stages.size.toLong())
  var previousEndMs = 0L
  var accumulatedTenths = 0L
  for ((index, stage) in stages.withIndex()) {
    val name = if (keepNames) stage.name?.takeIf { it.isNotBlank() } else null
    val instruction = if (index !in dropped) stage.instruction?.takeIf { it.isNotBlank() } else null
    var sflags = when (stage.type) {
      StageType.POUR -> 0
      StageType.SWIRL -> 1
      StageType.WAIT -> 2
    }
    if (name != null) sflags = sflags or ShareFormat.S_NAME
    if (instruction != null) sflags = sflags or ShareFormat.S_INSTRUCTION
    out.uvar(sflags.toLong())
    out.uvar((stage.startMs - previousEndMs) / unit.ms)
    out.uvar(stage.durationMs / unit.ms)
    if (stage.type == StageType.POUR) {
      val target = tenths(stage.targetWaterG)
      out.uvar(target - accumulatedTenths)
      accumulatedTenths = target
    }
    name?.let { out.str(it) }
    instruction?.let { out.str(it) }
    previousEndMs = stage.endMs
  }
  val trimmed = buildSet {
    if (trimNotes) add(TrimmedPart.NOTES)
    if (trimInstructions) add(TrimmedPart.INSTRUCTIONS)
    if (trimNames) add(TrimmedPart.STAGE_NAMES)
  }
  return EncodedPayload(out.toByteArray(), trimmed, dropped.size, withInstruction.size - dropped.size)
}

private fun tenths(value: Double): Long = Math.round(value * 10)

/** La mayor unidad (s, 0,1 s o ms) que divide todos los inicios y duraciones, y por tanto todos los huecos. */
private fun timeUnitOf(stages: List<RecipeStage>): TimeUnit =
  TimeUnit.entries.first { unit -> stages.all { it.startMs % unit.ms == 0L && it.durationMs % unit.ms == 0L } }

/** La molienda que viaja, o `null` si no hay nada que decir (D7). */
private fun sharedGrindOf(grind: GrindSetting?, grinder: Grinder?): SharedGrind? {
  if (grind == null || grind.isEmpty) return null
  if (grind.grinderId != null && grinder != null) {
    return SharedGrind(grinder.catalogKey, grinder.brand, grinder.model, grind.value, null)
  }
  val label = grind.label?.takeIf { it.isNotBlank() } ?: return null
  return SharedGrind(null, null, null, null, label)
}

private fun writeGrind(out: ByteWriter, grind: SharedGrind) {
  var gflags = 0
  if (grind.catalogKey != null) gflags = gflags or ShareFormat.G_CATALOG_KEY
  if (grind.brand != null && grind.model != null) gflags = gflags or ShareFormat.G_BRAND_MODEL
  if (grind.value != null) gflags = gflags or ShareFormat.G_VALUE
  if (grind.label != null) gflags = gflags or ShareFormat.G_LABEL
  out.uvar(gflags.toLong())
  grind.catalogKey?.let { out.str(it) }
  if (grind.brand != null && grind.model != null) {
    out.str(grind.brand)
    out.str(grind.model)
  }
  grind.value?.let { out.svar(Math.round(it * 100)) }
  grind.label?.let { out.str(it) }
}

/** Lee un payload `v=1`. Cualquier byte o bit inesperado es `Corrupt`; una receta que no pasa el validador, `Invalid`. */
internal fun decodePayload(bytes: ByteArray): DecodeResult = try {
  parsePayload(ByteReader(bytes))
} catch (_: MalformedPayload) {
  DecodeResult.Corrupt
}

private fun parsePayload(reader: ByteReader): DecodeResult {
  val flags = reader.uvarNonNegative()
  if (flags and ShareFormat.FLAGS_DEFINED.toLong().inv() != 0L) throw MalformedPayload("bit de flags sin definir")
  val unit = TimeUnit.fromCode(((flags shr ShareFormat.TIME_UNIT_SHIFT) and 0b11).toInt()) ?: throw MalformedPayload("unidad de tiempo")
  val name = reader.str()
  val doseG = reader.uvarNonNegative() / 10.0
  val method = if (flags has ShareFormat.F_METHOD) {
    ShareFormat.methodFromCode(reader.uvar()) ?: throw MalformedPayload("método desconocido")
  } else {
    null
  }
  val methodOther = if (flags has ShareFormat.F_METHOD_OTHER) {
    if (method != BrewMethod.OTHER) throw MalformedPayload("methodOther sin método OTHER")
    reader.str().takeIf { it.isNotBlank() }
  } else {
    null
  }
  val waterTempC = if (flags has ShareFormat.F_TEMP) reader.uvarNonNegative() / 10.0 else null
  val notes = if (flags has ShareFormat.F_NOTES) reader.str().takeIf { it.isNotBlank() } else null
  val grind = if (flags has ShareFormat.F_GRIND) readGrind(reader) else null
  val stageCount = reader.uvarNonNegative()
  if (stageCount !in 1..RecipeValidator.MAX_STAGES.toLong()) return DecodeResult.Invalid
  val stages = ArrayList<RecipeStage>(stageCount.toInt())
  var previousEndMs = 0L
  var accumulatedTenths = 0L
  repeat(stageCount.toInt()) {
    val sflags = reader.uvarNonNegative()
    if (sflags and ShareFormat.SFLAGS_DEFINED.toLong().inv() != 0L) throw MalformedPayload("bit de sflags sin definir")
    val type = when ((sflags and ShareFormat.S_TYPE_MASK.toLong()).toInt()) {
      0 -> StageType.POUR
      1 -> StageType.SWIRL
      2 -> StageType.WAIT
      else -> throw MalformedPayload("tipo de paso desconocido")
    }
    val gap = reader.uvarNonNegative()
    val duration = reader.uvarNonNegative()
    if (gap > MAX_TIME_UNITS || duration > MAX_TIME_UNITS) throw MalformedPayload("tiempo fuera de rango")
    val water = if (type == StageType.POUR) reader.uvarNonNegative() else 0L
    if (water > MAX_TIME_UNITS) throw MalformedPayload("agua fuera de rango")
    accumulatedTenths += water
    val stageName = if (sflags has ShareFormat.S_NAME) reader.str().takeIf { it.isNotBlank() } else null
    val instruction = if (sflags has ShareFormat.S_INSTRUCTION) reader.str().takeIf { it.isNotBlank() } else null
    val startMs = previousEndMs + gap * unit.ms
    val durationMs = duration * unit.ms
    stages += RecipeStage(
      name = stageName,
      startMs = startMs,
      targetWaterG = if (type == StageType.POUR) accumulatedTenths / 10.0 else 0.0,
      durationMs = durationMs,
      type = type,
      instruction = instruction,
    )
    previousEndMs = startMs + durationMs
  }
  if (reader.remaining != 0) throw MalformedPayload("bytes sobrantes")
  val recipe = Recipe(
    name = name,
    method = method,
    methodOther = methodOther,
    waterTempC = waterTempC,
    notes = notes,
    doseG = doseG,
    stages = stages.withDerivedWater(),
  )
  if (RecipeValidator.validate(recipe).isNotEmpty()) return DecodeResult.Invalid
  val trimmed = buildSet {
    if (flags has ShareFormat.F_TRIMMED_NOTES) add(TrimmedPart.NOTES)
    if (flags has ShareFormat.F_TRIMMED_INSTRUCTIONS) add(TrimmedPart.INSTRUCTIONS)
    if (flags has ShareFormat.F_TRIMMED_STAGE_NAMES) add(TrimmedPart.STAGE_NAMES)
  }
  return DecodeResult.Ok(SharedRecipe(recipe, grind, trimmed))
}

private fun readGrind(reader: ByteReader): SharedGrind {
  val gflags = reader.uvarNonNegative()
  if (gflags == 0L || gflags and ShareFormat.GFLAGS_DEFINED.toLong().inv() != 0L) throw MalformedPayload("gflags inválidos")
  val catalogKey = if (gflags has ShareFormat.G_CATALOG_KEY) reader.str() else null
  var brand: String? = null
  var model: String? = null
  if (gflags has ShareFormat.G_BRAND_MODEL) {
    brand = reader.str()
    model = reader.str()
  }
  val value = if (gflags has ShareFormat.G_VALUE) reader.svar() / 100.0 else null
  val label = if (gflags has ShareFormat.G_LABEL) reader.str() else null
  return SharedGrind(catalogKey, brand, model, value, label)
}

private infix fun Long.has(bit: Int): Boolean = this and bit.toLong() != 0L
