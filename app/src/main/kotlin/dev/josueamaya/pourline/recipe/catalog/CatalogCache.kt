package dev.josueamaya.pourline.recipe.catalog

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** El último catálogo descargado y válido, con la hora de la última comprobación correcta y el `ETag` de entonces. */
data class CachedCatalog(val text: String, val downloadedAtMs: Long?, val etag: String?)

/** El archivo del catálogo existe pero no se puede leer (por ejemplo, es un directorio). */
class CatalogCacheUnreadable(cause: Throwable) : Exception(cause)

@Serializable
private data class CacheMeta(val downloadedAtEpochMs: Long? = null, val etag: String? = null)

/**
 * `dir/recipes.json` y `dir/recipes.meta.json`. Cada archivo se escribe a un `*.tmp` del mismo directorio y se renombra,
 * primero el catálogo y después los metadatos: un fallo entre los dos deja un catálogo válido con la hora anterior, y
 * como mucho habrá una descarga de más (§3.3).
 */
class CatalogCache(private val dir: File) {
  private val catalogFile get() = File(dir, CATALOG_NAME)
  private val metaFile get() = File(dir, META_NAME)
  private val json = Json { ignoreUnknownKeys = true }

  /** `null` si no hay catálogo guardado. */
  fun read(): CachedCatalog? {
    val file = catalogFile
    if (!file.exists()) return null
    val text = try {
      if (file.isDirectory) throw IOException("${file.path} is a directory")
      file.readText(Charsets.UTF_8)
    } catch (e: IOException) {
      throw CatalogCacheUnreadable(e)
    }
    val meta = readMeta()
    return CachedCatalog(text, meta?.downloadedAtEpochMs, meta?.etag)
  }

  fun write(text: String, etag: String?, downloadedAtMs: Long) {
    dir.mkdirs()
    atomicWrite(catalogFile, text)
    writeMeta(downloadedAtMs, etag)
  }

  /** Un 304: el texto sigue valiendo; solo cambian la hora y el `ETag`. */
  fun markChecked(downloadedAtMs: Long, etag: String?) {
    if (!catalogFile.exists()) return
    writeMeta(downloadedAtMs, etag)
  }

  fun clear() {
    dir.deleteRecursively()
  }

  private fun writeMeta(downloadedAtMs: Long, etag: String?) {
    dir.mkdirs()
    atomicWrite(metaFile, json.encodeToString(CacheMeta.serializer(), CacheMeta(downloadedAtMs, etag)))
  }

  private fun readMeta(): CacheMeta? = try {
    json.decodeFromString(CacheMeta.serializer(), metaFile.readText(Charsets.UTF_8))
  } catch (_: IOException) {
    null
  } catch (_: SerializationException) {
    null
  } catch (_: IllegalArgumentException) {
    null
  }

  private fun atomicWrite(target: File, text: String) {
    val temp = File(target.parentFile, target.name + ".tmp")
    temp.writeText(text, Charsets.UTF_8)
    if (!temp.renameTo(target)) {
      temp.delete()
      throw IOException("cannot replace ${target.path}")
    }
  }

  private companion object {
    const val CATALOG_NAME = "recipes.json"
    const val META_NAME = "recipes.meta.json"
  }
}
