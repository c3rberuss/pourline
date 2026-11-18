package dev.josueamaya.pourline.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.ui.components.AntDivider
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.theme.AntTheme

private val AccentHeight = 4.dp
private val SideMargin = 20.dp
private val VerticalMargin = 16.dp
private val BodyGap = 12.dp
private val FooterGap = 8.dp
private val BrandHeight = 20.dp

/**
 * El marco común de las cuatro imágenes (F0.4): fondo `bgContainer`, barra de acento de 4 dp, márgenes de 20 dp a
 * los lados y 16 dp arriba y abajo, y la última fila con el pie del café ([footer]) y la marca. El [body] ocupa el
 * alto que queda, así que la gráfica puede pedir `weight(1f)` dentro de él.
 */
@Composable
fun ShareFrame(
  modifier: Modifier = Modifier,
  footer: List<ShareFooterLine> = emptyList(),
  size: DpSize = SHARE_SIZE,
  body: @Composable ColumnScope.() -> Unit,
) {
  Column(modifier.size(size).background(AntTheme.colors.bgContainer).testTag("share_card")) {
    Box(Modifier.fillMaxWidth().height(AccentHeight).background(AntTheme.colors.primary.base))
    Column(
      Modifier.weight(1f).fillMaxWidth().padding(start = SideMargin, end = SideMargin, top = VerticalMargin, bottom = VerticalMargin),
    ) {
      Column(Modifier.weight(1f).fillMaxWidth(), content = body)
      Spacer(Modifier.height(BodyGap))
      ShareBottomRow(footer)
    }
  }
}

/** Pie del café (`weight 1`) y marca a la derecha, en la última línea. Sin pie, solo la marca. */
@Composable
private fun ShareBottomRow(footer: List<ShareFooterLine>) {
  val brand = stringResource(R.string.app_name)
  if (footer.isEmpty()) {
    Row(Modifier.fillMaxWidth().height(BrandHeight), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
      Brand(brand)
    }
    return
  }
  Column(Modifier.fillMaxWidth()) {
    AntDivider()
    Spacer(Modifier.height(FooterGap))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
      Box(Modifier.weight(1f)) { ShareCoffeeFooter(footer) }
      Spacer(Modifier.width(FooterGap))
      Brand(brand)
    }
  }
}

@Composable
private fun Brand(text: String) {
  AntText(text, Modifier.testTag("share_brand"), type = AntTextType.Secondary, style = AntTheme.typography.textSM, maxLines = 1, overflow = TextOverflow.Clip)
}
