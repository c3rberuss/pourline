package dev.dotscale.recipe

import android.content.Context
import dev.dotscale.R

/** [RecipeTexts] con los recursos de la aplicación, leídos en cada llamada para seguir el idioma actual. */
class ResourcesRecipeTexts(context: Context) : RecipeTexts {
  private val resources = context.applicationContext.resources

  override fun builtInName(key: String): String = resources.getString(
    when (key) {
      BuiltInRecipes.V60_CLASSIC -> R.string.recipe_builtin_v60_classic
      BuiltInRecipes.FOUR_SIX -> R.string.recipe_builtin_four_six
      else -> error("Unknown built-in recipe $key")
    },
  )

  override fun builtInStageNames(key: String): List<String> = resources.getStringArray(
    when (key) {
      BuiltInRecipes.V60_CLASSIC -> R.array.recipe_builtin_v60_classic_stages
      BuiltInRecipes.FOUR_SIX -> R.array.recipe_builtin_four_six_stages
      else -> error("Unknown built-in recipe $key")
    },
  ).toList()

  override fun copyName(name: String): String = resources.getString(R.string.recipe_copy_name, name)

  override fun fromBrewName(base: String): String = resources.getString(R.string.recipe_from_brew_name, base)
}
