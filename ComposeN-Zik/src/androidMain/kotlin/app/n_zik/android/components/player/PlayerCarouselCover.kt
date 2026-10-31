package app.n_zik.android.components.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.LocalGlassOverlayArtworkBackdrop
import app.n_zik.android.components.glass.glassContentCaptureEnabled
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.player.lyrics.LyricsScreen
import app.n_zik.android.core.database.Database
import app.n_zik.android.extensions.nextvisualizer.views.NextVisualizer

/** An embedded cover uses card-sized overlay controls even when saved standalone preferences differ. */
val LocalPlayerCoverOverlay = staticCompositionLocalOf { false }

/** Carousel overlays always occupy the playing card without changing saved single-cover preferences. */
data class PlayerCoverOverlayPolicy(val lyricsOnCover: Boolean, val visualizerOnCover: Boolean)

fun playerCoverOverlayPolicy(
    carousel: Boolean,
    showThumbnail: Boolean,
    lyricsOnCover: Boolean,
    visualizerOnCover: Boolean,
): PlayerCoverOverlayPolicy {
    val keepCarousel = carousel && showThumbnail
    return PlayerCoverOverlayPolicy(keepCarousel || lyricsOnCover, keepCarousel || visualizerOnCover)
}

/** The same artwork preference gate is used in portrait and landscape. */
fun playerCoverArtworkVisible(
    showThumbnail: Boolean,
    showingLyrics: Boolean,
    lyricsOnCover: Boolean,
    showingVisualizer: Boolean,
    visualizerOnCover: Boolean,
): Boolean = showThumbnail && ((!showingLyrics && !showingVisualizer) ||
    (showingLyrics && lyricsOnCover) || (showingVisualizer && visualizerOnCover))

/** Without a visible thumbnail, the visualizer falls back to the standalone player area. */
fun playerStandaloneVisualizerVisible(
    showThumbnail: Boolean,
    showingVisualizer: Boolean,
    visualizerOnCover: Boolean,
): Boolean = showingVisualizer && (!showThumbnail || !visualizerOnCover)

internal data class PlayerCoverOverlayVisibility(val lyrics: Boolean, val visualizer: Boolean)

internal fun playerCoverOverlayVisibility(
    page: Int,
    playingIndex: Int,
    pageMediaId: String?,
    playingMediaId: String?,
    showingLyrics: Boolean,
    lyricsOnCover: Boolean,
    showingVisualizer: Boolean,
    visualizerOnCover: Boolean,
    hasPlaybackError: Boolean,
): PlayerCoverOverlayVisibility {
    val playingPage = page == playingIndex && pageMediaId != null && pageMediaId == playingMediaId
    return PlayerCoverOverlayVisibility(
        lyrics = playingPage && showingLyrics && lyricsOnCover && !hasPlaybackError,
        visualizer = playingPage && showingVisualizer && visualizerOnCover,
    )
}

/** Keeps overlays attached to the playing artwork while adjacent carousel pages stay artwork only. */
@UnstableApi
@Composable
fun PlayerCarouselCover(
    page: Int,
    playingIndex: Int,
    mediaItem: MediaItem?,
    playingMediaId: String?,
    showingLyrics: Boolean,
    lyricsOnCover: Boolean,
    showingVisualizer: Boolean,
    visualizerOnCover: Boolean,
    hasPlaybackError: Boolean,
    onDismissLyrics: () -> Unit,
    durationProvider: () -> Long,
    clickLyricsText: Boolean,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    artwork: @Composable () -> Unit,
) {
    val visibility = playerCoverOverlayVisibility(
        page, playingIndex, mediaItem?.mediaId, playingMediaId,
        showingLyrics, lyricsOnCover, showingVisualizer, visualizerOnCover, hasPlaybackError,
    )
    PlayerCoverOverlayHost(
        visibility = visibility,
        modifier = modifier,
        artwork = artwork,
        lyrics = {
            if (mediaItem != null) LyricsScreen(
                mediaId = mediaItem.mediaId,
                isDisplayed = visibility.lyrics,
                onDismiss = onDismissLyrics,
                ensureSongInserted = { Database.insertIgnore(mediaItem) },
                size = Dimensions.thumbnails.player.song,
                mediaMetadataProvider = mediaItem::mediaMetadata,
                durationProvider = durationProvider,
                isLandscape = isLandscape,
                clickLyricsText = clickLyricsText,
            )
        },
        visualizer = { NextVisualizer(isDisplayed = visibility.visualizer) },
    )
}

@Composable
internal fun PlayerCoverOverlayHost(
    visibility: PlayerCoverOverlayVisibility,
    modifier: Modifier = Modifier,
    artwork: @Composable () -> Unit,
    lyrics: @Composable () -> Unit,
    visualizer: @Composable () -> Unit,
) {
    val captureLyricsArtwork = visibility.lyrics && coverOverlayNeedsArtwork(GlassScope.Lyrics)
    val captureVisualizerArtwork = visibility.visualizer && coverOverlayNeedsArtwork(GlassScope.Visualizer)
    val artworkBackdrop = if (captureLyricsArtwork || captureVisualizerArtwork) rememberLayerBackdrop() else null
    Box(modifier) {
        // Keep artwork in the same composition slot when optical capture is toggled.
        // Only a visible optical overlay records it; neighboring/native pages allocate no layer.
        Box(Modifier.fillMaxSize().then(
            if (artworkBackdrop != null) Modifier.layerBackdrop(artworkBackdrop) else Modifier
        )) { artwork() }
        CompositionLocalProvider(
            LocalGlassOverlayArtworkBackdrop provides artworkBackdrop,
            LocalPlayerCoverOverlay provides true,
            // The card is its own viewport; the global information plate may sit beside/above it.
            LocalPlayerTransportGeometry provides null,
        ) {
            if (visibility.lyrics) lyrics()
            if (visibility.visualizer) visualizer()
        }
    }
}

@Composable
private fun coverOverlayNeedsArtwork(scope: GlassScope): Boolean {
    val settings = rememberGlassSettings(scope)
    return settings.usesGlassMaterial(GlassArea.ActionButtons) && glassContentCaptureEnabled(settings)
}
