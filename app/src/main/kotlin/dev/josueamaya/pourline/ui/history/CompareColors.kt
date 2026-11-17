package dev.josueamaya.pourline.ui.history

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.josueamaya.pourline.ui.theme.AntColors
import dev.josueamaya.pourline.ui.theme.AntPalette

/** Los colores de las dos series de la comparación. */
@Immutable
data class CompareColors(val a: Color, val b: Color)

/** El naranja de Ant Design (`#FA8C16`), de donde sale el tono de B. */
private const val ORANGE_SEED = 0xFFFA8C16.toInt()

/** Contraste mínimo de una serie contra el fondo de la tarjeta (WCAG 1.4.11). */
private const val MIN_SERIES_CONTRAST = 3.0

/**
 * A es el azul de la marca y B el tono de la paleta naranja más cercano al base con ≥ 3:1 sobre el fondo de la
 * tarjeta en el tema actual. Azul y naranja se distinguen con los daltonismos más comunes.
 */
fun compareColors(colors: AntColors): CompareColors {
  val tones = AntPalette.generate(ORANGE_SEED, dark = colors.isDark)
  val index = AntPalette.nearestAccessible(tones, listOf(colors.bgContainer.toArgb()), MIN_SERIES_CONTRAST)
  return CompareColors(a = colors.primary.base, b = Color(tones[index]))
}
