package dev.dotscale

import androidx.navigation3.runtime.NavKey

/**
 * La barra inferior se oculta en la sesión (esté donde esté en la pila: desde el paso 2 se puede abrir Molinos) y
 * en los editores a pantalla completa de la cima (D31, D5).
 */
fun showsBottomBar(stack: List<NavKey>): Boolean {
  if (stack.any { it is Session }) return false
  val top = stack.lastOrNull()
  return top !is CoffeeEdit && top !is RecipeEdit && top !is BrewEdit && top !is GrinderEdit
}

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
