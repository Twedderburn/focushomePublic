package com.focushome

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.focushome.ui.FocusHomeApp
import com.focushome.ui.FocusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var apps by mutableStateOf<List<AppEntry>>(emptyList())
    private var homePresses by mutableIntStateOf(0)
    private var resumes by mutableIntStateOf(0)
    private var blocked by mutableStateOf<String?>(null)
    private var blockReason by mutableStateOf(BlockReason.Focus)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.BLACK), SystemBarStyle.dark(Color.BLACK))
        blocked = intent?.getStringExtra(EXTRA_BLOCKED)
        blockReason = reasonOf(intent)
        setContent {
            FocusTheme {
                FocusHomeApp(
                    apps = apps,
                    homePresses = homePresses,
                    resumes = resumes,
                    blocked = blocked,
                    blockReason = blockReason,
                    onBlockedDone = { blocked = null },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        FocusStore.tick()
        Notifications.clearRelocked(this)
        resumes++
        DnsFilter.start(this)
        lifecycleScope.launch {
            apps = withContext(Dispatchers.Default) { Apps.load(this@MainActivity) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already home: drop back to the main screen.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
        intent.getStringExtra(EXTRA_BLOCKED)?.let {
            blocked = it
            blockReason = reasonOf(intent)
        }
    }

    private fun reasonOf(intent: Intent?): BlockReason =
        intent?.getStringExtra(EXTRA_BLOCK_REASON)?.let { runCatching { BlockReason.valueOf(it) }.getOrNull() } ?: BlockReason.Focus

    companion object {
        /** Label of an app FocusGuardService just sent away; shows the "Not now" screen. */
        const val EXTRA_BLOCKED = "com.focushome.BLOCKED"

        /** A [BlockReason] name: Focus mode, always blocked, or a browser while the web filter is off. */
        const val EXTRA_BLOCK_REASON = "com.focushome.BLOCK_REASON"
    }
}
