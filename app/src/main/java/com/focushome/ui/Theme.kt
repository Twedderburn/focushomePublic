package com.focushome.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focushome.FocusStore
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Warm, low-contrast neutrals: calm to look at, never loud. One accent colour, picked in Settings.
/** The page background: a warm near-black. */
val Bg = Color(0xFF0F0D0B)
val Ink = Color(0xFFF1EBE4)
val Dim = Color(0xFFA89E93)
val Faint = Color(0xFF6E645A)
val Line = Color(0xFF2B2520)
/** Raised surfaces (cards, the search field) sit just above the background. */
val Card = Color(0xFF1A1613)

/** The accent colours on offer. Muted so they sit comfortably on the brown neutrals. */
enum class AccentChoice(val label: String, val color: Color) {
    Clay("Clay", Color(0xFFD08B6E)),
    Ochre("Ochre", Color(0xFFD4A954)),
    Sage("Sage", Color(0xFF98AE84)),
    Teal("Teal", Color(0xFF6FAAA5)),
    Slate("Slate", Color(0xFF8DA2C8)),
    Rose("Rose", Color(0xFFD38FA3)),
    Plum("Plum", Color(0xFFAC8FC6)),
    ;

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: Clay
    }
}

val LocalAccent = staticCompositionLocalOf { AccentChoice.Clay.color }

/** The accent colour the user picked. */
val Accent: Color
    @Composable @ReadOnlyComposable get() = LocalAccent.current

@Composable
fun FocusTheme(content: @Composable () -> Unit) {
    val state by FocusStore.state.collectAsStateWithLifecycle()
    val accent = AccentChoice.of(state.accent).color
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                background = Bg,
                surface = Bg,
                onBackground = Ink,
                onSurface = Ink,
                primary = accent,
                onPrimary = Bg,
            ),
            content = content,
        )
    }
}

/** Full-bleed black page with the standard side margins. */
@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .padding(horizontal = 28.dp),
        content = content,
    )
}

@Composable
fun Heading(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Light)

@Composable
fun Body(text: String, modifier: Modifier = Modifier, color: Color = Dim) =
    Text(text, modifier, color = color, fontSize = 16.sp, lineHeight = 23.sp)

/** Primary actions are filled; everything else is an outline. */
@Composable
fun Action(text: String, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .fillMaxWidth()
            .then(
                if (primary && enabled) Modifier.background(Accent, shape)
                else Modifier.border(BorderStroke(1.dp, if (enabled) Faint else Line), shape),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = when {
                !enabled -> Faint
                primary -> Bg
                else -> Ink
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** A small text-only control, for things that should be easy to ignore. */
@Composable
fun Quiet(text: String, modifier: Modifier = Modifier, color: Color = Dim, onClick: () -> Unit) =
    Text(
        text,
        modifier
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        color = color,
        fontSize = 15.sp,
    )
