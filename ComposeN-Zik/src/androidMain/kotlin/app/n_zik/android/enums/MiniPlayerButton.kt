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
    Repeat(R.drawable.repeat, R.string.player_mini_button_repeat),
    Like(R.drawable.heart, R.string.player_action_like),
    AddToPlaylist(R.drawable.add_in_playlist, R.string.player_mini_action_add_to_playlist),
    Download(R.drawable.download, R.string.player_mini_button_download),
    Share(R.drawable.share_social, R.string.player_mini_button_share),
    Radio(R.drawable.radio, R.string.player_mini_button_radio),
    AudioOutput(R.drawable.devices, R.string.player_mini_button_audio_output),
    SleepTimer(R.drawable.sleep, R.string.player_mini_button_sleep_timer),
    Lyrics(R.drawable.song_lyrics, R.string.player_mini_button_lyrics),
    Visualizer(R.drawable.sound_effect, R.string.player_mini_button_visualizer),
    Queue(R.drawable.reorder, R.string.player_mini_button_queue),
    Video(R.drawable.video, R.string.player_mini_button_video),
    Discover(R.drawable.discover, R.string.player_mini_button_discover)
}

enum class PendingMiniPlayerAction {
    Lyrics,
    Visualizer,
    Queue,
    SleepTimer,
    Video
}
