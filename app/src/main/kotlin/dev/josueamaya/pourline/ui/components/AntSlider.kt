package dev.josueamaya.pourline.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.ui.theme.AntTheme
import kotlin.math.roundToInt

private val RailHeight = 4.dp
private val DotRadius = 4.dp
private val HandleSize = 20.dp
private val HandleBorder = 3.dp

/**
 * `Slider` de Ant Design con [count] posiciones discretas (de 0 a `count - 1`) marcadas con puntos sobre el riel. El
 * riel no se rellena hasta el tirador: sirve para escalas entre dos extremos, donde ninguno es "más". [stateDescription]
 * es lo que anuncia TalkBack en lugar del porcentaje.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntSlider(
  value: Int,
  onValueChange: (Int) -> Unit,
  count: Int,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  stateDescription: String? = null,
) {
  require(count >= 2) { "Un slider necesita al menos dos posiciones" }
  val colors = AntTheme.colors
  val railColor = colors.fillSecondary
  val dotColor = if (enabled) colors.border else colors.borderSecondary
  val handleColor = if (enabled) colors.primary.base else colors.textDisabled
  Slider(
    value = value.coerceIn(0, count - 1).toFloat(),
    onValueChange = { onValueChange(it.roundToInt()) },
    modifier = modifier.semantics { stateDescription?.let { this.stateDescription = it } },
    enabled = enabled,
    valueRange = 0f..(count - 1).toFloat(),
    steps = count - 2,
    thumb = {
      Box(
        Modifier
          .size(HandleSize)
          .shadow(1.dp, CircleShape)
          .background(colors.bgElevated, CircleShape)
          .border(HandleBorder, handleColor, CircleShape),
      )
    },
    track = {
      // El ancho del riel va del centro del tirador en la primera posición al centro en la última.
      Canvas(Modifier.fillMaxWidth().height(HandleSize)) {
        val y = size.height / 2
        drawLine(railColor, Offset(0f, y), Offset(size.width, y), RailHeight.toPx(), StrokeCap.Round)
        for (i in 0 until count) {
          drawCircle(dotColor, DotRadius.toPx(), Offset(size.width * i / (count - 1), y))
        }
      }
    },
  )
}
