package dev.josueamaya.pourline.session

import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.grinder.FakeGrindTexts
import dev.josueamaya.pourline.grinder.GrindOutcome
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.grinder.GrinderFixtures.dos
import dev.josueamaya.pourline.grinder.GrinderFixtures.tres
import dev.josueamaya.pourline.grinder.GrinderFixtures.uno
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.history.BrewDetails
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.history.ReferenceType
import dev.josueamaya.pourline.recipe.v60Classic
import dev.josueamaya.pourline.settings.DefaultGrinderStore
import dev.josueamaya.pourline.settings.ReferenceKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GrindSelectionTest {
  private class FakeGrinders(initial: List<Grinder>) : SessionGrinders {
    val owned = MutableStateFlow(initial)

    override fun owned(): Flow<List<Grinder>> = owned

    override fun grinder(id: Long?): Flow<Grinder?> = owned.map { list -> list.firstOrNull { it.id == id } }
  }

  private class FakeDefaults(initial: Long? = null) : DefaultGrinderStore {
    private val id = MutableStateFlow(initial)
    override val defaultGrinderId: StateFlow<Long?> = id

    override fun setDefaultGrinderId(id: Long?) {
      this.id.value = id
    }
  }

  private val target = MutableStateFlow<ActiveTarget?>(null)
  private val grinders = FakeGrinders(listOf(uno, dos, tres))
  private val defaults = FakeDefaults()

  private fun active(id: Long, grind: GrindSetting?, name: String = "Receta $id") = ActiveTarget(
    key = ReferenceKey.Recipe(id),
    type = ReferenceType.RECIPE,
    name = name,
    curve = TargetCurve.fromRecipe(v60Classic()),
    factor = 1.0,
    scaledDoseG = null,
    prefill = BrewDetails(method = BrewMethod.V60),
    sourceGrind = grind,
  )

  private fun TestScope.selection() = GrindSelection(target, grinders, defaults, FakeGrindTexts, backgroundScope)

  @Test
  fun `the explicit choice survives changes of the target with the same key and goes back on another recipe`() = runTest(UnconfinedTestDispatcher()) {
    val selection = selection()
    target.value = active(1, GrindSetting(1, 20.0, null))
    assertEquals(uno, selection.state.value.chosen)

    selection.choose(2)
    assertEquals(dos, selection.state.value.chosen)

    // La misma receta con otra dosis es otra instancia del objetivo, no otra receta.
    target.value = active(1, GrindSetting(1, 20.0, null)).copy(factor = 1.5, scaledDoseG = 24.0)
    assertEquals(dos, selection.state.value.chosen)

    // Otra receta: vuelve a la preselección.
    target.value = active(2, GrindSetting(3, 80.0, null))
    assertEquals(tres, selection.state.value.chosen)
  }

  @Test
  fun `a default that is no longer in my grinders falls to the first`() = runTest(UnconfinedTestDispatcher()) {
    defaults.setDefaultGrinderId(3)
    val selection = selection()
    assertEquals(tres, selection.state.value.chosen)

    grinders.owned.value = listOf(uno, dos)
    assertEquals(uno, selection.state.value.chosen)
  }

  @Test
  fun `current matches what the card publishes`() = runTest(UnconfinedTestDispatcher()) {
    val selection = selection()
    target.value = active(1, GrindSetting(1, 20.0, null))
    selection.choose(2)

    val value = selection.state.value.outcome as GrindOutcome.Value
    assertEquals(28.0, value.value, 0.0)
    assertEquals(GrindSetting(2, 28.0, "Dos · 28 clics"), selection.current())
  }

  @Test
  fun `without owned grinders there is nothing to save`() = runTest(UnconfinedTestDispatcher()) {
    grinders.owned.value = emptyList()
    val selection = selection()
    target.value = active(1, GrindSetting(1, 20.0, null))
    assertNull(selection.current())
    assertEquals(GrindOutcome.NoGrinder, selection.state.value.outcome)
  }

  @Test
  fun `without a target the card has no reference and only grind with remains`() = runTest(UnconfinedTestDispatcher()) {
    val selection = selection()
    assertEquals(GrindOutcome.NoReference, selection.state.value.outcome)
    selection.choose(3)
    assertEquals(GrindSetting(3, null, "Tres"), selection.current())
  }
}
