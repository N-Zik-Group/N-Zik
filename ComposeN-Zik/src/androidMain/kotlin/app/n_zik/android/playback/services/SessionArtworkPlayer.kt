package app.n_zik.android.playback.services

import app.n_zik.android.appTextResources
import java.util.IdentityHashMap

import android.media.MediaMetadata.METADATA_KEY_DURATION
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.cleanPrefix
import app.n_zik.android.utils.cropVideoThumbnailsKey
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.preferences
import app.n_zik.android.R
import app.n_zik.android.BuildConfig
import timber.log.Timber
import app.n_zik.android.appContext
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper

/** Publishes lightweight session metadata with profile-owned artwork identity. */
@UnstableApi
class SessionArtworkPlayer(upstream: Player, private val now: () -> Long = { System.currentTimeMillis() }) : ForwardingPlayer(upstream) {
    private val listeners = IdentityHashMap<Player.Listener, Player.Listener>()
    private var profile = getActiveProfile(appContext())
    private var square = appContext().preferences.getBoolean(cropVideoThumbnailsKey, true)
    private var expires = now() + 24 * 60 * 60 * 1000L
    private var localArtwork: LocalSessionArtwork? = null
    private var observedArtworkSource: String? = null
    private var observedArtworkRevision = 0L
    private var durationMediaId: String? = null
    private var knownDurationMs: Long? = null
    private var lastDurationDiagnostic: String? = null

    private data class LocalSessionArtwork(
        val source: String,
        val songId: String,
        val profile: String,
        val revision: Long,
        val uri: Uri,
    )

    private fun localArtworkUri(mediaId: String, source: String): Uri? {
        if (!source.startsWith("file:") && !source.startsWith('/')) return null
        val songId = LocalArtworkRules.songIdOf(mediaId)
        val revision = ImageCacheFactory.artworkSourceRevision(source, profile)
        val cached = localArtwork
        if (cached?.source == source && cached.songId == songId &&
            cached.profile == profile && cached.revision == revision
        ) return cached.uri
        val path = source.takeIf { it.startsWith('/') } ?: source.toUri().path ?: return null
        val file = SessionMediaItemMapper.resolveArtworkFile(source, path)
        val uri = runCatching { ArtworkContentProvider.uriForSource(appContext(), songId, file) }
            .getOrElse { ArtworkContentProvider.uriFor(songId) }
        localArtwork = LocalSessionArtwork(source, songId, profile, revision, uri)
        return uri
    }

    override fun getBufferedPosition(): Long = when (val buffered = super.getBufferedPosition()) {
        // Media3's legacy bridge copies these values into platform playback state unchanged.
        C.TIME_UNSET, C.TIME_END_OF_SOURCE -> currentPosition.coerceAtLeast(0L)
        else -> buffered
    }

    override fun getContentBufferedPosition(): Long = when (val buffered = super.getContentBufferedPosition()) {
        C.TIME_UNSET, C.TIME_END_OF_SOURCE -> contentPosition.coerceAtLeast(0L)
        else -> buffered
    }

    override fun addListener(listener: Player.Listener) {
        if (listener in listeners) return
        val translated = object : ExplicitForwardingPlayerListener(listener) {
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
        expires = now() + 24 * 60 * 60 * 1000L
        observedArtworkRevision = ImageCacheFactory.artworkSourceRevision(observedArtworkSource, profile)
        publishMetadata()
    }

    /** Republishes metadata when pixels change behind the same URI. */
    internal fun refreshChangedArtwork(): Boolean {
        val source = observedArtworkSource ?: return false
        val revision = ImageCacheFactory.artworkSourceRevision(source, profile)
        if (revision == observedArtworkRevision) return false
        observedArtworkRevision = revision
        publishMetadata()
        return true
    }

    /** Called on the player thread when stream resolution learns a finite track length. */
    internal fun refreshDuration() {
        if (currentMediaItem == null || isCurrentMediaItemLive) return
        publishMetadata()
    }

    private fun publishMetadata() {
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
        val selectedArtwork = currentItem?.mediaMetadata?.takeIf { it.extras?.getBoolean("isCoverModified") == true }
            ?.artworkUri
        val source = selectedArtwork?.toString()
            ?: metadata.extras?.getString(SessionMediaItemMapper.ORIGINAL_ARTWORK_SOURCE_EXTRA)
                ?.let(::cleanPrefix) ?: metadata.artworkUri?.toString()
        if (now() >= expires) expires = now() + 24 * 60 * 60 * 1000L
        val revision = ImageCacheFactory.artworkSourceRevision(source, profile)
        if (source != observedArtworkSource) {
            observedArtworkSource = source
            observedArtworkRevision = revision
        }
        val providerArtworkUri = source?.takeIf { currentItem != null }?.let {
            ArtworkContentProvider.uriForRemote(it, profile, square, revision, expires)
        } ?: selectedArtwork?.takeIf { it.authority == ArtworkContentProvider.AUTHORITY }
        ?: metadata.artworkUri?.takeIf { it.authority == ArtworkContentProvider.AUTHORITY }
        ?: mediaId?.let { id -> source?.let { localArtworkUri(id, it) } }
        ?: mediaId?.takeIf {
            LocalArtworkRules.needsProviderArtwork(it, source, ArtworkContentProvider.AUTHORITY)
        }?.let { ArtworkContentProvider.uriFor(LocalArtworkRules.songIdOf(it)) }
        val needsTitleFallback = hasLoadedTrack && SessionMetadataFallbacks.needsFallback(metadata.title)
        val needsArtistFallback = hasLoadedTrack && SessionMetadataFallbacks.needsFallback(metadata.artist)
        if (durationMediaId != mediaId || isCurrentMediaItemLive) {
            durationMediaId = mediaId
            knownDurationMs = null
        }
        if (mediaId != null && !isCurrentMediaItemLive) {
            duration.takeIf { it > 0 }?.let { knownDurationMs = it }
        }
        // Media3 prefers the real player duration over this declared-duration fallback.
        val declaredDuration = currentItem?.let {
            knownDurationMs
                ?: metadata.durationMs?.takeIf { it > 0 }
                ?: it.mediaMetadata.durationMs?.takeIf { duration -> duration > 0 }
                ?: SessionDurationMetadata.fromDurationText(metadata.extras?.getString("durationText"))
                ?: SessionDurationMetadata.fromDurationText(it.mediaMetadata.extras?.getString("durationText"))
                ?: ResolvedSessionDuration.forMediaId(it.mediaId)
        }?.takeUnless { isCurrentMediaItemLive }
        if (declaredDuration != null) knownDurationMs = declaredDuration
        if (BuildConfig.DEBUG && currentItem != null) {
            val diagnostic = "id=$mediaId state=$playbackState engineMs=$duration declaredMs=$declaredDuration " +
                "itemMs=${currentItem?.mediaMetadata?.durationMs} " +
                "cachedMs=${mediaId?.let(ResolvedSessionDuration::forMediaId)} live=$isCurrentMediaItemLive"
            if (diagnostic != lastDurationDiagnostic) {
                lastDurationDiagnostic = diagnostic
                Timber.tag("SessionDuration").d(diagnostic)
            }
        }
        val hasReservedDuration = metadata.extras?.containsKey(METADATA_KEY_DURATION) == true
        if (providerArtworkUri == null && !needsTitleFallback && !needsArtistFallback &&
            declaredDuration == metadata.durationMs && !hasReservedDuration
        ) return metadata
        return metadata.buildUpon().apply {
            setDurationMs(declaredDuration)
            // Legacy conversion applies extras last, so a reserved key could clobber the real duration.
            if (hasReservedDuration) setExtras(Bundle(metadata.extras).apply { remove(METADATA_KEY_DURATION) })
            providerArtworkUri?.let { setArtworkUri(it); setArtworkData(null, null) }
            if (needsTitleFallback) setTitle(appTextResources().getString(R.string.metadata_unknown_title))
            if (needsArtistFallback) setArtist(appTextResources().getString(R.string.metadata_unknown_artist))
        }.build()
    }
}
