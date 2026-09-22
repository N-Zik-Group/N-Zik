package app.n_zik.android.core.database.ext

import androidx.room.Embedded
import app.it.fast4x.rimusic.models.PlaylistPreview

data class PlaylistListeningStat(
    @Embedded val playlist: PlaylistPreview,
    val playTimeMs: Long
)
