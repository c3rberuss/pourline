package dev.dotscale.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dotscale.R
import dev.dotscale.ui.coffees.formatAltitude
import dev.dotscale.ui.coffees.formatCoffeeDate
import dev.dotscale.ui.coffees.label
import dev.dotscale.ui.coffees.processText
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.theme.AntTheme

private val LabelWidth = 72.dp
private const val NAME_MAX_LINES = 2

/**
 * La tarjeta del café: sobretítulo con el tostador, nombre, foto 1 (si hay), cinco filas de datos y las notas del
 * tostador. Nunca lleva precio ni stock: [CoffeeShareData] no los tiene. [photo] ya viene decodificada.
 */
@Composable
fun CoffeeShareCard(data: CoffeeShareData, photo: ImageBitmap?, modifier: Modifier = Modifier) {
  ShareFrame(modifier) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.base)) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
        data.roaster?.let { Overline(it, Modifier.testTag("share_roaster")) }
        AntText(
          data.name,
          Modifier.testTag("share_name"),
          style = AntTheme.typography.heading3,
          maxLines = NAME_MAX_LINES,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (photo != null) {
        Image(
          photo,
          contentDescription = null,
          modifier = Modifier.size(SHARE_PHOTO_DP.dp).clip(RoundedCornerShape(AntTheme.radius.lg)).testTag("share_photo"),
          contentScale = ContentScale.Crop,
        )
      }
    }
    Spacer(Modifier.height(AntTheme.spacing.base))
    Facts(data)
    data.roasterNotes?.let { notes ->
      Spacer(Modifier.height(AntTheme.spacing.base))
      Overline(stringResource(R.string.share_roaster_notes), Modifier.testTag("share_notes_label"))
      Spacer(Modifier.height(AntTheme.spacing.xxs))
      AntText(notes, Modifier.fillMaxWidth().testTag("share_notes"), maxLines = SHARE_NOTES_MAX_LINES, overflow = TextOverflow.Ellipsis)
    }
  }
}

/** El sobretítulo: `textSM` en mayúsculas, SemiBold y con 0,6 sp de separación. */
@Composable
private fun Overline(text: String, modifier: Modifier = Modifier) {
  Text(
    text.uppercase(),
    modifier = modifier,
    color = AntTheme.colors.textSecondary,
    style = AntTheme.typography.textSM.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
  )
}

@Composable
private fun Facts(data: CoffeeShareData) {
  val origin = listOfNotNull(data.country, data.region, data.farm).joinToString(" · ")
  val roast = listOfNotNull(
    data.roastLevel?.label(),
    data.roastDate?.let(::formatCoffeeDate),
    data.daysSinceRoast?.let { stringResource(R.string.share_days_one, it) },
  ).joinToString(" · ")
  val rows = listOf(
    Triple("origin", R.string.coffee_field_origin, origin),
    Triple("altitude", R.string.coffee_field_altitude_short, formatAltitude(data.altitudeMinM, data.altitudeMaxM).orEmpty()),
    Triple("varieties", R.string.coffee_field_varieties, data.varieties.joinToString(", ")),
    Triple("process", R.string.coffee_field_process, processText(data.process, data.processOther).orEmpty()),
    Triple("roast", R.string.coffee_field_roast, roast),
  ).filter { it.third.isNotEmpty() }
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    rows.forEach { (field, label, value) ->
      Row(Modifier.fillMaxWidth().testTag("share_fact_$field")) {
        AntText(stringResource(label), Modifier.width(LabelWidth), type = AntTextType.Secondary, style = AntTheme.typography.textSM, maxLines = 1)
        AntText(value, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}
