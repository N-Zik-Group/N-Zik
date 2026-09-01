package app.it.fast4x.rimusic.ui.components.themed

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import android.text.format.Formatter
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.core.coil.ImageCacheFactory

import app.n_zik.android.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.enums.CacheType
import app.it.fast4x.rimusic.enums.CoilDiskCacheMaxSize
import app.it.fast4x.rimusic.enums.ExoPlayerDiskCacheMaxSize
import app.it.fast4x.rimusic.enums.ExoPlayerDiskDownloadCacheMaxSize
import app.it.fast4x.rimusic.utils.coilDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.rememberPreference


@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun CacheSpaceIndicator(

    cacheType: CacheType = CacheType.Images,
    circularIndicator: Boolean = false,
    horizontalPadding: Dp = 12.dp,
    showCacheInfo: Boolean = true,
) {

    val coilDiskCacheMaxSize by rememberPreference(
        coilDiskCacheMaxSizeKey,
        CoilDiskCacheMaxSize.`128MB`
    )
    val exoPlayerDiskCacheMaxSize by rememberPreference(
        exoPlayerDiskCacheMaxSizeKey,
        ExoPlayerDiskCacheMaxSize.`2GB`
    )

    val exoPlayerDiskDownloadCacheMaxSize by rememberPreference(
        exoPlayerDiskDownloadCacheMaxSizeKey,
        ExoPlayerDiskDownloadCacheMaxSize.`2GB`
    )

    when (cacheType) {
        CacheType.Images -> {}
        CacheType.CachedSongs -> {
            if (exoPlayerDiskCacheMaxSize == ExoPlayerDiskCacheMaxSize.Unlimited) return
        }
        CacheType.DownloadedSongs -> {
            if (exoPlayerDiskDownloadCacheMaxSize == ExoPlayerDiskDownloadCacheMaxSize.Unlimited) return
        }
    }

    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current

    var imageDiskCacheSize by remember { mutableStateOf(ImageCacheFactory.getCacheSize()) }
    var cachedSongsDiskCacheSize by remember { mutableStateOf(0L) }
    var downloadedSongsDiskCacheSize by remember { mutableStateOf(0L) }

    LaunchedEffect(binder) {
        if (binder != null) {
            cachedSongsDiskCacheSize = binder.cache.cacheSpace
            downloadedSongsDiskCacheSize = binder.downloadCache.cacheSpace
        }
    }

    val currentCacheSize = when (cacheType) {
        CacheType.Images -> imageDiskCacheSize
        CacheType.CachedSongs -> cachedSongsDiskCacheSize
        CacheType.DownloadedSongs -> downloadedSongsDiskCacheSize
    }

    val maxCacheSize = when (cacheType) {
        CacheType.Images -> coilDiskCacheMaxSize.bytes
        CacheType.CachedSongs -> exoPlayerDiskCacheMaxSize.bytes
        CacheType.DownloadedSongs -> exoPlayerDiskDownloadCacheMaxSize.bytes
    }

    val maxCacheSizeText = when (cacheType) {
        CacheType.Images -> coilDiskCacheMaxSize.text
        CacheType.CachedSongs -> exoPlayerDiskCacheMaxSize.text
        CacheType.DownloadedSongs -> exoPlayerDiskDownloadCacheMaxSize.text
    }

    val targetProgress = (currentCacheSize.toFloat() / maxCacheSize.coerceAtLeast(1)).coerceIn(0f, 1f)
    val progressValue by animateFloatAsState(targetValue = targetProgress)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 12.dp)) {
        if (!circularIndicator) {
            ProgressIndicator(
                progress = progressValue,
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            ProgressIndicatorCircular(
                progress = progressValue,
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        if (showCacheInfo) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                BasicText(
                    text = "${Formatter.formatShortFileSize(context, currentCacheSize)} / $maxCacheSizeText",
                    style = typography().xxs.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = colorPalette().textSecondary)
                )
            }
        }
    }
}


