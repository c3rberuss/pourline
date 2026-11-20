package dev.josueamaya.pourline.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.util.Log
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.share.RecipeShareCodec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import java.util.Random
import kotlin.math.abs

/**
 * El modelo de lectura de Play Services se descarga bajo demanda: espera, hasta 240 s, a que [reader] lea un QR
 * antes de medir nada. Hace falta un emulador con Play Store (`emulator-5560`, AVD `SG_API36_play`).
 */
internal fun awaitQrModel(reader: MlKitQrImageReader) {
  val uri = "pourline://recipe?v=1&d=" + "AbCdEfGh0123456789-_".repeat(14).take(276)
  val bitmap = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
  val matrix = qrMatrix(uri)
  Canvas(bitmap).apply {
    drawColor(Color.WHITE)
    drawQrModules(this, matrix, 1080 - 48 - matrix.cells * 6, 228, 6)
  }
  val deadline = System.nanoTime() + 240_000_000_000L
  while (true) {
    try {
      if (runBlocking { reader.readBitmap(bitmap) }.contains(uri)) return
    } catch (e: Exception) {
      Log.i("QrTestSupport", "modelo no listo: ${e.message}")
    }
    check(System.nanoTime() < deadline) { "el modelo de lectura de QR no se descargó en 240 s" }
    Thread.sleep(3_000)
  }
}

/**
 * El borde, en píxeles enteros, de la celda [index] de [cells] celdas en una caja de [sizePx]. Los bordes son contiguos y
 * cubren la caja entera. Solo lo usa la medición de caja fija (`qrSize`): la tarjeta real dibuja módulos enteros.
 */
internal fun qrCellEdge(index: Int, cells: Int, sizePx: Int): Int = (index.toLong() * sizePx / cells).toInt()

/** Dibuja [matrix] con zona de silencio dentro de una caja de [sizePx], en píxeles enteros y negro sobre blanco. */
internal fun drawQr(canvas: Canvas, matrix: QrMatrix, left: Int, top: Int, sizePx: Int) {
  val cells = matrix.cells
  canvas.drawRect(left.toFloat(), top.toFloat(), (left + sizePx).toFloat(), (top + sizePx).toFloat(), Paint().apply { color = Color.WHITE })
  val dark = Paint().apply { color = Color.BLACK }
  for (y in 0 until matrix.modules) for (x in 0 until matrix.modules) {
    if (!matrix[x, y]) continue
    canvas.drawRect(
      left + qrCellEdge(x + QR_QUIET_ZONE, cells, sizePx).toFloat(),
      top + qrCellEdge(y + QR_QUIET_ZONE, cells, sizePx).toFloat(),
      left + qrCellEdge(x + QR_QUIET_ZONE + 1, cells, sizePx).toFloat(),
      top + qrCellEdge(y + QR_QUIET_ZONE + 1, cells, sizePx).toFloat(),
      dark,
    )
  }
}

/** Dibuja [matrix] con módulos de [modulePx] × [modulePx] y zona de silencio: la caja mide `cells × modulePx`. */
internal fun drawQrModules(canvas: Canvas, matrix: QrMatrix, left: Int, top: Int, modulePx: Int) {
  drawQr(canvas, matrix, left, top, matrix.cells * modulePx)
}

private const val URI_PREFIX = "pourline://recipe?v=1&d="
private const val URI_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

/** Un URI aleatorio y determinista de [length] caracteres (payload sin sentido, solo para medir el QR). */
internal fun uriOfLength(length: Int, seed: Long): String {
  val random = Random(length * 31L + seed)
  val body = CharArray(length - URI_PREFIX.length) { URI_ALPHABET[random.nextInt(URI_ALPHABET.length)] }
  return URI_PREFIX + String(body)
}

/** La receta de peor caso, su URI sin recorte y el texto de molienda de la tarjeta (2 líneas). */
internal class WorstCase(val recipe: Recipe, val uri: String, val grindText: String)

private fun randomText(length: Int, seed: Long): String {
  val letters = ('a'..'z') + ('A'..'Z') + ('0'..'9')
  val random = Random(seed)
  return String(CharArray(length) { letters[random.nextInt(letters.size)] })
}

private fun worstStages(): List<RecipeStage> {
  var startMs = 0L
  var water = 0.0
  return List(20) { i ->
    val pour = i % 2 == 0
    if (pour) water += 10.0 + i % 4
    RecipeStage(null, startMs, water, durationMs = (5L + i % 7 * 3) * 1_000, type = if (pour) StageType.POUR else StageType.WAIT)
      .also { startMs += it.durationMs }
  }
}

/**
 * La receta de peor caso (D43): 20 pasos, nombre aleatorio de 60 caracteres (2 líneas), V60, 93 °C y notas aleatorias de
 * hasta 1 000 caracteres, ajustadas para que el URI sin recorte quede entre `maxLength − 8` y `maxLength`. La tarjeta
 * lleva un texto de molienda que ocupa dos líneas.
 */
internal fun worstCaseRecipe(maxLength: Int, grindText: String = WORST_CASE_GRIND_TEXT): WorstCase {
  fun recipe(notesLength: Int) = Recipe(
    id = 1,
    name = randomText(60, 7).replaceFirstChar { 'R' },
    method = BrewMethod.V60,
    waterTempC = 93.0,
    notes = if (notesLength > 0) randomText(notesLength, 7) else null,
    doseG = 20.0,
    stages = worstStages(),
  )
  fun uri(notesLength: Int): String? =
    RecipeShareCodec.encode(recipe(notesLength), grinder = null, maxLength = maxLength)?.takeIf { it.trimmed.isEmpty() }?.uri
  var low = 0
  var high = 1_000
  while (low < high) {
    val mid = (low + high + 1) / 2
    if (uri(mid) != null) low = mid else high = mid - 1
  }
  val best = (maxOf(0, low - 4)..low + 4).mapNotNull { n -> uri(n)?.let { recipe(n) to it } }.maxBy { it.second.length }
  assertTrue("URI de ${best.second.length} caracteres", best.second.length in maxLength - 8..maxLength)
  return WorstCase(best.first, best.second, grindText)
}

/** Dos líneas a 12 sp en 960 px: tiene que quedar con "…" al cortar en la segunda. */
internal const val WORST_CASE_GRIND_TEXT =
  "Femobook A2 con una etiqueta muy larga para ocupar dos líneas · 34 · vueltas y clics de la rueda de ajuste principal, afinada a mano"

/**
 * La captura de la capa de Compose no es exacta al bit en el borde de un rectángulo: el primer píxel de un módulo sale con un
 * 1 % de mezcla (`#020202` en lugar de `#000000`, `#FDFDFD` en lugar de `#FFFFFF`). Con el reloj de los datos de SPEC-010 la
 * captura deja además una costura de un píxel (la primera columna de una fila de píxeles de los módulos, hasta `#0A0A0A` y
 * `#F5F5F5`), que no afecta a la lectura. Por eso cada color esperado admite 12 niveles de margen por canal. Con los azules de
 * la marca (enmienda 2), mezclar hasta un 4 % de blanco mueve `#003EB3` y `#1677FF` unos 10 niveles por canal: queda dentro
 * del margen. El texto de la tarjeta (negro al 88 % sobre blanco, `#1E1E1E` como mínimo) queda fuera.
 */
private const val PIXEL_TOLERANCE = 12

private const val WHITE = 0xFFFFFFFF.toInt()

/** Si este píxel está a [PIXEL_TOLERANCE] niveles o menos, por canal, del color [argb]. */
internal fun Int.isNear(argb: Int): Boolean =
  abs(Color.red(this) - Color.red(argb)) <= PIXEL_TOLERANCE &&
    abs(Color.green(this) - Color.green(argb)) <= PIXEL_TOLERANCE &&
    abs(Color.blue(this) - Color.blue(argb)) <= PIXEL_TOLERANCE

/**
 * La esquina de la caja del QR en [bitmap] (D52). Con el QR de la marca ya no hay píxeles negros, así que se busca el anillo
 * de los ojos en dos pasos. (1) Recorriendo filas desde arriba, se saltan las que tienen el color del anillo en x = 0 (la
 * barra de acento de `ShareFrame` ocupa todo el ancho y el QR nunca toca x = 0); la primera fila restante con un píxel de
 * ese color es el borde superior del ojo de arriba a la izquierda. (2) En la fila de la mitad del ojo, donde su borde
 * izquierdo es recto, el primer píxel de ese color es el borde izquierdo. La caja empieza 4 módulos antes en las dos
 * direcciones. Sirve igual con el anillo en `#1677FF` que en `#003EB3`.
 */
internal fun findQrBox(bitmap: Bitmap, modulePx: Int): Point {
  val ring = SHARE_QR_EYE_RING.toArgb()
  val width = bitmap.width
  val pixels = IntArray(width * bitmap.height)
  bitmap.getPixels(pixels, 0, width, 0, 0, width, bitmap.height)
  fun row(y: Int) = (0 until width).firstOrNull { pixels[y * width + it].isNear(ring) }
  val y0 = (0 until bitmap.height).firstOrNull { y -> !pixels[y * width].isNear(ring) && row(y) != null }
  check(y0 != null) { "no hay ningún píxel del color del anillo: no hay QR" }
  val middle = y0 + QR_EYE_SIZE * modulePx / 2
  val x0 = checkNotNull(row(middle)) { "no hay borde izquierdo del ojo en la fila $middle" }
  return Point(x0 - QR_QUIET_ZONE * modulePx, y0 - QR_QUIET_ZONE * modulePx)
}

/** Dónde cae un píxel de la caja del QR y el color que debe tener; `null` en las esquinas curvas de los ojos, que no se comprueban. */
private fun expectedQrColor(matrix: QrMatrix, px: Int, py: Int, modulePx: Int): Pair<String, Int>? {
  val mx = px / modulePx - QR_QUIET_ZONE
  val my = py / modulePx - QR_QUIET_ZONE
  if (mx !in 0 until matrix.modules || my !in 0 until matrix.modules) return "zona de silencio" to WHITE
  val eye = matrix.eyes.firstOrNull { (ex, ey) -> mx in ex until ex + QR_EYE_SIZE && my in ey until ey + QR_EYE_SIZE }
    ?: return if (matrix[mx, my]) "módulo oscuro" to SHARE_QR_DARK.toArgb() else "módulo claro" to WHITE
  val lx = px - (eye.first + QR_QUIET_ZONE) * modulePx
  val ly = py - (eye.second + QR_QUIET_ZONE) * modulePx
  val radii = qrEyeRadii(modulePx)
  val full = QR_EYE_SIZE * modulePx
  // Cuadrados de las esquinas curvas: del anillo (outer), del hueco (hole) y del centro (center).
  fun inCorner(x0: Int, y0: Int, size: Int, radius: Int): Boolean {
    val cx = if (lx - x0 < size / 2) lx - x0 else size - 1 - (lx - x0)
    val cy = if (ly - y0 < size / 2) ly - y0 else size - 1 - (ly - y0)
    return lx in x0 until x0 + size && ly in y0 until y0 + size && cx < radius && cy < radius
  }
  val hole = (QR_EYE_SIZE - 2) * modulePx
  val center = 3 * modulePx
  return when {
    inCorner(0, 0, full, radii.outer) -> null
    inCorner(modulePx, modulePx, hole, radii.hole) -> null
    inCorner(2 * modulePx, 2 * modulePx, center, radii.center) -> null
    lx in 2 * modulePx until 5 * modulePx && ly in 2 * modulePx until 5 * modulePx -> "centro del ojo" to SHARE_QR_DARK.toArgb()
    lx in modulePx until 6 * modulePx && ly in modulePx until 6 * modulePx -> "hueco del ojo" to WHITE
    else -> "anillo del ojo" to SHARE_QR_EYE_RING.toArgb()
  }
}

/**
 * Cada píxel de las `cells × cells` celdas tiene el color de su zona según [matrix] (D51): blanco en la zona de silencio, los
 * módulos claros y el hueco de los ojos; [SHARE_QR_DARK] en los módulos oscuros y el centro de los ojos; [SHARE_QR_EYE_RING]
 * en el anillo. Se salta lo que cae en las esquinas curvas de las tres piezas de cada ojo.
 */
internal fun assertQrModules(bitmap: Bitmap, matrix: QrMatrix, left: Int, top: Int, modulePx: Int) {
  val side = matrix.cells * modulePx
  val pixels = IntArray(side * side)
  bitmap.getPixels(pixels, 0, side, left, top, side, side)
  for (py in 0 until side) for (px in 0 until side) {
    val (zone, expected) = expectedQrColor(matrix, px, py, modulePx) ?: continue
    val actual = pixels[py * side + px]
    assertTrue(
      "celda (${px / modulePx}, ${py / modulePx}), píxel (${px % modulePx}, ${py % modulePx}), $zone: " +
        "#${Integer.toHexString(actual)} debía ser #${Integer.toHexString(expected)}",
      actual.isNear(expected),
    )
  }
}

/**
 * Captura varias tarjetas seguidas con una sola composición (`createComposeRule` solo admite un `setContent` por test): la
 * petición vive en un estado y cada una se compone con su propio [ShareCaptureHost] (`key`). Tras cada captura la tarjeta
 * sigue compuesta, para poder medir sus nodos.
 */
internal class CardCapturer(private val rule: ComposeContentTestRule) {
  private val request = mutableStateOf<ShareRequest?>(null)
  private var captured: ImageBitmap? = null
  private var failed = false

  fun start() {
    rule.setContent {
      request.value?.let { current ->
        key(current) { ShareCaptureHost(current, onCaptured = { captured = it }, onFailed = { failed = true }) }
      }
    }
  }

  /** La tarjeta de [next] como bitmap de software. */
  fun capture(next: ShareRequest): Bitmap {
    captured = null
    failed = false
    rule.runOnIdle { request.value = next }
    rule.waitUntil(10_000) { captured != null || failed }
    assertTrue("la captura falló", !failed)
    // La captura es un bitmap de hardware: no deja leer píxeles hasta copiarlo.
    return captured!!.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
  }
}
