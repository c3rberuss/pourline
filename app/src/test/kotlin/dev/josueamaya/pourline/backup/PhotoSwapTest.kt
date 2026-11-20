package dev.josueamaya.pourline.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PhotoSwapTest {
  @get:Rule val folder = TemporaryFolder()

  private val root by lazy { folder.newFolder() }
  private val photos by lazy { File(root, "coffee_photos") }
  private val staging by lazy { File(root, "backup_import/staging") }
  private val old by lazy { File(root, "backup_import/old_photos") }

  private fun dir(file: File, vararg names: String) {
    file.mkdirs()
    names.forEach { File(file, it).writeText("${file.name}:$it") }
  }

  private fun contents(file: File): Map<String, String> = file.listFiles().orEmpty().associate { it.name to it.readText() }

  private val imported = mapOf("new.jpg" to "staging:new.jpg", "new_thumb.jpg" to "staging:new_thumb.jpg")

  private fun swap() = swapPhotos(photos, staging, old)

  private fun assertDone() {
    assertEquals(imported, contents(photos))
    assertFalse(staging.exists())
    assertFalse(old.exists())
  }

  @Test
  fun `each intermediate state ends with the staging as the photos dir`() {
    // 1. El estado de partida: fotos y staging.
    dir(photos, "a.jpg")
    dir(staging, "new.jpg", "new_thumb.jpg")
    swap()
    assertDone()

    // 2. Ya se renombraron las fotos a `old_photos` y falta el staging.
    folder.delete()
    folder.create()
    dir(old, "a.jpg")
    dir(staging, "new.jpg", "new_thumb.jpg")
    swap()
    assertDone()

    // 3. Algo recreó `coffee_photos` (con otro contenido) estando los tres.
    folder.delete()
    folder.create()
    dir(photos, "recreated.jpg")
    dir(old, "a.jpg")
    dir(staging, "new.jpg", "new_thumb.jpg")
    swap()
    assertDone()

    // 4. El staging ya es `coffee_photos` y solo falta borrar `old_photos`.
    folder.delete()
    folder.create()
    dir(photos, "new.jpg", "new_thumb.jpg")
    photos.listFiles()!!.forEach { it.writeText("staging:${it.name}") }
    dir(old, "a.jpg")
    swap()
    assertDone()
  }

  @Test
  fun `without a previous photos dir the staging just moves`() {
    dir(staging, "new.jpg", "new_thumb.jpg")
    swap()
    assertDone()
    // Una copia sin fotos deja el directorio vacío.
    folder.delete()
    folder.create()
    dir(photos, "a.jpg")
    staging.mkdirs()
    swap()
    assertTrue(photos.isDirectory)
    assertEquals(emptyMap<String, String>(), contents(photos))
  }

  @Test
  fun `without staging the old dir is deleted and the photos stay as they are`() {
    dir(photos, "kept.jpg")
    dir(old, "a.jpg")
    swap()
    assertEquals(mapOf("kept.jpg" to "coffee_photos:kept.jpg"), contents(photos))
    assertFalse(old.exists())
    // Sin nada que hacer, tampoco falla.
    swap()
    assertEquals(mapOf("kept.jpg" to "coffee_photos:kept.jpg"), contents(photos))
  }

  @Test
  fun `running it twice changes nothing`() {
    dir(photos, "a.jpg")
    dir(staging, "new.jpg", "new_thumb.jpg")
    swap()
    swap()
    assertDone()
  }
}
