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
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.josueamaya.pourline.PourlineApp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.ui.scale.formatNumber
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
 * Lo arranca [BrewKeepAlive] y se detiene solo cuando [needsKeepAlive] deja de cumplirse.
 */
class BrewService : Service() {
  private val scope = MainScope()
  private var running = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (running) return START_NOT_STICKY
    running = true
    val manager = (application as PourlineApp).scaleSession
    ensureChannel(this)
    try {
      ServiceCompat.startForeground(this, NOTIFICATION_ID, build(manager.ui.value.toBrewNotice()), foregroundType())
    } catch (e: RuntimeException) {
      // Sin permiso para el tipo `connectedDevice` o fuera de primer plano: la sesión sigue, sin protección.
      Log.w(TAG, "startForeground failed", e)
      stopSelf()
      return START_NOT_STICKY
    }
    scope.launch {
      manager.ui.first { !it.needsKeepAlive() }
      stopSelf()
    }
    scope.launch {
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
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    super.onDestroy()
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

    /**
     * Arranca el servicio. Desde Android 14, el tipo `connectedDevice` exige el permiso de Bluetooth: sin él (p. ej.
     * con la báscula simulada y los permisos sin dar) no se arranca, porque `startForeground` fallaría.
     */
    fun start(context: Context) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
      ) {
        Log.w(TAG, "no BLUETOOTH_CONNECT: the session runs without a foreground service")
        return
      }
      try {
        ContextCompat.startForegroundService(context, Intent(context, BrewService::class.java))
      } catch (e: IllegalStateException) {
        // `ForegroundServiceStartNotAllowedException`: la app ya no estaba en primer plano.
        Log.w(TAG, "foreground service not allowed", e)
      }
    }

    private fun foregroundType(): Int =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0

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
