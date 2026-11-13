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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.dotscale.ui.theme.AntSize
import dev.dotscale.ui.theme.AntTheme
import dev.dotscale.ui.theme.DotScaleTheme

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
  }
}

@Preview(name = "Ant · Claro", showBackground = true, heightDp = 2200)
@Composable
private fun AntShowcaseLightPreview() {
  DotScaleTheme(darkTheme = false) { AntShowcase() }
}

@Preview(name = "Ant · Oscuro", showBackground = true, backgroundColor = 0xFF000000, heightDp = 2200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AntShowcaseDarkPreview() {
  DotScaleTheme(darkTheme = true) { AntShowcase() }
}
