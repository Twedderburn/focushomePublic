package com.focushome.ui

import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focushome.FocusStore
import kotlinx.coroutines.delay

/** For a lost key: unlocks only after the user sits through the whole countdown on this screen. */
@Composable
fun EmergencyScreen(onCancel: () -> Unit, onUnlocked: () -> Unit) {
    // Fixed when the screen opens; tick() first so a new day's shorter wait applies.
    val wait = remember {
        FocusStore.tick()
        FocusStore.state.value.emergencyWaitMs
    }
    val activity = LocalContext.current.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val cancel by rememberUpdatedState(onCancel)

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    // Leaving the screen, or turning it off, gives up the wait.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) cancel() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val startedAt = remember { SystemClock.elapsedRealtime() }
    var left by remember { mutableLongStateOf(wait) }
    LaunchedEffect(Unit) {
        while (true) {
            left = wait - (SystemClock.elapsedRealtime() - startedAt)
            if (left <= 0) break
            delay(250)
        }
        FocusStore.emergencyUnlock()
        onUnlocked()
    }

    Page {
        Spacer(Modifier.height(56.dp))
        Heading("Emergency unlock")
        Spacer(Modifier.height(12.dp))
        Body("Stay on this screen until the timer runs out. If you leave or turn the screen off, the wait starts over.")
        Spacer(Modifier.height(8.dp))
        Body(
            "Today's wait is ${wait / 60_000} minutes. It doubles each time you use this, up to an hour, " +
                "and goes back to 15 minutes tomorrow.",
            color = Faint,
        )
        Spacer(Modifier.weight(1f))
        val seconds = (left.coerceAtLeast(0) + 999) / 1000
        Text(
            "%d:%02d".format(seconds / 60, seconds % 60),
            Modifier.fillMaxWidth(),
            color = Ink,
            fontSize = 96.sp,
            fontWeight = FontWeight.Thin,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Body("Once you're unlocked, you can make a new key code in Settings.", color = Faint)
        Spacer(Modifier.height(16.dp))
        Action("Cancel", onClick = onCancel)
        Spacer(Modifier.height(20.dp))
    }
}
