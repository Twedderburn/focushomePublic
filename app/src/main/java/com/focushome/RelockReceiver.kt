package com.focushome

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Fired by the auto-relock alarm: turns Focus mode back on and pulls the home screen forward. */
class RelockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val wasUnlocked = !FocusStore.state.value.focusOn
        FocusStore.tick()
        if (!wasUnlocked || !FocusStore.state.value.focusOn) return

        // Android may refuse to start an activity from the background; the notification covers that case.
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
        Notifications.relocked(context)
    }
}

object Notifications {
    private const val CHANNEL = "relock"
    private const val RELOCK_ID = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Auto-relock", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Tells you when Focus mode turns itself back on"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun relocked(context: Context) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Focus mode is back on")
            .setContentText("Your unlock time is up. Head home.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60_000L)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(RELOCK_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    fun clearRelocked(context: Context) = NotificationManagerCompat.from(context).cancel(RELOCK_ID)
}
