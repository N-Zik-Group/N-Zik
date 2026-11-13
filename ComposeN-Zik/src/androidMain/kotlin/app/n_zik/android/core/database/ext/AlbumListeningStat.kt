package app.n_zik.android.core.database.ext

import androidx.room.Embedded
import app.n_zik.android.models.Album

data class AlbumListeningStat(
    @Embedded val album: Album,
    val playTimeMs: Long,
    val songCount: Long
)
