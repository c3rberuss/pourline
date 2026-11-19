package dev.josueamaya.pourline.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/** Compara con las paletas publicadas por `@ant-design/colors` (blue, green) y el `darkAlgorithm` de antd v5. */
class AntPaletteTest {

  private fun hex(colors: List<Int>) = colors.map { String.format("#%06x", it and 0xFFFFFF) }

  @Test
  fun `blue light palette matches antd`() {
    assertEquals(
      listOf("#e6f4ff", "#bae0ff", "#91caff", "#69b1ff", "#4096ff", "#1677ff", "#0958d9", "#003eb3", "#002c8c", "#001d66"),
      hex(AntPalette.generate(0xFF1677FF.toInt())),
    )
  }

  @Test
  fun `green light palette matches antd`() {
    assertEquals(
      listOf("#f6ffed", "#d9f7be", "#b7eb8f", "#95de64", "#73d13d", "#52c41a", "#389e0d", "#237804", "#135200", "#092b00"),
      hex(AntPalette.generate(0xFF52C41A.toInt())),
    )
  }

  @Test
  fun `blue dark palette matches antd dark algorithm`() {
    val dark = hex(AntPalette.generate(0xFF1677FF.toInt(), dark = true))
    assertEquals("#111a2c", dark[0])
    assertEquals("#15325b", dark[2])
    assertEquals("#1554ad", dark[4])
    assertEquals("#1668dc", dark[5])
    assertEquals("#3c89e8", dark[6])
  }
}
