package dev.josueamaya.pourline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.ui.theme.AntTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val StepperButtonSize = 40.dp
private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 120L

/**
 * Stepper vertical: [+] arriba, el valor, [−] abajo y la etiqueta de la unidad debajo. Los botones miden 40 dp con
 * área táctil de 48 dp y se repiten mientras se mantienen pulsados (cada 120 ms tras 400 ms). Con [testTag] expone
 * `<testTag>_inc`, `<testTag>_dec` y `<testTag>_value`.
 */
@Composable
fun AntStepper(
  value: String,
  label: String,
  onIncrement: () -> Unit,
  onDecrement: () -> Unit,
  incrementDescription: String,
  decrementDescription: String,
  modifier: Modifier = Modifier,
  incrementEnabled: Boolean = true,
  decrementEnabled: Boolean = true,
  testTag: String? = null,
) {
  val colors = AntTheme.colors
  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    StepperButton(AntIcons.Plus, incrementDescription, incrementEnabled, onIncrement, testTag?.let { "${it}_inc" })
    Text(
      value,
      Modifier.let { if (testTag != null) it.testTag("${testTag}_value") else it },
      color = colors.text,
      style = AntTheme.typography.heading3,
      textAlign = TextAlign.Center,
      maxLines = 1,
    )
    StepperButton(AntIcons.Minus, decrementDescription, decrementEnabled, onDecrement, testTag?.let { "${it}_dec" })
    Text(
      label,
      color = colors.textSecondary,
      style = AntTheme.typography.textSM,
      textAlign = TextAlign.Center,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun StepperButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit, tag: String?) {
  val colors = AntTheme.colors
  val scope = rememberCoroutineScope()
  val currentClick by rememberUpdatedState(onClick)
  val currentEnabled by rememberUpdatedState(enabled)
  Box(
    Modifier
      .minimumInteractiveComponentSize()
      .let { if (tag != null) it.testTag(tag) else it }
      .size(StepperButtonSize)
      .clip(RoundedCornerShape(AntTheme.radius.lg))
      .background(if (enabled) colors.fillTertiary else colors.fillQuaternary)
      // Un toque suma una vez; mantenerlo pulsado repite. El puntero se gestiona a mano para poder repetir.
      .pointerInput(Unit) {
        awaitEachGesture {
          awaitFirstDown()
          if (!currentEnabled) {
            waitForUpOrCancellation()
            return@awaitEachGesture
          }
          currentClick()
          val job = scope.launch {
            delay(REPEAT_DELAY_MS)
            while (currentEnabled) {
              currentClick()
              delay(REPEAT_INTERVAL_MS)
            }
          }
          waitForUpOrCancellation()
          job.cancel()
        }
      }
      // La acción de accesibilidad llama directamente: TalkBack no genera punteros.
      .semantics(mergeDescendants = true) {
        role = Role.Button
        contentDescription = description
        if (!enabled) disabled()
        onClick(label = description) {
          if (currentEnabled) currentClick()
          currentEnabled
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (enabled) colors.text else colors.textDisabled)
  }
}
