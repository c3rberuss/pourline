package dev.josueamaya.pourline.ui.recipes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Abre [url] en el navegador (`ACTION_VIEW`). `false` si no hay ninguna app que lo abra: quien llama avisa con un mensaje
 * (SPEC-012 §3.8).
 */
fun openSourceUrl(context: Context, url: String): Boolean {
  val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  return try {
    context.startActivity(intent)
    true
  } catch (_: ActivityNotFoundException) {
    false
  }
}
