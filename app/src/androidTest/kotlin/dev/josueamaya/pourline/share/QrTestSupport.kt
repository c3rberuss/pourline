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
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.share.RecipeShareCodec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import java.util.Random

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
 * 1 % de mezcla (`#020202` en lugar de `#000000`, `#FDFDFD` en lugar de `#FFFFFF`). Por eso "negro" y "blanco" admiten
 * 12 niveles de margen: con el reloj de los datos de SPEC-010 la captura deja además una costura de un píxel (la primera
 * columna de una fila de píxeles de los módulos, hasta `#0A0A0A` y `#F5F5F5`), que no afecta a la lectura. El texto de la
 * tarjeta (negro al 88 % sobre blanco, `#1E1E1E` como mínimo) queda fuera del margen.
 */
private const val PIXEL_TOLERANCE = 12

private fun Int.isDark(): Boolean = Color.red(this) <= PIXEL_TOLERANCE && Color.green(this) <= PIXEL_TOLERANCE && Color.blue(this) <= PIXEL_TOLERANCE

private fun Int.isLight(): Boolean =
  Color.red(this) >= 255 - PIXEL_TOLERANCE && Color.green(this) >= 255 - PIXEL_TOLERANCE && Color.blue(this) >= 255 - PIXEL_TOLERANCE

/**
 * La esquina de la caja del QR en [bitmap]: el primer píxel negro recorriendo filas es la esquina del patrón de posición (el
 * texto nunca es tan oscuro); la caja empieza 4 módulos antes.
 */
internal fun findQrBox(bitmap: Bitmap, modulePx: Int): Point {
  val pixels = IntArray(bitmap.width * bitmap.height)
  bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
  val index = pixels.indexOfFirst { it.isDark() }
  check(index >= 0) { "no hay ningún píxel negro: no hay QR" }
  return Point(index % bitmap.width - QR_QUIET_ZONE * modulePx, index / bitmap.width - QR_QUIET_ZONE * modulePx)
}

/** Cada bloque de [modulePx] × [modulePx] de las `cells × cells` celdas es todo negro o todo blanco según [matrix]. */
internal fun assertQrModules(bitmap: Bitmap, matrix: QrMatrix, left: Int, top: Int, modulePx: Int) {
  val side = matrix.cells * modulePx
  val pixels = IntArray(side * side)
  bitmap.getPixels(pixels, 0, side, left, top, side, side)
  for (cy in 0 until matrix.cells) for (cx in 0 until matrix.cells) {
    val dark = cx in QR_QUIET_ZONE until QR_QUIET_ZONE + matrix.modules &&
      cy in QR_QUIET_ZONE until QR_QUIET_ZONE + matrix.modules &&
      matrix[cx - QR_QUIET_ZONE, cy - QR_QUIET_ZONE]
    for (dy in 0 until modulePx) for (dx in 0 until modulePx) {
      val actual = pixels[(cy * modulePx + dy) * side + cx * modulePx + dx]
      val ok = if (dark) actual.isDark() else actual.isLight()
      assertTrue("celda ($cx, $cy), píxel ($dx, $dy): #${Integer.toHexString(actual)} debía ser ${if (dark) "negro" else "blanco"}", ok)
    }
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
