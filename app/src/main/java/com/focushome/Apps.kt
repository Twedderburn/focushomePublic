package com.focushome

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager

data class AppEntry(val label: String, val packageName: String, val component: ComponentName)

object Apps {
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val launchable = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(launchable, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                AppEntry(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    component = ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Packages with a launcher icon, without the cost of loading labels. */
    fun launchablePackages(context: Context): Set<String> {
        val launchable = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return context.packageManager.queryIntentActivities(launchable, 0).map { it.activityInfo.packageName }.toSet()
    }

    fun launch(context: Context, app: AppEntry): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
        true
    } catch (e: Exception) {
        false
    }

    /** Phone, Messages, Camera, Gallery, Clock, Calendar and Claude, matched to whatever is installed. */
    fun defaultEssentials(context: Context, installed: List<AppEntry>): List<String> {
        val have = installed.map { it.packageName }.toSet()
        val pm = context.packageManager
        val dialer = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        val sms = Telephony.Sms.getDefaultSmsPackage(context)
        val camera = pm.resolveActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE), 0)?.activityInfo?.packageName
        val groups = listOf(
            listOf(dialer, "com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer"),
            listOf(sms, "com.samsung.android.messaging", "com.google.android.apps.messaging", "com.android.messaging"),
            listOf("com.sec.android.app.camera", camera, "com.google.android.GoogleCamera", "com.android.camera2"),
            listOf("com.sec.android.gallery3d", "com.google.android.apps.photos"),
            listOf("com.sec.android.app.clockpackage", "com.google.android.deskclock", "com.android.deskclock"),
            listOf("com.samsung.android.calendar", "com.google.android.calendar", "com.android.calendar"),
            listOf("com.anthropic.claude"),
        )
        return groups.mapNotNull { group -> group.firstOrNull { it != null && it in have } }.distinct()
    }
}
