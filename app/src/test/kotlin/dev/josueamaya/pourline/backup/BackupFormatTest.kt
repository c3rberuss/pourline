package dev.josueamaya.pourline.backup

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class BackupFormatTest {

  @Test
  fun `the file name uses the local date and time`() {
    assertEquals("pourline-backup-2026-11-19-1845.zip", backupFileName(LocalDateTime.of(2026, 11, 19, 18, 45)))
    assertEquals("pourline-backup-2026-01-02-0304.zip", backupFileName(LocalDateTime.of(2026, 1, 2, 3, 4, 59)))
  }

  @Test
  fun `created at is iso 8601 with offset and seconds`() {
    val at = OffsetDateTime.of(2026, 11, 19, 18, 45, 12, 987_000_000, ZoneOffset.ofHours(-6))
    assertEquals("2026-11-19T18:45:12-06:00", formatCreatedAt(at))
    assertEquals("2026-11-19T18:45:00Z", formatCreatedAt(OffsetDateTime.of(2026, 11, 19, 18, 45, 0, 0, ZoneOffset.UTC)))
    assertEquals(at.withNano(0), OffsetDateTime.parse(formatCreatedAt(at)))
  }

  @Test
  fun `the manifest has the fields of the format`() {
    val manifest = ManifestDto(
      app = APP_ID,
      formatVersion = FORMAT_VERSION,
      schemaVersion = 7,
      appVersionName = "1.0",
      appVersionCode = 1,
      createdAt = "2026-11-19T18:45:12-06:00",
      counts = CountsDto(coffees = 12, grinders = 3, grindCalibrations = 4, recipes = 8, brews = 140, photos = 15),
    )
    val json = BackupJson.parseToJsonElement(BackupJson.encodeToString(ManifestDto.serializer(), manifest)).jsonObject
    assertEquals(
      setOf("app", "formatVersion", "schemaVersion", "appVersionName", "appVersionCode", "createdAt", "counts"),
      json.keys,
    )
    assertEquals(
      setOf("coffees", "grinders", "grindCalibrations", "recipes", "brews", "photos"),
      (json["counts"] as JsonObject).keys,
    )
    assertEquals("pourline", (json["app"] as kotlinx.serialization.json.JsonPrimitive).content)
    assertTrue(FORMAT_VERSION >= 1)
  }
}
