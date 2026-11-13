package dev.dotscale.ui.scale

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import dev.dotscale.brew.StageNotifier
import dev.dotscale.brew.TargetStage
import dev.dotscale.settings.TargetSettings

/**
 * Avisa de que empieza una etapa con una vibración corta y un pitido, según los dos ajustes (que se leen en
 * cada aviso, así que un cambio en ajustes se aplica enseguida). Si el dispositivo no vibra o el
 * `ToneGenerator` no se puede crear, ese aviso se omite.
 */
class AndroidStageNotifier(context: Context, private val settings: TargetSettings) : StageNotifier {
  private val appContext = context.applicationContext
  private var tone: ToneGenerator? = null
  private var toneFailed = false

  private val vibrator: Vibrator? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
  }

  override fun stageStarted(stage: TargetStage) {
    if (settings.stageVibration.value) vibrate()
    if (settings.stageSound.value) beep()
  }

  private fun vibrate() {
    val vibrator = vibrator ?: return
    if (!vibrator.hasVibrator()) return
    try {
      vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (e: SecurityException) {
      Log.w(TAG, "vibration not allowed", e)
    }
  }

  private fun beep() {
    val generator = tone ?: createTone() ?: return
    generator.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS)
  }

  /** Se crea la primera vez que hace falta; si el constructor falla, no se vuelve a intentar. */
  private fun createTone(): ToneGenerator? {
    if (toneFailed) return null
    return try {
      ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME).also { tone = it }
    } catch (e: RuntimeException) {
      Log.w(TAG, "tone generator unavailable", e)
      toneFailed = true
      null
    }
  }

  override fun release() {
    tone?.release()
    tone = null
  }

  private companion object {
    const val TAG = "AndroidStageNotifier"
    const val VIBRATION_MS = 250L
    const val BEEP_MS = 200
    const val VOLUME = 80
  }
}
