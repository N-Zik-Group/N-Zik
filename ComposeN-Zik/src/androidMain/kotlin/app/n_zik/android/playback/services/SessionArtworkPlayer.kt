package app.n_zik.android.playback.services

import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.utils.cropVideoThumbnailsKey
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.playback.services.automotive.models.ArtworkRevisions
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper

/** Publishes lightweight session metadata with profile-owned artwork identity. */
@UnstableApi
class SessionArtworkPlayer(upstream: Player, private val now: () -> Long = { System.currentTimeMillis() }) : ForwardingPlayer(upstream) {
    private val listeners = linkedMapOf<Player.Listener, Player.Listener>()
    private var profile = getActiveProfile(appContext())
    private var square = appContext().preferences.getBoolean(cropVideoThumbnailsKey, true)
    private var revision = ArtworkRevisions.current()
    private var expires = now() + 24 * 60 * 60 * 1000L

    override fun addListener(listener: Player.Listener) {
        if (listener in listeners) return
        val translated = object : Player.Listener by listener {
            override fun onMediaMetadataChanged(metadata: MediaMetadata) = listener.onMediaMetadataChanged(mediaMetadata)
            override fun onEvents(player: Player, events: Player.Events) = listener.onEvents(this@SessionArtworkPlayer, events)
        }
        listeners[listener] = translated
        super.addListener(translated)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)?.let { super.removeListener(it) }
    }

    /** Called on the player thread after profile, crop or connectivity policy changes. */
    fun refreshArtwork() {
        profile = getActiveProfile(appContext())
        square = appContext().preferences.getBoolean(cropVideoThumbnailsKey, true)
        revision = ArtworkRevisions.current()
        expires = now() + 24 * 60 * 60 * 1000L
        val metadata = mediaMetadata
        val events = Player.Events(FlagSet.Builder().add(Player.EVENT_MEDIA_METADATA_CHANGED).build())
        listeners.keys.toList().forEach {
            it.onMediaMetadataChanged(metadata)
            it.onEvents(this, events)
        }
    }

    override fun getMediaMetadata(): MediaMetadata {
        val metadata = super.getMediaMetadata()
        val currentItem = currentMediaItem
        val hasLoadedTrack = currentItem != null && playbackState != Player.STATE_IDLE
        val mediaId = currentItem?.mediaId
        val source = metadata.extras?.getString(SessionMediaItemMapper.ORIGINAL_ARTWORK_SOURCE_EXTRA)
            ?.let(::cleanPrefix) ?: metadata.artworkUri?.toString()
        if (now() >= expires) expires = now() + 24 * 60 * 60 * 1000L
        val providerArtworkUri = source?.takeIf { currentItem != null }?.let {
            ArtworkContentProvider.uriForRemote(it, profile, square, revision, expires)
        } ?: metadata.artworkUri?.takeIf { it.authority == ArtworkContentProvider.AUTHORITY }
        ?: mediaId?.takeIf {
            LocalArtworkRules.needsProviderArtwork(it, source, ArtworkContentProvider.AUTHORITY)
        }?.let { ArtworkContentProvider.uriFor(LocalArtworkRules.songIdOf(it)) }
        val needsTitleFallback = hasLoadedTrack && SessionMetadataFallbacks.needsFallback(metadata.title)
        val needsArtistFallback = hasLoadedTrack && SessionMetadataFallbacks.needsFallback(metadata.artist)
        if (providerArtworkUri == null && !needsTitleFallback && !needsArtistFallback) return metadata
        return metadata.buildUpon().apply {
            providerArtworkUri?.let { setArtworkUri(it); setArtworkData(null, null) }
            if (needsTitleFallback) setTitle(appContext().getString(R.string.unknown_title))
            if (needsArtistFallback) setArtist(appContext().getString(R.string.unknown_artist))
        }.build()
    }
}
