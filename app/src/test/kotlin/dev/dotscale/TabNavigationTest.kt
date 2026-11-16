package dev.dotscale

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las reglas puras de la barra inferior y de "Atrás" (D31). */
class TabNavigationTest {

  @Test
  fun theBottomBarShowsOnRootsListsAndDetails() {
    listOf<NavKey>(
      Prepare,
      Settings,
      Coffees,
      CoffeeDetail(1),
      Recipes,
      History,
      BrewDetail(1),
      ComparePick(1),
      BrewCompare(1, 2),
    ).forEach { assertTrue("$it debería llevar barra", showsBottomBar(it)) }
  }

  @Test
  fun theBottomBarHidesInTheSessionAndFullScreenEditors() {
    listOf<NavKey>(Session, CoffeeEdit(), CoffeeEdit(id = 3), RecipeEdit(), RecipeEdit(id = 2), BrewEdit(4))
      .forEach { assertFalse("$it no debería llevar barra", showsBottomBar(it)) }
  }

  @Test
  fun anEmptyStackKeepsTheBar() {
    assertTrue(showsBottomBar(null))
  }

  @Test
  fun backPopsWhenTheStackHasMoreThanOneEntry() {
    Tab.entries.forEach { assertEquals(BackAction.Pop, backAction(it, stackSize = 2)) }
  }

  @Test
  fun backAtTheRootOfAnotherTabSelectsPrepare() {
    listOf(Tab.COFFEES, Tab.RECIPES, Tab.HISTORY).forEach { assertEquals(BackAction.SelectPrepare, backAction(it, stackSize = 1)) }
  }

  @Test
  fun backAtTheRootOfPrepareExits() {
    assertEquals(BackAction.Exit, backAction(Tab.PREPARE, stackSize = 1))
  }

  @Test
  fun everyTabRootIsItsFirstKeyAndTagsAreUnique() {
    assertEquals(listOf<NavKey>(Prepare, Coffees, Recipes, History), Tab.entries.map { it.root })
    assertEquals(listOf("tab_prepare", "tab_coffees", "tab_recipes", "tab_history"), Tab.entries.map { it.testTag })
  }

  @Test
  fun popToRootLeavesOnlyTheRoot() {
    val stack = mutableListOf<NavKey>(Coffees, CoffeeDetail(1), BrewDetail(2), CoffeeEdit())
    stack.popToRoot()
    assertEquals(listOf<NavKey>(Coffees), stack)
  }

  @Test
  fun popToRootOnARootDoesNothing() {
    val stack = mutableListOf<NavKey>(History)
    stack.popToRoot()
    assertEquals(listOf<NavKey>(History), stack)
  }
}
