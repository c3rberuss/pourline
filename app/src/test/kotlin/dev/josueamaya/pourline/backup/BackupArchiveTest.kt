package dev.josueamaya.pourline.backup

import dev.josueamaya.pourline.coffee.photo.thumbNameOf
import dev.josueamaya.pourline.settings.SettingsSnapshot
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.OffsetDateTime
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
  @get:Rule val folder = TemporaryFolder()

  private val photoBytes = mapOf(
    PHOTO_A to ByteArray(2_000) { (it % 251).toByte() },
    thumbNameOf(PHOTO_A) to ByteArray(300) { (it % 13).toByte() },
    PHOTO_B to ByteArray(1_500) { (it % 97).toByte() },
    PHOTO_C to ByteArray(700) { (it % 31).toByte() },
  )

  private fun manifest(rows: BackupRows = fullRows(), formatVersion: Int = FORMAT_VERSION) = ManifestDto(
    app = APP_ID,
    formatVersion = formatVersion,
    schemaVersion = 7,
    appVersionName = "1.0",
    appVersionCode = 1,
    createdAt = formatCreatedAt(OffsetDateTime.parse("2026-11-19T18:45:12-06:00")),
    counts = rows.counts(3).let { CountsDto(it.coffees, it.grinders, it.grindCalibrations, it.recipes, it.brews, it.photos) },
  )

  private fun photoFiles(names: Collection<String> = photoBytes.keys): List<PhotoEntry> {
    val dir = folder.newFolder()
    return names.map { name -> PhotoEntry(name, File(dir, name).also { it.writeBytes(photoBytes.getValue(name)) }) }
  }

  private fun write(
    rows: BackupRows = fullRows(),
    settings: SettingsSnapshot = fullSettings(),
    photos: List<PhotoEntry> = photoFiles(),
    limits: BackupLimits = BackupLimits(),
    progress: (Long) -> Unit = {},
  ): ByteArray {
    val out = ByteArrayOutputStream()
    BackupWriter(limits).write(out, manifest(rows), rows.toDto(), settings.toDto(), photos, progress)
    return out.toByteArray()
  }

  private fun read(bytes: ByteArray, limits: BackupLimits = BackupLimits(), staging: File = folder.newFolder()): ReadArchive =
    BackupReader(limits).read(ByteArrayInputStream(bytes), staging) {}

  private fun failureOf(bytes: ByteArray, limits: BackupLimits = BackupLimits(), staging: File = folder.newFolder()): BackupError =
    assertThrows(BackupFailure::class.java) { read(bytes, limits, staging) }.reason

  private fun json(value: Any): ByteArray = when (value) {
    is ManifestDto -> BackupJson.encodeToString(ManifestDto.serializer(), value)
    is DataDto -> BackupJson.encodeToString(DataDto.serializer(), value)
    is SettingsDto -> BackupJson.encodeToString(SettingsDto.serializer(), value)
    else -> error("tipo no soportado")
  }.toByteArray()

  /**
   * Un ZIP con las entradas dadas, también con nombres repetidos o inseguros que `ZipOutputStream` no deja escribir: un
   * nombre repetido se escribe con otra última letra y se corrige en los bytes (misma longitud, mismo CRC).
   */
  private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    val seen = mutableSetOf<String>()
    val patches = mutableListOf<Pair<String, String>>()
    ZipOutputStream(out).use { zip ->
      entries.forEach { (name, bytes) ->
        val written = if (seen.add(name)) name else (name.dropLast(1) + '#').also { patches += it to name }
        zip.putNextEntry(ZipEntry(written))
        zip.write(bytes)
        zip.closeEntry()
      }
    }
    var result = out.toByteArray()
    patches.forEach { (from, to) -> result = replaceBytes(result, from.toByteArray(), to.toByteArray()) }
    return result
  }

  /** Sustituye cada aparición de [from] por [to], que mide lo mismo. */
  private fun replaceBytes(source: ByteArray, from: ByteArray, to: ByteArray): ByteArray {
    check(from.size == to.size)
    val result = source.copyOf()
    var i = 0
    while (i <= result.size - from.size) {
      if (from.indices.all { result[i + it] == from[it] }) {
        to.copyInto(result, i)
        i += from.size
      } else {
        i++
      }
    }
    return result
  }

  private fun standard(
    manifest: ByteArray = json(manifest()),
    data: ByteArray = json(fullRows().toDto()),
    settings: ByteArray = json(fullSettings().toDto()),
  ) = arrayOf(MANIFEST_ENTRY to manifest, DATA_ENTRY to data, SETTINGS_ENTRY to settings)

  @Test
  fun `a written backup has the manifest first and only the entries of the format`() {
    val file = folder.newFile("backup.zip").also { it.writeBytes(write()) }
    ZipFile(file).use { zip ->
      val entries = zip.entries().toList()
      assertEquals(
        listOf("manifest.json", "data.json", "settings.json") + photoBytes.keys.sorted().map { "photos/$it" },
        entries.map { it.name },
      )
      assertTrue("sin entradas de directorio", entries.none { it.isDirectory })
      entries.filter { it.name.endsWith(".json") }.forEach { assertEquals(it.name, ZipEntry.DEFLATED, it.method) }
      entries.filter { it.name.startsWith("photos/") }.forEach { assertEquals(it.name, ZipEntry.STORED, it.method) }
      val photo = zip.getEntry("photos/$PHOTO_A")
      assertEquals(2_000L, photo.size)
      assertEquals(CRC32().also { it.update(photoBytes.getValue(PHOTO_A)) }.value, photo.crc)
    }
  }

  @Test
  fun `a written backup reads back with the same data, settings and photos`() {
    val progress = mutableListOf<Long>()
    val bytes = write(progress = { progress += it })
    assertTrue("el progreso avanza", progress.isNotEmpty() && progress == progress.sorted())
    val staging = folder.newFolder()
    val result = read(bytes, staging = staging)
    assertEquals(manifest(), result.manifest)
    assertRowsEqual(fullRows(), result.data.toRows())
    assertEquals(fullSettings(), result.settings.toSnapshot())
    assertEquals(photoBytes.keys, result.staged)
    photoBytes.forEach { (name, content) -> assertArrayEquals(name, content, File(staging, name).readBytes()) }
    assertEquals(photoBytes.keys, staging.list()!!.toSet())
  }

  @Test
  fun `a file that is not a zip is not a backup`() {
    assertEquals(BackupError.NOT_A_BACKUP, failureOf(ByteArray(2_048) { (it * 31 + 7).toByte() }))
    assertEquals(BackupError.NOT_A_BACKUP, failureOf(ByteArray(0)))
    val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(500)
    assertEquals(BackupError.NOT_A_BACKUP, failureOf(png))
  }

  @Test
  fun `a zip without manifest or with another first entry is not a backup`() {
    assertEquals(BackupError.NOT_A_BACKUP, failureOf(zipOf(DATA_ENTRY to json(fullRows().toDto()), SETTINGS_ENTRY to json(fullSettings().toDto()))))
    assertEquals(
      BackupError.NOT_A_BACKUP,
      failureOf(zipOf(DATA_ENTRY to json(fullRows().toDto()), MANIFEST_ENTRY to json(manifest()), SETTINGS_ENTRY to json(fullSettings().toDto()))),
    )
    assertEquals(BackupError.NOT_A_BACKUP, failureOf(zipOf()))
    assertEquals(BackupError.NOT_A_BACKUP, failureOf(zipOf("readme.txt" to "hola".toByteArray())))
  }

  @Test
  fun `a manifest of another app is not a backup`() {
    val other = String(json(manifest())).replace("\"app\":\"pourline\"", "\"app\":\"otra-app\"").toByteArray()
    assertEquals(BackupError.NOT_A_BACKUP, failureOf(zipOf(*standard(manifest = other))))
    val withoutApp = String(json(manifest())).replace("\"app\":\"pourline\",", "").toByteArray()
    assertEquals(BackupError.NOT_A_BACKUP, failureOf(zipOf(*standard(manifest = withoutApp))))
    assertEquals(BackupError.NOT_A_BACKUP, failureOf(zipOf(*standard(manifest = "esto no es json".toByteArray()))))
    assertEquals(BackupError.NOT_A_BACKUP, failureOf(zipOf(*standard(manifest = "[1,2,3]".toByteArray()))))
  }

  @Test
  fun `a newer format is rejected and an older or equal one is read`() {
    val future = String(json(manifest(formatVersion = FORMAT_VERSION + 1))).dropLast(1) + ",\"campoNuevo\":true}"
    assertEquals(BackupError.NEWER_FORMAT, failureOf(zipOf(*standard(manifest = future.toByteArray()))))
    assertEquals(BackupError.NEWER_FORMAT, failureOf(zipOf(*standard(manifest = """{"app":"pourline","formatVersion":99}""".toByteArray()))))
    assertEquals(1, read(zipOf(*standard(manifest = json(manifest(formatVersion = 1))))).manifest.formatVersion)
    assertEquals(BackupError.CORRUPT, failureOf(zipOf(*standard(manifest = json(manifest(formatVersion = 0))))))
    // Un manifiesto de esta versión con campos que faltan o sobran es CORRUPT, no NEWER_FORMAT.
    assertEquals(BackupError.CORRUPT, failureOf(zipOf(*standard(manifest = """{"app":"pourline","formatVersion":1}""".toByteArray()))))
  }

  @Test
  fun `a format 1 backup without the new fields is read with nulls`() {
    val newKeys = setOf("author", "sourceUrl", "catalogKey", "referenceKey")
    fun dropNewKeys(element: JsonElement): JsonElement = when (element) {
      is JsonObject -> JsonObject(element.filterKeys { it !in newKeys }.mapValues { dropNewKeys(it.value) })
      is JsonArray -> JsonArray(element.map(::dropNewKeys))
      else -> element
    }
    val data = BackupJson.parseToJsonElement(String(json(fullRows().toDto()))).let(::dropNewKeys).toString().toByteArray()
    val settings = String(json(fullSettings().toDto())).let { text ->
      BackupJson.parseToJsonElement(text).jsonObject.filterKeys { it != "target_ref_key" }.let(::JsonObject).toString()
    }.toByteArray()
    val archive = read(zipOf(*standard(manifest = json(manifest(formatVersion = 1)), data = data, settings = settings)))

    val rows = archive.data.toRows()
    assertEquals(listOf<String?>(null, null), rows.recipes.map { it.catalogKey })
    assertEquals(listOf<String?>(null, null), rows.recipes.map { it.author })
    assertEquals(listOf<String?>(null, null, null), rows.brews.map { it.referenceKey })
    assertNull(archive.settings.toSnapshot().targetRefKey)
  }

  @Test
  fun `corrupt json is corrupt`() {
    val data = json(fullRows().toDto())
    assertEquals(BackupError.CORRUPT, failureOf(zipOf(*standard(data = data.copyOf(data.size / 2)))))
    val settings = String(json(fullSettings().toDto())).replace("\"auto_finish_seconds\":90", "\"auto_finish_seconds\":\"noventa\"").toByteArray()
    assertEquals(BackupError.CORRUPT, failureOf(zipOf(*standard(settings = settings))))
    assertEquals(BackupError.CORRUPT, failureOf(zipOf(MANIFEST_ENTRY to json(manifest()), SETTINGS_ENTRY to json(fullSettings().toDto()))))
    assertEquals(BackupError.CORRUPT, failureOf(zipOf(MANIFEST_ENTRY to json(manifest()), DATA_ENTRY to data)))
    assertEquals(BackupError.CORRUPT, failureOf(zipOf(*standard(data = "no es json".toByteArray()))))
  }

  @Test
  fun `an unexpected entry is corrupt`() {
    val extras = listOf(
      "readme.txt" to "hola".toByteArray(),
      "photos/sub/a.jpg" to ByteArray(10),
      "photos/a.png" to ByteArray(10),
      "photos/a b.jpg" to ByteArray(10),
      DATA_ENTRY to json(fullRows().toDto()),
      MANIFEST_ENTRY to json(manifest()),
    )
    extras.forEach { extra ->
      assertEquals(extra.first, BackupError.CORRUPT, failureOf(zipOf(*standard(), extra)))
    }
    // Una foto repetida.
    assertEquals(BackupError.CORRUPT, failureOf(zipOf(*standard(), "photos/$PHOTO_A" to ByteArray(10), "photos/$PHOTO_A" to ByteArray(10))))
    // Un directorio distinto de photos/.
    assertEquals(BackupError.CORRUPT, failureOf(zipOf(*standard(), "otro/" to ByteArray(0))))
    // La entrada de directorio photos/ se ignora.
    assertEquals(emptySet<String>(), read(zipOf(*standard(), "photos/" to ByteArray(0))).staged)
  }

  @Test
  fun `an unsafe path is corrupt and nothing is written outside staging`() {
    val root = folder.newFolder()
    val staging = File(root, "staging")
    listOf("photos/../evil.jpg", "../evil.jpg", "/data/evil.jpg", "photos\\..\\evil.jpg", "photos//evil.jpg").forEach { path ->
      assertEquals(path, BackupError.CORRUPT, failureOf(zipOf(*standard(), path to "malo".toByteArray()), staging = staging))
    }
    assertFalse(File(root, "evil.jpg").exists())
    assertFalse(File(staging, "evil.jpg").exists())
    assertFalse(File(root.parentFile, "evil.jpg").exists())
    assertTrue(staging.listFiles().isNullOrEmpty())
  }

  @Test
  fun `a broken reference is rejected`() {
    val rows = fullRows()
    val broken = rows.copy(brews = rows.brews.map { if (it.id == 1L) it.copy(coffeeId = 404) else it })
    val read = read(write(rows = broken))
    assertEquals(BackupError.BROKEN_REFERENCES, validate(read.data.toRows()))
    assertNull(validate(fullRows()))
  }

  @Test
  fun `entries over the limits are too large`() {
    val bytes = write()
    // data.json: el límite de la entrada.
    assertEquals(BackupError.TOO_LARGE, failureOf(bytes, BackupLimits(maxDataBytes = 100)))
    // manifest.json y settings.json.
    assertEquals(BackupError.TOO_LARGE, failureOf(bytes, BackupLimits(maxSmallEntryBytes = 20)))
    // Una foto.
    assertEquals(BackupError.TOO_LARGE, failureOf(bytes, BackupLimits(maxPhotoBytes = 1_000)))
    // El total descomprimido.
    assertEquals(BackupError.TOO_LARGE, failureOf(bytes, BackupLimits(maxTotalBytes = 5_000)))
    // El número de entradas (3 + 4 fotos).
    assertEquals(BackupError.TOO_LARGE, failureOf(bytes, BackupLimits(maxEntries = 6)))
    assertEquals(7, read(bytes, BackupLimits(maxEntries = 7)).manifest.let { 7 })
    // Una bomba: pocos bytes comprimidos, muchos descomprimidos; no se fía del tamaño declarado.
    val bomb = zipOf(*standard(data = ByteArray(2_000_000) { ' '.code.toByte() }))
    assertTrue("la bomba pesa poco comprimida", bomb.size < 10_000)
    assertEquals(BackupError.TOO_LARGE, failureOf(bomb, BackupLimits(maxDataBytes = 100_000)))
    // Dentro de los límites, se lee.
    assertEquals(photoBytes.keys, read(bytes, BackupLimits()).staged)
  }

  @Test
  fun `the writer refuses to go over the limits`() {
    fun reason(limits: BackupLimits, photos: List<PhotoEntry> = photoFiles()): BackupError {
      val out = ByteArrayOutputStream()
      return assertThrows(BackupFailure::class.java) {
        BackupWriter(limits).write(out, manifest(), fullRows().toDto(), fullSettings().toDto(), photos) {}
      }.reason
    }
    assertEquals(BackupError.TOO_LARGE, reason(BackupLimits(maxDataBytes = 100)))
    assertEquals(BackupError.TOO_LARGE, reason(BackupLimits(maxSmallEntryBytes = 20)))
    assertEquals(BackupError.TOO_LARGE, reason(BackupLimits(maxPhotoBytes = 1_000)))
    assertEquals(BackupError.TOO_LARGE, reason(BackupLimits(maxTotalBytes = 5_000)))
    assertEquals(BackupError.TOO_LARGE, reason(BackupLimits(maxEntries = 6)))
  }

  @Test
  fun `a referenced photo missing from the backup is tolerated and counted`() {
    val rows = fullRows()
    // Falta PHOTO_B (photo2 del primer café); PHOTO_A y PHOTO_C están.
    val staged = setOf(PHOTO_A, thumbNameOf(PHOTO_A), PHOTO_C, thumbNameOf(PHOTO_C))
    val plan = planPhotos(rows, staged)
    assertEquals(1, plan.missing)
    assertEquals(PHOTO_A, plan.rows.coffees[0].photo1)
    assertNull(plan.rows.coffees[0].photo2)
    assertEquals(PHOTO_C, plan.rows.coffees[1].photo2)
    assertEquals(setOf(PHOTO_A, PHOTO_C), plan.present)
    // La misma foto perdida en dos cafés cuenta una vez, y la otra foto del café no se mueve.
    val twice = rows.copy(coffees = rows.coffees.map { it.copy(photo1 = PHOTO_B) })
    val twicePlan = planPhotos(twice, setOf(PHOTO_C, thumbNameOf(PHOTO_C)))
    assertEquals(1, twicePlan.missing)
    assertNull(twicePlan.rows.coffees[0].photo1)
    assertEquals(PHOTO_C, twicePlan.rows.coffees[1].photo2)
  }

  @Test
  fun `a missing thumbnail is regenerated from the main photo`() {
    val staging = folder.newFolder()
    File(staging, PHOTO_A).writeBytes(photoBytes.getValue(PHOTO_A))
    File(staging, PHOTO_C).writeBytes(photoBytes.getValue(PHOTO_C))
    File(staging, thumbNameOf(PHOTO_C)).writeBytes(byteArrayOf(1))
    val plan = planPhotos(fullRows(), staging.list()!!.toSet())
    assertEquals(listOf(PHOTO_A), plan.regenerate)
    val calls = mutableListOf<Pair<String, String>>()
    applyPhotoPlan(plan, staging) { photo, thumb ->
      calls += photo.name to thumb.name
      thumb.writeBytes(byteArrayOf(9, 9))
      true
    }
    assertEquals(listOf(PHOTO_A to thumbNameOf(PHOTO_A)), calls)
    assertArrayEquals(byteArrayOf(9, 9), File(staging, thumbNameOf(PHOTO_A)).readBytes())
    assertArrayEquals(byteArrayOf(1), File(staging, thumbNameOf(PHOTO_C)).readBytes())
    // Si la regeneración falla, el café queda sin miniatura y la foto no cuenta como perdida.
    File(staging, thumbNameOf(PHOTO_A)).delete()
    applyPhotoPlan(plan, staging) { _, thumb ->
      thumb.writeBytes(byteArrayOf(5))
      false
    }
    assertFalse(File(staging, thumbNameOf(PHOTO_A)).exists())
    // Solo cuenta la foto que no está (PHOTO_B); la miniatura que no se pudo regenerar no suma.
    assertEquals(1, plan.missing)
  }

  @Test
  fun `photos no coffee references are dropped and not counted`() {
    val staging = folder.newFolder()
    val stray = "99999999-9999-4999-8999-999999999999.jpg"
    listOf(PHOTO_A, thumbNameOf(PHOTO_A), PHOTO_B, thumbNameOf(PHOTO_B), PHOTO_C, thumbNameOf(PHOTO_C), stray, thumbNameOf(stray)).forEach {
      File(staging, it).writeBytes(byteArrayOf(1, 2, 3))
    }
    val plan = planPhotos(fullRows(), staging.list()!!.toSet())
    assertEquals(0, plan.missing)
    assertEquals(setOf(PHOTO_A, PHOTO_B, PHOTO_C), plan.present)
    assertEquals(listOf(stray, thumbNameOf(stray)).sorted(), plan.unreferenced)
    assertEquals(3, plan.rows.counts(plan.present.size).photos)
    applyPhotoPlan(plan, staging) { _, _ -> true }
    assertFalse(File(staging, stray).exists())
    assertFalse(File(staging, thumbNameOf(stray)).exists())
    assertEquals(6, staging.list()!!.size)
  }
}
