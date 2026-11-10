package com.focushome

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZoneId

data class FocusState(
    val setupDone: Boolean = false,
    /** Persisted because becoming the default home app restarts this app mid-setup. */
    val setupStep: Int = 0,
    /** SHA-256 of the key code's contents; the code itself is never needed to check a scan. */
    val keyHash: String? = null,
    /** Contents of a code this app generated, so it can be printed again while unlocked. Null for paired codes. */
    val keyContent: String? = null,
    val focusOn: Boolean = false,
    /** Package names, in the order they appear on the Focus screen. */
    val essentials: List<String> = emptyList(),
    /** 0 means auto-relock is off. */
    val relockMinutes: Int = 30,
    /** SystemClock.elapsedRealtime() at the last unlock, so changing the clock can't stretch the unlock window. */
    val unlockedAt: Long = 0,
    /** Boot count at the last unlock. Elapsed time restarts at boot, so a reboot counts as the window running out. */
    val unlockBoot: Int = -1,
    val focusStartedAt: Long = 0,
    val statsDay: String = "",
    /** Focus time from sessions that already ended today. */
    val focusMsBanked: Long = 0,
    val unlocksToday: Int = 0,
    val emergencyUsesToday: Int = 0,
) {
    /** Relock deadline on the elapsedRealtime clock, or null when none is pending. */
    val relockAt: Long?
        get() = if (!focusOn && relockMinutes > 0 && unlockedAt > 0) unlockedAt + relockMinutes * 60_000L else null

    // A few seconds of slack so an alarm that fires a hair early still counts.
    fun relockDue(boot: Int, nowElapsed: Long): Boolean {
        val at = relockAt ?: return false
        return boot != unlockBoot || nowElapsed >= at - 5_000
    }

    /** The relock deadline as a wall-clock time, for display only. */
    fun relockAtWall(): Long? = relockAt?.let { System.currentTimeMillis() + (it - SystemClock.elapsedRealtime()) }

    /** 15 minutes, then 30, then 60 for each further emergency unlock on the same day. */
    val emergencyWaitMs: Long
        get() = (15 * 60_000L) shl emergencyUsesToday.coerceAtMost(2)

    fun focusMsToday(now: Long): Long =
        focusMsBanked + if (focusOn) (now - maxOf(focusStartedAt, startOfToday())).coerceAtLeast(0) else 0
}

/** The single source of truth, persisted to SharedPreferences and shared by the UI, the relock alarm and the blocker. */
object FocusStore {
    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state.asStateFlow()

    fun init(context: Context) {
        app = context.applicationContext
        prefs = app.getSharedPreferences("focus", Context.MODE_PRIVATE)
        _state.value = load()
        tick()
        scheduleRelock(_state.value)
    }

    fun isKey(raw: String): Boolean = _state.value.keyHash == QrKey.hash(raw)

    /** Only unlocking needs the key; going back into Focus mode is a plain button. */
    fun unlockWithKey(raw: String): Boolean {
        if (!_state.value.setupDone || !isKey(raw)) return false
        unlock()
        return true
    }

    fun setKey(raw: String, generated: Boolean) =
        update { it.copy(keyHash = QrKey.hash(raw), keyContent = if (generated) raw else null) }

    fun setEssentials(packages: List<String>) = update { it.copy(essentials = packages) }

    fun setRelockMinutes(minutes: Int) = update { it.copy(relockMinutes = minutes) }

    fun setSetupStep(step: Int) = update { it.copy(setupStep = step) }

    fun finishSetup(startFocus: Boolean) = update {
        val done = it.copy(setupDone = true)
        if (startFocus) focused(done) else done.copy(focusOn = false, unlockedAt = elapsed(), unlockBoot = boot())
    }

    fun enterFocus() = update { if (it.focusOn) it else focused(it) }

    fun unlock() = update { unlocked(it) }

    /** Unlocks and makes the next emergency wait today longer. */
    fun emergencyUnlock() = update { s ->
        if (!s.focusOn) s else unlocked(s).copy(emergencyUsesToday = s.emergencyUsesToday + 1)
    }

    /** Rolls the daily stats over and relocks if the unlock window has run out. Safe to call often. */
    fun tick() = update { s -> if (s.relockDue(boot(), elapsed())) focused(s) else s }

    private fun focused(s: FocusState) = s.copy(focusOn = true, focusStartedAt = now(), unlockedAt = 0)

    private fun unlocked(s: FocusState): FocusState {
        if (!s.focusOn) return s
        return s.copy(
            focusOn = false,
            focusMsBanked = s.focusMsBanked + (now() - maxOf(s.focusStartedAt, startOfToday())).coerceAtLeast(0),
            unlockedAt = elapsed(),
            unlockBoot = boot(),
            unlocksToday = s.unlocksToday + 1,
        )
    }

    @Synchronized
    private fun update(change: (FocusState) -> FocusState) {
        val current = rolledOver(_state.value)
        val next = change(current)
        if (next == _state.value) return
        save(next)
        _state.value = next
        scheduleRelock(next)
    }

    private fun rolledOver(s: FocusState): FocusState {
        val today = LocalDate.now().toString()
        return if (s.statsDay == today) s
        else s.copy(statsDay = today, focusMsBanked = 0, unlocksToday = 0, emergencyUsesToday = 0)
    }

    private fun scheduleRelock(s: FocusState) {
        val alarms = app.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(
            app, 0, Intent(app, RelockReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val at = s.relockAt
        when {
            at == null -> alarms.cancel(intent)
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms() ->
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, intent)
            else -> alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, intent)
        }
    }

    private fun load() = FocusState(
        setupDone = prefs.getBoolean("setupDone", false),
        setupStep = prefs.getInt("setupStep", 0),
        keyHash = prefs.getString("keyHash", null),
        // 1.0 stored only the token of a generated code, which was then wrapped in a link.
        keyContent = prefs.getString("keyContent", null)
            ?: prefs.getString("keyToken", null)?.let { "focushome://key/$it" },
        focusOn = prefs.getBoolean("focusOn", false),
        essentials = prefs.getString("essentials", "")!!.split(',').filter { it.isNotBlank() },
        relockMinutes = prefs.getInt("relockMinutes", 30),
        // 1.0 stored a wall-clock unlock time; treat an unlock carried over from it as already expired.
        unlockedAt = if (prefs.contains("unlockedAtElapsed")) prefs.getLong("unlockedAtElapsed", 0)
        else if (prefs.getLong("unlockedAt", 0) > 0) 1 else 0,
        unlockBoot = prefs.getInt("unlockBoot", -1),
        focusStartedAt = prefs.getLong("focusStartedAt", 0),
        statsDay = prefs.getString("statsDay", "")!!,
        focusMsBanked = prefs.getLong("focusMsBanked", 0),
        unlocksToday = prefs.getInt("unlocksToday", 0),
        emergencyUsesToday = prefs.getInt("emergencyUsesToday", 0),
    )

    // commit(), not apply(): the relock receiver's process can be killed right after it returns.
    private fun save(s: FocusState) {
        prefs.edit()
            .remove("keyToken")
            .remove("unlockedAt")
            .putBoolean("setupDone", s.setupDone)
            .putInt("setupStep", s.setupStep)
            .putString("keyHash", s.keyHash)
            .putString("keyContent", s.keyContent)
            .putBoolean("focusOn", s.focusOn)
            .putString("essentials", s.essentials.joinToString(","))
            .putInt("relockMinutes", s.relockMinutes)
            .putLong("unlockedAtElapsed", s.unlockedAt)
            .putInt("unlockBoot", s.unlockBoot)
            .putLong("focusStartedAt", s.focusStartedAt)
            .putString("statsDay", s.statsDay)
            .putLong("focusMsBanked", s.focusMsBanked)
            .putInt("unlocksToday", s.unlocksToday)
            .putInt("emergencyUsesToday", s.emergencyUsesToday)
            .commit()
    }

    private fun now() = System.currentTimeMillis()

    private fun elapsed() = SystemClock.elapsedRealtime()

    private fun boot() = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, 0)
}

fun startOfToday(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
