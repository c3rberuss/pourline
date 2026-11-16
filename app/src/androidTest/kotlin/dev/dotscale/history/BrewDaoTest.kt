package dev.dotscale.history

import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.brew.FinishReason
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.recipe.StageType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Room en memoria: el `history.db` real no se toca. */
class BrewDaoTest {
  private lateinit var db: HistoryDatabase
  private lateinit var repository: BrewRepository
  private val dao get() = db.brewDao()

  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
    repository = BrewRepository(db)
  }

  @After
  fun tearDown() = db.close()

  private suspend fun save(
    startedAt: Long = 1_700_000_000_000,
    seed: Long = 1,
    simulated: Boolean = false,
    scored: Boolean = true,
    unit: WeightUnit = WeightUnit.GRAMS,
    totalMs: Long = 60_000,
  ): Long = repository.save(
    TestBrews.finished(totalMs = totalMs, seed = seed, scored = scored),
    TestBrews.context(startedAtEpochMs = startedAt, simulated = simulated, unit = unit),
  )

  @Test
  fun insertAndRead() = runTest {
    val finished = TestBrews.finished(seed = 3)
    val id = repository.save(finished, TestBrews.context(startedAtEpochMs = 42, unit = WeightUnit.OUNCES, simulated = true))

    val record = repository.get(id)!!
    assertFalse(record.curveError)
    assertEquals(42L, record.entity.startedAtEpochMs)
    assertEquals(WeightUnit.OUNCES, record.entity.unit)
    assertTrue(record.entity.simulated)
    assertEquals(BrewConfigVersion, record.entity.metricVersion)
    assertEquals(finished.samples.size, record.samples.size)
    assertEquals(finished.summary.pours.size, record.summary.pours.size)
    assertEquals(finished.summary.stabilityScore, record.summary.stabilityScore)
    assertEquals(finished.summary.levelShare, record.summary.levelShare)
    assertEquals(finished.summary.finalRatio!!, record.summary.finalRatio!!, 1e-9)
    assertEquals(finished.summary.reason, record.summary.reason)
    assertEquals(48, record.entity.preview.size)
    assertNull(repository.get(id + 100))
  }

  @Test
  fun listIsNewestFirstAndCarriesThePreviewButNotTheCurve() = runTest {
    val old = save(startedAt = 1_000)
    val new = save(startedAt = 3_000)
    val middle = save(startedAt = 2_000)

    val items = repository.list(BrewFilter()).first()
    assertEquals(listOf(new, middle, old), items.map { it.id })
    assertEquals(48, items.first().preview.size)
    assertTrue(items.first().preview.last() > 0.9f)
    assertEquals(60_000L, items.first().totalTimeMs)
  }

  private val coffees get() = dev.dotscale.coffee.CoffeeRepository(db, dev.dotscale.coffee.PhotoFiles { })

  private suspend fun coffee(name: String, roastDate: java.time.LocalDate? = null): Long =
    coffees.save(TestBrews.coffeeDraft(name = name).copy(roastDate = roastDate))

  private suspend fun saveWith(coffeeId: Long?, startedAt: Long, method: BrewMethod? = null, methodOther: String? = null): Long {
    val id = repository.save(
      TestBrews.finished(totalMs = 60_000),
      TestBrews.context(startedAtEpochMs = startedAt, coffee = coffeeId?.let { UsedCoffee(it, "x") }),
    )
    repository.updateDetails(id, BrewDetails(method = method, methodOther = methodOther))
    return id
  }

  @Test
  fun filterByCoffeeIdAndMethod() = runTest {
    val etiopia = coffee("Etiopía")
    val kenia = coffee("Kenia")
    val a = saveWith(etiopia, startedAt = 1, method = BrewMethod.V60)
    val b = saveWith(etiopia, startedAt = 2, method = BrewMethod.CHEMEX)
    val c = saveWith(kenia, startedAt = 3, method = BrewMethod.OTHER, methodOther = "Sifón")
    val d = saveWith(null, startedAt = 4, method = BrewMethod.OTHER, methodOther = "Prensa")

    assertEquals(listOf(b, a), repository.list(BrewFilter(coffeeId = etiopia)).first().map { it.id })
    assertEquals(listOf(a), repository.list(BrewFilter(coffeeId = etiopia, method = BrewMethod.V60)).first().map { it.id })
    // OTHER agrupa todos los `methodOther`.
    assertEquals(listOf(d, c), repository.list(BrewFilter(method = BrewMethod.OTHER)).first().map { it.id })
    assertEquals(listOf(a), repository.progress(BrewFilter(method = BrewMethod.V60)).first().map { it.brewId })
    assertEquals(listOf(c), repository.list(BrewFilter(coffeeId = kenia)).first().map { it.id })
    assertTrue(repository.list(BrewFilter(coffeeId = kenia + 100)).first().isEmpty())
    // Una preparación sin vínculo solo sale con "Todos".
    assertTrue(d in repository.list(BrewFilter()).first().map { it.id })
    assertTrue(repository.list(BrewFilter(coffeeId = etiopia)).first().none { it.id == d })
    assertEquals(listOf(BrewMethod.V60, BrewMethod.CHEMEX, BrewMethod.OTHER), repository.methods().first())
  }

  @Test
  fun theListAndTheFilterCarryTheRating() = runTest {
    val coffeeId = coffee("Guji")
    val a = saveWith(coffeeId, startedAt = 1)
    val b = saveWith(coffeeId, startedAt = 2)
    repository.updateDetails(a, BrewDetails(rating = 4))
    assertEquals(mapOf(a to 4, b to null), repository.list(BrewFilter(coffeeId = coffeeId)).first().associate { it.id to it.rating })
  }

  @Test
  fun progressSkipsSimulatedAndUnscoredBrewsAndIsChronological() = runTest {
    val second = save(startedAt = 2_000)
    val first = save(startedAt = 1_000)
    save(startedAt = 1_500, simulated = true)
    save(startedAt = 1_600, scored = false)

    val points = repository.progress(BrewFilter()).first()
    assertEquals(listOf(first, second), points.map { it.brewId })
    assertTrue(points.all { it.score in 0..100 })
    assertEquals(4, repository.list(BrewFilter()).first().size)
  }

  @Test
  fun coffeeOptionsListsLinkedCoffeesIncludingArchivedOnesByMostRecentBrew() = runTest {
    val old = coffee("Etiopía")
    val recent = coffee("Kenia")
    val archived = coffee("Colombia")
    val unused = coffee("Sin uso")
    saveWith(old, startedAt = 1)
    saveWith(archived, startedAt = 2)
    saveWith(recent, startedAt = 3)
    saveWith(null, startedAt = 4)
    coffees.setArchived(archived, true)

    val options = repository.coffeeOptions().first()
    assertEquals(listOf(recent, archived, old), options.map { it.id })
    assertEquals(listOf("Kenia", "Colombia", "Etiopía"), options.map { it.name })
    assertFalse(unused in options.map { it.id })
  }

  @Test
  fun twoBagsOfTheSameCoffeeAreTwoOptionsWithTheirRoastDate() = runTest {
    val first = coffee("Guji", java.time.LocalDate.of(2026, 10, 12))
    val second = coffee("Guji", java.time.LocalDate.of(2026, 11, 2))
    saveWith(first, startedAt = 1)
    saveWith(second, startedAt = 2)

    val options = repository.coffeeOptions().first()
    assertEquals(listOf(second, first), options.map { it.id })
    assertEquals(listOf(java.time.LocalDate.of(2026, 11, 2), java.time.LocalDate.of(2026, 10, 12)), options.map { it.roastDate })
  }

  @Test
  fun applyDetailsDoesNotTouchTheCoffeeNameOrTheLink() = runTest {
    val coffeeId = coffee("Guji")
    val linked = saveWith(coffeeId, startedAt = 1)
    val legacy = saveWith(null, startedAt = 2)
    db.openHelper.writableDatabase.execSQL("UPDATE brews SET coffee = 'Etiopía antigua' WHERE id = $legacy")

    repository.updateDetails(linked, BrewDetails(coffee = "Otro", name = " Mañana ", waterTempC = 92.5, notes = "dulce", rating = 5))
    repository.updateDetails(legacy, BrewDetails(coffee = "Otro", notes = "x"))

    val entity = repository.get(linked)!!.entity
    assertEquals(coffeeId, entity.coffeeId)
    assertEquals("Guji", entity.coffee)
    assertEquals("Mañana", entity.name)
    assertEquals(92.5, entity.waterTempC!!, 0.0)
    assertEquals("dulce", entity.notes)
    assertEquals(5, entity.rating)
    assertEquals("Etiopía antigua", repository.get(legacy)!!.entity.coffee)
    assertNull(repository.get(legacy)!!.entity.coffeeId)
  }

  @Test
  fun insertBrewLinksTheCoffeeAndLinkCoffeeWithNullClearsTheNameToo() = runTest {
    val coffeeId = db.coffeeDao().let { _ ->
      dev.dotscale.coffee.CoffeeRepository(db, dev.dotscale.coffee.PhotoFiles { }).save(TestBrews.coffeeDraft(name = "Guji"))
    }
    val id = repository.save(TestBrews.finished(totalMs = 60_000), TestBrews.context(coffee = UsedCoffee(coffeeId, "otro")))
    assertEquals(coffeeId, dao.brew(id)!!.coffeeId)
    assertEquals("Guji", dao.brew(id)!!.coffee)

    repository.setCoffee(id, null)
    assertNull(dao.brew(id)!!.coffeeId)
    assertNull(dao.brew(id)!!.coffee)
  }

  @Test
  fun applyDetailsWritesTheRatingAndKeepsItBetween1And5() = runTest {
    val id = save()
    repository.updateDetails(id, BrewDetails(rating = 5))
    assertEquals(5, dao.brew(id)!!.rating)
    repository.updateDetails(id, BrewDetails(rating = 0))
    assertNull(dao.brew(id)!!.rating)
  }

  @Test
  fun methodOtherIsDroppedWithoutTheOtherMethod() = runTest {
    val a = save()
    repository.updateDetails(a, BrewDetails(method = BrewMethod.V60, methodOther = "Sifón"))
    assertNull(repository.get(a)!!.entity.methodOther)
  }

  @Test
  fun deleteCascadesToTheCurveAndFillsPendingUndo() = runTest {
    val id = save()
    val before = repository.get(id)!!
    assertNotNull(dao.curve(id))

    repository.delete(id)
    assertNull(repository.get(id))
    assertNull(dao.curve(id))
    assertTrue(repository.list(BrewFilter()).first().isEmpty())
    assertEquals(id, repository.pendingUndo.value!!.entity.id)
    val deleted = repository.pendingUndo.value!!.entity
    assertEquals(before.entity.copy(preview = deleted.preview), deleted) // `ByteArray` se compara por referencia
    assertArrayEquals(before.entity.preview, deleted.preview)
  }

  @Test
  fun restoreBringsBackTheSameIdAndCurve() = runTest {
    val id = save()
    val curveBefore = dao.curve(id)!!
    val before = repository.get(id)!!

    repository.delete(id)
    repository.restore(repository.pendingUndo.value!!)

    assertNull(repository.pendingUndo.value)
    val after = repository.get(id)!!
    assertEquals(id, after.entity.id)
    assertEquals(before.entity.startedAtEpochMs, after.entity.startedAtEpochMs)
    assertEquals(before.samples, after.samples)
    assertArrayEquals(curveBefore, dao.curve(id))
    assertEquals(listOf(id), repository.list(BrewFilter()).first().map { it.id })
  }

  @Test
  fun aSecondDeleteReplacesPendingUndoAndClearIsCompareAndSet() = runTest {
    val a = save(startedAt = 1)
    val b = save(startedAt = 2)
    repository.delete(a)
    val first = repository.pendingUndo.value!!
    repository.delete(b)
    val second = repository.pendingUndo.value!!
    assertEquals(b, second.entity.id)

    repository.clearPendingUndo(first) // un aviso que expira no borra el deshacer posterior
    assertEquals(second, repository.pendingUndo.value)
    repository.clearPendingUndo(second)
    assertNull(repository.pendingUndo.value)
  }

  @Test
  fun aCorruptCurveSetsCurveErrorAndKeepsTheSummary() = runTest {
    val finished = TestBrews.finished(totalMs = 60_000)
    val id = repository.save(finished, TestBrews.context())
    // Se sustituye el BLOB por basura con la versión correcta.
    db.openHelper.writableDatabase.execSQL("UPDATE brew_curves SET data = x'0102030405' WHERE brewId = $id")

    val record = repository.get(id)!!
    assertTrue(record.curveError)
    assertTrue(record.samples.isEmpty())
    assertTrue(record.summary.pours.isEmpty())
    assertEquals(finished.summary.stabilityScore, record.summary.stabilityScore)
    assertTrue(repository.observe(id).first()!!.curveError)
  }

  @Test
  fun observeEmitsNullWhenTheRowDisappears() = runTest {
    val id = save()
    assertNotNull(repository.observe(id).first())
    repository.delete(id)
    assertNull(repository.observe(id).first())
  }

  @Test
  fun finishReasonAndInterruptedAreStored() = runTest {
    val id = repository.save(
      TestBrews.finished(reason = FinishReason.DISCONNECTED),
      TestBrews.context(),
    )
    val entity = repository.get(id)!!.entity
    assertEquals(FinishReason.DISCONNECTED, entity.finishReason)
    assertTrue(entity.interrupted)
  }

  @Test
  fun aBrewWithAReferenceStoresTheColumnsAndTheTargetCurve() = runTest {
    val target = TestBrews.usedTarget(name = "V60 clásica", score = 86, factor = 16.4 / 15)
    val details = BrewDetails(method = BrewMethod.V60, grind = "Media", waterTempC = 93.0, coffee = "Etiopía")
    val id = repository.save(TestBrews.finished(), TestBrews.context(target = target, details = details))

    val record = repository.get(id)!!
    val entity = record.entity
    assertEquals(ReferenceType.RECIPE, entity.referenceType)
    assertEquals(1L, entity.referenceId)
    assertEquals("V60 clásica", entity.referenceName)
    assertEquals(86, entity.fitScore)
    assertEquals(target.curve.finalWaterG, entity.targetWaterG!!, 1e-9)
    assertEquals(target.curve.endMs, entity.targetEndMs)
    // Datos prellenados desde la referencia.
    assertEquals(BrewMethod.V60, entity.method)
    assertEquals("Media", entity.grind)
    assertEquals(93.0, entity.waterTempC!!, 0.0)
    assertEquals("Etiopía", entity.coffee)
    assertNull(entity.name)
    assertNull(entity.notes)
    // La curva guardada es la escalada, con error de 0,005 g como máximo.
    val stored = record.target!!
    assertFalse(record.targetError)
    // El formato guardado solo lleva los pasos POUR (D21): los SWIRL y WAIT no se persisten.
    assertEquals(target.curve.stages.count { it.type == StageType.POUR }, stored.stages.size)
    assertEquals(target.curve.finalWaterG, stored.finalWaterG, 0.005)
    assertEquals(target.curve.waterAt(55_000), stored.waterAt(55_000), 0.005)
    assertEquals(86, repository.observe(id).first()!!.entity.fitScore)
  }

  @Test
  fun aBrewWithoutAReferenceHasNullColumnsAndNoTarget() = runTest {
    val id = save()
    val record = repository.get(id)!!
    assertNull(record.entity.referenceType)
    assertNull(record.entity.referenceId)
    assertNull(record.entity.referenceName)
    assertNull(record.entity.targetWaterG)
    assertNull(record.entity.targetEndMs)
    assertNull(record.entity.fitScore)
    assertNull(record.target)
    assertFalse(record.targetError)
    assertNull(dao.target(id))
  }

  @Test
  fun aFitScoreThatCouldNotBeComputedStaysNullButKeepsTheReference() = runTest {
    val id = repository.save(TestBrews.finished(), TestBrews.context(target = TestBrews.usedTarget(score = null)))
    val entity = repository.get(id)!!.entity
    assertNull(entity.fitScore)
    assertEquals(ReferenceType.RECIPE, entity.referenceType)
  }

  @Test
  fun theListCarriesTheFitScore() = runTest {
    val withTarget = repository.save(TestBrews.finished(), TestBrews.context(startedAtEpochMs = 2, target = TestBrews.usedTarget(score = 73)))
    val without = save(startedAt = 1)
    val items = repository.list(BrewFilter()).first()
    assertEquals(73, items.single { it.id == withTarget }.fitScore)
    assertNull(items.single { it.id == without }.fitScore)
  }

  @Test
  fun deletingABrewCascadesToTheTargetAndRestoreBringsItBack() = runTest {
    val id = repository.save(TestBrews.finished(), TestBrews.context(target = TestBrews.usedTarget()))
    assertNotNull(dao.target(id))

    repository.delete(id)
    assertNull(dao.target(id))
    val deleted = repository.pendingUndo.value!!
    assertNotNull(deleted.target)
    assertEquals(id, deleted.target!!.brewId)

    repository.restore(deleted)
    assertNotNull(dao.target(id))
    val record = repository.get(id)!!
    assertNotNull(record.target)
    assertEquals(86, record.entity.fitScore)
  }

  @Test
  fun deletingABrewWithoutATargetLeavesNoTargetToRestore() = runTest {
    val id = save()
    repository.delete(id)
    assertNull(repository.pendingUndo.value!!.target)
    repository.restore(repository.pendingUndo.value!!)
    assertNull(dao.target(id))
  }

  @Test
  fun aCorruptTargetBlobSetsTargetErrorAndKeepsTheRealCurve() = runTest {
    val finished = TestBrews.finished()
    val id = repository.save(finished, TestBrews.context(target = TestBrews.usedTarget()))
    db.openHelper.writableDatabase.execSQL("UPDATE brew_targets SET data = ? WHERE brewId = ?", arrayOf<Any>(byteArrayOf(1, 2, 3), id))

    val record = repository.get(id)!!
    assertTrue(record.targetError)
    assertNull(record.target)
    assertFalse(record.curveError)
    assertEquals(finished.samples.size, record.samples.size)
    assertEquals(86, record.entity.fitScore)
    assertTrue(repository.observe(id).first()!!.targetError)
  }

  @Test
  fun countFollowsTheNumberOfBrews() = runTest {
    assertEquals(0, repository.count().first())
    save()
    save(startedAt = 2)
    assertEquals(2, repository.count().first())
  }

  private companion object {
    val BrewConfigVersion = dev.dotscale.brew.BrewConfig.METRIC_VERSION
  }
}
