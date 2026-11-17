package dev.josueamaya.pourline.coffee

import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.history.BrewDetails
import dev.josueamaya.pourline.history.BrewRepository
import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.history.TestBrews
import dev.josueamaya.pourline.history.UsedCoffee
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Room en memoria con "hoy" fijo y un `PhotoFiles` que registra lo borrado. */
class CoffeeDaoTest {
  private lateinit var db: HistoryDatabase
  private lateinit var coffees: CoffeeRepository
  private lateinit var brews: BrewRepository
  private val deleted = mutableListOf<String>()
  private val today = LocalDate.of(2026, 11, 13)
  private var now = 1_000_000L

  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
    coffees = CoffeeRepository(db, { deleted += it }, { today }, { ZoneId.of("UTC") }, { now })
    brews = BrewRepository(db)
  }

  @After
  fun tearDown() = db.close()

  private suspend fun saveCoffee(draft: CoffeeDraft = TestBrews.coffeeDraft()): Long = coffees.save(draft)

  private suspend fun brew(coffeeId: Long?, name: String = "x", doseG: Double? = 15.0, startedAt: Long = 1_700_000_000_000): Long {
    val finished = TestBrews.finished(totalMs = 60_000, doseG = doseG)
    return brews.save(finished, TestBrews.context(startedAtEpochMs = startedAt, coffee = coffeeId?.let { UsedCoffee(it, name) }))
  }

  private fun exec(sql: String) = db.openHelper.writableDatabase.execSQL(sql)

  private suspend fun list(query: String = "", archived: Boolean = false) = coffees.coffees(query, archived).first()

  @Test
  fun searchesTheFourFieldsIgnoringCaseAndAccents() = runTest {
    saveCoffee(TestBrews.coffeeDraft(name = "Guji Hambela", roaster = "Café de Altura"))
    saveCoffee(TestBrews.coffeeDraft(name = "Kiambu", roaster = "Origen Tostadores").copy(originCountry = "Kenia", originRegion = "Kiambu"))

    assertEquals(listOf("Guji Hambela"), list("hambela").map { it.name })
    assertEquals(listOf("Guji Hambela"), list("ALTURA").map { it.name })
    assertEquals(listOf("Guji Hambela"), list("etiopia").map { it.name })
    assertEquals(listOf("Kiambu"), list("kenia").map { it.name })
    assertEquals(2, list("").size)
    assertTrue(list("zzz").isEmpty())
    assertTrue(list("jazmín").isEmpty())
  }

  @Test
  fun archiveAndUnarchiveMoveTheCoffeeBetweenTabs() = runTest {
    val id = saveCoffee()
    assertEquals(1, list().size)
    coffees.setArchived(id, true)
    assertTrue(list().isEmpty())
    assertEquals(listOf(id), list(archived = true).map { it.id })
    assertTrue(coffees.pickable().first().isEmpty())
    coffees.setArchived(id, false)
    assertEquals(listOf(id), coffees.pickable().first().map { it.id })
  }

  @Test
  fun stockIsComputedFromTheLinkedBrews() = runTest {
    val id = saveCoffee(TestBrews.coffeeDraft(bagWeightG = 250.0))
    brew(id, doseG = 15.0, startedAt = 1)
    val second = brew(id, doseG = 18.0, startedAt = 2)
    brew(id, doseG = null, startedAt = 3)
    assertEquals(217.0, coffees.coffee(id).first()!!.stockG!!, 0.0)
    assertEquals(3, coffees.coffee(id).first()!!.brews.size)

    // Cambiar la dosis por SQL corrige el stock sin más acciones.
    exec("UPDATE brews SET doseG = 30.0 WHERE id = $second")
    assertEquals(205.0, coffees.coffee(id).first()!!.stockG!!, 0.0)

    // Borrar una preparación también.
    brews.delete(second)
    assertEquals(235.0, coffees.coffee(id).first()!!.stockG!!, 0.0)
    assertEquals(235.0, list().single().stockG!!, 0.0)
  }

  @Test
  fun withoutABagWeightThereIsNoStockAndNoSoldOut() = runTest {
    val id = saveCoffee(TestBrews.coffeeDraft(bagWeightG = null))
    brew(id, doseG = 500.0)
    val detail = coffees.coffee(id).first()!!
    assertNull(detail.stockG)
    assertFalse(detail.soldOut)
    assertFalse(list().single().soldOut)
  }

  @Test
  fun soldOutShowsZeroAndStaysPickable() = runTest {
    val id = saveCoffee(TestBrews.coffeeDraft(bagWeightG = 30.0))
    brew(id, doseG = 20.0, startedAt = 1)
    brew(id, doseG = 20.0, startedAt = 2)
    val item = list().single()
    assertTrue(item.soldOut)
    assertEquals(0.0, item.stockG!!, 0.0)
    assertTrue(coffees.coffee(id).first()!!.soldOut)
    assertEquals(listOf(id), coffees.pickable().first().map { it.id })
  }

  @Test
  fun daysSinceTheRoastUseTheInjectedToday() = runTest {
    val id = saveCoffee(TestBrews.coffeeDraft(roastDate = LocalDate.of(2026, 11, 4)))
    assertEquals(9, list().single().daysSinceRoast)
    assertEquals(9, coffees.coffee(id).first()!!.daysSinceRoast)
    // Una preparación del 10 de noviembre (UTC) lleva 6 días.
    val startedAt = LocalDate.of(2026, 11, 10).atTime(12, 0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
    brew(id, startedAt = startedAt)
    assertEquals(6, coffees.coffee(id).first()!!.brews.single().daysSinceRoast)
  }

  @Test
  fun deletingACoffeeKeepsTheNameInItsBrewsAndAsksToDeleteThePhotos() = runTest {
    val id = saveCoffee(TestBrews.coffeeDraft(name = "Guji", photo1 = "a.jpg", photo2 = "b.jpg"))
    val brewId = brew(id, name = "Guji")
    coffees.delete(id)

    val entity = db.brewDao().brew(brewId)!!
    assertNull(entity.coffeeId)
    assertEquals("Guji", entity.coffee)
    assertEquals(listOf("a.jpg", "b.jpg"), deleted)
    assertNull(coffees.coffee(id).first())
  }

  @Test
  fun lastUsedFollowsTheNewestLinkedBrew() = runTest {
    val id = saveCoffee()
    val dao = db.coffeeDao()
    assertNull(dao.get(id)!!.lastUsedAtEpochMs)

    val newest = brew(id, startedAt = 5_000)
    assertEquals(5_000L, dao.get(id)!!.lastUsedAtEpochMs)

    // Vincular una preparación antigua no lo adelanta.
    val old = brew(null, startedAt = 1_000)
    brews.setCoffee(old, id)
    assertEquals(5_000L, dao.get(id)!!.lastUsedAtEpochMs)

    // Desvincular o borrar la última lo recalcula.
    brews.setCoffee(newest, null)
    assertEquals(1_000L, dao.get(id)!!.lastUsedAtEpochMs)
    brews.delete(old)
    assertNull(dao.get(id)!!.lastUsedAtEpochMs)
  }

  @Test
  fun theSelectorIsOrderedByLastUseOrCreation() = runTest {
    now = 1_000
    val a = saveCoffee(TestBrews.coffeeDraft(name = "A"))
    now = 2_000
    val b = saveCoffee(TestBrews.coffeeDraft(name = "B"))
    now = 3_000
    val c = saveCoffee(TestBrews.coffeeDraft(name = "C"))
    assertEquals(listOf(c, b, a), coffees.pickable().first().map { it.id })
    // Usar A (la preparación empezó después de crear C) lo sube arriba.
    brew(a, startedAt = 5_000)
    assertEquals(listOf(a, c, b), coffees.pickable().first().map { it.id })
  }

  @Test
  fun renamingUpdatesTheCopyInTheBrews() = runTest {
    val id = saveCoffee(TestBrews.coffeeDraft(name = "Guji"))
    val brewId = brew(id, name = "Guji")
    coffees.save(TestBrews.coffeeDraft(name = "Guji Hambela").copy(id = id))
    assertEquals("Guji Hambela", db.brewDao().brew(brewId)!!.coffee)
    assertEquals(id, db.brewDao().brew(brewId)!!.coffeeId)
  }

  @Test
  fun theSpellingOfRoasterCountryRegionAndVarietiesIsUnified() = runTest {
    saveCoffee(TestBrews.coffeeDraft(name = "A"))
    val id = saveCoffee(
      TestBrews.coffeeDraft(name = "B", roaster = "café de altura")
        .copy(originCountry = "etiopía", originRegion = "GUJI", varieties = listOf("heirloom", "Sidra")),
    )
    val saved = db.coffeeDao().get(id)!!
    assertEquals("Café de Altura", saved.roaster)
    assertEquals("Etiopía", saved.originCountry)
    assertEquals("Guji", saved.originRegion)
    assertEquals(listOf("Heirloom", "Sidra"), saved.varieties)
  }

  @Test
  fun suggestionsHaveNoDuplicates() = runTest {
    saveCoffee(TestBrews.coffeeDraft(name = "A"))
    saveCoffee(TestBrews.coffeeDraft(name = "B").copy(varieties = listOf("Heirloom", "74110")))
    val s = coffees.suggestions().first()
    assertEquals(listOf("Café de Altura"), s.roasters)
    assertEquals(listOf("Etiopía"), s.countries)
    assertEquals(listOf("Guji"), s.regions)
    assertEquals(listOf("74110", "Heirloom"), s.varieties)
  }

  @Test
  fun replacingAPhotoAsksToDeleteTheOldOne() = runTest {
    val id = saveCoffee(TestBrews.coffeeDraft(photo1 = "a.jpg", photo2 = "b.jpg"))
    coffees.save(TestBrews.coffeeDraft(photo1 = "c.jpg", photo2 = "b.jpg").copy(id = id))
    assertEquals(listOf("a.jpg"), deleted)
    coffees.save(TestBrews.coffeeDraft(photo1 = "c.jpg", photo2 = null).copy(id = id))
    assertEquals(listOf("a.jpg", "b.jpg"), deleted)
  }

  @Test
  fun createdEmitsOnInsertAndNotOnEdit() = runTest {
    val emitted = mutableListOf<Long>()
    val job = launch(Dispatchers.Unconfined) { coffees.created.collect { emitted += it } }
    val id = saveCoffee()
    coffees.save(TestBrews.coffeeDraft(name = "Editado").copy(id = id))
    job.cancel()
    assertEquals(listOf(id), emitted)
  }

  @Test
  fun savingABrewWithACoffeeThatWasDeletedLeavesItUnlinkedWithTheName() = runTest {
    val id = saveCoffee(TestBrews.coffeeDraft(name = "Guji"))
    coffees.delete(id)
    val brewId = brew(id, name = "Guji")
    val entity = db.brewDao().brew(brewId)!!
    assertNull(entity.coffeeId)
    assertEquals("Guji", entity.coffee)
  }

  @Test
  fun savingABrewWithACoffeeUsesTheCurrentName() = runTest {
    val id = saveCoffee(TestBrews.coffeeDraft(name = "Guji Hambela"))
    val brewId = brew(id, name = "nombre viejo")
    assertEquals("Guji Hambela", db.brewDao().brew(brewId)!!.coffee)
  }

  @Test
  fun pickableHasNoArchivedAndKeepsTheSoldOut() = runTest {
    val a = saveCoffee(TestBrews.coffeeDraft(name = "A", bagWeightG = 10.0))
    val b = saveCoffee(TestBrews.coffeeDraft(name = "B"))
    brew(a, doseG = 15.0)
    coffees.setArchived(b, true)
    val pickable = coffees.pickable().first()
    assertEquals(listOf(a), pickable.map { it.id })
    assertTrue(pickable.single().soldOut)
  }

  @Test
  fun coffeeOptionsIncludeArchivedCoffeesWithBrews() = runTest {
    val a = saveCoffee(TestBrews.coffeeDraft(name = "A"))
    val b = saveCoffee(TestBrews.coffeeDraft(name = "B"))
    saveCoffee(TestBrews.coffeeDraft(name = "C"))
    brew(a, startedAt = 1)
    brew(b, startedAt = 2)
    coffees.setArchived(b, true)
    assertEquals(listOf(b, a), brews.coffeeOptions().first().map { it.id })
  }

  @Test
  fun ratingIsSavedByApplyDetails() = runTest {
    val brewId = brew(null)
    brews.updateDetails(brewId, BrewDetails(rating = 4, notes = "dulce"))
    assertEquals(4, db.brewDao().brew(brewId)!!.rating)
    brews.updateDetails(brewId, BrewDetails(rating = 9))
    assertNull(db.brewDao().brew(brewId)!!.rating)
  }
}
