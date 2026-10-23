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
        now: Long
    ): FetchNeeds {
        val currentLyrics = when (lyricsType) {
            LyricsType.Auto -> {
                allLyrics.find { it.type == LyricsType.Karaoke.name }
                    ?: allLyrics.find { it.type == LyricsType.Synced.name }
                    ?: allLyrics.find { it.type == LyricsType.Unsynced.name }
            }
            LyricsType.Karaoke -> allLyrics.find { it.type == LyricsType.Karaoke.name }
            LyricsType.Synced -> allLyrics.find { it.type == LyricsType.Synced.name }
                ?: allLyrics.find { it.type == LyricsType.Karaoke.name }
            LyricsType.Unsynced -> allLyrics.find { it.type == LyricsType.Unsynced.name }
        }

        // Each active check is decided from its own row only; the cascade above is for display
        // purposes.
        //
        // Active checks per mode: Karaoke -> K, Synced -> K+S, Unsynced -> U, Auto -> K+S+U.
        // Absent rows are always wanted so fallback rows stay maintained,
        //
        // and every dedup-based hunt below stays dedup-gated. One extra gate: the Karaoke
        // hunt stays off in Synced mode when the displayed row already carries word timings.
        val needKaraokeFetch = (lyricsType == LyricsType.Karaoke || lyricsType == LyricsType.Synced || lyricsType == LyricsType.Auto) &&
            fetchNeededFor(
                row = allLyrics.find { it.type == LyricsType.Karaoke.name },
                dedupOk = globalLastKaraokeAttemptMediaId != mediaId,
                absentRowCondition = lyricsType != LyricsType.Synced || !hasWordTimings(currentLyrics?.data),
                isUnsynced = false,
                now = now
            )

        val needSyncedFetch = (lyricsType == LyricsType.Synced || lyricsType == LyricsType.Auto) &&
            fetchNeededFor(
                row = allLyrics.find { it.type == LyricsType.Synced.name },
                dedupOk = globalLastSyncedAttemptMediaId != mediaId,
                absentRowCondition = true,
                isUnsynced = false,
                now = now
            )

        val needUnsyncedFetch = (lyricsType == LyricsType.Unsynced || lyricsType == LyricsType.Auto) &&
            fetchNeededFor(
                row = allLyrics.find { it.type == LyricsType.Unsynced.name },
                dedupOk = globalLastUnSyncedAttemptMediaId != mediaId,
                absentRowCondition = true,
                isUnsynced = true,
                now = now
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
        now: Long
    ): Boolean = when {
        row == null || row.data.isNullOrEmpty() -> dedupOk && absentRowCondition
        row.isEdited -> false
        hasWordTimings(row.data) -> isTtlExpired(row.lastFetchedAt, now)
        isUnsynced -> isTtlExpired(row.lastFetchedAt, now)
        else -> dedupOk
    }

    private fun isTtlExpired(lastFetchedAt: Long?, now: Long): Boolean =
        lastFetchedAt == null || lastFetchedAt > now || now - lastFetchedAt >= REFRESH_TTL_MILLIS

    private fun hasWordTimings(data: String?): Boolean =
        data?.lines()?.any { it.trim().startsWith("<") && it.contains(":") && it.contains(">") } == true
}
