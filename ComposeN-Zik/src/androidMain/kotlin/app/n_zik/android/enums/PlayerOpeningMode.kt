package app.n_zik.android.enums

import androidx.annotation.StringRes
import app.n_zik.android.R

internal enum class PlayerOpeningMode(@StringRes val textId: Int) {
    Audio(R.string.player_audio_mode),
    Video(R.string.player_video_mode),
    RememberLast(R.string.settings_player_opening_mode_remember_last);

    fun opensInVideo(lastModeWasVideo: Boolean): Boolean = when (this) {
        Audio -> false
        Video -> true
        RememberLast -> lastModeWasVideo
    }
}
