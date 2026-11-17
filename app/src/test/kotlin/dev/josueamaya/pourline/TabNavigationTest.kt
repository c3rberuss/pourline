package dev.josueamaya.pourline

import androidx.navigation3.runtime.NavKey
import dev.josueamaya.pourline.tools.FourSixParams
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
      Grinders,
      GrinderCatalog,
      GrinderDetail(1),
      Tools,
      RatioTool,
      FourSixTool,
    ).forEach { assertTrue("$it debería llevar barra", showsBottomBar(listOf(Prepare, it))) }
  }

  @Test
  fun theBottomBarHidesInTheSessionAndFullScreenEditors() {
    listOf<NavKey>(Session, CoffeeEdit(), CoffeeEdit(id = 3), RecipeEdit(), RecipeEdit(id = 2), RecipeEdit(fourSix = FourSixParams(20.0, 15.0)), BrewEdit(4), GrinderEdit(), GrinderEdit(id = 2, openRanges = true))
      .forEach { assertFalse("$it no debería llevar barra", showsBottomBar(listOf(Prepare, it))) }
  }

  @Test
  fun theBottomBarHidesWhenASessionIsAnywhereInTheStack() {
    assertFalse(showsBottomBar(listOf(Prepare, Session)))
    // Desde el paso 2 se abre Molinos (o su detalle) encima de la sesión: la barra sigue oculta.
    assertFalse(showsBottomBar(listOf(Prepare, Session, Settings, Grinders)))
    assertFalse(showsBottomBar(listOf(Prepare, Session, Settings, Grinders, GrinderDetail(1))))
    assertTrue(showsBottomBar(listOf(Prepare, Settings, Grinders)))
  }

  @Test
  fun anEmptyStackKeepsTheBar() {
    assertTrue(showsBottomBar(emptyList()))
  }

  @Test
  fun backPopsWhenTheStackHasMoreThanOneEntry() {
    Tab.entries.forEach { assertEquals(BackAction.Pop, backAction(it, stackSize = 2)) }
  }

  @Test
  fun backAtTheRootOfAnotherTabSelectsPrepare() {
    listOf(Tab.COFFEES, Tab.RECIPES, Tab.HISTORY, Tab.TOOLS).forEach { assertEquals(BackAction.SelectPrepare, backAction(it, stackSize = 1)) }
  }

  @Test
  fun backAtTheRootOfPrepareExits() {
    assertEquals(BackAction.Exit, backAction(Tab.PREPARE, stackSize = 1))
  }

  @Test
  fun everyTabRootIsItsFirstKeyAndTagsAreUnique() {
    assertEquals(listOf<NavKey>(Prepare, Coffees, Recipes, History, Tools), Tab.entries.map { it.root })
    assertEquals(listOf("tab_prepare", "tab_coffees", "tab_recipes", "tab_history", "tab_tools"), Tab.entries.map { it.testTag })
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
