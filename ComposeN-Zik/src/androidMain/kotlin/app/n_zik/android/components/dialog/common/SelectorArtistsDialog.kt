package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.R
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.colorPalette
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.coil.resize
import app.n_zik.android.core.database.Database
import app.n_zik.android.typography
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.models.Info
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.utils.medium
import it.fast4x.innertube.YtMusic
import kotlinx.coroutines.withContext
import app.n_zik.android.components.player.textOutlineColor

/** Artist thumbnail pager using the shared title and dismissal bands. */
@Composable
inline fun SelectorArtistsDialog(
    noinline onDismiss: () -> Unit,
    title: String,
    values: List<Info>?,
    crossinline onValueSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DialogFrame(
        onDismiss = onDismiss,
        title = title,
        modifier = modifier,
        footer = {
            ButtonRow(listOf(DialogButton(stringResource(R.string.dialog_close), onClick = onDismiss)))
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(artistThumbnailShape())
                .background(Color.Transparent)
        ) {
            if (values != null) {
                val pagerState = rememberPagerState(pageCount = { values.size })

                Box {
                    HorizontalPager(state = pagerState) { idArtist ->
                        val browseId = values[idArtist].id
                        val artist by remember( browseId ) {
                            Database.artistTable.findById( browseId )
                        }.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)

                        LaunchedEffect(Unit) {
                            if (artist?.thumbnailUrl == null) {
                                withContext(NzikDispatchers.DATA) {
                                    YtMusic.getArtistPage(browseId = browseId.removePrefix(MODIFIED_PREFIX))
                                       .onSuccess { currentArtistPage ->
                                           artist?.copy(
                                               thumbnailUrl = currentArtistPage.artist.thumbnail?.url
                                           )?.let( Database.artistTable::update )
                                       }
                                }
                            }
                        }

                        Box {
                            ImageCacheFactory.SubcomposeImage(
                                shape = artistThumbnailShape(),
                                thumbnailUrl = artist?.thumbnailUrl?.resize(1000, 1000),
                                contentDescription = "",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(artistThumbnailShape())
                                    .clickable(
                                        onClick = {
                                            onDismiss()
                                            onValueSelected(browseId)
                                        }
                                    )
                                    .align(Alignment.Center)
                            )
                            if (artist?.isYoutubeArtist == true) {
                                Image(
                                    painter = painterResource(R.drawable.ytmusic),
                                    colorFilter = ColorFilter.tint(
                                        Color.Red.copy(0.75f).compositeOver(Color.White)
                                    ),
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(all = 5.dp)
                                        .offset(10.dp,10.dp),
                                    contentDescription = stringResource(R.string.cd_background_image),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            values[idArtist].name?.let { it1 ->
                                BasicText(
                                    text = cleanPrefix(it1),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    style = typography().xs.medium,
                                    modifier = Modifier
                                        .padding(bottom = 20.dp)
                                        .align(Alignment.BottomCenter)
                                )
                                BasicText(
                                    text = cleanPrefix(it1),
                                    style = typography().xs.medium.merge(TextStyle(
                                        drawStyle = Stroke(width = 1.0f, join = StrokeJoin.Round),
                                        // Keyed on the effective tone, not the mode (spec-achromatic-ramp-luminance-cap)
                                        color = textOutlineColor(colorPalette().isDark, 0.5f)
                                    )),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(bottom = 20.dp)
                                        .align(Alignment.BottomCenter)
                                )
                            }
                        }

                    }
                    Row(
                        Modifier
                            .height(20.dp)
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(values.size) { iteration ->
                            val color = if (pagerState.currentPage == iteration) colorPalette().text else colorPalette().text.copy(alpha = 0.5f)
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .size(10.dp)

                            )
                        }
                    }
                }
            }
        }
    }
}
