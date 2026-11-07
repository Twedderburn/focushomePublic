package com.focushome

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

enum class ClaudeHandoff { Shared, CopiedAndOpened, NotInstalled }

data class AppEntry(val label: String, val packageName: String, val component: ComponentName)

object Apps {
    /**
     * Apps that are blocked all the time, in Focus mode and unlocked, whatever the essentials list says.
     * They're hidden from every app list, so they can't be picked as essential or launched from here.
     */
    val ALWAYS_BLOCKED = setOf(
        "com.reddit.frontpage", // Reddit
        "com.twitter.android", // X
        "com.twitter.android.lite", // X Lite
    )

    fun isAlwaysBlocked(pkg: String) = pkg in ALWAYS_BLOCKED

    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val launchable = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(launchable, 0)
            .filter { it.activityInfo.packageName != context.packageName && !isAlwaysBlocked(it.activityInfo.packageName) }
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

    /** Apps that open ordinary web links: the browsers. */
    fun browserPackages(context: Context): Set<String> {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        @Suppress("DEPRECATION")
        return context.packageManager.queryIntentActivities(view, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .toSet()
    }

    fun launch(context: Context, app: AppEntry): Boolean = if (isAlwaysBlocked(app.packageName)) false else try {
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

    /**
     * The app's icon as a full square bitmap, or null if it can't be loaded. Call off the main thread.
     * Adaptive icons are drawn without the system mask (the UI clips them to a rounded square);
     * older icons are centred on a light tile so they fill the same shape.
     */
    fun icon(context: Context, packageName: String, sizePx: Int): ImageBitmap? = try {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (drawable is AdaptiveIconDrawable) {
            // Layers are 108dp with the middle 72dp visible, so draw them 1.5x and centred.
            val inset = sizePx / 4
            listOfNotNull(drawable.background, drawable.foreground).forEach {
                it.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
                it.draw(canvas)
            }
        } else {
            canvas.drawColor(LEGACY_TILE)
            val pad = sizePx / 8
            canvas.drawBitmap(drawable.toBitmap(sizePx - 2 * pad, sizePx - 2 * pad), pad.toFloat(), pad.toFloat(), null)
        }
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
    }

    private const val LEGACY_TILE = 0xFFEDE6DE.toInt()

    /**
     * Hands [text] to the Claude app as a new message. Uses Claude's share target when it has one;
     * otherwise copies the text and opens Claude so it can be pasted.
     */
    fun sendToClaude(context: Context, text: String): ClaudeHandoff {
        val pm = context.packageManager
        val share = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .setPackage(CLAUDE)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        @Suppress("DEPRECATION")
        if (pm.queryIntentActivities(share, 0).isNotEmpty()) {
            try {
                context.startActivity(share)
                return ClaudeHandoff.Shared
            } catch (_: Exception) {
            }
        }
        val launch = pm.getLaunchIntentForPackage(CLAUDE) ?: return ClaudeHandoff.NotInstalled
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Message for Claude", text))
        return try {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ClaudeHandoff.CopiedAndOpened
        } catch (_: Exception) {
            ClaudeHandoff.NotInstalled
        }
    }

    const val CLAUDE = "com.anthropic.claude"

    /** Opens the phone's clock app (its alarms screen), falling back to launching a known clock app. */
    fun openClock(context: Context): Boolean {
        val show = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(show)
            return true
        } catch (_: Exception) {
        }
        val launch = CLOCKS.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) } ?: return false
        return try {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Opens the phone's calendar app, on today. */
    fun openCalendar(context: Context): Boolean {
        val attempts = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),
            Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(System.currentTimeMillis().toString()).build()),
        )
        for (intent in attempts) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    private val CLOCKS = listOf("com.sec.android.app.clockpackage", "com.google.android.deskclock", "com.android.deskclock")

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
            listOf(CLAUDE),
        )
        return groups.mapNotNull { group -> group.firstOrNull { it != null && it in have } }.distinct()
    }
}
