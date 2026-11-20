package dev.josueamaya.pourline.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

/** Cómo se abre el documento que el usuario eligió con el selector de archivos. Los tests usan uno falso. */
interface BackupStreams {
  fun openInput(uri: Uri): InputStream

  fun openOutput(uri: Uri): OutputStream

  /** El tamaño en bytes, o `null` si el proveedor no lo informa. */
  fun size(uri: Uri): Long?

  /** Intenta borrar un documento a medio escribir; ignora cualquier fallo (no todos los proveedores lo permiten). */
  fun deletePartial(uri: Uri)
}

class ContentResolverStreams(private val resolver: ContentResolver) : BackupStreams {
  override fun openInput(uri: Uri): InputStream = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())

  /** Con `"wt"`, que trunca si el usuario sobrescribe un archivo; si el proveedor lo rechaza, con `"w"`. */
  override fun openOutput(uri: Uri): OutputStream {
    val stream = try {
      resolver.openOutputStream(uri, "wt")
    } catch (e: FileNotFoundException) {
      resolver.openOutputStream(uri, "w")
    } catch (e: IllegalArgumentException) {
      resolver.openOutputStream(uri, "w")
    }
    return stream ?: throw FileNotFoundException(uri.toString())
  }

  override fun size(uri: Uri): Long? =
    try {
      resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
      } ?: uri.path?.takeIf { uri.scheme == "file" }?.let { File(it).length() }
    } catch (e: Exception) {
      null
    }

  override fun deletePartial(uri: Uri) {
    try {
      if (uri.scheme == "file") {
        uri.path?.let { File(it).delete() }
      } else {
        DocumentsContract.deleteDocument(resolver, uri)
      }
    } catch (e: Exception) {
      // Sin permiso o sin soporte del proveedor: el documento queda como esté.
    }
  }
}
