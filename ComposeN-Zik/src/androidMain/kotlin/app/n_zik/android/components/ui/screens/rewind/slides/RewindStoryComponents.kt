package app.n_zik.android.components.ui.screens.rewind.slides

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

import android.text.format.DateFormat
import androidx.annotation.PluralsRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.hypnoticcanvas.shaderBackground

import app.n_zik.android.R
import app.n_zik.android.cleanPrefix
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.LocalGlassMaterialSource
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCombinedBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.glassContentCaptureEnabled
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.components.ui.screens.rewind.RewindData
import app.n_zik.android.components.ui.screens.rewind.RewindLevel
import app.n_zik.android.components.ui.screens.rewind.rewindCaptureMode
import app.n_zik.android.components.ui.screens.rewind.rewindShareCaptureActive
import app.n_zik.android.components.ui.screens.rewind.rewindSlideShaders
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.Hsl
import app.n_zik.android.ui.styling.hsl
import app.n_zik.android.utils.conditional
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.rememberPreference

/**
 * Slide palette derived from the app's current [ColorPalette], so the deck recolors with the
 * NZik theme and follows the dynamic accent extracted from the now-playing cover.
 *
 * [lime] is the accent itself and every other vivid color stays on the same hue — the slides
 * are told apart by tone alone, so the whole deck reads as one violet (NZik) family.
 * [ink] and [cream] follow the app background/text.
 */
internal data class RewindColors(
    val ink: Color,
    val cream: Color,
    val purple: Color,
    val pink: Color,
    val red: Color,
    val orange: Color,
    val lime: Color,
    val blue: Color,
    val yellow: Color
) {
    companion object {
        /** Original deck defaults, used until RewindScreen publishes the themed palette. */
        val fallback = RewindColors(
            ink = Color(0xFF08070C),
            cream = Color(0xFFFFF6E6),
            purple = Color(0xFF461CF4),
            pink = Color(0xFFFF4B98),
            red = Color(0xFFE83B2F),
            orange = Color(0xFFFF5A2E),
            lime = Color(0xFFD9FF31),
            blue = Color(0xFF2369EB),
            yellow = Color(0xFFFFD72E)
        )

        fun fromPalette(palette: ColorPalette): RewindColors {
            val (hue, sat, _) = palette.accent.hsl
            // Every vivid color keeps the app's accent hue; the slides are told apart by tone
            // alone so the deck recolors as a coherent monochrome family with the theme.
            fun tonal(saturation: Float, lightness: Float) =
                Hsl(floatArrayOf(hue.mod(360f), saturation, lightness)).color
            return RewindColors(
                ink = palette.background0,
                cream = palette.text,
                lime = palette.accent,
                yellow = tonal(sat * 0.45f, 0.80f),
                pink = tonal(sat * 0.70f, 0.62f),
                blue = tonal(sat * 0.75f, 0.50f),
                orange = tonal(sat * 0.85f, 0.45f),
                purple = tonal(sat.coerceAtMost(0.9f), 0.35f),
                red = tonal(sat.coerceAtMost(0.9f), 0.26f)
            )
        }
    }

    /** ITU-R BT.709 relative luminance, 0 (black) to 1 (white). */
    private fun Color.luminance(): Float =
        0.2126f * red + 0.7152f * green + 0.0722f * blue

    /**
     * Readable side of the ink/cream pair to lay directly on [background]: the lighter side on
     * dark backgrounds, the darker side on light ones.
     *
     * Judged on the scrim-darkened background (slides always draw [REWIND_SCRIM_ALPHA] of ink
     * over it), with the sides resolved by their own luminance rather than by name, because
     * ink and cream swap dark/light roles with the app theme (light mode).
     */
    fun textOn(background: Color): Color {
        val effective = lerp(ink, background, 1f - REWIND_SCRIM_ALPHA)
        val darkSide = if (ink.luminance() <= cream.luminance()) ink else cream
        val lightSide = if (darkSide == ink) cream else ink
        return if (effective.luminance() < 0.45f) lightSide else darkSide
    }

    /** Readable side of the ink/cream pair for a solid surface the slide scrim does NOT cover (stat tiles, feature cards, pills): judged on the surface's own luminance, because the scrim-darkened assumption of [textOn] makes light text land on bright surfaces. */
    fun flatTextOn(surface: Color): Color {
        val darkSide = if (ink.luminance() <= cream.luminance()) ink else cream
        val lightSide = if (darkSide == ink) cream else ink
        return if (surface.luminance() < 0.45f) lightSide else darkSide
    }
}

/** Scrim laid over every slide background so the shader never washes out the typography. */
internal const val REWIND_SCRIM_ALPHA = 0.55f

/**
 * Live slide palette, written by RewindScreen from the app's current [ColorPalette] while the
 * deck is on screen.
 *
 * Backed by snapshot state rather than a CompositionLocal so every slide can read it from any
 * context — composable calls, Canvas draw lambdas, remember blocks, default argument values —
 * while composable reads stay reactive to app palette (accent) changes.
 */
internal val rewindColors = mutableStateOf(RewindColors.fallback)

/**
 * Whether the pager page currently being composed is the one on screen. Set per page by
 * RewindScreen so slide backgrounds only run their animated shader while actually visible
 * (off-screen pages fall back to a static solid color, saving GPU work).
 */
internal val LocalRewindActive = staticCompositionLocalOf { true }

/** Early activation window for slide shaders (current page and one page on each side), so the animated background is already running before a swipe lands — no "pop" on arrival. */
internal val LocalRewindShaderWarm = staticCompositionLocalOf { false }

internal enum class RewindRevealDirection {
    Up,
    Down,
    Left,
    Right
}

/**
 * Full-screen Rewind story shell.
 *
 * Important layout rule: slide content never owns the very bottom edge. That space is
 * reserved for the NZik signature so the brand cannot land on top of slide copy.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RewindStoryShell(
    page: Int,
    pageCount: Int,
    background: Color,
    progressColor: Color,
    modifier: Modifier = Modifier,
    onNext: (() -> Unit)? = null,
    onShareSlide: (() -> Unit)? = null,
    showProgress: Boolean = true,
    showBrand: Boolean = true,
    backgroundArt: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    // During screenshot capture the deck renders its solid fallback: the capture canvas is
    // software-only, where the RuntimeShader cannot be replayed at all.
    val shader =
        if (LocalRewindShaderWarm.current && !rewindCaptureMode.value) {
            rewindSlideShaders.getOrNull(page)
        } else {
            null
        }
    // During the 16-image share capture the system bars are hidden for clean frames; the
    // ignoring-visibility insets report the bar sizes even while hidden, so the deck keeps
//
    // exactly the same safe-area padding as on screen (the regular insets would collapse to 0).
    val holdBarPadding = rewindShareCaptureActive.value
    // Per-slide share button (top-right, below the progress row). It is an overlay only —
    // its visibility never changes the content layout, so pages never reflow when it
//
    // appears or disappears.
    val showShareButton = onShareSlide != null && LocalRewindActive.current && !rewindShareCaptureActive.value
    val capture = glassContentCaptureEnabled(rememberGlassSettings()) &&
        !rewindCaptureMode.value && !rewindShareCaptureActive.value
    val backgroundSource = if (capture) rememberLayerBackdrop() else null
    val foregroundSource = if (capture) rememberLayerBackdrop() else null
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(enabled = onNext != null) { onNext?.invoke() }
    ) {
        // This sibling ends before every sampling card and action.
        Box(Modifier.matchParentSize().then(if (backgroundSource != null)
            Modifier.layerBackdrop(backgroundSource) else Modifier)) {
            // Dark scrim over every slide background so the bright shader colors (and the
            // solid fallback) never wash out the typography
            val scrim = rewindColors.value.ink.copy(alpha = REWIND_SCRIM_ALPHA)
            if (shader != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shaderBackground(shader)
                        .background(scrim)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(background)
                        .background(scrim)
                )
            }
            backgroundArt()
        }
        CompositionLocalProvider(LocalGlassContainerBackdrop provides backgroundSource,
            LocalGlassMaterialSource provides backgroundSource) {
            Box(Modifier.matchParentSize().then(if (foregroundSource != null)
                Modifier.layerBackdrop(foregroundSource) else Modifier)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (holdBarPadding) {
                                Modifier.windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                            } else {
                                Modifier.statusBarsPadding()
                            }
                        )
                        .then(
                            if (holdBarPadding) {
                                Modifier.windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                            } else {
                                Modifier.navigationBarsPadding()
                            }
                        )
                        .padding(horizontal = 20.dp)
                        .padding(top = 28.dp, bottom = if (showBrand) 58.dp else 36.dp)
                ) {
                    RewindStoryProgress(
                        page = page,
                        pageCount = pageCount,
                        color = progressColor,
                        visible = showProgress
                    )
                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        content()
                    }
                }
                if (showBrand) {
                    RewindBrandBug(
                        foreground = progressColor,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .then(
                                if (holdBarPadding) {
                                    Modifier.windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                                } else {
                                    Modifier.navigationBarsPadding()
                                }
                            )
                            .padding(end = 16.dp, bottom = 8.dp)
                    )
                }
            }
        }
        // The overlay share control may overlap slide text. Its source includes
        // preceding foreground, but the capture ends before this control itself.
        val shareSource = if (backgroundSource != null && foregroundSource != null)
            rememberCombinedBackdrop(backgroundSource, foregroundSource) else backgroundSource
        CompositionLocalProvider(LocalGlassContainerBackdrop provides shareSource,
            LocalGlassMaterialSource provides shareSource) {
            // Per-slide system share (single PNG): only the active page offers it, hidden
            // during the export capture so it never lands in the exported frames. Overlay
    //
            // below the progress row (RewindShareSlideButton) — never in the layout flow,
            // so toggling it can't reflow the slide. Pops in/out with a small scale/fade,
    //
            // with an instant exit during a capture so no copied frame shows it mid-animation
            // (same rule as the deck's reveal snaps).
            AnimatedVisibility(
                visible = showShareButton,
                enter = scaleIn(
                    initialScale = 0.5f,
                    // Spring with a light overshoot: a smooth transition that still reads as a pop
                    animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)
                ) + fadeIn(tween(200, easing = LinearOutSlowInEasing)),
                exit = if (rewindShareCaptureActive.value) {
                    // Export capture in flight: vanish instantly for a clean frame
                    fadeOut(tween(0))
                } else {
                    scaleOut(
                        targetScale = 0.8f,
                        animationSpec = tween(160, easing = FastOutLinearInEasing)
                    ) + fadeOut(tween(160, easing = FastOutLinearInEasing))
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 44.dp, end = 12.dp)
            ) {
                RewindShareSlideButton(
                    tint = progressColor.copy(alpha = 0.85f),
                    onClick = { onShareSlide?.invoke() }
                )
            }
        }
    }
}

/**
 * Share icon in the top-right corner of a slide, below the 3dp progress row. It is an
 * overlay (outside the content layout) so showing or hiding it never reflows the slide.
 * The 24dp glyph sits in a 40dp touch target. Capturing and sharing the displayed slide is
 * handled by the caller
 * (see [app.n_zik.android.components.ui.screens.rewind.shareRewindSlide]).
 */
@Composable
internal fun RewindShareSlideButton(
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = rewindActionShape(RoundedCornerShape(14.dp))
    RewindActionFeedback(shape) { feedback, source ->
        Box(
            modifier = modifier.then(feedback)
                .size(40.dp)
                // Same rounded chrome as the deck's volume toggle (spec 3)
                .clip(shape)
                .iconBadgeBackground(rewindColors.value.cream.copy(alpha = 0.12f), shape)
                .clickable(interactionSource = source, indication = if (source == null) androidx.compose.foundation.LocalIndication.current else null, onClick = onClick)
        ) {
            Icon(
                painter = painterResource(R.drawable.share_social),
                contentDescription = stringResource(R.string.rewind_share_slide),
                tint = tint,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
            )
        }
    }
}

@Composable
private fun RewindStoryProgress(
    page: Int,
    pageCount: Int,
    color: Color,
    visible: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(pageCount) { index ->
            val alpha = if (!visible) 0f else when {
                index < page -> 0.82f
                index == page -> 1f
                else -> 0.22f
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(color.copy(alpha = alpha))
            )
        }
    }
}

@Composable
internal fun RewindBrandBug(
    foreground: Color,
    modifier: Modifier = Modifier
) {
    val textScale = LocalRewindTextScale.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(5.dp))
        )
        Text(
            text = stringResource(R.string.discord_presence_name),
            color = foreground.copy(alpha = 0.68f),
            fontSize = textScale.size(8.sp),
            lineHeight = textScale.size(9.sp),
            fontWeight = FontWeight.Black,
            letterSpacing = textScale.letterSpacing(0.9.sp)
        )
    }
}

/**
 * Stagger delay used by the deck's reveal chains. Skipped entirely while the 16-image export
 * capture is running: every page settles straight to its final state, so a stagger must not
 * hold a page back before its frame is copied.
 */
internal suspend fun rewindRevealDelay(millis: Int) {
    if (!rewindShareCaptureActive.value) delay(millis.toLong())
}

/**
 * Drives a reveal [Animatable] to [target]: animated with [spec] in normal use, but snapped
 * instantly while the export capture is running, so no page is ever screenshotted mid-reveal.
 * Float-only: every reveal in the deck drives a Float progress (the star projection keeps
 * the Animatable's animation-vector parameter out of the way).
 */
internal suspend fun rewindDriveTo(
    anim: Animatable<Float, *>,
    target: Float,
    spec: AnimationSpec<Float>
) {
    if (rewindShareCaptureActive.value) {
        anim.snapTo(target)
    } else {
        anim.animateTo(target, spec)
    }
}

/**
 * One-shot reveal. Nothing loops: when a page becomes active the element enters once,
 * settles, and stays still until the user leaves the page.
 */
@Composable
internal fun RewindReveal(
    active: Boolean,
    delayMillis: Int = 0,
    modifier: Modifier = Modifier,
    distance: Dp = 24.dp,
    direction: RewindRevealDirection = RewindRevealDirection.Up,
    scaleFrom: Float = 0.98f,
    durationMillis: Int = 520,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (!active) {
            progress.snapTo(0f)
        } else {
            progress.snapTo(0f)
            if (rewindShareCaptureActive.value) {
                // Export capture: settle straight to the revealed state
                progress.snapTo(1f)
            } else {
                if (delayMillis > 0) delay(delayMillis.toLong())
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis, easing = LinearOutSlowInEasing)
                )
            }
        }
    }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            val travel = distance.toPx() * (1f - progress.value)
            when (direction) {
                RewindRevealDirection.Up -> translationY = travel
                RewindRevealDirection.Down -> translationY = -travel
                RewindRevealDirection.Left -> translationX = travel
                RewindRevealDirection.Right -> translationX = -travel
            }
            scaleX = scaleFrom + (1f - scaleFrom) * progress.value
            scaleY = scaleFrom + (1f - scaleFrom) * progress.value
        }
    ) {
        content()
    }
}

@Composable
internal fun RewindAnimatedNumber(
    value: Long,
    active: Boolean,
    color: Color,
    fontSize: Int,
    delayMillis: Int = 220,
    modifier: Modifier = Modifier,
    durationMillis: Int = 1050
) {
    val anim = remember(value) { Animatable(0f) }
    LaunchedEffect(active, value) {
        if (!active) {
            anim.snapTo(0f)
        } else {
            anim.snapTo(0f)
            val target = value.coerceAtLeast(0).toFloat()
            if (rewindShareCaptureActive.value) {
                // Export capture: show the final number straight away
                anim.snapTo(target)
            } else {
                delay(delayMillis.toLong())
                anim.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)
                )
            }
        }
    }
    // The sizing policy scales the base size; the line height is derived from the scaled
    // size so both stay in ratio (the deck's tight display lines never re-wrap).
    val textScale = LocalRewindTextScale.current
    val size = textScale.size(fontSize.sp)
    Text(
        text = formatRewindNumber(anim.value.toLong()),
        color = color,
        fontSize = size,
        lineHeight = size * 0.90f,
        letterSpacing = textScale.letterSpacing((-3.2).sp),
        fontWeight = FontWeight.Black,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.rewindMarqueeOnly()
    )
}

/**
 * Single-line display-text guard for the deck's hero lines: fills the available width and,
 * when the line is wider than the slide, scrolls it in a marquee — honoring the global
 * "disable scrolling text" setting, which falls back to ellipsis alone (same convention
 * as the header badges and the onboarding cards).
 *
 * For hero display texts (display name, N°1, slide titles, empty-state title). Every hero
 * in the deck pairs it with `maxLines = 1` + `TextOverflow.Ellipsis` so the marquee is
 * the only behavior that changes when the line fits.
 *
 * Order contract: apply any width cap OUTSIDE this helper (e.g. the intro display name's
 * `fillMaxWidth(0.90f)`); the marquee scrolls within the final width it is given.
 */
@Composable
internal fun Modifier.rewindHeroLine(): Modifier = this
    .fillMaxWidth()
    .rewindMarqueeOnly()

/**
 * Marquee variant for wrap-content display texts (row names, stat values, badge labels)
 * that are capped with `maxLines = 1` + `TextOverflow.Ellipsis` but must keep their
 * natural width — [rewindHeroLine]'s `fillMaxWidth` would stretch the line and change
 * the layout. The text stays wrap-content: nothing moves when it fits, and the marquee
 * (honoring the global "disable scrolling text" setting) only activates where the
 * ellipsis would otherwise appear. It also stays dormant while the page is inactive
 * ([LocalRewindActive]), so off-screen slides never pump the animation.
 */
@Composable
internal fun Modifier.rewindMarqueeOnly(): Modifier {
    val isScrollingTextDisabled by rememberPreference(disableScrollingTextKey, false)
    val active = LocalRewindActive.current
    return this.conditional(!isScrollingTextDisabled && active) {
        basicMarquee(iterations = Int.MAX_VALUE)
    }
}

/**
 * Title discipline of the deck slides ("two lines max, then marquee"): the text keeps its
 * design line breaks and may occupy up to two lines; when it would not fit in two lines
 * (narrow screen, enlarged font, long content) it degrades to a single scrolling marquee
 * line — honoring the global "disable scrolling text" setting, which falls back to the
 * ellipsis — instead of being cut with "…". Nothing moves when the text fits.
 *
 * Used for the slide titles and the variable content titles (artist / song / album
 * names, taglines, empty-state texts). Single-line labels and stat values keep using
 * [rewindMarqueeOnly] directly; the artist bio stays fully wrapped (no cap, no marquee)
 * and auto-drifts slowly on its own when it overflows its block — deliberately no manual
 * swipe zone (a swipeable area leaks into the deck's swipe and reloads the card), and
 * the "disable scrolling text" setting stops the drift.
 */
@Composable
internal fun RewindAdaptiveTitle(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = fontSize,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Start
) {
    val isScrollingTextDisabled by rememberPreference(disableScrollingTextKey, false)
    val active = LocalRewindActive.current
    var marqueeLine by remember { mutableStateOf(false) }
    Text(
        // Marquee fallback: flatten the design line breaks so the line scrolls cleanly.
        text = if (marqueeLine) text.replace('\n', ' ') else text,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = if (marqueeLine) 1 else 2,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            // Two-line mode: an ellipsis on the last visible line means the text would
            // need a third line — degrade to the single-line marquee (sticky).
            if (!marqueeLine && result.isLineEllipsized(result.lineCount - 1)) marqueeLine = true
        },
        modifier = modifier.conditional(marqueeLine && !isScrollingTextDisabled && active) {
            basicMarquee(iterations = Int.MAX_VALUE)
        }
    )
}

@Composable
internal fun RewindKicker(
    text: String,
    background: Color,
    // The pill is a small solid surface the slide scrim does not cover, so contrast is
    // judged on the pill itself.
    foreground: Color = rewindColors.value.flatTextOn(background),
    modifier: Modifier = Modifier,
    preserveCase: Boolean = false
) {
    val textScale = LocalRewindTextScale.current
    Text(
        text = if (preserveCase) text else text.uppercase(),
        color = foreground,
        fontSize = textScale.size(10.sp),
        lineHeight = textScale.size(11.sp),
        fontWeight = FontWeight.Black,
        letterSpacing = textScale.letterSpacing(1.0.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .rewindCardBackground(background, RoundedCornerShape(2.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp)
            .rewindMarqueeOnly()
    )
}

@Composable
internal fun RewindRankRow(
    rank: Int,
    title: String,
    subtitle: String,
    meta: String,
    imageUrl: String?,
    foreground: Color,
    accent: Color,
    active: Boolean,
    delayMillis: Int,
    modifier: Modifier = Modifier,
    circularArt: Boolean = false,
    rowBackground: Color? = null
) {
    RewindReveal(
        active = active,
        delayMillis = delayMillis,
        direction = RewindRevealDirection.Left,
        distance = 18.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (rowBackground != null) {
                        Modifier
                            .rewindCardBackground(rowBackground, RoundedCornerShape(11.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    } else {
                        Modifier
                    }
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val textScale = LocalRewindTextScale.current
            Text(
                text = rank.toString(),
                color = accent,
                fontSize = textScale.size(25.sp),
                lineHeight = textScale.size(26.sp),
                fontWeight = FontWeight.Black,
                // widthIn (not a fixed width) so a two-digit rank ("10") widens the slot
                // instead of wrapping on an enlarged font; single digits keep the 27dp slot
                modifier = Modifier.widthIn(min = 27.dp),
                textAlign = TextAlign.Center
            )
            RewindArtworkWithFallback(
                imageUrl = imageUrl,
                title = title,
                modifier = Modifier.size(44.dp),
                circular = circularArt,
                background = accent.copy(alpha = 0.28f),
                foreground = foreground
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cleanPrefix(title).ifBlank { "—" },
                    color = foreground,
                    fontSize = textScale.size(13.sp),
                    lineHeight = textScale.size(15.sp),
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.rewindMarqueeOnly()
                )
                Text(
                    text = cleanPrefix(subtitle).ifBlank { "—" },
                    color = foreground.copy(alpha = 0.62f),
                    fontSize = textScale.size(10.sp),
                    lineHeight = textScale.size(13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.rewindMarqueeOnly()
                )
            }
            Text(
                text = meta,
                color = foreground.copy(alpha = 0.72f),
                fontSize = textScale.size(10.sp),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.width(60.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Compact listening-level badge used on the show slides (top song, album, artist, playlist):
 * a small "YOUR LEVEL" label above the tier pill, with the tier tagline below it.
 */
@Composable
internal fun RewindLevelBadge(
    level: RewindLevel,
    foreground: Color,
    pillBackground: Color,
    pillForeground: Color,
    modifier: Modifier = Modifier
) {
    val textScale = LocalRewindTextScale.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.rewind_label_your_level),
            color = foreground.copy(alpha = 0.62f),
            fontSize = textScale.size(8.sp),
            lineHeight = textScale.size(10.sp),
            fontWeight = FontWeight.Black,
            letterSpacing = textScale.letterSpacing(1.0.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(level.nameId),
            color = pillForeground,
            fontSize = textScale.size(10.sp),
            lineHeight = textScale.size(12.sp),
            fontWeight = FontWeight.Black,
            letterSpacing = textScale.letterSpacing(0.7.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .rewindCardBackground(pillBackground, RoundedCornerShape(100.dp))
                .padding(horizontal = 11.dp, vertical = 6.dp)
                .rewindMarqueeOnly()
        )
        Spacer(Modifier.height(5.dp))
        RewindAdaptiveTitle(
            text = stringResource(level.taglineId),
            color = foreground.copy(alpha = 0.72f),
            fontSize = textScale.size(10.sp),
            lineHeight = textScale.size(13.sp),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun RewindEmptyState(
    title: String,
    body: String,
    foreground: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val textScale = LocalRewindTextScale.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RewindKicker(stringResource(R.string.rewind_empty_state_kicker), accent)
        RewindAdaptiveTitle(
            text = title,
            color = foreground,
            fontSize = textScale.size(34.sp),
            lineHeight = textScale.size(34.sp),
            letterSpacing = textScale.letterSpacing((-1.7).sp),
            fontWeight = FontWeight.Black
        )
        RewindAdaptiveTitle(
            text = body,
            color = foreground.copy(alpha = 0.66f),
            fontSize = textScale.size(13.sp),
            lineHeight = textScale.size(18.sp)
        )
    }
}

internal data class ListenerBadge(
    val titleId: Int,
    val subtitleId: Int,
    val index: Int,
    val tier: Int
)

/**
 * NZik listening index. This is deliberately NOT called a percentile because the app only
 * has the listener's local history, not a global population. The reference point is calibrated
 * so ~45k minutes / 14k plays / 201 days / 5.6k unique songs lands around index 70 on a FULL
 * YEAR, leaving several genuinely harder tiers above it.
 *
 * The four annual thresholds are scaled by the period's length
 * ([RewindData.daysInPeriod] / 365.25), so a month is judged against a month's worth of the
 * reference instead of a whole year's (spec GH-275, patch "Listener badge not scaled").
 * A period with zero days (empty all-time data) scores every ratio 0 — no 0/0 NaN.
 */
internal fun calculateListenerBadge(data: RewindData): ListenerBadge {
    val scale = data.daysInPeriod.toDouble() / 365.25
    val minuteRatio = if (scale > 0.0) (data.stats.totalMinutes.toDouble() / (45_000.0 * scale)).coerceIn(0.0, 2.4) else 0.0
    val playRatio = if (scale > 0.0) (data.stats.totalPlays.toDouble() / (14_000.0 * scale)).coerceIn(0.0, 2.4) else 0.0
    val dayRatio = if (scale > 0.0) (data.daysWithMusic.toDouble() / (201.0 * scale)).coerceIn(0.0, 1.82) else 0.0
    val uniqueRatio = if (scale > 0.0) (data.totalUniqueSongs.toDouble() / (5_612.0 * scale)).coerceIn(0.0, 2.4) else 0.0
    val intensity = (
        minuteRatio * 0.38 +
            playRatio * 0.25 +
            dayRatio * 0.22 +
            uniqueRatio * 0.15
        )
    val index = (intensity * 70.0).roundToInt().coerceIn(0, 170)
    return when {
        index < 15 -> ListenerBadge(R.string.rewind_badge_tier_0_name, R.string.rewind_badge_tier_0_sub, index, 0)
        index < 30 -> ListenerBadge(R.string.rewind_badge_tier_1_name, R.string.rewind_badge_tier_1_sub, index, 1)
        index < 45 -> ListenerBadge(R.string.rewind_badge_tier_2_name, R.string.rewind_badge_tier_2_sub, index, 2)
        index < 60 -> ListenerBadge(R.string.rewind_badge_tier_3_name, R.string.rewind_badge_tier_3_sub, index, 3)
        index < 75 -> ListenerBadge(R.string.rewind_badge_tier_4_name, R.string.rewind_badge_tier_4_sub, index, 4)
        index < 90 -> ListenerBadge(R.string.rewind_badge_tier_5_name, R.string.rewind_badge_tier_5_sub, index, 5)
        index < 110 -> ListenerBadge(R.string.rewind_badge_tier_6_name, R.string.rewind_badge_tier_6_sub, index, 6)
        index < 130 -> ListenerBadge(R.string.rewind_badge_tier_7_name, R.string.rewind_badge_tier_7_sub, index, 7)
        else -> ListenerBadge(R.string.rewind_badge_tier_8_name, R.string.rewind_badge_tier_8_sub, index, 8)
    }
}

internal fun formatRewindNumber(value: Long): String =
    NumberFormat.getIntegerInstance().format(value.coerceAtLeast(0))

internal fun formatRewindMinutes(minutes: Long): String =
    formatRewindNumber(minutes.coerceAtLeast(0))

internal fun firstNonBlank(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() && it != "null" }.orEmpty()

@Composable
internal fun formatHourLabel(hour: String?): String {
    val raw = hour?.substringBefore(':')?.toIntOrNull()?.takeIf { it in 0..23 } ?: return "—"
    val locale = LocalConfiguration.current.locales[0]
    val pattern = DateFormat.getBestDateTimePattern(locale, "ha")
    return SimpleDateFormat(pattern, locale).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(raw * 3_600_000L))
}

@Composable
internal fun rewindCount(@PluralsRes id: Int, count: Long): String =
    pluralStringResource(id, count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), formatRewindNumber(count))

@Composable
internal fun rewindCount(@PluralsRes id: Int, count: Long, duration: String): String =
    pluralStringResource(id, count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), duration, formatRewindNumber(count))
