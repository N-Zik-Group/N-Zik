package app.it.fast4x.rimusic.enums

import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.CommandSearch
import android.app.PendingIntent
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import app.n_zik.android.R
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.CommandSearch
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.CommandStartRadio
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.CommandToggleDownload
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.CommandToggleLike
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.CommandToggleDiscover
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.CommandToggleRepeatMode
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.CommandToggleShuffle
import app.n_zik.android.playback.services.PlayerServiceModern
import app.kreate.android.me.knighthat.enums.TextView

enum class NotificationButtons(
    @field:StringRes override val textId: Int,
    @field:DrawableRes override val iconId: Int
): TextView, Drawable {

    Download( R.string.download, R.drawable.download ),

    Favorites( R.string.favorites, R.drawable.heart_outline ),

    Repeat( R.string.repeat, R.drawable.repeat ),

    Shuffle( R.string.shuffle, R.drawable.shuffle ),

    Radio( R.string.start_radio, R.drawable.radio ),

    // Issue #866 (gh-866): discover command button (notification + AA overflow) — joins the
    // "other" command buttons; its active-state icon (discover_stop) is applied by
    // PlayerServiceModern.commandButtonIconRes.
    Discover( R.string.discover, R.drawable.discover ),

    Search( R.string.search, R.drawable.search );

    val sessionCommand: SessionCommand
    get() = when (this) {
        Download -> CommandToggleDownload
        Favorites -> CommandToggleLike
        Repeat -> CommandToggleRepeatMode
        Shuffle -> CommandToggleShuffle
        Radio -> CommandStartRadio
        Discover -> CommandToggleDiscover
        Search -> CommandSearch
    }

    val pendingIntent: PendingIntent
        @OptIn(UnstableApi::class)
        get() = when (this) {
            Download -> PlayerServiceModern.Action.download.pendingIntent
            Favorites -> PlayerServiceModern.Action.like.pendingIntent
            Repeat -> PlayerServiceModern.Action.repeat.pendingIntent
            Shuffle -> PlayerServiceModern.Action.shuffle.pendingIntent
            Radio -> PlayerServiceModern.Action.playradio.pendingIntent
            Discover -> PlayerServiceModern.Action.discover.pendingIntent
            Search -> PlayerServiceModern.Action.search.pendingIntent
        }

    @OptIn(UnstableApi::class)
    fun getStateIcon(button: NotificationButtons, likedState: Long?, downloadState: Int, repeatMode: Int, shuffleMode: Boolean): Int {
        return when (button) {
            Download -> when (downloadState) {
                androidx.media3.exoplayer.offline.Download.STATE_COMPLETED -> R.drawable.downloaded
                androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING,
                androidx.media3.exoplayer.offline.Download.STATE_QUEUED -> R.drawable.download_progress
                else -> R.drawable.download
            }
            Favorites -> when (likedState) {
                -1L -> R.drawable.heart_dislike
                null -> R.drawable.heart_outline
                else -> R.drawable.heart
            }
            Repeat -> when (repeatMode) {
                REPEAT_MODE_OFF -> R.drawable.repeat
                REPEAT_MODE_ONE -> R.drawable.repeatone
                REPEAT_MODE_ALL -> R.drawable.infinite
                else -> throw IllegalStateException()
            }
            Shuffle -> if (shuffleMode) R.drawable.shuffle_filled else R.drawable.shuffle
            Radio -> R.drawable.radio
            Discover -> R.drawable.discover
            Search -> R.drawable.search
        }
    }
}



