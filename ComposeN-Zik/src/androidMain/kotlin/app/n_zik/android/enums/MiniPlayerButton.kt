package app.n_zik.android.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.n_zik.android.R

enum class MiniPlayerButton(
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int
) {
    PlayPause(R.drawable.play, R.string.player_mini_button_play_pause),
    SkipBack(R.drawable.play_skip_back, R.string.player_action_previous),
    SkipForward(R.drawable.play_skip_forward, R.string.player_action_next),
    Shuffle(R.drawable.shuffle, R.string.player_mini_action_shuffle),
    SmartShuffle(R.drawable.smart_shuffle, R.string.recommendations_action_smart_shuffle),
    Repeat(R.drawable.repeat, R.string.player_action_repeat),
    Like(R.drawable.heart, R.string.player_action_like),
    AddToPlaylist(R.drawable.playlist, R.string.playlist_actions_title),
    Download(R.drawable.download, R.string.player_mini_button_download),
    Share(R.drawable.share_social, R.string.player_mini_button_share),
    Radio(R.drawable.radio, R.string.player_mini_button_radio),
    AudioOutput(R.drawable.devices, R.string.audio_output_title),
    SleepTimer(R.drawable.sleep, R.string.player_mini_button_sleep_timer),
    Lyrics(R.drawable.song_lyrics, R.string.lyrics_title),
    Visualizer(R.drawable.sound_effect, R.string.player_mini_button_visualizer),
    Queue(R.drawable.reorder, R.string.player_mini_button_queue),
    Discover(R.drawable.discover, R.string.player_mini_button_discover)
}

enum class PendingMiniPlayerAction {
    Lyrics,
    Visualizer,
    Queue,
    SleepTimer,
}
