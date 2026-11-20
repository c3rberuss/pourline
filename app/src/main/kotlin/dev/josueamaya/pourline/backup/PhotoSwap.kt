package dev.josueamaya.pourline.backup

import java.io.File
import java.io.IOException

/**
 * Sustituye [photos] (`coffee_photos`) por [staging] y borra [old], desde cualquier estado intermedio que deje una
 * muerte del proceso (SPEC-011 §3.5): se puede repetir sin daño. Los tres están en el mismo volumen, así que cada
 * renombrado es atómico.
 *
 * - [staging] sin [old]: [photos] (si existe) pasa a [old], [staging] pasa a [photos] y se borra [old].
 * - [staging] y [old] sin [photos]: [staging] pasa a [photos] y se borra [old].
 * - Los tres a la vez (algo recreó [photos]): se borra [photos], [staging] pasa a [photos] y se borra [old].
 * - Sin [staging]: la sustitución ya se hizo; solo se borra [old] y [photos] no se toca.
 *
 * @throws IOException si un renombrado falla.
 */
internal fun swapPhotos(photos: File, staging: File, old: File) {
  if (staging.exists()) {
    when {
      !old.exists() -> if (photos.exists()) rename(photos, old)
      photos.exists() -> photos.deleteRecursively()
    }
    rename(staging, photos)
  }
  old.deleteRecursively()
}

private fun rename(from: File, to: File) {
  if (!from.renameTo(to)) throw IOException("No se pudo renombrar ${from.name} a ${to.name}")
}
