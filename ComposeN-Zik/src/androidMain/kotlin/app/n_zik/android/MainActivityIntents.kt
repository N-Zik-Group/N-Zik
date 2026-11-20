package app.n_zik.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshotFlow
import androidx.core.net.toUri
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.components.ui.screens.rewind.RewindReminderWorker
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.listentogether.listenTogetherInviteUri
import app.n_zik.android.listentogether.parseListenTogetherInvite
import app.n_zik.android.listentogether.restoreListenTogetherInvite
import kotlinx.coroutines.flow.first

/**
 * Decodes the deck target carried by a rewind reminder's content intent: a valid year with a
 * month 1..12 yields the finished `(year, month)` pair (monthly reminder); a valid year with
 * the month extra missing (default 0) or explicitly 0 yields the yearly target `(year, 0)`
 * (yearly reminder — its content intent carries the year extra only). Anything else (missing
 * year, out-of-range values, no extras) yields null so the app starts without a forced deck
 * open.
 *
 * The one-shot contract is layered (spec GH-275 + follow-up):
 * - [consumeRewindDeckExtras] strips the extras off the intent the activity keeps, so a
 *   singleTask relaunch re-delivering that intent decodes to null;
 * - [lastConsumedMarker] rejects an intent whose target was already consumed. That is the
 *   task record re-sending the ORIGINAL launch intent (extras intact) when the task is
 *   restored after a process death — a path the in-memory strip cannot reach, which
 *   previously re-opened the deck on every tap of any other notification (device-observed
 *   leak, fixed 2026-10-01). A fresh notification tap still decodes to its target: the tap
 *   is the user's intent (user decision 2026-10-01 — the tap always re-opens the deck).
 *
 * Top-level so the parse contract is unit-testable without launching the activity
 * (spec GH-275 — the consumer side is covered by RewindDeckDeepLinkTest).
 */
internal fun rewindDeckTargetFromIntent(
    intent: Intent?,
    lastConsumedMarker: Int = 0,
): Pair<Int, Int>? {
    val year = intent?.getIntExtra(RewindReminderWorker.EXTRA_DECK_YEAR, 0) ?: 0
    val month = intent?.getIntExtra(RewindReminderWorker.EXTRA_DECK_MONTH, 0) ?: 0
    val target: Pair<Int, Int>? = when {
        year !in 2000..2100 -> null
        month in 1..12 -> year to month
        // Yearly sentinel: the yearly reminder posts the year extra only, so the missing
        // month extra (default 0) selects the finished-year deck
        month == 0 -> year to month
        else -> null
    }
    return target?.takeUnless {
        rewindDeckTargetMarker(it.first, it.second) == lastConsumedMarker
    }
}

/**
 * Encodes a decoded deck target for the persistent consumption marker
 * ([DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED]): `year * 100 + month`, the yearly
 * sentinel (month 0) kept as is. A monthly target repeats only 12 cycles apart and a yearly
 * one yearly, so the encoding is unique per notification cycle — the marker never needs
 * resetting, only overwriting. Top-level so the encoding is unit-testable.
 */
internal fun rewindDeckTargetMarker(year: Int, month: Int): Int = year * 100 + month

/**
 * Decodes the rewind playlist deep link carried by a playlist-ready notification's content
 * intent ([RewindPlaylists.EXTRA_REWIND_PLAYLIST_ID], spec GH-275 follow-up — the playlist
 * notifications now open the generated 'Rewind — <period>' playlist directly instead of a
 * bare app open): a positive playlist id yields it, anything else (missing extra, 0,
 * negative) yields null so the app starts without a forced playlist open. [lastConsumedId]
 * rejects an intent pointing at an already-opened playlist — the task record re-sends the
 * original launch intent after a process-death restore (same guard as the deck target).
 * Top-level so the parse contract is unit-testable without launching the activity.
 */
internal fun rewindPlaylistTargetFromIntent(
    intent: Intent?,
    lastConsumedId: Long = 0L,
): Long? =
    intent?.getLongExtra(RewindPlaylists.EXTRA_REWIND_PLAYLIST_ID, 0L)
        ?.takeIf { it > 0L && it != lastConsumedId }

/**
 * One-shot consumption of the deck extras on the activity's current intent: removes
 * [RewindReminderWorker.EXTRA_DECK_YEAR] and [RewindReminderWorker.EXTRA_DECK_MONTH] so a
 * later singleTask relaunch decodes to null instead of re-opening the deck on the same
 * finished month/year. singleTask re-delivers the activity's current intent on every task
 * relaunch (recents), so a tap on the notification must strip the extras from the intent
 * kept as the current intent — otherwise every app re-foreground re-opens the deck.
 * Call it AFTER [rewindDeckTargetFromIntent] has consumed the target into the activity
 * state. Top-level so the consume-then-reparse contract is unit-testable.
 */
internal fun consumeRewindDeckExtras(intent: Intent?) {
    intent?.removeExtra(RewindReminderWorker.EXTRA_DECK_YEAR)
    intent?.removeExtra(RewindReminderWorker.EXTRA_DECK_MONTH)
}

/**
 * One-shot consumption of the playlist deep link on the activity's current intent — the
 * playlist-id twin of [consumeRewindDeckExtras] (spec GH-275 follow-up). The strip is
 * unconditional: even a rejected (already-opened) playlist id must not stay alive in the
 * kept intent for a later re-delivery.
 */
internal fun consumeRewindPlaylistExtras(intent: Intent?) {
    intent?.removeExtra(RewindPlaylists.EXTRA_REWIND_PLAYLIST_ID)
}

/**
 * Decodes the "now playing" notification tap carried by the notification's content intent
 * ([MainActivity.EXTRA_OPEN_PLAYER_TOKEN], set by PlayerServiceModern's global session
 * activity — the PendingIntent media3 uses as the notification content intent,
 * spec-notification-click-opens-player option B): a positive token (the session's one-shot
 * System.currentTimeMillis() token, static per service instance) yields it, anything else
 * (missing extra, 0, negative) yields null so the app starts without a forced player open.
 * Staleness is NOT rejected here — the task record re-sends the original launch intent
 * (extras intact) after a process-death restore, and the token is static per service, so
 * the rejection lives at deployment time as a live comparison against the current service's
 * token (see [consumeOpenPlayerDeepLink] / the cold deploy site).
 * Top-level so the parse contract is unit-testable without launching the activity.
 */
internal fun openPlayerTokenFromIntent(intent: Intent?): Long? =
    intent?.getLongExtra(MainActivity.EXTRA_OPEN_PLAYER_TOKEN, 0L)
        ?.takeIf { it > 0L }

/**
 * One-shot consumption of the "open player" extra on the intent the activity keeps — the
 * token twin of [consumeRewindPlaylistExtras] (spec-notification-click-opens-player). The
 * strip is unconditional: even a rejected (non-positive / malformed) token must not stay
 * alive in the kept intent for a later re-delivery.
 */
internal fun consumeOpenPlayerExtras(intent: Intent?) {
    intent?.removeExtra(MainActivity.EXTRA_OPEN_PLAYER_TOKEN)
}

/**
 * Builds the deck route for a decoded target (spec GH-275): the monthly target (month 1..12)
 * carries both arguments, while the yearly target (month = 0 intent sentinel) omits the month
 * argument — the route's default month=-1 then maps to `rewindMonth = null` in
 * `RewindScreen`, i.e. the year-only deck. Top-level so the monthly/yearly branch is
 * unit-testable without launching the activity.
 */
internal fun rewindDeckRoute(year: Int, month: Int): String =
    if (month == 0) {
        "${NavRoutes.rewind.name}?year=$year"
    } else {
        "${NavRoutes.rewind.name}?year=$year&month=$month"
    }

/**
 * The stable profile ID a direct profile shortcut intent points to (action
 * [MainActivity.action_profile] + [MainActivity.EXTRA_PROFILE_ID]), or null when the intent
 * carries no such target (missing action, or missing/absent extra). Top-level so the
 * cold/warm decision is unit-testable without launching the activity (spec-profile-shortcuts).
 */
internal fun profileShortcutTarget(intent: Intent?): String? =
    if (intent?.action == MainActivity.action_profile) {
        intent.getStringExtra(MainActivity.EXTRA_PROFILE_ID)
    } else {
        null
    }

/**
 * One-shot consumption of the profile-shortcut extra on the intent the activity keeps: a
 * singleTask relaunch re-delivers the kept intent, so the extra must be stripped off it —
 * the in-memory state alone cannot reach the task record (same pattern as
 * [consumeRewindDeckExtras], spec-profile-shortcuts). The strip is unconditional: even a
 * rejected (unknown profile) tap must not keep its extra alive for a later re-delivery.
 */
internal fun consumeProfileShortcutExtra(intent: Intent?) {
    intent?.removeExtra(MainActivity.EXTRA_PROFILE_ID)
}

/**
 * The profile a profile-shortcut tap should switch to, or null for a bare launch: a tap on
 * the profile that is already active (no switch, no restart) or on an unknown/deleted ID
 * (ignored — the app never points itself at a profile that does not exist) both yield null.
 * Pure so the cold/warm decision is unit-testable without an Intent or a Context
 * (spec-profile-shortcuts).
 */
internal fun profileSwitchTarget(tapId: String?, activeId: String, validIds: Collection<String>): String? =
    tapId?.takeIf { it != activeId && it in validIds }

/**
 * True when [action] is consumed by its own dedicated path: the profile-shortcut actions
 * (generic « Profiles » or a direct per-profile one, spec-profile-shortcuts) and the
 * debug-only updater backdoor ([MainActivity.action_debug_open_updater]). Those must NOT
 * feed [shortcutIntentAction]: doing so would make the home-tab effect pop the back
 * stack to home, undoing their own navigation. Top-level so the exclusion is
 * unit-testable without launching the activity.
 */
internal fun isSelfConsumedShortcutAction(action: String?): Boolean =
    action == MainActivity.action_profiles ||
        action == MainActivity.action_profile ||
        action == MainActivity.action_debug_open_updater

internal fun initialLibraryUri(intent: Intent, savedState: Bundle?, waitingColdLaunch: Boolean): Uri? {
    if (savedState != null && !waitingColdLaunch) return restoreListenTogetherInvite(intent, savedState)
    return listenTogetherInviteUri(intent)
        ?: (intent.data ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri())
            ?.takeUnless { parseListenTogetherInvite(it) != null }
}

/** Persists targets after the original intent has been consumed, including one-shot Rewind extras. */
internal data class PendingLibraryNavigation(
    val uri: Uri?,
    val shortcut: String?,
    val rewindDeck: Pair<Int, Int>?,
    val rewindPlaylist: Long?,
    val coldPlayerToken: Long?,
    val openWarmPlayer: Boolean,
    val openProfiles: Boolean,
    val openUpdater: Boolean,
) {
    val hasPending: Boolean
        get() = uri != null || shortcut != null || rewindDeck != null || rewindPlaylist != null ||
            openWarmPlayer || openProfiles || openUpdater

    fun toBundle(): Bundle = Bundle().apply {
        putString("uri", uri?.toString())
        putString("shortcut", shortcut)
        rewindDeck?.let { (year, month) -> putIntArray("rewindDeck", intArrayOf(year, month)) }
        rewindPlaylist?.let { putLong("rewindPlaylist", it) }
        coldPlayerToken?.let { putLong("coldPlayerToken", it) }
        putBoolean("openWarmPlayer", openWarmPlayer)
        putBoolean("openProfiles", openProfiles)
        putBoolean("openUpdater", openUpdater)
    }

    companion object {
        fun fromBundle(bundle: Bundle): PendingLibraryNavigation = PendingLibraryNavigation(
            uri = bundle.getString("uri")?.let(Uri::parse),
            shortcut = bundle.getString("shortcut"),
            rewindDeck = bundle.getIntArray("rewindDeck")?.takeIf { it.size == 2 }?.let { it[0] to it[1] },
            rewindPlaylist = bundle.getLong("rewindPlaylist").takeIf { bundle.containsKey("rewindPlaylist") },
            coldPlayerToken = bundle.getLong("coldPlayerToken").takeIf { bundle.containsKey("coldPlayerToken") },
            openWarmPlayer = bundle.getBoolean("openWarmPlayer"),
            openProfiles = bundle.getBoolean("openProfiles"),
            openUpdater = bundle.getBoolean("openUpdater"),
        )
    }
}

/** Queues only warm deliveries. The activity's original intent owns the cold launch. */
internal class DeferredLibraryIntents {
    private val pending = mutableStateListOf<Intent>()
    private var dispatching = false

    val hasPending: Boolean
        get() = dispatching || pending.isNotEmpty()

    val hasQueued: Boolean
        get() = pending.isNotEmpty()

    fun offer(intent: Intent) {
        if (intent.action != Intent.ACTION_MAIN || intent.data != null || intent.extras?.isEmpty == false) {
            pending.add(intent)
        }
    }

    fun snapshot(): ArrayList<Intent> = ArrayList(pending)

    /** Each consumer clears its own state after accepting a request. Wait before writing over it. */
    suspend fun drain(consumersIdle: () -> Boolean, dispatch: (Intent) -> Unit) {
        while (hasPending) {
            snapshotFlow(consumersIdle).first { it }
            if (dispatching) {
                dispatching = false
                continue
            }
            dispatching = true
            dispatch(pending.removeAt(0))
        }
    }
}
