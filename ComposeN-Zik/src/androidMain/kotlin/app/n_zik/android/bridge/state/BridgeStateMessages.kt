package app.n_zik.android.bridge.state

import app.n_zik.android.bridge.BridgeContract
import app.n_zik.android.bridge.BridgeJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val hasArtwork: Boolean,
)

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
    val speed: Float,
    val positionMs: Long,
    val repeatMode: RepeatModeDto,
    val shuffle: Boolean,
) : RevisedMessage

/** Deltas of contract §7.2: each one is exactly `revision + 1`. */
internal sealed interface DeltaMessage : RevisedMessage

@Serializable
@SerialName("playbackChanged")
internal data class PlaybackChangedMessage(
    override val revision: Long,
    val serverTimeMs: Long,
    val isPlaying: Boolean,
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
