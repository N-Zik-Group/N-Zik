package app.n_zik.android.components.ui.screens.rewind.slides

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.pow

/**
 * Calibration width of the deck design, in dp: the width at which every size in the cards
 * was calibrated (font scale 1.0). It is a large-phone reference — most phones (≤393dp)
 * render slightly below 1.0×. Below it, sizes scale down proportionally; above it, they
 * stay put (no upscaling on large screens/tablets).
 */
internal const val REWIND_TEXT_REFERENCE_WIDTH_DP = 400

/**
 * How much of the system font-scale the width factor absorbs, as an exponent.
 *
 * The rendered size is `base * factor * fontScale` (Compose multiplies `sp` by the font
 * scale). With the factor carrying `1 / fontScale^0.5`, the rendered size grows with
 * `fontScale^0.5` — the user's font-size choice stays effective (it is never cancelled,
 * which would be anti-accessibility), but it no longer doubles the width on doubled
 * fonts, which is what overflowed small screens.
 */
internal const val REWIND_TEXT_FONT_SCALE_ABSORB = 0.5f

/**
 * Size tier of a deck base size — the per-tier minimum scale factor (legibility floor)
 * is chosen from the tier, inferred from the base size itself.
 *
 * Inventory of the deck (7sp-126sp): micro labels 7-9, labels 10-14, body 15-22,
 * stats 24-31, titles 32-47, heroes 48-126.
 */
internal enum class RewindTextTier(val minFactor: Float) {
    /** 7-9sp: micro labels (kickers, tiny captions). May shrink the most. */
    CAPTION(0.55f),

    /** 10-14sp: labels and row text. */
    LABEL(0.60f),

    /** 15-22sp: body text and sub-lines. */
    BODY(0.60f),

    /** 24-31sp: stat numbers. */
    STAT(0.60f),

    /** 32-45sp: slide titles. */
    TITLE(0.60f),

    /** 48sp and up: hero display text (display name, N°1, big numbers). */
    HERO(0.55f);

    internal companion object {
        fun of(baseSp: Float): RewindTextTier = when {
            baseSp < 10f -> CAPTION
            baseSp < 15f -> LABEL
            baseSp < 24f -> BODY
            baseSp < 32f -> STAT
            baseSp < 48f -> TITLE
            else -> HERO
        }
    }
}

/**
 * Shared text sizing policy of the Rewind deck (spec problem-solution-2026-09-28-rewind,
 * solution S-A): every deck text size passes through [size] instead of a raw `sp` literal.
 *
 * Calibration contract (pinned by [RewindTextScaleTest]):
 * - I1 — at the reference (400dp wide, font scale 1.0) [size] returns the base size
 *   EXACTLY, so standard-screen renders are unchanged by construction;
 * - I2 — below the reference the factor is non-increasing in the width and clamped to
 *   `[tier.minFactor, 1.0]`; above the reference it is flat (no upscaling);
 * - I3 — at a fixed width the rendered size is non-decreasing in the font scale (the
 *   user's accessibility font choice stays effective — never cancelled);
 * - I4 — overflow guard: the factor is `width / (reference * fontScale^ABSORB)`, so even
 *   at a doubled font scale the rendered size stays within the screen's width budget.
 *   Font scales below 1.0 are clamped to the 1.0 behavior (Android's user font-scale
 *   floor), so the contract is uniform for every font scale.
 *
 * The sizing only keeps proportions in budget — it does not by itself prevent a word
 * from overflowing its box. That is the fitting discipline of the slides (single-line
 * display texts are capped with ellipsis or a marquee honoring the "disable scrolling
 * text" setting; multiline texts are capped with `maxLines` + `Ellipsis`), see
 * `RewindStoryComponents`.
 *
 * @param screenWidthDp available screen width of the device, dp (not physical pixels)
 * @param fontScale system font-scale (1.0 = default; from [androidx.compose.ui.unit.Density])
 */
internal class RewindTextScale(
    val screenWidthDp: Int,
    val fontScale: Float,
) {

    /**
     * The effective size of a deck base [base] on this screen: width-scaled,
     * font-scale-absorbing, clamped to the tier's legibility floor.
     */
    fun size(base: TextUnit): TextUnit = size(base.value).sp

    /**
     * The layout factor for scaling non-text dimensions (hero photo areas) in step with the
     * deck's text: width-scaled, font-scale-absorbing, clamped to the hero floor. Exactly
     * 1.0 at the calibration reference (I1), so standard-screen renders are unchanged by
     * construction — the photo only shrinks where the text shrinks.
     */
    val layoutFactor: Float
        get() = (screenWidthDp.toFloat() /
            (REWIND_TEXT_REFERENCE_WIDTH_DP.toFloat() * fontScale.coerceAtLeast(1f).pow(REWIND_TEXT_FONT_SCALE_ABSORB)))
            .coerceIn(RewindTextTier.HERO.minFactor, 1f)

    /** The effective size of a deck base size in sp (the raw [size] math, also used for letterSpacing). */
    fun size(baseSp: Float): Float {
        val reference = REWIND_TEXT_REFERENCE_WIDTH_DP.toFloat()
        // Width below the reference shrinks the text proportionally; width above it
        // does nothing (factor clamped to 1.0). The font-scale part is absorbed with
        // the ABSORB exponent so the rendered size grows with fontScale^0.5, not fontScale.
        // Scales below 1.0 are clamped to 1.0: the user font-scale floor on Android is
        // 1.0, and clamping keeps the I4 width-budget contract uniform for every scale.
        val effectiveFontScale = fontScale.coerceAtLeast(1f)
        val raw = screenWidthDp.toFloat() / (reference * effectiveFontScale.pow(REWIND_TEXT_FONT_SCALE_ABSORB))
        val factor = raw.coerceIn(RewindTextTier.of(baseSp).minFactor, 1f)
        return baseSp * factor
    }

    /** The effective letterSpacing for a deck base value (same scaling as [size]). */
    fun letterSpacing(base: TextUnit): TextUnit = size(base.value).sp
}

/**
 * Live deck text scale, provided by [RewindScreen] over the pager pages (same pattern as
 * [LocalRewindActive] / [LocalRewindShaderWarm]). The default is the calibration
 * reference itself, so an unprovided composition (tests, previews) renders exactly the
 * design sizes — deliberately, so a missing provider degrades to the current look rather
 * than to a broken layout.
 */
internal val LocalRewindTextScale = staticCompositionLocalOf {
    RewindTextScale(REWIND_TEXT_REFERENCE_WIDTH_DP, 1f)
}

/**
 * The [RewindTextScale] for the current screen: width from [LocalConfiguration], font
 * scale from [LocalDensity] (remembered, so cards stay cheap on recomposition).
 */
@Composable
internal fun rememberRewindScale(): RewindTextScale {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    return remember(configuration.screenWidthDp, density.fontScale) {
        RewindTextScale(configuration.screenWidthDp, density.fontScale)
    }
}
