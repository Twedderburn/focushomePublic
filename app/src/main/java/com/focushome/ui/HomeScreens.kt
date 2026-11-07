package com.focushome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focushome.AppEntry
import com.focushome.Apps
import com.focushome.BlockReason
import com.focushome.ClaudeHandoff
import com.focushome.FocusState
import kotlinx.coroutines.delay

/**
 * The layout both home screens share: clock and status on top, the Claude card, the favorites grid,
 * then a footer. Swiping up calls [onSwipeUp].
 */
@Composable
private fun HomeLayout(
    state: FocusState,
    apps: List<AppEntry>,
    now: Long,
    status: String,
    message: String?,
    resumes: Int,
    onSwipeUp: () -> Unit,
    footer: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }
    val favorites = state.essentials.mapNotNull { byPackage[it] }
    val claudeInstalled = Apps.CLAUDE in byPackage
    // Claude is blocked in Focus mode unless it's one of the essentials.
    val claudeHint = when {
        !claudeInstalled -> "Install the Claude app to write to Claude from here"
        state.focusOn && Apps.CLAUDE !in state.essentials -> "Add Claude to your favorites to use this in Focus mode"
        else -> null
    }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(note) {
        if (note != null) {
            delay(4_000)
            note = null
        }
    }

    Page {
        Column(
            Modifier
                .weight(1f)
                .onSwipeUp(onSwipeUp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(44.dp))
            HomeClock(now, status)
            Spacer(Modifier.height(32.dp))

            if (state.claudeWidget) {
                ClaudePrompt(
                    resumes = resumes,
                    disabledHint = claudeHint,
                    onSend = { text ->
                        note = when (Apps.sendToClaude(context, text)) {
                            ClaudeHandoff.Shared -> null
                            ClaudeHandoff.CopiedAndOpened -> "Copied. Paste it into Claude."
                            ClaudeHandoff.NotInstalled -> "Couldn't open Claude"
                        }
                    },
                )
                Spacer(Modifier.height(32.dp))
            }

            SectionLabel("Favorites", Modifier.padding(start = 4.dp, bottom = 6.dp))
            when {
                favorites.isNotEmpty() -> FavoritesGrid(favorites, onOpen = { Apps.launch(context, it) })
                // An empty list means the app list hasn't loaded yet; only complain once it has.
                apps.isNotEmpty() -> Body(
                    if (state.focusOn) "No favorites yet. Unlock to choose some." else "No favorites yet. Choose some in Settings.",
                    Modifier.padding(start = 4.dp, top = 8.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        (note ?: message)?.let { Body(it, Modifier.padding(bottom = 4.dp), color = Ink) }
        footer()
    }
}

/** Focus mode: clock, stats, Claude and favorites. All apps stay hidden until you scan your key. */
@Composable
fun FocusScreen(
    state: FocusState,
    apps: List<AppEntry>,
    now: Long,
    message: String?,
    resumes: Int,
    onScan: () -> Unit,
    onEmergency: () -> Unit,
) {
    var hint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hint) {
        if (hint != null) {
            delay(3_000)
            hint = null
        }
    }
    val explainLocked = { hint = "All apps open once you scan your key" }

    HomeLayout(
        state, apps, now,
        status = "Focused ${formatDuration(state.focusMsToday(now))} today  ·  ${unlocksText(state.unlocksToday)}",
        message = hint ?: message,
        resumes = resumes,
        onSwipeUp = explainLocked,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Quiet("Scan to unlock", color = Ink, onClick = onScan)
            DrawerHandle("Locked", onClick = explainLocked)
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
            .background(Bg)
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

/** Unlocked: the same home screen, plus a swipe-up drawer with every app. */
@Composable
fun OpenScreen(
    state: FocusState,
    apps: List<AppEntry>,
    now: Long,
    message: String?,
    resumes: Int,
    homePresses: Int,
    onLock: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    // Leaving and coming back home, or pressing Home, closes the drawer.
    var drawerOpen by rememberSaveable(resumes, homePresses) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        HomeLayout(
            state, apps, now,
            status = state.relockAtWall()?.let { "Unlocked until ${formatTime(context, it)}" } ?: "Unlocked",
            message = message,
            resumes = resumes,
            onSwipeUp = { drawerOpen = true },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LockButton(onClick = onLock)
                DrawerHandle("All apps", onClick = { drawerOpen = true })
                SettingsButton(onClick = onSettings)
            }
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            AllAppsDrawer(
                apps = apps,
                favorites = state.essentials.toSet(),
                onOpen = { Apps.launch(context, it) },
                onClose = { drawerOpen = false },
            )
        }
    }
}
