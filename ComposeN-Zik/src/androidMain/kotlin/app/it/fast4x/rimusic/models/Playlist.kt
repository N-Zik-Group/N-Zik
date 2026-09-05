package app.it.fast4x.rimusic.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.it.fast4x.rimusic.cleanPrefix

@Immutable
@Entity
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val browseId: String? = null,
    val isEditable: Boolean = true,
    val isYoutubePlaylist: Boolean = false,
    val isAutoSync: Boolean = false,
    val position: Int = -1
) {
    fun cleanName() = cleanPrefix( this.name )
}



