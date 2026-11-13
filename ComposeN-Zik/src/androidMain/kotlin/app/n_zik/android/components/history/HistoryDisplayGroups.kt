package app.n_zik.android.components.history

import app.n_zik.android.cleanPrefix
import app.n_zik.android.core.database.ext.EventWithSong

/** Group visible names without changing the stored events used for playback or removal. */
internal fun historyTitleGroups(events: List<EventWithSong>): Map<String, List<EventWithSong>> =
    events.sortedBy { cleanPrefix(it.song.title) }
        .groupBy { cleanPrefix(it.song.title).firstOrNull()?.uppercase() ?: "#" }

internal fun historyArtistGroups(events: List<EventWithSong>): Map<String?, List<EventWithSong>> =
    events.sortedBy { it.song.artistsText?.let(::cleanPrefix) }
        .groupBy { event ->
            event.song.artistsText?.let(::cleanPrefix)?.substringBefore(',')?.trim()?.takeIf(String::isNotEmpty)
        }
