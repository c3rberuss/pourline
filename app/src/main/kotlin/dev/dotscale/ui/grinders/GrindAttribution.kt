package dev.dotscale.ui.grinders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.dotscale.R
import dev.dotscale.grinder.Grinder
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.theme.AntTheme

/**
 * La fuente de los valores de los predefinidos (D9): "Fuente: Honest Coffee Guide · valores orientativos" y un
 * enlace por cada predefinido de [grinders] con su modelo, que abre su `sourceUrl` en el navegador. No muestra
 * nada si ninguno es predefinido.
 */
@Composable
fun GrindAttribution(grinders: List<Grinder>, modifier: Modifier = Modifier, testTag: String = "grinder_attribution") {
  val sources = grinders.filter { it.isPredefined && it.sourceUrl != null }.distinctBy { it.catalogKey }
  if (sources.isEmpty()) return
  val uriHandler = LocalUriHandler.current
  Column(modifier, verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    AntText(stringResource(R.string.grinder_attribution), Modifier.testTag(testTag), type = AntTextType.Secondary, style = AntTheme.typography.textSM)
    for (grinder in sources) {
      AntButton(
        text = grinder.model,
        onClick = { grinder.sourceUrl?.let(uriHandler::openUri) },
        modifier = Modifier.testTag("${testTag}_link_${grinder.catalogKey}"),
        type = AntButtonType.Link,
      )
    }
  }
}
