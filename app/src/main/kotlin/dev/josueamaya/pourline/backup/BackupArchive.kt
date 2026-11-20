package dev.josueamaya.pourline.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.format.DateTimeParseException
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Los límites de tamaño de una copia (SPEC-011 §3.4). Se cuentan los bytes **descomprimidos**, sin fiarse de los
 * tamaños que declara el ZIP, y valen igual al exportar y al importar.
 */
internal data class BackupLimits(
  val maxDataBytes: Long = 64L * 1024 * 1024,
  val maxSmallEntryBytes: Long = 256L * 1024,
  val maxPhotoBytes: Long = 16L * 1024 * 1024,
  val maxTotalBytes: Long = 1024L * 1024 * 1024,
  val maxEntries: Int = 10_000,
)

/** Una foto (principal o miniatura) que va en `photos/`. */
internal data class PhotoEntry(val name: String, val file: File)

/** El contenido de una copia leída: los tres documentos y los nombres de los archivos de `photos/` ya en el staging. */
internal data class ReadArchive(
  val manifest: ManifestDto,
  val data: DataDto,
  val settings: SettingsDto,
  val staged: Set<String>,
)

/** Cuenta los bytes de todas las entradas y avisa de cada avance. */
private class ByteBudget(private val max: Long, private val onProgress: (Long) -> Unit) {
  var total = 0L
    private set

  fun add(n: Long) {
    total += n
    onProgress(total)
  }

  val exceeded: Boolean get() = total > max
}

/** Escribe en [target] contando los bytes de una entrada; al pasar el límite de la entrada o el total, falla. No cierra [target]. */
private class BoundedOutput(
  private val target: OutputStream,
  private val limit: Long,
  private val budget: ByteBudget,
) : OutputStream() {
  var exceeded = false
    private set
  private var written = 0L

  override fun write(b: Int) {
    account(1)
    target.write(b)
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    account(len.toLong())
    target.write(b, off, len)
  }

  override fun flush() = target.flush()

  override fun close() = Unit

  private fun account(n: Long) {
    written += n
    budget.add(n)
    if (written > limit || budget.exceeded) {
      exceeded = true
      throw IOException("Límite de tamaño superado")
    }
  }
}

/** Lee de [source] contando los bytes de una entrada; al pasar el límite de la entrada o el total, falla. No cierra [source]. */
private class BoundedInput(
  private val source: InputStream,
  private val limit: Long,
  private val budget: ByteBudget,
) : InputStream() {
  var exceeded = false
    private set
  private var read = 0L

  override fun read(): Int {
    val b = source.read()
    if (b >= 0) account(1)
    return b
  }

  override fun read(b: ByteArray, off: Int, len: Int): Int {
    val n = source.read(b, off, len)
    if (n > 0) account(n.toLong())
    return n
  }

  override fun close() = Unit

  private fun account(n: Long) {
    read += n
    budget.add(n)
    if (read > limit || budget.exceeded) {
      exceeded = true
      throw IOException("Límite de tamaño superado")
    }
  }
}

/** Cuenta los bytes (comprimidos) que salen de [source], para el progreso de la lectura. */
private class CountingInput(private val source: InputStream, private val onRead: (Long) -> Unit) : InputStream() {
  private var total = 0L

  override fun read(): Int {
    val b = source.read()
    if (b >= 0) advance(1)
    return b
  }

  override fun read(b: ByteArray, off: Int, len: Int): Int {
    val n = source.read(b, off, len)
    if (n > 0) advance(n.toLong())
    return n
  }

  override fun available(): Int = source.available()

  override fun close() = source.close()

  private fun advance(n: Long) {
    total += n
    onRead(total)
  }
}

/**
 * Escribe el ZIP del §3.1: `manifest.json`, `data.json`, `settings.json` y las fotos ordenadas por nombre, sin entradas
 * de directorio. Los JSON van `DEFLATED`; las fotos, `STORED` (un JPEG no se comprime), con el CRC y el tamaño
 * calculados antes con una lectura previa. Ante un límite superado lanza `BackupFailure(TOO_LARGE)`.
 */
@OptIn(ExperimentalSerializationApi::class)
internal class BackupWriter(private val limits: BackupLimits = BackupLimits()) {

  /** [out] no se cierra. [onWritten] recibe los bytes descomprimidos escritos hasta el momento. */
  fun write(
    out: OutputStream,
    manifest: ManifestDto,
    data: DataDto,
    settings: SettingsDto,
    photos: List<PhotoEntry>,
    onWritten: (Long) -> Unit,
  ) {
    if (3 + photos.size > limits.maxEntries) throw BackupFailure(BackupError.TOO_LARGE)
    val budget = ByteBudget(limits.maxTotalBytes, onWritten)
    val zip = ZipOutputStream(out)
    writeJson(zip, MANIFEST_ENTRY, limits.maxSmallEntryBytes, budget) { BackupJson.encodeToStream(ManifestDto.serializer(), manifest, it) }
    writeJson(zip, DATA_ENTRY, limits.maxDataBytes, budget) { BackupJson.encodeToStream(DataDto.serializer(), data, it) }
    writeJson(zip, SETTINGS_ENTRY, limits.maxSmallEntryBytes, budget) { BackupJson.encodeToStream(SettingsDto.serializer(), settings, it) }
    photos.sortedBy { it.name }.forEach { writePhoto(zip, it, budget) }
    zip.finish()
    zip.flush()
  }

  private fun writeJson(zip: ZipOutputStream, name: String, limit: Long, budget: ByteBudget, encode: (OutputStream) -> Unit) {
    zip.putNextEntry(ZipEntry(name).apply { method = ZipEntry.DEFLATED })
    val bounded = BoundedOutput(zip, limit, budget)
    try {
      encode(bounded)
    } catch (e: Exception) {
      if (bounded.exceeded) throw BackupFailure(BackupError.TOO_LARGE, e)
      throw e
    }
    zip.closeEntry()
  }

  private fun writePhoto(zip: ZipOutputStream, photo: PhotoEntry, budget: ByteBudget) {
    val size = photo.file.length()
    if (size > limits.maxPhotoBytes) throw BackupFailure(BackupError.TOO_LARGE)
    val crc = CRC32()
    CheckedInputStream(photo.file.inputStream().buffered(), crc).use { input ->
      val buffer = ByteArray(BUFFER_SIZE)
      while (input.read(buffer) >= 0) Unit
    }
    zip.putNextEntry(
      ZipEntry(PHOTOS_PREFIX + photo.name).apply {
        method = ZipEntry.STORED
        this.size = size
        compressedSize = size
        this.crc = crc.value
      },
    )
    val bounded = BoundedOutput(zip, limits.maxPhotoBytes, budget)
    try {
      photo.file.inputStream().use { it.copyTo(bounded, BUFFER_SIZE) }
    } catch (e: IOException) {
      if (bounded.exceeded) throw BackupFailure(BackupError.TOO_LARGE, e)
      throw e
    }
    zip.closeEntry()
  }

  private companion object {
    const val BUFFER_SIZE = 64 * 1024
  }
}

/**
 * Lee el ZIP en una sola pasada y sin modificar nada fuera de [staging] (SPEC-011 §3.4): valida el manifiesto, los
 * nombres de las entradas y los límites, y deja las fotos en el staging. No valida las filas (eso es [validate]).
 */
@OptIn(ExperimentalSerializationApi::class)
internal class BackupReader(private val limits: BackupLimits = BackupLimits()) {

  /**
   * [input] no se cierra. [onRead] recibe los bytes comprimidos leídos. Lanza `BackupFailure` con el [BackupError]
   * que corresponda; ante cualquier fallo el staging puede quedar a medias y lo vacía quien llama.
   */
  fun read(input: InputStream, staging: File, onRead: (Long) -> Unit): ReadArchive {
    val zip = ZipInputStream(CountingInput(input, onRead))
    var manifest: ManifestDto? = null
    var data: DataDto? = null
    var settings: SettingsDto? = null
    val staged = linkedSetOf<String>()
    val budget = ByteBudget(limits.maxTotalBytes) {}
    var entries = 0
    try {
      staging.mkdirs()
      val canonicalStaging = staging.canonicalFile
      while (true) {
        val entry = zip.nextEntry ?: break
        if (++entries > limits.maxEntries) throw BackupFailure(BackupError.TOO_LARGE)
        val name = entry.name
        if (manifest == null) {
          // La primera entrada tiene que ser el manifiesto.
          if (name != MANIFEST_ENTRY || entry.isDirectory) throw BackupFailure(BackupError.NOT_A_BACKUP)
          manifest = readManifest(zip, budget)
          continue
        }
        when {
          entry.isDirectory -> if (name != PHOTOS_PREFIX) throw BackupFailure(BackupError.CORRUPT)
          name == MANIFEST_ENTRY -> throw BackupFailure(BackupError.CORRUPT)
          name == DATA_ENTRY -> {
            if (data != null) throw BackupFailure(BackupError.CORRUPT)
            data = decode(zip, limits.maxDataBytes, budget, DataDto.serializer())
          }
          name == SETTINGS_ENTRY -> {
            if (settings != null) throw BackupFailure(BackupError.CORRUPT)
            settings = decode(zip, limits.maxSmallEntryBytes, budget, SettingsDto.serializer())
          }
          name.startsWith(PHOTOS_PREFIX) -> {
            val photoName = name.removePrefix(PHOTOS_PREFIX)
            if (!PHOTO_NAME.matches(photoName) || !staged.add(photoName)) throw BackupFailure(BackupError.CORRUPT)
            stagePhoto(zip, photoName, canonicalStaging, budget)
          }
          else -> throw BackupFailure(BackupError.CORRUPT)
        }
      }
    } catch (e: BackupFailure) {
      throw e
    } catch (e: ZipException) {
      // Antes de validar el manifiesto, algo que no es una copia; después, una copia dañada (CRC, ruta insegura de Android 14).
      throw BackupFailure(if (manifest == null) BackupError.NOT_A_BACKUP else BackupError.CORRUPT, e)
    } catch (e: EOFException) {
      throw BackupFailure(if (manifest == null) BackupError.NOT_A_BACKUP else BackupError.CORRUPT, e)
    } catch (e: IOException) {
      throw BackupFailure(BackupError.IO, e)
    } catch (e: SecurityException) {
      throw BackupFailure(BackupError.IO, e)
    } catch (e: SerializationException) {
      throw BackupFailure(BackupError.CORRUPT, e)
    } catch (e: IllegalArgumentException) {
      throw BackupFailure(BackupError.CORRUPT, e)
    } catch (e: DateTimeParseException) {
      throw BackupFailure(BackupError.CORRUPT, e)
    } catch (e: OutOfMemoryError) {
      throw BackupFailure(BackupError.TOO_LARGE)
    }
    // Sin entradas no es un ZIP, o no es una copia.
    val readManifest = manifest ?: throw BackupFailure(BackupError.NOT_A_BACKUP)
    return ReadArchive(
      manifest = readManifest,
      data = data ?: throw BackupFailure(BackupError.CORRUPT),
      settings = settings ?: throw BackupFailure(BackupError.CORRUPT),
      staged = staged,
    )
  }

  /** El manifiesto se lee primero como JSON suelto: así `NEWER_FORMAT` se distingue aunque el nuevo traiga campos que esta versión no conoce. */
  private fun readManifest(zip: ZipInputStream, budget: ByteBudget): ManifestDto {
    val bounded = BoundedInput(zip, limits.maxSmallEntryBytes, budget)
    val bytes = try {
      bounded.readBytes()
    } catch (e: IOException) {
      if (bounded.exceeded) throw BackupFailure(BackupError.TOO_LARGE, e)
      throw e
    }
    val root = try {
      BackupJson.parseToJsonElement(String(bytes, Charsets.UTF_8)) as? JsonObject
    } catch (e: SerializationException) {
      null
    } catch (e: IllegalArgumentException) {
      null
    } ?: throw BackupFailure(BackupError.NOT_A_BACKUP)
    if ((root["app"] as? JsonPrimitive)?.takeIf { it.isString }?.content != APP_ID) throw BackupFailure(BackupError.NOT_A_BACKUP)
    val version = (root["formatVersion"] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()
    if (version != null && version > FORMAT_VERSION) throw BackupFailure(BackupError.NEWER_FORMAT)
    val manifest = try {
      BackupJson.decodeFromString(ManifestDto.serializer(), String(bytes, Charsets.UTF_8))
    } catch (e: SerializationException) {
      throw BackupFailure(BackupError.CORRUPT, e)
    }
    if (manifest.formatVersion < 1) throw BackupFailure(BackupError.CORRUPT)
    return manifest
  }

  private fun <T> decode(zip: ZipInputStream, limit: Long, budget: ByteBudget, serializer: kotlinx.serialization.KSerializer<T>): T {
    val bounded = BoundedInput(zip, limit, budget)
    try {
      return BackupJson.decodeFromStream(serializer, bounded)
    } catch (e: Exception) {
      if (bounded.exceeded) throw BackupFailure(BackupError.TOO_LARGE, e)
      throw e
    }
  }

  private fun stagePhoto(zip: ZipInputStream, name: String, canonicalStaging: File, budget: ByteBudget) {
    val target = File(canonicalStaging, name).canonicalFile
    // Segunda barrera contra "zip slip": el patrón de nombre ya no admite separadores.
    if (target.parentFile != canonicalStaging) throw BackupFailure(BackupError.CORRUPT)
    val bounded = BoundedInput(zip, limits.maxPhotoBytes, budget)
    try {
      target.outputStream().use { bounded.copyTo(it, BUFFER_SIZE) }
    } catch (e: IOException) {
      if (bounded.exceeded) throw BackupFailure(BackupError.TOO_LARGE, e)
      throw e
    }
  }

  private companion object {
    const val BUFFER_SIZE = 64 * 1024
  }
}
