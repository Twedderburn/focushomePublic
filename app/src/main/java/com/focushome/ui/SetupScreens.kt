package com.focushome.ui

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focushome.AppEntry
import com.focushome.Apps
import com.focushome.FocusGuard
import com.focushome.FocusStore
import com.focushome.Notifications
import com.focushome.QrKey
import com.focushome.ScanResult

/** First run: welcome, key code, essential apps, home-screen role, then start. */
@Composable
fun SetupFlow(apps: List<AppEntry>, resumes: Int) {
    val context = LocalContext.current
    val state by FocusStore.state.collectAsStateWithLifecycle()
    val step = state.setupStep
    var seeded by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = step > 0) { FocusStore.setSetupStep(step - 1) }

    when (step) {
        0 -> Welcome(onNext = { FocusStore.setSetupStep(1) })
        1 -> KeySetup(onDone = { FocusStore.setSetupStep(2) }, onCancel = null)
        2 -> {
            LaunchedEffect(apps) {
                if (!seeded && apps.isNotEmpty()) {
                    if (state.essentials.isEmpty()) FocusStore.setEssentials(Apps.defaultEssentials(context, apps))
                    seeded = true
                }
            }
            EssentialsPicker(
                apps, state.essentials, onChange = FocusStore::setEssentials,
                onDone = { FocusStore.setSetupStep(3) }, doneLabel = "Continue",
            )
        }
        3 -> HomeAndAlerts(resumes, onNext = { FocusStore.setSetupStep(4) })
        else -> Ready(onStart = { FocusStore.finishSetup(startFocus = true) }, onLater = { FocusStore.finishSetup(startFocus = false) })
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    Page {
        Spacer(Modifier.height(72.dp))
        Text("Focus Home", color = Ink, fontSize = 44.sp, fontWeight = FontWeight.Thin)
        Spacer(Modifier.height(12.dp))
        Body("A home screen with only the apps you need.", color = Ink)
        Spacer(Modifier.height(28.dp))
        listOf(
            "Focus mode shows the time and your favorite apps. Nothing else.",
            "Scan your QR key to unlock everything. Tap Lock to go back to Focus.",
            "After you unlock, Focus mode turns itself back on after 30 minutes.",
            "Lost your key? An emergency unlock starts at 15 minutes and gets longer each time you use it in a day.",
        ).forEach {
            Row(Modifier.padding(vertical = 7.dp)) {
                Body("—", Modifier.width(24.dp), color = Faint)
                Body(it)
            }
        }
        Spacer(Modifier.weight(1f))
        Action("Get started", primary = true, onClick = onNext)
        Spacer(Modifier.height(20.dp))
    }
}

/** Choosing the key: a fresh printable code, or any QR code the user already has. */
@Composable
fun KeySetup(onDone: () -> Unit, onCancel: (() -> Unit)?) {
    val context = LocalContext.current
    var token by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    token?.let {
        GeneratedKey(it, onDone = onDone, onBack = { token = null })
        return
    }

    Page {
        Spacer(Modifier.height(56.dp))
        Heading("Your key code")
        Spacer(Modifier.height(12.dp))
        Body(
            "Focus mode turns on and off only when you scan this code. Keep it somewhere inconvenient, " +
                "like another room or a drawer, so unlocking takes a little effort.",
        )
        Spacer(Modifier.height(32.dp))
        Action("Make a new code to print", primary = true) { token = QrKey.newToken() }
        Spacer(Modifier.height(12.dp))
        Action("Use a QR code I already have") {
            error = null
            QrKey.scan(context) { result ->
                when (result) {
                    is ScanResult.Code -> {
                        FocusStore.setKey(result.raw, generated = false)
                        onDone()
                    }
                    ScanResult.Cancelled -> Unit
                    is ScanResult.Failed -> error = result.reason
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Body(
            "Any QR code works, like a sticker or the code on a box. From then on you'll need that exact code.",
            color = Faint,
        )
        error?.let {
            Spacer(Modifier.height(12.dp))
            Body(it, color = Ink)
        }
        Spacer(Modifier.weight(1f))
        onCancel?.let { Quiet("Cancel", onClick = it) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun GeneratedKey(token: String, onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val content = remember(token) { QrKey.contentFor(token) }
    val bitmap = remember(content) { QrKey.render(content) }
    // The paper is the key, so nothing goes to Photos, and screenshots of this screen are blocked.
    var handedOff by rememberSaveable { mutableStateOf(false) }
    SecureWindow()

    Page {
        Spacer(Modifier.height(28.dp))
        Heading("Print this code")
        Spacer(Modifier.height(8.dp))
        Body(
            "Print it, or send it to another device to print. No copy is kept in your photos and screenshots " +
                "are blocked, so the paper is your key. Keep it away from where you use your phone.",
        )
        Spacer(Modifier.height(20.dp))
        Image(
            bitmap.asImageBitmap(),
            contentDescription = "Your key code",
            modifier = Modifier
                .weight(1f, fill = false)
                .aspectRatio(bitmap.width / bitmap.height.toFloat(), matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(12.dp))
                .align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(20.dp))
        KeyHandoff(bitmap, primary = !handedOff, onHandedOff = { handedOff = true })
        Spacer(Modifier.height(10.dp))
        Action("Use this code", primary = handedOff, enabled = handedOff) {
            FocusStore.setKey(content, generated = true)
            onDone()
        }
        Quiet("Back", onClick = onBack)
        Spacer(Modifier.height(4.dp))
    }
}

/** Print and Share buttons for a key card. Shared by setup and Settings. */
@Composable
fun KeyHandoff(bitmap: Bitmap, primary: Boolean = false, onHandedOff: () -> Unit = {}) {
    val context = LocalContext.current
    Action("Print", primary = primary) {
        context.findActivity()?.let { QrKey.print(it, bitmap) }
        onHandedOff()
    }
    Spacer(Modifier.height(10.dp))
    Action("Send to another device") {
        shareImage(context, QrKey.shareFile(context, bitmap))
        onHandedOff()
    }
}

/** The allowlist. Shown during setup and from Settings (which is only reachable while unlocked). */
@Composable
fun EssentialsPicker(
    apps: List<AppEntry>,
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    onDone: () -> Unit,
    doneLabel: String = "Done",
) {
    val installed = remember(apps) { apps.map { it.packageName }.toSet() }
    Page {
        Spacer(Modifier.height(40.dp))
        Heading("Favorites")
        Spacer(Modifier.height(8.dp))
        Body("These are the only apps on your Focus screen. Choose the ones you really need.")
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(apps, key = { it.packageName }) { app ->
                val on = app.packageName in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onChange(if (on) selected - app.packageName else selected + app.packageName) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (on) "●" else "○", Modifier.width(34.dp), color = if (on) Ink else Faint, fontSize = 18.sp)
                    Text(app.label, color = if (on) Ink else Dim, fontSize = 19.sp)
                }
            }
        }
        Body("${selected.count { it in installed }} chosen", Modifier.padding(vertical = 10.dp), color = Faint)
        Action(doneLabel, primary = true, onClick = onDone)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HomeAndAlerts(resumes: Int, onNext: () -> Unit) {
    val context = LocalContext.current
    var recheck by remember { mutableIntStateOf(0) }
    val isHome = remember(resumes, recheck) { isDefaultHome(context) }
    val canNotify = remember(resumes, recheck) { Notifications.canPost(context) }
    val homeSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { recheck++ }
    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Android stops showing the dialog after it's been declined; fall back to the system picker.
        if (!isDefaultHome(context)) homeSettings.launch(android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
        recheck++
    }
    val notifyRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { recheck++ }
    val guardOn = remember(resumes, recheck) { FocusGuard.isEnabled(context) }

    Page {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(56.dp))
            Heading("Make it your home screen")
            Spacer(Modifier.height(12.dp))
            Body("Focus Home only works if it's your default home app, so the Home button always brings you back here.")
            Spacer(Modifier.height(20.dp))
            if (isHome) {
                Body("✓  Focus Home is your home screen", color = Ink)
            } else {
                Action("Set as home screen", primary = true) { roleRequest.launch(requestHomeIntent(context)) }
            }

            Spacer(Modifier.height(36.dp))
            Heading("Block other apps")
            Spacer(Modifier.height(12.dp))
            GuardSetup(guardOn)

            Spacer(Modifier.height(36.dp))
            Heading("Relock alerts")
            Spacer(Modifier.height(12.dp))
            Body("When your unlock time runs out, Focus Home can send a notification. It doesn't send any others.")
            Spacer(Modifier.height(20.dp))
            if (canNotify) {
                Body("✓  Notifications allowed", color = Ink)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Action("Allow notifications") { notifyRequest.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
            Spacer(Modifier.height(24.dp))
        }
        Action("Continue", primary = isHome, onClick = onNext)
        if (!isHome) Body("You can do this later in Settings.", Modifier.padding(top = 8.dp), color = Faint)
        Spacer(Modifier.height(20.dp))
    }
}

/** Explains and turns on the accessibility service. Shared by setup and Settings. */
@Composable
fun GuardSetup(guardOn: Boolean) {
    val context = LocalContext.current
    if (guardOn) {
        Body(
            "✓  App blocking is on. In Focus mode, other apps close as soon as they open, even from Recents or notifications.",
            color = Ink,
        )
        return
    }
    Body(
        "Without this, apps left in Recents and links in notifications get around Focus mode. " +
            "Android lists it under Accessibility. It only checks which app is on screen, never what's in it.",
    )
    Spacer(Modifier.height(20.dp))
    Action("Turn on app blocking", primary = true) { FocusGuard.openSettings(context) }
    Spacer(Modifier.height(10.dp))
    Body("Turn on Focus Home app blocking, then come back here.", color = Faint)
    Spacer(Modifier.height(6.dp))
    Body("If Android says the setting is restricted:", color = Faint)
    Quiet("Open App info › ⋮ › Allow restricted settings", color = Dim) { FocusGuard.openAppInfo(context) }
}

@Composable
private fun Ready(onStart: () -> Unit, onLater: () -> Unit) {
    Page {
        Spacer(Modifier.height(72.dp))
        Heading("You're set")
        Spacer(Modifier.height(12.dp))
        Body(
            "Turn Focus mode on now? From here on, only your key code unlocks it. " +
                "Tap Lock to turn it back on, or let auto-relock do it for you.",
        )
        Spacer(Modifier.weight(1f))
        Action("Start Focus mode", primary = true, onClick = onStart)
        Spacer(Modifier.height(12.dp))
        Action("Not yet, stay unlocked", onClick = onLater)
        Spacer(Modifier.height(20.dp))
    }
}
