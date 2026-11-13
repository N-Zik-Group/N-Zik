package app.n_zik.android.core.database.ext

import androidx.room.Embedded
import app.n_zik.android.models.PlaylistPreview

data class PlaylistListeningStat(
    @Embedded val playlist: PlaylistPreview,
    val playTimeMs: Long
)
