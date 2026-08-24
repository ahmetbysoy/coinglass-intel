package com.coinglass.intel.alert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.coinglass.intel.MainActivity
import com.coinglass.intel.domain.fmtPrice
import kotlin.math.abs

object AlertNotifier {
    const val CH_ALERT = "intel.alerts"
    const val CH_FG = "intel.fg"
    const val ID_FG = 42
    private const val ID_ALERT_BASE = 1000

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Skor uyarilari", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(sound, attrs)
                enableVibration(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_FG, "Izleme servisi", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
            },
        )
    }

    fun foreground(ctx: Context, watching: Int): android.app.Notification {
        ensureChannels(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(ctx, CH_FG)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("CoinGlass Intel")
            .setContentText(if (watching == 0) "watchlist bos" else "$watching sembol izleniyor")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun scoreAlert(
        ctx: Context,
        symbol: String,
        score: Double,
        direction: String,
        price: Double,
        priority: Boolean = false,
    ) {
        ensureChannels(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open = PendingIntent.getActivity(
            ctx, symbol.hashCode(),
            Intent(ctx, MainActivity::class.java).putExtra("symbol", symbol),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle((if (priority) "★ " else "") + "$symbol  ${"%+.1f".format(score)}  $direction")
            .setContentText("fiyat ${fmtPrice(price)}  |skor|=${"%.1f".format(abs(score))}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .build()
        nm.notify(ID_ALERT_BASE + (symbol.hashCode() and 0xffff), n)
    }

    fun opportunityAlert(
        ctx: Context,
        symbol: String,
        score: Double,
        grade: String,
        price: Double,
    ) {
        ensureChannels(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open = PendingIntent.getActivity(
            ctx, symbol.hashCode(),
            Intent(ctx, MainActivity::class.java).putExtra("symbol", symbol),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("KEŞİF $grade  $symbol  " + "%+.1f".format(score))
            .setContentText("fiyat ${fmtPrice(price)}  fırsat (A/B + spoof<40 + netRR≥1.5)")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .build()
        nm.notify(ID_ALERT_BASE + (symbol.hashCode() and 0xffff), n)
    }

    fun alarmHit(ctx: Context, hit: com.coinglass.intel.domain.AlarmHit) {
        ensureChannels(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val sym = hit.alarm.symbol
        val open = PendingIntent.getActivity(
            ctx, (ID_ALARM_BASE + hit.alarm.id).toInt(),
            Intent(ctx, MainActivity::class.java).putExtra("symbol", sym),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ALARM  $sym")
            .setContentText(com.coinglass.intel.domain.AlarmEngine.line(hit))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .build()
        nm.notify(ID_ALARM_BASE + (hit.alarm.id.toInt() and 0xffff), n)
    }

    private const val ID_ALARM_BASE = 5000
}
