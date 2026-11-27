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
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ForwardingTimeline
import app.n_zik.android.cleanPrefix
import app.n_zik.android.utils.cropVideoThumbnailsKey
import app.n_zik.android.utils.EXPLICIT_BUNDLE_TAG
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.preferences
import app.n_zik.android.R
import app.n_zik.android.BuildConfig
import timber.log.Timber
import app.n_zik.android.appContext
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.playback.services.automotive.models.ArtworkRevisions
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import app.n_zik.android.playback.utils.isSmartShuffleRecommendation
import app.n_zik.android.playback.utils.smartShuffleSessionTitle

/** Publishes lightweight session metadata with profile-owned artwork identity. */
@UnstableApi
class SessionArtworkPlayer(upstream: Player, private val now: () -> Long = { System.currentTimeMillis() }) : ForwardingPlayer(upstream) {
    private val listeners = IdentityHashMap<Player.Listener, Player.Listener>()
    private var profile = getActiveProfile(appContext())
    private val artistCreditProfile = profile
    private var artistCredit: Pair<String, SessionArtistCredit>? = null
    private var square = appContext().preferences.getBoolean(cropVideoThumbnailsKey, true)
    private var expires = now() + 24 * 60 * 60 * 1000L
    private var localArtwork: LocalSessionArtwork? = null
    private var providerArtwork: ProviderArtwork? = null
    private var observedArtwork: ArtworkObservation? = null
    private var artworkItem: MediaItem? = null
    private var artworkProfile = profile
    private var durationMediaId: String? = null
    private var knownDurationMs: Long? = null
    private var lastDurationDiagnostic: String? = null
    private var sourceTimeline: Timeline? = null
    private var sessionTimeline: Timeline? = null

    private data class LocalSessionArtwork(
        val source: String,
        val songId: String,
        val profile: String,
        val revision: Long,
        val uri: Uri,
    )

    private data class ArtworkObservation(
        val source: String?,
        val sourceRevision: Long,
        val providerUri: Uri?,
        val refreshedProviderUri: Uri?,
    )

    private data class ProviderArtwork(val uri: Uri, val sequence: Long, val refreshedUri: Uri?)

    private fun artworkObservation(source: String?, providerUri: Uri?): ArtworkObservation =
        ArtworkObservation(
            source,
            ImageCacheFactory.artworkSourceRevision(source, profile),
            providerUri,
            providerUri?.let(::currentProviderArtworkUri),
        )

    private fun currentProviderArtworkUri(uri: Uri): Uri? {
        if (uri.pathSegments == listOf("remote")) return uri
        val sequence = ArtworkRevisions.current()
        val cached = providerArtwork
        if (cached?.uri == uri && cached.sequence == sequence) return cached.refreshedUri
        val context = appContext()
        val scoped = ArtworkContentProvider.bindLocalRequestUri(context, profile, uri)
        val refreshedUri = scoped?.let { ArtworkContentProvider.refreshLocalUri(context, profile, it) }
        providerArtwork = ProviderArtwork(uri, sequence, refreshedUri)
        return refreshedUri
    }

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
        val context = appContext()
        val uri = runCatching { ArtworkContentProvider.uriForSource(context, profile, songId, file) }
            .getOrElse { ArtworkContentProvider.scopedUriFor(context, profile, songId) } ?: return null
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

    override fun getCurrentTimeline(): Timeline {
        val source = super.getCurrentTimeline()
        if (source === sourceTimeline) return sessionTimeline ?: source
        val published = if (source.isEmpty) source else object : ForwardingTimeline(source) {
            private val markedItems = IdentityHashMap<MediaItem, MediaItem>()

            override fun getWindow(windowIndex: Int, window: Timeline.Window, defaultPositionProjectionUs: Long): Timeline.Window {
                val result = super.getWindow(windowIndex, window, defaultPositionProjectionUs)
                val item = result.mediaItem
                if (item.isSmartShuffleRecommendation) {
                    result.mediaItem = markedItems.getOrPut(item) {
                        item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().apply {
                            applySmartShuffleTitle(item.mediaMetadata)
                        }.build()).build()
                    }
                }
                return result
            }
        }
        sourceTimeline = source
        sessionTimeline = published
        return published
    }

    override fun addListener(listener: Player.Listener) {
        if (listener in listeners) return
        val translated = object : ExplicitForwardingPlayerListener(listener) {
            override fun onMediaMetadataChanged(metadata: MediaMetadata) = listener.onMediaMetadataChanged(mediaMetadata)
            override fun onTimelineChanged(timeline: Timeline, reason: Int) = listener.onTimelineChanged(currentTimeline, reason)
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
        val selectedProfile = getActiveProfile(appContext())
        if (selectedProfile != profile) rememberArtworkItem(currentMediaItem)
        profile = selectedProfile
        square = appContext().preferences.getBoolean(cropVideoThumbnailsKey, true)
        expires = now() + 24 * 60 * 60 * 1000L
        localArtwork = null
        providerArtwork = null
        observedArtwork = null
        publishMetadata()
    }

    /** Republishes metadata when pixels change behind the same URI. */
    internal fun refreshChangedArtwork(): Boolean {
        val previous = observedArtwork ?: return false
        val current = artworkObservation(previous.source, previous.providerUri)
        if (current == previous) return false
        observedArtwork = current
        publishMetadata()
        return true
    }

    /** Called on the player thread when stream resolution learns a finite track length. */
    internal fun refreshDuration() {
        if (currentMediaItem == null || isCurrentMediaItemLive) return
        publishMetadata()
    }

    internal fun refreshTrackIdentity() = publishMetadata()

    internal fun artistTrackId(): String? =
        currentMediaItem?.sessionArtistTrackId()?.takeIf {
            it.isNotBlank() && getActiveProfile(appContext()) == artistCreditProfile
        }

    internal fun updateArtistCredit(trackId: String, credit: SessionArtistCredit?) {
        if (artistTrackId() != trackId) return
        val updatedCredit = credit?.let { trackId to it }
        if (artistCredit == updatedCredit) return
        val previousArtist = mediaMetadata.artist
        artistCredit = updatedCredit
        if (previousArtist != mediaMetadata.artist) publishMetadata()
    }

    private fun publishMetadata() {
        val metadata = mediaMetadata
        val events = Player.Events(FlagSet.Builder().add(Player.EVENT_MEDIA_METADATA_CHANGED).build())
        listeners.keys.toList().forEach {
            it.onMediaMetadataChanged(metadata)
            it.onEvents(this, events)
        }
    }

    private fun rememberArtworkItem(currentItem: MediaItem?) {
        // A retained item still belongs to the profile that supplied its artwork.
        if (artworkItem !== currentItem) {
            artworkItem = currentItem
            artworkProfile = profile
        }
    }

    override fun getMediaMetadata(): MediaMetadata {
        val currentItem = currentMediaItem
        val upstream = super.getMediaMetadata()
        val identity = currentItem?.mediaMetadata?.takeIf {
            it.extras?.getBoolean(COMMITTED_TRACK_IDENTITY_EXTRA) == true &&
                getActiveProfile(appContext()) == artistCreditProfile
        }
        val metadata = if (identity == null) upstream else upstream.buildUpon()
            .setTitle(identity.title)
            .setArtist(identity.artist)
            .setAlbumTitle(identity.albumTitle)
            .setExtras(Bundle(upstream.extras ?: Bundle()).apply { identity.extras?.let(::putAll) })
            .build()
        rememberArtworkItem(currentItem)
        val activeProfile = getActiveProfile(appContext())
        val ownsArtwork = (currentItem == null || artworkProfile == profile) && profile == activeProfile
        val hasLoadedTrack = currentItem != null && playbackState != Player.STATE_IDLE
        val mediaId = currentItem?.mediaId
        val selectedArtwork = currentItem?.mediaMetadata?.takeIf { it.extras?.getBoolean("isCoverModified") == true }
            ?.artworkUri
        val source = selectedArtwork?.toString()
            ?: metadata.extras?.getString(SessionMediaItemMapper.ORIGINAL_ARTWORK_SOURCE_EXTRA)
                ?.let(::cleanPrefix) ?: metadata.artworkUri?.toString()
        if (now() >= expires) expires = now() + 24 * 60 * 60 * 1000L
        val existingProviderUri = selectedArtwork?.takeIf { it.authority == ArtworkContentProvider.AUTHORITY }
            ?: metadata.artworkUri?.takeIf { it.authority == ArtworkContentProvider.AUTHORITY }
        val observation = if (ownsArtwork) artworkObservation(source, existingProviderUri) else null
        if (observation?.source != observedArtwork?.source || observation?.providerUri != observedArtwork?.providerUri) {
            observedArtwork = observation
        }
        val rejectedProvider = existingProviderUri != null && observation?.refreshedProviderUri == null
        val providerArtworkUri = observation?.takeUnless { rejectedProvider }?.let {
            source?.takeIf { currentItem != null }?.let { remote ->
                ArtworkContentProvider.uriForRemote(remote, profile, square, it.sourceRevision, expires)
            } ?: it.refreshedProviderUri
                ?: mediaId?.let { id -> source?.let { localArtworkUri(id, it) } }
                ?: mediaId?.takeIf {
                    LocalArtworkRules.needsProviderArtwork(it, source, ArtworkContentProvider.AUTHORITY)
                }?.let { ArtworkContentProvider.scopedUriFor(appContext(), profile, LocalArtworkRules.songIdOf(it)) }
        }
        val clearArtwork = !ownsArtwork || rejectedProvider
        val needsTitleFallback = hasLoadedTrack && SessionMetadataFallbacks.needsFallback(metadata.title)
        val needsArtistFallback = hasLoadedTrack && SessionMetadataFallbacks.needsFallback(metadata.artist)
        val correctedArtist = currentItem?.takeIf { hasLoadedTrack && artistCreditProfile == activeProfile }?.let { item ->
            artistCredit?.takeIf { it.first == item.sessionArtistTrackId() }?.second?.resolve(
                item, metadata, appTextResources().getString(R.string.metadata_unknown_artist),
            ) { artistId -> appContext().preferences.getString("old_browse_id_$artistId", null) }
        }?.takeUnless { it == metadata.artist?.toString() }
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
        val recommended = currentItem?.isSmartShuffleRecommendation == true
        if (providerArtworkUri == null && !clearArtwork && !needsTitleFallback && !needsArtistFallback && correctedArtist == null && !recommended &&
            declaredDuration == metadata.durationMs && !hasReservedDuration
        ) return metadata
        return metadata.buildUpon().apply {
            setDurationMs(declaredDuration)
            // Legacy conversion applies extras last, so a reserved key could clobber the real duration.
            if (hasReservedDuration) setExtras(Bundle(metadata.extras).apply { remove(METADATA_KEY_DURATION) })
            if (clearArtwork || providerArtworkUri != null) {
                setArtworkUri(providerArtworkUri)
                setArtworkData(null, null)
            }
            if (recommended) applySmartShuffleTitle(metadata, correctedArtist)
            else if (needsTitleFallback) setTitle(appTextResources().getString(R.string.metadata_unknown_title))
            if (correctedArtist != null) setArtist(correctedArtist)
            else if (needsArtistFallback) setArtist(appTextResources().getString(R.string.metadata_unknown_artist))
        }.build()
    }
}

private fun MediaMetadata.Builder.applySmartShuffleTitle(metadata: MediaMetadata, correctedArtist: String? = null) {
    val title = metadata.title?.takeUnless(SessionMetadataFallbacks::needsFallback)?.toString()?.let(::cleanPrefix)
        ?: appTextResources().getString(R.string.metadata_unknown_title)
    val explicit = metadata.extras?.getBoolean(EXPLICIT_BUNDLE_TAG) == true ||
        metadata.extras?.getBoolean("isExplicit") == true ||
        metadata.extras?.getBoolean("androidx.media3.session.EXTRAS_KEY_IS_EXPLICIT") == true
    val displayedTitle = smartShuffleSessionTitle(title, explicit)
    setTitle(displayedTitle)
    if (metadata.displayTitle != null) {
        setDisplayTitle(displayedTitle)
        setSubtitle(metadata.subtitle ?: correctedArtist ?: metadata.artist)
    }
}
