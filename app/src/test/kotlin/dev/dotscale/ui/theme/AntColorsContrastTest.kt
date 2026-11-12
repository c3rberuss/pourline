package dev.dotscale.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los tonos `text` y `solid` de cada rol deben cumplir WCAG AA (≥ 4.5:1) en claro y oscuro. */
class AntColorsContrastTest {

  private fun ratio(a: Color, b: Color) = AntPalette.contrast(a.toArgb(), b.toArgb())

  private fun AntColors.roles() = mapOf("primary" to primary, "success" to success, "warning" to warning, "error" to error, "info" to info)

  private fun check(colors: AntColors) {
    val mode = if (colors.isDark) "dark" else "light"
    colors.roles().forEach { (name, role) ->
      val onBg = ratio(role.text, role.bg)
      val onContainer = ratio(role.text, colors.bgContainer)
      val solid = ratio(role.solid, colors.textLightSolid)
      val solidActive = ratio(role.solidActive, colors.textLightSolid)
      assertTrue("$mode $name text/bg = $onBg", onBg >= 4.5)
      assertTrue("$mode $name text/container = $onContainer", onContainer >= 4.5)
      assertTrue("$mode $name solid = $solid", solid >= 4.5)
      assertTrue("$mode $name solidActive = $solidActive", solidActive >= solid)
    }
  }

  @Test
  fun `light roles are accessible`() = check(antLightColors())

  @Test
  fun `dark roles are accessible`() = check(antDarkColors())
}
