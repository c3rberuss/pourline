package dev.josueamaya.pourline.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.history.BrewMethod
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Fecha y hora con el formato del idioma actual y la zona del sistema. */
fun formatBrewDateTime(epochMs: Long, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String =
  DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withLocale(locale)
    .format(Instant.ofEpochMilli(epochMs).atZone(zone))

fun formatBrewDate(epochMs: Long, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String =
  DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    .withLocale(locale)
    .format(Instant.ofEpochMilli(epochMs).atZone(zone))

/** Nombre fijo del método (no se traduce), o `null` si va en `strings.xml` (`OTHER`, Prensa francesa, Cold brew y Moka). */
val BrewMethod.fixedName: String?
  get() = when (this) {
    BrewMethod.V60 -> "V60"
    BrewMethod.KALITA_WAVE -> "Kalita Wave"
    BrewMethod.CHEMEX -> "Chemex"
    BrewMethod.ORIGAMI -> "Origami"
    BrewMethod.AEROPRESS -> "Aeropress"
    BrewMethod.ESPRESSO -> "Espresso"
    BrewMethod.FRENCH_PRESS, BrewMethod.COLD_BREW, BrewMethod.MOKA, BrewMethod.OTHER -> null
  }

@Composable
fun BrewMethod.label(): String = fixedName ?: stringResource(
  when (this) {
    BrewMethod.FRENCH_PRESS -> R.string.brew_method_french_press
    BrewMethod.COLD_BREW -> R.string.brew_method_cold_brew
    BrewMethod.MOKA -> R.string.brew_method_moka
    else -> R.string.brew_method_other
  },
)
