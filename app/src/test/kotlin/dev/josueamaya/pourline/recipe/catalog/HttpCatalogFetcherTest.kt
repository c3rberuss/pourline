package dev.josueamaya.pourline.recipe.catalog

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

class HttpCatalogFetcherTest {
  private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
  private val url get() = "http://127.0.0.1:${server.address.port}/recipes.json"

  /** Las cabeceras de la última petición, con el nombre en minúsculas. */
  @Volatile private var headers: Map<String, String> = emptyMap()

  @After
  fun stop() {
    server.stop(0)
  }

  private fun serve(handler: (HttpExchange) -> Unit) {
    server.createContext("/recipes.json") { exchange ->
      headers = exchange.requestHeaders.entries.associate { (k, v) -> k.lowercase() to v.first() }
      handler(exchange)
      exchange.close()
    }
    server.start()
  }

  private fun HttpExchange.reply(code: Int, body: String = "", etag: String? = null) {
    if (etag != null) responseHeaders.add("ETag", etag)
    val bytes = body.toByteArray()
    sendResponseHeaders(code, if (code == 304) -1 else bytes.size.toLong())
    if (code != 304) responseBody.use { it.write(bytes) }
  }

  private fun fetch(fetcher: HttpCatalogFetcher, etag: String? = null) = runBlocking { fetcher.fetch(etag) }

  @Test
  fun `a 200 returns the body and its etag`() {
    serve { it.reply(200, """{"ñ":"café"}""", etag = "\"v1\"") }
    assertEquals(FetchResult.Fetched("""{"ñ":"café"}""", "\"v1\""), fetch(HttpCatalogFetcher(url)))
  }

  @Test
  fun `the etag is sent and a 304 is not modified`() {
    serve { exchange ->
      if (exchange.requestHeaders.getFirst("If-None-Match") == "\"v1\"") exchange.reply(304) else exchange.reply(200, "x", "\"v1\"")
    }
    val fetcher = HttpCatalogFetcher(url)
    assertEquals(FetchResult.Fetched("x", "\"v1\""), fetch(fetcher))
    assertEquals(FetchResult.NotModified, fetch(fetcher, "\"v1\""))
    assertEquals("\"v1\"", headers["if-none-match"])
  }

  @Test
  fun `an http error, a refused connection or a timeout fail`() {
    serve { exchange ->
      when (exchange.requestURI.query) {
        "slow" -> Thread.sleep(1_000)
        else -> Unit
      }
      exchange.reply(404, "no")
    }
    assertEquals(FetchResult.Failed, fetch(HttpCatalogFetcher(url)))
    assertEquals(FetchResult.Failed, fetch(HttpCatalogFetcher("$url?slow", timeoutMs = 200)))
    val closedPort = ServerSocket(0).use { it.localPort }
    assertEquals(FetchResult.Failed, fetch(HttpCatalogFetcher("http://127.0.0.1:$closedPort/recipes.json", timeoutMs = 500)))
  }

  @Test
  fun `a body over the limit fails`() {
    serve { it.reply(200, "x".repeat(2_000)) }
    assertEquals(FetchResult.Failed, fetch(HttpCatalogFetcher(url, maxBytes = 1_000)))
    assertEquals(FetchResult.Fetched("x".repeat(2_000), null), fetch(HttpCatalogFetcher(url, maxBytes = 2_000)))
  }

  @Test
  fun `only the accept, user agent and etag headers are sent`() {
    serve { it.reply(200, "ok") }
    fetch(HttpCatalogFetcher(url), etag = "\"e\"")
    assertEquals("Pourline", headers["user-agent"])
    assertEquals("application/json", headers["accept"])
    assertEquals("\"e\"", headers["if-none-match"])
    // Lo demás es del protocolo (host, connection) o de la compresión transparente y de `useCaches = false` (`Pragma` y `Cache-Control`); nada del usuario ni del teléfono.
    val allowed = setOf("accept", "user-agent", "if-none-match", "host", "connection", "accept-encoding", "pragma", "cache-control")
    assertTrue(headers.keys.toString(), allowed.containsAll(headers.keys))
    assertTrue(headers.keys.none { it in setOf("accept-language", "cookie", "authorization", "x-requested-with") })
  }
}
