package app.n_zik.android.ui.screens.player

import android.util.LruCache
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.SurfaceForegroundContent
import app.n_zik.android.components.player.BlurAdjuster
import app.n_zik.android.components.player.PlayerInformationGlassRegion
import app.n_zik.android.components.player.PlayerQueueDuration
import app.n_zik.android.components.player.PlayerQueueDurationGlass
import app.n_zik.android.components.player.playerTextOutlineColor
import app.n_zik.android.components.player.rememberPlayerBackdropRegionColor
import app.n_zik.android.typography
import app.n_zik.android.ui.components.themed.ItemTextPlaceholder
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.utils.PlayerTrackLinks
import app.n_zik.android.utils.formatAsTime
import app.n_zik.android.utils.playerTrackLinks
import app.n_zik.android.utils.semiBold

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalTextApi::class, ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
internal fun PlayerTrackInformation(
    displayItem: MediaItem,
    currentMediaId: String,
    currentLinks: PlayerTrackLinks,
    linkCache: LruCache<String, PlayerTrackLinks>,
    navController: NavController,
    onDismiss: () -> Unit,
    blurAdjuster: BlurAdjuster,
    expandedplayer: Boolean,
    titleExpanded: Boolean,
    timelineExpanded: Boolean,
    controlsExpanded: Boolean,
    isShowingStandaloneLyrics: Boolean,
    shouldBePlaying: Boolean,
    backgroundPalette: ColorPalette,
    artworkAccent: State<Color>,
    modifier: Modifier,
    isBuffering: Boolean,
    position: () -> Long,
    duration: () -> Long,
) {
    var region by remember { mutableStateOf<Rect?>(null) }
    val surface = rememberPlayerBackdropRegionColor(region, useLocalSample = true)
    val displayLinks = if (displayItem.mediaId == currentMediaId) currentLinks
        else displayItem.playerTrackLinks(linkCache)
    val controls: @Composable () -> Unit = {
        Controls(
            navController = navController,
            onCollapse = onDismiss,
            onBlurScaleChange = { strength -> blurAdjuster.strength = strength },
            expandedplayer = expandedplayer,
            titleExpanded = titleExpanded,
            timelineExpanded = timelineExpanded,
            controlsExpanded = controlsExpanded,
            isShowingLyrics = isShowingStandaloneLyrics,
            mediaItem = displayItem,
            artistIds = displayLinks.artists,
            albumId = displayLinks.albumId,
            shouldBePlaying = shouldBePlaying,
            isBuffering = isBuffering,
            position = position,
            duration = duration,
            dynamicColorPalette = backgroundPalette.copy(accent = artworkAccent.value),
            modifier = modifier.onGloballyPositioned { region = it.boundsInWindow() },
        )
    }
    SurfaceForegroundContent(surface, preferMeasuredText = true, content = controls)
}

@Composable
internal fun PlayerQueueDurationInformation(totalPlayTimes: PlayerQueueDuration, textoutline: Boolean) {
    PlayerInformationGlassRegion("queueDuration") {
        PlayerQueueDurationGlass {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
            ) {
                Image(
                    painter = painterResource(R.drawable.time),
                    colorFilter = ColorFilter.tint(colorPalette().accent),
                    modifier = Modifier
                        .size(20.dp)
                        .padding(horizontal = 5.dp),
                    contentDescription = stringResource(R.string.accessibility_background_image),
                    contentScale = ContentScale.Fit
                )

                Box {
                    if (totalPlayTimes == PlayerQueueDuration.Loading) {
                        ItemTextPlaceholder(
                            text = null,
                            style = typography().xxs.semiBold,
                            modifier = Modifier.width(56.dp),
                            width = 1f,
                        )
                    } else {
                        val durationText = (totalPlayTimes as? PlayerQueueDuration.Known)
                            ?.let { " ${formatAsTime(it.milliseconds)}" } ?: " —"
                        BasicText(
                            text = durationText,
                            style = typography().xxs.semiBold.merge(
                                TextStyle(
                                    textAlign = TextAlign.Center,
                                    color = colorPalette().text,
                                )
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        BasicText(
                            text = durationText,
                            style = typography().xxs.semiBold.merge(
                                TextStyle(
                                    textAlign = TextAlign.Center,
                                    drawStyle = Stroke(
                                        width = 1f,
                                        join = StrokeJoin.Round
                                    ),
                                    // Keyed on the effective tone, not the mode (spec-achromatic-ramp-luminance-cap)
                                    color = if (!textoutline) Color.Transparent
                                    else playerTextOutlineColor(colorPalette().isDark, 0.5f),
                                )
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
