package com.focushome.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focushome.AppEntry
import com.focushome.DnsFilter
import com.focushome.FocusGuard
import com.focushome.FocusState
import com.focushome.FocusStore
import com.focushome.Notifications
import com.focushome.QrKey

private val relockChoices = listOf(0, 15, 30, 60, 120)

@Composable
fun SettingsScreen(
    state: FocusState,
    apps: List<AppEntry>,
    resumes: Int,
    onBack: () -> Unit,
    onEssentials: () -> Unit,
    onNewKey: () -> Unit,
) {
    val context = LocalContext.current
    var recheck by remember { mutableIntStateOf(0) }
    val isHome = remember(resumes, recheck) { isDefaultHome(context) }
    val canNotify = remember(resumes, recheck) { Notifications.canPost(context) }
    val guardOn = remember(resumes, recheck) { FocusGuard.isEnabled(context) }
    val filterRunning by DnsFilter.running.collectAsStateWithLifecycle()
    val deviceOwner = remember(resumes) { DnsFilter.isDeviceOwner(context) }
    val homeSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { recheck++ }
    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (!isDefaultHome(context)) homeSettings.launch(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
        recheck++
    }
    val notifyRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { recheck++ }
    val installed = remember(apps) { apps.map { it.packageName }.toSet() }

    Page {
        Quiet("‹  Back", Modifier.padding(top = 12.dp), onClick = onBack)
        Heading("Settings")
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Section("Essential apps") {
                Body("${state.essentials.count { it in installed }} apps on your Focus screen.")
                Spacer(Modifier.height(12.dp))
                Action("Choose apps", onClick = onEssentials)
            }

            Section("Auto-relock") {
                Body("Focus mode turns itself back on this long after you unlock.")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    relockChoices.forEach { minutes ->
                        Chip(
                            label = if (minutes == 0) "Off" else if (minutes < 60) "${minutes}m" else "${minutes / 60}h",
                            selected = state.relockMinutes == minutes,
                            modifier = Modifier.weight(1f),
                        ) { FocusStore.setRelockMinutes(minutes) }
                    }
                }
            }

            Section("Key code") {
                Body("Replacing your key means the old code stops working.")
                Spacer(Modifier.height(12.dp))
                state.keyContent?.let { content ->
                    val bitmap = remember(content) { QrKey.render(content) }
                    KeyHandoff(bitmap)
                    Spacer(Modifier.height(10.dp))
                }
                Action("Replace key code", onClick = onNewKey)
            }

            Section("Home screen") {
                if (isHome) {
                    Body("✓  Focus Home is your home screen.", color = Ink)
                } else {
                    Body("Focus Home isn't your default home app, so the Home button skips it.")
                    Spacer(Modifier.height(12.dp))
                    Action("Set as home screen", primary = true) { roleRequest.launch(requestHomeIntent(context)) }
                }
            }

            Section("App blocking") {
                GuardSetup(guardOn)
            }

            Section("Web filter") {
                WebFilterSection(filterRunning, deviceOwner)
            }

            if (!canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Section("Relock alerts") {
                    Body("Let Focus Home tell you when your unlock time runs out.")
                    Spacer(Modifier.height(12.dp))
                    Action("Allow notifications") { notifyRequest.launch(Manifest.permission.POST_NOTIFICATIONS) }
                }
            }

            Section("What this can't stop") {
                Body(
                    if (guardOn) {
                        "Android Settings is left alone, so it can still switch off app blocking or pick another home app. " +
                            "Disconnecting the web filter there only blocks your browsers until you turn it back on. " +
                            "Focus Home adds friction; it isn't a lock."
                    } else {
                        "Without app blocking, apps already open in Recents and links in notifications get around Focus mode, " +
                            "and Android Settings can pick another home app. Focus Home adds friction; it isn't a lock."
                    },
                    color = Faint,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(22.dp))
    HorizontalDivider(color = Line)
    Spacer(Modifier.height(18.dp))
    Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(6.dp))
    content()
}

@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Text(
        label,
        modifier
            .then(if (selected) Modifier.background(Ink, shape) else Modifier.border(1.dp, Faint, shape))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        color = if (selected) Color.Black else Ink,
        fontSize = 15.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
