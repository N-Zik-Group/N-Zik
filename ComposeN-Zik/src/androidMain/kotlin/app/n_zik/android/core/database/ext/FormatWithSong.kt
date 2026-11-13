package app.n_zik.android.core.database.ext

import androidx.room.Embedded
import app.n_zik.android.models.Format
import app.n_zik.android.models.Song

data class FormatWithSong(
    @Embedded val format: Format,
    @Embedded val song: Song
)

