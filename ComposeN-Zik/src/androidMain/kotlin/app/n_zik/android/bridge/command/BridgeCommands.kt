package app.n_zik.android.bridge.command

import app.n_zik.android.bridge.AddPosition
import app.n_zik.android.bridge.BridgeContract
import app.n_zik.android.bridge.BridgeJson
import app.n_zik.android.bridge.EmptyCommandBody
import app.n_zik.android.bridge.QueueAddCommandBody
import app.n_zik.android.bridge.QueueItemCommandBody
import app.n_zik.android.bridge.QueueMoveCommandBody
import app.n_zik.android.bridge.QueuePlayCommandBody
import app.n_zik.android.bridge.RepeatCommandBody
import app.n_zik.android.bridge.SeekCommandBody
import app.n_zik.android.bridge.ShuffleCommandBody
import app.n_zik.android.bridge.SpeedCommandBody
import app.n_zik.android.bridge.state.RepeatModeDto
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonObject

/**
 * What a playback or queue command asks (contract §9). Queue indexes are in the effective
 * play order, the one of the published queue (shuffle order when enabled).
 */
internal sealed interface PlayerAction {
    /**
     * `false` only for play and pause, which a Listen Together guest may still use; every
     * other action is refused with `422 PLAYER_REJECTED` while the guest lock is on.
     */
    val guarded: Boolean get() = true

    data object Play : PlayerAction {
        override val guarded: Boolean get() = false
    }

    data object Pause : PlayerAction {
        override val guarded: Boolean get() = false
    }

    data class Seek(val positionMs: Long) : PlayerAction
    data object Next : PlayerAction
    data object Previous : PlayerAction
    data class Speed(val speed: Float) : PlayerAction
    data class Repeat(val mode: RepeatModeDto) : PlayerAction
    data class Shuffle(val enabled: Boolean) : PlayerAction
    data class QueuePlay(val trackIds: List<String>, val startIndex: Int, val positionMs: Long) : PlayerAction
    data class QueueAdd(val trackIds: List<String>, val position: AddPosition) : PlayerAction
    data class QueueRemove(val index: Int, val trackId: String) : PlayerAction
    data class QueueMove(val fromIndex: Int, val toIndex: Int, val trackId: String) : PlayerAction
    data class QueueJump(val index: Int, val trackId: String) : PlayerAction
    data object QueueClear : PlayerAction
}

/** A validated command and the client's `commandId`, copied into a late WS `error` (contract §7.6). */
internal data class BridgeCommand(val action: PlayerAction, val commandId: String? = null)

/** Outcome of a command, mapped to the HTTP answers of contract §9. */
internal sealed interface CommandResult {
    /** `200`: applied by the choke point; [revision] is the delta to wait for when [changed]. */
    data class Applied(val changed: Boolean, val revision: Long) : CommandResult

    /** `422 PLAYER_REJECTED`: refused, nothing applied. */
    data object Rejected : CommandResult

    /** `503 PLAYER_UNAVAILABLE`: no player service bound, nothing applied. */
    data object Unavailable : CommandResult

    /** `409 QUEUE_MISMATCH` carrying the current [revision], nothing applied. */
    data class QueueMismatch(val revision: Long) : CommandResult

    /** `404 NOT_FOUND`: a `trackId` is not in the library, nothing applied. */
    data object NotFound : CommandResult
}

/** Applies commands to the phone's player. Implementations serialize concurrent calls. */
internal fun interface BridgeCommandExecutor {
    suspend fun execute(command: BridgeCommand): CommandResult

    companion object {
        /** No player behind the server (e.g. a server without a player source): always `503`. */
        val UNAVAILABLE = BridgeCommandExecutor { CommandResult.Unavailable }
    }
}

/** Parsing result of a command request. */
internal sealed interface ParsedCommand {
    data class Valid(val command: BridgeCommand) : ParsedCommand

    /** Unknown route below `/player` or `/queue`: `404 NOT_FOUND`. */
    data object UnknownRoute : ParsedCommand

    /** Malformed JSON, a bound violated or an unknown enum value: `400 BAD_REQUEST`. */
    data object Invalid : ParsedCommand
}

/**
 * Pure parsing and validation of the command bodies of contract §9. [parse] takes the route
 * relative to `/api/v1` (`"player/seek"`) and the raw body, which must be a JSON object
 * (`{}` when the command has no field).
 */
internal object BridgeCommandParser {

    /** Decodes a body into its action and `commandId`; `null` when a bound is violated. */
    private fun interface Route {
        fun decode(json: JsonObject): Pair<PlayerAction, String?>?
    }

    private fun <T> route(deserializer: DeserializationStrategy<T>, build: (T) -> Pair<PlayerAction, String?>?) =
        Route { json -> build(BridgeJson.decodeFromJsonElement(deserializer, json)) }

    private fun simple(action: PlayerAction) = route(EmptyCommandBody.serializer()) { action to it.commandId }

    private fun validTrackIds(trackIds: List<String>) = trackIds.size in 1..BridgeContract.TRACK_IDS_MAX

    private val routes: Map<String, Route> = mapOf(
        "player/play" to simple(PlayerAction.Play),
        "player/pause" to simple(PlayerAction.Pause),
        "player/next" to simple(PlayerAction.Next),
        "player/previous" to simple(PlayerAction.Previous),
        "player/seek" to route(SeekCommandBody.serializer()) { body ->
            (PlayerAction.Seek(body.positionMs) to body.commandId).takeIf { body.positionMs >= 0 }
        },
        "player/speed" to route(SpeedCommandBody.serializer()) { body ->
            (PlayerAction.Speed(body.speed) to body.commandId)
                .takeIf { body.speed in BridgeContract.SPEED_MIN..BridgeContract.SPEED_MAX }
        },
        "player/repeat" to route(RepeatCommandBody.serializer()) { PlayerAction.Repeat(it.mode) to it.commandId },
        "player/shuffle" to route(ShuffleCommandBody.serializer()) { PlayerAction.Shuffle(it.enabled) to it.commandId },
        "queue/play" to route(QueuePlayCommandBody.serializer()) { body ->
            (PlayerAction.QueuePlay(body.trackIds, body.startIndex, body.positionMs) to body.commandId).takeIf {
                validTrackIds(body.trackIds) && body.startIndex in body.trackIds.indices && body.positionMs >= 0
            }
        },
        "queue/add" to route(QueueAddCommandBody.serializer()) { body ->
            (PlayerAction.QueueAdd(body.trackIds, body.position) to body.commandId).takeIf { validTrackIds(body.trackIds) }
        },
        // An index out of bounds (negative included) is a QUEUE_MISMATCH, decided against the live queue
        "queue/remove" to route(QueueItemCommandBody.serializer()) { PlayerAction.QueueRemove(it.index, it.trackId) to it.commandId },
        "queue/move" to route(QueueMoveCommandBody.serializer()) {
            PlayerAction.QueueMove(it.fromIndex, it.toIndex, it.trackId) to it.commandId
        },
        "queue/jump" to route(QueueItemCommandBody.serializer()) { PlayerAction.QueueJump(it.index, it.trackId) to it.commandId },
        "queue/clear" to simple(PlayerAction.QueueClear),
    )

    fun isKnown(route: String): Boolean = route in routes

    fun parse(route: String, body: String): ParsedCommand {
        val definition = routes[route] ?: return ParsedCommand.UnknownRoute
        return decode(definition, body) ?: ParsedCommand.Invalid
    }

    private fun decode(definition: Route, body: String): ParsedCommand? {
        val json = runCatching { BridgeJson.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        // A missing field, a wrong type or an unknown enum value fails the decoding
        val (action, commandId) = runCatching { definition.decode(json) }.getOrNull() ?: return null
        if (commandId != null && commandId.length > BridgeContract.COMMAND_ID_MAX_LENGTH) return null
        return ParsedCommand.Valid(BridgeCommand(action, commandId))
    }
}
