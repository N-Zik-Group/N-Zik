package app.n_zik.android.components.dialog.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toRect
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import kotlinx.coroutines.launch
import android.graphics.Color as AndroidColor
import app.n_zik.android.uiRoundnessShape
import android.graphics.LinearGradient

/** HSV color picker on the shared [DefaultDialog] (moved from the legacy tree). */
@Composable
fun DialogColorPicker(
    onDismiss: () -> Unit,
    initialColor: Color,
    onColorSelected: (Color) -> Unit
) {
    val hsv = remember {
        val hsv = floatArrayOf(0f, 0f, 0f)
        AndroidColor.colorToHSV(initialColor.toArgb(), hsv)
        mutableStateOf(
            Triple(hsv[0], hsv[1], hsv[2])
        )
    }
    val backgroundColor = remember(hsv.value) {
        mutableStateOf(Color.hsv(hsv.value.first, hsv.value.second, hsv.value.third, initialColor.alpha))
    }

    DefaultDialog(
            title = stringResource(R.string.common_color_picker_title),
        onDismiss = onDismiss,
        footer = {
            ButtonRow(listOf(
                DialogButton(text = stringResource(R.string.common_action_cancel), onClick = onDismiss, danger = true),
                DialogButton(text = stringResource(R.string.common_action_apply), primary = true, onClick = {
                    onColorSelected(backgroundColor.value)
                    onDismiss()
                })
            ))
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            SatValPanel(hue = hsv.value.first, saturation = hsv.value.second, brightness = hsv.value.third) { sat, value ->
                hsv.value = Triple(hsv.value.first, sat, value)
            }

            Spacer(modifier = Modifier.height(32.dp))

            HueBar(selectedHue = hsv.value.first) { hue ->
                hsv.value = Triple(hue, hsv.value.second, hsv.value.third)
            }

            Spacer(modifier = Modifier.height(32.dp))

            ColorPreviewSwatch(backgroundColor.value, Modifier.size(50.dp))


        }
    }
}



@Composable
internal fun ColorPreviewSwatch(color: Color, modifier: Modifier = Modifier) {
    val shape = uiRoundnessShape()
    Box(modifier.background(color, shape).border(1.dp, colorPalette().textSecondary.copy(alpha = 0.3f), shape))
}

@Composable
private fun SatValPanel(
    hue: Float,
    saturation: Float,
    brightness: Float,
    setSatVal: (Float, Float) -> Unit
) {
    val interactionSource = remember {
        MutableInteractionSource()
    }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val onChange by rememberUpdatedState(setSatVal)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            (interaction as? PressInteraction.Press)?.pressPosition?.let { position ->
                val size = panelSize
                if (size.width > 0 && size.height > 0) {
                    onChange(
                        (position.x / size.width).coerceIn(0f, 1f),
                        (1f - position.y / size.height).coerceIn(0f, 1f),
                    )
                }
            }
        }
    }
    Canvas(
        modifier = Modifier
            .size(300.dp)
            .onSizeChanged { panelSize = it }
            .emitDragGesture(interactionSource)
            .clip(uiRoundnessShape())
    ) {
        val cornerRadius = 12.dp.toPx()
        val bitmap = Bitmap.createBitmap(size.width.toInt(), size.height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val satValPanel = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        val rgb = AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))
        val satShader = LinearGradient(
            satValPanel.left, satValPanel.top, satValPanel.right, satValPanel.top,
            -0x1, rgb, Shader.TileMode.CLAMP
        )
        val valShader = LinearGradient(
            satValPanel.left, satValPanel.top, satValPanel.left, satValPanel.bottom,
            -0x1, -0x1000000, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            satValPanel,
            cornerRadius,
            cornerRadius,
            Paint().apply {
                shader = ComposeShader(
                    valShader,
                    satShader,
                    PorterDuff.Mode.MULTIPLY
                )
            }
        )

        drawBitmap(
            bitmap = bitmap,
            panel = satValPanel
        )

        val selection = Offset(saturation * size.width, (1f - brightness) * size.height)
        drawCircle(
            color = Color.White,
            radius = 8.dp.toPx(),
            center = selection,
            style = Stroke(
                width = 2.dp.toPx()
            )
        )
        drawCircle(
            color = Color.White,
            radius = 2.dp.toPx(),
            center = selection,
        )

    }
}

@Composable
private fun Modifier.emitDragGesture(
    interactionSource: MutableInteractionSource
): Modifier = composed {
    val scope = rememberCoroutineScope()
    pointerInput(Unit) {
        detectDragGestures { input, _ ->
            scope.launch {
                interactionSource.emit(PressInteraction.Press(input.position))
            }
        }
    }.clip(uiRoundnessShape()).clickable(interactionSource, null) {
    }
}

@Composable
private fun HueBar(
    selectedHue: Float,
    setColor: (Float) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val onChange by rememberUpdatedState(setColor)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            (interaction as? PressInteraction.Press)?.pressPosition?.let { position ->
                if (panelSize.width > 0) onChange((position.x / panelSize.width).coerceIn(0f, 1f) * 360f)
            }
        }
    }
    Canvas(
        modifier = Modifier
            .height(40.dp)
            .width(300.dp)
            .onSizeChanged { panelSize = it }
            .clip(uiRoundnessShape())
            .emitDragGesture(interactionSource)
    ) {
        val bitmap = Bitmap.createBitmap(size.width.toInt(), size.height.toInt(), Bitmap.Config.ARGB_8888)
        val hueCanvas = Canvas(bitmap)
        val huePanel = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        val hueColors = IntArray((huePanel.width()).toInt())
        var hue = 0f
        for (i in hueColors.indices) {
            hueColors[i] = AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))
            hue += 360f / hueColors.size
        }
        val linePaint = Paint()
        linePaint.strokeWidth = 0F
        for (i in hueColors.indices) {
            linePaint.color = hueColors[i]
            hueCanvas.drawLine(i.toFloat(), 0F, i.toFloat(), huePanel.bottom, linePaint)
        }
        drawBitmap(
            bitmap = bitmap,
            panel = huePanel
        )
        drawCircle(
            Color.White,
            radius = size.height/2,
            center = Offset(selectedHue / 360f * size.width, size.height / 2),
            style = Stroke(
                width = 2.dp.toPx()
            )
        )
    }
}

private fun DrawScope.drawBitmap(
    bitmap: Bitmap,
    panel: RectF
) {
    drawIntoCanvas {
        it.nativeCanvas.drawBitmap(
            bitmap,
            null,
            panel.toRect(),
            null
        )
    }
}
