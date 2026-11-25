package app.n_zik.android.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.n_zik.android.enums.MiniPlayerButton
import app.n_zik.android.utils.miniPlayerButtonOrderKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.showMiniPlayerAddToPlaylistKey
import app.n_zik.android.utils.showMiniPlayerAudioOutputKey
import app.n_zik.android.utils.showMiniPlayerDiscoverKey
import app.n_zik.android.utils.showMiniPlayerDownloadKey
import app.n_zik.android.utils.showMiniPlayerLikeKey
import app.n_zik.android.utils.showMiniPlayerLyricsKey
import app.n_zik.android.utils.showMiniPlayerPlayPauseKey
import app.n_zik.android.utils.showMiniPlayerQueueKey
import app.n_zik.android.utils.showMiniPlayerRadioKey
import app.n_zik.android.utils.showMiniPlayerRepeatKey
import app.n_zik.android.utils.showMiniPlayerShareKey
import app.n_zik.android.utils.showMiniPlayerShuffleKey
import app.n_zik.android.utils.showMiniPlayerSkipBackKey
import app.n_zik.android.utils.showMiniPlayerSkipForwardKey
import app.n_zik.android.utils.showMiniPlayerSleepTimerKey
import app.n_zik.android.utils.showMiniPlayerVisualizerKey
import org.json.JSONArray
import org.json.JSONException

internal val defaultMiniPlayerButtonOrder = listOf(
    "skip_back", "play_pause", "skip_forward", "audio_output",
    "queue", "shuffle", "repeat", "radio", "sleep_timer",
    "like", "add_to_playlist", "download", "share", "discover", "lyrics", "visualizer",
)

internal fun parseMiniPlayerOrder(serialized: String): List<String> {
    if (serialized.isBlank()) return defaultMiniPlayerButtonOrder
    return try {
        val array = JSONArray(serialized)
        val savedIds = List(array.length()) { array.getString(it) }
            .filter { it in defaultMiniPlayerButtonOrder }
        savedIds + defaultMiniPlayerButtonOrder.filterNot { it in savedIds }
    } catch (_: JSONException) {
        defaultMiniPlayerButtonOrder
    }
}

@Composable
internal fun rememberMiniPlayerButtons(): List<MiniPlayerButton> {
    val miniPlayerButtonIdToButton = mapOf(
        "play_pause" to MiniPlayerButton.PlayPause,
        "skip_back" to MiniPlayerButton.SkipBack,
        "skip_forward" to MiniPlayerButton.SkipForward,
        "shuffle" to MiniPlayerButton.Shuffle,
        "repeat" to MiniPlayerButton.Repeat,
        "like" to MiniPlayerButton.Like,
        "add_to_playlist" to MiniPlayerButton.AddToPlaylist,
        "download" to MiniPlayerButton.Download,
        "share" to MiniPlayerButton.Share,
        "radio" to MiniPlayerButton.Radio,
        "audio_output" to MiniPlayerButton.AudioOutput,
        "sleep_timer" to MiniPlayerButton.SleepTimer,
        "lyrics" to MiniPlayerButton.Lyrics,
        "visualizer" to MiniPlayerButton.Visualizer,
        "queue" to MiniPlayerButton.Queue,
        "discover" to MiniPlayerButton.Discover,
    )

    val orderSerialized by rememberPreference(miniPlayerButtonOrderKey, "")
    val orderedIds = remember(orderSerialized) { parseMiniPlayerOrder(orderSerialized) }

    val togglePlayPause by rememberPreference(showMiniPlayerPlayPauseKey, true)
    val toggleSkipBack by rememberPreference(showMiniPlayerSkipBackKey, true)
    val toggleSkipForward by rememberPreference(showMiniPlayerSkipForwardKey, true)
    val toggleShuffle by rememberPreference(showMiniPlayerShuffleKey, false)
    val toggleRepeat by rememberPreference(showMiniPlayerRepeatKey, false)
    val toggleLike by rememberPreference(showMiniPlayerLikeKey, false)
    val toggleAddToPlaylist by rememberPreference(showMiniPlayerAddToPlaylistKey, false)
    val toggleDownload by rememberPreference(showMiniPlayerDownloadKey, false)
    val toggleShare by rememberPreference(showMiniPlayerShareKey, false)
    val toggleRadio by rememberPreference(showMiniPlayerRadioKey, false)
    val toggleAudioOutput by rememberPreference(showMiniPlayerAudioOutputKey, true)
    val toggleSleepTimer by rememberPreference(showMiniPlayerSleepTimerKey, false)
    val toggleLyrics by rememberPreference(showMiniPlayerLyricsKey, false)
    val toggleVisualizer by rememberPreference(showMiniPlayerVisualizerKey, false)
    val toggleQueue by rememberPreference(showMiniPlayerQueueKey, false)
    val toggleDiscover by rememberPreference(showMiniPlayerDiscoverKey, false)
    val toggleMap = remember(togglePlayPause, toggleSkipBack, toggleSkipForward, toggleShuffle, toggleRepeat, toggleLike, toggleAddToPlaylist, toggleDownload, toggleShare, toggleRadio, toggleAudioOutput, toggleSleepTimer, toggleLyrics, toggleVisualizer, toggleQueue, toggleDiscover) {
        mapOf(
            "play_pause" to togglePlayPause,
            "skip_back" to toggleSkipBack,
            "skip_forward" to toggleSkipForward,
            "shuffle" to toggleShuffle,
            "repeat" to toggleRepeat,
            "like" to toggleLike,
            "add_to_playlist" to toggleAddToPlaylist,
            "download" to toggleDownload,
            "share" to toggleShare,
            "radio" to toggleRadio,
            "audio_output" to toggleAudioOutput,
            "sleep_timer" to toggleSleepTimer,
            "lyrics" to toggleLyrics,
            "visualizer" to toggleVisualizer,
            "queue" to toggleQueue,
            "discover" to toggleDiscover,
        )
    }

    return remember(toggleMap, orderedIds) {
        orderedIds.filter { id -> toggleMap[id] == true }
            .mapNotNull { id -> miniPlayerButtonIdToButton[id] }
    }
}
