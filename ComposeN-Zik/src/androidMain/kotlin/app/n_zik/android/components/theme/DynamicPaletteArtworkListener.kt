package app.n_zik.android.components.theme

import android.net.Uri
import androidx.media3.common.Player
import timber.log.Timber

/** Publishes late artwork metadata as well as the first queue transition to the global theme. */
internal class DynamicPaletteArtworkListener(
    private val player: Player,
    private val onArtworkChanged: (Uri?) -> Unit,
) : Player.Listener {
    private var lastArtwork: Pair<String?, Uri?>? = null

    fun publishCurrentArtwork() {
        val item = player.currentMediaItem
        val artwork = item?.mediaId to
            (player.mediaMetadata.artworkUri ?: item?.mediaMetadata?.artworkUri)
        if (artwork == lastArtwork) return
        lastArtwork = artwork
        Timber.tag("GlassTintFlow").d("Theme artwork changed: media=%s, available=%s", artwork.first, artwork.second != null)
        onArtworkChanged(artwork.second)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_MEDIA_METADATA_CHANGED,
                Player.EVENT_TIMELINE_CHANGED,
            )) {
            publishCurrentArtwork()
        }
    }
}
