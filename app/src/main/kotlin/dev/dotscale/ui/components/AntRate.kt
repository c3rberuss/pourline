package dev.dotscale.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag as semanticsTestTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dotscale.R
import dev.dotscale.ui.theme.AntTheme

private val RateMinTouch = 48.dp

/** Valor tras tocar la estrella [tapped]: tocar la marcada la quita si [allowClear] (`allowClear` de antd). */
internal fun rateAfterTap(current: Int?, tapped: Int, allowClear: Boolean): Int? =
  if (allowClear && current == tapped) null else tapped

/**
 * `Rate` de Ant Design con estrellas de 1 a [count]. Con [onValueChange] `null` es de solo lectura y se lee
 * como "Valoración: N de 5". Interactiva, cada estrella es un `RadioButton` con área de al menos 48 dp.
 * La estrella llena usa `warning.text` (el dorado de antd no llega a 3:1 sobre blanco) y la vacía, `fill`.
 *
 * @param testTag la interactiva etiqueta cada estrella como `"${testTag}_$i"`; la de solo lectura, el conjunto.
 */
@Composable
fun AntRate(
  value: Int?,
  onValueChange: ((Int?) -> Unit)?,
  modifier: Modifier = Modifier,
  count: Int = 5,
  starSize: Dp = 24.dp,
  allowClear: Boolean = true,
  testTag: String = "ant_rate",
) {
  val colors = AntTheme.colors
  if (onValueChange == null) {
    val description = if (value == null) {
      stringResource(R.string.rate_none)
    } else {
      stringResource(R.string.rate_description, value, count)
    }
    Row(
      modifier.clearAndSetSemantics {
        this.semanticsTestTag = testTag
        contentDescription = description
      },
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      for (i in 1..count) {
        val filled = value != null && i <= value
        Icon(
          if (filled) AntIcons.StarFilled else AntIcons.Star,
          contentDescription = null,
          modifier = Modifier.size(starSize),
          tint = if (filled) colors.warning.text else colors.fill,
        )
      }
    }
    return
  }
  val touch = maxOf(starSize, RateMinTouch)
  Row(modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
    for (i in 1..count) {
      val filled = value != null && i <= value
      val description = pluralStringResource(R.plurals.rate_star, i, i)
      Box(
        Modifier
          .size(touch)
          .selectable(
            selected = i == value,
            role = Role.RadioButton,
            onClick = { onValueChange(rateAfterTap(value, i, allowClear)) },
          )
          .semantics { contentDescription = description }
          .testTag("${testTag}_$i"),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          if (filled) AntIcons.StarFilled else AntIcons.Star,
          contentDescription = null,
          modifier = Modifier.size(starSize),
          tint = if (filled) colors.warning.text else colors.fill,
        )
      }
    }
  }
}
