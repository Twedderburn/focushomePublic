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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Monochrome on purpose: nothing on the home screen should be pleasant to stare at.
val Ink = Color(0xFFEDEDED)
val Dim = Color(0xFF9A9A9A)
val Faint = Color(0xFF5C5C5C)
val Line = Color(0xFF2E2E2E)

@Composable
fun FocusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Black,
            surface = Color.Black,
            onBackground = Ink,
            onSurface = Ink,
            primary = Ink,
            onPrimary = Color.Black,
        ),
        content = content,
    )
}

/** Full-bleed black page with the standard side margins. */
@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
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
                if (primary && enabled) Modifier.background(Ink, shape)
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
                primary -> Color.Black
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
