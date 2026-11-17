package dev.josueamaya.pourline.share

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Quien lanza el selector de apps. En los tests, un falso que guarda el `Intent` (D30). */
fun interface ShareSender {
  fun send(chooser: Intent)
}

@Composable
fun rememberShareSender(): ShareSender {
  val context = LocalContext.current
  return remember(context) {
    ShareSender { chooser ->
      if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(chooser)
    }
  }
}
