package dev.josueamaya.pourline.coffee.photo

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoSizingTest {
  @Test
  fun `a 12 MP photo is decoded at half size and shrunk to 1600`() {
    assertEquals(2, inSampleSize(4000, 3000, 1600))
    assertEquals(1600 to 1200, scaledSize(4000, 3000, 1600))
  }

  @Test
  fun `a photo that already fits is not touched`() {
    assertEquals(1, inSampleSize(1200, 900, 1600))
    assertEquals(1200 to 900, scaledSize(1200, 900, 1600))
  }

  @Test
  fun `the sample size never leaves the photo shorter than the target`() {
    assertEquals(1, inSampleSize(3199, 100, 1600))
    assertEquals(2, inSampleSize(3200, 100, 1600))
    assertEquals(4, inSampleSize(6400, 4800, 1600))
  }

  @Test
  fun `portrait photos shrink by their long side`() {
    assertEquals(1200 to 1600, scaledSize(3000, 4000, 1600))
  }

  @Test
  fun `the central square`() {
    assertEquals(SquareCrop(500, 0, 3000), centerSquare(4000, 3000))
    assertEquals(SquareCrop(0, 500, 3000), centerSquare(3000, 4000))
    assertEquals(SquareCrop(0, 0, 500), centerSquare(500, 500))
  }

  @Test
  fun `EXIF orientations map to a rotation`() {
    assertEquals(0, exifRotationDegrees(1))
    assertEquals(180, exifRotationDegrees(3))
    assertEquals(90, exifRotationDegrees(6))
    assertEquals(270, exifRotationDegrees(8))
    assertEquals(0, exifRotationDegrees(2))
    assertEquals(180, exifRotationDegrees(4))
    assertEquals(0, exifRotationDegrees(0))
  }
}
