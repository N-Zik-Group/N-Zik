package app.n_zik.android.components.settings.data

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.color
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.glassSettingsCardBackground
import app.n_zik.android.components.glass.glassSettingsCardRim
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.typography

internal data class StorageChartSegment(
    val category: StorageCategory,
    val startFraction: Double,
    val endFraction: Double,
) {
    fun startX(width: Float): Float = (startFraction * width).toFloat()
    fun endX(width: Float): Float = (endFraction * width).toFloat()
}

internal fun StorageBreakdown.chartSegments(): List<StorageChartSegment> = buildList {
    var cumulativeBytes = 0L
    for ((category, bytes) in this@chartSegments.bytes) {
        val start = cumulativeBytes.toDouble() / totalBytes
        cumulativeBytes += bytes
        val end = cumulativeBytes.toDouble() / totalBytes
        if (end > start) add(StorageChartSegment(category, start, end))
    }
}

internal fun List<StorageChartSegment>.categoryAt(x: Float, width: Float): StorageCategory? {
    if (width <= 0f || x !in 0f..width) return null
    // Match the drawn boundaries, assigning each shared edge to the segment on its right.
    return firstOrNull {
        val start = it.startX(width)
        val end = it.endX(width)
        end > start && x >= start && (x < end || x == width && end == width)
    }?.category
}

private fun LayoutCoordinates.storageChartBounds(): Rect =
    Rect(positionInRoot(), Size(size.width.toFloat(), size.height.toFloat()))

@Composable
internal fun StorageChart(snapshot: StorageBreakdown) {
    val context = LocalContext.current
    val palette = colorPalette()
    val settings = rememberGlassSettings()
    val glass = settings.usesGlassMaterial(GlassArea.SettingsCards)
    val segments = remember(snapshot) { snapshot.chartSegments() }
    val latestSegments by rememberUpdatedState(segments)
    val rowBounds = remember { mutableStateMapOf<StorageCategory, Rect>() }
    val markers = remember { mutableStateMapOf<StorageCategory, Offset>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var bar by remember { mutableStateOf(Rect.Zero) }
    var inspectedCategory by remember { mutableStateOf<StorageCategory?>(null) }
    val inactiveColor = palette.textSecondary.copy(alpha = 0.55f)
    val inspection = updateTransition(inspectedCategory, label = "storageInspection")
    val emphasis = StorageCategory.entries.associateWith { category ->
        inspection.animateFloat(
            transitionSpec = { tween(180) },
            label = "${category.name} emphasis",
        ) { selected -> if (selected == null || selected == category) 1f else 0f }
    }
    val leaderProgress = StorageCategory.entries.associateWith { category ->
        inspection.animateFloat(
            transitionSpec = { tween(180) },
            label = "${category.name} leader",
        ) { selected -> if (selected == category) 1f else 0f }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(Unit) {
                fun categoryAt(position: Offset): StorageCategory? {
                    val point = position + origin
                    if (bar.height > 0f && point.y >= bar.top && point.y < bar.bottom + 6.dp.toPx()) {
                        return latestSegments.categoryAt(point.x - bar.left, bar.width)
                    }
                    return rowBounds.entries.firstOrNull { point in it.value }?.key
                }

                var inspecting = false
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        inspectedCategory = categoryAt(it)
                        inspecting = inspectedCategory != null
                    },
                    onDrag = { change, _ ->
                        if (inspecting) categoryAt(change.position)?.let { inspectedCategory = it }
                    },
                    onDragEnd = { inspectedCategory = null },
                    onDragCancel = { inspectedCategory = null },
                )
            }
            .drawBehind {
                for (segment in segments) {
                    val category = segment.category
                    val progress = leaderProgress.getValue(category).value
                    if (progress <= 0f) continue
                    val marker = markers[category]?.minus(origin) ?: continue
                    val start = segment.startX(bar.width)
                    val end = segment.endX(bar.width)
                    if (end <= start) continue
                    val anchorX = bar.left - origin.x + (start + end) / 2f
                    val anchorY = bar.bottom - origin.y
                    val turnY = anchorY + 6.dp.toPx()
                    val gutterX = marker.x + if (layoutDirection == LayoutDirection.Ltr) {
                        -8.dp.toPx()
                    } else {
                        8.dp.toPx()
                    }
                    val path = Path().apply {
                        moveTo(anchorX, anchorY)
                        lineTo(anchorX, turnY)
                        lineTo(gutterX, turnY)
                        lineTo(gutterX, marker.y)
                        lineTo(marker.x, marker.y)
                    }
                    val visiblePath = if (progress >= 1f) path else {
                        val measure = PathMeasure().apply { setPath(path, false) }
                        Path().also { measure.getSegment(0f, measure.length * progress, it, true) }
                    }
                    drawPath(
                        path = visiblePath,
                        color = category.color,
                        style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(
            Modifier.fillMaxWidth().height(8.dp)
                .testTag("data.storage.chart.bar")
                .onGloballyPositioned { bar = it.storageChartBounds() }
                .glassSettingsCardBackground(
                    backgroundColor = palette.background0,
                    cardColor = palette.background1,
                    shape = CircleShape,
                    settings = settings,
                    sourceBackdrop = LocalGlassContainerBackdrop.current,
                )
                .glassSettingsCardRim(CircleShape)
                .clip(CircleShape),
        ) {
            for (segment in segments) {
                val start = segment.startX(size.width)
                val end = segment.endX(size.width)
                if (end <= start) continue
                val color = lerp(inactiveColor, segment.category.color, emphasis.getValue(segment.category).value)
                val alpha = if (glass) color.alpha * 0.68f else color.alpha
                drawRect(color.copy(alpha = alpha), Offset(start, 0f), Size(end - start, size.height))
            }
        }
        Column(
            modifier = Modifier.padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((category, bytes) in snapshot.bytes) {
                val startsVolumeGroup = category == StorageCategory.OTHER_USED || category == StorageCategory.FREE
                val amount = emphasis.getValue(category).value
                val textColor = lerp(inactiveColor, palette.text, amount)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = if (startsVolumeGroup) 6.dp else 0.dp)
                        .onGloballyPositioned { rowBounds[category] = it.storageChartBounds() }
                        .semantics(mergeDescendants = true) {
                            if (inspectedCategory != null) selected = inspectedCategory == category
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(10.dp)
                            .onGloballyPositioned { markers[category] = it.storageChartBounds().center }
                            .background(lerp(inactiveColor, category.color, amount), CircleShape),
                    )
                    BasicText(
                        text = stringResource(category.label),
                        modifier = Modifier.weight(1.2f),
                        style = typography().xxs.color(textColor),
                    )
                    BasicText(
                        text = Formatter.formatShortFileSize(context, bytes),
                        modifier = Modifier.weight(1f),
                        style = typography().xxs.color(textColor).copy(textAlign = TextAlign.End),
                    )
                }
            }
        }
    }
}
