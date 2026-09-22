package app.n_zik.android.core.database.ext

import androidx.room.Embedded
import app.it.fast4x.rimusic.models.Album

data class AlbumListeningStat(
    @Embedded val album: Album,
    val playTimeMs: Long,
    val songCount: Long
)
