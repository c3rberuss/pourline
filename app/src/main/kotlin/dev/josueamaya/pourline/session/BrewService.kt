package dev.josueamaya.pourline.session

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.josueamaya.pourline.PourlineApp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.BrewConfig
import dev.josueamaya.pourline.brew.BrewPhase
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.ui.scale.formatNumber
import dev.josueamaya.pourline.ui.session.stepTypeLabel
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano (`connectedDevice`) mientras hay una preparación abierta: mantiene el proceso vivo y sin
 * congelar al salir de la app, así la conexión con la báscula, el tiempo y los avisos de etapa siguen. Su
 * notificación muestra el paso o el agua vertida con un cronómetro y abre la app al tocarla.
 *
 * La sesión sin báscula (SPEC-007) no tiene dispositivo: desde Android 14 usa el tipo `specialUse`, que no exige el permiso
 * de Bluetooth, y mientras corre retiene un `WakeLock` parcial, porque sin el tráfico de la báscula la CPU puede dormirse
 * con la pantalla apagada y retrasar el reloj de los avisos.
 *
 * Lo arranca [BrewKeepAlive] y se detiene solo cuando [needsKeepAlive] deja de cumplirse.
 */
class BrewService : Service() {
  private val scope = MainScope()
  private var running = false
  private var updates: Job? = null
  private var wakeLock: PowerManager.WakeLock? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (running) return START_NOT_STICKY
    running = true
    val manager = (application as PourlineApp).scaleSession
    val scaleless = intent?.getBooleanExtra(EXTRA_SCALELESS, false) ?: manager.ui.value.scaleless
    ensureChannel(this)
    try {
      ServiceCompat.startForeground(this, NOTIFICATION_ID, build(manager.ui.value.toBrewNotice()), foregroundType(scaleless))
    } catch (e: RuntimeException) {
      // Sin permiso para el tipo del servicio o fuera de primer plano: la sesión sigue, sin protección.
      Log.w(TAG, "startForeground failed", e)
      stopSelf()
      return START_NOT_STICKY
    }
    scope.launch {
      manager.ui.first { !it.needsKeepAlive() }
      stop()
    }
    scope.launch {
      manager.ui.map { it.scaleless && it.brew.phase is BrewPhase.Active }.distinctUntilChanged().collect { hold ->
        if (hold) acquireWakeLock() else releaseWakeLock()
      }
    }
    updates = scope.launch {
      manager.ui.map { it.toBrewNotice() }.distinctUntilChanged().conflate().collect { notice ->
        notify(build(notice))
        delay(MIN_UPDATE_MS)
      }
    }
    // Si Android mata el proceso, la sesión ya se perdió: no tiene sentido recrear el servicio.
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    scope.cancel()
    releaseWakeLock()
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    // Por si una actualización se publicó cuando el servicio ya no estaba en primer plano: esa ya no se quita sola.
    NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
    super.onDestroy()
  }

  /**
   * Corta las actualizaciones antes de parar: `stopSelf` retira la notificación al momento, pero `onDestroy` llega
   * después, y un `notify` en ese hueco la volvería a publicar como una notificación fija que ya nadie quita.
   */
  private fun stop() {
    updates?.cancel()
    releaseWakeLock()
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  /** Con tiempo máximo: la sesión dura 15 min como mucho, y un fallo no debe dejar la CPU despierta. */
  private fun acquireWakeLock() {
    if (wakeLock?.isHeld == true) return
    val power = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
      setReferenceCounted(false)
      acquire(BrewConfig().maxDurationMs + WAKE_LOCK_MARGIN_MS)
    }
  }

  private fun releaseWakeLock() {
    wakeLock?.takeIf { it.isHeld }?.release()
    wakeLock = null
  }

  private fun notify(notification: Notification) {
    val manager = NotificationManagerCompat.from(this)
    if (!manager.areNotificationsEnabled()) return
    try {
      manager.notify(NOTIFICATION_ID, notification)
    } catch (e: SecurityException) {
      Log.w(TAG, "notification not allowed", e)
    }
  }

  private fun build(notice: BrewNotice): Notification {
    val builder = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_notification_brew)
      .setContentTitle(notice.title ?: getString(R.string.session_title_free))
      .setContentText(text(notice))
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .setContentIntent(openApp())
    val t0 = notice.t0MonoMs
    if (t0 != null) {
      // El cronómetro usa la hora de pared; el inicio de la sesión está en el reloj monotónico.
      builder.setUsesChronometer(true).setShowWhen(true).setWhen(System.currentTimeMillis() - (SystemClock.elapsedRealtime() - t0))
    } else {
      builder.setShowWhen(false)
    }
    return builder.build()
  }

  private fun text(notice: BrewNotice): String {
    if (notice.scaleless) return scalelessText(notice)
    if (notice.disconnected) return getString(R.string.brew_reason_disconnected)
    return when (notice.stage) {
      BrewNoticeStage.PREP -> {
        val step = notice.prepStep ?: PrepStep.TARE
        getString(R.string.prep_step_label, step.ordinal + 1, getString(step.label()))
      }
      BrewNoticeStage.POURING -> {
        val water = weightText(notice.water ?: 0.0, notice)
        notice.targetWater?.let { getString(R.string.brew_notice_water, water, weightText(it, notice)) } ?: water
      }
      BrewNoticeStage.SAVING -> getString(R.string.brew_notice_saving)
    }
  }

  /** Sin báscula: el paso (y hasta dónde verter), nunca el agua vertida (D14). */
  private fun scalelessText(notice: BrewNotice): String {
    val step = notice.step
    return when {
      notice.stage == BrewNoticeStage.SAVING -> getString(R.string.brew_notice_saving)
      notice.stage == BrewNoticeStage.PREP -> getString(R.string.brew_notice_scaleless_ready)
      notice.stepsDone -> getString(R.string.session_steps_done)
      step != null -> {
        val label = step.name ?: getString(stepTypeLabel(step.type))
        if (step.type == StageType.POUR && step.targetWaterG != null) {
          getString(R.string.brew_notice_step_pour, step.number, step.count, label, "${formatNumber(step.targetWaterG, notice.unit)} ${notice.unit.symbol}")
        } else {
          getString(R.string.brew_notice_step, step.number, step.count, label)
        }
      }
      notice.targetWater != null -> getString(R.string.scaleless_water_goal, formatNumber(notice.targetWater, notice.unit))
      else -> getString(R.string.session_step_waiting)
    }
  }

  private fun weightText(value: Double, notice: BrewNotice) = "${formatNumber(value, notice.unit)} ${notice.unit.symbol}"

  private fun openApp(): PendingIntent? {
    val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
    return PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  }

  companion object {
    private const val TAG = "BrewService"
    private const val CHANNEL_ID = "brew_session"
    private const val NOTIFICATION_ID = 1
    private const val MIN_UPDATE_MS = 1_000L
    private const val EXTRA_SCALELESS = "scaleless"
    private const val WAKE_LOCK_TAG = "pourline:scaleless"

    /** Margen sobre los 15 min de la sesión, por si el servicio tarda en notar el final. */
    private const val WAKE_LOCK_MARGIN_MS = 60_000L

    /**
     * Arranca el servicio. Desde Android 14, el tipo `connectedDevice` exige el permiso de Bluetooth: sin él (p. ej.
     * con la báscula simulada y los permisos sin dar) no se arranca, porque `startForeground` fallaría. La sesión sin
     * báscula ([scaleless]) usa `specialUse`, que no lo exige.
     */
    fun start(context: Context, scaleless: Boolean = false) {
      if (!scaleless && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
      ) {
        Log.w(TAG, "no BLUETOOTH_CONNECT: the session runs without a foreground service")
        return
      }
      try {
        ContextCompat.startForegroundService(context, Intent(context, BrewService::class.java).putExtra(EXTRA_SCALELESS, scaleless))
      } catch (e: IllegalStateException) {
        // `ForegroundServiceStartNotAllowedException`: la app ya no estaba en primer plano.
        Log.w(TAG, "foreground service not allowed", e)
      }
    }

    /** `specialUse` en Android 14 o superior para la sesión sin báscula; `connectedDevice` en el resto (desde Android 10). */
    private fun foregroundType(scaleless: Boolean): Int = when {
      scaleless && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
      else -> 0
    }

    private fun ensureChannel(context: Context) {
      val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
        .setName(context.getString(R.string.brew_notice_channel))
        .setDescription(context.getString(R.string.brew_notice_channel_description))
        .setShowBadge(false)
        .build()
      NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun PrepStep.label(): Int = when (this) {
      PrepStep.TARE -> R.string.prep_step_tare
      PrepStep.DOSE -> R.string.prep_step_dose
      PrepStep.TARE_FOR_POUR -> R.string.prep_step_tare_pour
      PrepStep.POUR -> R.string.prep_step_pour
    }
  }
}
