package com.focushome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import com.focushome.AppItem
import com.focushome.FocusStore
import com.focushome.FolderItem
import com.focushome.HomeLayout
import com.focushome.BlockReason
import com.focushome.ClaudeHandoff
import com.focushome.FocusState
import kotlinx.coroutines.delay

/**
 * The layout both home screens share: clock and status on top, the Claude card, the favorites grid,
 * then a footer. Swiping left anywhere calls [onSwipeLeft].
 */
@Composable
private fun HomeBody(
    state: FocusState,
    apps: List<AppEntry>,
    now: Long,
    status: String,
    message: String?,
    resumes: Int,
    onSwipeLeft: () -> Unit,
    footer: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }
    // The arranged favorites, limited to apps that are installed. Empty folders are hidden.
    val layout = state.homeLayout
    val visible = remember(layout, byPackage) {
        layout.mapNotNull { item ->
            when (item) {
                is AppItem -> item.takeIf { it.pkg in byPackage }
                is FolderItem -> item.takeIf { f -> f.apps.any { it in byPackage } }
            }
        }
    }
    val visibleKeys = remember(visible) { visible.map { it.key }.toSet() }
    // Leaving and coming back home closes any open folder.
    var openFolder by rememberSaveable(resumes) { mutableStateOf<String?>(null) }
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

    // Swipe left anywhere for all apps; not while a folder is open on top.
    Box(Modifier.fillMaxSize().onSwipeLeft { if (openFolder == null) onSwipeLeft() }) {
    Page {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(34.dp))
            HomeClock(now, status, Modifier.tourTarget("clock"))
            Spacer(Modifier.height(32.dp))

            if (state.claudeWidget) {
                ClaudePrompt(
                    resumes = resumes,
                    modifier = Modifier.tourTarget("claude"),
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
                visible.isNotEmpty() -> ArrangeableGrid(
                    keys = visible.map { it.key },
                    labels = visible.associate { item ->
                        item.key to when (item) {
                            is AppItem -> byPackage[item.pkg]?.label ?: item.pkg
                            is FolderItem -> "${item.name}, folder"
                        }
                    },
                    columns = 4,
                    // Only a single app can be dropped onto something; folders don't nest.
                    canMerge = { dragged, _ -> !dragged.startsWith("folder:") },
                    onTap = { key ->
                        when (val item = visible.firstOrNull { it.key == key }) {
                            is AppItem -> byPackage[item.pkg]?.let { Apps.launch(context, it) }
                            is FolderItem -> openFolder = item.id
                            null -> Unit
                        }
                    },
                    onMove = { key, to ->
                        // Reorder what's on screen; favorites that aren't installed keep their place at the end.
                        val moved = HomeLayout.move(visible, key, to).mapNotNull { v -> layout.firstOrNull { it.key == v.key } }
                        FocusStore.setHomeLayout(moved + layout.filter { it.key !in visibleKeys })
                    },
                    onMerge = { dragged, target ->
                        FocusStore.setHomeLayout(HomeLayout.dropOnto(layout, dragged, target, newFolderId()))
                    },
                    modifier = Modifier.tourTarget("favorites"),
                ) { key, highlighted ->
                    when (val item = visible.firstOrNull { it.key == key }) {
                        is AppItem -> byPackage[item.pkg]?.let { AppTile(it, highlighted) }
                        is FolderItem -> FolderTile(item, item.apps.mapNotNull { byPackage[it] }, highlighted)
                        null -> Unit
                    }
                }
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

    // An open folder sits over the whole home screen.
    (layout.firstOrNull { it is FolderItem && it.id == openFolder } as? FolderItem)?.let { folder ->
        FolderSheet(
            folder = folder,
            apps = byPackage,
            onRename = { FocusStore.setHomeLayout(HomeLayout.renameFolder(FocusStore.state.value.homeLayout, folder.id, it)) },
            onOpen = { Apps.launch(context, it) },
            onMove = { pkg, to ->
                // The folder grid only shows installed apps; map its index back to the full folder list.
                val shown = folder.apps.filter { it in byPackage }
                val anchor = HomeLayout.move(shown.map { AppItem(it) }, pkg, to).map { (it as AppItem).pkg }
                val full = anchor + folder.apps.filter { it !in byPackage }
                FocusStore.setHomeLayout(HomeLayout.moveInFolder(layout, folder.id, pkg, full.indexOf(pkg)))
            },
            onTakeOut = { pkg ->
                val wasLast = folder.apps.size <= 2
                FocusStore.setHomeLayout(HomeLayout.removeFromFolder(layout, folder.id, pkg))
                if (wasLast) openFolder = null
            },
            onClose = { openFolder = null },
        )
    }
    }
}

private fun newFolderId(): String = System.currentTimeMillis().toString(36)

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

    HomeBody(
        state, apps, now,
        status = "Focused ${formatDuration(state.focusMsToday(now))} today  ·  ${unlocksText(state.unlocksToday)}",
        message = hint ?: message,
        resumes = resumes,
        onSwipeLeft = explainLocked,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Quiet("Scan to unlock", Modifier.tourTarget("scan"), color = Ink, onClick = onScan)
            Quiet("Lost your key?", Modifier.tourTarget("emergency"), color = Faint, onClick = onEmergency)
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

/** Unlocked: the same home screen, plus a swipe-left drawer with every app. */
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
    // Tell a running tour when the drawer opens or closes (only on a real change, not the first composition).
    val tour = LocalTour.current
    var lastDrawer by remember { mutableStateOf(drawerOpen) }
    LaunchedEffect(drawerOpen) {
        if (drawerOpen != lastDrawer) {
            lastDrawer = drawerOpen
            tour?.event(if (drawerOpen) TourEvents.DRAWER_OPENED else TourEvents.DRAWER_CLOSED)
        }
    }

    Box(Modifier.fillMaxSize()) {
        HomeBody(
            state, apps, now,
            status = state.relockAtWall()?.let { "Unlocked until ${formatTime(context, it)}" } ?: "Unlocked",
            message = message,
            resumes = resumes,
            onSwipeLeft = { drawerOpen = true },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LockButton(onClick = onLock, modifier = Modifier.tourTarget("lock"))
                SettingsButton(onClick = onSettings, modifier = Modifier.tourTarget("settings"))
            }
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
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
