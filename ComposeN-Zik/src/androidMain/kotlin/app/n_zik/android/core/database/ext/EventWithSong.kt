package app.n_zik.android.core.database.ext

import androidx.room.Embedded
import androidx.room.Relation
import app.n_zik.android.models.Event
import app.n_zik.android.models.Song

data class EventWithSong(
    @Embedded val event: Event,
    @Relation(
        entity = Song::class,
        parentColumn = "songId",
        entityColumn = "id"
    )
    val song: Song
)

