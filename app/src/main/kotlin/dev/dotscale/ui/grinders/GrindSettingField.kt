package dev.dotscale.ui.grinders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.dotscale.R
import dev.dotscale.grinder.ConverterArgs
import dev.dotscale.grinder.GrindChoice
import dev.dotscale.grinder.GrindConverter
import dev.dotscale.grinder.Grinder
import dev.dotscale.grinder.clampToScale
import dev.dotscale.grinder.snap
import dev.dotscale.grinder.withGrinder
import dev.dotscale.grinder.withValue
import dev.dotscale.history.BrewMethod
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntFormItem
import dev.dotscale.ui.components.AntSelect
import dev.dotscale.ui.components.AntSelectOption
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.theme.AntTheme

/** El valor de "Sin molino" en el desplegable. */
private const val NO_GRINDER = -1L

/** El ajuste inicial de un molino sin valor: el sugerido para el método o el centro de la escala (D13). */
fun startValue(grinder: Grinder, method: BrewMethod?): Double =
  GrindConverter.suggested(method, grinder) ?: clampToScale(snap((grinder.scaleMin + grinder.scaleMax) / 2, grinder), grinder)

/**
 * El campo de molienda de una receta o una preparación (§3.7): el selector de molino ("Sin molino", mis molinos y,
 * si ya no es de ellos, el actual) y el ajuste con [GrindInput]. Un molino eliminado ([GrindChoice.Orphan]) muestra su
 * copia de texto con "molino eliminado" y el selector en "Elige un molino". Los tags son `<tagPrefix>_grinder` y
 * `<tagPrefix>_grind_value`. Con [onOpenConverter] y un molino vivo con ajuste, añade el atajo "Ver en mis otros
 * molinos" (`<tagPrefix>_grind_converter`).
 *
 * @param options mis molinos; [current] es el molino vivo de la elección (aunque no esté en [options]).
 */
@Composable
fun GrindSettingField(
  choice: GrindChoice,
  options: List<Grinder>,
  current: Grinder?,
  method: BrewMethod?,
  onChange: (GrindChoice) -> Unit,
  tagPrefix: String,
  modifier: Modifier = Modifier,
  onOpenConverter: ((ConverterArgs) -> Unit)? = null,
) {
  val noGrinder = stringResource(R.string.grind_none)
  val choices = buildList {
    add(AntSelectOption(NO_GRINDER, noGrinder))
    options.forEach { add(AntSelectOption(it.id, it.displayName)) }
    if (current != null && options.none { it.id == current.id }) add(AntSelectOption(current.id, current.displayName))
  }
  val selected = when (choice) {
    GrindChoice.None -> NO_GRINDER
    is GrindChoice.Live -> choice.grinderId
    is GrindChoice.Orphan -> null
  }
  Column(modifier, verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm)) {
    AntSelect(
      options = choices,
      selected = selected,
      onSelect = { id -> onChange(choice.withGrinder(id.takeIf { it != NO_GRINDER })) },
      label = stringResource(R.string.grind_grinder),
      modifier = Modifier.fillMaxWidth(),
      placeholder = stringResource(R.string.grind_choose),
      testTag = "${tagPrefix}_grinder",
    )
    when (choice) {
      GrindChoice.None -> Unit
      is GrindChoice.Orphan -> GrindSettingText(choice.setting, grinder = null, testTag = "${tagPrefix}_grind_orphan")
      is GrindChoice.Live -> if (current != null) {
        AntFormItem(label = stringResource(R.string.grind_value)) {
          Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
            GrindInput(
              value = choice.value,
              grinder = current,
              onChange = { onChange(choice.withValue(it)) },
              start = startValue(current, method),
              testTag = "${tagPrefix}_grind_value",
            )
            if (choice.value == null) {
              AntText(stringResource(R.string.grind_value_hint), type = AntTextType.Secondary, style = AntTheme.typography.textSM)
            }
            if (isOutOfScale(choice.value, current)) {
              GrindSettingText(dev.dotscale.grinder.GrindSetting(current.id, choice.value, null), current, testTag = "${tagPrefix}_grind_text")
            }
            if (onOpenConverter != null && choice.value != null) {
              AntButton(
                text = stringResource(R.string.grind_card_converter),
                onClick = { onOpenConverter(ConverterArgs(current.id, method, choice.value)) },
                modifier = Modifier.testTag("${tagPrefix}_grind_converter"),
                type = AntButtonType.Link,
              )
            }
          }
        }
      }
    }
  }
}
