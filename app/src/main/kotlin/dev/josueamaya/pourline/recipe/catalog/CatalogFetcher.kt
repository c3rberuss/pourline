package dev.josueamaya.pourline.recipe.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Lo que da una descarga del catálogo. */
sealed interface FetchResult {
  /** 200: el cuerpo ya decodificado y el `ETag` de la respuesta, si lo trae. */
  data class Fetched(val body: String, val etag: String?) : FetchResult

  /** 304: el archivo no cambió desde el `ETag` enviado. */
  data object NotModified : FetchResult

  /** Sin red, tiempo agotado, código de error o cuerpo demasiado grande. */
  data object Failed : FetchResult
}

/** De dónde se descarga el catálogo. Una interfaz para que los tests usen una descarga guionizada. */
interface CatalogFetcher {
  /** Pide el archivo; con [etag] la petición es condicional (`If-None-Match`). */
  suspend fun fetch(etag: String?): FetchResult
}

/**
 * GET anónimo con `HttpURLConnection`, sin dependencias nuevas (SPEC-012 §3.3). Solo envía `Accept`, `User-Agent: Pourline`
 * e `If-None-Match`: el `User-Agent` por defecto de Android lleva el modelo del teléfono y no se usa.
 */
class HttpCatalogFetcher(
  private val url: String,
  private val timeoutMs: Int = 10_000,
  private val maxBytes: Int = 1_048_576,
) : CatalogFetcher {
  override suspend fun fetch(etag: String?): FetchResult = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
      connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = timeoutMs
        readTimeout = timeoutMs
        useCaches = false
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "Pourline")
        if (etag != null) setRequestProperty("If-None-Match", etag)
      }
      when (connection.responseCode) {
        HttpURLConnection.HTTP_OK -> {
          val bytes = connection.inputStream.use { readBounded(it) } ?: return@withContext FetchResult.Failed
          FetchResult.Fetched(bytes.toString(Charsets.UTF_8), connection.getHeaderField("ETag"))
        }
        HttpURLConnection.HTTP_NOT_MODIFIED -> FetchResult.NotModified
        else -> FetchResult.Failed
      }
    } catch (_: IOException) {
      FetchResult.Failed
    } catch (_: SecurityException) {
      // Sin el permiso INTERNET la conexión lanza esto.
      FetchResult.Failed
    } finally {
      connection?.disconnect()
    }
  }

  /** Lee como mucho [maxBytes]; `null` si el cuerpo es más grande. */
  private fun readBounded(input: InputStream): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
      val read = input.read(buffer)
      if (read < 0) break
      if (out.size() + read > maxBytes) return null
      out.write(buffer, 0, read)
    }
    return out.toByteArray()
  }
}
