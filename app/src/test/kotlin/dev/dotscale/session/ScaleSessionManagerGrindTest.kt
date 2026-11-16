package dev.dotscale.session

import dev.dotscale.brew.BrewPhase
import dev.dotscale.grinder.GrindSetting
import dev.dotscale.history.BrewMethod
import dev.dotscale.protocol.ScaleMessage
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.recipe.v60Classic
import dev.dotscale.settings.ReferenceKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** El ajuste de molienda de "Moler con" se congela al pasar a `Active` y es el que se guarda (D23, D16). */
@OptIn(ExperimentalCoroutinesApi::class)
class ScaleSessionManagerGrindTest {
  private val connection = FakeScaleConnection()
  private val saver = FakeBrewSaver()
  private val references = FakeReferenceStore()
  private val targets = FakeTargetReferences()
  private val scheduler = TestCoroutineScheduler()
  private var now = 1_000L
  private var current: GrindSetting? = null
  private lateinit var manager: ScaleSessionManager
  private lateinit var managerScope: CoroutineScope
  private lateinit var applicationScope: CoroutineScope

  private fun build(grind: SessionGrind) {
    val dispatcher = UnconfinedTestDispatcher(scheduler)
    Dispatchers.setMain(dispatcher)
    applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
    managerScope = CoroutineScope(SupervisorJob() + dispatcher)
    manager = ScaleSessionManager(
      { connection },
      managerScope,
      MutableStateFlow(120),
      saver,
      applicationScope,
      references,
      targets,
      FakeNotifier(),
      grind = grind,
      clock = { now },
      wallClock = { 1_700_000_000_000L + now },
    )
    manager.connect(ScaleTarget.Real("00:11:22:33:44:55", "Test"))
    manager.arm()
  }

  @Before
  fun setUp() = build { current }

  @After
  fun tearDown() {
    manager.disconnect()
    managerScope.cancel()
    applicationScope.cancel()
    Dispatchers.resetMain()
  }

  private fun weight(grams: Double, flowGps: Double) {
    now += 100
    connection.messages.tryEmit(
      ScaleMessage.Weight(rawWeight = (grams * 10).toInt(), rawFlow = (flowGps * 10).toInt(), seconds = 0, unit = WeightUnit.GRAMS, outOfRange = false),
    )
  }

  private fun activePour() {
    repeat(5) { weight(0.0, 0.0) }
    repeat(30) { weight(4.0, 4.0) }
    assertTrue(manager.ui.value.brew.phase is BrewPhase.Active)
  }

  @Test
  fun `the setting is frozen when the session becomes active and is the one saved`() {
    current = GrindSetting(3, 21.0, "Acme Tres · 21 clics")
    activePour()
    // Elegir otro molino después de empezar no cambia lo guardado.
    current = GrindSetting(4, 10.0, "Acme Dos · 10 clics")
    manager.finishBrew()

    assertEquals(GrindSetting(3, 21.0, "Acme Tres · 21 clics"), saver.calls.single().context.details!!.grind)
  }

  @Test
  fun `the saved grind is the one chosen to grind with and not the one of the reference`() {
    targets.putRecipe(1, v60Classic().copy(grind = GrindSetting(1, 30.0, "Acme Uno · 30 clics")))
    references.setReference(ReferenceKey.Recipe(1))
    current = GrindSetting(3, 55.0, "Acme Tres · 55 clics")
    activePour()
    manager.finishBrew()

    val details = saver.calls.single().context.details!!
    assertEquals(GrindSetting(3, 55.0, "Acme Tres · 55 clics"), details.grind)
    assertEquals(BrewMethod.V60, details.method)
  }

  @Test
  fun `without a session grind the details carry no grind as before`() {
    tearDown()
    build(NoSessionGrind)
    targets.putRecipe(1, v60Classic().copy(grind = GrindSetting(1, 30.0, "Acme Uno · 30 clics")))
    references.setReference(ReferenceKey.Recipe(1))
    activePour()
    manager.finishBrew()

    assertNull(saver.calls.single().context.details!!.grind)
  }

  @Test
  fun `a brew without a recipe keeps the grinder without a setting`() {
    current = GrindSetting(3, null, "Acme Tres")
    activePour()
    manager.finishBrew()

    val details = saver.calls.single().context.details!!
    assertEquals(GrindSetting(3, null, "Acme Tres"), details.grind)
    assertNull(details.method)
  }

  @Test
  fun `no grinder and no recipe leave no details at all`() {
    current = null
    activePour()
    manager.finishBrew()

    assertNull(saver.calls.single().context.details)
  }
}
