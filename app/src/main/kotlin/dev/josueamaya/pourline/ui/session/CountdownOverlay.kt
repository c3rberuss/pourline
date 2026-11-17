package dev.josueamaya.pourline.ui.session

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.components.AntTitle
import dev.josueamaya.pourline.ui.theme.AntSize
import dev.josueamaya.pourline.ui.theme.AntTheme

/**
 * La cuenta regresiva previa a la preparación, sobre los pasos previos o el panel sin báscula: tapa la pantalla (los
 * toques no llegan a lo de debajo), muestra los segundos que quedan y deja cancelarla.
 */
@Composable
internal fun CountdownOverlay(seconds: Int, onCancel: () -> Unit) {
  val description = stringResource(R.string.countdown_description, seconds)
  Box(
    Modifier
      .fillMaxSize()
      .background(AntTheme.colors.bgLayout)
      // Se tragan los toques que no recoge el botón, para no pulsar nada de la pantalla de debajo.
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) awaitPointerEvent().changes.forEach { it.consume() }
        }
      }
      .testTag("session_countdown"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      Modifier.fillMaxWidth().padding(AntTheme.spacing.base),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.base),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      AntTitle(stringResource(R.string.countdown_title), level = 3)
      AnimatedContent(
        targetState = seconds,
        transitionSpec = { (scaleIn(initialScale = 1.4f) + fadeIn()) togetherWith fadeOut() },
        label = "countdown",
      ) { value ->
        // `Text` y no `AntText`: el color del tipo de `AntText` taparía el primario.
        Text(
          "$value",
          Modifier
            .testTag("session_countdown_value")
            .semantics {
              contentDescription = description
              liveRegion = LiveRegionMode.Assertive
            },
          color = AntTheme.colors.primary.base,
          style = AntTheme.typography.heading1.copy(fontSize = 120.sp, lineHeight = 132.sp, fontWeight = FontWeight.Light),
          textAlign = TextAlign.Center,
        )
      }
      AntText(stringResource(R.string.countdown_hint), type = AntTextType.Secondary, textAlign = TextAlign.Center)
      AntButton(
        text = stringResource(R.string.countdown_cancel),
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth().testTag("session_countdown_cancel"),
        size = AntSize.Large,
      )
    }
  }
}
