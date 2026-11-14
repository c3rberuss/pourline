package dev.dotscale

import androidx.navigation3.runtime.NavKey

/** La barra inferior se oculta en la sesión y en los editores a pantalla completa (D31). */
fun showsBottomBar(key: NavKey?): Boolean = key !is Session && key !is CoffeeEdit && key !is RecipeEdit && key !is BrewEdit

/** Qué hace "Atrás" en la pestaña [Tab] con una pila de [backAction] entradas. */
sealed interface BackAction {
  /** Quita la cima de la pila de la pestaña. */
  data object Pop : BackAction

  /** En la raíz de otra pestaña: vuelve a Preparar. */
  data object SelectPrepare : BackAction

  /** En la raíz de Preparar: sale al sistema. */
  data object Exit : BackAction
}

fun backAction(tab: Tab, stackSize: Int): BackAction = when {
  stackSize > 1 -> BackAction.Pop
  tab == Tab.PREPARE -> BackAction.Exit
  else -> BackAction.SelectPrepare
}

/** Tocar la pestaña activa vacía su pila hasta la raíz. */
fun MutableList<NavKey>.popToRoot() {
  while (size > 1) removeAt(lastIndex)
}
