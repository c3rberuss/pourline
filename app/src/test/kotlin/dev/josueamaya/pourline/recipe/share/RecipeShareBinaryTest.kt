package dev.josueamaya.pourline.recipe.share

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecipeShareBinaryTest {
  private fun write(block: ByteWriter.() -> Unit): ByteArray = ByteWriter().apply(block).toByteArray()

  private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

  @Test
  fun `uvar round trips and uses seven bits per byte`() {
    val sizes = mapOf(0L to 1, 127L to 1, 128L to 2, 16_383L to 2, 16_384L to 3, Long.MAX_VALUE to 9, -1L to 10)
    for ((value, size) in sizes) {
      val encoded = write { uvar(value) }
      assertEquals("tamaño de $value", size, encoded.size)
      assertEquals(value, ByteReader(encoded).uvar())
    }
    assertArrayEquals(bytes(0x96, 0x01), write { uvar(150) })
    assertArrayEquals(bytes(0xE8, 0x07), write { uvar(1_000) })
  }

  @Test
  fun `svar uses zigzag`() {
    assertArrayEquals(bytes(0), write { svar(0) })
    assertArrayEquals(bytes(1), write { svar(-1) })
    assertArrayEquals(bytes(2), write { svar(1) })
    assertArrayEquals(bytes(3), write { svar(-2) })
    for (value in listOf(0L, 1, -1, 2_400, -2_400, Long.MAX_VALUE, Long.MIN_VALUE)) {
      assertEquals(value, ByteReader(write { svar(value) }).svar())
    }
  }

  @Test
  fun `str is the length then utf8`() {
    assertArrayEquals(bytes(0), write { str("") })
    assertArrayEquals(bytes(2, 'h'.code, 'i'.code), write { str("hi") })
    assertArrayEquals(bytes(2, 0xC3, 0xB1), write { str("ñ") })
    for (text in listOf("", "café", "ñandú", "☕", "😀 ok")) {
      assertEquals(text, ByteReader(write { str(text) }).str())
    }
  }

  @Test
  fun `reading past the end throws`() {
    assertThrows(MalformedPayload::class.java) { ByteReader(ByteArray(0)).uvar() }
    assertThrows(MalformedPayload::class.java) { ByteReader(bytes(0x80)).uvar() }
    assertThrows(MalformedPayload::class.java) { ByteReader(bytes(5, 'a'.code)).str() }
  }

  @Test
  fun `an uvar longer than ten bytes throws`() {
    assertThrows(MalformedPayload::class.java) { ByteReader(ByteArray(11) { 0x80.toByte() }).uvar() }
    assertThrows(MalformedPayload::class.java) { ByteReader(ByteArray(10) { 0xFF.toByte() }).uvar() }
  }

  @Test
  fun `invalid utf8 throws`() {
    assertThrows(MalformedPayload::class.java) { ByteReader(bytes(1, 0xFF)).str() }
    assertThrows(MalformedPayload::class.java) { ByteReader(bytes(2, 0xC3, 0x28)).str() }
  }

  @Test
  fun `a string longer than a long throws`() {
    assertThrows(MalformedPayload::class.java) { ByteReader(write { uvar(-1) }).str() }
  }
}
