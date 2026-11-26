package dev.josueamaya.pourline.recipe

import android.content.Context
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.history.LegacyBuiltIns

/** [RecipeTexts] con los recursos de la aplicación, leídos en cada llamada para seguir el idioma actual. */
class ResourcesRecipeTexts(context: Context) : RecipeTexts {
  private val resources = context.applicationContext.resources

  override fun builtInName(key: String): String = resources.getString(
    when (key) {
      LegacyBuiltIns.V60_CLASSIC -> R.string.recipe_builtin_v60_classic
      LegacyBuiltIns.FOUR_SIX -> R.string.recipe_builtin_four_six
      else -> error("Unknown built-in recipe $key")
    },
  )

  override fun builtInStageNames(key: String): List<String?> = resources.getStringArray(
    when (key) {
      LegacyBuiltIns.V60_CLASSIC -> R.array.recipe_builtin_v60_classic_stages
      LegacyBuiltIns.FOUR_SIX -> R.array.recipe_builtin_four_six_stages
      else -> error("Unknown built-in recipe $key")
    },
  ).map { it.takeIf(String::isNotBlank) }

  override fun builtInStageInstructions(key: String): List<String?> = resources.getStringArray(
    when (key) {
      LegacyBuiltIns.V60_CLASSIC -> R.array.recipe_builtin_v60_classic_instructions
      LegacyBuiltIns.FOUR_SIX -> R.array.recipe_builtin_four_six_instructions
      else -> error("Unknown built-in recipe $key")
    },
  ).map { it.takeIf(String::isNotBlank) }

  override fun copyName(name: String): String = resources.getString(R.string.recipe_copy_name, name)

  override fun fromBrewName(base: String): String = resources.getString(R.string.recipe_from_brew_name, base)
}
