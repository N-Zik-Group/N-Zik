// Metrolist Project (C) 2026, licensed under GPL-3.0. See git history for contributors.

package app.n_zik.android.listentogether

import kotlinx.coroutines.flow.MutableStateFlow

// Preserve the live room reads and publication order inside the guarded copies.
internal fun MutableStateFlow<RoomState?>.applyPlaybackAction(payload: PlaybackActionPayload) {
    when (payload.action) {
        PlaybackActions.PLAY -> {
            value =
                value?.copy(
                    isPlaying = true,
                    position = payload.positionOrNull ?: value!!.position,
                    lastUpdate = payload.serverTimeOrNull ?: value!!.lastUpdate,
                    revision = maxOf(value!!.revision, payload.revision),
                )
        }

        PlaybackActions.PAUSE -> {
            value =
                value?.copy(
                    isPlaying = false,
                    position = payload.positionOrNull ?: value!!.position,
                    lastUpdate = payload.serverTimeOrNull ?: value!!.lastUpdate,
                    revision = maxOf(value!!.revision, payload.revision),
                )
        }

        PlaybackActions.SEEK -> {
            value =
                value?.copy(
                    position = payload.positionOrNull ?: value!!.position,
                    lastUpdate = payload.serverTimeOrNull ?: value!!.lastUpdate,
                    revision = maxOf(value!!.revision, payload.revision),
                )
        }

        PlaybackActions.CHANGE_TRACK -> {
            value =
                value?.copy(
                    currentTrack = payload.trackInfoOrNull,
                    isPlaying = false,
                    position = 0,
                    lastUpdate = payload.serverTimeOrNull ?: value!!.lastUpdate,
                    queue = if (payload.revision > 0L) payload.queue.orEmpty() else value!!.queue,
                    revision = maxOf(value!!.revision, payload.revision),
                )
        }

        PlaybackActions.QUEUE_ADD -> {
            val ti = payload.trackInfoOrNull
            if (ti != null) {
                val currentQueue = value?.queue ?: emptyList()
                value =
                    value?.copy(
                        queue =
                            if (payload.revision > 0L) {
                                payload.queue.orEmpty()
                            } else if (payload.insertNext == true) {
                                listOf(ti) + currentQueue
                            } else {
                                currentQueue + ti
                            },
                    )
            }
        }

        PlaybackActions.QUEUE_REMOVE -> {
            val id = payload.trackIdOrNull
            if (!id.isNullOrEmpty()) {
                val currentQueue = value?.queue ?: emptyList()
                value =
                    value?.copy(
                        queue = if (payload.revision > 0L) payload.queue.orEmpty() else currentQueue.filter { it.id != id },
                    )
            }
        }

        PlaybackActions.QUEUE_CLEAR -> {
            value =
                value?.copy(
                    queue = if (payload.revision > 0L) payload.queue.orEmpty() else emptyList(),
                )
        }

        PlaybackActions.SYNC_QUEUE -> {
            value = value?.copy(queue = payload.queue.orEmpty())
        }

        PlaybackActions.SET_VOLUME -> {
            val vol = payload.volumeOrNull
            if (vol != null) {
                value = value?.copy(volume = vol.coerceIn(0f, 1f))
            }
        }
    }

    value =
        value?.copy(
            revision = maxOf(value!!.revision, payload.revision),
        )
}

internal fun MutableStateFlow<RoomState?>.applyPlaybackSnapshot(payload: SyncStatePayload) {
    value =
        value?.copy(
            currentTrack = payload.currentTrackOrNull,
            isPlaying = payload.isPlaying,
            position = payload.position,
            lastUpdate = payload.lastUpdate,
            volume = payload.volume,
            queue = payload.queue ?: value!!.queue,
            revision = maxOf(value!!.revision, payload.revision),
        )
}
