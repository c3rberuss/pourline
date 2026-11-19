package dev.josueamaya.pourline.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import dev.josueamaya.pourline.ui.theme.AntColors
import dev.josueamaya.pourline.ui.theme.AntPalette
import dev.josueamaya.pourline.ui.theme.antDarkColors
import dev.josueamaya.pourline.ui.theme.antLightColors
import org.junit.Assert.assertTrue
import org.junit.Test

/** La estrella llena (`warning.text`) es un elemento gráfico: debe llegar a 3:1 sobre el contenedor. */
class AntRateColorsTest {
  private fun check(colors: AntColors) {
    val container = colors.bgContainer.compositeOver(Color.Black)
    val ratio = AntPalette.contrast(colors.warning.text.toArgb(), container.toArgb())
    assertTrue("isDark=${colors.isDark} warning.text/bgContainer = $ratio", ratio >= 3.0)
  }

  @Test
  fun `filled star reaches 3 to 1 in light`() = check(antLightColors())

  @Test
  fun `filled star reaches 3 to 1 in dark`() = check(antDarkColors())
}
