package app.n_zik.android.components.history

import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.core.database.ext.EventWithSong

/** Group visible names without changing the stored events used for playback or removal. */
internal fun historyTitleGroups(events: List<EventWithSong>): Map<String, List<EventWithSong>> =
    events.sortedBy { cleanPrefix(it.song.title) }
        .groupBy { cleanPrefix(it.song.title).firstOrNull()?.uppercase() ?: "#" }

internal fun historyArtistGroups(events: List<EventWithSong>): Map<String, List<EventWithSong>> =
    events.sortedBy { it.song.artistsText?.let(::cleanPrefix) }
        .groupBy { it.song.artistsText?.let(::cleanPrefix)?.split(",")?.first()?.trim() ?: "Unknown" }
