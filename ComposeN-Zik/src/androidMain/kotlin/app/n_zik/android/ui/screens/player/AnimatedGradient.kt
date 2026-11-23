package app.n_zik.android.ui.screens.player

import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.player.M3ECoverColors
import app.n_zik.android.components.player.PlayerArtworkPaint
import app.n_zik.android.components.player.PlayerFluidColors
import app.n_zik.android.components.player.PlayerGradientFade
import app.n_zik.android.components.player.PlayerGradientLayers
import app.n_zik.android.components.player.PlayerM3EOccurrences
import app.n_zik.android.components.player.PlayerMeshGradient
import app.n_zik.android.components.player.animatedM3EBackground
import app.n_zik.android.components.player.playerFluidBackground
import app.n_zik.android.components.player.playerShaderBackground
import app.n_zik.android.components.player.playerShaderWash
import app.n_zik.android.components.player.rememberPlayerDecorationElapsedNanos
import app.n_zik.android.components.theme.paletteDarkness
import app.n_zik.android.enums.AnimatedGradient
import app.n_zik.android.ui.styling.ColorPalette
import com.mikepenz.hypnoticcanvas.shaders.BlackCherryCosmos
import com.mikepenz.hypnoticcanvas.shaders.GlossyGradients
import com.mikepenz.hypnoticcanvas.shaders.GoldenMagma
import com.mikepenz.hypnoticcanvas.shaders.GradientFlow
import com.mikepenz.hypnoticcanvas.shaders.IceReflection
import com.mikepenz.hypnoticcanvas.shaders.InkFlow
import com.mikepenz.hypnoticcanvas.shaders.MesmerizingLens
import com.mikepenz.hypnoticcanvas.shaders.OilFlow
import com.mikepenz.hypnoticcanvas.shaders.PurpleLiquid
import com.mikepenz.hypnoticcanvas.shaders.Stage
import kotlin.math.sqrt

@OptIn(UnstableApi::class)
fun Modifier.animatedGradient(
    animating: Boolean,
    swatches: State<List<Color>>,
): Modifier = composed {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(rotation, animating) {
        if (!animating) return@LaunchedEffect
        val target = rotation.value + 360f
        rotation.animateTo(
            targetValue = target,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 30_000,
                    easing = LinearEasing,
                ),
            ),
        )
    }

    drawWithCache {
        val colors = swatches.value
        val rectSize = sqrt(size.width * size.width + size.height * size.height)
        val topLeft = Offset(
            x = -(rectSize - size.width) / 2,
            y = -(rectSize - size.height) / 2,
        )

        val brush1 = Brush.linearGradient(
            0f to colors[0],
            0.33f to colors[2],
            0.66f to colors[1],
            1f to colors[3],
            start = topLeft,
            end = Offset(rectSize * 0.7f, rectSize * 0.7f),
        )

        val brush2 = Brush.linearGradient(
            0f to colors[0],
            0.33f to colors[5],
            0.66f to colors[4],
            1f to colors[6],
            start = Offset(rectSize, 0f),
            end = Offset(0f, rectSize),
        )

        val maskBrush = Brush.linearGradient(
            0f to colors[0],
            1f to Color.Transparent,
            start = Offset(rectSize / 2f, 0f),
            end = Offset(rectSize / 2f, rectSize),
        )

        onDrawBehind {
            val value = rotation.value

            withTransform(transformBlock = { rotate(value) }) {
                drawRect(
                    brush = brush1,
                    topLeft = topLeft,
                    size = Size(rectSize, rectSize),
                )
            }

            withTransform(transformBlock = { rotate(-value) }) {
                drawRect(
                    brush = maskBrush,
                    topLeft = topLeft,
                    size = Size(rectSize, rectSize),
                    blendMode = BlendMode.DstOut,
                )
            }

            withTransform(transformBlock = { rotate(value) }) {
                drawRect(
                    brush = brush2,
                    topLeft = topLeft,
                    size = Size(rectSize, rectSize),
                    blendMode = BlendMode.DstAtop,
                )
            }
        }
    }
}

@Composable
@OptIn(UnstableApi::class)
internal fun BoxScope.PlayerAnimatedBackground(
    backdrop: LayerBackdrop,
    capturePlayerBackground: Boolean,
    gradientFade: PlayerGradientFade,
    decorationsActive: Boolean,
    color: ColorPalette,
    targetFluidColors: PlayerFluidColors?,
    artworkPaint: State<PlayerArtworkPaint>,
    isActuallyPlaying: () -> Boolean,
    shouldPlayM3E: () -> Boolean,
    coverSwatches: M3ECoverColors,
    coverGradientColor: (Int) -> Color,
    m3eOccurrences: PlayerM3EOccurrences,
    m3eBurstGradient: AnimatedGradient,
    m3eAlwaysShowShapes: Boolean,
    m3eRendered: Boolean,
    displayedSwatches: State<List<Color>>,
) {
    val source = Modifier.matchParentSize().then(
        if (capturePlayerBackground) Modifier.layerBackdrop(backdrop) else Modifier
    )
    PlayerGradientLayers(gradientFade, source) { type, incoming ->
        val animationActive = decorationsActive && incoming
        when (type) {
            AnimatedGradient.FluidThemeColorGradient, AnimatedGradient.FluidCoverColorGradient,
            AnimatedGradient.M3EMorphingTheme, AnimatedGradient.M3EMorphingCover -> {
                val isTheme = type == AnimatedGradient.FluidThemeColorGradient || type == AnimatedGradient.M3EMorphingTheme
                val themeStops = PlayerFluidColors(color.background0, color.background2, color.background1, color.accent)
                val fallbackStops = rememberUpdatedState(targetFluidColors ?: themeStops)
                val currentThemeStops = rememberUpdatedState(themeStops)
                val fluidStops = remember(artworkPaint, isTheme) { derivedStateOf {
                    if (isTheme) currentThemeStops.value else artworkPaint.value.fluid ?: fallbackStops.value
                } }
                val fluid = Modifier.playerFluidBackground(
                    fluidStops, rememberPlayerDecorationElapsedNanos(animationActive),
                )
                if (type == AnimatedGradient.M3EMorphingTheme || type == AnimatedGradient.M3EMorphingCover) {
                    fluid.animatedM3EBackground(
                        animating = shouldPlayM3E(),
                        active = decorationsActive,
                        incoming = incoming,
                        V = if (isTheme) color.background2 else coverGradientColor(coverSwatches.vibrant),
                        DV = if (isTheme) color.background1 else coverGradientColor(coverSwatches.darkVibrant),
                        M = if (isTheme) color.background1 else coverGradientColor(coverSwatches.muted),
                        occurrences = m3eOccurrences,
                        gradient = type,
                        burstActive = type == m3eBurstGradient,
                        rendered = m3eRendered,
                        alwaysShowShapes = m3eAlwaysShowShapes,
                        artworkSwatches = displayedSwatches.takeIf { !isTheme },
                    )
                } else fluid
            }
            AnimatedGradient.Linear -> Modifier.animatedGradient(
                animating = isActuallyPlaying() && animationActive,
                swatches = displayedSwatches,
            )
            else -> {
                val shader = when (type) {
                    AnimatedGradient.Mesh -> remember(displayedSwatches) { PlayerMeshGradient(displayedSwatches) }
                    AnimatedGradient.MesmerizingLens -> MesmerizingLens
                    AnimatedGradient.GlossyGradients -> GlossyGradients
                    AnimatedGradient.GradientFlow -> GradientFlow
                    AnimatedGradient.PurpleLiquid -> PurpleLiquid
                    AnimatedGradient.InkFlow -> InkFlow
                    AnimatedGradient.OilFlow -> OilFlow
                    AnimatedGradient.IceReflection -> IceReflection
                    AnimatedGradient.Stage -> Stage
                    AnimatedGradient.GoldenMagma -> GoldenMagma
                    AnimatedGradient.BlackCherryCosmos -> BlackCherryCosmos
                    else -> error("Unexpected shader gradient: $type")
                }
                val shade = playerShaderWash(type, paletteDarkness())
                Modifier.playerShaderBackground(shader, animationActive).background(shade)
            }
        }
    }
}
