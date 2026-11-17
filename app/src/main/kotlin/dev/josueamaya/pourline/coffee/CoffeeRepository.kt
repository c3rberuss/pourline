package dev.josueamaya.pourline.coffee

import dev.josueamaya.pourline.history.HistoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** Quien borra los archivos de fotos de un café (la foto y su miniatura). En F2, un no-op; luego, `CoffeePhotoStore`. */
fun interface PhotoFiles {
  fun delete(names: Collection<String>)
}

/**
 * Catálogo de cafés. Singleton en `PourlineApp`. Los días desde el tueste se calculan aquí con [today] y
 * [zone], inyectados para poder probarlos; el stock sale de SQL (D19).
 */
class CoffeeRepository(
  db: HistoryDatabase,
  private val photos: PhotoFiles,
  private val today: () -> LocalDate = LocalDate::now,
  private val zone: () -> ZoneId = ZoneId::systemDefault,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  private val dao = db.coffeeDao()

  private val _created = MutableSharedFlow<Long>(extraBufferCapacity = 8)

  /** El id de cada alta (no de las ediciones): quien pidió "Nuevo café" lo recoge y lo elige (D23). */
  val created: SharedFlow<Long> = _created.asSharedFlow()

  /** Por uso más reciente. [query] busca en nombre, tostador, país y región sin distinguir mayúsculas ni tildes (D18). */
  fun coffees(query: String, archived: Boolean): Flow<List<CoffeeListItem>> =
    dao.list(archived).map { rows -> rows.map(::toItem).filter { CoffeeSearch.matches(it, query) } }.flowOn(Dispatchers.Default)

  /** Los no archivados (también los agotados), por uso más reciente. */
  fun pickable(): Flow<List<CoffeeListItem>> = dao.pickable().map { rows -> rows.map(::toItem) }.flowOn(Dispatchers.Default)

  /** El café con su estado y su bitácora; emite `null` cuando la fila desaparece. */
  fun coffee(id: Long): Flow<CoffeeDetail?> =
    combine(dao.observe(id), dao.observeRow(id), dao.logEntries(id)) { entity, row, log ->
      if (entity == null || row == null) return@combine null
      val zoneId = zone()
      val soldOut = isSoldOut(row.stockG)
      CoffeeDetail(
        coffee = entity,
        stockG = row.stockG?.let(::displayStock),
        soldOut = soldOut,
        daysSinceRoast = daysSinceRoast(entity.roastDate, today()),
        brews = log.map {
          CoffeeLogEntry(it.brewId, it.startedAtEpochMs, it.rating, it.notes, brewDaysSinceRoast(entity.roastDate, it.startedAtEpochMs, zoneId), it.doseG)
        },
      )
    }.flowOn(Dispatchers.Default)

  /** El café tal como está guardado, para el editor. */
  suspend fun get(id: Long): CoffeeEntity? = withContext(Dispatchers.IO) { dao.get(id) }

  /** Alta o edición; devuelve el id. Borra las fotos sustituidas o quitadas y, en un alta, emite en [created]. */
  suspend fun save(draft: CoffeeDraft): Long = withContext(Dispatchers.IO + NonCancellable) {
    val result = dao.save(draft, clock())
    if (result.removedPhotos.isNotEmpty()) photos.delete(result.removedPhotos)
    if (result.inserted) _created.emit(result.id)
    result.id
  }

  suspend fun setArchived(id: Long, archived: Boolean) {
    withContext(Dispatchers.IO + NonCancellable) { dao.setArchived(id, archived) }
  }

  /** Borra el café en una transacción y luego sus archivos de fotos. Sus preparaciones conservan el nombre. */
  suspend fun delete(id: Long) {
    withContext(Dispatchers.IO + NonCancellable) {
      val deleted = dao.delete(id) ?: return@withContext
      val names = listOfNotNull(deleted.photo1, deleted.photo2)
      if (names.isNotEmpty()) photos.delete(names)
    }
  }

  /** Nombres de foto que algún café referencia: lo que no se debe barrer. */
  suspend fun referencedPhotos(): Set<String> = withContext(Dispatchers.IO) { dao.photoNames().toSet() }

  /** Valores distintos de tostador, país, región y variedades. */
  fun suggestions(): Flow<CoffeeSuggestions> = dao.observeSuggestionRows().map { it.toSuggestions() }.flowOn(Dispatchers.Default)

  private fun toItem(row: CoffeeListRow): CoffeeListItem = CoffeeListItem(
    id = row.id,
    name = row.name,
    roaster = row.roaster,
    originCountry = row.originCountry,
    originRegion = row.originRegion,
    roastDate = row.roastDate,
    daysSinceRoast = daysSinceRoast(row.roastDate, today()),
    stockG = row.stockG?.let(::displayStock),
    soldOut = isSoldOut(row.stockG),
    photo1 = row.photo1,
    archived = row.archived,
  )
}
