
package app.n_zik.android.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.graphics.asComposePath
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import android.graphics.Path as AndroidPath
import android.graphics.Matrix as AndroidMatrix
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.random.Random
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import app.n_zik.android.components.theme.LaunchedAppearanceAnimationEffect
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.shakeSensitivityThemeKey
import app.n_zik.android.enums.AnimatedGradient
import app.n_zik.android.enums.ShakeSensitivityTheme

private const val M3E_HIDDEN_VISIBILITY = 0.01f
private const val M3E_RETRACTED_SCALE = 0.62f
private const val M3E_SOFT_DRAW_AREA = 0.15f
private const val M3E_SHAPE_ALPHA = 0.4f
private const val M3E_TRACK_RETRACTION_MS = 230
private const val M3E_POSITION_COLUMNS = 5
private const val M3E_POSITION_ROWS = 4
private const val M3E_SHAPE_COUNT = M3E_POSITION_COLUMNS * M3E_POSITION_ROWS

internal fun m3ePlaybackIsActive(playbackState: Int, isPlaying: Boolean): Boolean =
    playbackState == Player.STATE_READY && isPlaying

internal fun m3eTrackRetractionTarget(alwaysShowShapes: Boolean): Float =
    if (alwaysShowShapes) M3E_RETRACTED_SCALE * M3E_RETRACTED_SCALE else 0f

/** Interpret the animation value as painted area to keep both extremes of the sweep visible. */
internal fun m3eShapeScale(area: Float): Float = sqrt(area)

internal fun m3eShapeAlpha(area: Float): Float =
    M3E_SHAPE_ALPHA * (area / M3E_SOFT_DRAW_AREA).coerceAtMost(1f)

/** Gives each shape a jittered cell only when the previous arrangement is invisible. */
internal fun m3eRevealPositions(visibility: Float, random: Random = Random.Default): List<Offset>? {
    if (visibility > M3E_HIDDEN_VISIBILITY) return null
    return List(M3E_SHAPE_COUNT) { index ->
        Offset(
            x = (index % M3E_POSITION_COLUMNS + 0.25f + random.nextFloat() * 0.5f) / M3E_POSITION_COLUMNS,
            y = (index / M3E_POSITION_COLUMNS + 0.25f + random.nextFloat() * 0.5f) / M3E_POSITION_ROWS,
        )
    }.shuffled(random)
}

/** Tracks one cover presentation and one impulse per Media3 transition on the UI thread. */
@Stable
internal class PlayerM3EOccurrences {
    private data class Presentation(val occurrence: Long, val gradient: AnimatedGradient)

    var current by mutableLongStateOf(0L)
        private set
    private var presented by mutableStateOf<Presentation?>(null)
    private var consumed: Long? = null

    fun advance() {
        current++
        presented = null
    }

    fun present(occurrence: Long, gradient: AnimatedGradient) {
        if (occurrence == current && !isPresented(occurrence, gradient)) {
            presented = Presentation(occurrence, gradient)
        }
    }

    fun isPresented(occurrence: Long, gradient: AnimatedGradient): Boolean {
        val release = presented ?: return false
        return occurrence == current && release.occurrence == occurrence && release.gradient == gradient
    }

    fun consume(occurrence: Long, gradient: AnimatedGradient): Boolean {
        if (!isPresented(occurrence, gradient) || occurrence == consumed) return false
        consumed = occurrence
        return true
    }
}

@OptIn(UnstableApi::class)
internal fun Modifier.animatedM3EBackground(
    animating: Boolean,
    V: Color,
    DV: Color,
    M: Color,
    occurrences: PlayerM3EOccurrences,
    gradient: AnimatedGradient,
    burstActive: Boolean,
    rendered: Boolean,
    alwaysShowShapes: Boolean,
    active: Boolean = true,
    incoming: Boolean = true,
    artworkSwatches: State<List<Color>>? = null,
): Modifier = composed {
    val accumulatedTime = remember { mutableFloatStateOf(0f) }

    val colors = listOf(V, DV, M)

    val occurrence = occurrences.current
    var lastRetractedOccurrence by remember(occurrences) { mutableLongStateOf(occurrence) }
    val physicsActive = active && incoming
    val revealAllowed = (animating || alwaysShowShapes) && physicsActive && burstActive
    val trackRetractionTarget = m3eTrackRetractionTarget(alwaysShowShapes)
    val retractionDuration = when {
        !active -> 150
        !incoming || !burstActive -> 350
        else -> 600
    }
    val visibilityAnimation = remember { Animatable(0f) }
    val visibilityProgress by visibilityAnimation.asState()
    val shapesMoving = animating || alwaysShowShapes

    val shapes = remember {
        List(M3E_SHAPE_COUNT) { index ->
            ShapeConfig(
                x = 0f,
                y = 0f,
                vx = (Random.nextFloat() - 0.5f) * 0.08f,
                vy = (Random.nextFloat() - 0.5f) * 0.08f,
                baseSpeed = Random.nextFloat() * 0.04f + 0.03f,
                radiusScale = Random.nextFloat() * 0.20f + 0.03f,
                colorIndex = index % colors.size,
                type = index % 10,
                rotation = Random.nextFloat() * 360f,
                rotSpeed = (Random.nextFloat() - 0.5f) * 0.8f,
                pulsePhase = Random.nextFloat() * 100f,
                isMorph = index < 18,
            )
        }
    }

    fun arrangeWhileHidden() {
        m3eRevealPositions(visibilityAnimation.value)?.forEachIndexed { index, position ->
            val shape = shapes[index]
            shape.x = position.x
            shape.y = position.y
        }
    }

    LaunchedAppearanceAnimationEffect(
        occurrences, occurrence, gradient, rendered, revealAllowed, retractionDuration, trackRetractionTarget,
    ) {
        if (!rendered) {
            visibilityAnimation.snapTo(0f)
            return@LaunchedAppearanceAnimationEffect
        }
        if (!revealAllowed) {
            if (visibilityAnimation.value > 0f) {
                visibilityAnimation.animateTo(0f, tween(retractionDuration, easing = FastOutSlowInEasing))
            }
            return@LaunchedAppearanceAnimationEffect
        }
        val needsRetraction = lastRetractedOccurrence != occurrence || !occurrences.isPresented(occurrence, gradient)
        if (needsRetraction && visibilityAnimation.value > maxOf(trackRetractionTarget, M3E_HIDDEN_VISIBILITY)) {
            visibilityAnimation.animateTo(trackRetractionTarget, tween(M3E_TRACK_RETRACTION_MS, easing = LinearEasing))
        }
        if (occurrence != occurrences.current) return@LaunchedAppearanceAnimationEffect
        lastRetractedOccurrence = occurrence

        coroutineScope {
            // The optional waiting state can start opening before artwork arrives.
            val waitingAnimation = if (!occurrences.isPresented(occurrence, gradient) &&
                visibilityAnimation.value < trackRetractionTarget
            ) launch {
                arrangeWhileHidden()
                visibilityAnimation.animateTo(trackRetractionTarget, tween(600, easing = FastOutSlowInEasing))
            } else null
            snapshotFlow { occurrences.isPresented(occurrence, gradient) }.first { it }
            waitingAnimation?.cancelAndJoin()
        }
        if (!occurrences.isPresented(occurrence, gradient)) return@LaunchedAppearanceAnimationEffect

        arrangeWhileHidden()
        if (occurrences.consume(occurrence, gradient)) {
            shapes.forEach { shape ->
                shape.vx += Random.nextFloat() - 0.5f
                shape.vy += Random.nextFloat() - 0.5f
            }
        }
        if (visibilityAnimation.value < 1f) {
            visibilityAnimation.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        }
    }

    val context = LocalContext.current

    // Listen for shake only while the visible shapes are moving.
    DisposableEffect(shapesMoving, physicsActive) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var listener: SensorEventListener? = null

        if (shapesMoving && physicsActive && accelerometer != null) {
            val sensitivity = context.preferences.getEnum(shakeSensitivityThemeKey, ShakeSensitivityTheme.Disabled)
            if (sensitivity != ShakeSensitivityTheme.Disabled) {
                var lastShakeTime = 0L

                listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        event?.let {
                            val x = it.values[0]
                            val gX = x / SensorManager.GRAVITY_EARTH

                            if (abs(gX) > sensitivity.thresholdG) {
                                val now = System.currentTimeMillis()
                                if (now - lastShakeTime > 1000) {
                                    lastShakeTime = now
                                    shapes.forEach { shape ->
                                        shape.vx += (Random.nextFloat() - 0.5f) * 1.2f
                                        shape.vy += (Random.nextFloat() - 0.5f) * 1.2f
                                    }
                                }
                            }
                        }
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }

                sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }

        onDispose {
            listener?.let { sensorManager.unregisterListener(it) }
        }
    }

    // Animation loop - physics simulation with grid-based collision
    LaunchedAppearanceAnimationEffect(shapesMoving, physicsActive) {
        if (shapesMoving && physicsActive) {
            val gridSize = 8
            val grid = Array(gridSize * gridSize) { mutableListOf<Int>() }

            playerDecorationFrames { deltaNanos ->
                if (visibilityAnimation.value <= M3E_HIDDEN_VISIBILITY) return@playerDecorationFrames
                val delta = (deltaNanos / 1_000_000_000f).coerceAtMost(0.05f)

                // Clear grid
                for (cell in grid) cell.clear()

                // Assign shapes to grid cells
                for (index in shapes.indices) {
                    val shape = shapes[index]
                    val cellX = ((shape.x * gridSize).toInt()).coerceIn(0, gridSize - 1)
                    val cellY = ((shape.y * gridSize).toInt()).coerceIn(0, gridSize - 1)
                    grid[cellY * gridSize + cellX].add(index)
                }

                // Collision detection - only check adjacent cells, process each pair once
                for (i in shapes.indices) {
                    val shape1 = shapes[i]
                    val cellX1 = ((shape1.x * gridSize).toInt()).coerceIn(0, gridSize - 1)
                    val cellY1 = ((shape1.y * gridSize).toInt()).coerceIn(0, gridSize - 1)

                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val neighborX = cellX1 + dx
                            val neighborY = cellY1 + dy
                            if (neighborX in 0 until gridSize && neighborY in 0 until gridSize) {
                                for (j in grid[neighborY * gridSize + neighborX]) {
                                    if (j <= i) continue
                                    val shape2 = shapes[j]
                                    val ddx = shape2.x - shape1.x
                                    val ddy = shape2.y - shape1.y
                                    val distSq = ddx * ddx + ddy * ddy
                                    val minDist = shape1.radiusScale + shape2.radiusScale
                                    val minDistSq = minDist * minDist

                                    if (distSq < minDistSq && distSq > 0.0001f) {
                                        val dist = sqrt(distSq)
                                        val force = (minDist - dist) * 0.4f
                                        val fx = (ddx / dist) * force
                                        val fy = (ddy / dist) * force
                                        shape1.vx -= fx * delta
                                        shape1.vy -= fy * delta
                                        shape2.vx += fx * delta
                                        shape2.vy += fy * delta
                                    }
                                }
                            }
                        }
                    }

                    val currentSpeed = sqrt(shape1.vx * shape1.vx + shape1.vy * shape1.vy)
                    if (currentSpeed > 0.001f) {
                        val speedDiff = shape1.baseSpeed - currentSpeed
                        shape1.vx += (shape1.vx / currentSpeed) * speedDiff * delta * 0.3f
                        shape1.vy += (shape1.vy / currentSpeed) * speedDiff * delta * 0.3f
                    }

                    val speedSq = shape1.vx * shape1.vx + shape1.vy * shape1.vy
                    if (speedSq > 4.0f) {
                        val scale = 2.0f / sqrt(speedSq)
                        shape1.vx *= scale
                        shape1.vy *= scale
                    }

                    shape1.x += shape1.vx * delta
                    shape1.y += shape1.vy * delta

                    val margin = 0.08f
                    val edgeForce = 0.15f
                    if (shape1.x < margin) { shape1.vx += delta * edgeForce }
                    if (shape1.x > 1f - margin) { shape1.vx -= delta * edgeForce }
                    if (shape1.y < margin) { shape1.vy += delta * edgeForce }
                    if (shape1.y > 1f - margin) { shape1.vy -= delta * edgeForce }

                    if (shape1.x < -0.1f) { shape1.x = -0.1f; shape1.vx *= -1f }
                    if (shape1.x > 1.1f) { shape1.x = 1.1f; shape1.vx *= -1f }
                    if (shape1.y < -0.1f) { shape1.y = -0.1f; shape1.vy *= -1f }
                    if (shape1.y > 1.1f) { shape1.y = 1.1f; shape1.vy *= -1f }
                }

                accumulatedTime.floatValue += delta
            }
        }
    }

    val sequenceMorphs = remember {
        val morphPolygons = listOf(
            RoundedPolygon.star(numVerticesPerRadius = 5, innerRadius = 0.5f, rounding = CornerRounding(0.2f)),
            RoundedPolygon(numVertices = 12, rounding = CornerRounding(1f)),
            RoundedPolygon(numVertices = 4, rounding = CornerRounding(0.3f)),
            RoundedPolygon.star(numVerticesPerRadius = 8, innerRadius = 0.8f, rounding = CornerRounding(0.15f)),
            RoundedPolygon(numVertices = 3, rounding = CornerRounding(0.2f)),
            RoundedPolygon(numVertices = 5, rounding = CornerRounding(0.2f)),
            RoundedPolygon.star(numVerticesPerRadius = 4, innerRadius = 0.3f, rounding = CornerRounding(0.15f)),
            RoundedPolygon(numVertices = 6, rounding = CornerRounding(0.4f)),
            RoundedPolygon.star(numVerticesPerRadius = 6, innerRadius = 0.7f, rounding = CornerRounding(0.3f)),
            RoundedPolygon(numVertices = 7, rounding = CornerRounding(0.2f))
        )
        morphPolygons.mapIndexed { index, poly ->
            Morph(poly, morphPolygons[(index + 1) % morphPolygons.size])
        }
    }

    this.drawWithCache {
        val width = size.width
        val height = size.height
        val minDim = minOf(width, height)

        val basePaths = List(6) { type ->
            val polygon = when (type) {
                0 -> RoundedPolygon(numVertices = 3, rounding = CornerRounding(0.2f))
                1 -> RoundedPolygon(numVertices = 4, rounding = CornerRounding(0.3f))
                2 -> RoundedPolygon(numVertices = 6, rounding = CornerRounding(0.2f))
                3 -> RoundedPolygon.star(numVerticesPerRadius = 5, innerRadius = 0.5f, rounding = CornerRounding(0.2f))
                4 -> RoundedPolygon.star(numVerticesPerRadius = 4, innerRadius = 0.3f, rounding = CornerRounding(0.15f))
                else -> RoundedPolygon.star(numVerticesPerRadius = 8, innerRadius = 0.8f, rounding = CornerRounding(0.15f))
            }

            val composePath = ComposePath()
            val scale = minDim
            val cubics = polygon.cubics
            if (cubics.isNotEmpty()) {
                composePath.moveTo(cubics[0].anchor0X * scale, cubics[0].anchor0Y * scale)
                for (cubic in cubics) {
                    composePath.cubicTo(
                        cubic.control0X * scale, cubic.control0Y * scale,
                        cubic.control1X * scale, cubic.control1Y * scale,
                        cubic.anchor1X * scale, cubic.anchor1Y * scale
                    )
                }
                composePath.close()
            }
            composePath
        }

        val cachedMatrix = AndroidMatrix()
        val cachedPath = AndroidPath()
        val cachedComposePath = cachedPath.asComposePath()

        onDrawBehind {
            val area = visibilityProgress
            if (area <= M3E_HIDDEN_VISIBILITY) return@onDrawBehind
            val time = accumulatedTime.floatValue
            val drawScale = m3eShapeScale(area)
            val drawAlpha = m3eShapeAlpha(area)
            val swatches = artworkSwatches?.value

            shapes.forEach { shape ->
                val scaleFactor = shape.radiusScale
                val radius = scaleFactor * minDim

                val spanX = width - 2 * radius
                val spanY = height - 2 * radius

                val currentX = radius + shape.x * spanX
                val currentY = radius + shape.y * spanY

                val swatchIndex = when (shape.colorIndex) {
                    0 -> 1 // Vibrant
                    1 -> 3 // Dark vibrant
                    else -> 4 // Muted
                }
                val fill = swatches?.getOrNull(swatchIndex) ?: colors[shape.colorIndex]
                val shapeColor = fill.copy(alpha = drawAlpha)

                withTransform({
                    translate(left = currentX, top = currentY)
                    rotate(degrees = shape.rotation + (shape.rotSpeed * time * 250f), pivot = Offset.Zero)
                    val visualScale = scaleFactor * drawScale
                    scale(scaleX = visualScale, scaleY = visualScale, pivot = Offset.Zero)
                }) {
                    val pathToDraw = if (shape.isMorph) {
                        val cycleSpeed = 0.35f
                        val totalMorphs = sequenceMorphs.size
                        val cycleTime = ((time * cycleSpeed + shape.pulsePhase) % totalMorphs + totalMorphs) % totalMorphs
                        val morphIndex = cycleTime.toInt()
                        val progress = cycleTime - morphIndex

                        val easedProgress = (sin((progress - 0.5f) * PI.toFloat()) + 1f) / 2f

                        val morph = sequenceMorphs[morphIndex]

                        cachedPath.rewind()
                        morph.toPath(easedProgress, cachedPath)

                        cachedMatrix.reset()
                        cachedMatrix.setScale(minDim, minDim)
                        cachedPath.transform(cachedMatrix)
                        cachedComposePath
                    } else {
                        basePaths[shape.type % basePaths.size]
                    }

                    drawPath(
                        path = pathToDraw,
                        color = shapeColor,
                        blendMode = BlendMode.SrcOver
                    )
                }
            }
        }
    }
}

private class ShapeConfig(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val baseSpeed: Float,
    val radiusScale: Float,
    val colorIndex: Int,
    val type: Int,
    val rotation: Float,
    val rotSpeed: Float,
    val pulsePhase: Float,
    val isMorph: Boolean
)
