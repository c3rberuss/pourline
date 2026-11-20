package dev.josueamaya.pourline.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImportMarkerTest {
  @get:Rule val folder = TemporaryFolder()

  private fun marker(state: ImportMarker.State = ImportMarker.State.APPLYING) = ImportMarker(
    state = state,
    settings = fullSettings().copy(onboardingDone = true).toDto(),
    fingerprint = fullRows().fingerprint(),
  )

  @Test
  fun `the marker round trips`() {
    val dir = File(folder.root, "backup_import")
    ImportMarker.write(dir, marker())
    assertEquals(marker(), ImportMarker.read(dir))
    // Se puede pasar a `DB_DONE` sobre el mismo archivo.
    ImportMarker.write(dir, marker(ImportMarker.State.DB_DONE))
    assertEquals(ImportMarker.State.DB_DONE, ImportMarker.read(dir)!!.state)
    assertEquals(fullSettings().copy(onboardingDone = true), ImportMarker.read(dir)!!.settings.toSnapshot())
    assertEquals(fullRows().fingerprint(), ImportMarker.read(dir)!!.fingerprint)
    // Sin archivo temporal que sobre.
    assertEquals(listOf(ImportMarker.FILE_NAME), dir.list()!!.toList())
    ImportMarker.delete(dir)
    assertNull(ImportMarker.read(dir))
  }

  @Test
  fun `a leftover temporary file is ignored`() {
    val dir = File(folder.root, "backup_import").apply { mkdirs() }
    File(dir, "import.json.tmp").writeText("""{"state":"DB_DONE"""")
    assertNull(ImportMarker.read(dir))
    ImportMarker.write(dir, marker())
    assertEquals(ImportMarker.State.APPLYING, ImportMarker.read(dir)!!.state)
    File(dir, "import.json.tmp").writeText("basura")
    assertEquals(ImportMarker.State.APPLYING, ImportMarker.read(dir)!!.state)
    ImportMarker.delete(dir)
    assertFalse(File(dir, "import.json.tmp").exists())
  }

  @Test
  fun `an unreadable marker reads as null`() {
    val dir = File(folder.root, "backup_import").apply { mkdirs() }
    assertNull(ImportMarker.read(dir))
    File(dir, ImportMarker.FILE_NAME).writeText("no es json")
    assertNull(ImportMarker.read(dir))
    File(dir, ImportMarker.FILE_NAME).writeText("""{"state":"OTHER","settings":{},"fingerprint":{"counts":[],"maxIds":[],"photoNames":[]}}""")
    assertNull(ImportMarker.read(dir))
    File(dir, ImportMarker.FILE_NAME).writeText("")
    assertNull(ImportMarker.read(dir))
    assertTrue(File(dir, ImportMarker.FILE_NAME).exists())
  }
}
