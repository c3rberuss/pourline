package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.settings.DefaultGrinderStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/** Un molino que no se puede guardar porque el validador lo rechaza (§3.5). */
class InvalidGrinderException(val errors: List<GrinderError>) : IllegalArgumentException("Invalid grinder: $errors")

/**
 * Los molinos (SPEC-006). Singleton en `PourlineApp`. Las escrituras corren en `Dispatchers.IO + NonCancellable`
 * para que salir de la pantalla no las deje a medias.
 */
class GrinderRepository(
  db: HistoryDatabase,
  private val defaults: DefaultGrinderStore,
  private val texts: GrindTexts,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  private val dao = db.grinderDao()

  /** "Mis molinos": los propios y los predefinidos marcados "Lo tengo", por marca y modelo (D28). */
  fun owned(): Flow<List<Grinder>> = dao.observeOwned().map(::sorted).flowOn(Dispatchers.Default)

  /** Los predefinidos del catálogo, sin los retirados, por marca y modelo. */
  fun catalog(): Flow<List<Grinder>> = dao.observeCatalog().map(::sorted).flowOn(Dispatchers.Default)

  /** Todos, retirados incluidos, por marca y modelo. */
  fun all(): Flow<List<Grinder>> = dao.observeAll().map(::sorted).flowOn(Dispatchers.Default)

  /** Emite `null` si el molino desaparece o [id] es `null`. */
  fun grinder(id: Long?): Flow<Grinder?> =
    if (id == null) flowOf(null) else dao.observe(id).map { it?.toGrinder() }.flowOn(Dispatchers.Default)

  suspend fun get(id: Long): Grinder? = withContext(Dispatchers.IO) { dao.get(id)?.toGrinder() }

  /** Cuántas recetas usan el molino. */
  fun recipeCount(id: Long): Flow<Int> = dao.recipeCount(id)

  /**
   * Inserta (`id = 0`) o actualiza un molino **propio** y devuelve su id. Lanza [InvalidGrinderException] si no
   * pasa el validador y [IllegalArgumentException] si es un predefinido (`catalogKey != null`).
   */
  suspend fun save(grinder: Grinder): Long {
    require(grinder.catalogKey == null) { "Predefined grinders are read-only" }
    val errors = GrinderValidator.validate(grinder)
    if (errors.isNotEmpty()) throw InvalidGrinderException(errors)
    return withContext(Dispatchers.IO + NonCancellable) {
      dao.save(grinder.copy(owned = true, retired = false).toRows(clock()))
    }
  }

  /**
   * El borrador de "Duplicar para personalizar" (D22): una copia en memoria, sin guardar, o `null` si no existe.
   */
  suspend fun duplicateDraft(id: Long): Grinder? = get(id)?.let { original ->
    original.copy(
      id = 0,
      catalogKey = null,
      model = texts.copyName(original.model),
      owned = true,
      retired = false,
      sourceUrl = null,
      sourceName = null,
      duplicatedFromKey = original.catalogKey ?: original.duplicatedFromKey,
    )
  }

  /** Añade o quita un predefinido de "Mis molinos". Los propios siempre están: no se pueden quitar. */
  suspend fun setOwned(id: Long, owned: Boolean) {
    withContext(Dispatchers.IO + NonCancellable) {
      val grinder = dao.get(id)?.grinder ?: return@withContext
      if (grinder.catalogKey == null) return@withContext
      dao.setOwned(id, owned, clock())
      if (!owned && defaults.defaultGrinderId.value == id) defaults.setDefaultGrinderId(null)
    }
  }

  /**
   * Borra un molino **propio** y devuelve `true` (D17): antes regenera la copia de texto de las recetas y
   * preparaciones que lo usan, que conservan valor y copia con `grinderId = NULL`. Un predefinido no se borra.
   */
  suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO + NonCancellable) {
    val grinder = dao.get(id)?.toGrinder() ?: return@withContext false
    if (grinder.catalogKey != null) return@withContext false
    fun labels(uses: List<GrindUse>) = uses.filter { it.value != null }.associate { it.id to GrindFormat.label(grinder, it.value, texts) }
    dao.deleteRelabeling(id, labels(dao.recipesUsing(id)), labels(dao.brewsUsing(id)))
    if (defaults.defaultGrinderId.value == id) defaults.setDefaultGrinderId(null)
    true
  }

  private fun sorted(all: List<GrinderWithDetails>): List<Grinder> {
    val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }
    return all.map { it.toGrinder() }.sortedWith { a, b ->
      collator.compare(a.brand, b.brand).takeIf { it != 0 }
        ?: collator.compare(a.model, b.model).takeIf { it != 0 }
        ?: a.id.compareTo(b.id)
    }
  }
}
