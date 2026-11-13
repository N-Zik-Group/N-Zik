package app.n_zik.android.core.database.ext

import androidx.room.Embedded
import app.n_zik.android.models.Artist

data class ArtistListeningStat(
    @Embedded val artist: Artist,
    val playTimeMs: Long,
    val songCount: Long
)
