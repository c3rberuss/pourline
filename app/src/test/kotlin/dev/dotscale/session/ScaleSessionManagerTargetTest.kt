package dev.dotscale.session

import dev.dotscale.brew.BrewPhase
import dev.dotscale.brew.DeviationLevel
import dev.dotscale.brew.TargetConfig
import dev.dotscale.brew.brewFit
import dev.dotscale.grinder.GrindSetting
import dev.dotscale.history.BrewMethod
import dev.dotscale.history.ReferenceType
import dev.dotscale.protocol.ScaleMessage
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.recipe.recipeOf
import dev.dotscale.recipe.stage
import dev.dotscale.recipe.v60Classic
import dev.dotscale.settings.ReferenceKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.ceil

@OptIn(ExperimentalCoroutinesApi::class)
class ScaleSessionManagerTargetTest {

  private val connection = FakeScaleConnection()
  private val saver = FakeBrewSaver()
  private val references = FakeReferenceStore()
  private val targets = FakeTargetReferences()
  private val notifier = FakeNotifier()
  private val scheduler = TestCoroutineScheduler()
  private val autoFinish = MutableStateFlow(120)
  private val errors = mutableListOf<ScaleError>()
  private var now = 1_000L
  private lateinit var viewModel: ScaleSessionManager
  private lateinit var managerScope: CoroutineScope
  private lateinit var applicationScope: CoroutineScope

  private val ui get() = viewModel.ui.value

  @Before
  fun setUp() {
    val dispatcher = UnconfinedTestDispatcher(scheduler)
    Dispatchers.setMain(dispatcher)
    applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
    managerScope = CoroutineScope(SupervisorJob() + dispatcher)
    viewModel = ScaleSessionManager(
        { connection },
        managerScope,
        autoFinish,
        saver,
        applicationScope,
        references,
        targets,
        notifier,
        clock = { now },
        wallClock = { WALL_BASE + now },
    )
    viewModel.connect(ScaleTarget.Real("00:11:22:33:44:55", "Test"))
    viewModel.arm()
    applicationScope.launch { viewModel.errors.collect { errors += it } }
  }

  @After
  fun tearDown() {
    viewModel.disconnect()
    managerScope.cancel()
    applicationScope.cancel()
    Dispatchers.resetMain()
  }

  private fun advanceSeconds(seconds: Int) = repeat(seconds) {
    now += 1_000
    scheduler.advanceTimeBy(1_000)
    scheduler.runCurrent()
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
    assertTrue(ui.brew.phase is BrewPhase.Active)
  }

  private fun selectRecipe(recipe: dev.dotscale.recipe.Recipe = v60Classic(), id: Long = 1) {
    targets.putRecipe(id, recipe)
    references.setReference(ReferenceKey.Recipe(id))
  }

  private fun captureDose(grams: Double) {
    weight(grams, 0.0)
    viewModel.captureDose()
  }

  /** V60 con tres etapas, en 0 s, 30 s y 60 s. */
  private fun threeStages() = recipeOf(stage(0, 50.0, 10, "Bloom"), stage(30, 150.0, 10, "Segundo"), stage(60, 250.0, 10, "Tercero"))

  // --- Resolución y escalado ---

  @Test
  fun `a recipe reference becomes an unscaled target`() {
    val recipe = v60Classic()
    selectRecipe(recipe)

    val target = ui.target!!
    assertEquals(ReferenceType.RECIPE, target.type)
    assertEquals(ReferenceKey.Recipe(1), target.key)
    assertEquals(recipe.name, target.name)
    assertEquals(1.0, target.factor, 0.0)
    assertNull(target.scaledDoseG)
    assertEquals(recipe.totalWaterG, target.curve.finalWaterG, 1e-9)
    // En `Armed` la guía anuncia la primera etapa.
    assertEquals(0, ui.guide!!.stage!!.index)
  }

  @Test
  fun `no reference means no target and no guide`() {
    assertNull(ui.target)
    assertNull(ui.guide)
  }

  @Test
  fun `capturing 16_4 g with a 15 g recipe scales the whole curve by 16_4 over 15`() {
    val recipe = v60Classic()
    selectRecipe(recipe)
    val unscaled = ui.target!!.curve

    captureDose(16.4)

    val target = ui.target!!
    val factor = 16.4 / recipe.doseG
    assertEquals(factor, target.factor, 1e-9)
    assertEquals(16.4, target.scaledDoseG!!, 1e-9)
    unscaled.stages.zip(target.curve.stages).forEach { (a, b) ->
      assertEquals(a.targetWaterG * factor, b.targetWaterG, 1e-9)
      assertEquals(a.startMs, b.startMs) // los tiempos no cambian
    }
  }

  @Test
  fun `clearing the dose goes back to the unscaled curve`() {
    selectRecipe()
    captureDose(16.4)
    viewModel.clearDose()

    assertEquals(1.0, ui.target!!.factor, 0.0)
  }

  @Test
  fun `a brew reference resolves with its own nominal dose and prefill`() {
    targets.putBrew(7, brewRecord(id = 7, doseG = 20.0))
    references.setReference(ReferenceKey.Brew(7))

    val target = ui.target!!
    assertEquals(ReferenceType.BREW, target.type)
    assertEquals("Mañana", target.name)
    assertEquals(250.0, target.curve.finalWaterG, 1e-9)
    assertNull(target.prefill.coffee) // el café sale solo del selector (D21)
    assertEquals(BrewMethod.V60, target.prefill.method)
    assertNull(target.prefill.grind) // la molienda va en sourceGrind (D24)
    assertEquals(GrindSetting(null, 21.0, "Acme · 21 clics"), target.sourceGrind)
    assertEquals(94.0, target.prefill.waterTempC!!, 0.0)
    assertNull(target.prefill.name)
    assertNull(target.prefill.notes) // las notas de cata no se copian

    captureDose(10.0) // la mitad de la dosis nominal
    assertEquals(0.5, ui.target!!.factor, 1e-9)
  }

  @Test
  fun `a recipe prefill has the method and temperature but no grind, name or notes, and the target carries the source grind`() {
    val setting = GrindSetting(5, 21.0, "Acme · 21 clics")
    val recipe = v60Classic().copy(grind = setting, waterTempC = 92.0, notes = "Instrucciones")
    selectRecipe(recipe)

    val prefill = ui.target!!.prefill
    assertEquals(recipe.method, prefill.method)
    assertNull(prefill.grind)
    assertEquals(setting, ui.target!!.sourceGrind)
    assertEquals(92.0, prefill.waterTempC!!, 0.0)
    assertNull(prefill.coffee)
    assertNull(prefill.notes)
    assertNull(prefill.name)
  }

  // --- Referencias que desaparecen ---

  @Test
  fun `a deleted recipe clears the reference and reports TargetRemoved`() {
    selectRecipe()
    assertNotNull(ui.target)

    targets.putRecipe(1, null)

    assertNull(references.sets.last())
    assertNull(ui.target)
    assertEquals(listOf<ScaleError>(ScaleError.TargetRemoved), errors)
  }

  @Test
  fun `a reference that never existed is removed at once`() {
    references.setReference(ReferenceKey.Recipe(99))

    assertNull(references.sets.last())
    assertEquals(listOf<ScaleError>(ScaleError.TargetRemoved), errors)
  }

  @Test
  fun `a brew with an unreadable curve reports TargetUnreadable`() {
    targets.putBrew(3, brewRecord(id = 3, samples = emptyList(), curveError = true))
    references.setReference(ReferenceKey.Brew(3))

    assertNull(references.sets.last())
    assertNull(ui.target)
    assertEquals(listOf<ScaleError>(ScaleError.TargetUnreadable), errors)
  }

  @Test
  fun `a brew without valid samples reports TargetUnreadable`() {
    targets.putBrew(3, brewRecord(id = 3, samples = emptyList()))
    references.setReference(ReferenceKey.Brew(3))

    assertEquals(listOf<ScaleError>(ScaleError.TargetUnreadable), errors)
    assertNull(references.sets.last())
  }

  // --- Congelación ---

  @Test
  fun `the target is frozen while active and a recipe edit reaches the next idle state`() {
    val recipe = v60Classic()
    selectRecipe(recipe)
    activePour()
    val frozen = ui.target!!

    targets.putRecipe(1, recipe.copy(stages = recipe.stages.map { it.copy(targetWaterG = it.targetWaterG * 2) }))
    assertSame(frozen, ui.target) // sigue la congelada
    captureDose(30.0)
    assertSame(frozen, ui.target) // tampoco cambia con otra dosis

    viewModel.finishBrew()

    // El ajuste usa la curva congelada; la nueva llega en `Finished`.
    val call = saver.calls.single()
    assertEquals(frozen.curve, call.context.target!!.curve)
    assertEquals(frozen.curve, ui.lastFit!!.curve)
    assertEquals(recipe.totalWaterG * 2 * 30.0 / recipe.doseG, ui.target!!.curve.finalWaterG, 1e-9)
  }

  @Test
  fun `selectTarget is ignored while active`() {
    selectRecipe()
    activePour()
    val sets = references.sets.size

    viewModel.selectTarget(null)
    viewModel.selectTarget(ReferenceKey.Recipe(5))

    assertEquals(sets, references.sets.size)
    assertNotNull(ui.target)
  }

  @Test
  fun `selectTarget changes the reference while armed or finished`() {
    targets.putRecipe(1, v60Classic())
    viewModel.selectTarget(ReferenceKey.Recipe(1))
    assertNotNull(ui.target)

    activePour()
    viewModel.finishBrew()
    viewModel.selectTarget(null)
    assertNull(ui.target)
    assertNull(references.reference.value)
  }

  @Test
  fun `a deleted recipe while active keeps the frozen curve and reports the error`() {
    selectRecipe()
    activePour()
    val frozen = ui.target

    targets.putRecipe(1, null)

    assertSame(frozen, ui.target)
    assertEquals(listOf<ScaleError>(ScaleError.TargetRemoved), errors)
    viewModel.finishBrew()
    assertNull(ui.target) // ya no hay referencia para la siguiente
    assertNotNull(ui.lastFit) // pero la sesión se midió contra la congelada
  }

  // --- Guardado ---

  @Test
  fun `a brew with a recipe is saved with target fit and prefilled details`() {
    val recipe = v60Classic().copy(grind = GrindSetting(5, 21.0, "Acme · 21 clics"), waterTempC = 94.0)
    selectRecipe(recipe)
    activePour()
    viewModel.finishBrew()

    val call = saver.calls.single()
    val used = call.context.target!!
    assertEquals(ReferenceType.RECIPE, used.type)
    assertEquals(1L, used.id)
    assertEquals(recipe.name, used.name)
    assertEquals(brewFit(call.brew.samples, call.brew.summary.totalTimeMs, used.curve, TargetConfig()), used.fit)
    assertEquals(BrewMethod.V60, call.context.details!!.method)
    // La molienda de la preparación es la de "Moler con" (F7), no la de la referencia.
    assertNull(call.context.details!!.grind)
    assertNull(call.context.details!!.name)
    assertNull(call.context.details!!.notes)
    // El resumen y lo guardado llevan el mismo ajuste.
    assertEquals(used.fit, ui.lastFit!!.fit)
    assertEquals(recipe.name, ui.lastFit!!.name)
  }

  @Test
  fun `without a target the context has no target or details and there is no lastFit`() {
    activePour()
    viewModel.finishBrew()

    val call = saver.calls.single()
    assertNull(call.context.target)
    assertNull(call.context.details)
    assertNull(ui.lastFit)
  }

  @Test
  fun `lastFit is cleared when another session starts`() {
    selectRecipe()
    activePour()
    viewModel.finishBrew()
    assertNotNull(ui.lastFit)

    viewModel.beginArmedSession()
    activePour()

    assertNull(ui.lastFit)
  }

  @Test
  fun `a unit change that clears the dose also clears the session dose and resets the factor`() {
    selectRecipe()
    captureDose(16.4)
    assertEquals(16.4 / v60Classic().doseG, ui.target!!.factor, 1e-9)

    connection.messages.tryEmit(ScaleMessage.UnitChanged(WeightUnit.OUNCES))

    assertNull(ui.dose)
    assertEquals(1.0, ui.target!!.factor, 0.0)
    activePour()
    viewModel.finishBrew()
    assertNull(saver.calls.single().brew.doseG)
    assertNull(saver.calls.single().brew.summary.finalRatio)
  }

  // --- Guía y avisos ---

  @Test
  fun `the guide shows the stage the countdown and the deviation level while active`() {
    selectRecipe(threeStages())
    activePour()
    val t0 = ui.brew.t0MonoMs!!

    while (now - t0 < 26_000) advanceSeconds(1)

    val guide = ui.guide!!
    assertEquals(0, guide.stage!!.index)
    assertEquals(1, guide.nextStage!!.index)
    assertEquals(ceil((30_000 - (now - t0)) / 1000.0).toInt(), guide.countdownS)
    // Solo hay 4 g vertidos y el objetivo es 50 g.
    assertTrue(guide.deviationG!! < 0)
    assertEquals(DeviationLevel.ERROR, guide.deviationLevel)
  }

  @Test
  fun `the notifier hears each stage once and never the one that starts at zero`() {
    selectRecipe(threeStages())
    activePour()
    assertTrue(notifier.started.isEmpty())

    advanceSeconds(35)
    assertEquals(listOf(1), notifier.started.map { it.index })

    advanceSeconds(1) // más ticks no repiten el aviso
    assertEquals(listOf(1), notifier.started.map { it.index })

    advanceSeconds(30)
    assertEquals(listOf(1, 2), notifier.started.map { it.index })
  }

  @Test
  fun `a new session alerts again`() {
    selectRecipe(threeStages())
    activePour()
    advanceSeconds(35)
    viewModel.finishBrew()
    viewModel.beginArmedSession()
    activePour()
    advanceSeconds(35)

    assertEquals(listOf(1, 1), notifier.started.map { it.index })
  }

  @Test
  fun `nothing is announced without a target`() {
    activePour()
    advanceSeconds(70)
    assertTrue(notifier.started.isEmpty())
  }

  @Test
  fun `disconnecting the manager releases the notifier`() {
    viewModel.disconnect()
    assertEquals(1, notifier.released)
  }

  // --- Rendimiento: la curva no se recalcula con cada muestra ---

  @Test
  fun `the target is built once per reference or dose change and never per sample`() {
    selectRecipe()
    assertEquals(1, viewModel.targetBuilds)

    repeat(50) { weight(0.0, 0.0) } // muchas muestras en `Armed`
    assertEquals(1, viewModel.targetBuilds)

    captureDose(16.4)
    assertEquals(2, viewModel.targetBuilds)

    val frozen = ui.target
    activePour()
    advanceSeconds(10)
    repeat(100) { weight(4.0, 4.0) }
    assertEquals(2, viewModel.targetBuilds)
    assertSame(frozen, ui.target)

    viewModel.finishBrew()
    assertEquals(2, viewModel.targetBuilds) // la misma base y la misma dosis: se reutiliza

    targets.putRecipe(2, v60Classic().copy(name = "Otra"))
    references.setReference(ReferenceKey.Recipe(2))
    assertEquals(3, viewModel.targetBuilds)
    assertNotSame(frozen, ui.target)
  }

  private companion object {
    const val WALL_BASE = 1_700_000_000_000L
  }
}
