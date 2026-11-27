package app.n_zik.android.components.player.lyrics

import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.models.Lyrics

/**
 * Encapsulates the logic for determining which lyrics to display and whether a network fetch is needed.
 */
data class FetchNeeds(
    val currentLyrics: Lyrics?,
    val needKaraokeFetch: Boolean,
    val needSyncedFetch: Boolean,
    val needUnsyncedFetch: Boolean
)

object LyricsDecisionMaker {

    /** Fetched lyrics are re-verified only after this window (30 days, constant by design — not a setting). */
    const val REFRESH_TTL_MILLIS: Long = 30L * 24L * 60L * 60L * 1000L

    fun evaluateFetchNeeds(
        mediaId: String,
        lyricsType: LyricsType,
        allLyrics: List<Lyrics>,
        globalLastKaraokeAttemptMediaId: String?,
        globalLastSyncedAttemptMediaId: String?,
        globalLastUnSyncedAttemptMediaId: String?,
        now: Long,
        forceAutoRetry: Boolean = false,
        hasTrackTitle: Boolean = true,
    ): FetchNeeds {
        val karaoke = allLyrics.find { it.type == LyricsType.Karaoke.name && !it.data.isNullOrBlank() }
        val synced = allLyrics.find { it.type == LyricsType.Synced.name && !it.data.isNullOrBlank() }
        val unsynced = allLyrics.find { it.type == LyricsType.Unsynced.name && !it.data.isNullOrBlank() }
        val karaokeNotAttempted = globalLastKaraokeAttemptMediaId != mediaId
        val syncedNotAttempted = globalLastSyncedAttemptMediaId != mediaId
        val unsyncedNotAttempted = globalLastUnSyncedAttemptMediaId != mediaId
        val currentLyrics = when (lyricsType) {
            LyricsType.Auto -> {
                // Old releases sometimes stored a line-only response as Karaoke.
                karaoke?.takeIf { it.isEdited || hasWordTimings(it.data) } ?: synced ?: karaoke ?: unsynced
            }
            LyricsType.Karaoke -> karaoke
            LyricsType.Synced -> synced ?: karaoke
            LyricsType.Unsynced -> unsynced
        }

        if (!hasTrackTitle && lyricsType != LyricsType.Unsynced) {
            // A fresh plain result would otherwise suppress the later timed searches in Auto.
            return FetchNeeds(currentLyrics, false, false, false)
        }

        if (lyricsType == LyricsType.Auto) {
            if (currentLyrics == null || forceAutoRetry) {
                return FetchNeeds(currentLyrics, karaokeNotAttempted, syncedNotAttempted, unsyncedNotAttempted)
            }
            val refresh = !currentLyrics.isEdited && isTtlExpired(currentLyrics.lastFetchedAt, now)
            val searchHigherFormats = !currentLyrics.isEdited
            val isKaraoke = currentLyrics.type == LyricsType.Karaoke.name && hasWordTimings(currentLyrics.data)
            val isPlain = currentLyrics.type == LyricsType.Unsynced.name
            return FetchNeeds(
                currentLyrics = currentLyrics,
                needKaraokeFetch = karaokeNotAttempted && (if (isKaraoke) refresh else searchHigherFormats),
                needSyncedFetch = syncedNotAttempted && (if (isPlain) searchHigherFormats else
                    refresh && currentLyrics.type == LyricsType.Synced.name),
                needUnsyncedFetch = refresh && unsyncedNotAttempted && isPlain,
            )
        }

        // Manual modes look up their requested format even if displaying a fallback.
        val needKaraokeFetch = (lyricsType == LyricsType.Karaoke || lyricsType == LyricsType.Synced) &&
            fetchNeededFor(
                row = karaoke,
                dedupOk = karaokeNotAttempted,
                absentRowCondition = lyricsType != LyricsType.Synced || !hasWordTimings(currentLyrics?.data),
                isUnsynced = false,
                now = now,
                forceRetry = forceAutoRetry,
            )

        val needSyncedFetch = lyricsType == LyricsType.Synced &&
            fetchNeededFor(
                row = synced,
                dedupOk = syncedNotAttempted,
                absentRowCondition = true,
                isUnsynced = false,
                now = now,
                forceRetry = forceAutoRetry,
            )

        val needUnsyncedFetch = lyricsType == LyricsType.Unsynced &&
            fetchNeededFor(
                row = unsynced,
                dedupOk = unsyncedNotAttempted,
                absentRowCondition = true,
                isUnsynced = true,
                now = now,
                forceRetry = forceAutoRetry,
            )

        return FetchNeeds(
            currentLyrics = currentLyrics,
            needKaraokeFetch = needKaraokeFetch,
            needSyncedFetch = needSyncedFetch,
            needUnsyncedFetch = needUnsyncedFetch
        )
    }

    /**
     * Unified per-row decision for one active check.
     *
     * - row absent / data empty: fetch if dedup allows; [absentRowCondition] carries the
     *   Karaoke hunt gate in Synced mode (off when the displayed row already has word timings)
     * - row edited by the user (non-empty): never fetched (gh-765)
     * - row with word timings: re-fetched only after [REFRESH_TTL_MILLIS]; a `null`
     *   [Lyrics.lastFetchedAt] (row stored before the column existed) counts as expired
     * - row without word timings: Karaoke/Synced keep the dedup-based "hunt a better version"
     *   behaviour; Unsynced is re-fetched after the TTL
     */
    private fun fetchNeededFor(
        row: Lyrics?,
        dedupOk: Boolean,
        absentRowCondition: Boolean,
        isUnsynced: Boolean,
        now: Long,
        forceRetry: Boolean,
    ): Boolean = when {
        row == null || row.data.isNullOrEmpty() -> dedupOk && absentRowCondition
        row.isEdited -> false
        forceRetry -> dedupOk
        hasWordTimings(row.data) -> isTtlExpired(row.lastFetchedAt, now)
        isUnsynced -> isTtlExpired(row.lastFetchedAt, now)
        else -> dedupOk
    }

    private fun isTtlExpired(lastFetchedAt: Long?, now: Long): Boolean =
        lastFetchedAt == null || lastFetchedAt > now || now - lastFetchedAt >= REFRESH_TTL_MILLIS

    private fun hasWordTimings(data: String?): Boolean =
        data?.lines()?.any { it.trim().startsWith("<") && it.contains(":") && it.contains(">") } == true
}
