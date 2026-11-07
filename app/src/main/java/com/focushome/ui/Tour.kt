package com.focushome.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focushome.FocusState
import kotlin.math.roundToInt

/**
 * One stop on the tour. [target] names a control marked with [tourTarget]; when it's on screen it's
 * spotlit, otherwise the card sits in the middle. A step with [waitFor] has no Next button: it moves
 * on when that event happens (the user tries the control), and touches inside the spotlight go through.
 */
data class TourStep(
    val id: String,
    val title: String,
    val body: String,
    val target: String? = null,
    val waitFor: String? = null,
    val hint: String? = null,
)

object TourEvents {
    const val DRAWER_OPENED = "drawerOpened"
    const val DRAWER_CLOSED = "drawerClosed"
}

/** Holds the running tour and where each marked control is on screen. */
class TourController {
    var steps by mutableStateOf<List<TourStep>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    val targets = mutableStateMapOf<String, Rect>()
    var onFinish: () -> Unit = {}

    val active: Boolean get() = steps.isNotEmpty()
    val current: TourStep? get() = steps.getOrNull(index)

    fun start(steps: List<TourStep>) {
        index = 0
        this.steps = steps
    }

    fun next() = if (index < steps.lastIndex) index++ else finish()

    /** Going back over a "try it" step would leave the user stuck, so Back stops there. */
    val canGoBack: Boolean get() = index > 0 && steps[index - 1].waitFor == null

    fun back() {
        if (canGoBack) index--
    }

    fun finish() {
        steps = emptyList()
        index = 0
        onFinish()
    }

    /**
     * Something the tour may be waiting for happened. It satisfies the current step, or an upcoming
     * one when only plain steps lie in between (e.g. Back closed the drawer while reading about search).
     */
    fun event(name: String) {
        for (i in index until steps.size) {
            val step = steps[i]
            if (step.waitFor == name) {
                index = i
                next()
                return
            }
            if (step.waitFor != null) return
        }
    }
}

val LocalTour = staticCompositionLocalOf<TourController?> { null }

/** Marks a control so the tour can point at it. */
fun Modifier.tourTarget(key: String): Modifier = composed {
    val tour = LocalTour.current ?: return@composed Modifier
    DisposableEffect(tour, key) { onDispose { tour.targets.remove(key) } }
    Modifier.onGloballyPositioned { coords ->
        val bounds = coords.boundsInRoot()
        if (tour.targets[key] != bounds) tour.targets[key] = bounds
    }
}

/** The steps, written for the mode the user is in when the tour starts. */
fun buildTourSteps(state: FocusState): List<TourStep> = buildList {
    val relock = when {
        state.relockMinutes <= 0 -> "Auto-relock is off, so it stays unlocked until you lock it."
        state.relockMinutes < 60 -> "Focus mode also comes back on its own ${state.relockMinutes} minutes after you unlock."
        else -> "Focus mode also comes back on its own ${state.relockMinutes / 60} h after you unlock."
    }

    add(TourStep("welcome", "Welcome to Focus Home",
        "A one-minute tour of how everything works. You can skip it at any time, and replay it later from Settings."))
    add(TourStep("clock", "Your day at a glance",
        if (state.focusOn) "The time and date, and below them how long you've been in Focus mode today and how many times you've unlocked. Tap the time to open your clock app, or the date to open your calendar."
        else "The time and date. Below them, the pill shows you're unlocked and when Focus mode will come back. Tap the time to open your clock app, or the date to open your calendar.",
        target = "clock"))
    if (state.claudeWidget) {
        add(TourStep("claude", "Write to Claude",
            "Type what you want to work on and tap the arrow. Your message opens in the Claude app, ready to go. " +
                "In Focus mode this only works if Claude is one of your favorites.",
            target = "claude"))
    }
    add(TourStep("favorites", "Your favorites",
        "Tap an icon to open it. In Focus mode these are the only apps that open, so pick the ones you really need. " +
            "Change them in Settings › Favorites.",
        target = "favorites"))
    add(TourStep("arrange", "Arrange them your way",
        "Hold an icon until it lifts, then drag it. Rest over a spot and the other icons slide aside to make room. Let go to drop it there.",
        target = "favorites"))
    add(TourStep("folders", "Folders",
        "Drag an app onto the middle of another app. When it lights up, let go to make a folder. Drop more apps on it to add them. " +
            "Tap a folder to open it, tap its name to rename it, hold an app to reorder it, " +
            "or drag an app out past the folder's edge to take it out. Tap outside or press Back to close.",
        target = "favorites"))

    if (!state.focusOn) {
        add(TourStep("drawer", "Every app, one swipe away",
            "While unlocked, all your apps live in a drawer.",
            target = "drawer", waitFor = TourEvents.DRAWER_OPENED, hint = "Try it: tap All apps. Later you can also swipe up anywhere."))
        add(TourStep("search", "Find any app",
            "Type a few letters to filter the list. Go on the keyboard opens the first match. Starred apps are your favorites.",
            target = "search"))
        add(TourStep("close", "Back to home",
            "The drawer closes when you're done, or when you press Home.",
            target = "close", waitFor = TourEvents.DRAWER_CLOSED, hint = "Try it: tap Close or press Back."))
        add(TourStep("lock", "Lock",
            "Tap the padlock to turn on Focus mode. Only your favorites will open until you scan your key. $relock",
            target = "lock"))
        add(TourStep("settings", "Settings",
            "Choose favorites, your background and accent colours, turn on the Claude box, set auto-relock, reprint your key, " +
                "and manage app blocking and the web filter. Settings only opens while unlocked.",
            target = "settings"))
        add(TourStep("focus", "How Focus mode works",
            "In Focus mode the home screen shows only your favorites and the drawer stays locked. " +
                "To unlock, tap Scan to unlock and scan the QR key you printed. Keep it somewhere inconvenient. That's the point."))
        add(TourStep("emergency", "Lost your key?",
            "Focus mode has an emergency unlock. You wait on its screen for 15 minutes the first time each day, then 30, then 60. " +
                "Leaving the screen starts the wait over."))
    } else {
        add(TourStep("drawer", "All apps are locked",
            "In Focus mode the app drawer stays closed. Swiping up just reminds you to scan your key.",
            target = "drawer"))
        add(TourStep("scan", "Scan to unlock",
            "Scan the QR key you printed to unlock every app. Keep it somewhere inconvenient. That's the point.",
            target = "scan"))
        add(TourStep("emergency", "Lost your key?",
            "Start an emergency unlock instead. You wait on that screen for 15 minutes the first time each day, then 30, then 60. " +
                "Leaving the screen starts the wait over.",
            target = "emergency"))
        add(TourStep("unlocked", "Once you're unlocked",
            "You get a searchable drawer with every app, a padlock to lock again, and Settings for favorites, colours and more. $relock"))
    }

    add(TourStep("blocked", "Not now",
        "If an app that isn't a favorite opens in Focus mode, from a notification, a link or Recents, you'll see \"Not now\" and land back here. " +
            "Reddit and X are blocked in every mode."))
    add(TourStep("webfilter", "The web filter",
        "Focus Home filters web lookups so Reddit, X and adult sites stay blocked in every browser. " +
            "Keep it on: while it's off, your browsers are blocked too."))
    if (!state.claudeWidget) {
        add(TourStep("claudeOff", "Claude on your home screen",
            "Want to start working with Claude right from here? Turn on Settings › Claude on the home screen to add a box for writing to Claude."))
    }
    add(TourStep("done", "You're all set",
        "That's everything. Replay this tour any time from Settings › Tour."))
}

/** Dims the screen, spotlights the current step's control, and shows the step's card. */
@Composable
fun TourOverlay(tour: TourController) {
    val step = tour.current ?: return
    val density = LocalDensity.current
    val accent = Accent
    val pad = with(density) { 10.dp.toPx() }
    val hole = step.target?.let { tour.targets[it] }?.let {
        Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad)
    }
    val radius = with(density) { 22.dp.toPx() }
    val ring = with(density) { 2.dp.toPx() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = constraints.maxHeight.toFloat()
        val width = constraints.maxWidth.toFloat()

        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        ) {
            drawRect(Color.Black.copy(alpha = 0.74f))
            if (hole != null) {
                val topLeft = Offset(hole.left, hole.top)
                val size = Size(hole.width, hole.height)
                drawRoundRect(Color.Transparent, topLeft, size, CornerRadius(radius), blendMode = BlendMode.Clear)
                drawRoundRect(accent, topLeft, size, CornerRadius(radius), style = Stroke(ring))
            }
        }

        // Block touches, except inside the spotlight on a "try it" step.
        if (hole != null && step.waitFor != null) {
            Blocker(0f, 0f, width, hole.top)
            Blocker(0f, hole.bottom, width, height)
            Blocker(0f, hole.top, hole.left, hole.bottom)
            Blocker(hole.right, hole.top, width, hole.bottom)
        } else {
            Blocker(0f, 0f, width, height)
        }

        // The card goes in the larger space above or below the spotlight, or in the middle.
        val gap = with(density) { 16.dp.toPx() }
        val cardModifier = when {
            hole == null -> Modifier.align(Alignment.Center)
            height - hole.bottom >= hole.top -> Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (hole.bottom + gap).roundToInt()) }
            else -> Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, -(height - hole.top + gap).roundToInt()) }
        }
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tour card",
            modifier = cardModifier.padding(horizontal = 20.dp),
        ) { shown ->
            TourCard(tour, shown)
        }
    }
}

@Composable
private fun Blocker(left: Float, top: Float, right: Float, bottom: Float) {
    if (right <= left || bottom <= top) return
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(with(density) { (right - left).toDp() }, with(density) { (bottom - top).toDp() })
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            },
    )
}

@Composable
private fun TourCard(tour: TourController, step: TourStep) {
    val shape = RoundedCornerShape(24.dp)
    val position = tour.steps.indexOf(step)
    val isLast = position == tour.steps.lastIndex
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Card)
            .border(1.dp, Line, shape)
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
    ) {
        // Fixed height so the card doesn't jump when "Skip tour" disappears on the last step.
        Row(Modifier.heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
            Progress(position, tour.steps.size, Modifier.weight(1f))
            if (!isLast) Quiet("Skip tour", color = Faint, onClick = tour::finish)
        }
        Spacer(Modifier.height(4.dp))
        Text(step.title, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 8.dp))
        Spacer(Modifier.height(6.dp))
        Body(step.body, Modifier.padding(end = 8.dp))
        if (step.hint != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Accent))
                Spacer(Modifier.width(8.dp))
                Text(step.hint, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (tour.canGoBack && tour.current == step) Quiet("Back", onClick = tour::back) else Spacer(Modifier.width(1.dp))
            if (step.waitFor == null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Accent)
                        .clickable { if (tour.current == step) tour.next() }
                        .padding(horizontal = 22.dp, vertical = 11.dp),
                ) {
                    Text(
                        when {
                            position == 0 -> "Start tour"
                            isLast -> "Done"
                            else -> "Next"
                        },
                        color = Bg,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            } else {
                Spacer(Modifier.height(44.dp))
            }
        }
    }
}

/** A row of short bars, one per step, filled up to the current one. */
@Composable
private fun Progress(position: Int, count: Int, modifier: Modifier = Modifier) {
    Row(modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (i <= position) Accent else Line),
            )
        }
    }
}
