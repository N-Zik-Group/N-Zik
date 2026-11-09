package app.n_zik.android.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import app.n_zik.android.R

internal fun RemoteViews.setPlaybackDescriptions(
    context: Context,
    artwork: Int,
    previous: Int,
    playPause: Int,
    next: Int,
    like: Int,
) {
    val strings = ContextCompat.getContextForLanguage(context).resources
    setContentDescription(artwork, strings.getString(R.string.album_art))
    setContentDescription(previous, strings.getString(R.string.common_action_previous))
    setContentDescription(playPause, strings.getString(R.string.widget_action_play_pause))
    setContentDescription(next, strings.getString(R.string.common_action_next))
    setContentDescription(like, strings.getString(R.string.player_action_like))
}
