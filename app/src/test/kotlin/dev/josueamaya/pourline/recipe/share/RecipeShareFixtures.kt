package dev.josueamaya.pourline.recipe.share

import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.grinder.GrindUnit
import dev.josueamaya.pourline.grinder.GrindUnitLevel
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.withDerivedWater
import java.util.Random

/** Contrato `v=1`: la V60 clásica de `FakeRecipeTexts("es")` antes de `deflate`, escrita a mano contra el §3.1 (D23). */
const val GOLDEN_V60_PAYLOAD =
  "010C56363020636CC3A173696361960100050C000A900305426C6F6F6D0269310A00230269320C0014CC080C322EC2BA207665727469646F0269330A00190269340C0019E8070C332E6572207665727469646F026935"

/** El URI completo de la V60 clásica, congelado en F1. Una app futura tiene que seguir leyéndolo. */
const val GOLDEN_V60_URI =
  "pourline://recipe?v=1&d=Y-QJMzNQSM45vLA4MzlxGiMDKw8D1wRmVqec_PxcpkxDLgZlpkwjHgaRMxw8RnqHdimUpRaVZKbkM2UaczFIMmWa8DBIvmDnMdZLLUJImQIA"

/** El URI de 331 caracteres (la longitud máxima) de la prueba del escáner de F0: 20 pasos, nombre de 60 y notas de 78. */
const val WORST_CASE_URI =
  "pourline://recipe?v=1&d=k7UJSk1OLUlUSElVKCgqTU0CsXIUUouTE_NSixSS8_MUSvMU8vJzk4pSFXJLKxVyEovS80Gqi1OLU08wMixi9_MrDCuPDLYwDAsw90gydImwKAhIMc3OqHA1Lqj0TPcuSAzNcyzKMTOOqMwOj0rN9PNzzsxMNE7Mi8izzDbOLi61zPU0CfLPy7GwDM61tGTnL8nMTc3NL0qNTzaOLyjK5wiB8vmdM1KLS_JKSxScjRUCivIPqIowMIisYWJiUIbSIP4JRgh_AjOYzz8NyJdkYGAFiXMB-d8h6uWg8qwgPlCcFaROBAA"

/** Un molino del catálogo con el que se comparte la molienda. */
val catalogGrinder = Grinder(
  id = 7,
  catalogKey = "timemore_c3_pro",
  brand = "Timemore",
  model = "Chestnut C3 Pro",
  units = listOf(GrindUnit.CLICKS).map { GrindUnitLevel(it) },
  scaleMin = 0.0,
  scaleMax = 40.0,
  owned = true,
)

/** El molino Femobook A2 de la base de datos del móvil del usuario: vueltas y clics (40 por vuelta), de 0 a 120. */
val femobookA2 = Grinder(
  id = 9,
  catalogKey = "femobook-a2",
  brand = "Femobook",
  model = "A2",
  units = listOf(GrindUnitLevel(GrindUnit.ROTATIONS), GrindUnitLevel(GrindUnit.CLICKS, perParent = 40)),
  scaleMin = 0.0,
  scaleMax = 120.0,
  owned = true,
)

private val letters = ('a'..'z') + ('A'..'Z') + ('0'..'9')

/** Texto aleatorio (poco comprimible) y determinista de [length] caracteres. */
fun randomText(length: Int, seed: Long = 1): String {
  val random = Random(seed)
  return String(CharArray(length) { letters[random.nextInt(letters.size)] })
}

/** El molino vivo [catalogGrinder] con el ajuste 24. */
fun catalogGrind(value: Double? = 24.0) = GrindSetting(catalogGrinder.id, value, "Timemore Chestnut C3 Pro · 24")

/**
 * [count] pasos que alternan `POUR` y `WAIT`, con tiempos múltiplos de [unitMs] (1 000 = segundos) y el agua en
 * décimas exactas. Sin nombres ni instrucciones salvo que se pidan.
 */
fun alternatingStages(
  count: Int = 20,
  unitMs: Long = 1_000,
  names: Boolean = false,
  instructions: Boolean = false,
  swirls: Boolean = false,
): List<RecipeStage> {
  val stages = ArrayList<RecipeStage>()
  var startMs = 0L
  var waterTenths = 0L
  for (i in 0 until count) {
    val type = when {
      i % 2 == 0 -> StageType.POUR
      swirls && i % 4 == 1 -> StageType.SWIRL
      else -> StageType.WAIT
    }
    val duration = when (unitMs) {
      1_000L -> (5L + (i % 7) * 3) * 1_000
      100L -> (11L + i % 9) * 100
      else -> 1_001L + i * 7
    }
    if (type == StageType.POUR) waterTenths += 100 + (i * 7 % 40)
    stages += RecipeStage(
      name = if (names && i % 2 == 0) "Paso $i" else null,
      startMs = startMs,
      targetWaterG = waterTenths / 10.0,
      durationMs = duration,
      type = type,
      instruction = if (instructions && i % 3 == 0) "Vierte en círculos $i" else null,
    )
    startMs += duration + if (i % 5 == 4) unitMs else 0
  }
  return stages.withDerivedWater()
}

/** La receta de peor caso del §6: 20 pasos sin textos, nombre de 60 caracteres, método, temperatura y molienda de catálogo. */
fun worstCaseRecipe(unitMs: Long = 1_000) = Recipe(
  name = randomText(60, seed = 3),
  method = BrewMethod.V60,
  waterTempC = 93.0,
  doseG = 20.0,
  grind = catalogGrind(),
  stages = alternatingStages(unitMs = unitMs),
)

/** Una receta con todos los campos: `OTHER` con texto, temperatura, notas y 20 pasos con nombre, instrucción y `SWIRL`. */
fun everyField(unitMs: Long = 1_000) = Recipe(
  name = "Receta completa ñ",
  method = BrewMethod.OTHER,
  methodOther = "Sifón",
  waterTempC = 93.5,
  notes = "Notas de la receta: molienda media, agua filtrada.",
  doseG = 18.5,
  stages = alternatingStages(unitMs = unitMs, names = true, instructions = true, swirls = true),
)

/** La V60 clásica con notas y con instrucciones largas (poco comprimibles), para probar el recorte. */
fun withLongTexts(notesLength: Int, instructionLength: Int, stageCount: Int = 5): Recipe {
  val stages = alternatingStages(count = stageCount, names = true).mapIndexed { i, stage ->
    stage.copy(instruction = randomText(instructionLength, seed = 10L + i))
  }
  return Recipe(
    name = "Con textos largos",
    method = BrewMethod.V60,
    doseG = 15.0,
    notes = randomText(notesLength, seed = 99),
    stages = stages,
  )
}

private const val FIRST_POURS = "Vierte despacio y de forma uniforme: este vertido define el dulzor y la acidez."
private const val LAST_POURS = "Vierte despacio y de forma uniforme: este vertido define la intensidad."
private const val WAIT_TEXT = "Espera a que baje el agua."

private fun fourSixStage(name: String?, startS: Long, waterG: Double, durationS: Long, type: StageType, instruction: String) =
  RecipeStage(name, startS * 1_000, waterG, durationS * 1_000, type, instruction)

/**
 * La receta 4:6 equilibrada de intensidad media del Xiaomi del usuario, con los valores literales de su base de datos (D42):
 * 18,8 g, V60, 90 °C, notas, 10 pasos con nombre e instrucción y el molino [femobookA2] con el ajuste 34. Con el códec de
 * la enmienda 1 mide 450 caracteres.
 */
fun fourSixUserRecipe(grinderId: Long = femobookA2.id) = Recipe(
  name = "4:6 · Equilibrado · Media",
  method = BrewMethod.V60,
  waterTempC = 90.0,
  notes = "Generada con el método 4:6: el 40 % del agua define el sabor y el 60 % restante, la intensidad. Molienda gruesa.",
  doseG = 18.8,
  grind = GrindSetting(grinderId, 34.0, "A2 · 0 · 34"),
  stages = listOf(
    fourSixStage("1.er vertido", 0, 64.0, 20, StageType.POUR, FIRST_POURS),
    fourSixStage(null, 20, 64.0, 25, StageType.WAIT, WAIT_TEXT),
    fourSixStage("2.º vertido", 45, 128.0, 20, StageType.POUR, FIRST_POURS),
    fourSixStage(null, 65, 128.0, 25, StageType.WAIT, WAIT_TEXT),
    fourSixStage("3.er vertido", 90, 192.0, 20, StageType.POUR, LAST_POURS),
    fourSixStage(null, 110, 192.0, 25, StageType.WAIT, WAIT_TEXT),
    fourSixStage("4.º vertido", 135, 256.0, 20, StageType.POUR, LAST_POURS),
    fourSixStage(null, 155, 256.0, 25, StageType.WAIT, WAIT_TEXT),
    fourSixStage("5.º vertido", 180, 320.0, 20, StageType.POUR, "Último vertido: sin prisa, hasta el agua total."),
    fourSixStage(null, 200, 320.0, 10, StageType.WAIT, "Deja que drene y retira el filtro."),
  ),
)
