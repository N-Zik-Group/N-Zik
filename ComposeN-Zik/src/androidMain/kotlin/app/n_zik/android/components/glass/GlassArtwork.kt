package app.n_zik.android.components.glass

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import app.n_zik.android.components.player.extractM3ECoverColors
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber

internal data class GlassArtwork(val mediaId: String?, val uri: String?)

private fun Player.glassArtwork(): GlassArtwork {
    val item = currentMediaItem
    return GlassArtwork(
        item?.mediaId,
        (mediaMetadata.artworkUri ?: item?.mediaMetadata?.artworkUri)?.toString(),
    )
}

/** Observes metadata as well as queue transitions, including artwork replaced on the same song. */
@Composable
internal fun rememberGlassArtwork(player: Player?): GlassArtwork? {
    var artwork by remember(player) { mutableStateOf(player?.glassArtwork()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_MEDIA_METADATA_CHANGED,
                        Player.EVENT_TIMELINE_CHANGED,
                    )) {
                    artwork = player.glassArtwork()
                }
            }
        }
        player?.addListener(listener)
        artwork = player?.glassArtwork()
        onDispose { player?.removeListener(listener) }
    }
    return artwork
}

/** Uses NZik's shared dominant-cover extraction rather than an unrelated vibrant accent. */
internal suspend fun extractGlassArtworkColor(bitmap: Bitmap, isDark: Boolean): Color? =
    extractM3ECoverColors(bitmap, isDark)?.dominant?.let(::Color)
        ?: withContext(NzikDispatchers.MEDIA) {
            // Palette's default filter excludes pure white/black covers entirely.
            Palette.from(bitmap).clearFilters().maximumColorCount(8).generate()
                .dominantSwatch?.rgb?.let(::Color)
        }

/** Clears the preceding song's tint immediately while loading the current cover. */
@Composable
fun rememberGlassArtworkColor(player: Player?, enabled: Boolean, isDark: Boolean): Color? {
    val artwork = rememberGlassArtwork(player)
    return rememberGlassArtworkColor(artwork, enabled, isDark) { current, dark ->
        ImageCacheFactory.loadBitmap(current.uri, allowHardware = false)?.let {
            extractGlassArtworkColor(it, dark)
        }
    }
}

@Composable
internal fun rememberGlassArtworkColor(
    artwork: GlassArtwork?,
    enabled: Boolean,
    isDark: Boolean,
    loadColor: suspend (GlassArtwork, Boolean) -> Color?,
): Color? {
    var color by remember(artwork, enabled, isDark) { mutableStateOf<Color?>(null) }
    LaunchedEffect(artwork, enabled, isDark) {
        if (!enabled || artwork?.uri == null) return@LaunchedEffect
        try {
            Timber.tag("GlassTintFlow").d("Glass artwork load: media=%s", artwork.mediaId)
            val loaded = loadColor(artwork, isDark)
            currentCoroutineContext().ensureActive()
            color = loaded
            Timber.tag("GlassTintFlow").d("Glass artwork ready: media=%s, color=%s",
                artwork.mediaId, loaded?.toArgb()?.toUInt()?.toString(16))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("GlassArtwork").w(error, "Artwork tint unavailable")
        }
    }
    return color
}
