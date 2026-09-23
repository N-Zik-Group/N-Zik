package app.n_zik.android.core.database.ext

import androidx.room.ColumnInfo
import androidx.room.Embedded
import app.it.fast4x.rimusic.models.Song

/**
 * A [Song] with its listening totals inside one Rewind window
 * (see [app.n_zik.android.core.database.RewindEventSource.findSongListeningStatsBetween]).
 *
 * The windowed play count is exposed as [playCount]; its backing SQL column is
 * aliased `playCountTotal` because [Song.playCount] is already a column on the
 * entity and a same-name alias would shadow it.
 */
data class SongListeningStat(
    @Embedded val song: Song,
    @ColumnInfo(name = "playCountTotal") val playCount: Long,
    val playTimeMs: Long
)
