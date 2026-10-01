package app.n_zik.android.bridge

import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart

/**
 * The two auto-stop settings of contract §11.2, in minutes; [NEVER] disables one.
 */
@Immutable
data class AutoStopSettings(
    val noClientMinutes: Int = DEFAULT_NO_CLIENT_MINUTES,
    val inactivityMinutes: Int = DEFAULT_INACTIVITY_MINUTES,
) {
    companion object {
        const val NEVER = 0
        const val DEFAULT_NO_CLIENT_MINUTES = 15
        const val DEFAULT_INACTIVITY_MINUTES = NEVER

        /** Values offered by both settings (contract §11.2). */
        val OPTIONS: List<Int> = listOf(NEVER, 5, 15, 30, 60)

        /** [minutes] when it is one of [OPTIONS], [default] otherwise (unknown stored value). */
        fun sanitize(minutes: Int, default: Int): Int = if (minutes in OPTIONS) minutes else default
    }
}

/**
 * Where the auto-stop settings live in the app preferences (read and written by
 * [BridgeServerController]).
 */
internal object AutoStopPreferences {
    const val NO_CLIENT_KEY = "bridgeAutoStopNoClientMinutes"
    const val INACTIVITY_KEY = "bridgeAutoStopInactivityMinutes"

    /** Persisted settings; a missing or unknown value gives the default. */
    fun read(prefs: SharedPreferences): AutoStopSettings =
        AutoStopSettings(
            noClientMinutes = AutoStopSettings.sanitize(
                prefs.getInt(NO_CLIENT_KEY, AutoStopSettings.DEFAULT_NO_CLIENT_MINUTES),
                AutoStopSettings.DEFAULT_NO_CLIENT_MINUTES,
            ),
            inactivityMinutes = AutoStopSettings.sanitize(
                prefs.getInt(INACTIVITY_KEY, AutoStopSettings.DEFAULT_INACTIVITY_MINUTES),
                AutoStopSettings.DEFAULT_INACTIVITY_MINUTES,
            ),
        )

    fun writeNoClient(prefs: SharedPreferences, minutes: Int) {
        prefs.edit().putInt(NO_CLIENT_KEY, minutes).apply()
    }

    fun writeInactivity(prefs: SharedPreferences, minutes: Int) {
        prefs.edit().putInt(INACTIVITY_KEY, minutes).apply()
    }
}
private const val MS_PER_MINUTE = 60_000L

/**
 * Longest single wait: [delay] follows a clock frozen in deep sleep while `now` may not, so the
 * deadline is re-checked against `now` at least this often (expiry at the first wake after it).
 */
private const val MAX_WAIT_STEP_MS = 60_000L

/**
 * Auto-stop timers of contract §11.2, kept free of Android so they can run in virtual time.
 *
 * - "No client": counts while no PC holds the session; it starts again from zero each time
 *   the session is released.
 * - "Inactivity": counts since the watcher started or since the last valid command handed to
 *   the executor (§9); nothing else resets it.
 *
 * A change of [settings] applies at once, measured from the start of the current state
 * (a delay already elapsed under the new value expires immediately).
 */
internal object BridgeAutoStop {

    /**
     * Suspends until one of the timers expires, then returns (once). Never returns while both
     * settings are [AutoStopSettings.NEVER]; cancel the caller to stop watching.
     *
     * @param settings current auto-stop settings
     * @param activeDevice PC holding the session, `null` when none (contract §6.2)
     * @param commandTicks one emission per valid command handed to the executor
     * @param now monotonic clock in ms (virtual time in tests)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun awaitExpiry(
        settings: Flow<AutoStopSettings>,
        activeDevice: Flow<ActiveDevice?>,
        commandTicks: Flow<Unit>,
        now: () -> Long,
    ) {
        val noClientSince: Flow<Long?> = activeDevice
            .map { it != null }
            .distinctUntilChanged()
            .map { connected -> if (connected) null else now() }
        val lastCommandAt: Flow<Long> = commandTicks
            .map { now() }
            .onStart { emit(now()) }
        combine(settings, noClientSince, lastCommandAt) { current, since, lastCommand ->
            listOfNotNull(
                since?.let { deadline(it, current.noClientMinutes) },
                deadline(lastCommand, current.inactivityMinutes),
            ).minOrNull()
        }
            .distinctUntilChanged()
            .mapLatest { deadline ->
                if (deadline == null) awaitCancellation()
                while (true) {
                    val remaining = deadline - now()
                    if (remaining <= 0L) break
                    delay(minOf(remaining, MAX_WAIT_STEP_MS))
                }
            }
            .first()
    }

    private fun deadline(since: Long, minutes: Int): Long? =
        if (minutes == AutoStopSettings.NEVER) null else since + minutes * MS_PER_MINUTE
}
