package dev.josueamaya.pourline.ui.recipes

import androidx.annotation.StringRes
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.recipe.share.ImportError
import dev.josueamaya.pourline.recipe.share.ImportNotice
import dev.josueamaya.pourline.ui.components.AntStatus

/** El texto del mensaje de una importación (§4 del spec). */
@StringRes
fun importNoticeText(notice: ImportNotice): Int = when (notice) {
  ImportNotice.Added -> R.string.recipe_import_added
  ImportNotice.Deferred -> R.string.recipe_import_deferred
  is ImportNotice.Failed -> when (notice.error) {
    ImportError.NOT_POURLINE -> R.string.recipe_import_not_pourline
    ImportError.NO_RECIPE_IN_IMAGE -> R.string.recipe_import_no_recipe
    ImportError.NEWER_VERSION -> R.string.recipe_import_newer
    ImportError.CORRUPT -> R.string.recipe_import_corrupt
    ImportError.INVALID -> R.string.recipe_import_invalid
    ImportError.SCANNER_UNAVAILABLE -> R.string.recipe_import_scanner_unavailable
    ImportError.READER_NOT_READY -> R.string.recipe_import_reader_preparing
  }
}

/** Añadida es un éxito, aplazada es informativa y cualquier fallo es un error. */
fun importNoticeStatus(notice: ImportNotice): AntStatus = when (notice) {
  ImportNotice.Added -> AntStatus.Success
  ImportNotice.Deferred -> AntStatus.Info
  is ImportNotice.Failed -> AntStatus.Error
}
