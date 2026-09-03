package app.it.fast4x.rimusic.ui.screens.player

import app.n_zik.android.core.database.*

import android.annotation.SuppressLint
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.PlayerBackgroundColors
import app.it.fast4x.rimusic.enums.PlayerType
import app.it.fast4x.rimusic.models.Format
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.n_zik.android.playback.services.playbackDataCache
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.it.fast4x.rimusic.ui.styling.onOverlay
import app.it.fast4x.rimusic.ui.styling.overlay
import app.it.fast4x.rimusic.utils.blackgradientKey
import app.it.fast4x.rimusic.utils.color
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.playerBackgroundColorsKey
import app.it.fast4x.rimusic.utils.playerTypeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showthumbnailKey
import app.it.fast4x.rimusic.utils.statsfornerdsKey
import app.it.fast4x.rimusic.utils.transparentBackgroundPlayerActionBarKey
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import timber.log.Timber
import androidx.compose.ui.geometry.Offset

@SuppressLint("LongLogTag")
@UnstableApi
@Composable
fun StatsForNerds(
    mediaId: String,
    isDisplayed: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current ?: return

//    val audioQualityFormat by rememberPreference(audioQualityFormatKey, AudioQualityFormat.High)
//
//    val connectivityManager = getSystemService(context, ConnectivityManager::class.java) as ConnectivityManager

    AnimatedVisibility(
        visible = isDisplayed,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val cleanMediaId = remember(mediaId) { mediaId.split("/").lastOrNull() ?: mediaId }

        var playbackData by remember(cleanMediaId) { mutableStateOf(playbackDataCache[cleanMediaId]) }
        var cachedBytes by remember(cleanMediaId) {
            mutableStateOf(binder.cache.getCachedBytes(cleanMediaId, 0, -1))
        }

        var downloadCachedBytes by remember(cleanMediaId) {
            mutableStateOf(binder.downloadCache.getCachedBytes(cleanMediaId, 0, -1))
        }

        var format by remember(cleanMediaId) { mutableStateOf<Format?>(null) }
        LaunchedEffect(cleanMediaId) {
            while (true) {
                playbackDataCache[cleanMediaId]?.let { if (it != playbackData) playbackData = it }
                cachedBytes = binder.cache.getCachedBytes(cleanMediaId, 0, -1)
                downloadCachedBytes = binder.downloadCache.getCachedBytes(cleanMediaId, 0, -1)
                val dbFormat = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    Database.formatTable.findBySongIdDirect(cleanMediaId)
                }
                if (dbFormat != format) format = dbFormat
                delay(1000)
            }
        }
        val showThumbnail by rememberPreference(showthumbnailKey, true)
        val statsForNerds by rememberPreference(statsfornerdsKey, false)
        val playerType by rememberPreference(playerTypeKey, PlayerType.Essential)
        val transparentBackgroundActionBarPlayer by rememberPreference(
            transparentBackgroundPlayerActionBarKey,
            true
        )
        var blackgradient by rememberPreference(blackgradientKey, false)
        val playerBackgroundColors by rememberPreference(
            playerBackgroundColorsKey,
            PlayerBackgroundColors.AnimatedGradient
        )
        var statsfornerdsfull by remember {mutableStateOf(false)}
        val rotationAngle by animateFloatAsState(
            targetValue = if (statsfornerdsfull) 180f else 0f,
            animationSpec = tween(durationMillis = 500)
        )

        DisposableEffect(mediaId) {
            val listener = object : Cache.Listener {
                override fun onSpanAdded(cache: Cache, span: CacheSpan) {
                    cachedBytes += span.length
                }

                override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
                    cachedBytes -= span.length
                }

                override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) =
                    Unit
            }

            binder.cache.addListener(mediaId, listener)

            onDispose {
                binder.cache.removeListener(mediaId, listener)
            }
        }

    if (showThumbnail && (!statsForNerds || playerType == PlayerType.Essential)) {
        val scrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    return if (source == NestedScrollSource.UserInput) available.copy(x = 0f) else Offset.Zero
                }
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    return if (source == NestedScrollSource.UserInput) available.copy(x = 0f) else Offset.Zero
                }
            }
        }
        Box(
            modifier = modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            onDismiss()
                        }
                    )
                }
                .background(colorPalette().overlay)
                .clipToBounds()
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .nestedScroll(scrollConnection)
                    .verticalScroll(rememberScrollState())
            ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .wrapContentSize(Alignment.Center)
                    .padding(all = 16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.widthIn(max = 100.dp)
                ) {
                    BasicText(
                        text = stringResource(R.string.id),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        overflow = TextOverflow.Visible,
                        style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                    )
                    if (format?.songId?.startsWith(LOCAL_KEY_PREFIX) == false) {
                        BasicText(
                            text = stringResource(R.string.itag),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            overflow = TextOverflow.Visible,
                            style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                        )
                        BasicText(
                            text = stringResource(R.string.quality),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            overflow = TextOverflow.Visible,
                            style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                        )
                    }
                    BasicText(
                        text = stringResource(R.string.bitrate),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        overflow = TextOverflow.Visible,
                        style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                    )
                    BasicText(
                        text = stringResource(R.string.size),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        overflow = TextOverflow.Visible,
                        style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                    )

                    if (format?.songId?.startsWith(LOCAL_KEY_PREFIX) == true)
                        BasicText(
                            text = stringResource(R.string.cached),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            overflow = TextOverflow.Visible,
                            style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                        )

                    if (format?.songId?.startsWith(LOCAL_KEY_PREFIX) == false) {
                        BasicText(
                            text = if (downloadCachedBytes == 0L) stringResource(R.string.cached)
                            else stringResource(R.string.downloaded),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            overflow = TextOverflow.Visible,
                            style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                        )

                        BasicText(
                            text = stringResource(R.string.loudness),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            overflow = TextOverflow.Visible,
                            style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                        )
                        BasicText(
                            text = stringResource(R.string.perceptual_loudness),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            overflow = TextOverflow.Visible,
                            style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                        )
                    }
                    BasicText(
                        text = stringResource(R.string.container),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        overflow = TextOverflow.Visible,
                        style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                    )
                    BasicText(
                        text = stringResource(R.string.codec),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        overflow = TextOverflow.Visible,
                        style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                    )
                    BasicText(
                        text = stringResource(R.string.sample_rate),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        overflow = TextOverflow.Visible,
                        style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                    )
                    BasicText(
                        text = stringResource(R.string.stream_client),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        overflow = TextOverflow.Visible,
                        style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                    )
                    if (format?.songId?.startsWith(LOCAL_KEY_PREFIX) == false) {
                        BasicText(
                            text = stringResource(R.string.volume),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            overflow = TextOverflow.Visible,
                            style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.wrapContentSize()
                ) {
                    BasicText(
                        text = mediaId,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        overflow = TextOverflow.Visible,
                        style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                    )

                    if (format?.songId?.startsWith(LOCAL_KEY_PREFIX) == false) {
                        BasicText(
                            text = format?.itag?.toString()
                                ?: stringResource(R.string.audio_quality_format_unknown),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            overflow = TextOverflow.Visible,
                            style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                        )
                        BasicText(
                            text = format?.let { getQuality(it) } ?: stringResource(R.string.audio_quality_format_unknown),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            overflow = TextOverflow.Visible,
                            style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                        )
                    }
                    BasicText(
                        text = format?.bitrate?.let { "${it / 1000} kbps" } ?: stringResource(R.string.audio_quality_format_unknown),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        overflow = TextOverflow.Visible,
                        style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                    )
                    BasicText(
                        text = format?.contentLength?.let { 
                            if (it > 0) Formatter.formatShortFileSize(context, it) 
                            else stringResource(R.string.audio_quality_format_unknown)
                        } ?: stringResource(R.string.audio_quality_format_unknown),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        overflow = TextOverflow.Visible,
                        style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                    )

                    if (format?.songId?.startsWith(LOCAL_KEY_PREFIX) == true) {
                         BasicText(
                            text = "100%",
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            overflow = TextOverflow.Visible,
                            style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                        )
                    }

                    if (format?.songId?.startsWith(LOCAL_KEY_PREFIX) == false) {
                        BasicText(
                            text = if (downloadCachedBytes == 0L) {
                                Formatter.formatShortFileSize(context, cachedBytes) + (format?.contentLength?.let {
                                    if (it > 0) " (${(cachedBytes.toFloat() / it * 100).roundToInt()}%)" else ""
                                } ?: "")
                            } else {
                                Formatter.formatShortFileSize(context, downloadCachedBytes) + (format?.contentLength?.let {
                                    if (it > 0) " (${(downloadCachedBytes.toFloat() / it * 100).roundToInt()}%)" else ""
                                } ?: "")
                            },
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            overflow = TextOverflow.Visible,
                            style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                        )

                        BasicText(
                            text = format?.loudnessDb?.let { "%.2f dB".format(it) } ?: stringResource(R.string.audio_quality_format_unknown),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            overflow = TextOverflow.Visible,
                            style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                        )
                        BasicText(
                            text = format?.perceptualLoudnessDb?.let { "%.2f dB".format(it) } ?: stringResource(R.string.audio_quality_format_unknown),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            overflow = TextOverflow.Visible,
                            style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                        )
                    }
                    BasicText(
                        text = format?.let { getContainer(it) } ?: stringResource(R.string.audio_quality_format_unknown),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        overflow = TextOverflow.Visible,
                        style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                    )
                    BasicText(
                        text = format?.let { getCodec(it) } ?: stringResource(R.string.audio_quality_format_unknown),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        overflow = TextOverflow.Visible,
                        style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                    )
                    BasicText(
                        text = format?.sampleRate?.let { "${it / 1000} kHz" } ?: stringResource(R.string.audio_quality_format_unknown),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        overflow = TextOverflow.Visible,
                        style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                    )
                    BasicText(
                        text = if (downloadCachedBytes != 0L) {
                            stringResource(R.string.downloaded)
                        } else if (cachedBytes > 0) {
                            stringResource(R.string.cached) + " : " + (playbackData?.streamClient ?: stringResource(R.string.audio_quality_format_unknown))
                        } else {
                            playbackData?.streamClient ?: stringResource(R.string.audio_quality_format_unknown)
                        },
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        overflow = TextOverflow.Visible,
                        style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                    )
                    if (format?.songId?.startsWith(LOCAL_KEY_PREFIX) == false) {
                        BasicText(
                            text = "${(binder.player.volume * 100).roundToInt()}%",
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            overflow = TextOverflow.Visible,
                            style = typography().xs.medium.color(colorPalette().onOverlay).copy(textAlign = TextAlign.Start)
                        )
                }
            }
        }
    }
        }
    }
        if ((statsForNerds) && (!showThumbnail || playerType == PlayerType.Modern)) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = modifier
                        .background(colorPalette().background2.copy(alpha = if ((transparentBackgroundActionBarPlayer) || ((playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient) || (playerBackgroundColors == PlayerBackgroundColors.ThemeColorGradient)) && blackgradient) 0.0f else 0.7f))
                        .padding(vertical = 5.dp)
                        .fillMaxWidth(if (isLandscape) 0.8f else 1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = modifier.weight(1f)
                            .padding(end = 4.dp)
                    ) {
                        if (format?.songId?.startsWith(LOCAL_KEY_PREFIX) == false) {
                            BasicText(
                                text = stringResource(R.string.quality) + " : " + (format?.let { getQuality(it) } ?: stringResource(R.string.audio_quality_format_unknown)),
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                                overflow = TextOverflow.Visible,
                                style = typography().xs.medium.color(colorPalette().text)
                            )
                        }
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = modifier.weight(1f)
                    ) {
                        Timber.tag("StatsForNerds").d("modern player bitrate: ${format?.bitrate}")
                        BasicText(
                            text = format?.bitrate?.let { stringResource(R.string.bitrate) + " : " + "${it / 1000} kbps" }
                                ?: (stringResource(R.string.bitrate) + " : " + stringResource(R.string.audio_quality_format_unknown)),
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                            overflow = TextOverflow.Visible,
                            style = typography().xs.medium.color(colorPalette().text)
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = modifier.weight(1f)
                    ) {
                        BasicText(
                            text = format?.contentLength
                                ?.let {stringResource(R.string.size) + " : " + Formatter.formatShortFileSize(context,it)}
                                ?: (stringResource(R.string.size) + " : " + stringResource(R.string.audio_quality_format_unknown)),
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                            overflow = TextOverflow.Visible,
                            style = typography().xs.medium.color(colorPalette().text)
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = modifier.weight(0.2f)
                    ) {
                        IconButton(
                            icon = R.drawable.chevron_up,
                            color = colorPalette().text,
                            onClick = {statsfornerdsfull = !statsfornerdsfull},
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(rotationAngle)
                        )
                    }
                }
                AnimatedVisibility(visible = statsfornerdsfull) {
                  Column {
                      // Row 1: ID + itag
                      Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.Center,
                          modifier = modifier
                              .background(colorPalette().background2.copy(alpha = if ((transparentBackgroundActionBarPlayer) || ((playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient) || (playerBackgroundColors == PlayerBackgroundColors.ThemeColorGradient)) && blackgradient) 0.0f else 0.7f))
                              .padding(vertical = 5.dp)
                              .fillMaxWidth(if (isLandscape) 0.8f else 1f)
                      ) {
                          Box(
                              contentAlignment = Alignment.Center,
                              modifier = modifier.weight(1f)
                          ) {
                              BasicText(
                                  text = stringResource(R.string.id) + " : " + mediaId,
                                  maxLines = 1,
                                  modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                                  overflow = TextOverflow.Visible,
                                  style = typography().xs.medium.color(colorPalette().text)
                              )
                          }
                          if (format?.songId?.startsWith(LOCAL_KEY_PREFIX) == false) {
                              Box(
                                  contentAlignment = Alignment.Center,
                                  modifier = modifier.weight(1f)
                              ) {
                          BasicText(
                              text = stringResource(R.string.itag) + " : " + (format?.itag?.toString() ?: stringResource(R.string.audio_quality_format_unknown)),
                              maxLines = 1,
                              modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                              overflow = TextOverflow.Visible,
                              style = typography().xs.medium.color(colorPalette().text)
                          )
                              }
                          }
                      }
                      // Row 2: Cached/Downloaded + Loudness
                      Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.Center,
                          modifier = modifier
                              .background(colorPalette().background2.copy(alpha = if ((transparentBackgroundActionBarPlayer) || ((playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient) || (playerBackgroundColors == PlayerBackgroundColors.ThemeColorGradient)) && blackgradient) 0.0f else 0.7f))
                              .padding(vertical = 5.dp)
                              .fillMaxWidth(if (isLandscape) 0.8f else 1f)
                      ) {
                          if (format?.songId?.startsWith(LOCAL_KEY_PREFIX) == true) {
                              Box(
                                  contentAlignment = Alignment.Center,
                                  modifier = modifier.weight(1f)
                              ) {
                                  BasicText(
                                      text = stringResource(R.string.cached) + " : " + "100%",
                                      maxLines = 1,
                                      modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                                      overflow = TextOverflow.Visible,
                                      style = typography().xs.medium.color(colorPalette().text)
                                  )
                              }
                          }
                          if (format?.songId?.startsWith(LOCAL_KEY_PREFIX) == false) {
                              Box(
                                  contentAlignment = Alignment.Center,
                                  modifier = modifier.weight(1f)
                              ) {
                                  BasicText(
                                      text = if (downloadCachedBytes == 0L)
                                          stringResource(R.string.cached) + " : " + Formatter.formatShortFileSize(context, cachedBytes) + format?.contentLength?.let { if (it > 0) " (${(cachedBytes.toFloat() / it * 100).roundToInt()}%)" else "" }
                                      else
                                          stringResource(R.string.downloaded) + " : " + Formatter.formatShortFileSize(context, downloadCachedBytes) + format?.contentLength?.let { if (it > 0) " (${(downloadCachedBytes.toFloat() / it * 100).roundToInt()}%)" else "" },
                                      maxLines = 1,
                                      modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                                      overflow = TextOverflow.Visible,
                                      style = typography().xs.medium.color(colorPalette().text)
                                  )
                              }
                              Box(
                                  contentAlignment = Alignment.Center,
                                  modifier = modifier.weight(1f)
                              ) {
                                  BasicText(
                                      text = stringResource(R.string.loudness) + " : " + (format?.loudnessDb?.let { "%.2f dB".format(it) } ?: stringResource(R.string.audio_quality_format_unknown)),
                                      maxLines = 1,
                                      modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                                      overflow = TextOverflow.Visible,
                                      style = typography().xs.medium.color(colorPalette().text)
                                  )
                              }
                          }
                      }
                      // Row 3: Container + Codec + Sample Rate + Channels + Perceptual Loudness
                      Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.Center,
                          modifier = modifier
                              .background(colorPalette().background2.copy(alpha = if ((transparentBackgroundActionBarPlayer) || ((playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient) || (playerBackgroundColors == PlayerBackgroundColors.ThemeColorGradient)) && blackgradient) 0.0f else 0.7f))
                              .padding(vertical = 5.dp)
                              .fillMaxWidth(if (isLandscape) 0.8f else 1f)
                      ) {
                           Box(
                                contentAlignment = Alignment.Center,
                                modifier = modifier.weight(1f)
                            ) {
                                     BasicText(
                                         text = stringResource(R.string.container) + " : " + (format?.let { getContainer(it) } ?: stringResource(R.string.audio_quality_format_unknown)),
                                         maxLines = 1,
                                         modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                                         overflow = TextOverflow.Visible,
                                         style = typography().xs.medium.color(colorPalette().text)
                                     )
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = modifier.weight(1f)
                                ) {
                                     BasicText(
                                         text = stringResource(R.string.codec) + " : " + (format?.let { getCodec(it) } ?: stringResource(R.string.audio_quality_format_unknown)),
                                         maxLines = 1,
                                         modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                                         overflow = TextOverflow.Visible,
                                         style = typography().xs.medium.color(colorPalette().text)
                                     )
                                }
                               Box(
                                   contentAlignment = Alignment.Center,
                                   modifier = modifier.weight(1f)
                               ) {
                                    BasicText(
                                        text = stringResource(R.string.sample_rate) + " : " + (format?.sampleRate?.let { "${it / 1000} kHz" } ?: stringResource(R.string.audio_quality_format_unknown)),
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                                        overflow = TextOverflow.Visible,
                                        style = typography().xs.medium.color(colorPalette().text)
                                    )
                               }
                               if (format?.songId?.startsWith(LOCAL_KEY_PREFIX) == false) {
                               Box(
                                   contentAlignment = Alignment.Center,
                                   modifier = modifier.weight(1f)
                               ) {
                                    BasicText(
                                        text = stringResource(R.string.perceptual_loudness) + " : " + (format?.perceptualLoudnessDb?.let { "%.2f dB".format(it) } ?: stringResource(R.string.audio_quality_format_unknown)),
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                                        overflow = TextOverflow.Visible,
                                        style = typography().xs.medium.color(colorPalette().text)
                                    )
                               }
                                }
                       }
                       // Row 4: Stream Client
                       Row(
                           verticalAlignment = Alignment.CenterVertically,
                           horizontalArrangement = Arrangement.Center,
                           modifier = modifier
                               .background(colorPalette().background2.copy(alpha = if ((transparentBackgroundActionBarPlayer) || ((playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient) || (playerBackgroundColors == PlayerBackgroundColors.ThemeColorGradient)) && blackgradient) 0.0f else 0.7f))
                               .padding(vertical = 5.dp)
                               .fillMaxWidth(if (isLandscape) 0.8f else 1f)
                       ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = modifier.weight(1f)
                            ) {
                                 BasicText(
                                     text = stringResource(R.string.stream_client) + " : " + if (downloadCachedBytes != 0L) {
                                         stringResource(R.string.downloaded)
                                     } else if (cachedBytes > 0) {
                                         stringResource(R.string.cached) + " : " + (playbackData?.streamClient ?: stringResource(R.string.audio_quality_format_unknown))
                                     } else {
                                         playbackData?.streamClient ?: stringResource(R.string.audio_quality_format_unknown)
                                     },
                                     maxLines = 1,
                                     modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                                     overflow = TextOverflow.Visible,
                                     style = typography().xs.medium.color(colorPalette().text)
                                 )
                            }
                       }
                       // Row 5: Volume only (Player Hash and Cipher Since removed — InnerTubeX handles cipher internally)
                       if (format?.songId?.startsWith(LOCAL_KEY_PREFIX) == false) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = modifier
                                .background(colorPalette().background2.copy(alpha = if ((transparentBackgroundActionBarPlayer) || ((playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient) || (playerBackgroundColors == PlayerBackgroundColors.ThemeColorGradient)) && blackgradient) 0.0f else 0.7f))
                                .padding(vertical = 5.dp)
                                .fillMaxWidth(if (isLandscape) 0.8f else 1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = modifier.weight(1f)
                            ) {
                                 BasicText(
                                     text = stringResource(R.string.volume) + " : " + "${(binder.player.volume * 100).roundToInt()}%",
                                     maxLines = 1,
                                     modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                                     overflow = TextOverflow.Visible,
                                     style = typography().xs.medium.color(colorPalette().text)
                                 )
                             }
                        }
                    }
                  }
                }
            }

        }
    }
}


private fun getContainer(format: Format): String {
    val mimeType = format.mimeType ?: return "?"
    return mimeType.substringBefore(";")
}

private fun getCodec(format: Format): String {
    format.codecs?.takeIf { it.isNotEmpty() }?.let { return it }
    val mimeType = format.mimeType ?: return "?"
    return mimeType.substringAfter("codecs=", "").removeSurrounding("\"")
}

@Composable
fun getQuality(format: Format): String {
    return when (format.itag) {
        // Very High (Premium / Surround / Special)
        774, 773, 338, 328, 327, 325, 380, 258, 256, 141 -> stringResource(R.string.audio_quality_format_very_high)
        // High (128kbps+)
        251, 140 -> stringResource(R.string.audio_quality_format_high)
        // Medium (50-128kbps)
        250, 171 -> stringResource(R.string.audio_quality_format_medium)
        // Low (<50kbps)
        249, 139, 600, 599 -> stringResource(R.string.audio_quality_format_low)
        else -> format.itag?.toString() ?: stringResource(R.string.audio_quality_format_unknown)
    }
}


