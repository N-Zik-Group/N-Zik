package app.n_zik.android.components.ui.screens.rewind.slides

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import app.it.fast4x.rimusic.utils.menuStyleKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.ui.screens.rewind.RewindData
import app.n_zik.android.components.ui.screens.rewind.rewindShareCaptureActive
import app.n_zik.android.uiRoundnessShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Finale slide of the Rewind deck.
 *
 * Export actions live at the bottom as two explicitly labelled pills — "export this page"
 * (single-PNG system share of the displayed slide, [onShareSlide]) and "export all pages"
 * (16-image folder export, [onShare]) — instead of the top-right per-slide share icon the
 * other slides carry.
 *
 * Regeneration actions (spec 2) also live in the interactive branch only, so captured
 * frames never carry them: Month/Year decks offer [onRegeneratePlaylist] (delete-then-
 * recreate of the deck's own playlist, which also covers the current month) and the
 * Global deck offers [onRegenerateAlltime] (the unique `rewind-alltime` snapshot). Each
 * callback returns the number of songs written; the pill label flips to "PLAYLIST
 * UPDATED" for 2 s only on a successful write (count > 0) — an empty window is a silent
 * no-op.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    onShareSlide: (() -> Unit)? = null,
    onRegeneratePlaylist: (suspend () -> Int)? = null,
    onRegenerateAlltime: (suspend () -> Int)? = null
) {
    val textScale = LocalRewindTextScale.current
    val topArtist = data.topArtists.firstOrNull()
    val topSong = data.topSongs.firstOrNull()
    val topAlbum = data.topAlbums.firstOrNull()
    val topPlaylist = data.topPlaylists.firstOrNull()
    val badge = calculateListenerBadge(data)
    // Regeneration pill state (spec 2): the label flips to "PLAYLIST UPDATED" for 2 s
    // after a successful write (count > 0); an empty window (count 0) changes nothing.
    var regenConfirmed by remember { mutableStateOf(false) }
    val regenScope = rememberCoroutineScope()
    val onRegenerate = onRegeneratePlaylist ?: onRegenerateAlltime
    // Export options menu (app-standard sheet, like the song item menu): the single
    // bottom pill is the only live-deck chrome; the individual actions live inside.
    var showExportMenu by remember { mutableStateOf(false) }
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
            // Scaled at the definition: the tiles read it as-is (fontSize = valueSize).
            // Kept deliberately small (device: at 23/19sp the numbers were still
            // compressed on a tight screen — the adaptive tile then shrank them further):
            // the smaller base means the fit clamp kicks in later and reads larger.
            val statSize = textScale.size(if (compact) 14.sp else 17.sp)
            Column(modifier = Modifier.fillMaxSize()) {
                RewindReveal(active, 40, direction = RewindRevealDirection.Left) {
                    RewindKicker(stringResource(R.string.rw_finale_kicker, data.periodLabel), rewindColors.value.lime)
                }
                Spacer(Modifier.height(10.dp))
                RewindReveal(active, 110, direction = RewindRevealDirection.Left) {
                    // Fixed 2-line closing headline ("YOUR %1$s\nREWIND.") — not variable user
                    // content, so no hero marquee despite the size.
                    Text(
                        text = stringResource(R.string.rw_finale_heading, data.periodLabel),
                        color = rewindColors.value.cream,
                        fontSize = textScale.size(if (compact) 34.sp else 42.sp),
                        lineHeight = textScale.size(if (compact) 32.sp else 38.sp),
                        letterSpacing = textScale.letterSpacing((-2.4).sp),
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // The badge title is already showcased by the LISTENER LEVEL row below (and by
                // the share footer in shareMode), so the username line stays plain to avoid
                // repeating it twice on the slide.
                RewindReveal(active, 190) {
                    Text(
                        text = username,
                        color = rewindColors.value.lime,
                        fontSize = textScale.size(9.sp),
                        fontWeight = FontWeight.Black,
                        letterSpacing = textScale.letterSpacing(0.8.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                // The stats grid is the slide's shrinkable block: at the reference it renders
                // at its natural height, and on a tight screen (narrow + enlarged font) it
                // yields vertical space row by row so the export action below always stays
                // visible — the deck has no scrolling. It outweights the bottom spacer 2:1
                // so the slack goes to the grid before it is compressed, and the tiles adapt
                // their text to whatever height they get (FinaleStatTile).
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(2f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
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
                                    fontSize = textScale.size(8.sp),
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = textScale.letterSpacing(0.8.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(badge.titleId),
                                    color = rewindColors.value.ink,
                                    fontSize = textScale.size(13.sp),
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = badge.index.toString(),
                                color = onLime,
                                fontSize = textScale.size(15.sp),
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
                                fontSize = textScale.size(10.sp),
                                fontWeight = FontWeight.Black,
                                letterSpacing = textScale.letterSpacing(1.0.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                                    fontSize = textScale.size(11.sp),
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = badge.index.toString(),
                                    color = onLime,
                                    fontSize = textScale.size(12.sp),
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .background(rewindColors.value.lime, CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Interactive UI: a single "EXPORT OPTIONS" toggle only exists in the live
                    // deck — any captured frame (per-slide share, deck export) renders the brand
                    // footer above instead, so shared images never carry clickable chrome.
                    // The tap opens the app-standard menu (CustomModalBottomSheet + ListMenu,
                    // same pattern as the song item menu) holding the individual actions —
                    // identical on every screen size.
                    RewindReveal(active, 1_080) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rewindColors.value.lime, RoundedCornerShape(100.dp))
                                .clickable(
                                    onClick = { showExportMenu = true },
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                )
                                .padding(vertical = 8.dp),
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
                                text = stringResource(R.string.rw_finale_export_options),
                                color = onLime,
                                fontSize = textScale.size(11.sp),
                                fontWeight = FontWeight.Black,
                                letterSpacing = textScale.letterSpacing(0.7.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    // The export menu reuses the app-standard sheet recipe (same as the song item menu):
    // transparent container, top-only roundness, statusBarsPadding,
    // skipPartiallyExpanded = false (opens at its content height, draggable to full
    // screen). ListMenu.Menu draws its own background, its own top clip and its own
    // drag handle. Capture-safe: it stays closed while a share capture is in flight.
    CustomModalBottomSheet(
        showSheet = showExportMenu && !rewindShareCaptureActive.value,
        onDismissRequest = { showExportMenu = false },
        modifier = Modifier.statusBarsPadding(),
        containerColor = Color.Transparent,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        shape = (uiRoundnessShape() as? RoundedCornerShape)?.let {
            RoundedCornerShape(
                topStart = it.topStart,
                topEnd = it.topEnd,
                bottomStart = CornerSize(0.dp),
                bottomEnd = CornerSize(0.dp)
            )
        } ?: uiRoundnessShape(),
        dragHandle = {}
    ) {
        // The same style toggle as the song item menu (settings: list or grid) — the
        // entries are defined once and rendered in whichever layout the user picked.
        val menuStyle by rememberPreference(menuStyleKey, MenuStyle.List)
        val exportTitle = stringResource(R.string.rw_finale_export_options)
        val regenLabel = stringResource(
            if (regenConfirmed) R.string.rw_finale_regen_done
            else if (onRegeneratePlaylist != null) R.string.rw_finale_regen_playlist
            else R.string.rw_finale_regen_alltime
        )
        val onExportPage: (() -> Unit)? = onShareSlide?.let {
            {
                showExportMenu = false
                it()
            }
        }
        val onRegenerateAction: (() -> Unit)? = onRegenerate?.let {
            {
                regenScope.launch {
                    val count = it()
                    if (count > 0) {
                        regenConfirmed = true
                        delay(2_000)
                        regenConfirmed = false
                    }
                }
            }
        }
        if (menuStyle == MenuStyle.List) {
            ListMenu.Menu(title = exportTitle) {
                if (onExportPage != null) {
                    ListMenu.Entry(
                        text = stringResource(R.string.rw_finale_export_page),
                        icon = { ExportMenuIcon(R.drawable.share_social) },
                        onClick = onExportPage
                    )
                }
                if (onRegenerateAction != null) {
                    ListMenu.Entry(
                        text = regenLabel,
                        icon = { ExportMenuIcon(R.drawable.refresh) },
                        onClick = onRegenerateAction
                    )
                }
                ListMenu.Entry(
                    text = stringResource(R.string.rw_finale_share),
                    icon = { ExportMenuIcon(R.drawable.share_social) },
                    onClick = {
                        showExportMenu = false
                        onShare()
                    }
                )
                ListMenu.Entry(
                    text = stringResource(R.string.rw_finale_play_again),
                    icon = { ExportMenuIcon(R.drawable.refresh) },
                    onClick = {
                        showExportMenu = false
                        onRestart()
                    }
                )
            }
        } else {
            GridMenu.Menu(title = exportTitle) {
                if (onExportPage != null) {
                    item {
                        GridMenu.Entry(
                            text = stringResource(R.string.rw_finale_export_page),
                            icon = { ExportMenuIcon(R.drawable.share_social) },
                            onClick = onExportPage
                        )
                    }
                }
                if (onRegenerateAction != null) {
                    item {
                        GridMenu.Entry(
                            text = regenLabel,
                            icon = { ExportMenuIcon(R.drawable.refresh) },
                            onClick = onRegenerateAction
                        )
                    }
                }
                item {
                    GridMenu.Entry(
                        text = stringResource(R.string.rw_finale_share),
                        icon = { ExportMenuIcon(R.drawable.share_social) },
                        onClick = {
                            showExportMenu = false
                            onShare()
                        }
                    )
                }
                item {
                    GridMenu.Entry(
                        text = stringResource(R.string.rw_finale_play_again),
                        icon = { ExportMenuIcon(R.drawable.refresh) },
                        onClick = {
                            showExportMenu = false
                            onRestart()
                        }
                    )
                }
            }
        }
    }
}

/**
 * The icon chip of the export-options menu, styled exactly like the song item menu's
 * MenuIcon.SettingIcon: a 32dp rounded chip (accent @ 10%) with an 18dp accent icon —
 * the "coloured button + logo" look of the app-standard menus. Wrapped by the Entry
 * icon lambdas of both the list and the grid variant.
 */
@Composable
private fun ExportMenuIcon(iconRes: Int) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(
                colorPalette().accent.copy(alpha = 0.1f),
                uiRoundnessShape()
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = colorPalette().accent,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
internal fun FinaleStatTile(
    label: String,
    value: String,
    background: Color,
    foreground: Color,
    valueSize: TextUnit,
    active: Boolean,
    delayMillis: Int,
    modifier: Modifier = Modifier
) {
    val textScale = LocalRewindTextScale.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        // Natural height at the reference sizes: value line + label line + v-padding.
        // 1sp ≈ 1dp for layout purposes: the TextUnit/Dp unit converters are not
        // available in this Compose version, so the comparison runs on raw values.
        val naturalDp = valueSize.value.dp + textScale.size(8.sp).value.dp + 18.dp
        val tileHeightDp = with(density) { constraints.maxHeight.toDp() }
        // Compressed = the grid yielded vertical space on a tight screen. The tile height
        // is constraint-driven (fillMaxHeight below), so this decision can never feed back
        // into its own size — no oscillation is possible.
        val compressed = tileHeightDp < naturalDp
        // Compressed tile: drop the label and fit the value line into the inner height
        // (tile minus the vertical padding), so the number stays readable instead of
        // clipping top/bottom.
        val fittedValueSize = if (compressed) {
            (tileHeightDp - 18.dp).coerceAtLeast(6.dp).value.sp
        } else {
            valueSize
        }
        RewindReveal(active, delayMillis, scaleFrom = 0.86f, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // fillMaxHeight + clip: when the row above is capped by the tight-screen
                    // weight, the tile is capped with it instead of spilling over the next row.
                    .fillMaxHeight()
                    .background(background, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    text = value,
                    color = foreground,
                    fontSize = fittedValueSize,
                    lineHeight = fittedValueSize,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!compressed) {
                    Text(
                        text = label,
                        color = foreground.copy(alpha = 0.66f),
                        fontSize = textScale.size(8.sp),
                        fontWeight = FontWeight.Black,
                        letterSpacing = textScale.letterSpacing(0.7.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
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
    val textScale = LocalRewindTextScale.current
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
                modifier = Modifier.size(36.dp)
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
                sizeDp = 36.dp,
                modifier = Modifier.size(36.dp)
            )
        } else {
            RewindArtworkWithFallback(
                imageUrl = imageUrl,
                title = title,
                modifier = Modifier.size(36.dp),
                circular = circular,
                background = rewindColors.value.ink,
                foreground = rewindColors.value.cream
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = labelColor,
                fontSize = textScale.size(7.sp),
                fontWeight = FontWeight.Black,
                letterSpacing = textScale.letterSpacing(0.7.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            RewindAdaptiveTitle(
                text = title,
                color = rewindColors.value.flatTextOn(background),
                fontSize = textScale.size(10.sp),
                lineHeight = textScale.size(12.sp),
                fontWeight = FontWeight.Black
            )
            Text(
                text = subtitle,
                color = rewindColors.value.flatTextOn(background).copy(alpha = 0.56f),
                fontSize = textScale.size(8.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
