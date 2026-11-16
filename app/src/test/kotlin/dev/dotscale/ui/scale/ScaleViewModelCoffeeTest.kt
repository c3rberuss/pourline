package dev.dotscale.ui.scale

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.dotscale.brew.BrewPhase
import dev.dotscale.coffee.PickedCoffee
import dev.dotscale.protocol.ScaleMessage
import dev.dotscale.protocol.WeightUnit
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** La elección del café en Scale: preselección, congelado en `Active`, guardado y "Nuevo café" (D21, D23, D32). */
@OptIn(ExperimentalCoroutinesApi::class)
class ScaleViewModelCoffeeTest {
  private val connection = FakeScaleConnection()
  private val saver = FakeBrewSaver()
  private val coffees = FakeScaleCoffees()
  private val scheduler = TestCoroutineScheduler()
  private val store = ViewModelStore()
  private var now = 1_000L
  private lateinit var viewModel: ScaleViewModel
  private lateinit var applicationScope: CoroutineScope

  private val ui get() = viewModel.ui.value

  private val etiopia = PickedCoffee(1, "Etiopía Guji", "Café de Altura", LocalDate.of(2026, 11, 4))
  private val kenia = PickedCoffee(2, "Kenia AA", null, null)


  private fun create(initialCoffee: Long? = null) {
    val dispatcher = UnconfinedTestDispatcher(scheduler)
    Dispatchers.setMain(dispatcher)
    applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
    val initial = FakeCoffeeStore(initialCoffee)
    coffeeStoreRef = initial
    val factory = viewModelFactory {
      initializer {
        ScaleViewModel(
          connection,
          MutableStateFlow(120),
          saver,
          applicationScope,
          FakeReferenceStore(),
          FakeTargetReferences(),
          FakeNotifier(),
          coffeeStore = initial,
          coffees = coffees,
          clock = { now },
          wallClock = { WALL_BASE + now },
        )
      }
    }
    viewModel = ViewModelProvider.create(store, factory)[ScaleViewModel::class]
  }

  private lateinit var coffeeStoreRef: FakeCoffeeStore

  @After
  fun tearDown() {
    store.clear()
    if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
    if (::applicationScope.isInitialized) applicationScope.cancel()
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
    assertTrue(ui.brew.phase is BrewPhase.Active)
  }

  @Test
  fun `the last used coffee is preselected`() {
    coffees.put(etiopia)
    create(initialCoffee = 1)
    assertEquals(etiopia, ui.coffee)
  }

  @Test
  fun `an archived or deleted last coffee is not preselected and a returning one is`() {
    create(initialCoffee = 1)
    assertNull(ui.coffee)
    coffees.put(etiopia)
    assertEquals(etiopia, ui.coffee)
    coffees.remove(1)
    assertNull(ui.coffee)
  }

  @Test
  fun `selecting a coffee stores it and clearing it removes the choice`() {
    coffees.put(etiopia)
    coffees.put(kenia)
    create()
    viewModel.selectCoffee(2)
    assertEquals(listOf<Long?>(2L), coffeeStoreRef.sets)
    assertEquals(kenia, ui.coffee)
    viewModel.selectCoffee(null)
    assertNull(ui.coffee)
    assertEquals(listOf<Long?>(2L, null), coffeeStoreRef.sets)
  }

  @Test
  fun `selecting a coffee during an active session is ignored`() {
    coffees.put(etiopia)
    coffees.put(kenia)
    create(initialCoffee = 1)
    activePour()
    viewModel.selectCoffee(2)
    assertTrue(coffeeStoreRef.sets.isEmpty())
    assertEquals(etiopia, ui.coffee)
  }

  @Test
  fun `changing the store during a session does not change the session coffee and the saved one is the frozen one`() {
    coffees.put(etiopia)
    coffees.put(kenia)
    create(initialCoffee = 1)
    activePour()
    coffeeStoreRef.setLastCoffeeId(2)
    assertEquals(etiopia, ui.coffee)

    viewModel.finishBrew()
    val used = saver.calls.single().context.coffee!!
    assertEquals(1L, used.id)
    assertEquals("Etiopía Guji", used.name)
    // Terminada la sesión, la pantalla ya muestra la elección para la siguiente.
    assertEquals(kenia, ui.coffee)
  }

  @Test
  fun `in Finished choosing another coffee applies to the next brew and lastBrew keeps the saved one`() {
    coffees.put(etiopia)
    coffees.put(kenia)
    create(initialCoffee = 1)
    activePour()
    viewModel.finishBrew()
    assertTrue(ui.brew.phase is BrewPhase.Finished)

    viewModel.selectCoffee(2)
    assertEquals(kenia, ui.coffee)
    val last = ui.lastBrew!!
    assertEquals(etiopia, last.coffee)
    assertEquals(saver.calls.single().context.startedAtEpochMs, last.startedAtEpochMs)

    // Al empezar la siguiente sesión, lastBrew se borra y el café es el nuevo.
    connection.messages.tryEmit(ScaleMessage.Tared(success = true))
    repeat(5) { weight(0.0, 0.0) }
    repeat(30) { weight(4.0, 4.0) }
    assertTrue(ui.brew.phase is BrewPhase.Active)
    assertNull(ui.lastBrew)
    assertEquals(kenia, ui.coffee)
  }

  @Test
  fun `a brew without a coffee is saved without one`() {
    create()
    activePour()
    viewModel.finishBrew()
    assertNull(saver.calls.single().context.coffee)
    assertNull(ui.lastBrew!!.coffee)
  }

  @Test
  fun `new coffee requested then created is chosen`() {
    coffees.put(etiopia)
    create()
    viewModel.requestNewCoffee()
    coffees.put(kenia)
    coffees.emitCreated(2)
    assertEquals(listOf<Long?>(2L), coffeeStoreRef.sets)
    assertEquals(kenia, ui.coffee)
    // Un segundo alta sin haberlo pedido no cambia nada.
    coffees.emitCreated(1)
    assertEquals(listOf<Long?>(2L), coffeeStoreRef.sets)
  }

  @Test
  fun `a coffee created without asking for it does nothing`() {
    coffees.put(etiopia)
    create()
    coffees.emitCreated(1)
    assertTrue(coffeeStoreRef.sets.isEmpty())
    assertNull(ui.coffee)
  }

  @Test
  fun `a new coffee created during a session is kept for the next one`() {
    coffees.put(etiopia)
    coffees.put(kenia)
    create(initialCoffee = 1)
    activePour()
    viewModel.requestNewCoffee()
    coffees.emitCreated(2)
    assertEquals(etiopia, ui.coffee)
    viewModel.finishBrew()
    assertEquals(kenia, ui.coffee)
    assertEquals(1L, saver.calls.single().context.coffee!!.id)
  }

  @Test
  fun `a brew reference does not copy its coffee text`() {
    val targets = FakeTargetReferences().apply { putBrew(7, brewRecord(id = 7, coffee = "Etiopía")) }
    val references = FakeReferenceStore(ReferenceKey.Brew(7))
    val dispatcher = UnconfinedTestDispatcher(scheduler)
    Dispatchers.setMain(dispatcher)
    applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
    viewModel = ViewModelProvider.create(
      store,
      viewModelFactory {
        initializer {
          ScaleViewModel(connection, MutableStateFlow(120), saver, applicationScope, references, targets, FakeNotifier(), clock = { now })
        }
      },
    )[ScaleViewModel::class]
    assertNotNull(ui.target)
    assertNull(ui.target!!.prefill.coffee)
  }

  private companion object {
    const val WALL_BASE = 1_700_000_000_000L
  }
}
