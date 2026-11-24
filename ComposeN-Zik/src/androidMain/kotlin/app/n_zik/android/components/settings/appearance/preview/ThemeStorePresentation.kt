package app.n_zik.android.components.settings.appearance.preview

import java.io.File

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.typography
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import timber.log.Timber

// Same portrait frame as the official catalogue assets.
internal const val StorePreviewAspectRatio = 780f / 1736f

internal data class StoreCardPreview(val file: File, val aspectRatio: Float)

@Composable
internal fun rememberStorePreview(theme: StoreTheme, preview: StorePreview?, catalog: ThemeStoreCatalog, refreshVersion: Int): StoreCardPreview? {
    return androidx.compose.runtime.key(catalog, preview?.sha256) {
        val cached = preview?.let { catalog.previewMetadata.get(it.sha256) }
        val image by produceState(cached, catalog, theme, preview, refreshVersion) {
            if (preview == null || cached != null) return@produceState
            val file = catalog.preview(theme, preview) ?: return@produceState
            value = withContext(NzikDispatchers.DATA) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    StoreCardPreview(file, bounds.outWidth.toFloat() / bounds.outHeight).also {
                        catalog.previewMetadata.put(preview.sha256, it)
                    }
                } else {
                    null
                }
            }
        }
        image
    }
}

@Composable
internal fun rememberVerifiedCreatorIds(catalog: ThemeStoreCatalog, refreshVersion: Int): Set<Long>? {
    val ids by produceState<Set<Long>?>(null, catalog, refreshVersion) {
        try {
            value = catalog.verifiedCreatorIds()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("ThemeStore").w(error, "Could not load verified creators")
        }
    }
    return ids
}

@Composable
internal fun OfficialCreatorBadge(official: Boolean) {
    if (!official) return
    Icon(
        painter = painterResource(R.drawable.shield_checkmark),
        contentDescription = stringResource(R.string.theme_store_official),
        tint = colorPalette().accent,
        modifier = Modifier.size(14.dp).clip(uiRoundnessShape()).clickable {
            Toaster.i(R.string.theme_store_official)
        },
    )
}

@Composable
internal fun StorePreviewImage(
    file: File?,
    description: String?,
    showImageRim: Boolean = true,
    fitPortraitFrame: Boolean = false,
    modifier: Modifier = Modifier,
    crossfade: Boolean? = null,
    onSuccess: (() -> Unit)? = null,
) {
    val fitImage = fitPortraitFrame && file != null
    ImageCacheFactory.LocalAsyncImage(
        model = file,
        cacheKey = file?.absolutePath,
        contentDescription = description,
        modifier = modifier.fillMaxSize().then(if (fitImage) Modifier.background(Color.Black) else Modifier),
        contentScale = if (fitImage) ContentScale.Fit else ContentScale.Crop,
        shape = if (showImageRim) uiRoundnessShape() else null,
        crossfade = crossfade,
        onSuccess = onSuccess?.let { report -> { _ -> report() } },
    )
}

private data class StorePreviewFrame(val file: File, val description: String?)

/** Keeps the decoded image on screen until the new variant can replace it. */
@Composable
internal fun FadingStorePreviewImage(file: File?, description: String?) {
    var current by remember { mutableStateOf<StorePreviewFrame?>(null) }
    var revealing by remember { mutableStateOf<StorePreviewFrame?>(null) }
    val requested = file?.let { StorePreviewFrame(it, description) }
    val latestRequest by rememberUpdatedState(requested)
    val incoming = revealing ?: requested?.takeIf { it.file != current?.file }
    val opacity = remember(incoming?.file) { Animatable(0f) }
    val returningToCurrent = current != null && requested?.file == current?.file
    LaunchedEffect(revealing, returningToCurrent) {
        val next = revealing ?: return@LaunchedEffect
        if (returningToCurrent) {
            opacity.animateTo(0f, tween(250))
            revealing = null
            return@LaunchedEffect
        }
        if (current != null) opacity.animateTo(1f, tween(250))
        current = next
        revealing = null
    }

    Box(Modifier.fillMaxSize()) {
        if (current == null && incoming == null) {
            StorePreviewImage(null, description, showImageRim = false, fitPortraitFrame = true)
        }
        for (frame in listOfNotNull(current, incoming)) key(frame.file) {
            val overlaysCurrent = current != null && frame.file != current?.file
            val reportReady: (() -> Unit)? = if (frame.file == current?.file) null else ({
                if (revealing == null && latestRequest?.file == frame.file) revealing = frame
            })
            StorePreviewImage(
                frame.file,
                if (overlaysCurrent) null else frame.description,
                showImageRim = false,
                fitPortraitFrame = true,
                modifier = if (overlaysCurrent) Modifier.graphicsLayer { alpha = opacity.value } else Modifier,
                crossfade = false,
                onSuccess = reportReady,
            )
        }
    }
}

@Composable
internal fun StoreLastSync(
    timestamp: Long,
    modifier: Modifier = Modifier,
    shimmer: Boolean = forceLoadingShimmerPreview(),
) {
    val context = LocalContext.current
    val date = java.util.Date(timestamp)
    val label = stringResource(
            R.string.theme_store_last_sync,
            android.text.format.DateFormat.getDateFormat(context).format(date),
            android.text.format.DateFormat.getTimeFormat(context).format(date),
    )
    val style = typography().xxs.copy(color = colorPalette().textSecondary, textAlign = TextAlign.Center)
    val line = modifier.fillMaxWidth().padding(horizontal = 20.dp)
    if (shimmer) Box(line, contentAlignment = Alignment.Center) {
        StoreLoadingLine(label, style)
    } else BasicText(label, modifier = line, style = style)
}

@Composable
internal fun StoreText(text: String, secondary: Boolean = false, style: TextStyle = typography().s, marquee: Boolean = false) {
    val palette = colorPalette()
    BasicText(
        text = text,
        modifier = if (marquee) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
        style = style.copy(color = if (secondary) palette.textSecondary else palette.text),
        maxLines = 1,
        overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

@Composable
internal fun storeTagLabel(tag: String): String = when (canonicalStoreTag(tag)) {
    "stock" -> stringResource(R.string.theme_store_tag_stock)
    "built-in" -> stringResource(R.string.theme_store_tag_builtin)
    "artwork" -> stringResource(R.string.theme_store_tag_artwork)
    "dark" -> stringResource(R.string.theme_store_tag_dark)
    "blur" -> stringResource(R.string.theme_store_tag_blur)
    "carousel" -> stringResource(R.string.theme_store_tag_carousel)
    "coverless" -> stringResource(R.string.theme_store_tag_coverless)
    "audio-waves" -> stringResource(R.string.theme_store_tag_waves)
    "animated-gradient" -> stringResource(R.string.theme_store_tag_animated)
    "gradient" -> stringResource(R.string.theme_store_tag_gradient)
    else -> canonicalStoreTag(tag).replace('-', ' ').replaceFirstChar { it.titlecase() }
}
