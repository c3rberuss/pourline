package dev.josueamaya.pourline.recipe.catalog

import dev.josueamaya.pourline.grinder.CatalogSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Un [CatalogFetcher] guionizado: los resultados de [script] en orden y, agotados, `Failed`. [gate] retiene la respuesta. */
private class ScriptedFetcher(vararg script: FetchResult) : CatalogFetcher {
  private val queue = ArrayDeque(script.toList())
  private val count = AtomicInteger()
  var gate: CompletableDeferred<Unit>? = null
  var lastEtag: String? = null
  val calls: Int get() = count.get()

  override suspend fun fetch(etag: String?): FetchResult {
    count.incrementAndGet()
    lastEtag = etag
    gate?.await()
    return queue.removeFirstOrNull() ?: FetchResult.Failed
  }
}

/**
 * La carga y las descargas corren en `backgroundScope` (como en el ámbito de la aplicación) y `advanceUntilIdle` no
 * ejecuta trabajo de fondo: estos tests usan `runCurrent`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeCatalogRepositoryTest {
  @get:Rule val folder = TemporaryFolder()

  private val hour = 60 * 60 * 1000L
  private var clock = 1_000 * hour

  private fun json(revision: Int, vararg ids: String) = catalogJson(*ids.map { recipeJson(it) }.toTypedArray(), revision = revision)

  private fun cacheDir() = File(folder.root, "catalog")

  private fun TestScope.repository(
    bundled: String?,
    fetcher: CatalogFetcher,
    cache: CatalogCache = CatalogCache(cacheDir()),
  ) = RecipeCatalogRepository(
    bundled = CatalogSource { bundled },
    cache = cache,
    fetcher = fetcher,
    grinders = { testGrinders() },
    scope = backgroundScope,
    io = StandardTestDispatcher(testScheduler),
    now = { clock },
  )

  private suspend fun TestScope.loaded(repository: RecipeCatalogRepository): CatalogState {
    repository.ensureLoaded()
    runCurrent()
    return repository.state.value
  }

  @Test
  fun `the bundled copy is used until something newer is downloaded`() = runTest {
    val repository = repository(json(1, "uno", "dos"), ScriptedFetcher())
    assertFalse(repository.state.value.loaded)
    val state = loaded(repository)
    assertTrue(state.loaded)
    assertEquals(listOf("uno", "dos"), state.entries.map { it.key })
    assertEquals(1, state.revision)
    assertNull(state.lastDownloadMs)
    assertNull(state.error)
  }

  @Test
  fun `a downloaded catalog with a higher revision replaces the bundled one`() = runTest {
    val fetcher = ScriptedFetcher(FetchResult.Fetched(json(2, "uno", "nueva"), "\"e2\""))
    val cache = CatalogCache(cacheDir())
    val repository = repository(json(1, "uno"), fetcher, cache)
    loaded(repository)
    repository.refresh(force = true).await()
    val state = repository.state.value
    assertEquals(2, state.revision)
    assertEquals(listOf("uno", "nueva"), state.entries.map { it.key })
    assertEquals(clock, state.lastDownloadMs)
    assertNull(state.error)
    assertFalse(state.refreshing)
    assertEquals(CachedCatalog(json(2, "uno", "nueva"), clock, "\"e2\""), cache.read())
  }

  @Test
  fun `an app update with a newer bundled catalog beats an older cache`() = runTest {
    val cache = CatalogCache(cacheDir())
    cache.write(json(1, "vieja"), "\"e1\"", clock - hour)
    val state = loaded(repository(json(3, "empaquetada"), ScriptedFetcher(), cache))
    assertEquals(3, state.revision)
    assertEquals(listOf("empaquetada"), state.entries.map { it.key })
    assertEquals(clock - hour, state.lastDownloadMs)
  }

  @Test
  fun `a cache with the same revision as the bundled copy wins the tie`() = runTest {
    val cache = CatalogCache(cacheDir())
    cache.write(json(2, "descargada"), null, clock - hour)
    val state = loaded(repository(json(2, "empaquetada"), ScriptedFetcher(), cache))
    assertEquals(listOf("descargada"), state.entries.map { it.key })
  }

  @Test
  fun `a fresh download is skipped within 24 hours and forced by refresh`() = runTest {
    val cache = CatalogCache(cacheDir())
    cache.write(json(1, "uno"), "\"e1\"", clock - hour)
    val fetcher = ScriptedFetcher(FetchResult.Fetched(json(2, "dos"), null))
    val repository = repository(json(1, "uno"), fetcher, cache)
    loaded(repository)
    repository.refresh(force = false).await()
    assertEquals(0, fetcher.calls)
    clock += 24 * hour
    repository.refresh(force = false).await()
    assertEquals(1, fetcher.calls)
    assertEquals(2, repository.state.value.revision)
    repository.refresh(force = true).await()
    assertEquals(2, fetcher.calls)
  }

  @Test
  fun `a corrupt download keeps the cache and reports the failure`() = runTest {
    val cache = CatalogCache(cacheDir())
    cache.write(json(2, "buena"), "\"e1\"", clock - 48 * hour)
    val repository = repository(json(1, "uno"), ScriptedFetcher(FetchResult.Fetched("esto no es json", null)), cache)
    loaded(repository)
    repository.refreshIfStale()
    runCurrent()
    val state = repository.state.value
    assertEquals(CatalogError.UPDATE_FAILED, state.error)
    assertEquals(listOf("buena"), state.entries.map { it.key })
    assertEquals(clock - 48 * hour, state.lastDownloadMs)
    assertEquals(json(2, "buena"), cache.read()?.text)
  }

  @Test
  fun `a failed download keeps the list and a later success clears the error`() = runTest {
    val fetcher = ScriptedFetcher(FetchResult.Failed, FetchResult.Fetched(json(2, "nueva"), null))
    val repository = repository(json(1, "uno"), fetcher)
    loaded(repository)
    repository.refresh(force = true).await()
    assertEquals(CatalogError.UPDATE_FAILED, repository.state.value.error)
    assertEquals(listOf("uno"), repository.state.value.entries.map { it.key })
    assertNull(repository.state.value.lastDownloadMs)
    repository.refresh(force = true).await()
    assertNull(repository.state.value.error)
    assertEquals(listOf("nueva"), repository.state.value.entries.map { it.key })
  }

  @Test
  fun `a catalog without valid recipes does not replace the cache`() = runTest {
    val cache = CatalogCache(cacheDir())
    cache.write(json(2, "buena"), null, clock - 48 * hour)
    val invalid = catalogJson(recipeJson("mala", mapOf("method" to JsonPrimitive("SIPHON"))), revision = 9)
    val repository = repository(json(1, "uno"), ScriptedFetcher(FetchResult.Fetched(invalid, null)), cache)
    loaded(repository)
    repository.refresh(force = true).await()
    assertEquals(CatalogError.UPDATE_FAILED, repository.state.value.error)
    assertEquals(2, repository.state.value.revision)
    assertEquals(json(2, "buena"), cache.read()?.text)
  }

  @Test
  fun `a newer schema keeps the cache and asks to update the app`() = runTest {
    val cache = CatalogCache(cacheDir())
    cache.write(json(2, "buena"), null, clock - 48 * hour)
    val newer = """{"schemaVersion":2,"revision":50,"recipes":[]}"""
    val repository = repository(json(1, "uno"), ScriptedFetcher(FetchResult.Fetched(newer, null)), cache)
    loaded(repository)
    repository.refresh(force = true).await()
    val state = repository.state.value
    assertEquals(CatalogError.NEWER_SCHEMA, state.error)
    assertEquals(2, state.revision)
    assertEquals(clock - 48 * hour, state.lastDownloadMs)
    assertEquals(json(2, "buena"), cache.read()?.text)
  }

  @Test
  fun `an unreadable cache falls back to the bundled copy and is deleted`() = runTest {
    File(cacheDir(), "recipes.json").mkdirs()
    val state = loaded(repository(json(1, "uno"), ScriptedFetcher()))
    assertEquals(listOf("uno"), state.entries.map { it.key })
    assertNull(state.lastDownloadMs)
    assertFalse(cacheDir().exists())
  }

  @Test
  fun `a cache that is not a catalog falls back to the bundled copy and is deleted`() = runTest {
    CatalogCache(cacheDir()).write("basura", null, clock)
    val state = loaded(repository(json(1, "uno"), ScriptedFetcher()))
    assertEquals(listOf("uno"), state.entries.map { it.key })
    assertNull(state.lastDownloadMs)
    assertFalse(File(cacheDir(), "recipes.json").exists())
  }

  @Test
  fun `two refreshes at the same time make a single request`() = runTest {
    val fetcher = ScriptedFetcher(FetchResult.Fetched(json(2, "nueva"), null)).apply { gate = CompletableDeferred() }
    val repository = repository(json(1, "uno"), fetcher)
    loaded(repository)
    val first = repository.refresh(force = true)
    val second = repository.refresh(force = true)
    runCurrent()
    assertTrue(repository.state.value.refreshing)
    fetcher.gate!!.complete(Unit)
    first.await()
    second.await()
    assertEquals(1, fetcher.calls)
    assertFalse(repository.state.value.refreshing)
    assertEquals(2, repository.state.value.revision)
  }

  @Test
  fun `a not modified answer updates the time without an error`() = runTest {
    val cache = CatalogCache(cacheDir())
    cache.write(json(2, "buena"), "\"e1\"", clock - 48 * hour)
    val fetcher = ScriptedFetcher(FetchResult.NotModified)
    val repository = repository(json(1, "uno"), fetcher, cache)
    loaded(repository)
    repository.refreshIfStale()
    runCurrent()
    assertEquals("\"e1\"", fetcher.lastEtag)
    val state = repository.state.value
    assertNull(state.error)
    assertEquals(clock, state.lastDownloadMs)
    assertEquals(CachedCatalog(json(2, "buena"), clock, "\"e1\""), cache.read())
  }

  @Test
  fun `entry waits for the first load and then emits null for an unknown key`() = runTest {
    val repository = repository(json(1, "uno", "dos"), ScriptedFetcher())
    assertEquals("dos", repository.entry("dos").first()?.key)
    assertNull(repository.entry("no_existe").first())
    assertTrue(repository.state.value.loaded)
  }

  @Test
  fun `entry follows the catalog when it is updated`() = runTest {
    val fetcher = ScriptedFetcher(FetchResult.Fetched(json(2, "dos"), null))
    val repository = repository(json(1, "uno", "dos"), fetcher)
    loaded(repository)
    assertEquals("dos", repository.entry("dos").first()?.key)
    repository.refresh(force = true).await()
    assertEquals("dos", repository.entry("dos").first()?.key)
    assertNull(repository.entry("uno").first())
  }

  @Test
  fun `without any local copy the list is empty but loaded`() = runTest {
    val state = loaded(repository(null, ScriptedFetcher()))
    assertTrue(state.loaded)
    assertTrue(state.entries.isEmpty())
    assertNull(state.revision)
  }
}
