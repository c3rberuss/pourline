package dev.josueamaya.pourline.backup

import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

/** Las reglas de la copia automática de Android del paquete instalado (SPEC-011 §3.6). */
class BackupRulesTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  /** Las reglas de cada sección: `include` y `exclude` como `(domain, path)`, con el nombre de la sección. */
  private data class Rule(val kind: String, val domain: String, val path: String)

  private fun rulesOf(resourceName: String): Map<String, List<Rule>> {
    val id = context.resources.getIdentifier(resourceName, "xml", context.packageName)
    assertTrue("$resourceName no existe", id != 0)
    val parser = context.resources.getXml(id)
    val sections = linkedMapOf<String, MutableList<Rule>>()
    var section: String? = null
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      if (event == XmlPullParser.START_TAG) {
        when (parser.name) {
          "full-backup-content", "cloud-backup", "device-transfer" -> {
            section = parser.name
            sections[section] = mutableListOf()
          }
          "include", "exclude" ->
            sections.getValue(section!!) += Rule(parser.name, parser.getAttributeValue(null, "domain"), parser.getAttributeValue(null, "path"))
        }
      }
      event = parser.next()
    }
    return sections
  }

  private val databaseAndSettings = setOf(
    Rule("include", "database", "history.db"),
    Rule("include", "database", "history.db-wal"),
    Rule("include", "database", "history.db-shm"),
    Rule("include", "sharedpref", "settings.xml"),
  )

  @Test
  fun cloudAndLegacyRulesIncludeOnlyTheDatabaseAndTheSettings() {
    val extraction = rulesOf("data_extraction_rules")
    val legacy = rulesOf("backup_rules")

    // Sin `exclude` y sin las fotos: son exactamente las cuatro reglas de la base y los ajustes.
    assertEquals(databaseAndSettings, extraction.getValue("cloud-backup").toSet())
    assertEquals(4, extraction.getValue("cloud-backup").size)
    assertEquals(databaseAndSettings, legacy.getValue("full-backup-content").toSet())
    assertEquals(4, legacy.getValue("full-backup-content").size)
  }

  @Test
  fun deviceTransferAlsoIncludesThePhotos() {
    val transfer = rulesOf("data_extraction_rules").getValue("device-transfer")

    assertEquals(databaseAndSettings + Rule("include", "file", "coffee_photos/"), transfer.toSet())
    assertEquals(5, transfer.size)
    // La carpeta de las reglas es la de las fotos de la app.
    assertEquals("coffee_photos", dev.josueamaya.pourline.coffee.photo.COFFEE_PHOTOS_DIR)
  }

  @Test
  fun theAppAllowsBackup() {
    assertTrue(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
  }
}
