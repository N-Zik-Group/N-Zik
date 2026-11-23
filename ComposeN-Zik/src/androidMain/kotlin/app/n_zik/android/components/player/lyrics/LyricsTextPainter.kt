package app.n_zik.android.components.player.lyrics

import app.n_zik.android.components.glass.glassGlobalIndication

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.components.player.lyricsThemeColor
import app.n_zik.android.enums.lyrics.LyricsAlignment
import app.n_zik.android.enums.lyrics.LyricsColor
import app.n_zik.android.enums.lyrics.LyricsFontSize
import app.n_zik.android.enums.lyrics.LyricsHighlight
import app.n_zik.android.enums.lyrics.LyricsOutline
import app.n_zik.android.utils.conditional
import app.n_zik.android.colorPalette
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun LyricsTextPainter(
    text: String,
    isSync: Boolean,
    isCurrentIndex: Boolean,
    showlyricsthumbnail: Boolean,
    lyricsOutline: LyricsOutline,
    colorPaletteMode: ColorPaletteMode,
    fontSize: LyricsFontSize,
    customSize: Float,
    lyricsAlignment: LyricsAlignment,
    lyricsSizeAnimate: Boolean,
    lyricsColor: LyricsColor,
    lyricsCustomColor: Int,
    dominantColor: Int,
    lyricsHighlight: LyricsHighlight,
    clickLyricsText: Boolean,
    textOnAccentBackground: Boolean = false,
    showShimmer: Boolean = false,
    onClick: () -> Unit
) {
    val textSize = lyricsReadingFontSize(fontSize, customSize)
    if (showShimmer) {
        val alignment = when (lyricsAlignment) {
            LyricsAlignment.Left -> Alignment.CenterStart
            LyricsAlignment.Center -> Alignment.Center
            LyricsAlignment.Right -> Alignment.CenterEnd
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
            LyricsTextShimmer(
                text = text,
                style = TextStyle(fontSize = textSize, fontWeight = FontWeight.Medium, textAlign = lyricsAlignment.selected),
                modifier = Modifier.padding(horizontal = 32.dp, vertical = if (isSync) 8.dp else 4.dp),
            )
        }
        return
    }
    val lightTheme = colorPaletteMode == ColorPaletteMode.Light

    val animateSizeText by animateFloatAsState(
        targetValue = if (isCurrentIndex) 1.08f else 0.92f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = ""
    )
    val animateOpacity by animateFloatAsState(
        targetValue = if (isCurrentIndex) 1f else 0.35f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = ""
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (!showlyricsthumbnail) {
            if (lyricsOutline != LyricsOutline.None && lyricsOutline != LyricsOutline.Rainbow && lyricsOutline != LyricsOutline.Glow) {
                BasicText(
                    text = text,
                    style = TextStyle(
                        fontWeight = FontWeight.Medium,
                        color = if (lyricsOutline == LyricsOutline.White) Color.White
                        else if (lyricsOutline == LyricsOutline.Black) Color.Black
                        else if (lyricsOutline == LyricsOutline.Thememode)
                            if (colorPaletteMode == ColorPaletteMode.Light) Color.White else Color.Black
                        else Color.Transparent,
                        fontSize = textSize,
                        textAlign = lyricsAlignment.selected,
                        drawStyle = Stroke(
                            width = when (fontSize) {
                                LyricsFontSize.Large -> if (lyricsOutline == LyricsOutline.White || (lyricsOutline == LyricsOutline.Thememode && lightTheme)) 6f else 10f
                                LyricsFontSize.Heavy -> if (lyricsOutline == LyricsOutline.White || (lyricsOutline == LyricsOutline.Thememode && lightTheme)) 3f else 7f
                                LyricsFontSize.Medium -> if (lyricsOutline == LyricsOutline.White || (lyricsOutline == LyricsOutline.Thememode && lightTheme)) 2f else 6f
                                LyricsFontSize.Light -> if (lyricsOutline == LyricsOutline.White || (lyricsOutline == LyricsOutline.Thememode && lightTheme)) 1.3f else 5.3f
                                else -> if (lyricsOutline == LyricsOutline.White || (lyricsOutline == LyricsOutline.Thememode && lightTheme)) (customSize / 5.6f) else (customSize / 3.4f)
                            },
                            join = StrokeJoin.Round
                        )
                    ),
                    modifier = Modifier
                        .padding(vertical = 4.dp, horizontal = 32.dp)
                        .conditional(isSync) { padding(vertical = 4.dp) }
                        .align(
                            if (lyricsAlignment == LyricsAlignment.Left) Alignment.CenterStart
                            else if (lyricsAlignment == LyricsAlignment.Right) Alignment.CenterEnd else Alignment.Center
                        )
                        .graphicsLayer {
                            alpha = if(isSync) animateOpacity else 1f
                            transformOrigin = if (lyricsAlignment == LyricsAlignment.Center) TransformOrigin(0.5f, 0.5f)
                            else if (lyricsAlignment == LyricsAlignment.Left) TransformOrigin(0f, 0.5f)
                            else TransformOrigin(1f, 0.5f)
                            scaleY = animateSizeText
                            scaleX = animateSizeText
                        }
                )
            } else if (lyricsOutline == LyricsOutline.Rainbow) {
                BasicText(
                    text = text,
                    style = TextStyle(
                        textAlign = lyricsAlignment.selected,
                        brush = rememberRainbowOutlineBrush(isCurrentIndex, lightTheme),
                        fontSize = textSize,
                        fontWeight = FontWeight.Medium,
                        drawStyle = Stroke(
                            width = when (fontSize) {
                                LyricsFontSize.Large -> if (isCurrentIndex) 10.0f else 6f
                                LyricsFontSize.Heavy -> if (isCurrentIndex) 7f else 5f
                                LyricsFontSize.Medium -> if (isCurrentIndex) 6f else 4f
                                LyricsFontSize.Light -> if (isCurrentIndex) 5.3f else 3.3f
                                else -> if (isCurrentIndex) (customSize / 3.4f) else (customSize / 5.6f)
                            },
                            join = StrokeJoin.Round
                        )
                    ),
                    modifier = Modifier
                        .padding(vertical = 4.dp, horizontal = 32.dp)
                        .conditional(isSync) { padding(vertical = 4.dp) }
                        .align(
                            if (lyricsAlignment == LyricsAlignment.Left) Alignment.CenterStart
                            else if (lyricsAlignment == LyricsAlignment.Right) Alignment.CenterEnd else Alignment.Center
                        )
                        .graphicsLayer {
                            alpha = if(isSync) animateOpacity else 1f
                            transformOrigin = if (lyricsAlignment == LyricsAlignment.Center) TransformOrigin(0.5f, 0.5f)
                            else if (lyricsAlignment == LyricsAlignment.Left) TransformOrigin(0f, 0.5f)
                            else TransformOrigin(1f, 0.5f)
                            scaleY = animateSizeText
                            scaleX = animateSizeText
                        }
                )
            } else if (lyricsOutline == LyricsOutline.Glow && !showlyricsthumbnail) {
                // Glow outline logic is inside the text shadow
                BasicText(
                    text = text,
                    style = TextStyle(
                        fontSize = textSize,
                        fontWeight = FontWeight.Medium,
                        textAlign = lyricsAlignment.selected,
                        color = if (lyricsColor == LyricsColor.Thememode || lyricsColor == LyricsColor.White || lyricsColor == LyricsColor.Cover || lyricsColor == LyricsColor.Custom)
                            Color.White.copy(0.3f) else Color.Transparent,
                        shadow = Shadow(
                            color = if (isCurrentIndex)
                                if (lyricsColor == LyricsColor.Thememode) Color.White.copy(0.3f).compositeOver(lyricsThemeColor(colorPalette(), textOnAccentBackground))
                                else if (lyricsColor == LyricsColor.White) Color.White.copy(0.3f).compositeOver(Color.White)
                                else if (lyricsColor == LyricsColor.Cover) Color.White.copy(0.3f).compositeOver(Color(dominantColor))
                                else if (lyricsColor == LyricsColor.Custom) Color.White.copy(0.3f).compositeOver(Color(lyricsCustomColor))
                                else Color.Transparent
                            else Color.Transparent,
                            offset = Offset(0f, 0f),
                            blurRadius = 25f
                        )
                    ),
                    modifier = Modifier
                        .padding(vertical = 4.dp, horizontal = 32.dp)
                        .conditional(isSync) { padding(vertical = 4.dp) }
                        .align(
                            if (lyricsAlignment == LyricsAlignment.Left) Alignment.CenterStart
                            else if (lyricsAlignment == LyricsAlignment.Right) Alignment.CenterEnd else Alignment.Center
                        )
                        .graphicsLayer {
                            alpha = if(isSync) animateOpacity else 1f
                            transformOrigin = if (lyricsAlignment == LyricsAlignment.Center) TransformOrigin(0.5f, 0.5f)
                            else if (lyricsAlignment == LyricsAlignment.Left) TransformOrigin(0f, 0.5f)
                            else TransformOrigin(1f, 0.5f)
                            scaleY = animateSizeText
                            scaleX = animateSizeText
                        }
                )
            }
        }

        if (showlyricsthumbnail) {
            BasicText(
                text = text,
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    color = if (isCurrentIndex) {
                        if (lyricsColor == LyricsColor.White) Color.White
                        else if (lyricsColor == LyricsColor.Cover) Color(dominantColor)
                        else if (lyricsColor == LyricsColor.Thememode) lyricsThemeColor(colorPalette(), textOnAccentBackground)
                        else Color(lyricsCustomColor)
                    } else {
                        if (lyricsColor == LyricsColor.White) Color.White
                        else if (lyricsColor == LyricsColor.Cover) Color(dominantColor)
                        else if (lyricsColor == LyricsColor.Thememode) lyricsThemeColor(colorPalette(), textOnAccentBackground)
                        else Color(lyricsCustomColor)
                    },
                    fontSize = textSize,
                    textAlign = lyricsAlignment.selected,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.5f),
                        offset = Offset(0f, 0f), blurRadius = 10f
                    )
                ),
                modifier = Modifier
                    .padding(vertical = 4.dp, horizontal = 32.dp)
                    .align(
                        if (lyricsAlignment == LyricsAlignment.Left) Alignment.CenterStart
                        else if (lyricsAlignment == LyricsAlignment.Right) Alignment.CenterEnd else Alignment.Center
                    )
                    .graphicsLayer {
                        alpha = animateOpacity
                        scaleY = animateSizeText
                        scaleX = animateSizeText
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = glassGlobalIndication(if (clickLyricsText) ripple(true) else null),
                        onClick = onClick
                    )
            )
        } else {
            BasicText(
                text = text,
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    color = if (lyricsColor == LyricsColor.White) Color.White
                    else if (lyricsColor == LyricsColor.Cover) Color(dominantColor)
                    else if (lyricsColor == LyricsColor.Thememode) lyricsThemeColor(colorPalette(), textOnAccentBackground)
                    else Color(lyricsCustomColor),
                    fontSize = textSize,
                    textAlign = lyricsAlignment.selected,
                ),
                modifier = Modifier
                    .padding(vertical = 4.dp, horizontal = 32.dp)
                    .conditional(isSync) { padding(vertical = 4.dp) }
                    .align(
                        if (lyricsAlignment == LyricsAlignment.Left) Alignment.CenterStart
                        else if (lyricsAlignment == LyricsAlignment.Right) Alignment.CenterEnd else Alignment.Center
                    )
                    .graphicsLayer {
                        alpha = if(isSync) animateOpacity else 1f
                        transformOrigin = if (lyricsAlignment == LyricsAlignment.Center) TransformOrigin(0.5f, 0.5f)
                        else if (lyricsAlignment == LyricsAlignment.Left) TransformOrigin(0f, 0.5f)
                        else TransformOrigin(1f, 0.5f)
                        scaleY = animateSizeText
                        scaleX = animateSizeText
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = glassGlobalIndication(if (clickLyricsText) ripple(true) else null),
                        onClick = onClick
                    )
                    .background(
                        if (isCurrentIndex) if (lyricsHighlight == LyricsHighlight.White) Color.White.copy(0.5f)
                        else if (lyricsHighlight == LyricsHighlight.Black) Color.Black.copy(0.5f)
                        else Color.Transparent else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .conditional(lyricsHighlight != LyricsHighlight.None) { fillMaxWidth() }
            )
        }
    }
}

private val rainbowOutlineColors =
    listOf(Color.Red, Color.Magenta, Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Red)
private val darkRainbowOutlineColors = rainbowOutlineColors.map { Color.Black.copy(alpha = 0.35f).compositeOver(it) }
private val inactiveRainbowOutlineColors = rainbowOutlineColors.map { it.copy(alpha = 0.3f) }

private val LocalRainbowOutlines = staticCompositionLocalOf<State<List<ShaderBrush>>?> { null }

@Composable
internal fun LyricsRainbowOutlines(enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) CompositionLocalProvider(LocalRainbowOutlines provides rememberRainbowOutlines(), content = content)
    else content()
}

@Composable
private fun rememberRainbowOutlines(): State<List<ShaderBrush>> {
    val transition = rememberInfiniteTransition(label = "Lyrics rainbow outline")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Lyrics rainbow offset",
    )
    return remember(phase) {
        derivedStateOf {
            val offset = phase.value
            listOf(inactiveRainbowOutlineColors, rainbowOutlineColors, darkRainbowOutlineColors).map { colors ->
                object : ShaderBrush() {
                    override fun createShader(size: Size): Shader {
                        val start = Offset(size.width * offset, size.height * offset)
                        return LinearGradientShader(
                            colors = colors,
                            from = start,
                            to = start + Offset(size.width, size.height),
                            tileMode = TileMode.Mirror,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberRainbowOutlineBrush(isCurrentIndex: Boolean, lightTheme: Boolean): ShaderBrush {
    val outlines = LocalRainbowOutlines.current ?: rememberRainbowOutlines()
    val index = when {
        !isCurrentIndex -> 0
        lightTheme -> 1
        else -> 2
    }
    return outlines.value[index]
}
