package app.n_zik.android.bridge.state

import androidx.media3.common.C
import app.n_zik.android.bridge.AudioOutput
import app.n_zik.android.bridge.BridgeContract
import app.n_zik.android.bridge.BridgeJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `Track.like` (contract §1.1, since 1.7): the phone's like tri-state. */
@Serializable
internal enum class TrackLike {
    @SerialName("liked") LIKED,
    @SerialName("neutral") NEUTRAL,
    @SerialName("disliked") DISLIKED;

    companion object {
        fun parse(value: String?): TrackLike? = entries.firstOrNull { it.name.lowercase() == value }

        /**
         * The phone's `likedAt` column: `null` = neutral, a positive timestamp = liked, `-1` =
         * disliked. `0` is neutral too: the phone's `isLiked` only counts `likedAt > 0`, and the
         * writers only ever store `null`, a timestamp or `-1`.
         */
        fun of(likedAt: Long?): TrackLike = when {
            likedAt == null -> NEUTRAL
            likedAt > 0 -> LIKED
            likedAt < 0 -> DISLIKED
            else -> NEUTRAL
        }

        /** The explicit setter's argument of `SongTable.likeState(songId, Boolean?)`. */
        fun toColumnValue(state: TrackLike): Boolean? = when (state) {
            LIKED -> true
            DISLIKED -> false
            NEUTRAL -> null
        }
    }
}

/** `Track` shared object (contract §1.1). */
@Serializable
internal data class TrackDto(
    val id: String,
    val title: String,
    val artists: String?,
    val durationMs: Long?,
    val source: TrackSource,
    val isDownloaded: Boolean,
    val isLiked: Boolean,
    /** Since 1.7: the like tri-state; `isLiked` is its `liked` projection (contract §1.1). */
    val like: TrackLike,
    val hasArtwork: Boolean,
    /** Since 1.3: explicit content, the source of the phone's "E" badge. */
    val isExplicit: Boolean = false,
    /** Since 1.7.1: the song's total play time in ms (the phone's `totalPlayTimeMs` column); the library reads only. */
    val totalPlayTimeMs: Long = 0L,
    /** Since 1.7.1: the song's play count (the phone's `playCount` column); the library reads only. */
    val playCount: Int = 0,
    /** Since 1.7.1: the phone's active download state; `none` when settled (the row icon derives from `isDownloaded`). */
    val downloadState: TrackDownloadState = TrackDownloadState.NONE,
    /** Since 1.7.1: the download progress, 0..1, only while [downloadState] is `downloading`. */
    val downloadProgress: Float? = null,
    /** Since 1.7.1: the song is in the phone's streaming cache (the phone's row icon color, apart from its state). */
    val isCached: Boolean = false,
    /** Since 1.7.2: the track's artwork is a phone-local custom image (the phone's `isCustomImage` predicate — it shows it with `Crop`, every other artwork with `FillHeight`). */
    val isCustomArtwork: Boolean = false,
)

/** `Track.downloadState` (contract §1.1, since 1.7.1): the phone's active download states, the settled ones apart. */
@Serializable
internal enum class TrackDownloadState {
    /** No active download: the settled icon derives from `Track.isDownloaded` (and `Track.isCached` for its color). */
    @SerialName("none") NONE,

    /** Queued or restarting: the phone's `download_progress` icon. */
    @SerialName("queued") QUEUED,

    /** Downloading: the phone's progress ring, `Track.downloadProgress` in 0..1. */
    @SerialName("downloading") DOWNLOADING,
}

@Serializable
internal enum class TrackSource {
    @SerialName("online") ONLINE,
    @SerialName("local") LOCAL,
}

/** `RepeatMode` (contract §1.1). */
@Serializable
internal enum class RepeatModeDto {
    @SerialName("off") OFF,
    @SerialName("one") ONE,
    @SerialName("all") ALL,
}

/**
 * Server → client WebSocket messages of contract §6.3 and §7. The `type` field is the
 * class discriminator of [BridgeJson] (its default discriminator name is `type`).
 */
@Serializable
internal sealed interface BridgeServerMessage

/** Messages carrying a `revision`: the snapshot and the deltas (contract §7.1, §7.2). */
internal sealed interface RevisedMessage : BridgeServerMessage {
    val revision: Long
}

/** Full state (contract §7.1): first message of every session and answer to `requestSnapshot`. */
@Serializable
@SerialName("snapshot")
internal data class SnapshotMessage(
    override val revision: Long,
    val serverTimeMs: Long,
    val queue: List<TrackDto>,
    val currentIndex: Int,
    val currentTrackId: String?,
    val isPlaying: Boolean,
    /** Since 1.4 (contract §7.1): buffering (loading a track, or rebuffering while playing). */
    val isBuffering: Boolean = false,
    /** Since 1.5 (contract §7.1): the player's live duration, [C.TIME_UNSET] while it is not known yet. */
    val durationMs: Long = C.TIME_UNSET,
    val speed: Float,
    val positionMs: Long,
    val repeatMode: RepeatModeDto,
    val shuffle: Boolean,
    /** Since 1.2 (contract §7.1); a 1.1 client ignores it. */
    val audioOutput: AudioOutput,
) : RevisedMessage

/** Deltas of contract §7.2: each one is exactly `revision + 1`. */
internal sealed interface DeltaMessage : RevisedMessage

@Serializable
@SerialName("playbackChanged")
internal data class PlaybackChangedMessage(
    override val revision: Long,
    val serverTimeMs: Long,
    val isPlaying: Boolean,
    /** Since 1.4 (contract §7.2). */
    val isBuffering: Boolean = false,
    /** Since 1.5 (contract §7.2): the player's live duration, [C.TIME_UNSET] while it is not known yet. */
    val durationMs: Long = C.TIME_UNSET,
    val speed: Float,
    val positionMs: Long,
) : DeltaMessage

@Serializable
@SerialName("trackChanged")
internal data class TrackChangedMessage(
    override val revision: Long,
    val serverTimeMs: Long,
    val currentIndex: Int,
    val currentTrackId: String?,
    val positionMs: Long,
    val isPlaying: Boolean,
    /** Since 1.4 (contract §7.2): a new track loads, so the buffering state travels with the change. */
    val isBuffering: Boolean = false,
    /** Since 1.5 (contract §7.2): a new track loads, so the live duration (reset to [C.TIME_UNSET]) travels with it. */
    val durationMs: Long = C.TIME_UNSET,
) : DeltaMessage

@Serializable
@SerialName("queueChanged")
internal data class QueueChangedMessage(
    override val revision: Long,
    val serverTimeMs: Long,
    val queue: List<TrackDto>,
    val currentIndex: Int,
    val currentTrackId: String?,
) : DeltaMessage

@Serializable
@SerialName("modesChanged")
internal data class ModesChangedMessage(
    override val revision: Long,
    val serverTimeMs: Long,
    val repeatMode: RepeatModeDto,
    val shuffle: Boolean,
) : DeltaMessage

/** Change of the audio output (contract §7.2, §8.5, since 1.2). */
@Serializable
@SerialName("outputChanged")
internal data class OutputChangedMessage(
    override val revision: Long,
    val serverTimeMs: Long,
    val audioOutput: AudioOutput,
) : DeltaMessage

/**
 * Library change (contract §7.2, §10.3, since 1.7.3, feature `library.live`): a write to the
 * phone's library (a client §10.2 write or the phone's own UI) or an edit of its library
 * presentation settings (the sort menu). [kind] is the changed entity — `"songs"`, `"albums"`,
 * `"artists"` or `"playlists"`; the delta carries no content, the client re-queries the
 * invalidated families (§10.3).
 */
@Serializable
@SerialName("libraryChanged")
internal data class LibraryChangedMessage(
    override val revision: Long,
    val serverTimeMs: Long,
    val kind: String,
) : DeltaMessage

/** Light, non-revised snapshot sent every 10 s (contract §7.3). */
@Serializable
@SerialName("heartbeat")
internal data class HeartbeatMessage(
    val revision: Long,
    val serverTimeMs: Long,
    val positionMs: Long,
    val isPlaying: Boolean,
    val speed: Float,
) : BridgeServerMessage

/** Immediate answer to a client `ping` (contract §6.3). */
@Serializable
@SerialName("pong")
internal data class PongMessage(
    val clientTimeMs: Long,
    val serverReceiveTimeMs: Long,
    val serverSendTimeMs: Long,
) : BridgeServerMessage

/**
 * Late failure of a command already confirmed over REST (contract §7.6). Not revised:
 * it never affects the revision.
 */
@Serializable
@SerialName(BridgeContract.TYPE_ERROR)
internal data class ErrorMessage(
    val code: String,
    val message: String,
    val commandId: String?,
) : BridgeServerMessage

/** Wire form of [message]: one JSON object with its `type` (contract §6.1). */
internal fun encodeServerMessage(message: BridgeServerMessage): String =
    BridgeJson.encodeToString(BridgeServerMessage.serializer(), message)
