package com.focushome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.focushome.AppEntry
import com.focushome.FolderItem
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** How long the dragged icon has to rest over a new spot before the others make room. */
private const val REORDER_DWELL_MS = 220L

private val TileIcon = 56.dp
private val TileHeight = 106.dp

/**
 * A grid whose tiles can be held and dragged. Tap a tile to open it. Hold it to pick it up, then:
 * rest over another spot and the others slide aside to make room ([onMove]); hover over the middle
 * of a tile that [canMerge] allows and it lights up, so letting go drops onto it ([onMerge]);
 * with [onDropOutside], letting go well outside the grid hands the tile out.
 */
@Composable
fun ArrangeableGrid(
    keys: List<String>,
    labels: Map<String, String>,
    columns: Int,
    canMerge: (dragged: String, target: String) -> Boolean,
    onTap: (String) -> Unit,
    onMove: (key: String, toIndex: Int) -> Unit,
    onMerge: (dragged: String, target: String) -> Unit,
    modifier: Modifier = Modifier,
    onDropOutside: ((String) -> Unit)? = null,
    tile: @Composable (key: String, mergeTarget: Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val cellH = with(density) { TileHeight.toPx() }

    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragStart by remember { mutableStateOf(0) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var preview by remember { mutableStateOf(keys) }
    var mergeTarget by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Int?>(null) }
    // Bumped whenever the finger travels; the reorder only happens once it has rested for the dwell time.
    var restToken by remember { mutableStateOf(0) }
    var pressed by remember { mutableStateOf<String?>(null) }
    // After a drop, keep showing the previewed order until the saved layout comes back, so nothing jumps.
    var settling by remember { mutableStateOf(false) }
    LaunchedEffect(keys) { settling = false }

    val order = if (dragKey != null || settling) preview else keys

    // Make room only once the icon has rested over a spot for a moment.
    LaunchedEffect(pending, restToken) {
        val to = pending ?: return@LaunchedEffect
        delay(REORDER_DWELL_MS)
        val k = dragKey ?: return@LaunchedEffect
        preview = (preview - k).toMutableList().apply { add(to.coerceIn(0, size), k) }
        pending = null
    }

    val currentKeys by rememberUpdatedState(keys)
    val callbacks by rememberUpdatedState(Triple(onTap, onMove, onMerge))
    val dropOutside by rememberUpdatedState(onDropOutside)
    val merge by rememberUpdatedState(canMerge)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = constraints.maxWidth.toFloat()
        val cellW = width / columns
        val rows = ceil(order.size / columns.toFloat()).toInt().coerceAtLeast(1)
        val gridHeight = rows * cellH
        fun origin(index: Int) = Offset((index % columns) * cellW, (index / columns) * cellH)
        fun indexAt(p: Offset, count: Int): Int {
            val col = (p.x / cellW).toInt().coerceIn(0, columns - 1)
            val row = (p.y / cellH).toInt().coerceIn(0, rows - 1)
            return (row * columns + col).coerceIn(0, (count - 1).coerceAtLeast(0))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(with(density) { gridHeight.toDp() })
                .pointerInput(columns, cellW, cellH) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val list = currentKeys
                        val col = (down.position.x / cellW).toInt()
                        val row = (down.position.y / cellH).toInt()
                        val index = row * columns + col
                        val key = list.getOrNull(index)?.takeIf { col in 0 until columns && down.position.y >= 0 }
                            ?: return@awaitEachGesture
                        pressed = key
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress == null) {
                            pressed = null
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up != null && up.changedToUp() && !up.isConsumed) {
                                up.consume()
                                callbacks.first(key)
                            }
                            return@awaitEachGesture
                        }

                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        pressed = null
                        preview = list
                        dragStart = index
                        dragDelta = Offset.Zero
                        mergeTarget = null
                        pending = null
                        dragKey = key
                        longPress.consume()

                        val half = Offset(cellW / 2, cellH / 2)
                        var outside = false
                        val restRadius = 6.dp.toPx()
                        var anchor = Offset.Zero
                        drag(longPress.id) { change ->
                            dragDelta += change.positionChange()
                            if ((dragDelta - anchor).getDistance() > restRadius) {
                                anchor = dragDelta
                                restToken++
                            }
                            change.consume()
                            val center = origin(dragStart) + half + dragDelta
                            outside = dropOutside != null && (
                                center.y < -cellH * 0.35f || center.y > gridHeight + cellH * 0.35f ||
                                    center.x < -cellW * 0.35f || center.x > width + cellW * 0.35f
                                )
                            if (outside) {
                                mergeTarget = null
                                pending = null
                                return@drag
                            }
                            val at = indexAt(center, preview.size)
                            val within = center - origin(at)
                            val onIconMiddle = abs(within.x - cellW / 2) < cellW * 0.26f && within.y in (cellH * 0.08f)..(cellH * 0.62f)
                            val there = preview[at]
                            if (there != key && onIconMiddle && merge(key, there)) {
                                mergeTarget = there
                                pending = null
                            } else {
                                mergeTarget = null
                                pending = if (at != preview.indexOf(key)) at else null
                            }
                        }

                        val target = mergeTarget
                        val finalIndex = pending ?: preview.indexOf(key)
                        pending?.let { to -> preview = (preview - key).toMutableList().apply { add(to.coerceIn(0, size), key) } }
                        pending = null
                        mergeTarget = null
                        settling = true
                        dragKey = null
                        when {
                            outside -> dropOutside?.invoke(key)
                            target != null -> callbacks.third(key, target)
                            else -> callbacks.second(key, finalIndex)
                        }
                    }
                },
        ) {
            order.forEachIndexed { index, k ->
                key(k) {
                    val isDragged = k == dragKey
                    val slot = origin(index).let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) }
                    val finger = (origin(dragStart) + dragDelta).let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) }
                    val position = remember { Animatable(slot, IntOffset.VectorConverter) }
                    LaunchedEffect(isDragged, finger) { if (isDragged) position.snapTo(finger) }
                    LaunchedEffect(isDragged, slot) {
                        if (!isDragged) position.animateTo(slot, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
                    }
                    val scale by animateFloatAsState(
                        when {
                            isDragged -> 1.1f
                            pressed == k -> 0.94f
                            else -> 1f
                        },
                        label = "tile scale",
                    )
                    Box(
                        Modifier
                            .offset { if (isDragged) finger else position.value }
                            .size(with(density) { cellW.toDp() }, TileHeight)
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                alpha = if (isDragged) 0.95f else 1f
                            }
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                                contentDescription = labels[k] ?: k
                                onClick { callbacks.first(k); true }
                            },
                    ) {
                        tile(k, k == mergeTarget)
                    }
                }
            }
        }
    }
}

/** An app on the home grid: its icon and name. Lights up when another app hovers to make a folder. */
@Composable
fun AppTile(app: AppEntry, highlighted: Boolean) {
    TileFrame(app.label, highlighted) { AppIcon(app, TileIcon) }
}

/** A folder on the home grid: up to four of its apps in a 2×2 preview, and its name. */
@Composable
fun FolderTile(folder: FolderItem, apps: List<AppEntry>, highlighted: Boolean) {
    val shape = RoundedCornerShape(TileIcon * 0.28f)
    TileFrame(folder.name, highlighted) {
        Box(
            Modifier
                .size(TileIcon)
                .clip(shape)
                .background(Card)
                .border(1.dp, Line, shape)
                .padding(7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                apps.take(4).chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        pair.forEach { AppIcon(it, 19.dp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TileFrame(label: String, highlighted: Boolean, icon: @Composable () -> Unit) {
    val ring by animateFloatAsState(if (highlighted) 1f else 0f, label = "merge ring")
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // The ring that says "let go to make a folder".
            Box(
                Modifier
                    .size(TileIcon + 12.dp)
                    .graphicsLayer { alpha = ring; scaleX = 0.9f + 0.1f * ring; scaleY = 0.9f + 0.1f * ring }
                    .clip(RoundedCornerShape((TileIcon + 12.dp) * 0.3f))
                    .background(Accent.copy(alpha = 0.22f))
                    .border(2.dp, Accent, RoundedCornerShape((TileIcon + 12.dp) * 0.3f)),
            )
            Box(Modifier.graphicsLayer { val s = 1f - 0.08f * ring; scaleX = s; scaleY = s }) { icon() }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = Ink,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/**
 * An open folder over the home screen: its name (tap to rename), its apps (tap to open, hold to
 * reorder, drag out of the folder to take an app out), and a tap outside or Back to close.
 */
@Composable
fun FolderSheet(
    folder: FolderItem,
    apps: Map<String, AppEntry>,
    onRename: (String) -> Unit,
    onOpen: (AppEntry) -> Unit,
    onMove: (pkg: String, toIndex: Int) -> Unit,
    onTakeOut: (pkg: String) -> Unit,
    onClose: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    val close = {
        focusManager.clearFocus()
        if (name.isBlank()) onRename(com.focushome.HomeLayout.DEFAULT_FOLDER_NAME)
        onClose()
    }
    BackHandler(onBack = close)
    val inFolder = folder.apps.filter { it in apps }
    val shape = RoundedCornerShape(28.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // Background without clipping, so an app being dragged out stays visible past the edge.
                .background(Bg, shape)
                .border(1.dp, Line, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 14.dp, vertical = 18.dp),
        ) {
            BasicTextField(
                value = name,
                onValueChange = {
                    name = it.take(30)
                    if (name.isNotBlank()) onRename(name.trim())
                },
                singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                decorationBox = { field ->
                    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                        if (name.isEmpty()) Text("Folder name", color = Faint, fontSize = 22.sp)
                        field()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            ArrangeableGrid(
                keys = inFolder,
                labels = inFolder.associateWith { apps[it]?.label ?: it },
                columns = 3,
                canMerge = { _, _ -> false },
                onTap = { pkg -> apps[pkg]?.let(onOpen) },
                onMove = onMove,
                onMerge = { _, _ -> },
                onDropOutside = onTakeOut,
            ) { pkg, _ -> apps[pkg]?.let { AppTile(it, highlighted = false) } }
        }
    }
}

