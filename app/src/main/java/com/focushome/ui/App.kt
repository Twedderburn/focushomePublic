package com.focushome.ui

import android.app.Activity
import android.app.role.RoleManager
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focushome.AppEntry
import com.focushome.BlockReason
import com.focushome.DnsFilter
import com.focushome.FocusStore
import com.focushome.QrKey
import com.focushome.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Screen { Home, Settings, Essentials, NewKey, Emergency }

@Composable
fun FocusHomeApp(
    apps: List<AppEntry>,
    homePresses: Int,
    resumes: Int,
    blocked: String?,
    blockReason: BlockReason,
    onBlockedDone: () -> Unit,
) {
    val context = LocalContext.current
    val state by FocusStore.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(homePresses) { screen = Screen.Home }
    // 1.0 saved key images to Photos, where Gallery's QR detection could open them. Clear them out once
    // unlocked, when the paper key is at hand (or a new one can be printed from Settings).
    LaunchedEffect(state.focusOn) {
        if (state.setupDone && !state.focusOn) withContext(Dispatchers.IO) { QrKey.deleteSavedCopies(context) }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(4_000)
            message = null
        }
    }

    if (blocked != null && blockReason != BlockReason.Focus) {
        NotNowScreen(blocked, blockReason, onDone = onBlockedDone)
        return
    }
    if (!state.setupDone) {
        SetupFlow(apps, resumes)
        return
    }
    if (blocked != null && state.focusOn) {
        NotNowScreen(blocked, BlockReason.Focus, onDone = onBlockedDone)
        return
    }

    // The web filter isn't optional: until Android lets it run, it's all the home screen shows.
    var filterCheck by remember { mutableIntStateOf(0) }
    val filterRunning by DnsFilter.running.collectAsStateWithLifecycle()
    val needsConsent = remember(resumes, filterCheck, filterRunning) { !DnsFilter.hasConsent(context) }
    val privateDns = remember(resumes, filterCheck, filterRunning) { DnsFilter.privateDnsBypass(context) }
    if (needsConsent || privateDns) {
        WebFilterGate(needsConsent, privateDns, onChanged = { filterCheck++ })
        return
    }

    val now = rememberMinuteClock(resumes)
    // Settings and the app picker exist only while unlocked, so a relock mid-edit lands you on the Focus screen.
    val shown = when {
        state.focusOn && screen != Screen.Emergency -> Screen.Home
        !state.focusOn && screen == Screen.Emergency -> Screen.Home
        else -> screen
    }
    // On the home screens Back does nothing, like any launcher.
    BackHandler { screen = Screen.Home }

    val scanToUnlock: () -> Unit = {
        QrKey.scan(context) { result ->
            message = when (result) {
                is ScanResult.Code ->
                    if (!FocusStore.unlockWithKey(result.raw)) "That isn't your key code"
                    else FocusStore.state.value.relockAtWall()?.let { "Unlocked until ${formatTime(context, it)}" } ?: "Unlocked"
                ScanResult.Cancelled -> null
                is ScanResult.Failed -> result.reason
            }
        }
    }

    when (shown) {
        Screen.Home ->
            if (state.focusOn) {
                FocusScreen(state, apps, now, message, resumes, onScan = scanToUnlock, onEmergency = { screen = Screen.Emergency })
            } else {
                OpenScreen(
                    state, apps, now, message, resumes, homePresses,
                    onLock = {
                        FocusStore.enterFocus()
                        message = "Focus mode on"
                    },
                    onSettings = { screen = Screen.Settings },
                )
            }
        Screen.Settings -> SettingsScreen(
            state = state,
            apps = apps,
            resumes = resumes,
            onBack = { screen = Screen.Home },
            onEssentials = { screen = Screen.Essentials },
            onNewKey = { screen = Screen.NewKey },
        )
        Screen.Essentials -> {
            BackHandler { screen = Screen.Settings }
            EssentialsPicker(apps, state.essentials, onChange = FocusStore::setEssentials, onDone = { screen = Screen.Settings })
        }
        Screen.NewKey -> {
            BackHandler { screen = Screen.Settings }
            KeySetup(
                onDone = {
                    screen = Screen.Settings
                    message = "New key code saved"
                },
                onCancel = { screen = Screen.Settings },
            )
        }
        Screen.Emergency -> EmergencyScreen(
            onCancel = { screen = Screen.Home },
            onUnlocked = {
                screen = Screen.Home
                message = "Unlocked"
            },
        )
    }
}

/** Wall-clock time that updates on each minute boundary; also drives stats rollover and auto-relock. */
@Composable
fun rememberMinuteClock(resumes: Int): Long {
    val now by produceState(System.currentTimeMillis(), resumes) {
        value = System.currentTimeMillis()
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
            FocusStore.tick()
            value = System.currentTimeMillis()
        }
    }
    return now
}

fun clockText(context: Context, millis: Long): String =
    SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm", Locale.getDefault()).format(Date(millis))

fun formatTime(context: Context, millis: Long): String = DateFormat.getTimeFormat(context).format(Date(millis))

fun dateText(millis: Long): String = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(millis))

fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

fun unlocksText(count: Int) = when (count) {
    0 -> "no unlocks"
    1 -> "1 unlock"
    else -> "$count unlocks"
}

fun isDefaultHome(context: Context): Boolean =
    context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true

fun requestHomeIntent(context: Context): Intent {
    val roles = context.getSystemService(RoleManager::class.java)
    return if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
        roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
    } else {
        Intent(Settings.ACTION_HOME_SETTINGS)
    }
}

fun shareImage(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // ClipData carries the read grant through the chooser to whichever app is picked.
    send.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(Intent.createChooser(send, "Send your key code"))
}

/** Blocks screenshots and screen recording while this composable is shown. */
@Composable
fun SecureWindow() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
