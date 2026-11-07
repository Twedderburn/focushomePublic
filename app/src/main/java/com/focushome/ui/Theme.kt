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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import android.graphics.drawable.ColorDrawable
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

// Low-contrast neutrals: calm to look at, never loud. The background theme and one accent colour are
// picked in Settings.

/**
 * A background theme: the page colour plus the raised surface, divider and three text tones tuned to
 * sit on it. Gray themes get neutral text; brown themes get warm text.
 */
enum class BackgroundChoice(
    val label: String,
    val bg: Color,
    val card: Color,
    val line: Color,
    val ink: Color,
    val dim: Color,
    val faint: Color,
) {
    Black("Black", Color(0xFF000000), Color(0xFF121212), Color(0xFF2A2A2A), Color(0xFFEDEDED), Color(0xFF9E9E9E), Color(0xFF636363)),
    Graphite("Graphite", Color(0xFF1C1D1F), Color(0xFF292A2D), Color(0xFF3A3B3F), Color(0xFFEEEEEF), Color(0xFFA5A6AA), Color(0xFF717378)),
    Espresso("Espresso", Color(0xFF0F0D0B), Color(0xFF1A1613), Color(0xFF2B2520), Color(0xFFF1EBE4), Color(0xFFA89E93), Color(0xFF6E645A)),
    Walnut("Walnut", Color(0xFF1E1712), Color(0xFF2A211A), Color(0xFF3C3027), Color(0xFFF3ECE4), Color(0xFFB3A595), Color(0xFF7E6F61)),
    Mocha("Mocha", Color(0xFF2C221B), Color(0xFF392D24), Color(0xFF4D3E32), Color(0xFFF6EFE7), Color(0xFFBFAF9F), Color(0xFF8C7B6B)),
    ;

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: Espresso
    }
}

val LocalBackground = staticCompositionLocalOf { BackgroundChoice.Espresso }

/** The page background. */
val Bg: Color @Composable @ReadOnlyComposable get() = LocalBackground.current.bg
/** Raised surfaces (cards, the search field) sit just above the background. */
val Card: Color @Composable @ReadOnlyComposable get() = LocalBackground.current.card
val Line: Color @Composable @ReadOnlyComposable get() = LocalBackground.current.line
val Ink: Color @Composable @ReadOnlyComposable get() = LocalBackground.current.ink
val Dim: Color @Composable @ReadOnlyComposable get() = LocalBackground.current.dim
val Faint: Color @Composable @ReadOnlyComposable get() = LocalBackground.current.faint

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
    val background = BackgroundChoice.of(state.background)
    // Match the window behind Compose too, so switching apps or rotating never flashes the old colour.
    val activity = LocalContext.current.findActivity()
    SideEffect { activity?.window?.setBackgroundDrawable(ColorDrawable(background.bg.toArgb())) }
    CompositionLocalProvider(LocalAccent provides accent, LocalBackground provides background) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                background = background.bg,
                surface = background.bg,
                onBackground = background.ink,
                onSurface = background.ink,
                primary = accent,
                onPrimary = background.bg,
            ),
            content = content,
        )
    }
}

/** Full-bleed page in the background colour, with the standard side margins. */
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
