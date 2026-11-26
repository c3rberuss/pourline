package dev.josueamaya.pourline.grinder

import android.database.sqlite.SQLiteConstraintException
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.recipe.FakeRecipeTexts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Los pares de calibración en Room en memoria: el orden canónico, las transacciones y la `CASCADE`. */
class GrindCalibrationDaoTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var db: HistoryDatabase
  private val dao get() = db.grindCalibrationDao()
  private var uno = 0L
  private var dos = 0L
  private var tres = 0L

  @Before
  fun setUp() = runTest {
    db = HistoryDatabase.inMemory(context)
    uno = db.addGrinder(TestGrinders.uno)
    dos = db.addGrinder(TestGrinders.dos)
    tres = db.addGrinder(TestGrinders.tres)
  }

  @After
  fun tearDown() = db.close()

  private suspend fun grinder(id: Long) = db.grinderDao().get(id)!!.toGrinder()

  private suspend fun save(id: Long, value: Double, otherId: Long, otherValue: Double, editingId: Long? = null) =
    dao.save(grinder(id), value, grinder(otherId), otherValue, editingId, nowMs = 2_000L)

  private suspend fun replace(id: Long, value: Double, otherId: Long, otherValue: Double, editingId: Long? = null) =
    dao.replace(grinder(id), value, grinder(otherId), otherValue, editingId, nowMs = 2_000L)

  private suspend fun pairs(a: Long, b: Long) = dao.between(a, b).map { it.toCalibration() }

  @Test
  fun saveStoresThePairInCanonicalOrder() = runTest {
    // Se guarda con el molino de mayor id primero: Dos 30 = Uno 20.
    val saved = save(dos, 30.0, uno, 20.0) as CalibrationSaveResult.Saved
    val row = dao.get(saved.id)!!
    assertEquals(uno, row.grinderAId)
    assertEquals(20.0, row.valueA, 0.0)
    assertEquals(dos, row.grinderBId)
    assertEquals(30.0, row.valueB, 0.0)
    assertEquals(2_000L, row.updatedAtEpochMs)
    assertEquals(listOf(GrindCalibration(saved.id, uno, 20.0, dos, 30.0)), pairs(uno, dos))
  }

  @Test
  fun observeOfListsThePairsOnEitherSide() = runTest {
    db.addCalibration(uno, 20.0, dos, 30.0)
    db.addCalibration(dos, 10.0, tres, 40.0)
    db.addCalibration(uno, 10.0, tres, 30.0)
    assertEquals(2, dao.observeOf(uno).first().size)
    assertEquals(2, dao.observeOf(dos).first().size)
    assertEquals(2, dao.observeOf(tres).first().size)
    assertEquals(1, dao.observeBetween(uno, dos).first().size)
    assertEquals(0, dao.observeBetween(dos, uno).first().size) // fuera del orden canónico
  }

  @Test
  fun saveWithARepeatedValueUpdatesThePair() = runTest {
    val first = (save(uno, 20.0, dos, 30.0) as CalibrationSaveResult.Saved).id
    // El mismo valor de Uno con otro de Dos.
    assertEquals(CalibrationSaveResult.Updated(first), save(uno, 20.0, dos, 31.0))
    assertEquals(listOf(GrindCalibration(first, uno, 20.0, dos, 31.0)), pairs(uno, dos))
    // El mismo valor de Dos visto desde el otro molino.
    assertEquals(CalibrationSaveResult.Updated(first), save(dos, 31.0, uno, 22.0))
    assertEquals(listOf(GrindCalibration(first, uno, 22.0, dos, 31.0)), pairs(uno, dos))
  }

  @Test
  fun saveWithAConflictWritesNothing() = runTest {
    val first = db.addCalibration(uno, 20.0, dos, 30.0)
    val result = save(uno, 25.0, dos, 25.0)
    assertEquals(CalibrationSaveResult.Conflict(listOf(GrindCalibration(first, uno, 20.0, dos, 30.0))), result)
    assertEquals(1, pairs(uno, dos).size)
  }

  @Test
  fun saveRejectsAnInvalidPair() = runTest {
    val result = save(uno, 45.0, dos, 30.0)
    assertEquals(CalibrationSaveResult.Invalid(listOf(CalibrationError.OutOfScale(uno))), result)
    assertTrue(pairs(uno, dos).isEmpty())
  }

  @Test
  fun editingAPairUpdatesItsRow() = runTest {
    val id = (save(uno, 20.0, dos, 30.0) as CalibrationSaveResult.Saved).id
    assertEquals(CalibrationSaveResult.Saved(id), save(uno, 22.0, dos, 31.0, editingId = id))
    assertEquals(listOf(GrindCalibration(id, uno, 22.0, dos, 31.0)), pairs(uno, dos))
  }

  @Test
  fun replaceDeletesTheConflictsAndSavesInOneTransaction() = runTest {
    db.addCalibration(uno, 10.0, dos, 10.0)
    db.addCalibration(uno, 20.0, dos, 20.0)
    val keep = db.addCalibration(uno, 30.0, dos, 30.0)
    // Uno 5 = Dos 35 contradice a los tres; Uno 25 = Dos 5 contradice a los dos primeros.
    val result = replace(uno, 25.0, dos, 5.0)
    val saved = result as CalibrationSaveResult.Saved
    assertEquals(
      listOf(GrindCalibration(keep, uno, 30.0, dos, 30.0), GrindCalibration(saved.id, uno, 25.0, dos, 5.0)).sortedBy { it.id },
      pairs(uno, dos).sortedBy { it.id },
    )
  }

  @Test
  fun replaceActsLikeSaveWithoutConflicts() = runTest {
    assertTrue(replace(uno, 20.0, dos, 30.0) is CalibrationSaveResult.Saved)
    assertTrue(replace(uno, 20.0, dos, 31.0) is CalibrationSaveResult.Updated)
    assertEquals(1, pairs(uno, dos).size)
  }

  @Test
  fun replaceRollsBackWhenTheInsertFails() = runTest {
    // Un par con un molino que no existe (con las claves foráneas apagadas un momento) contradice al nuevo: `replace`
    // lo borra y después la inserción falla por la clave foránea. La transacción entera se deshace.
    val sqlite = db.openHelper.writableDatabase
    sqlite.execSQL("PRAGMA foreign_keys = OFF")
    sqlite.execSQL("INSERT INTO grind_calibrations (grinderAId, valueA, grinderBId, valueB, updatedAtEpochMs) VALUES ($dos, 10.0, 999, 10.0, 1000)")
    sqlite.execSQL("PRAGMA foreign_keys = ON")
    val ghost = grinder(dos).copy(id = 999L)
    try {
      dao.replace(ghost, 20.0, grinder(dos), 5.0, editingId = null, nowMs = 2_000L)
      fail("la inserción debía fallar")
    } catch (expected: SQLiteConstraintException) {
      // esperado
    }
    assertEquals(1, dao.between(dos, 999L).size)
  }

  @Test
  fun uniqueIndexesRejectADuplicateValue() = runTest {
    db.addCalibration(uno, 20.0, dos, 30.0)
    try {
      db.addCalibration(uno, 20.0, dos, 31.0)
      fail("valueA repetido")
    } catch (expected: SQLiteConstraintException) {
      // esperado
    }
    try {
      db.addCalibration(uno, 21.0, dos, 30.0)
      fail("valueB repetido")
    } catch (expected: SQLiteConstraintException) {
      // esperado
    }
    // Otro molino con los mismos valores sí cabe.
    db.addCalibration(uno, 20.0, tres, 30.0)
    assertEquals(2, dao.observeOf(uno).first().size)
  }

  @Test
  fun deletingAGrinderDeletesItsPairs() = runTest {
    db.addCalibration(uno, 20.0, dos, 30.0)
    db.addCalibration(dos, 10.0, tres, 40.0)
    db.grinderDao().deleteRelabeling(dos, emptyMap(), emptyMap())
    assertTrue(dao.observeOf(uno).first().isEmpty())
    assertTrue(dao.observeOf(tres).first().isEmpty())
  }

  @Test
  fun editingAGrinderKeepsItsPairs() = runTest {
    val id = db.addCalibration(uno, 20.0, dos, 30.0)
    // `GrinderDao.save` con el mismo id es un @Update: no borra la fila y no dispara la CASCADE.
    db.grinderDao().save(TestGrinders.uno.copy(id = uno, model = "Uno bis").toRows(5_000L))
    assertEquals(listOf(GrindCalibration(id, uno, 20.0, dos, 30.0)), pairs(uno, dos))
    assertEquals("Uno bis", grinder(uno).model)
  }

  @Test
  fun deleteByIdRemovesOnlyThatPair() = runTest {
    val first = db.addCalibration(uno, 10.0, dos, 10.0)
    val second = db.addCalibration(uno, 20.0, dos, 20.0)
    dao.deleteById(first)
    assertEquals(listOf(second), pairs(uno, dos).map { it.id })
  }
}
