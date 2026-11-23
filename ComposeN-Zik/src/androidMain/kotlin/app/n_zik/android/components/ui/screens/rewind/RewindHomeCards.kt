package app.n_zik.android.components.ui.screens.rewind

import java.util.Locale

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.PageButtonFeedback
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.ui.screens.rewind.slides.RewindArtwork
import app.n_zik.android.components.ui.screens.rewind.slides.RewindPlaylistArtwork
import app.n_zik.android.components.ui.screens.rewind.slides.formatRewindNumber
import app.n_zik.android.components.ui.screens.rewind.slides.rewindCollageFrame
import app.n_zik.android.components.ui.screens.rewind.slides.rewindCount
import app.n_zik.android.ui.components.themed.MeasuredMetadataPlaceholder
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.shimmerEffect

/** Card artwork keeps Glass; native feedback lets overview drags scroll the parent page. */
@Composable
private fun RewindHomeCardFeedback(
    content: @Composable (Modifier, MutableInteractionSource?) -> Unit,
) {
    PageButtonFeedback(
        material = false,
        enabled = true,
        shape = uiRoundnessShape(),
        interactionSource = null,
        settings = rememberGlassSettings().copy(jellyEnabled = false),
        content = content,
    )
}

/** One tappable home row (a year, or the all-time row): a strip collage of the period's top song/album/artist/playlist behind the label + totals, with the same dark-scrim treatment as the month cells. */
@Composable
internal fun RewindHomeYearRow(
    label: String,
    stats: String,
    tops: TopArtworks,
    // Locked = the period is not over yet (the in-progress year): no deck navigation, no
    // album previews, a padlock replaces the "open" chevron; the caller passes "LOCKED"
    // as [stats].
    locked: Boolean,
    onClick: () -> Unit,
    // Only called while [locked] (the spam rotation — funny replies, then the kick).
    onLockedTap: () -> Unit = {},
    shimmer: Boolean = false,
) {
    val palette = colorPalette()
    // Row height; all four quadrants share it, as in the month collage.
    val rowHeight = 96.dp
    val hasArtwork = !tops.song.isNullOrBlank() ||
        !tops.album.isNullOrBlank() ||
        !tops.artist.isNullOrBlank() ||
        tops.playlist != null
    RewindHomeCardFeedback { feedback, source ->
        Box(
            modifier = Modifier.then(feedback)
                .fillMaxWidth()
                .height(rowHeight)
                .rewindCollageFrame(palette.background1, uiRoundnessShape())
                .clip(uiRoundnessShape())
                // Theme background (not raw black): while the network artworks are loading,
                // the row blends into the page instead of flashing black (same user decision
    //
                // as the month cells, 2026-09-24).
                .background(palette.background1)
                .clickable(enabled = !shimmer, interactionSource = source,
                    indication = if (source == null) androidx.compose.foundation.LocalIndication.current else null,
                    onClick = if (locked) onLockedTap else onClick),
            contentAlignment = Alignment.CenterStart
        ) {
            if (shimmer) {
                Spacer(Modifier.fillMaxSize().alpha(0.25f).shimmerEffect())
            } else if (locked) {
                // In-progress year: no album/artist previews — they would spoil the annual
                // deck (user decision, 2026-09-24). The N-Zik launcher box under the dark
    //
                // scrim, same treatment as the locked month cells.
                RewindHomeLauncherBox(Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                )
            } else if (hasArtwork) {
                // The year's top song, album, artist and playlist as a strip behind the text;
                // the dark scrim keeps the white text readable over any covers.
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1: song
                    Box(modifier = Modifier.weight(1f)) {
                        if (tops.song.isNullOrBlank()) {
                            RewindHomeLauncherBox(Modifier.fillMaxSize())
                        } else {
                            RewindArtwork(
                                imageUrl = tops.song,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    // 2: album
                    Box(modifier = Modifier.weight(1f)) {
                        if (tops.album.isNullOrBlank()) {
                            RewindHomeLauncherBox(Modifier.fillMaxSize())
                        } else {
                            RewindArtwork(
                                imageUrl = tops.album,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    // 3: artist
                    Box(modifier = Modifier.weight(1f)) {
                        if (tops.artist.isNullOrBlank()) {
                            RewindHomeLauncherBox(Modifier.fillMaxSize())
                        } else {
                            RewindArtwork(
                                imageUrl = tops.artist,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    // 4: playlist — the same quadrant as the others. The cover mosaic is sized to
                    // the quadrant's height so its corner-aligned covers fill the whole cell, as in
    //
                    // the month collage.
                    BoxWithConstraints(modifier = Modifier.weight(1f)) {
                        val playlist = tops.playlist
                        if (playlist != null) {
                            RewindPlaylistArtwork(
                                playlistId = playlist.playlist.id,
                                title = playlist.playlist.cleanName(),
                                name = playlist.playlist.name,
                                browseId = playlist.playlist.browseId,
                                isYoutubePlaylist = playlist.playlist.isYoutubePlaylist,
                                sizeDp = maxHeight,
                                shape = RoundedCornerShape(0.dp),
                                emptyFallback = true,
                                // The year row paints its own dark scrim over the strip.
                                dimOverlay = false,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            RewindHomeLauncherBox(Modifier.fillMaxSize())
                        }
                    }
                }
                // Dark scrim on top of the images so the text is readable — same 0.7 as the month cell.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                )
            } else {
                // No plays this year: the N-Zik launcher box behind the label, under a dark
                // scrim — same treatment as an empty month cell.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                )
                RewindHomeLauncherBox(Modifier.fillMaxSize())
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RewindValueText(shimmer,
                        text = label,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.weight(1f))
                    RewindValueText(shimmer,
                        text = stats,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(4.dp))
                    if (locked) {
                        // In-progress year: a padlock instead of the "open the deck" chevron —
                        // the annual deck unlocks once the year is over.
                        Icon(
                            painter = painterResource(R.drawable.locked),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(15.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.chevron_forward),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}



@Composable
private fun RewindHomeLauncherBox(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_box),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

@Composable
internal fun RewindHomeMonthCell(
    modifier: Modifier = Modifier,
    month: MonthlyStat,
    empty: Boolean,
    // Locked = the month is not over yet (in progress or future): the N-Zik launcher box
    // with a padlock on top, no stats, no deck navigation (user decision, 2026-09-24).
    locked: Boolean,
    tops: TopArtworks,
    onClick: () -> Unit,
    // Only called while [locked] (the spam rotation — funny replies, then the kick).
    onLockedTap: () -> Unit = {},
    // Only called while the month is finished and empty (the random sad replies).
    onEmptyTap: () -> Unit = {},
    shimmer: Boolean = false,
) {
    val palette = colorPalette()
    val playable = !empty && !locked
    val showArtwork = playable &&
        (!tops.song.isNullOrBlank() ||
            !tops.artist.isNullOrBlank() ||
            !tops.album.isNullOrBlank() ||
            tops.playlist != null)
    RewindHomeCardFeedback { feedback, source ->
        Box(
            modifier = modifier.then(feedback)
                .aspectRatio(0.92f)
                .rewindCollageFrame(palette.background1, uiRoundnessShape())
                .clip(uiRoundnessShape())
                // Theme background (not raw black): while the network artworks are loading,
                // the cell blends into the page instead of flashing black (user decision,
    //
                // 2026-09-24).
                .background(palette.background1)
                .clickable(enabled = !shimmer, interactionSource = source,
                    indication = if (source == null) androidx.compose.foundation.LocalIndication.current else null,
                    onClick = when {
                        playable -> onClick
                        locked -> onLockedTap
                        else -> onEmptyTap
                    }),
            contentAlignment = Alignment.CenterStart
        ) {
            if (shimmer) {
                Spacer(Modifier.fillMaxSize().alpha(0.25f).shimmerEffect())
            } else if (showArtwork) {
                // The month's top song, album, artist and playlist as a 2x2 collage behind the
                // text; the dark scrim keeps the white text readable over any covers.
                RewindHomeMonthCollage(tops, Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                )
            } else {
                // No artwork: an empty finished month or an unfinished one — the N-Zik launcher
                // box under the same dark scrim as the playable cells (scrim on top of the
    //
                // image, full opacity, so the text stays readable — user decision 2026-09-24).
                RewindHomeLauncherBox(Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                )
                if (locked) {
                    // Unfinished month: a padlock on top, no stats and no message (the funny
                    // replies only come out when the cell is spammed). The deck unlocks once
    //
                    // the month is over.
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.locked),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(9.dp)) {
                RewindValueText(shimmer,
                    text = month.month.uppercase(Locale.getDefault()),
                    color = if (showArtwork || empty || locked) Color.White else palette.text,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.7.sp
                )
                if (locked) {
                    // Locked cell: the "LOCKED" label under the month name (same treatment as
                    // the locked year row — user decision, 2026-09-24); the funny replies still
    //
                    // appear as toasts while the cell is spammed.
                    Spacer(Modifier.height(4.dp))
                    RewindValueText(shimmer,
                        text = stringResource(R.string.rewind_home_locked),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else if (playable) {
                    Spacer(Modifier.height(4.dp))
                    RewindValueText(shimmer,
                        text = rewindHomeDuration(month.minutes),
                        color = if (showArtwork) Color.White else palette.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    RewindValueText(shimmer,
                        text = rewindCount(R.plurals.rewind_play_count_label, month.plays.toLong()),
                        color = if (showArtwork) Color.White.copy(alpha = 0.7f) else palette.textSecondary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    // Empty finished month: a sad one-liner instead of the dash + "0 plays"
                    // (user decision, 2026-09-24)
                    Spacer(Modifier.height(4.dp))
                    RewindValueText(shimmer,
                        text = stringResource(R.string.rewind_home_month_empty_sad),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
internal fun RewindValueText(
    shimmer: Boolean,
    text: String,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    if (shimmer) {
        val style = LocalTextStyle.current
        MeasuredMetadataPlaceholder(text, style.copy(
            fontSize = if (fontSize == TextUnit.Unspecified) style.fontSize else fontSize,
            fontWeight = fontWeight ?: style.fontWeight,
            letterSpacing = if (letterSpacing == TextUnit.Unspecified) style.letterSpacing else letterSpacing,
        ))
    } else Text(text, color = color, fontSize = fontSize, fontWeight = fontWeight, letterSpacing = letterSpacing)
}

/**
 * 2x2 collage of a month's top items: song and album on top, artist (landscape crop of the
 * portrait artwork) and playlist (the app's cover pipeline) below. Missing categories stay
 * on the base background.
 */
@Composable
private fun RewindHomeMonthCollage(
    tops: TopArtworks,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            RewindHomeThumbnailCell(tops.song, Modifier.weight(1f))
            RewindHomeThumbnailCell(tops.album, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            RewindHomeThumbnailCell(tops.artist, Modifier.weight(1f))
            // The playlist cover is a mosaic of corner-aligned square covers: sizing it to
            // the quadrant's height makes the top/bottom covers overlap across the (narrower)
//
            // width, so it fills the whole quadrant instead of a small centered square. The
            // cell is already rounded: no corner radius on the mosaic itself.
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val playlist = tops.playlist
                if (playlist != null) {
                    RewindPlaylistArtwork(
                        playlistId = playlist.playlist.id,
                        title = playlist.playlist.cleanName(),
                        name = playlist.playlist.name,
                        browseId = playlist.playlist.browseId,
                        isYoutubePlaylist = playlist.playlist.isYoutubePlaylist,
                        sizeDp = maxHeight,
                        shape = RoundedCornerShape(0.dp),
                        emptyFallback = true,
                        // The month cell paints its own dark scrim over the collage.
                        dimOverlay = false,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // No top playlist for this month: the N-Zik launcher box.
                    RewindHomeLauncherBox(Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun RewindHomeThumbnailCell(
    thumbnail: String?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (thumbnail.isNullOrBlank()) {
            // No artwork: the N-Zik launcher box (ImageCacheFactory's fallback).
            RewindHomeLauncherBox(Modifier.fillMaxSize())
        } else {
            RewindArtwork(
                imageUrl = thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Compact listening time for the home rows and cells: "38 h 12" over an hour, "45 min" under one (reuses the deck's compact minutes string). */
@Composable
internal fun rewindHomeDuration(minutes: Long): String {
    val hours = (minutes / 60L).toInt()
    val rest = (minutes % 60L).toInt()
    return if (hours > 0) {
        stringResource(R.string.rewind_home_duration, hours, rest)
    } else {
        stringResource(R.string.rewind_minutes_compact, formatRewindNumber(minutes))
    }
}
