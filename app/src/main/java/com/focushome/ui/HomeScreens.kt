package com.focushome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focushome.AppEntry
import com.focushome.Apps
import com.focushome.BlockReason
import com.focushome.FocusState
import kotlinx.coroutines.delay

/** Focus mode: the time, today's stats, and the essential apps as plain text. */
@Composable
fun FocusScreen(
    state: FocusState,
    apps: List<AppEntry>,
    now: Long,
    message: String?,
    onScan: () -> Unit,
    onEmergency: () -> Unit,
) {
    val context = LocalContext.current
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }
    val essentials = state.essentials.mapNotNull { byPackage[it] }

    Page {
        Spacer(Modifier.height(56.dp))
        Text(clockText(context, now), color = Ink, fontSize = 88.sp, fontWeight = FontWeight.Thin, lineHeight = 92.sp)
        Text(dateText(now), color = Dim, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "Focused ${formatDuration(state.focusMsToday(now))} today  ·  ${unlocksText(state.unlocksToday)}",
            color = Faint,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(40.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(essentials, key = { it.packageName }) { app ->
                Text(
                    app.label,
                    Modifier
                        .fillMaxWidth()
                        .clickable { Apps.launch(context, app) }
                        .padding(vertical = 12.dp),
                    color = Ink,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light,
                )
            }
            // An empty list means the app list hasn't loaded yet; only complain once it has.
            if (essentials.isEmpty() && apps.isNotEmpty()) {
                item { Body("No essential apps yet. Unlock to choose some.") }
            }
        }

        if (message != null) Body(message, Modifier.padding(bottom = 4.dp), color = Ink)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Quiet("Scan to unlock", color = Ink, onClick = onScan)
            Quiet("Lost your key?", color = Faint, onClick = onEmergency)
        }
    }
}

/** Shown for a moment when the app-blocking service sends a non-essential (or always-blocked) app away. */
@Composable
fun NotNowScreen(label: String, reason: BlockReason, onDone: () -> Unit) {
    LaunchedEffect(label) {
        delay(2_500)
        onDone()
    }
    BackHandler(onBack = onDone)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(horizontal = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Not now", color = Ink, fontSize = 52.sp, fontWeight = FontWeight.Thin)
            Spacer(Modifier.height(14.dp))
            when (reason) {
                BlockReason.Always -> Body("$label is always blocked.", color = Dim)
                BlockReason.WebFilter -> {
                    Body("$label is blocked while the web filter is off.", color = Dim)
                    Spacer(Modifier.height(6.dp))
                    Body("Turn the web filter back on to use it.", color = Faint)
                }
                BlockReason.Focus -> {
                    Body("$label is blocked in Focus mode.", color = Dim)
                    Spacer(Modifier.height(6.dp))
                    Body("Scan your key to unlock.", color = Faint)
                }
            }
        }
    }
}

/** Unlocked: every app, searchable, still text-only. */
@Composable
fun OpenScreen(
    state: FocusState,
    apps: List<AppEntry>,
    now: Long,
    message: String?,
    resumes: Int,
    onLock: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    // Coming back home starts with an empty search.
    var query by rememberSaveable(resumes) { mutableStateOf("") }
    val shown = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    val open: (AppEntry) -> Unit = { app ->
        focusManager.clearFocus()
        Apps.launch(context, app)
    }

    Page {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(clockText(context, now), color = Ink, fontSize = 44.sp, fontWeight = FontWeight.Thin)
            Spacer(Modifier.width(14.dp))
            Text(
                state.relockAtWall()?.let { "Focus returns at ${formatTime(context, it)}" } ?: "Unlocked",
                Modifier.padding(bottom = 9.dp),
                color = Faint,
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.height(18.dp))

        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 18.sp),
            cursorBrush = SolidColor(Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { shown.firstOrNull()?.let(open) }),
            decorationBox = { field ->
                Box(Modifier.padding(vertical = 10.dp)) {
                    if (query.isEmpty()) Text("Search apps", color = Faint, fontSize = 18.sp)
                    field()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider(color = Line)
        Spacer(Modifier.height(6.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.packageName }) { app ->
                Text(
                    app.label,
                    Modifier
                        .fillMaxWidth()
                        .clickable { open(app) }
                        .padding(vertical = 11.dp),
                    color = Ink,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Light,
                )
            }
        }

        if (message != null) Body(message, Modifier.padding(top = 4.dp), color = Ink)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Quiet("Lock", color = Ink, onClick = onLock)
            Quiet("Settings", onClick = onSettings)
        }
    }
}
