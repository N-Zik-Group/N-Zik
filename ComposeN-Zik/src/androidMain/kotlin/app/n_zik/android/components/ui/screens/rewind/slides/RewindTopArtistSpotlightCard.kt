package app.n_zik.android.components.ui.screens.rewind.slides

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import app.n_zik.android.components.ui.screens.rewind.TopArtist
import app.n_zik.android.components.ui.screens.rewind.rewindArtistLevel
import app.n_zik.android.components.ui.screens.rewind.rewindShareCaptureActive
import app.n_zik.android.uiRoundnessShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewindTopArtistSpotlightCard(
    artist: TopArtist?,
    periodLabel: String,
    page: Int,
    pageCount: Int,
    active: Boolean,
    onNext: () -> Unit,
    onShareSlide: (() -> Unit)? = null
) {
    val artistName = artist?.artist?.cleanName().orEmpty()
    val bioMeta = rememberArtistBio(artist?.artist?.id, artist?.artist?.description)
    RewindStoryShell(
        page = page,
        pageCount = pageCount,
        background = rewindColors.value.ink,
        progressColor = rewindColors.value.cream,
        onNext = onNext,
        onShareSlide = onShareSlide,
        backgroundArt = {
            Canvas(Modifier.fillMaxSize()) {
                val pinkSlash = Path().apply {
                    moveTo(size.width * 0.77f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height * 0.20f)
                    lineTo(size.width * 0.93f, size.height * 0.16f)
                    close()
                }
                drawPath(pinkSlash, rewindColors.value.pink)
                drawRect(
                    color = rewindColors.value.lime,
                    topLeft = Offset(0f, size.height * 0.79f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.05f, size.height * 0.13f)
                )
            }
        }
    ) {
        if (artist == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                RewindEmptyState(
                    title = stringResource(R.string.rw_artist_spotlight_empty_title),
                    body = stringResource(R.string.rw_artist_spotlight_empty_body),
                    foreground = rewindColors.value.cream,
                    accent = rewindColors.value.lime
                )
            }
            return@RewindStoryShell
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 700.dp
            val textScale = LocalRewindTextScale.current
            // The photo scales with the deck's text squared (layoutFactor² = 1.0 at the
            // reference): it yields vertical room quickly on a narrow screen / enlarged
            // font, so the bio below gets space even before its internal scroll kicks in.
            val heroHeight = (if (compact) 238.dp else 292.dp) * (textScale.layoutFactor * textScale.layoutFactor)
            // Length ladder of the design, then the deck sizing policy on top — the ladder
            // picks the tier for the name length, the scale adapts it to the screen
            val nameSize = textScale.size(
                when {
                    artistName.length > 20 -> if (compact) 31.sp else 36.sp
                    artistName.length > 13 -> if (compact) 37.sp else 43.sp
                    else -> if (compact) 43.sp else 50.sp
                }
            )
            Column(modifier = Modifier.fillMaxSize()) {
                RewindReveal(active, 40, direction = RewindRevealDirection.Left) {
                    RewindKicker(stringResource(R.string.rw_artist_spotlight_kicker, periodLabel), rewindColors.value.lime)
                }
                Spacer(Modifier.height(12.dp))
                RewindReveal(active, 180, scaleFrom = 0.90f, durationMillis = 760) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heroHeight)
                            .background(rewindColors.value.purple)
                    ) {
                        RewindArtistArtwork(
                            artistName = artistName,
                            primaryUrl = artist.artist.thumbnailUrl,
                            preferWikipedia = false,
                            circular = false,
                            enableSlideshow = true,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Fade the portrait naturally into the black page instead of cropping it
                        // into a circle. This also makes portrait/landscape source images behave.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        0.00f to rewindColors.value.ink,
                                        0.12f to rewindColors.value.ink.copy(alpha = 0.70f),
                                        0.28f to Color.Transparent,
                                        0.74f to Color.Transparent,
                                        0.91f to rewindColors.value.ink.copy(alpha = 0.72f),
                                        1.00f to rewindColors.value.ink
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.00f to rewindColors.value.ink.copy(alpha = 0.15f),
                                        0.60f to Color.Transparent,
                                        1.00f to rewindColors.value.ink
                                    )
                                )
                        )
                        RewindReveal(
                            active = active,
                            delayMillis = 620,
                            scaleFrom = 0.55f,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 13.dp, end = 12.dp)
                        ) {
                            Text(
                                text = "#1",
                                color = rewindColors.value.textOn(rewindColors.value.lime),
                                fontSize = textScale.size(30.sp),
                                lineHeight = textScale.size(30.sp),
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .background(rewindColors.value.lime, RoundedCornerShape(2.dp))
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                RewindReveal(active, 520, direction = RewindRevealDirection.Left, distance = 30.dp) {
                    RewindAdaptiveTitle(
                        text = artistName.uppercase(),
                        color = rewindColors.value.cream,
                        fontSize = nameSize,
                        lineHeight = nameSize * 0.90f,
                        letterSpacing = textScale.letterSpacing((-2.3).sp),
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.height(10.dp))
                RewindReveal(active, 650) {
                    RewindLevelBadge(
                        level = rewindArtistLevel(artist.minutes),
                        foreground = rewindColors.value.cream,
                        pillBackground = rewindColors.value.cream,
                        pillForeground = rewindColors.value.ink
                    )
                }
                Spacer(Modifier.height(10.dp))
                RewindReveal(active, 760) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpotlightMetric(
                            label = stringResource(R.string.rw_label_minutes),
                            value = formatRewindNumber(artist.minutes),
                            modifier = Modifier.weight(1f)
                        )
                        SpotlightMetric(
                            label = stringResource(R.string.rw_label_songs),
                            value = formatRewindNumber(artist.songCount.toLong()),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                val bio = bioMeta?.bio ?: bioMeta?.description
                if (!bio.isNullOrBlank()) {
                    // The bio block takes whatever vertical space is left after the
                    // photo/name/badge/metrics. The text is deliberately small (9sp) so a
                    // typical bio fits; a bio that does NOT fit shows a short preview
                    // (a few lines, clamped with an ellipsis) with a "SEE MORE" pill
                    // below opening the full text in a sheet.
                    // There is no auto-drift and no manual scroll zone on the slide: a
                    // swipeable area would leak into the deck's swipe. A running capture
                    // hides the pill, so shared images stay stable.
                    val density = LocalDensity.current
                    // The area box is measured (its full height, in px); a hidden
                    // natural-height copy of the bio is measured too — the overflow is a
                    // plain comparison of the two measurements, neither of which depends
                    // on the decision, so it can never feed back into itself (no
                    // oscillation).
                    var bioAreaPx by remember { mutableStateOf(0) }
                    var bioNaturalPx by remember { mutableStateOf(0) }
                    var showBioSheet by remember { mutableStateOf(false) }
                    // The app menu-style setting (settings: list or grid) — the same
                    // toggle the song item menu reads.
                    val menuStyle by rememberPreference(menuStyleKey, MenuStyle.List)
                    val bioOverflow = bioAreaPx > 0 && bioNaturalPx > bioAreaPx
                    // Overflowing bio: a short fixed preview (a few lines) with the
                    // SEE MORE pill right below — the owner wants a glanceable teaser,
                    // not the whole block; the full text lives in the sheet. The area
                    // budget (pill reserved) stays the hard cap, so the pill can never
                    // fall off the slide on a very tight screen. A bio that fits
                    // renders in full, no pill.
                    val bioMaxLines = if (bioOverflow) {
                        minOf(
                            BIO_PREVIEW_LINES,
                            rewindBioMaxLines(
                                areaHeightDp = with(density) { bioAreaPx.toDp() },
                                // 1sp ≈ 1dp for layout purposes: the unit converters are
                                // not available in this Compose version, so the math
                                // runs on values.
                                lineHeightDp = textScale.size(13.sp).value.dp,
                                reservedDp = BIO_SEE_MORE_RESERVED_DP.dp
                            )
                        )
                    } else {
                        rewindBioMaxLines(
                            areaHeightDp = with(density) { bioAreaPx.toDp() },
                            lineHeightDp = textScale.size(13.sp).value.dp,
                            reservedDp = 0.dp
                        )
                    }
                    RewindReveal(
                        active = active,
                        delayMillis = 960,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = stringResource(R.string.rw_artist_spotlight_about),
                                color = rewindColors.value.lime,
                                fontSize = textScale.size(9.sp),
                                fontWeight = FontWeight.Black,
                                letterSpacing = textScale.letterSpacing(1.0.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(5.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .onSizeChanged { bioAreaPx = it.height }
                            ) {
                                // Hidden natural-height copy: same text, same sizes, fully
                                // transparent — its measured height is the bio's real
                                // height regardless of the visible clamp (unbounded = true
                                // so it is NOT squashed by the box's bounded constraint —
                                // in Compose 1.12 a capped Column squashes its children,
                                // which is exactly what makes the unbounded measurement
                                // mandatory here).
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                                ) {
                                    Text(
                                        text = bio,
                                        color = Color.Transparent,
                                        fontSize = textScale.size(9.sp),
                                        lineHeight = textScale.size(13.sp),
                                        modifier = Modifier.onSizeChanged { bioNaturalPx = it.height }
                                    )
                                }
                                // Visible copy: the bio that fits renders in full; the
                                // bio that overflows shows a short preview (bioMaxLines)
                                // with an ellipsis instead of a cut-off last line, and
                                // the SEE MORE pill right below.
                                Column {
                                    Text(
                                        text = bio,
                                        color = rewindColors.value.cream.copy(alpha = 0.78f),
                                        fontSize = textScale.size(9.sp),
                                        lineHeight = textScale.size(13.sp),
                                        maxLines = bioMaxLines,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (bioOverflow && !rewindShareCaptureActive.value) {
                                        Spacer(Modifier.height(6.dp))
                                        RewindSeeMorePill(
                                            text = stringResource(R.string.rw_artist_spotlight_see_more),
                                            onClick = { showBioSheet = true }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = stringResource(R.string.rw_artist_spotlight_innertube),
                                color = rewindColors.value.cream.copy(alpha = 0.35f),
                                fontSize = textScale.size(8.sp),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = textScale.letterSpacing(0.8.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // Full bio sheet — the same app-standard menu as the finale's export
                    // options (CustomModalBottomSheet + ListMenu.Menu): the menu draws its
                    // own background, top clip and drag handle, and its content column is
                    // already scrollable — the sheet is a popup, not part of the deck, so
                    // the scroll never leaks into a swipe. Capture-safe: it stays closed
                    // while a share capture is in flight.
                    CustomModalBottomSheet(
                        showSheet = showBioSheet && !rewindShareCaptureActive.value,
                        onDismissRequest = { showBioSheet = false },
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
                        // Same style toggle as the app's item menus (settings: list or
                        // grid): the full text renders as the single item of the grid,
                        // or as plain content of the list — the menu chrome (background,
                        // clip, drag handle, title header, scrolling) is shared.
                        if (menuStyle == MenuStyle.List) {
                            ListMenu.Menu(
                                title = stringResource(R.string.rw_artist_spotlight_about)
                            ) {
                                Text(
                                    text = bio,
                                    color = colorPalette().text.copy(alpha = 0.78f),
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.rw_artist_spotlight_innertube),
                                    color = colorPalette().text.copy(alpha = 0.45f),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    maxLines = 1
                                )
                            }
                        } else {
                            GridMenu.Menu(
                                title = stringResource(R.string.rw_artist_spotlight_about)
                            ) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Column {
                                        Text(
                                            text = bio,
                                            color = colorPalette().text.copy(alpha = 0.78f),
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                            text = stringResource(R.string.rw_artist_spotlight_innertube),
                                            color = colorPalette().text.copy(alpha = 0.45f),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp,
                                            maxLines = 1
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
}

@Composable
private fun SpotlightMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val textScale = LocalRewindTextScale.current
    Column(
        modifier = modifier
            .background(rewindColors.value.cream, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = value,
            color = rewindColors.value.ink,
            fontSize = textScale.size(22.sp),
            lineHeight = textScale.size(22.sp),
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.rewindMarqueeOnly()
        )
        Text(
            text = label,
            color = rewindColors.value.ink.copy(alpha = 0.58f),
            fontSize = textScale.size(8.sp),
            fontWeight = FontWeight.Black,
            letterSpacing = textScale.letterSpacing(0.8.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Vertical space (dp) reserved inside the bio area for the "SEE MORE" pill + its
 * spacer, deducted from the line budget when the bio overflows — so the last clamped
 * line and the pill can never collide.
 */
internal const val BIO_SEE_MORE_RESERVED_DP = 28f

/**
 * Lines of the bio previewed on the slide when it overflows its area: the owner
 * wants a short teaser ("quelques lignes") with the SEE MORE button right below —
 * the full text lives in the sheet. The area budget ([rewindBioMaxLines]) is
 * applied on top as the hard cap.
 */
internal const val BIO_PREVIEW_LINES = 3

/**
 * The hard area budget: how many lines the bio block can physically hold given the
 * measured area height, the line height and the reserved SEE MORE pill space. Pure
 * on purpose (see [rewindBioHalfLines]). Pinned by RewindBioFitTest.
 */
internal fun rewindBioMaxLines(areaHeightDp: Dp, lineHeightDp: Dp, reservedDp: Dp): Int =
    ((areaHeightDp - reservedDp).coerceAtLeast(0.dp) / lineHeightDp).toInt().coerceAtLeast(1)

/**
 * The "SEE MORE" pill of the artist spotlight bio: a small cream outlined pill that
 * opens the full-bio sheet. Same visual language as the deck's outlined action pills.
 */
@Composable
private fun RewindSeeMorePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                rewindColors.value.cream.copy(alpha = 0.10f),
                RoundedCornerShape(100.dp)
            )
            .border(
                1.dp,
                rewindColors.value.cream.copy(alpha = 0.45f),
                RoundedCornerShape(100.dp)
            )
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = rewindColors.value.cream,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.7.sp,
            maxLines = 1
        )
    }
}
