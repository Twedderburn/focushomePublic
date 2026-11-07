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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
            Section("Favorites") {
                Body("${state.essentials.count { it in installed }} favorites, the only apps open in Focus mode.")
                Spacer(Modifier.height(12.dp))
                Action("Choose apps", onClick = onEssentials)
            }

            Section("Claude on the home screen") {
                Body("A box at the top of the home screen where you can write a message and send it to the Claude app.")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Off", selected = !state.claudeWidget, modifier = Modifier.weight(1f)) { FocusStore.setClaudeWidget(false) }
                    Chip("On", selected = state.claudeWidget, modifier = Modifier.weight(1f)) { FocusStore.setClaudeWidget(true) }
                }
            }

            Section("Colours") {
                Body("A background theme for every screen, and one accent colour for the clock, buttons and highlights.")
                Spacer(Modifier.height(16.dp))
                Text("Background", color = Dim, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                BackgroundPicker(selected = BackgroundChoice.of(state.background), onPick = { FocusStore.setBackground(it.name) })
                Spacer(Modifier.height(18.dp))
                Text("Accent", color = Dim, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                AccentPicker(selected = AccentChoice.of(state.accent), onPick = { FocusStore.setAccent(it.name) })
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

            Section("Tour") {
                Body("A quick walkthrough of the home screen, Focus mode and everything else.")
                Spacer(Modifier.height(12.dp))
                Action("Take the tour") {
                    FocusStore.setTourDone(false)
                    onBack()
                }
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

/**
 * A row of rounded-square swatches, each a tiny preview of its theme: the background with a card
 * strip and an accent dot. The picked one gets a ring and its name shows underneath.
 */
@Composable
private fun BackgroundPicker(selected: BackgroundChoice, onPick: (BackgroundChoice) -> Unit) {
    val outer = RoundedCornerShape(16.dp)
    val inner = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        BackgroundChoice.entries.forEach { choice ->
            val isSelected = choice == selected
            Box(
                Modifier
                    .size(56.dp)
                    .clip(outer)
                    .then(if (isSelected) Modifier.border(2.dp, Ink, outer) else Modifier)
                    .clickable { onPick(choice) }
                    .padding(if (isSelected) 5.dp else 2.dp)
                    .clip(inner)
                    .background(choice.bg)
                    // A light edge so Black still reads as a swatch on a black page.
                    .border(1.dp, choice.ink.copy(alpha = 0.18f), inner),
            ) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 6.dp, vertical = 7.dp)
                        .fillMaxWidth()
                        .height(9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(choice.card),
                )
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Accent),
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Body(selected.label, color = Ink)
}

/** A row of colour swatches; the picked one gets a ring and its name shows underneath. */
@Composable
private fun AccentPicker(selected: AccentChoice, onPick: (AccentChoice) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AccentChoice.entries.forEach { choice ->
            val isSelected = choice == selected
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(if (isSelected) Modifier.border(2.dp, Ink, CircleShape) else Modifier)
                    .clickable { onPick(choice) }
                    .padding(if (isSelected) 5.dp else 2.dp)
                    .clip(CircleShape)
                    .background(choice.color),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Body(selected.label, color = Ink)
}

@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Text(
        label,
        modifier
            .then(if (selected) Modifier.background(Accent, shape) else Modifier.border(1.dp, Faint, shape))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        color = if (selected) Bg else Ink,
        fontSize = 15.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
