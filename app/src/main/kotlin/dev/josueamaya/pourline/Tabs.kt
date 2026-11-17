package dev.josueamaya.pourline

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import dev.josueamaya.pourline.ui.components.AntIcons

/** Las pestañas de la barra inferior; cada una tiene su pila de navegación y su raíz. */
enum class Tab(val root: NavKey, @StringRes val label: Int, val testTag: String) {
  PREPARE(Prepare, R.string.tab_prepare, "tab_prepare"),
  COFFEES(Coffees, R.string.tab_coffees, "tab_coffees"),
  RECIPES(Recipes, R.string.tab_recipes, "tab_recipes"),
  HISTORY(History, R.string.tab_history, "tab_history"),
  TOOLS(Tools, R.string.tab_tools, "tab_tools"),
  ;

  val icon: ImageVector
    get() = when (this) {
      PREPARE -> AntIcons.Dripper
      COFFEES -> AntIcons.Coffee
      RECIPES -> AntIcons.Recipe
      HISTORY -> AntIcons.History
      TOOLS -> AntIcons.Calculator
    }
}
