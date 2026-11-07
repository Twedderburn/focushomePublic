package com.focushome

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Enforces Focus mode beyond the home screen: when an app that isn't essential comes to the front
 * (from Recents, a notification, a link or a widget), it's sent away and the "Not now" screen shows.
 * Apps in [Apps.ALWAYS_BLOCKED] are sent away in every mode.
 * Only the package and activity name of each new window are read, never its contents.
 */
class FocusGuardService : AccessibilityService() {
    private val activityCache = HashMap<String, Boolean>()
    private var launchable: Set<String> = emptySet()
    private var alwaysAllowed: Set<String> = emptySet()
    private var refreshedAt = 0L
    private var lastBlocked = ""
    private var lastBlockedAt = 0L

    override fun onServiceConnected() = refresh()

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        // Reddit and X are blocked in every mode, even unlocked and even if marked essential.
        if (Apps.isAlwaysBlocked(pkg)) {
            if (isActivity(pkg, cls)) maybeBlock(pkg, always = true)
            return
        }

        val state = FocusStore.state.value
        if (!state.setupDone || !state.focusOn) return
        if (pkg == packageName || pkg in state.essentials) return

        if (SystemClock.elapsedRealtime() - refreshedAt > 60_000) refresh()
        // Apps without a launcher icon are system helpers (in-call screen, pickers, share sheet); never block those.
        if (pkg !in launchable || pkg in alwaysAllowed) return
        // A link opened inside an essential app shows as a browser tab activity; let it through.
        if (cls.contains("CustomTab")) return
        // Overlays, dialogs and bubbles from background apps also fire this event; only real activities count.
        if (!isActivity(pkg, cls)) return

        maybeBlock(pkg, always = false)
    }

    private fun maybeBlock(pkg: String, always: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastBlocked && now - lastBlockedAt < 1_500) return
        lastBlocked = pkg
        lastBlockedAt = now
        block(pkg, always)
    }

    override fun onInterrupt() = Unit

    private fun block(pkg: String, always: Boolean) {
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            "That app"
        }
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_BLOCKED, label)
                .putExtra(MainActivity.EXTRA_BLOCKED_ALWAYS, always),
        )
    }

    private fun isActivity(pkg: String, cls: String): Boolean = activityCache.getOrPut("$pkg/$cls") {
        try {
            packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun refresh() {
        refreshedAt = SystemClock.elapsedRealtime()
        launchable = Apps.launchablePackages(this)
        val dialer = getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        val keyboards = getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.map { it.packageName }.orEmpty()
        alwaysAllowed = ALWAYS_ALLOWED + keyboards + listOfNotNull(dialer)
    }

    companion object {
        /** Calls, alarms, contacts and Settings must keep working whatever the essentials list says. */
        private val ALWAYS_ALLOWED = setOf(
            "com.android.settings",
            "com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer",
            "com.samsung.android.incallui", "com.android.server.telecom",
            "com.samsung.android.app.contacts", "com.google.android.contacts", "com.android.contacts",
            "com.sec.android.app.clockpackage", "com.google.android.deskclock", "com.android.deskclock",
            "com.samsung.android.emergency", "com.android.emergency",
        )
    }
}

object FocusGuard {
    private fun component(context: Context) = ComponentName(context, FocusGuardService::class.java)

    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = component(context)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** Straight to this service's page where Android allows it, otherwise the Accessibility list. */
    fun openSettings(context: Context) {
        // Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS isn't in the public SDK, but Settings accepts it from the service's own app.
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, component(context).flattenToString())
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) throw IllegalStateException()
            context.startActivity(details)
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    /** Sideloaded apps may need "Allow restricted settings" from App info before Android lets the service on. */
    fun openAppInfo(context: Context) = context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}
