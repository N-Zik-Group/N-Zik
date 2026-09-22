package app.n_zik.android.core.database.ext

import androidx.room.Embedded
import app.it.fast4x.rimusic.models.Artist

data class ArtistListeningStat(
    @Embedded val artist: Artist,
    val playTimeMs: Long,
    val songCount: Long
)
