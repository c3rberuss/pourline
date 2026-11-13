package dev.dotscale.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Iconos de trazo (outlined, 24×24) con el estilo lineal de los iconos de Ant Design. Se dibujan en
 * negro y se tiñen con `Icon(tint = …)`, así que funcionan igual en modo claro y oscuro.
 */
object AntIcons {
  val ArrowLeft: ImageVector by lazy { strokeIcon("ArrowLeft", "M12 19L5 12L12 5", "M19 12H5") }
  val Check: ImageVector by lazy { strokeIcon("Check", "M20 6L9 17L4 12") }
  val Close: ImageVector by lazy { strokeIcon("Close", "M18 6L6 18", "M6 6L18 18") }
  val Plus: ImageVector by lazy { strokeIcon("Plus", "M5 12H19", "M12 5V19") }
  val Down: ImageVector by lazy { strokeIcon("Down", "M6 9L12 15L18 9") }
  val Reload: ImageVector by lazy {
    strokeIcon("Reload", "M21 12A9 9 0 1 1 12 3C14.52 3 16.93 4 18.74 5.74L21 8", "M21 3V8H16")
  }
  val RotateLeft: ImageVector by lazy {
    strokeIcon("RotateLeft", "M3 12A9 9 0 1 0 12 3C9.48 3 7.07 4 5.26 5.74L3 8", "M3 3V8H8")
  }
  /** Reloj con flecha antihoraria. */
  val History: ImageVector by lazy {
    strokeIcon("History", "M3 12A9 9 0 1 0 12 3C9.48 3 7.07 4 5.26 5.74L3 8", "M3 3V8H8", "M12 7V12L15 14")
  }
  /** Hoja con líneas de texto y una gota: las recetas. */
  val Recipe: ImageVector by lazy {
    strokeIcon(
      "Recipe",
      "M6 3H15L19 7V19A2 2 0 0 1 17 21H6A2 2 0 0 1 4 19V5A2 2 0 0 1 6 3Z",
      "M15 3V7H19",
      "M8 11H11",
      "M8 14H11",
      "M15 12C13.5 14 13 14.8 13 15.8A2 2 0 0 0 17 15.8C17 14.8 16.5 14 15 12Z",
    )
  }
  val Play: ImageVector by lazy { strokeIcon("Play", "M6 3L20 12L6 21Z") }
  val Pause: ImageVector by lazy {
    strokeIcon(
      "Pause",
      "M7 4H9A1 1 0 0 1 10 5V19A1 1 0 0 1 9 20H7A1 1 0 0 1 6 19V5A1 1 0 0 1 7 4Z",
      "M15 4H17A1 1 0 0 1 18 5V19A1 1 0 0 1 17 20H15A1 1 0 0 1 14 19V5A1 1 0 0 1 15 4Z",
    )
  }
  val Sun: ImageVector by lazy {
    strokeIcon(
      "Sun",
      "M16 12A4 4 0 1 1 8 12A4 4 0 1 1 16 12Z",
      "M12 2V4", "M12 20V22", "M2 12H4", "M20 12H22",
      "M4.93 4.93L6.34 6.34", "M17.66 17.66L19.07 19.07",
      "M6.34 17.66L4.93 19.07", "M19.07 4.93L17.66 6.34",
    )
  }
  val Moon: ImageVector by lazy { strokeIcon("Moon", "M12 3A6 6 0 0 0 21 12A9 9 0 1 1 12 3Z") }

  /** Mitad rellena: "seguir al sistema". */
  val Contrast: ImageVector by lazy {
    icon("Contrast") {
      stroke("M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12Z")
      fill("M12 3A9 9 0 0 1 12 21Z")
    }
  }
  val Bluetooth: ImageVector by lazy { strokeIcon("Bluetooth", "M7 7L17 17L12 22V2L17 7L7 17") }
  val Battery: ImageVector by lazy {
    strokeIcon("Battery", "M4 7H16A2 2 0 0 1 18 9V15A2 2 0 0 1 16 17H4A2 2 0 0 1 2 15V9A2 2 0 0 1 4 7Z", "M22 11V13")
  }
  val Settings: ImageVector by lazy {
    strokeIcon(
      "Settings",
      "M15 12A3 3 0 1 1 9 12A3 3 0 1 1 15 12Z",
      "M19.4 15A1.65 1.65 0 0 0 19.73 16.82L19.79 16.88A2 2 0 0 1 19.79 19.71A2 2 0 0 1 16.96 19.71L16.9 19.65" +
        "A1.65 1.65 0 0 0 15.08 19.32A1.65 1.65 0 0 0 14.08 20.83V21A2 2 0 0 1 12.08 23A2 2 0 0 1 10.08 21V20.91" +
        "A1.65 1.65 0 0 0 9 19.4A1.65 1.65 0 0 0 7.18 19.73L7.12 19.79A2 2 0 0 1 4.29 19.79A2 2 0 0 1 4.29 16.96" +
        "L4.35 16.9A1.65 1.65 0 0 0 4.68 15.08A1.65 1.65 0 0 0 3.17 14.08H3A2 2 0 0 1 1 12.08A2 2 0 0 1 3 10.08" +
        "H3.09A1.65 1.65 0 0 0 4.6 9A1.65 1.65 0 0 0 4.27 7.18L4.21 7.12A2 2 0 0 1 4.21 4.29A2 2 0 0 1 7.04 4.29" +
        "L7.1 4.35A1.65 1.65 0 0 0 8.92 4.68H9A1.65 1.65 0 0 0 10 3.17V3A2 2 0 0 1 12 1A2 2 0 0 1 14 3V3.09" +
        "A1.65 1.65 0 0 0 15 4.6A1.65 1.65 0 0 0 16.82 4.27L16.88 4.21A2 2 0 0 1 19.71 4.21A2 2 0 0 1 19.71 7.04" +
        "L19.65 7.1A1.65 1.65 0 0 0 19.32 8.92V9A1.65 1.65 0 0 0 20.83 10H21A2 2 0 0 1 23 12A2 2 0 0 1 21 14" +
        "H20.91A1.65 1.65 0 0 0 19.4 15Z",
    )
  }
  val Inbox: ImageVector by lazy {
    strokeIcon(
      "Inbox",
      "M22 12H16L14 15H10L8 12H2",
      "M5.45 5.11L2 12V18A2 2 0 0 0 4 20H20A2 2 0 0 0 22 18V12L18.55 5.11A2 2 0 0 0 16.76 4H7.24A2 2 0 0 0 5.45 5.11Z",
    )
  }
}

private const val STROKE_WIDTH = 1.75f

private class IconScope(private val builder: ImageVector.Builder) {
  fun stroke(d: String) {
    builder.addPath(
      pathData = addPathNodes(d),
      stroke = SolidColor(Color.Black),
      strokeLineWidth = STROKE_WIDTH,
      strokeLineCap = StrokeCap.Round,
      strokeLineJoin = StrokeJoin.Round,
    )
  }

  fun fill(d: String) {
    builder.addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
  }
}

private fun icon(name: String, block: IconScope.() -> Unit): ImageVector {
  val builder = ImageVector.Builder(
    name = "Ant.$name",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
  )
  IconScope(builder).block()
  return builder.build()
}

private fun strokeIcon(name: String, vararg paths: String): ImageVector = icon(name) { paths.forEach(::stroke) }
