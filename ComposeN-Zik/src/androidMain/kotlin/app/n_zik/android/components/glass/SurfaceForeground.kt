package app.n_zik.android.components.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.components.theme.withColor
import app.n_zik.android.ui.styling.LocalAppearance

/** Estimated scene or plate color at the origin of an overlay. */
internal val LocalSurfaceBackdropColor = compositionLocalOf<Color?> { null }
internal val LocalNeutralForegroundOverride = compositionLocalOf<Color?> { null }
internal val LocalNeutralForegroundAmount = compositionLocalOf { 1f }
internal val LocalNeutralForegroundSurface = compositionLocalOf<Color?> { null }
internal val LocalNeutralForegroundTransition = compositionLocalOf<NeutralForegroundTransition?> { null }
internal val LocalForegroundSourcePalette = compositionLocalOf<ColorPalette?> { null }
private val LocalForegroundSurface = compositionLocalOf<Color?> { null }
private val LocalForegroundScrim = compositionLocalOf { Color.Transparent }

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
    content: @Composable () -> Unit,
) {
    val appearance = LocalAppearance.current
    val source = LocalForegroundSourcePalette.current ?: appearance.colorPalette
    val commonNeutral = LocalNeutralForegroundOverride.current
    val amount = LocalNeutralForegroundAmount.current
    val commonSurface = LocalNeutralForegroundSurface.current ?: surface
    val transition = LocalNeutralForegroundTransition.current
    val adapt = surface != null || commonNeutral != null || transition != null
    val inheritedContent = LocalContentColor.current
    fun ink(original: Color, minimum: Float, glyph: Boolean = false): Color =
        if (transition != null) transition.resolve(original, minimum, glyph = glyph)
        else if (glyph && surface != null && isNeutralForeground(original) && isGentleGlyph(original, true))
            readableGlyphForeground(original, commonSurface ?: surface, minimum)
        else if (commonNeutral != null && isNeutralForeground(original) && original.alpha > 0f) {
            val target = if (commonSurface != null &&
                contrastAgainst(original, commonSurface) >= minOf(minimum, NeutralRetentionContrast)) original
                else commonNeutral.copy(alpha = original.alpha)
            lerp(original, target, amount)
        } else if (surface != null) readableForeground(original, surface, minimum) else original
    val palette = if (adapt) appearance.colorPalette.copy(
        text = ink(source.text, TextContrast),
        textSecondary = ink(source.textSecondary, TextContrast),
        textDisabled = source.textDisabled,
        iconButtonPlayer = ink(source.iconButtonPlayer, IconContrast, glyph = true),
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
        content = content,
    )
}

internal data class NeutralForegroundDecision(val surface: Color?, val anchor: Color?)
internal data class WeightedNeutralDecision(val decision: NeutralForegroundDecision, val weight: Float)

/** Every role resolves both endpoints from its source ink and uses this single shared clock. */
internal data class NeutralForegroundTransition(
    val from: List<WeightedNeutralDecision>,
    val to: NeutralForegroundDecision,
    val progress: Float,
) {
    fun resolve(original: Color, minimum: Float = IconContrast, plate: Color? = null,
        localSurface: Color? = null, glyph: Boolean = false, enabled: Boolean = true): Color {
        if (!original.isSpecified || !isNeutralForeground(original) || original.alpha == 0f) return original
        fun endpoint(decision: NeutralForegroundDecision): Color {
            val background = decision.surface ?: return original
            val actual = if (plate != null && plate.alpha > 0f)
                effectiveSurfaceColor(localSurface ?: background, plate) else background
            if (glyph && isGentleGlyph(original, enabled))
                return readableGlyphForeground(original, actual, minimum, enabled)
            if (plate != null && plate.alpha > 0f)
                return readableForeground(original, actual, minimum)
            if (contrastAgainst(original, background) >= minOf(minimum, NeutralRetentionContrast)) return original
            return decision.anchor?.copy(alpha = original.alpha) ?: readableForeground(original, background, minimum)
        }
        var start = original
        var accumulated = 0f
        for ((decision, weight) in from) if (weight > 0f) {
            start = if (accumulated == 0f) endpoint(decision)
                else lerp(start, endpoint(decision), weight / (accumulated + weight))
            accumulated += weight
        }
        val end = endpoint(to)
        return when {
            progress >= 1f || start == end -> end
            progress <= 0f -> start
            else -> lerp(start, end, progress)
        }
    }

    internal fun frozenBlend(): List<WeightedNeutralDecision> =
        (from.map { it.copy(weight = it.weight * (1f - progress)) } + WeightedNeutralDecision(to, progress))
            .filter { it.weight > 0.00001f }
}

@Composable
internal fun rememberNeutralForegroundFade(surface: Color?, initial: Color): NeutralForegroundTransition {
    val original = remember { NeutralForegroundDecision(null, null) }
    var from by remember { mutableStateOf(listOf(WeightedNeutralDecision(original, 1f))) }
    var to by remember { mutableStateOf(original) }
    val clock = remember { Animatable(1f) }
    val decision = NeutralForegroundDecision(surface, surface?.let(::neutralForeground))
    LaunchedEffect(decision) {
        if (decision != to) {
            val retained = NeutralForegroundTransition(from, to, clock.value).frozenBlend()
            clock.snapTo(0f)
            from = retained
            to = decision
            clock.animateTo(1f, tween(180))
        }
    }
    return NeutralForegroundTransition(from, to, clock.value)
}

/** Semantic ink before any ancestor surface adjustment, for controls with their own plate. */
@Composable
internal fun surfaceSourcePalette(): ColorPalette =
    LocalForegroundSourcePalette.current ?: LocalAppearance.current.colorPalette

/** Resolve an explicit glyph against the current surface or against its own translucent plate. */
@Composable
internal fun surfaceGlyphColor(original: Color, plate: Color? = null, minimum: Float = IconContrast,
    enabled: Boolean = true): Color {
    val transition = LocalNeutralForegroundTransition.current
    val commonNeutral = LocalNeutralForegroundOverride.current
    val surface = LocalForegroundSurface.current ?: LocalSurfaceBackdropColor.current ?:
        LocalNeutralForegroundSurface.current ?: transition?.to?.surface ?: transition?.from?.firstOrNull()?.decision?.surface
    if (!isNeutralForeground(original) || original.alpha == 0f) return original
    val palette = LocalAppearance.current.colorPalette
    val source = LocalForegroundSourcePalette.current
    // Neutral role inputs may already contain the parent's shared fade. Resolve once from source.
    fun isRole(role: Color): Boolean = original.copy(alpha = role.alpha) == role
    val ink = if (plate != null && plate.alpha > 0f) original else when {
        isRole(palette.text) -> source?.text?.copy(alpha = original.alpha) ?: original
        isRole(palette.textSecondary) -> source?.textSecondary?.copy(alpha = original.alpha) ?: original
        isRole(palette.iconButtonPlayer) -> source?.iconButtonPlayer?.copy(alpha = original.alpha) ?: original
        else -> original
    }
    if (transition != null) return transition.resolve(ink, minimum, plate, surface, glyph = true, enabled = enabled)
    if (surface == null) return original
    val actual = plate?.let { effectiveSurfaceColor(surface, it) } ?: surface
    val decisionSurface = if (plate == null || plate.alpha == 0f)
        LocalNeutralForegroundSurface.current ?: actual else actual
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

@Composable
internal fun surfaceForegroundIsLight(default: Boolean): Boolean =
    LocalForegroundSurface.current?.let { neutralForeground(it) == Color.White } ?: default
