package dev.josueamaya.pourline.recipe.share

import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.StageType
import java.util.Locale

/** La clave con la que se comparan los nombres: sin espacios en los extremos ni distinción de mayúsculas. */
internal fun nameKey(name: String): String = name.trim().lowercase(Locale.ROOT)

/**
 * Si [a] y [b] son la misma receta (§3.6): mismo nombre (por [nameKey]), dosis, método, temperatura y pasos (tipo,
 * nombre, inicio, duración, agua de `POUR` e instrucción). Las notas, `methodOther` y la molienda no cuentan.
 */
fun isIdentical(a: Recipe, b: Recipe): Boolean =
  nameKey(a.name) == nameKey(b.name) &&
    a.doseG == b.doseG &&
    a.method == b.method &&
    a.waterTempC == b.waterTempC &&
    a.stages.size == b.stages.size &&
    a.stages.zip(b.stages).all { (x, y) -> sameStage(x, y) }

private fun sameStage(a: RecipeStage, b: RecipeStage): Boolean =
  a.type == b.type &&
    a.name.normalized() == b.name.normalized() &&
    a.instruction.normalized() == b.instruction.normalized() &&
    a.startMs == b.startMs &&
    a.durationMs == b.durationMs &&
    (a.type != StageType.POUR || a.targetWaterG == b.targetWaterG)

/** El texto recortado, y `null` si queda vacío: un nombre en blanco es no tener nombre. */
private fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/** [name] recortado, con " (2)", " (3)"… si ya hay una receta con ese nombre en [existing]. */
fun uniqueName(name: String, existing: Collection<String>): String {
  val trimmed = name.trim()
  val taken = existing.mapTo(HashSet()) { nameKey(it) }
  if (nameKey(trimmed) !in taken) return trimmed
  var n = 2
  while (nameKey("$trimmed ($n)") in taken) n++
  return "$trimmed ($n)"
}
