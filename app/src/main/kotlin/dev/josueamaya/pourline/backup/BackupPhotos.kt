package dev.josueamaya.pourline.backup

import dev.josueamaya.pourline.coffee.photo.thumbNameOf
import java.io.File

/** Genera la miniatura de [photo] en [thumb]; `false` si no se pudo. En Android decodifica un `Bitmap`; en la JVM, uno falso. */
fun interface ThumbnailMaker {
  fun make(photo: File, thumb: File): Boolean
}

/**
 * Qué hacer con las fotos de una copia ya descomprimida (SPEC-011 §3.4).
 *
 * @property rows las filas con `photo1` o `photo2` a `null` donde la foto principal no está en la copia.
 * @property missing las fotos referenciadas que no están, por nombre distinto.
 * @property present los nombres de las fotos principales que están y las referencia algún café.
 * @property regenerate las presentes a las que les falta la miniatura.
 * @property unreferenced los archivos de la copia que ningún café referencia (y las miniaturas de esos); se borran.
 */
internal data class PhotoPlan(
  val rows: BackupRows,
  val missing: Int,
  val present: Set<String>,
  val regenerate: List<String>,
  val unreferenced: List<String>,
)

/**
 * Una foto referenciada sin su principal en la copia pasa a `null` (sin mover la otra) y cuenta como perdida. Con la
 * principal y sin miniatura, se regenera. Lo no referenciado se borra. [staged] son los nombres de los archivos de `photos/`.
 */
internal fun planPhotos(rows: BackupRows, staged: Set<String>): PhotoPlan {
  val missing = linkedSetOf<String>()
  val present = linkedSetOf<String>()
  val coffees = rows.coffees.map { coffee ->
    fun keep(name: String?): String? {
      if (name == null) return null
      return if (name in staged) {
        present += name
        name
      } else {
        missing += name
        null
      }
    }
    coffee.copy(photo1 = keep(coffee.photo1), photo2 = keep(coffee.photo2))
  }
  val kept = present + present.map(::thumbNameOf)
  return PhotoPlan(
    rows = rows.copy(coffees = coffees),
    missing = missing.size,
    present = present,
    regenerate = present.filter { thumbNameOf(it) !in staged },
    unreferenced = staged.filter { it !in kept }.sorted(),
  )
}

/**
 * Deja el staging como dice [plan]: regenera las miniaturas que faltan con [maker] y borra lo no referenciado. Si la
 * regeneración falla, el café queda sin miniatura y no cuenta como foto perdida.
 */
internal fun applyPhotoPlan(plan: PhotoPlan, staging: File, maker: ThumbnailMaker) {
  plan.unreferenced.forEach { File(staging, it).delete() }
  plan.regenerate.forEach { name ->
    val thumb = File(staging, thumbNameOf(name))
    val made = try {
      maker.make(File(staging, name), thumb)
    } catch (e: Exception) {
      false
    }
    if (!made) thumb.delete()
  }
}
