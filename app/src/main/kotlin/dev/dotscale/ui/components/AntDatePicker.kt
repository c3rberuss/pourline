package dev.dotscale.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dotscale.R
import dev.dotscale.ui.theme.AntSize
import dev.dotscale.ui.theme.AntTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Medianoche UTC del día, que es lo que usa el `DatePicker` de Material 3. */
internal fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Día que representa una marca del `DatePicker` (medianoche UTC), sin desfase por la zona horaria. */
internal fun utcMillisToLocalDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * Campo de fecha: un [AntSelectTrigger] con el icono de calendario que abre el `DatePickerDialog` de Material 3
 * con las fechas posteriores a [maxDate] deshabilitadas (también al escribir). Con valor, un botón la borra
 * (`"${testTag}_clear"`).
 *
 * @param status `Error` pinta el borde del color de error (la validación vive en el llamador).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntDatePicker(
  value: LocalDate?,
  onValueChange: (LocalDate?) -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  maxDate: LocalDate? = null,
  status: AntInputStatus = AntInputStatus.Default,
  enabled: Boolean = true,
  testTag: String? = null,
) {
  val colors = AntTheme.colors
  var open by remember { mutableStateOf(false) }
  val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
  Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
    AntSelectTrigger(
      value = value?.format(formatter),
      onClick = { open = true },
      placeholder = placeholder,
      trailing = {
        Row {
          if (value != null) Spacer(Modifier.width(48.dp))
          Icon(AntIcons.Calendar, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textTertiary)
        }
      },
      enabled = enabled,
      status = status,
      testTag = testTag,
    )
    if (value != null && enabled) {
      AntIconButton(
        AntIcons.Close,
        stringResource(R.string.date_picker_clear),
        { onValueChange(null) },
        Modifier.padding(end = 20.dp).let { if (testTag != null) it.testTag("${testTag}_clear") else it },
        size = AntSize.Small,
      )
    }
  }
  if (open) {
    val maxMillis = maxDate?.toUtcMillis()
    val selectable = remember(maxMillis) {
      object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long) = maxMillis == null || utcTimeMillis <= maxMillis
        override fun isSelectableYear(year: Int) = maxDate == null || year <= maxDate.year
      }
    }
    val state = rememberDatePickerState(
      initialSelectedDateMillis = value?.toUtcMillis(),
      selectableDates = selectable,
    )
    DatePickerDialog(
      onDismissRequest = { open = false },
      confirmButton = {
        AntButton(
          stringResource(R.string.date_picker_accept),
          {
            state.selectedDateMillis?.let { onValueChange(utcMillisToLocalDate(it)) }
            open = false
          },
          type = AntButtonType.Text,
          modifier = Modifier.testTag("date_picker_accept"),
        )
      },
      dismissButton = {
        AntButton(stringResource(R.string.date_picker_cancel), { open = false }, type = AntButtonType.Text)
      },
    ) {
      DatePicker(state)
    }
  }
}
