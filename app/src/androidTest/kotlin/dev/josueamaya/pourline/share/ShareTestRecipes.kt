package dev.josueamaya.pourline.share

import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.StageType

/** Las notas de la receta 4:6 generada por la app. */
internal const val FOUR_SIX_NOTES =
  "Generada con el método 4:6: el 40 % del agua define el sabor y el 60 % restante, la intensidad. Molienda gruesa."

private const val FIRST_TWO_POURS = "Vierte despacio y de forma uniforme: este vertido define el dulzor y la acidez."
private const val LAST_TWO_POURS = "Vierte despacio y de forma uniforme: este vertido define la intensidad."
private const val WAIT_TEXT = "Espera a que baje el agua."

private fun fourSixStage(name: String?, startS: Long, waterG: Double, durationS: Long, type: StageType, instruction: String) =
  RecipeStage(name, startS * 1_000, waterG, durationS * 1_000, type, instruction)

/**
 * La receta 4:6 equilibrada de intensidad media que se recortaba en el Xiaomi del usuario, con los valores literales de su
 * base de datos (D42): 18,8 g, V60, 90 °C, 10 pasos con nombre e instrucción y el molino Femobook A2 con el ajuste 34.
 * Es la misma que `fourSixUserRecipe` de los tests JVM; los instrumentados no ven esas fixtures.
 */
internal fun fourSixUserRecipe(grinderId: Long? = null) = Recipe(
  name = "4:6 · Equilibrado · Media",
  method = BrewMethod.V60,
  waterTempC = 90.0,
  notes = FOUR_SIX_NOTES,
  doseG = 18.8,
  grind = GrindSetting(grinderId, 34.0, "A2 · 0 · 34"),
  stages = listOf(
    fourSixStage("1.er vertido", 0, 64.0, 20, StageType.POUR, FIRST_TWO_POURS),
    fourSixStage(null, 20, 64.0, 25, StageType.WAIT, WAIT_TEXT),
    fourSixStage("2.º vertido", 45, 128.0, 20, StageType.POUR, FIRST_TWO_POURS),
    fourSixStage(null, 65, 128.0, 25, StageType.WAIT, WAIT_TEXT),
    fourSixStage("3.er vertido", 90, 192.0, 20, StageType.POUR, LAST_TWO_POURS),
    fourSixStage(null, 110, 192.0, 25, StageType.WAIT, WAIT_TEXT),
    fourSixStage("4.º vertido", 135, 256.0, 20, StageType.POUR, LAST_TWO_POURS),
    fourSixStage(null, 155, 256.0, 25, StageType.WAIT, WAIT_TEXT),
    fourSixStage("5.º vertido", 180, 320.0, 20, StageType.POUR, "Último vertido: sin prisa, hasta el agua total."),
    fourSixStage(null, 200, 320.0, 10, StageType.WAIT, "Deja que drene y retira el filtro."),
  ),
)
