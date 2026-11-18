package dev.josueamaya.pourline

import androidx.navigation3.runtime.NavKey

/**
 * La barra inferior se oculta en la sesión (esté donde esté en la pila: desde el paso 2 se puede abrir Molinos), en los
 * editores a pantalla completa de la cima (D31, D5) y en la vista previa de una receta importada (D15 de SPEC-009).
 */
fun showsBottomBar(stack: List<NavKey>): Boolean {
  if (stack.any { it is Session }) return false
  val top = stack.lastOrNull()
  return top !is CoffeeEdit && top !is RecipeEdit && top !is BrewEdit && top !is GrinderEdit && top !is GrindConverter &&
    top !is RecipeImport
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

/**
 * Abre la vista previa de [uri]. Si la cima ya es una vista previa la sustituye en lugar de apilar otra: dos recetas
 * seguidas no dejan vistas previas debajo (D15 de SPEC-009).
 */
fun MutableList<NavKey>.openImport(uri: String) {
  if (lastOrNull() is RecipeImport) removeAt(lastIndex)
  add(RecipeImport(uri))
}

/** Tocar la pestaña activa vacía su pila hasta la raíz. */
fun MutableList<NavKey>.popToRoot() {
  while (size > 1) removeAt(lastIndex)
}
