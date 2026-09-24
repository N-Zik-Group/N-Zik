package app.n_zik.android.components.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.bold
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.typography
import app.n_zik.android.updater.services.Updater

/**
 * Reference label for the header badges: the rendered width of this title at the badge
 * style caps the width of EVERY header badge (version + debug) — no badge may be wider
 * than the "DEBUG" badge; longer labels scroll in a marquee instead.
 */
internal const val HEADER_BADGE_REF_TITLE = "DEBUG"

private const val HEADER_BADGE_H_PADDING = 4
private const val HEADER_BADGE_V_PADDING = 1

/**
 * Finite marquee for the header badges: a few full passes, then the scroll stops and the
 * label settles back at its start position — the badges must not spin forever in the
 * action bar (the platform default is [MarqueeDefaults]-equivalent: 3 iterations).
 */
private const val HEADER_BADGE_MARQUEE_ITERATIONS = 3

/**
 * The badge style: 14sp bold — the "Rescue Center" burger item size (labelLarge),
 * which is the maximum text size for header badges.
 */
@Composable
private fun headerBadgeStyle(): TextStyle = typography().xs.bold

/**
 * Rendered width of [reference] at [style] — a hard width cap for labels: any wider
 * label is clamped to it and scrolls in a marquee. The header badges use the "DEBUG"
 * badge title as reference; the burger "Enable debug logs" item uses the "Rescue
 * Center" menu label. Measuring at the same style keeps the cap exact for whatever
 * font is loaded.
 */
@Composable
internal fun clampedLabelMaxWidth(reference: String, style: TextStyle): Dp {
    val textMeasurer = rememberTextMeasurer()
    return with(LocalDensity.current) {
        textMeasurer.measure(reference, style = style).size.width.toDp()
    }
}

/**
 * Shared container for the header badges (version + debug).
 *
 * The label is one line, capped at the "DEBUG" badge width: a shorter label keeps a
 * compact badge, a longer one scrolls in a marquee (honoring the global
 * "disable scrolling text" setting, which falls back to ellipsis).
 *
 * @param text badge label
 * @param color badge tint (background at 0.2 alpha + text)
 * @param onToggle tap handler; null for non-interactive badges
 */
@Composable
internal fun HeaderBadgeBox(
    text: String,
    color: Color,
    onToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isScrollingTextDisabled by rememberPreference(disableScrollingTextKey, false)
    val style = headerBadgeStyle().copy(color = color)
    val maxWidth = clampedLabelMaxWidth(HEADER_BADGE_REF_TITLE, style)

    Box(
        modifier = modifier
            .background(
                color = color.copy(alpha = 0.2f),
                shape = uiRoundnessShape()
            )
            .clip(uiRoundnessShape())
            // Only the interactive debug badge gets the click modifier: even a
            // disabled clickable would enforce the 48dp minimum touch target and
            // inflate the badge box (see short-badge test).
            .conditional(onToggle != null) {
                clickable(onClick = { onToggle?.invoke() })
            }
            .padding(
                horizontal = HEADER_BADGE_H_PADDING.dp,
                vertical = HEADER_BADGE_V_PADDING.dp
            )
    ) {
        BasicText(
            text = text,
            style = style,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = Modifier
                .widthIn(max = maxWidth)
                .conditional(!isScrollingTextDisabled) {
                    // Finite marquee: scrolls a few full passes, then stops on the start
                    // position (BasicMarquee snaps the offset back to zero when done).
                    basicMarquee(iterations = HEADER_BADGE_MARQUEE_ITERATIONS)
                }
        )
    }
}

/**
 * Version badge of the app header (FOSS / BETA / MINIFIED / DEV + 32-bit variants).
 *
 * Same container and width cap as the debug badge: a label longer than "DEBUG"
 * (e.g. "MINIFIED 32") scrolls in a marquee instead of widening the header.
 */
@Composable
fun HeaderVersionBadge(
    modifier: Modifier = Modifier
) {
    val versionSuffix = Updater.extractVersionSuffix(BuildConfig.VERSION_NAME)
    val isFoss = BuildConfig.BUILD_TYPE.equals("foss", ignoreCase = true)

    if (!isFoss && (versionSuffix.isEmpty() || versionSuffix == "f")) return

    val badgeText = if (isFoss) {
        "FOSS"
    } else {
        when (versionSuffix) {
            "b" -> stringResource(R.string.beta_title)
            "m" -> stringResource(R.string.minified_title)
            "b32" -> "${stringResource(R.string.beta_title)} 32"
            "m32" -> "${stringResource(R.string.minified_title)} 32"
            "f32" -> "${stringResource(R.string.full_title)} 32"
            "debug" -> stringResource(R.string.debug_title)
            "dev" -> stringResource(R.string.dev_title)
            "dev32" -> "${stringResource(R.string.dev_title)} 32"
            else -> versionSuffix.uppercase()
        }
    }

    HeaderBadgeBox(
        text = badgeText,
        color = colorPalette().accent,
        modifier = modifier
    )
}
