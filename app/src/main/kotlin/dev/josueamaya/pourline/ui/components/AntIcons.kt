package dev.josueamaya.pourline.ui.components

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
  /** Restar: el trazo horizontal del más. */
  val Minus: ImageVector by lazy { strokeIcon("Minus", "M5 12H19") }
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
  /** Taza con vapor. */
  val Coffee: ImageVector by lazy {
    strokeIcon(
      "Coffee",
      "M5 10H17V15A4 4 0 0 1 13 19H9A4 4 0 0 1 5 15Z",
      "M17 11H18.5A2.5 2.5 0 0 1 18.5 16H16.6",
      "M8 3V6", "M11 3V6", "M14 3V6",
    )
  }
  val Camera: ImageVector by lazy {
    strokeIcon(
      "Camera",
      "M4 8H7L9 5H15L17 8H20A1 1 0 0 1 21 9V18A1 1 0 0 1 20 19H4A1 1 0 0 1 3 18V9A1 1 0 0 1 4 8Z",
      "M15.5 13A3.5 3.5 0 1 1 8.5 13A3.5 3.5 0 1 1 15.5 13Z",
    )
  }
  /** Galería de imágenes. */
  val Image: ImageVector by lazy {
    strokeIcon(
      "Image",
      "M5 4H19A1 1 0 0 1 20 5V19A1 1 0 0 1 19 20H5A1 1 0 0 1 4 19V5A1 1 0 0 1 5 4Z",
      "M9.5 9A1.5 1.5 0 1 1 6.5 9A1.5 1.5 0 1 1 9.5 9Z",
      "M4 17L9 12L13 16L15 14L20 19",
    )
  }
  /** Tres nodos unidos. */
  val Share: ImageVector by lazy {
    strokeIcon(
      "Share",
      "M21 5A3 3 0 1 1 15 5A3 3 0 1 1 21 5Z",
      "M9 12A3 3 0 1 1 3 12A3 3 0 1 1 9 12Z",
      "M21 19A3 3 0 1 1 15 19A3 3 0 1 1 21 19Z",
      "M8.6 10.5L15.4 6.5", "M8.6 13.5L15.4 17.5",
    )
  }
  val Search: ImageVector by lazy {
    strokeIcon("Search", "M17 11A6 6 0 1 1 5 11A6 6 0 1 1 17 11Z", "M21 21L15.5 15.5")
  }
  /** Estrella de contorno. */
  val Star: ImageVector by lazy { strokeIcon("Star", STAR_PATH) }
  /** Estrella rellena, para la valoración. */
  val StarFilled: ImageVector by lazy { icon("StarFilled") { fill(STAR_PATH); stroke(STAR_PATH) } }
  /** Esquinas de visor con dos líneas de texto: leer la etiqueta. */
  val ScanText: ImageVector by lazy {
    strokeIcon(
      "ScanText",
      "M4 8V5A1 1 0 0 1 5 4H8", "M16 4H19A1 1 0 0 1 20 5V8",
      "M20 16V19A1 1 0 0 1 19 20H16", "M8 20H5A1 1 0 0 1 4 19V16",
      "M8 10H16", "M8 14H13",
    )
  }
  val Calendar: ImageVector by lazy {
    strokeIcon(
      "Calendar",
      "M5 5H19A1 1 0 0 1 20 6V19A1 1 0 0 1 19 20H5A1 1 0 0 1 4 19V6A1 1 0 0 1 5 5Z",
      "M4 10H20", "M8 3V7", "M16 3V7",
    )
  }
  /** Chevron a la derecha. */
  val Right: ImageVector by lazy { strokeIcon("Right", "M9 6L15 12L9 18") }
  /** Gotero V60: cono sobre una jarra. Pestaña Preparar y tarjeta "Básico". */
  val Dripper: ImageVector by lazy {
    strokeIcon("Dripper", "M4 4H20L15 12H9Z", "M10 12V16H14V12", "M7 20H17", "M12 18V18.5")
  }
  /** Línea con ejes: tarjeta "Experto". */
  val Chart: ImageVector by lazy { strokeIcon("Chart", "M4 4V20H20", "M7 15L11 11L14 13L19 7") }
  /** Tres puntos horizontales. */
  val More: ImageVector by lazy {
    icon("More") {
      fill("M7.5 12A1.5 1.5 0 1 1 4.5 12A1.5 1.5 0 1 1 7.5 12Z")
      fill("M13.5 12A1.5 1.5 0 1 1 10.5 12A1.5 1.5 0 1 1 13.5 12Z")
      fill("M19.5 12A1.5 1.5 0 1 1 16.5 12A1.5 1.5 0 1 1 19.5 12Z")
    }
  }
  /** Gota: paso Verter. */
  val Pour: ImageVector by lazy {
    strokeIcon("Pour", "M12 3C9 7.5 6.5 10.5 6.5 14A5.5 5.5 0 0 0 17.5 14C17.5 10.5 15 7.5 12 3Z")
  }
  /** Dos flechas circulares: paso Remover. */
  val Swirl: ImageVector by lazy {
    strokeIcon("Swirl", "M4 12A8 8 0 0 1 18.3 7", "M18.3 3V7H14", "M20 12A8 8 0 0 1 5.7 17", "M5.7 21V17H10")
  }
  /** Reloj de arena: paso Esperar. */
  val Wait: ImageVector by lazy {
    strokeIcon(
      "Wait",
      "M6 3H18", "M6 21H18",
      "M7 3V6A5 5 0 0 0 12 12A5 5 0 0 0 7 18V21",
      "M17 3V6A5 5 0 0 1 12 12A5 5 0 0 1 17 18V21",
    )
  }
  /** Círculo con una "i": abre una explicación. */
  val Info: ImageVector by lazy {
    strokeIcon("Info", "M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12Z", "M12 11V16", "M12 8V8.01")
  }
  /** Báscula vista de frente con su esfera. */
  val Scale: ImageVector by lazy {
    strokeIcon(
      "Scale",
      "M5 4H19A1 1 0 0 1 20 5V19A1 1 0 0 1 19 20H5A1 1 0 0 1 4 19V5A1 1 0 0 1 5 4Z",
      "M8 10A4 4 0 0 1 16 10", "M12 10L13.5 7.5",
    )
  }
  /** Molino manual: tolva, cuerpo y manivela. */
  val Grinder: ImageVector by lazy {
    strokeIcon(
      "Grinder",
      "M8 3H16", "M9 3L10 7H14L15 3",
      "M8 7H16V19A2 2 0 0 1 14 21H10A2 2 0 0 1 8 19Z",
      "M12 7V3", "M16 5H20", "M20 5V7",
      "M10 13H14",
    )
  }
  /** Calculadora: pantalla y teclas. Las herramientas. */
  val Calculator: ImageVector by lazy {
    strokeIcon(
      "Calculator",
      "M7 3H17A2 2 0 0 1 19 5V19A2 2 0 0 1 17 21H7A2 2 0 0 1 5 19V5A2 2 0 0 1 7 3Z",
      "M8.5 7H15.5V10H8.5Z",
      "M9 14H9.01", "M12 14H12.01", "M15 14H15.01",
      "M9 17.5H9.01", "M12 17.5H12.01", "M15 17.5H15.01",
    )
  }
  /** Dos flechas opuestas: el convertidor. */
  val Convert: ImageVector by lazy {
    strokeIcon("Convert", "M4 8H20", "M16 4L20 8L16 12", "M20 16H4", "M8 12L4 16L8 20")
  }
}

private const val STAR_PATH = "M12 3L14.8 8.8L21 9.6L16.5 14L17.6 20.3L12 17.3L6.4 20.3L7.5 14L3 9.6L9.2 8.8Z"

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
