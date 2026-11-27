/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package app.n_zik.android.listentogether

import android.content.res.Resources
import androidx.annotation.StringRes
import app.n_zik.android.R

/**
 * Connection state for the Listen Together feature
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR,
}

/**
 * Room role for the current user
 */
enum class RoomRole {
    HOST,
    GUEST,
    NONE,
}

/**
 * Log entry for debugging
 */
data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    @StringRes val messageRes: Int,
    val details: LogDetail? = null,
)

fun interface LogDetail {
    fun resolve(resources: Resources): String
}

// Stable legacy wire reasons. Resolve them in the recipient's locale.
internal const val LISTEN_TOGETHER_BLOCKED_REASON = "Blocked by host"
internal const val LISTEN_TOGETHER_JOIN_BLOCKED_REASON = "You are blocked"

enum class LogLevel(@StringRes val labelRes: Int) {
    INFO(R.string.listen_together_log_level_info),
    WARNING(R.string.listen_together_log_level_warning),
    ERROR(R.string.listen_together_log_level_error),
    DEBUG(R.string.listen_together_log_level_debug),
}

/**
 * Pending action to execute when connected
 */
sealed class PendingAction {
    data class CreateRoom(
        val username: String,
    ) : PendingAction()

    data class JoinRoom(
        val roomCode: String,
        val username: String,
    ) : PendingAction()
}

/**
 * Event types for the Listen Together client
 */
sealed class ListenTogetherEvent {
    // Connection events
    data class Connected(
        val userId: String,
    ) : ListenTogetherEvent()

    data object Disconnected : ListenTogetherEvent()

    data class ConnectionError(
        val error: String,
        val isServerConfigurationError: Boolean = false,
    ) : ListenTogetherEvent()

    data class Reconnecting(
        val attempt: Int,
        val maxAttempts: Int,
    ) : ListenTogetherEvent()

    // Room events
    data class RoomCreated(
        val roomCode: String,
        val userId: String,
    ) : ListenTogetherEvent()

    data class JoinRequestReceived(
        val userId: String,
        val username: String,
    ) : ListenTogetherEvent()

    data class JoinApproved(
        val roomCode: String,
        val userId: String,
        val state: RoomState,
    ) : ListenTogetherEvent()

    data class JoinRejected(
        val reason: String,
    ) : ListenTogetherEvent()

    data class UserJoined(
        val userId: String,
        val username: String,
    ) : ListenTogetherEvent()

    data class UserLeft(
        val userId: String,
        val username: String,
    ) : ListenTogetherEvent()

    data class HostChanged(
        val newHostId: String,
        val newHostName: String,
    ) : ListenTogetherEvent()

    data class Kicked(
        val reason: String,
    ) : ListenTogetherEvent()

    data object SuggestionApproved : ListenTogetherEvent()

    data class SuggestionRejected(
        val reason: String,
    ) : ListenTogetherEvent()

    data class Reconnected(
        val roomCode: String,
        val userId: String,
        val state: RoomState,
        val isHost: Boolean,
    ) : ListenTogetherEvent()

    data class UserReconnected(
        val userId: String,
        val username: String,
    ) : ListenTogetherEvent()

    data class UserDisconnected(
        val userId: String,
        val username: String,
    ) : ListenTogetherEvent()

    // Playback events
    data class PlaybackSync(
        val action: PlaybackActionPayload,
    ) : ListenTogetherEvent()

    data class BufferWait(
        val trackId: String,
        val waitingFor: List<String>,
    ) : ListenTogetherEvent()

    data class BufferComplete(
        val trackId: String,
    ) : ListenTogetherEvent()

    data class SyncStateReceived(
        val state: SyncStatePayload,
    ) : ListenTogetherEvent()

    // Error events
    data class ServerError(
        val code: String,
        val message: String,
    ) : ListenTogetherEvent()
}
