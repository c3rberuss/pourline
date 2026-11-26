package dev.josueamaya.pourline.recipe.catalog

import dev.josueamaya.pourline.grinder.CatalogSource
import dev.josueamaya.pourline.grinder.Grinder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.IOException

/** Por qué falló el último intento de actualizar. */
enum class CatalogError {
  /** Sin red, error HTTP, tiempo agotado o un archivo que no vale. */
  UPDATE_FAILED,

  /** El archivo es de un `schemaVersion` más nuevo: hay que actualizar la app. */
  NEWER_SCHEMA,
}

/**
 * Lo que la interfaz ve del catálogo. [lastDownloadMs] es la hora de la última descarga correcta (o de la última
 * comprobación con 304), `null` si nunca hubo una: con él y [error] la interfaz elige el aviso.
 */
data class CatalogState(
  val loaded: Boolean = false,
  val entries: List<CatalogRecipeEntry> = emptyList(),
  val revision: Int? = null,
  val lastDownloadMs: Long? = null,
  val refreshing: Boolean = false,
  val error: CatalogError? = null,
)

/**
 * El catálogo de recetas recomendadas (SPEC-012 §3.3): elige la mejor copia local (la descargada o la del APK), la
 * actualiza cuando toca y la expone como estado. El trabajo corre en [scope] (el de la aplicación), así que rotar o salir
 * de la pantalla no lo cancela, y dos peticiones simultáneas se unen en una.
 */
class RecipeCatalogRepository(
  private val bundled: CatalogSource,
  private val cache: CatalogCache,
  private val fetcher: CatalogFetcher,
  private val grinders: () -> Map<String, Grinder>,
  private val scope: CoroutineScope,
  private val io: CoroutineDispatcher = Dispatchers.IO,
  private val now: () -> Long = System::currentTimeMillis,
) {
  private val _state = MutableStateFlow(CatalogState())
  val state: StateFlow<CatalogState> = _state

  // Solo se tocan desde el trabajo de carga (antes de `loaded`) o desde la descarga en curso, que es única.
  private var bundledCatalog: ParsedCatalog? = null
  private var cachedCatalog: ParsedCatalog? = null
  private var cachedEtag: String? = null

  private val initial: Deferred<Unit> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    scope.async(io, start = CoroutineStart.LAZY) { loadLocal() }
  }

  private val lock = Any()
  private var inFlight: Deferred<Unit>? = null

  /** Lee la copia local si aún no se hizo. Idempotente. */
  fun ensureLoaded() {
    initial.start()
  }

  /** Descarga si hace 24 h o más de la última descarga correcta (o no hubo ninguna). */
  fun refreshIfStale() {
    refresh(force = false)
  }

  /**
   * Descarga con [force] sin mirar las 24 h. Si ya hay una en curso devuelve esa. Se completa también si la descarga
   * falla: el resultado queda en [state].
   */
  fun refresh(force: Boolean): Deferred<Unit> = synchronized(lock) {
    inFlight?.takeIf { it.isActive }?.let { return it }
    val job = scope.async(io) { download(force) }
    inFlight = job
    job
  }

  /** La receta con esa [key]: no emite hasta que se leyó la copia local, y después emite `null` si no existe. */
  fun entry(key: String): Flow<CatalogRecipeEntry?> = flow {
    ensureLoaded()
    initial.await()
    emitAll(_state.map { state -> state.entries.firstOrNull { it.key == key } }.distinctUntilChanged())
  }

  private fun loadLocal() {
    val grinderMap = grinders()
    bundledCatalog = bundled.read()?.let { parseOrNull(it, grinderMap) }
    val cached = try {
      cache.read()
    } catch (_: CatalogCacheUnreadable) {
      cache.clear()
      null
    }
    var lastDownload: Long? = null
    if (cached != null) {
      val parsed = parseOrNull(cached.text, grinderMap)
      if (parsed != null) {
        cachedCatalog = parsed
        cachedEtag = cached.etag
        lastDownload = cached.downloadedAtMs
      } else {
        cache.clear()
      }
    }
    publish(lastDownload) { it.copy(loaded = true) }
  }

  private suspend fun download(force: Boolean) {
    initial.start()
    initial.await()
    if (!force && !CatalogPolicy.isStale(_state.value.lastDownloadMs, now())) return
    _state.update { it.copy(refreshing = true) }
    try {
      when (val result = fetcher.fetch(cachedEtag)) {
        is FetchResult.Fetched -> accept(result)
        FetchResult.NotModified -> {
          val time = now()
          try {
            cache.markChecked(time, cachedEtag)
            publish(time) { it.copy(error = null) }
          } catch (_: IOException) {
            _state.update { it.copy(error = CatalogError.UPDATE_FAILED) }
          }
        }
        FetchResult.Failed -> _state.update { it.copy(error = CatalogError.UPDATE_FAILED) }
      }
    } finally {
      _state.update { it.copy(refreshing = false) }
    }
  }

  private fun accept(result: FetchResult.Fetched) {
    when (val parsed = RecipeCatalogParser.parse(result.body, grinders())) {
      is CatalogParseResult.Parsed -> {
        val time = now()
        try {
          cache.write(result.body, result.etag, time)
        } catch (_: IOException) {
          _state.update { it.copy(error = CatalogError.UPDATE_FAILED) }
          return
        }
        cachedCatalog = parsed.catalog
        cachedEtag = result.etag
        publish(time) { it.copy(error = null) }
      }
      is CatalogParseResult.NewerSchema -> _state.update { it.copy(error = CatalogError.NEWER_SCHEMA) }
      CatalogParseResult.Corrupt -> _state.update { it.copy(error = CatalogError.UPDATE_FAILED) }
    }
  }

  /** Vuelve a elegir la copia (§3.3) y la publica con la hora de la última descarga. */
  private fun publish(lastDownloadMs: Long?, change: (CatalogState) -> CatalogState) {
    val chosen = CatalogPolicy.choose(cachedCatalog, bundledCatalog)
    _state.update { current ->
      change(current).copy(
        entries = chosen?.entries.orEmpty(),
        revision = chosen?.revision,
        lastDownloadMs = lastDownloadMs ?: current.lastDownloadMs,
      )
    }
  }

  private fun parseOrNull(text: String, grinderMap: Map<String, Grinder>): ParsedCatalog? =
    (RecipeCatalogParser.parse(text, grinderMap) as? CatalogParseResult.Parsed)?.catalog
}
