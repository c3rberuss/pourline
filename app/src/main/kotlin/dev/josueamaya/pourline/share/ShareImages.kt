package dev.josueamaya.pourline.share

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Escribe la imagen en `cacheDir/shared/` y la prepara para `ACTION_SEND` con `FileProvider`
 * (`${packageName}.fileprovider`). Cada escritura borra primero las imágenes anteriores de la carpeta (§3.7).
 */
class ShareImages(private val context: Context) {
  private val directory get() = File(context.cacheDir, SHARED_DIR)

  /** Guarda [bitmap] como PNG con el nombre [name] y devuelve el archivo. */
  suspend fun write(bitmap: ImageBitmap, name: String): File = withContext(Dispatchers.IO) {
    val dir = directory
    dir.mkdirs()
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, name)
    val written = file.outputStream().use { bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
    if (!written) throw IOException("No se pudo codificar el PNG")
    file
  }

  fun uri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

  /** `ACTION_SEND` de `image/png` con `EXTRA_STREAM`, permiso de lectura y el selector de apps con [title]. */
  fun chooser(uri: Uri, title: String): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
      type = "image/png"
      putExtra(Intent.EXTRA_STREAM, uri)
      clipData = ClipData.newRawUri(null, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, title).apply {
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      // El filtro `SEND image/*` de Pourline la haría aparecer como destino de sus propias imágenes (D13).
      putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context.packageName, MAIN_ACTIVITY)))
    }
  }

  private companion object {
    const val SHARED_DIR = "shared"
    const val MAIN_ACTIVITY = "dev.josueamaya.pourline.MainActivity"
    const val PNG_QUALITY = 100
  }
}
