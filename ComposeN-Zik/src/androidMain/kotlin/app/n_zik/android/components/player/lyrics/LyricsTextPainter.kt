package app.n_zik.android.components.player.lyrics

import app.n_zik.android.components.glass.glassGlobalIndication

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.n_zik.android.components.player.lyricsThemeColor
import app.n_zik.android.enums.lyrics.LyricsAlignment
import app.n_zik.android.enums.lyrics.LyricsColor
import app.n_zik.android.enums.lyrics.LyricsFontSize
import app.n_zik.android.enums.lyrics.LyricsHighlight
import app.n_zik.android.enums.lyrics.LyricsOutline
import app.it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import app.it.fast4x.rimusic.utils.conditional
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
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
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()

    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 10000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ), label = ""
    )

    val RainbowColors = listOf(Color.Red, Color.Magenta, Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Red)
    val RainbowColorsdark = listOf(
        Color.Black.copy(0.35f).compositeOver(Color.Red),
        Color.Black.copy(0.35f).compositeOver(Color.Magenta),
        Color.Black.copy(0.35f).compositeOver(Color.Blue),
        Color.Black.copy(0.35f).compositeOver(Color.Cyan),
        Color.Black.copy(0.35f).compositeOver(Color.Green),
        Color.Black.copy(0.35f).compositeOver(Color.Yellow),
        Color.Black.copy(0.35f).compositeOver(Color.Red)
    )
    val RainbowColors2 = listOf(
        Color.Red.copy(0.3f), Color.Magenta.copy(0.3f), Color.Blue.copy(0.3f),
        Color.Cyan.copy(0.3f), Color.Green.copy(0.3f), Color.Yellow.copy(0.3f), Color.Red.copy(0.3f)
    )
    val Themegradient = listOf(colorPalette().background2, colorPalette().accent)
    val Themegradient2 = listOf(colorPalette().background2.copy(0.5f), colorPalette().accent.copy(0.5f))
    val oldlyrics = listOf(PureBlackColorPalette.text, PureBlackColorPalette.text)
    val oldlyrics2 = listOf(PureBlackColorPalette.textDisabled, PureBlackColorPalette.textDisabled)

    val lightTheme = colorPaletteMode == ColorPaletteMode.Light
    
    val brushrainbow = remember(offset) {
        object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                val widthOffset = size.width * offset
                val heightOffset = size.height * offset
                return LinearGradientShader(
                    colors = if (isCurrentIndex)
                        if (showlyricsthumbnail) oldlyrics else RainbowColors
                    else if (showlyricsthumbnail) oldlyrics2 else RainbowColors2,
                    from = Offset(widthOffset, heightOffset),
                    to = Offset(widthOffset + size.width, heightOffset + size.height),
                    tileMode = TileMode.Mirror
                )
            }
        }
    }
    
    val brushrainbowdark = remember(offset) {
        object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                val widthOffset = size.width * offset
                val heightOffset = size.height * offset
                return LinearGradientShader(
                    colors = if (isCurrentIndex) RainbowColorsdark else RainbowColors2,
                    from = Offset(widthOffset, heightOffset),
                    to = Offset(widthOffset + size.width, heightOffset + size.height),
                    tileMode = TileMode.Mirror
                )
            }
        }
    }

    val brushtheme = remember(offset) {
        object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                val widthOffset = size.width * offset
                val heightOffset = size.height * offset
                return LinearGradientShader(
                    colors = if (isCurrentIndex)
                        if (showlyricsthumbnail) oldlyrics else Themegradient
                    else if (showlyricsthumbnail) oldlyrics2 else Themegradient2,
                    from = Offset(widthOffset, heightOffset),
                    to = Offset(widthOffset + size.width, heightOffset + size.height),
                    tileMode = TileMode.Mirror
                )
            }
        }
    }

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
                        fontSize = when (fontSize) {
                            LyricsFontSize.Light -> typography().m.fontSize
                            LyricsFontSize.Medium -> typography().l.fontSize
                            LyricsFontSize.Heavy -> typography().xl.fontSize
                            LyricsFontSize.Large -> typography().xlxl.fontSize
                            else -> customSize.sp
                        },
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
                        brush = if (lightTheme) brushrainbow else brushrainbowdark,
                        fontSize = when (fontSize) {
                            LyricsFontSize.Light -> typography().m.fontSize
                            LyricsFontSize.Medium -> typography().l.fontSize
                            LyricsFontSize.Heavy -> typography().xl.fontSize
                            LyricsFontSize.Large -> typography().xlxl.fontSize
                            else -> customSize.sp
                        },
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
                        fontSize = when (fontSize) {
                            LyricsFontSize.Light -> typography().m.fontSize
                            LyricsFontSize.Medium -> typography().l.fontSize
                            LyricsFontSize.Heavy -> typography().xl.fontSize
                            LyricsFontSize.Large -> typography().xlxl.fontSize
                            else -> customSize.sp
                        },
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
                    fontSize = when (fontSize) {
                        LyricsFontSize.Light -> typography().m.fontSize
                        LyricsFontSize.Medium -> typography().l.fontSize
                        LyricsFontSize.Heavy -> typography().xl.fontSize
                        LyricsFontSize.Large -> typography().xlxl.fontSize
                        else -> customSize.sp
                    },
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
        } else if (lyricsColor == LyricsColor.White || lyricsColor == LyricsColor.Cover || lyricsColor == LyricsColor.Custom || lyricsColor == LyricsColor.Thememode) {
            BasicText(
                text = text,
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    color = if (lyricsColor == LyricsColor.White) Color.White
                    else if (lyricsColor == LyricsColor.Cover) Color(dominantColor)
                    else if (lyricsColor == LyricsColor.Thememode) lyricsThemeColor(colorPalette(), textOnAccentBackground)
                    else Color(lyricsCustomColor),
                    fontSize = when (fontSize) {
                        LyricsFontSize.Light -> typography().m.fontSize
                        LyricsFontSize.Medium -> typography().l.fontSize
                        LyricsFontSize.Heavy -> typography().xl.fontSize
                        LyricsFontSize.Large -> typography().xlxl.fontSize
                        else -> customSize.sp
                    },
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
        } else {
            BasicText(
                text = text,
                style = TextStyle(
                    brush = if (lightTheme) brushrainbow else brushrainbowdark,
                    fontSize = when (fontSize) {
                        LyricsFontSize.Light -> typography().m.fontSize
                        LyricsFontSize.Medium -> typography().l.fontSize
                        LyricsFontSize.Heavy -> typography().xl.fontSize
                        LyricsFontSize.Large -> typography().xlxl.fontSize
                        else -> customSize.sp
                    },
                    fontWeight = FontWeight.Medium,
                    textAlign = lyricsAlignment.selected
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
            )
        }
    }
}

