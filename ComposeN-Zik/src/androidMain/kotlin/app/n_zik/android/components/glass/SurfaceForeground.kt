package app.n_zik.android.components.glass

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.components.theme.withColor
import app.n_zik.android.components.theme.paletteDarkness
import app.n_zik.android.ui.styling.LocalAppearance
import kotlin.math.abs

/** Estimated scene or plate color at the origin of an overlay. */
internal val LocalSurfaceBackdropColor = compositionLocalOf<Color?> { null }
internal val LocalNeutralForegroundOverride = compositionLocalOf<Color?> { null }
internal val LocalNeutralForegroundAmount = compositionLocalOf { 1f }
internal val LocalNeutralForegroundSurface = compositionLocalOf<Color?> { null }
internal val LocalNeutralForegroundTransition = compositionLocalOf<State<NeutralForegroundTransition>?> { null }
internal val LocalForegroundSourcePalette = compositionLocalOf<ColorPalette?> { null }
private val LocalForegroundSurface = compositionLocalOf<Color?> { null }
private val LocalForegroundScrim = compositionLocalOf { Color.Transparent }
internal val LocalGlassInkDarkness = compositionLocalOf<Float?> { null }
internal val LocalPlayerForegroundFrame = compositionLocalOf<PlayerForegroundFrame?> { null }
internal val LocalPlayerFollowsPaletteInk = compositionLocalOf { false }
internal val LocalPlayerGlyphCorrection = compositionLocalOf<State<Float>?> { null }

@Immutable
internal data class PlayerForegroundFrame(val generation: Int, val progress: Float, val changesPolarity: Boolean = false)

internal const val TextContrast = 4.5f
internal const val IconContrast = 3f
// Preserve selected neutral ink unless it becomes indistinct.
private const val NeutralRetentionContrast = 2f

internal fun contrastAgainst(foreground: Color, surface: Color): Float {
    val ink = foreground.compositeOver(surface).luminance()
    val fill = surface.luminance()
    return (maxOf(ink, fill) + 0.05f) / (minOf(ink, fill) + 0.05f)
}

internal fun neutralForeground(surface: Color): Color =
    if (contrastAgainst(Color.Black, surface) >= contrastAgainst(Color.White, surface)) Color.Black else Color.White

/** Retains accent hue and saturation while adding only the lightness needed for visibility. */
internal fun contrastAdjustedAccent(accent: Color, background: Color, minimum: Float, endpoint: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(accent.toArgb(), hsl)
    val originalLightness = hsl[2]
    var low = 0f
    var high = 1f
    fun candidate(amount: Float): Color {
        hsl[2] = originalLightness + (endpoint - originalLightness) * amount
        return Color(ColorUtils.HSLToColor(hsl)).copy(alpha = accent.alpha)
    }
    repeat(16) {
        val middle = (low + high) / 2f
        if (contrastAgainst(candidate(middle), background) >= minimum) high = middle else low = middle
    }
    return candidate(high)
}

/** Theme grays have near-zero saturation; a pale or very dark chromatic color is still colored. */
internal fun isNeutralForeground(color: Color): Boolean {
    if (!color.isSpecified) return false
    val maximum = maxOf(color.red, color.green, color.blue)
    val minimum = minOf(color.red, color.green, color.blue)
    val chroma = maximum - minimum
    if (chroma == 0f) return true
    val denominator = 1f - kotlin.math.abs(maximum + minimum - 1f)
    return denominator > 0f && chroma / denominator <= .025f
}

internal fun readableForeground(original: Color, surface: Color, minimum: Float): Color {
    if (!original.isSpecified) return neutralForeground(surface)
    if (!isNeutralForeground(original) || original.alpha == 0f ||
        contrastAgainst(original, surface) >= minOf(minimum, NeutralRetentionContrast)) return original
    return neutralForeground(surface).copy(alpha = original.alpha)
}

private fun isGentleGlyph(original: Color, enabled: Boolean): Boolean {
    val tone = (original.red + original.green + original.blue) / 3f
    return !enabled || tone in .18f.. .82f || original.alpha < .7f
}

/** Deliberately gray or disabled glyphs keep their subdued tone and original opacity. */
internal fun readableGlyphForeground(
    original: Color,
    surface: Color,
    minimum: Float = IconContrast,
    enabled: Boolean = true,
): Color {
    if (!original.isSpecified) return neutralForeground(surface)
    if (!isNeutralForeground(original) || original.alpha == 0f) return original
    if (!isGentleGlyph(original, enabled)) return readableForeground(original, surface, minimum)
    if (contrastAgainst(original, surface) >= minOf(minimum, NeutralRetentionContrast)) return original
    val direction = if (neutralForeground(surface) == Color.White) .08f else -.08f
    return Color((original.red + direction).coerceIn(0f, 1f),
        (original.green + direction).coerceIn(0f, 1f),
        (original.blue + direction).coerceIn(0f, 1f), original.alpha)
}

private fun smoothGlassStep(fraction: Float): Float {
    val x = fraction.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** Follow the visible plate continuously when neutral ink needs to change polarity. */
internal fun glassPaneForeground(
    original: Color,
    surface: Color,
    minimum: Float,
    darkness: Float,
    glyph: Boolean = false,
    enabled: Boolean = true,
): Color {
    if (original.isSpecified && (!isNeutralForeground(original) || original.alpha == 0f)) return original
    // Black and white have equal contrast near a relative luminance of 0.18.
    // Blend across that range instead of switching either anchor in one frame.
    val darkInk = smoothGlassStep((surface.luminance() - .12f) / .12f)
    if (!original.isSpecified) return lerp(Color.White, Color.Black, darkInk)
    val deficit = minOf(minimum, NeutralRetentionContrast) - contrastAgainst(original, surface)
    if (deficit <= 0f) return original
    // In a light/dark fade the palette already interpolates its ink. Ease out
    // corrective ink through the middle so it cannot reverse that transition.
    val stableInk = 1f - smoothGlassStep(darkness / .25f) * smoothGlassStep((1f - darkness) / .25f)
    val correction = smoothGlassStep(deficit / .18f) * stableInk
    if (glyph && isGentleGlyph(original, enabled)) {
        val shift = (.08f - .16f * darkInk) * correction
        return Color((original.red + shift).coerceIn(0f, 1f),
            (original.green + shift).coerceIn(0f, 1f),
            (original.blue + shift).coerceIn(0f, 1f), original.alpha)
    }
    return lerp(original, lerp(Color.White, Color.Black, darkInk).copy(alpha = original.alpha), correction)
}

@Composable
internal fun surfaceBackdropColor(): Color =
    LocalSurfaceBackdropColor.current ?: LocalAppearance.current.colorPalette.background0.copy(alpha = 1f)

internal fun effectiveSurfaceColor(
    backdrop: Color,
    fill: Color,
    scrim: Color = Color.Transparent,
): Color {
    val underlay = scrim.compositeOver(backdrop)
    return if (fill.isSpecified) fill.compositeOver(underlay) else underlay
}

@Composable
internal fun glassFilmColor(
    settings: GlassSettings,
    role: Color?,
    menu: Boolean = true,
    progress: Float = 1f,
): Color {
    val tint = glassFilmTint(settings, role, menu, progress)
    return tint.copy(alpha = tint.alpha * glassFilmOpacity(settings.film, menu, progress))
}

@Composable
internal fun SurfaceForegroundContent(
    surface: Color?,
    scrim: Color = Color.Transparent,
    preferMeasuredText: Boolean = false,
    glassInkDarkness: Float? = LocalGlassInkDarkness.current,
    content: @Composable () -> Unit,
) {
    val appearance = LocalAppearance.current
    val source = LocalForegroundSourcePalette.current ?: appearance.colorPalette
    val commonNeutral = LocalNeutralForegroundOverride.current
    val amount = LocalNeutralForegroundAmount.current
    val commonSurface = LocalNeutralForegroundSurface.current ?: surface
    val transition = LocalNeutralForegroundTransition.current?.value
    val playerFrame = LocalPlayerForegroundFrame.current
    val glyphCorrection = LocalPlayerGlyphCorrection.current
    val followsPalette = followsPlayerPaletteInk(playerFrame)
    val textFrame = remember { PlayerMeasuredInkFrame() }
    val secondaryFrame = remember { PlayerMeasuredInkFrame() }
    val glyphFrame = remember { PlayerMeasuredInkFrame() }
    val adapt = surface != null || commonNeutral != null || transition != null
    val inheritedContent = LocalContentColor.current
    fun endpoint(original: Color, minimum: Float, glyph: Boolean = false): Color =
        if (preferMeasuredText && !glyph && surface != null) measuredPlayerForeground(original, surface, minimum)
        else if (transition != null) transition.resolve(original, minimum, localSurface = surface.takeIf { playerFrame != null }, glyph = glyph)
        else if (glassInkDarkness != null && surface != null) glassPaneForeground(original, surface, minimum, glassInkDarkness, glyph)
        else if (glyph && surface != null && isNeutralForeground(original) && isGentleGlyph(original, true))
            readableGlyphForeground(original, commonSurface ?: surface, minimum)
        else if (commonNeutral != null && isNeutralForeground(original) && original.alpha > 0f) {
            val target = if (commonSurface != null &&
                contrastAgainst(original, commonSurface) >= minOf(minimum, NeutralRetentionContrast)) original
                else commonNeutral.copy(alpha = original.alpha)
            lerp(original, target, amount)
        } else if (surface != null) readableForeground(original, surface, minimum) else original
    fun ink(original: Color, minimum: Float, frame: PlayerMeasuredInkFrame, glyph: Boolean = false): Color {
        val native = followsPalette && isNeutralForeground(original)
        val target = if (native) original else endpoint(original, minimum, glyph)
        val contrast = if (glyph && original.isSpecified && isGentleGlyph(original, true))
            NeutralRetentionContrast else minimum
        return frame.resolveEndpoint(original, target, playerFrame, surface ?: transition?.to?.surface, contrast, native,
            glyphCorrection.takeIf { glyph })
    }
    val palette = if (adapt) appearance.colorPalette.copy(
        text = ink(source.text, TextContrast, frame = textFrame),
        textSecondary = ink(source.textSecondary, TextContrast, frame = secondaryFrame),
        textDisabled = source.textDisabled,
        iconButtonPlayer = ink(source.iconButtonPlayer, IconContrast, glyphFrame, glyph = true),
    ) else appearance.colorPalette
    // Keep the same composition tree before the first sample and throughout sheet transitions.
    CompositionLocalProvider(
        LocalAppearance provides if (adapt) appearance.copy(colorPalette = palette,
            typography = appearance.typography.withColor(palette.text)) else appearance,
        LocalContentColor provides if (adapt && (!inheritedContent.isSpecified || isNeutralForeground(inheritedContent)))
            palette.text else inheritedContent,
        LocalForegroundSourcePalette provides source,
        LocalSurfaceBackdropColor provides if (adapt) surface else LocalSurfaceBackdropColor.current,
        LocalForegroundSurface provides if (adapt) surface else LocalForegroundSurface.current,
        LocalForegroundScrim provides if (adapt) scrim else LocalForegroundScrim.current,
        LocalGlassInkDarkness provides glassInkDarkness,
        content = content,
    )
}

internal data class NeutralForegroundDecision(val surface: Color?, val anchor: Color?)
internal data class WeightedNeutralDecision(val decision: NeutralForegroundDecision, val weight: Float)

internal data class NeutralForegroundEndpointKey(
    val decision: NeutralForegroundDecision,
    val ink: Color,
    val minimum: Float,
    val plate: Color?,
    val localSurface: Color?,
    val glyph: Boolean,
    val enabled: Boolean,
    val measuredText: Boolean = false,
)

internal class PlayerForegroundMemo(private val capacity: Int = 96) {
    private val endpoints = LinkedHashMap<NeutralForegroundEndpointKey, Color>(16, .75f, true)

    fun resolve(key: NeutralForegroundEndpointKey, calculate: () -> Color): Color = synchronized(endpoints) {
        endpoints[key] ?: calculate().also { result ->
            endpoints[key] = result
            if (endpoints.size > capacity) endpoints.remove(endpoints.keys.first())
        }
    }

    fun clear() = synchronized(endpoints) { endpoints.clear() }
}

private val playerForegroundMemo = PlayerForegroundMemo()

internal fun invalidatePlayerForegroundMemo() = playerForegroundMemo.clear()

internal fun measuredPlayerTextForeground(original: Color, surface: Color, minimum: Float = TextContrast): Color =
    measuredPlayerForeground(original, surface, minimum)

/** Reuses the shared clock, retaining the actual ink when that clock is retargeted. */
internal class PlayerMeasuredInkFrame {
    private var generation: Int? = null
    private var origin = Color.Unspecified
    private var displayed = Color.Unspecified
    private var displayedSurface: Color? = null
    private var lastEndpoint = Color.Unspecified
    private var lastCorrected = false
    private var startProgress = 0f
    private var blendFromCorrection = false
    private var correctionOwner: State<Float>? = null
    private var correctionOrigin = Color.Unspecified

    fun resolveEndpoint(
        original: Color,
        endpoint: Color,
        frame: PlayerForegroundFrame?,
        surface: Color? = null,
        minimum: Float = IconContrast,
        followPaletteInk: Boolean = false,
        glyphCorrection: State<Float>? = null,
    ): Color {
        val native = followPaletteInk && isNeutralForeground(original)
        val corrected = original != endpoint
        val correctionProgress = glyphCorrection?.value ?: 1f
        // The final background can only be sampled after the palette has settled.
        if (!native && (frame == null || frame.progress >= 1f) && correctionProgress < 1f &&
            (corrected || lastCorrected || correctionOwner === glyphCorrection) &&
            displayed.isSpecified && isNeutralForeground(original) && isNeutralForeground(endpoint) && original.alpha > 0f) {
            if (correctionOwner !== glyphCorrection) {
                correctionOwner = glyphCorrection
                correctionOrigin = displayed
            }
            val color = if (correctionProgress == 0f || correctionOrigin == endpoint) correctionOrigin
                else lerp(correctionOrigin, endpoint, correctionProgress)
            displayed = color.copy(alpha = endpoint.alpha)
            displayedSurface = surface
            lastEndpoint = endpoint
            lastCorrected = original != displayed
            return displayed
        }
        correctionOwner = null
        if (native || frame == null || frame.progress >= 1f || !displayed.isSpecified || !isNeutralForeground(original) ||
            !endpoint.isSpecified || original.alpha == 0f) {
            generation = null
            displayed = if (native) original else endpoint
            displayedSurface = surface
            lastEndpoint = displayed
            lastCorrected = !native && corrected
            return displayed
        }
        val newFade = generation != frame.generation
        val endpointJumped = lastEndpoint.isSpecified && (corrected || lastCorrected) &&
            abs(endpoint.red - lastEndpoint.red) >= .25f
        if (newFade || endpointJumped) {
            origin = displayed.takeIf { it.isSpecified } ?: original
            generation = frame.generation
            startProgress = if (newFade) 0f else frame.progress
            blendFromCorrection = lastCorrected || endpointJumped
        }
        lastEndpoint = endpoint
        lastCorrected = corrected
        // Rebase a changed sample on the remaining shared progress. Native roles already fade with the palette.
        val progress = ((frame.progress - startProgress) / (1f - startProgress)).coerceIn(0f, 1f)
        val candidate = when {
            !corrected && !blendFromCorrection -> endpoint
            progress == 0f -> origin.copy(alpha = endpoint.alpha)
            else -> lerp(origin, endpoint, progress).copy(alpha = endpoint.alpha)
        }
        val previousSurface = displayedSurface
        val darkInk = previousSurface?.let { displayed.luminance() < it.luminance() }
        val readable = if (surface == null) candidate else {
            val correctedInk = readableCandidate(candidate, endpoint, surface, minimum, darkInk)
            if (previousSurface == null || darkInk == null) correctedInk
            else preserveInkDirection(correctedInk, endpoint.alpha, previousSurface, surface, minimum, darkInk)
        }
        if (surface != null && darkInk != null && readable != candidate &&
            (readable.luminance() < surface.luminance()) != darkInk && contrastAgainst(readable, surface) >= minimum) {
            // Continue from the forced polarity change if the next sample relaxes the contrast constraint.
            origin = readable
            startProgress = frame.progress
            blendFromCorrection = true
        }
        displayed = readable
        displayedSurface = surface
        return readable
    }

    private fun readableCandidate(candidate: Color, endpoint: Color, surface: Color, minimum: Float,
        darkInk: Boolean?): Color {
        if (contrastAgainst(candidate, surface) >= minimum) return candidate
        if (contrastAgainst(endpoint, surface) < minimum) return endpoint
        val previousAnchor = darkInk?.let {
            val anchor = if (it) Color.Black else Color.White
            anchor.copy(alpha = endpoint.alpha)
        }
        // Keep the current polarity while it can meet the contrast ratio.
        val readable = previousAnchor?.takeIf { contrastAgainst(it, surface) >= minimum } ?: endpoint
        return closestReadableShade(candidate, readable, surface, minimum)
    }

    private fun preserveInkDirection(candidate: Color, alpha: Float, previousSurface: Color, surface: Color,
        minimum: Float, darkInk: Boolean): Color {
        val surfaceChange = surface.luminance() - previousSurface.luminance()
        val inkChange = candidate.luminance() - displayed.luminance()
        val reversing = if (darkInk) surfaceChange < 0f && inkChange < 0f else surfaceChange > 0f && inkChange > 0f
        if (!reversing) return candidate
        // Hold a readable shade through an endpoint dip instead of briefly moving away from the next polarity.
        val previous = displayed.copy(alpha = alpha)
        if (contrastAgainst(previous, surface) >= minimum) return previous
        val nextPolarity = (if (darkInk) Color.White else Color.Black).copy(alpha = alpha)
        return if (contrastAgainst(nextPolarity, surface) >= minimum) {
            closestReadableShade(previous, nextPolarity, surface, minimum)
        } else candidate
    }

    private fun closestReadableShade(candidate: Color, anchor: Color, surface: Color, minimum: Float): Color {
        var readable = anchor
        var unreadable = candidate
        repeat(7) {
            val middle = lerp(unreadable, readable, .5f)
            if (contrastAgainst(middle, surface) >= minimum) readable = middle else unreadable = middle
        }
        return readable
    }
}

internal fun measuredPlayerForeground(original: Color, surface: Color, minimum: Float): Color {
    if (!original.isSpecified) return neutralForeground(surface)
    if (!isNeutralForeground(original) || original.alpha == 0f) return original
    val key = NeutralForegroundEndpointKey(NeutralForegroundDecision(surface, null), original, minimum,
        plate = null, localSurface = null, glyph = false, enabled = true, measuredText = true)
    return playerForegroundMemo.resolve(key) {
        if (contrastAgainst(original, surface) >= minimum) original
        else neutralForeground(surface).copy(alpha = original.alpha)
    }
}

@Composable
internal fun playerControlGlyphColor(
    original: Color,
    plate: Color = Color.Transparent,
    minimum: Float = IconContrast,
    enabled: Boolean = true,
    measuredBackdrop: Color? = null,
): Color {
    val measuredFrame = remember { PlayerMeasuredInkFrame() }
    val backdrop = measuredBackdrop ?: LocalForegroundSurface.current
    val frame = LocalPlayerForegroundFrame.current
    val visiblePlate = backdrop?.let { effectiveSurfaceColor(it, plate) }.takeIf { enabled }
    if (enabled && followsPlayerPaletteInk(frame) && isNeutralForeground(original)) {
        val native = unadjustedPlayerInk(original)
        return measuredFrame.resolveEndpoint(native, native, frame, visiblePlate, minimum, followPaletteInk = true)
    }
    val color = resolveSurfaceGlyphColor(original, plate, minimum, enabled)
    val endpoint = if (visiblePlate != null) {
        if (measuredBackdrop != null) measuredPlayGlyphForeground(color, visiblePlate, minimum)
        else measuredPlayerForeground(color, visiblePlate, minimum)
    } else color
    val correction = LocalPlayerGlyphCorrection.current.takeIf { enabled }
    return measuredFrame.resolveEndpoint(original, endpoint, frame, visiblePlate, minimum, glyphCorrection = correction)
}

internal fun measuredPlayGlyphForeground(color: Color, plate: Color, minimum: Float): Color {
    if (!color.isSpecified || color.alpha == 0f || contrastAgainst(color, plate) >= minimum) return color
    val anchor = neutralForeground(plate)
    if (isNeutralForeground(color)) return anchor.copy(alpha = color.alpha)
    return contrastAdjustedAccent(color, plate, minimum + .05f, if (anchor == Color.White) 1f else 0f)
}

/** Every role resolves both endpoints from its source ink and uses this single shared clock. */
internal data class NeutralForegroundTransition(
    val from: List<WeightedNeutralDecision>,
    val to: NeutralForegroundDecision,
    val progress: Float,
    private val memo: PlayerForegroundMemo? = null,
) {
    fun resolve(original: Color, minimum: Float = IconContrast, plate: Color? = null,
        localSurface: Color? = null, glyph: Boolean = false, enabled: Boolean = true): Color {
        if (!original.isSpecified || !isNeutralForeground(original) || original.alpha == 0f) return original
        fun resolveEndpoint(decision: NeutralForegroundDecision): Color {
            val background = localSurface ?: decision.surface ?: return original
            val actual = if (plate != null && plate.alpha > 0f) effectiveSurfaceColor(background, plate) else background
            if (glyph && isGentleGlyph(original, enabled))
                return readableGlyphForeground(original, actual, minimum, enabled)
            if (plate != null && plate.alpha > 0f)
                return readableForeground(original, actual, minimum)
            if (contrastAgainst(original, background) >= minimum) return original
            val anchor = if (localSurface != null) neutralForeground(background) else decision.anchor
            return anchor?.copy(alpha = original.alpha) ?: readableForeground(original, background, minimum)
        }
        fun endpoint(decision: NeutralForegroundDecision): Color {
            if (decision.surface == null || memo == null) return resolveEndpoint(decision)
            val key = NeutralForegroundEndpointKey(decision, original, minimum, plate, localSurface, glyph, enabled)
            return memo.resolve(key) { resolveEndpoint(decision) }
        }
        val end = endpoint(to)
        if (progress >= 1f) return end
        var start = original
        var accumulated = 0f
        for ((decision, weight) in from) if (weight > 0f) {
            start = if (accumulated == 0f) endpoint(decision)
                else lerp(start, endpoint(decision), weight / (accumulated + weight))
            accumulated += weight
        }
        return when {
            start == end -> end
            progress <= 0f -> start
            else -> lerp(start, end, progress)
        }
    }

    internal fun frozenBlend(): List<WeightedNeutralDecision> =
        (from.map { it.copy(weight = it.weight * (1f - progress)) } + WeightedNeutralDecision(to, progress))
            .filter { it.weight > 0.00001f }
}

@Composable
internal fun rememberNeutralForegroundForSurface(
    surface: () -> Color?,
): State<NeutralForegroundTransition> {
    // The surface already interpolates, so another ink tween would trail it.
    val currentSurface = rememberUpdatedState(surface)
    return remember {
        derivedStateOf {
            val color = currentSurface.value()
            val decision = NeutralForegroundDecision(color, color?.let(::neutralForeground))
            NeutralForegroundTransition(emptyList(), decision, 1f, playerForegroundMemo)
        }
    }
}

/** Semantic ink before any ancestor surface adjustment, for controls with their own plate. */
@Composable
internal fun surfaceSourcePalette(): ColorPalette =
    LocalForegroundSourcePalette.current ?: LocalAppearance.current.colorPalette

/** Resolve an explicit glyph against the current surface or against its own translucent plate. */
@Composable
internal fun surfaceGlyphColor(original: Color, plate: Color? = null, minimum: Float = IconContrast,
    enabled: Boolean = true): Color {
    val surface = LocalForegroundSurface.current?.let { backdrop ->
        plate?.let { effectiveSurfaceColor(backdrop, it) } ?: backdrop
    }.takeIf { enabled }
    val endpoint = resolveSurfaceGlyphColor(original, plate, minimum, enabled)
    return playerForegroundOnSharedFrame(original, endpoint, surface, minOf(minimum, NeutralRetentionContrast),
        allowPaletteInk = enabled, correctSampledGlyph = enabled && minimum <= IconContrast)
}

@Composable
internal fun playerForegroundOnSharedFrame(original: Color, endpoint: Color, surface: Color? = null,
    minimum: Float = IconContrast, allowPaletteInk: Boolean = true, correctSampledGlyph: Boolean = false): Color {
    val playerFrame = LocalPlayerForegroundFrame.current
    val correction = LocalPlayerGlyphCorrection.current.takeIf { correctSampledGlyph }
    if (playerFrame == null && correction == null) return endpoint
    val frame = remember { PlayerMeasuredInkFrame() }
    val native = allowPaletteInk && followsPlayerPaletteInk(playerFrame) && isNeutralForeground(original)
    val ink = if (native) unadjustedPlayerInk(original) else original
    return frame.resolveEndpoint(ink, endpoint, playerFrame, surface, minimum, native, correction)
}

@Composable
private fun followsPlayerPaletteInk(frame: PlayerForegroundFrame?): Boolean =
    frame != null && frame.changesPolarity && frame.progress < 1f && LocalPlayerFollowsPaletteInk.current

@Composable
private fun unadjustedPlayerInk(original: Color): Color {
    val palette = LocalAppearance.current.colorPalette
    val source = LocalForegroundSourcePalette.current ?: return original
    // Neutral role inputs may already contain the parent's shared fade. Resolve once from source.
    fun isRole(role: Color): Boolean = original.copy(alpha = role.alpha) == role
    return when {
        isRole(palette.text) -> source.text.copy(alpha = original.alpha)
        isRole(palette.textSecondary) -> source.textSecondary.copy(alpha = original.alpha)
        isRole(palette.iconButtonPlayer) -> source.iconButtonPlayer.copy(alpha = original.alpha)
        else -> original
    }
}

@Composable
private fun resolveSurfaceGlyphColor(original: Color, plate: Color?, minimum: Float, enabled: Boolean): Color {
    val transition = LocalNeutralForegroundTransition.current?.value
    val commonNeutral = LocalNeutralForegroundOverride.current
    val surface = LocalForegroundSurface.current ?: LocalSurfaceBackdropColor.current ?:
        LocalNeutralForegroundSurface.current ?: transition?.to?.surface ?: transition?.from?.firstOrNull()?.decision?.surface
    if (!isNeutralForeground(original) || original.alpha == 0f) return original
    val ink = if (plate != null && plate.alpha > 0f) original else unadjustedPlayerInk(original)
    if (transition != null) return transition.resolve(ink, minimum, plate, surface, glyph = true, enabled = enabled)
    if (surface == null) return original
    val actual = plate?.let { effectiveSurfaceColor(surface, it) } ?: surface
    val decisionSurface = if (plate == null || plate.alpha == 0f)
        LocalNeutralForegroundSurface.current ?: actual else actual
    LocalGlassInkDarkness.current?.let {
        return glassPaneForeground(ink, decisionSurface, minimum, it, glyph = true, enabled = enabled)
    }
    val target = if (isGentleGlyph(ink, enabled)) readableGlyphForeground(ink, decisionSurface, minimum, enabled)
        else if (contrastAgainst(ink, decisionSurface) >= minOf(minimum, NeutralRetentionContrast)) ink
        else if (commonNeutral != null && (plate == null || plate.alpha == 0f)) commonNeutral.copy(alpha = ink.alpha)
        else readableForeground(ink, actual, minimum)
    return if (commonNeutral == null) target else lerp(ink, target, LocalNeutralForegroundAmount.current)
}

/** Keep an inactive track visually distinct when it approaches the adjusted progress color. */
@Composable
internal fun surfaceProgressTrackColor(progress: Color, track: Color, plate: Color? = null): Color {
    val surface = LocalForegroundSurface.current ?: return track
    if (!track.isSpecified || track.alpha == 0f || !isNeutralForeground(track)) return track
    // Player tracks follow the same source roles and clock instead of a second local recoloring.
    if (LocalNeutralForegroundTransition.current != null) return surfaceGlyphColor(track, plate, 1.5f)
    val actual = plate?.let { effectiveSurfaceColor(surface, it) } ?: surface
    val visibleProgress = progress.compositeOver(actual)
    val visibleTrack = track.compositeOver(actual)
    return if (contrastAgainst(visibleProgress, visibleTrack) >= 1.5f) track
        else neutralForeground(actual).copy(alpha = 0.38f)
}

@Composable
internal fun surfaceForegroundScrim(): Color = LocalForegroundScrim.current

/** Keeps decoration polarity on the same continuous local surface as the visible text. */
@Composable
internal fun surfaceNeutralForeground(default: Color): Color {
    if (followsPlayerPaletteInk(LocalPlayerForegroundFrame.current))
        return LocalForegroundSourcePalette.current?.text ?: default
    return LocalForegroundSurface.current?.let { surface ->
        glassPaneForeground(Color.Unspecified, surface, TextContrast, paletteDarkness())
    } ?: default
}
