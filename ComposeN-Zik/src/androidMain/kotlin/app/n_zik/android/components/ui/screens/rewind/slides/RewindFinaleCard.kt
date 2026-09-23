package app.n_zik.android.components.ui.screens.rewind.slides

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rewind.RewindData
import app.n_zik.android.components.ui.screens.rewind.rewindShareCaptureActive

/**
 * Finale slide of the Rewind deck.
 *
 * Export actions live at the bottom as two explicitly labelled pills — "export this page"
 * (single-PNG system share of the displayed slide, [onShareSlide]) and "export all pages"
 * (16-image folder export, [onShare]) — instead of the top-right per-slide share icon the
 * other slides carry.
 */
@Composable
fun RewindFinaleCard(
    data: RewindData,
    username: String,
    page: Int,
    pageCount: Int,
    active: Boolean,
    shareMode: Boolean,
    onShare: () -> Unit,
    onRestart: () -> Unit,
    onShareSlide: (() -> Unit)? = null
) {
    val topArtist = data.topArtists.firstOrNull()
    val topSong = data.topSongs.firstOrNull()
    val topAlbum = data.topAlbums.firstOrNull()
    val topPlaylist = data.topPlaylists.firstOrNull()
    val badge = calculateListenerBadge(data)
    // Solid tile/card surfaces are not scrimmed, so contrast must be judged on the surface
    // itself (flatTextOn) instead of the scrim-darkened slide background (textOn).
    val onLime = rewindColors.value.flatTextOn(rewindColors.value.lime)
    val onPink = rewindColors.value.flatTextOn(rewindColors.value.pink)
    val onBlue = rewindColors.value.flatTextOn(rewindColors.value.blue)
    val onOrange = rewindColors.value.flatTextOn(rewindColors.value.orange)
    val onPurple = rewindColors.value.flatTextOn(rewindColors.value.purple)
    val onYellow = rewindColors.value.flatTextOn(rewindColors.value.yellow)
    RewindStoryShell(
        page = page,
        pageCount = pageCount,
        background = rewindColors.value.ink,
        progressColor = rewindColors.value.cream,
        onNext = null,
        // The finale shows no top-right share icon: its export actions live at the bottom as
        // two explicitly labelled pills (export this page / export all pages).
        onShareSlide = null,
        showProgress = !shareMode,
        showBrand = true,
        backgroundArt = {
            Canvas(Modifier.fillMaxSize()) {
                val purpleShape = Path().apply {
                    moveTo(size.width * 0.62f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height * 0.38f)
                    lineTo(size.width * 0.82f, size.height * 0.30f)
                    close()
                }
                drawPath(purpleShape, rewindColors.value.purple)
                val pinkShape = Path().apply {
                    moveTo(0f, size.height * 0.70f)
                    lineTo(size.width * 0.34f, size.height * 0.76f)
                    lineTo(size.width * 0.52f, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(pinkShape, rewindColors.value.pink)
                drawCircle(
                    color = rewindColors.value.lime,
                    radius = size.width * 0.12f,
                    center = Offset(size.width * 0.90f, size.height * 0.60f)
                )
                drawCircle(
                    color = rewindColors.value.orange,
                    radius = size.width * 0.055f,
                    center = Offset(size.width * 0.12f, size.height * 0.18f)
                )
            }
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 700.dp
            val statSize = if (compact) 19.sp else 23.sp
            Column(modifier = Modifier.fillMaxSize()) {
                RewindReveal(active, 40, direction = RewindRevealDirection.Left) {
                    RewindKicker(stringResource(R.string.rw_finale_kicker, data.periodLabel), rewindColors.value.lime)
                }
                Spacer(Modifier.height(10.dp))
                RewindReveal(active, 110, direction = RewindRevealDirection.Left) {
                    Text(
                        text = stringResource(R.string.rw_finale_heading, data.periodLabel),
                        color = rewindColors.value.cream,
                        fontSize = if (compact) 40.sp else 48.sp,
                        lineHeight = if (compact) 37.sp else 44.sp,
                        letterSpacing = (-2.4).sp,
                        fontWeight = FontWeight.Black
                    )
                }
                // The badge title is already showcased by the LISTENER LEVEL row below (and by
                // the share footer in shareMode), so the username line stays plain to avoid
                // repeating it twice on the slide.
                RewindReveal(active, 190) {
                    Text(
                        text = username,
                        color = rewindColors.value.lime,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FinaleStatTile(
                            label = stringResource(R.string.rw_label_minutes),
                            value = formatRewindMinutes(data.stats.totalMinutes),
                            background = rewindColors.value.lime,
                            foreground = onLime,
                            valueSize = statSize,
                            active = active,
                            delayMillis = 260,
                            modifier = Modifier.weight(1f)
                        )
                        FinaleStatTile(
                            label = stringResource(R.string.rw_label_plays),
                            value = formatRewindNumber(data.stats.totalPlays.toLong()),
                            background = rewindColors.value.pink,
                            foreground = onPink,
                            valueSize = statSize,
                            active = active,
                            delayMillis = 340,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FinaleStatTile(
                            label = stringResource(R.string.rw_label_days),
                            value = formatRewindNumber(data.daysWithMusic.toLong()),
                            background = rewindColors.value.blue,
                            foreground = onBlue,
                            valueSize = statSize,
                            active = active,
                            delayMillis = 420,
                            modifier = Modifier.weight(1f)
                        )
                        FinaleStatTile(
                            label = stringResource(R.string.rw_label_unique_songs),
                            value = formatRewindNumber(data.totalUniqueSongs.toLong()),
                            background = rewindColors.value.orange,
                            foreground = onOrange,
                            valueSize = statSize,
                            active = active,
                            delayMillis = 500,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FinaleStatTile(
                            label = stringResource(R.string.rw_label_unique_artists),
                            value = formatRewindNumber(data.totalUniqueArtists.toLong()),
                            background = rewindColors.value.purple,
                            foreground = onPurple,
                            valueSize = statSize,
                            active = active,
                            delayMillis = 580,
                            modifier = Modifier.weight(1f)
                        )
                        FinaleStatTile(
                            label = stringResource(R.string.rw_label_unique_albums),
                            value = formatRewindNumber(data.totalUniqueAlbums.toLong()),
                            background = rewindColors.value.yellow,
                            foreground = onYellow,
                            valueSize = statSize,
                            active = active,
                            delayMillis = 660,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
                // In shareMode — and while any capture is in flight — the footer signature
                // already carries the badge + index, so the big row is hidden there to
                // avoid showing it twice in the shared image.
                if (!shareMode && !rewindShareCaptureActive.value) {
                    RewindReveal(active, 740, scaleFrom = 0.94f) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rewindColors.value.cream, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.rw_finale_listener_level),
                                    color = rewindColors.value.ink.copy(alpha = 0.54f),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = stringResource(badge.titleId),
                                    color = rewindColors.value.ink,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Text(
                                text = badge.index.toString(),
                                color = onLime,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier
                                    .background(rewindColors.value.lime, CircleShape)
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(if (compact) 8.dp else 9.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RewindReveal(
                        active = active,
                        delayMillis = 820,
                        direction = RewindRevealDirection.Left,
                        modifier = Modifier.weight(1f)
                    ) {
                        FinaleFeature(
                            label = stringResource(R.string.rw_finale_top_artist),
                            title = topArtist?.artist?.cleanName() ?: "—",
                            subtitle = topArtist?.let { stringResource(R.string.rw_minutes_compact, formatRewindNumber(it.minutes)) } ?: "",
                            imageUrl = topArtist?.artist?.thumbnailUrl,
                            circular = true,
                            artistName = topArtist?.artist?.cleanName(),
                            background = rewindColors.value.purple
                        )
                    }
                    RewindReveal(
                        active = active,
                        delayMillis = 880,
                        direction = RewindRevealDirection.Right,
                        modifier = Modifier.weight(1f)
                    ) {
                        FinaleFeature(
                            label = stringResource(R.string.rw_finale_top_song),
                            title = topSong?.song?.cleanTitle() ?: "—",
                            subtitle = topSong?.let { stringResource(R.string.rw_meta_plays, formatRewindNumber(it.playCount.toLong())) } ?: "",
                            imageUrl = topSong?.song?.thumbnailUrl,
                            circular = false,
                            artistName = null,
                            background = rewindColors.value.red
                        )
                    }
                }
                Spacer(Modifier.height(if (compact) 8.dp else 9.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RewindReveal(
                        active = active,
                        delayMillis = 940,
                        direction = RewindRevealDirection.Left,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Same yellow as the Album of the Year slide.
                        FinaleFeature(
                            label = stringResource(R.string.rw_finale_top_album),
                            title = topAlbum?.album?.cleanTitle() ?: "—",
                            subtitle = topAlbum?.let { stringResource(R.string.rw_minutes_compact, formatRewindNumber(it.minutes)) } ?: "",
                            imageUrl = topAlbum?.album?.thumbnailUrl,
                            circular = false,
                            artistName = null,
                            background = rewindColors.value.yellow,
                            labelColor = rewindColors.value.ink
                        )
                    }
                    RewindReveal(
                        active = active,
                        delayMillis = 1_000,
                        direction = RewindRevealDirection.Right,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Same lime as the top playlists slide; no cover field in the DB, so the
                        // most-played track cover is used as the artwork.
                        FinaleFeature(
                            label = stringResource(R.string.rw_finale_top_playlist),
                            title = topPlaylist?.playlist?.playlist?.cleanName() ?: "—",
                            subtitle = topPlaylist?.let { stringResource(R.string.rw_meta_songs, formatRewindNumber(it.songCount.toLong())) } ?: "",
                            imageUrl = null,
                            circular = false,
                            artistName = null,
                            playlistId = topPlaylist?.playlist?.playlist?.id,
                            playlistName = topPlaylist?.playlist?.playlist?.name.orEmpty(),
                            playlistBrowseId = topPlaylist?.playlist?.playlist?.browseId,
                            playlistIsYoutube = topPlaylist?.playlist?.playlist?.isYoutubePlaylist ?: false,
                            background = rewindColors.value.lime,
                            labelColor = rewindColors.value.ink
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                // The brand footer is the "exported" look of the finale: the 16-image deck
                // export shows it on this page, and a single-page capture of the finale must
                // produce the same frame — identical content, no interactive pills.
                if (shareMode || rewindShareCaptureActive.value) {
                    RewindReveal(active, 1_060) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.rw_finale_brand, data.periodLabel),
                                color = rewindColors.value.lime,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.0.sp
                            )
                            // Same pill as the live listener-level row: a solid surface keeps
                            // the badge readable on the slide background in the exported image,
                            // where a bare text line would vanish.
                            Row(
                                modifier = Modifier
                                    .background(rewindColors.value.cream, RoundedCornerShape(100.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(badge.titleId),
                                    color = rewindColors.value.ink,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = badge.index.toString(),
                                    color = onLime,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier
                                        .background(rewindColors.value.lime, CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Interactive UI: the export pills only exist in the live deck — any
                    // captured frame (per-slide share, deck export) renders the brand footer
                    // above instead, so shared images never carry clickable chrome.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Two distinct export actions (on-device feedback): the single-page
                        // share of the displayed slide and the full-deck folder export.
                        if (onShareSlide != null) {
                            RewindReveal(active, 1_080) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            rewindColors.value.cream.copy(alpha = 0.10f),
                                            RoundedCornerShape(100.dp)
                                        )
                                        .border(
                                            1.dp,
                                            rewindColors.value.cream.copy(alpha = 0.45f),
                                            RoundedCornerShape(100.dp)
                                        )
                                        .clickable(onClick = onShareSlide, indication = null, interactionSource = remember { MutableInteractionSource() })
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.share_social),
                                        contentDescription = null,
                                        tint = rewindColors.value.cream,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.rw_finale_export_page),
                                        color = rewindColors.value.cream,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                            }
                        }
                        RewindReveal(active, 1_150) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rewindColors.value.lime, RoundedCornerShape(100.dp))
                                    .clickable(onClick = onShare, indication = null, interactionSource = remember { MutableInteractionSource() })
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.share_social),
                                    contentDescription = null,
                                    tint = onLime,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.rw_finale_share),
                                    color = onLime,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.7.sp
                                )
                            }
                        }
                        RewindReveal(active, 1_220) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        rewindColors.value.cream.copy(alpha = 0.10f),
                                        RoundedCornerShape(100.dp)
                                    )
                                    .border(
                                        1.dp,
                                        rewindColors.value.cream.copy(alpha = 0.45f),
                                        RoundedCornerShape(100.dp)
                                    )
                                    .clickable(onClick = onRestart, indication = null, interactionSource = remember { MutableInteractionSource() })
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.refresh),
                                    contentDescription = null,
                                    tint = rewindColors.value.cream,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.rw_finale_play_again),
                                    color = rewindColors.value.cream,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FinaleStatTile(
    label: String,
    value: String,
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color,
    valueSize: TextUnit,
    active: Boolean,
    delayMillis: Int,
    modifier: Modifier = Modifier
) {
    RewindReveal(active, delayMillis, scaleFrom = 0.86f, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(background, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                text = value,
                color = foreground,
                fontSize = valueSize,
                lineHeight = valueSize,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                color = foreground.copy(alpha = 0.66f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.7.sp
            )
        }
    }
}

@Composable
private fun FinaleFeature(
    label: String,
    title: String,
    subtitle: String,
    imageUrl: String?,
    circular: Boolean,
    artistName: String?,
    background: androidx.compose.ui.graphics.Color,
    labelColor: androidx.compose.ui.graphics.Color = rewindColors.value.lime,
    playlistId: Long? = null,
    playlistName: String = "",
    playlistBrowseId: String? = null,
    playlistIsYoutube: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (circular && !artistName.isNullOrBlank()) {
            RewindArtistArtwork(
                artistName = artistName,
                primaryUrl = imageUrl,
                preferWikipedia = true,
                modifier = Modifier.size(40.dp)
            )
        } else if (playlistId != null) {
            // No cover field on the DB playlist: use the exact same mosaic as the top
            // playlists slide, the statistics screen and the home library (the four
            // most-played track covers, or a single cover when they all match) so the
            // artwork can never disagree with what the user sees elsewhere.
            RewindPlaylistArtwork(
                playlistId = playlistId,
                title = title,
                name = playlistName,
                browseId = playlistBrowseId,
                isYoutubePlaylist = playlistIsYoutube,
                sizeDp = 40.dp,
                modifier = Modifier.size(40.dp)
            )
        } else {
            RewindArtworkWithFallback(
                imageUrl = imageUrl,
                title = title,
                modifier = Modifier.size(40.dp),
                circular = circular,
                background = rewindColors.value.ink,
                foreground = rewindColors.value.cream
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 7.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.7.sp
            )
            Text(
                text = title,
                color = rewindColors.value.flatTextOn(background),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = rewindColors.value.flatTextOn(background).copy(alpha = 0.56f),
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
