package com.focushome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import android.text.format.DateFormat
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focushome.AppEntry
import com.focushome.Apps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CardShape = RoundedCornerShape(22.dp)

/** Loaded icons, kept for the life of the process. */
private object IconCache {
    val map = HashMap<String, ImageBitmap?>()
}

@Composable
private fun rememberIcon(packageName: String, size: Dp): ImageBitmap? {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val key = "$packageName@$px"
    val icon by produceState(IconCache.map[key], key) {
        if (!IconCache.map.containsKey(key)) {
            value = withContext(Dispatchers.IO) { Apps.icon(context, packageName, px) }
            IconCache.map[key] = value
        }
    }
    return icon
}

/** An app icon in full colour as a rounded square, or a lettered tile while it loads. */
@Composable
fun AppIcon(app: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val icon = rememberIcon(app.packageName, size)
    // Corner radius in proportion to the size, like a squircle.
    val shape = RoundedCornerShape(size * 0.28f)
    val tile = modifier.size(size).clip(shape)
    if (icon != null) {
        Image(icon, contentDescription = null, tile)
    } else {
        Box(tile.background(Card), contentAlignment = Alignment.Center) {
            Text(app.label.take(1).uppercase(), color = Dim, fontSize = (size.value * 0.4f).sp)
        }
    }
}

/**
 * Calls [onSwipeLeft] when the user swipes right-to-left anywhere inside, like paging over to an app
 * drawer. Only a mostly-horizontal drag counts, so scrolling the home screen up and down never
 * triggers it, and holding an icon to move it still wins once the hold is recognised.
 */
fun Modifier.onSwipeLeft(onSwipeLeft: () -> Unit): Modifier = onHorizontalSwipe(toLeft = true, onSwipeLeft)

/** Calls [onSwipeRight] on a left-to-right swipe; the drawer uses it to close. */
fun Modifier.onSwipeRight(onSwipeRight: () -> Unit): Modifier = onHorizontalSwipe(toLeft = false, onSwipeRight)

private fun Modifier.onHorizontalSwipe(toLeft: Boolean, onSwipe: () -> Unit): Modifier = composed {
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val latest by rememberUpdatedState(onSwipe)
    pointerInput(threshold, toLeft) {
        var travelled = 0f
        var fired = false
        detectHorizontalDragGestures(
            onDragStart = {
                travelled = 0f
                fired = false
            },
            onDragCancel = { travelled = 0f },
        ) { change, dx ->
            change.consume()
            travelled += dx
            // Fire as soon as the swipe is long enough, so it feels immediate rather than on release.
            if (!fired && (if (toLeft) travelled < -threshold else travelled > threshold)) {
                fired = true
                latest()
            }
        }
    }
}

/**
 * The "write to Claude" card. Typing and sending hands the text to the Claude app.
 * When [disabledHint] is set the field is read-only and the hint explains why.
 */
@Composable
fun ClaudePrompt(
    resumes: Int,
    disabledHint: String?,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    // Coming back home starts with an empty box.
    var text by rememberSaveable(resumes) { mutableStateOf("") }
    val enabled = disabledHint == null
    val canSend = enabled && text.isNotBlank()
    val send = {
        if (canSend) {
            focusManager.clearFocus()
            onSend(text.trim())
            text = ""
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Card)
            .border(1.dp, Line, CardShape)
            .padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (enabled) Accent else Faint))
            Spacer(Modifier.width(8.dp))
            Text("Claude", color = Dim, fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                enabled = enabled,
                maxLines = 4,
                textStyle = TextStyle(color = Ink, fontSize = 18.sp, lineHeight = 25.sp),
                cursorBrush = SolidColor(Ink),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { send() }),
                decorationBox = { field ->
                    Box(Modifier.padding(vertical = 8.dp)) {
                        if (text.isEmpty()) {
                            Text(disabledHint ?: "What do you want to work on?", color = Faint, fontSize = if (enabled) 18.sp else 15.sp)
                        }
                        field()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (canSend) Accent else Line)
                    .clickable(enabled = canSend, onClick = send),
                contentAlignment = Alignment.Center,
            ) {
                Text("↑", color = if (canSend) Bg else Faint, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Small uppercase label above a section. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), modifier, color = Faint, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.4.sp)

/** Favorites as a four-across grid of icons with labels. */
@Composable
fun FavoritesGrid(apps: List<AppEntry>, onOpen: (AppEntry) -> Unit, modifier: Modifier = Modifier, columns: Int = 4) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        apps.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { app ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onOpen(app) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(app, 52.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            app.label,
                            color = Ink,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Every installed app, searchable, over the home screen. Only shown while unlocked. */
@Composable
fun AllAppsDrawer(apps: List<AppEntry>, favorites: Set<String>, onOpen: (AppEntry) -> Unit, onClose: () -> Unit) {
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    val open: (AppEntry) -> Unit = { app ->
        focusManager.clearFocus()
        onOpen(app)
    }
    BackHandler(onBack = onClose)

    Column(
        Modifier
            .fillMaxSize()
            .onSwipeRight(onClose)
            .background(Bg)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("All apps", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
            Quiet("Close", Modifier.tourTarget("close"), onClick = onClose)
        }

        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 17.sp),
            cursorBrush = SolidColor(Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { shown.firstOrNull()?.let(open) }),
            decorationBox = { field ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Card)
                        .border(1.dp, Line, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    if (query.isEmpty()) Text("Search ${apps.size} apps", color = Faint, fontSize = 17.sp)
                    field()
                }
            },
            modifier = Modifier.fillMaxWidth().tourTarget("search"),
        )
        Spacer(Modifier.height(10.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.packageName }) { app ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { open(app) }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app, 38.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        app.label,
                        color = Ink,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (app.packageName in favorites) Text("★", color = Accent, fontSize = 14.sp)
                }
            }
            if (shown.isEmpty()) item { Body("No apps match \"${query.trim()}\".", Modifier.padding(top = 12.dp)) }
        }
    }
}

/**
 * The home screen clock: the day and date in the accent colour above a large, light time with
 * tabular digits and an accent colon, plus AM/PM when the phone uses a 12-hour clock.
 */
@Composable
fun HomeClock(now: Long, status: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val is24h = DateFormat.is24HourFormat(context)
    val date = Date(now)
    val hours = SimpleDateFormat(if (is24h) "H" else "h", Locale.getDefault()).format(date)
    val minutes = SimpleDateFormat("mm", Locale.getDefault()).format(date)
    val amPm = if (is24h) null else SimpleDateFormat("a", Locale.getDefault()).format(date).lowercase()
    val accent = Accent
    // Tapping the time opens the clock app. A ripple looks odd on huge text, so it dims a little instead.
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()

    // Tapping the date opens the calendar app, with the same dimming.
    val datePress = remember { MutableInteractionSource() }
    val datePressed by datePress.collectIsPressedAsState()

    Column(modifier) {
        Text(
            SimpleDateFormat("EEEE  ·  MMM d", Locale.getDefault()).format(date).uppercase(),
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
            modifier = Modifier
                .graphicsLayer { alpha = if (datePressed) 0.6f else 1f }
                .clickable(interactionSource = datePress, indication = null, onClickLabel = "Open calendar") { Apps.openCalendar(context) }
                // A taller touch target than the small text alone.
                .padding(vertical = 10.dp),
        )
        Row(
            Modifier
                .graphicsLayer { alpha = if (pressed) 0.6f else 1f }
                .clickable(interactionSource = press, indication = null, onClickLabel = "Open clock") { Apps.openClock(context) },
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                buildAnnotatedString {
                    append(hours)
                    withStyle(SpanStyle(color = accent)) { append(":") }
                    append(minutes)
                },
                color = Ink,
                fontSize = 104.sp,
                lineHeight = 104.sp,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-4).sp,
                style = TextStyle(fontFeatureSettings = "tnum", platformStyle = PlatformTextStyle(includeFontPadding = false)),
            )
            if (amPm != null) {
                Spacer(Modifier.width(10.dp))
                Text(amPm, color = Dim, fontSize = 20.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(bottom = 16.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        // Status as a soft pill so it reads as information, not a heading.
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Card)
                .border(1.dp, Line, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(8.dp))
            Text(status, color = Dim, fontSize = 13.sp)
        }
    }
}

/** A round icon button for the home screen footer. */
@Composable
private fun RoundIconButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Card)
            .border(1.dp, Line, CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** A round button with a padlock: turns Focus mode back on. */
@Composable
fun LockButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    RoundIconButton(Icons.Rounded.Lock, "Lock", tint = Accent, onClick = onClick, modifier = modifier)

/** A round button with a gear: opens Settings. Quieter than Lock, so it's tinted neutral. */
@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    RoundIconButton(Icons.Rounded.Settings, "Settings", tint = Dim, onClick = onClick, modifier = modifier)
