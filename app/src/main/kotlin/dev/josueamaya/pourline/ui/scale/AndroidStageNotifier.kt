package dev.josueamaya.pourline.ui.scale

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import dev.josueamaya.pourline.brew.StageNotifier
import dev.josueamaya.pourline.brew.TargetStage
import dev.josueamaya.pourline.settings.TargetSettings

/**
 * Avisa de que empieza una etapa con un doble pitido agudo y una doble vibración, y de los últimos 5 s antes de ella con
 * un tic corto por segundo, según los dos ajustes (que se leen en cada aviso, así que un cambio en ajustes se aplica
 * enseguida). El cambio de paso suena y vibra distinto del tic para reconocerlo sin mirar la pantalla. Si el dispositivo
 * no vibra o el `ToneGenerator` no se puede crear, ese aviso se omite.
 *
 * Ambos avisos van por el canal de alarma: el usuario los activó a propósito en ajustes y el teléfono suele estar
 * en modo silencio mientras prepara, que silencia el canal de notificaciones y suprime las vibraciones sin
 * atributos (el sistema las trata como háptica táctil).
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

  override fun stageStarted(stage: TargetStage) = stepCue()

  /** Los tics de la cuenta regresiva son cortos; la salida suena y vibra como un cambio de paso. */
  override fun countdownTick(remainingS: Int) = if (remainingS <= 0) stepCue() else tick()

  /** Los tics hacia el siguiente paso son como los de la cuenta regresiva; el paso en sí suena con [stageStarted]. */
  override fun nextStepTick(remainingS: Int) = tick()

  private fun tick() {
    if (settings.stageVibration.value) vibrate(VibrationEffect.createOneShot(TICK_VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    if (settings.stageSound.value) beep(TICK_TONE)
  }

  private fun stepCue() {
    if (settings.stageVibration.value) vibrate(VibrationEffect.createWaveform(STEP_VIBRATION_PATTERN, NO_REPEAT))
    if (settings.stageSound.value) beep(STEP_TONE)
  }

  private fun vibrate(effect: VibrationEffect) {
    val vibrator = vibrator ?: return
    if (!vibrator.hasVibrator()) return
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
      } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(effect, ALARM_AUDIO_ATTRIBUTES)
      }
    } catch (e: SecurityException) {
      Log.w(TAG, "vibration not allowed", e)
    }
  }

  /** Los tonos tienen la duración que les da su definición: un tic de 35 ms o dos ráfagas de 100 ms. */
  private fun beep(toneType: Int) {
    val generator = tone ?: createTone() ?: return
    generator.startTone(toneType, FULL_TONE)
  }

  /** Se crea la primera vez que hace falta; si el constructor falla, no se vuelve a intentar. */
  private fun createTone(): ToneGenerator? {
    if (toneFailed) return null
    return try {
      ToneGenerator(AudioManager.STREAM_ALARM, VOLUME).also { tone = it }
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
    const val TICK_VIBRATION_MS = 40L

    /** Dos pulsos de 150 ms separados por 100 ms (el primer valor es la espera antes de empezar). */
    val STEP_VIBRATION_PATTERN = longArrayOf(0L, 150L, 100L, 150L)
    const val NO_REPEAT = -1

    /** 400 Hz + 1200 Hz durante 35 ms. */
    const val TICK_TONE = ToneGenerator.TONE_PROP_BEEP

    /** 1200 Hz puro en dos ráfagas de 100 ms: más agudo y largo que el tic. */
    const val STEP_TONE = ToneGenerator.TONE_PROP_ACK

    /** `startTone` con -1 toca el tono entero, sin recortarlo. */
    const val FULL_TONE = -1
    const val VOLUME = 80
    val ALARM_AUDIO_ATTRIBUTES: AudioAttributes =
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
  }
}
