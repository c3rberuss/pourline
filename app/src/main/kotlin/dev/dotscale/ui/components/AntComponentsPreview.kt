package dev.dotscale.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.dotscale.ui.theme.AntSize
import dev.dotscale.ui.theme.AntTheme
import dev.dotscale.ui.theme.DotScaleTheme
import java.time.LocalDate

/** Catálogo de los componentes Ant para revisarlos en claro y oscuro desde el panel de previews. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AntShowcase() {
  val spacing = AntTheme.spacing
  Column(
    Modifier.background(AntTheme.colors.bgLayout).padding(spacing.base),
    verticalArrangement = Arrangement.spacedBy(spacing.base),
  ) {
    AntTitle("Dot Scale · Ant Design", level = 4)

    AntDivider(text = "Button", orientation = AntDividerOrientation.Left)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
      AntButton("Primary", {}, type = AntButtonType.Primary)
      AntButton("Default", {})
      AntButton("Dashed", {}, type = AntButtonType.Dashed)
      AntButton("Text", {}, type = AntButtonType.Text)
      AntButton("Link", {}, type = AntButtonType.Link)
      AntButton("Danger", {}, type = AntButtonType.Primary, danger = true)
      AntButton("Danger", {}, danger = true)
      AntButton("Cargando", {}, type = AntButtonType.Primary, loading = true)
      AntButton("Deshabilitado", {}, enabled = false)
      AntButton("Round", {}, shape = AntButtonShape.Round, icon = AntIcons.Plus)
      AntIconButton(AntIcons.Reload, "Recargar", {}, type = AntButtonType.Default)
      AntIconButton(AntIcons.Play, "Iniciar", {}, type = AntButtonType.Primary)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
      AntButton("Small", {}, size = AntSize.Small)
      AntButton("Middle", {})
      AntButton("Large", {}, size = AntSize.Large, type = AntButtonType.Primary)
    }

    AntDivider(text = "Tag", orientation = AntDividerOrientation.Left)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
      AntTag("Default")
      AntTag("Primary", color = AntTagColor.Primary)
      AntTag("Success", color = AntTagColor.Success, icon = AntIcons.Check)
      AntTag("Warning", color = AntTagColor.Warning)
      AntTag("Error", color = AntTagColor.Error, onClose = {})
      var checked by remember { mutableStateOf(true) }
      AntCheckableTag("Filtro", checked = checked, onCheckedChange = { checked = it })
      AntCheckableTag("Otro", checked = !checked, onCheckedChange = { checked = !it })
    }

    AntDivider(text = "Switch · Segmented", orientation = AntDividerOrientation.Left)
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.base), verticalAlignment = Alignment.CenterVertically) {
      var on by remember { mutableStateOf(true) }
      AntSwitch(on, { on = it })
      AntSwitch(!on, { on = !it })
      AntSwitch(true, {}, size = AntSwitchSize.Small)
      AntSwitch(true, {}, loading = true)
      AntSwitch(false, {}, enabled = false)
    }
    var unit by remember { mutableStateOf("g") }
    AntSegmented(
      options = listOf(AntSegmentedOption("g", "Gramos"), AntSegmentedOption("oz", "Onzas"), AntSegmentedOption("ml", "Mililitros")),
      selected = unit,
      onSelect = { unit = it },
      modifier = Modifier.fillMaxWidth(),
    )

    AntDivider(text = "Alert · Message", orientation = AntDividerOrientation.Left)
    AntAlert("Conectando…", type = AntStatus.Info)
    AntAlert("Dosis guardada", type = AntStatus.Success, description = "Se hizo tara para empezar a verter.")
    AntAlert("Desconectada", type = AntStatus.Warning, action = { AntButton("Reintentar", {}, size = AntSize.Small) })
    AntAlert("No se pudo conectar", type = AntStatus.Error, onClose = {})
    AntMessage("Unidad cambiada", AntStatus.Success)

    AntDivider(text = "Card · Statistic · Badge", orientation = AntDividerOrientation.Left)
    AntCard(Modifier.fillMaxWidth(), title = "Dosis y ratio", extra = { AntButton("Quitar", {}, type = AntButtonType.Link, size = AntSize.Small, danger = true) }) {
      Row(Modifier.fillMaxWidth()) {
        AntStatistic("18.0", Modifier.weight(1f), title = "Dosis", suffix = "g")
        AntStatistic("1:15.2", Modifier.weight(1f), title = "Ratio", valueColor = AntTheme.colors.primary.base)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(spacing.base)) {
        AntBadge(AntBadgeStatus.Success, text = "Lista")
        AntBadge(AntBadgeStatus.Processing, text = "Buscando")
        AntBadge(AntBadgeStatus.Error, text = "Error")
      }
    }

    AntDivider(text = "Progress · Spin", orientation = AntDividerOrientation.Left)
    AntProgress(30f)
    AntProgress(60f, status = AntProgressStatus.Active)
    AntProgress(100f)
    AntProgress(70f, status = AntProgressStatus.Exception)
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.base), verticalAlignment = Alignment.CenterVertically) {
      AntSpin(size = AntSpinSize.Small)
      AntSpin()
      AntSpin(size = AntSpinSize.Large)
    }

    AntDivider(text = "Input · List · Empty", orientation = AntDividerOrientation.Left)
    var text by remember { mutableStateOf("") }
    AntInput(text, { text = it }, Modifier.fillMaxWidth(), placeholder = "Dosis objetivo", suffix = { AntText("g", type = AntTextType.Secondary) })
    AntInput("abc", {}, Modifier.fillMaxWidth(), status = AntInputStatus.Error)
    AntCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(), verticalArrangement = Arrangement.Top) {
      AntListItem("TIMEMORE DOT", description = "Black Mirror DOT · AA:BB:CC", extra = { AntText("-58 dBm", type = AntTextType.Secondary) }, onClick = {})
      AntListItem("Dispositivo sin nombre", description = "11:22:33", divider = false, onClick = {})
    }
    AntInput("Primera línea\nSegunda línea", {}, Modifier.fillMaxWidth(), singleLine = false, minLines = 3)
    AntEmpty("Enciende la báscula y acércala al teléfono.")

    AntDivider(text = "Select · AutoComplete · Message", orientation = AntDividerOrientation.Left)
    val methods = listOf("V60", "Kalita Wave", "Chemex").map { AntSelectOption(it, it) }
    var method by remember { mutableStateOf<String?>("V60") }
    AntSelect(methods, method, { method = it }, label = "Método", Modifier.fillMaxWidth())
    AntSelect(methods, null, {}, label = "Café", Modifier.fillMaxWidth(), placeholder = "Todos")
    var coffee by remember { mutableStateOf("Et") }
    AntAutoComplete(coffee, { coffee = it }, listOf("Etiopía", "Kenia"), Modifier.fillMaxWidth(), placeholder = "Café")
    AntMessage("Preparación borrada", AntStatus.Success, action = { AntButton("Deshacer", {}, type = AntButtonType.Link, size = AntSize.Small) })

    AntDivider(text = "Rate · FormItem · SelectTrigger", orientation = AntDividerOrientation.Left)
    var stars by remember { mutableStateOf<Int?>(3) }
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.base), verticalAlignment = Alignment.CenterVertically) {
      AntRate(null, { stars = it }, starSize = 32.dp, testTag = "preview_rate_empty")
    }
    AntRate(stars, { stars = it }, starSize = 32.dp)
    AntRate(4, null, starSize = 16.dp)
    AntFormItem("Nombre", Modifier.fillMaxWidth(), required = true, error = "El nombre es obligatorio") {
      AntInput("", {}, Modifier.fillMaxWidth(), status = AntInputStatus.Error)
    }
    AntFormItem("Tostador", Modifier.fillMaxWidth(), help = "Se sugieren los que ya registraste") {
      AntInput("Café de Altura", {}, Modifier.fillMaxWidth())
    }
    AntFormItem(
      "Variedades",
      Modifier.fillMaxWidth(),
      extra = { AntTag("Leído de la etiqueta", color = AntTagColor.Primary, icon = AntIcons.ScanText) },
    ) {
      AntInput("Heirloom", {}, Modifier.fillMaxWidth())
    }
    AntSelectTrigger("Etiopía Guji · día 9", {}, Modifier.fillMaxWidth(), label = "Café", leading = {
      Icon(AntIcons.Coffee, contentDescription = null, modifier = Modifier.size(16.dp), tint = AntTheme.colors.textTertiary)
    })
    AntSelectTrigger("Etiopía Guji · día 9", {}, Modifier.fillMaxWidth(), label = "Café", enabled = false)

    AntDivider(text = "DatePicker · Collapse", orientation = AntDividerOrientation.Left)
    AntDatePicker(null, {}, Modifier.fillMaxWidth(), placeholder = "Fecha de tueste")
    AntDatePicker(LocalDate.of(2026, 10, 12), {}, Modifier.fillMaxWidth(), testTag = "preview_date")
    var folded by remember { mutableStateOf(false) }
    AntCollapse("Datos del café", folded, { folded = it }, Modifier.fillMaxWidth(), extra = "Natural · Claro") {
      AntText("Tostador: Café de Altura")
      AntText("Origen: Etiopía · Guji", type = AntTextType.Secondary)
    }
    AntCollapse("Datos del café", true, {}, Modifier.fillMaxWidth(), testTag = "preview_collapse_open") {
      AntText("Proceso: Natural")
    }

    AntDivider(text = "TabBar · ProgressCircle · PageIndicator · ChoiceModal", orientation = AntDividerOrientation.Left)
    AntTabBar(
      items = listOf(
        AntTabBarItem("prepare", "Preparar", AntIcons.Dripper, "preview_tab_prepare"),
        AntTabBarItem("coffees", "Cafés", AntIcons.Coffee, "preview_tab_coffees"),
        AntTabBarItem("recipes", "Recetas", AntIcons.Recipe, "preview_tab_recipes"),
        AntTabBarItem("history", "Historial", AntIcons.History, "preview_tab_history"),
      ),
      selected = "coffees",
      onSelect = {},
    )
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.base)) {
      listOf(0f, 0.61f, 1f).forEach { fraction ->
        AntProgressCircle(fraction, Modifier.size(96.dp), strokeWidth = 10.dp, segments = listOf(0.16f, 0.6f)) {
          AntText("${(fraction * 100).toInt()} %")
        }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.base), verticalAlignment = Alignment.CenterVertically) {
      AntPageIndicator(count = 2, selected = 0)
      AntPageIndicator(count = 2, selected = 1)
      listOf(AntIcons.Pour, AntIcons.Swirl, AntIcons.Wait, AntIcons.Scale, AntIcons.Chart, AntIcons.More).forEach {
        Icon(it, contentDescription = null, modifier = Modifier.size(24.dp), tint = AntTheme.colors.text)
      }
    }
    AntCard(Modifier.fillMaxWidth(), title = "¿Terminar la preparación?") {
      AntText("Puedes guardarla o descartarla.", type = AntTextType.Secondary)
      AntButton("Terminar y guardar", {}, Modifier.fillMaxWidth(), type = AntButtonType.Primary)
      AntButton("Descartar", {}, Modifier.fillMaxWidth(), danger = true)
      AntButton("Seguir preparando", {}, Modifier.fillMaxWidth())
    }
  }
}

@Preview(name = "Ant · Claro", showBackground = true, heightDp = 3800)
@Composable
private fun AntShowcaseLightPreview() {
  DotScaleTheme(darkTheme = false) { AntShowcase() }
}

@Preview(name = "Ant · Oscuro", showBackground = true, backgroundColor = 0xFF000000, heightDp = 3800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AntShowcaseDarkPreview() {
  DotScaleTheme(darkTheme = true) { AntShowcase() }
}
