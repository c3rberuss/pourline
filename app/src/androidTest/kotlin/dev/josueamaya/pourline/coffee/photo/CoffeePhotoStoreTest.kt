package dev.josueamaya.pourline.coffee.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

class CoffeePhotoStoreTest {
  private lateinit var root: File
  private lateinit var store: CoffeePhotoStore

  @Before
  fun setUp() {
    root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "photo-store-test").apply {
      deleteRecursively()
      mkdirs()
    }
    store = CoffeePhotoStore(File(root, "photos"), File(root, "cache"))
  }

  @After
  fun tearDown() {
    root.deleteRecursively()
  }

  private fun jpeg(width: Int, height: Int, orientation: Int? = null): File {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawColor(Color.rgb(200, 120, 40))
    val file = File(root, "source-$width-$height.jpg")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    if (orientation != null) {
      ExifInterface(file.path).apply {
        setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        saveAttributes()
      }
    }
    return file
  }

  private fun size(file: File): Pair<Int, Int> {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, o)
    return o.outWidth to o.outHeight
  }

  @Test
  fun aRotated12MpPhotoBecomesUprightAndSmallWithASquareThumbnail() = runTest {
    val source = jpeg(4000, 3000, orientation = ExifInterface.ORIENTATION_ROTATE_90)
    val name = store.import { FileInputStream(source) }

    assertTrue(name.endsWith(".jpg"))
    assertEquals(1200 to 1600, size(store.file(name)))
    assertEquals(256 to 256, size(store.thumbFile(name)))
  }

  @Test
  fun aSmallPhotoKeepsItsSize() = runTest {
    val name = store.import { FileInputStream(jpeg(800, 600)) }
    assertEquals(800 to 600, size(store.file(name)))
  }

  @Test
  fun deleteRemovesThePhotoAndItsThumbnail() = runTest {
    val name = store.import { FileInputStream(jpeg(800, 600)) }
    assertTrue(store.file(name).exists() && store.thumbFile(name).exists())
    store.delete(listOf(name))
    assertFalse(store.file(name).exists())
    assertFalse(store.thumbFile(name).exists())
  }

  @Test
  fun unreadableBytesThrow() = runTest {
    assertThrows(PhotoImportException::class.java) {
      kotlinx.coroutines.runBlocking { store.import { ByteArrayInputStream(ByteArray(64) { it.toByte() }) } }
    }
  }

  @Test
  fun sweepKeepsReferencedAndRecentFiles() = runTest {
    val referenced = store.import { FileInputStream(jpeg(400, 300)) }
    val orphan = store.import { FileInputStream(jpeg(400, 300)) }
    val recent = store.import { FileInputStream(jpeg(400, 300)) }
    val old = 10L * 24 * 3600 * 1000
    listOf(referenced, orphan).forEach {
      store.file(it).setLastModified(System.currentTimeMillis() - old)
      store.thumbFile(it).setLastModified(System.currentTimeMillis() - old)
    }

    store.sweepOrphans(setOf(referenced), olderThanMs = 24L * 3600 * 1000)

    assertTrue(store.file(referenced).exists() && store.thumbFile(referenced).exists())
    assertFalse(store.file(orphan).exists() || store.thumbFile(orphan).exists())
    assertTrue(store.file(recent).exists())
  }

  @Test
  fun aCameraTargetIsInTheCacheAndClearsTheOldOnes() {
    val first = store.newCameraTarget()
    first.writeText("x")
    val second = store.newCameraTarget()
    assertFalse(first.exists())
    assertEquals("camera", second.parentFile!!.name)
    assertTrue(second.parentFile!!.canonicalPath.startsWith(File(root, "cache").canonicalPath))
  }
}
