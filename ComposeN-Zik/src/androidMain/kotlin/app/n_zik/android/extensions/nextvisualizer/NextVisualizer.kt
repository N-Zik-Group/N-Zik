package app.n_zik.android.extensions.nextvisualizer

import app.n_zik.android.components.glass.rememberGlassArtwork
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.coil.rememberDisplayArtworkSource
import app.n_zik.android.components.glass.glassGlobalIndication

import app.n_zik.android.privacy.PermissionUsageJournal
import app.n_zik.android.privacy.PermissionFeature
import app.n_zik.android.privacy.JournalPermission
import app.n_zik.android.privacy.PermissionEventKind

import androidx.compose.ui.draw.clip


import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.utils.colorPaletteModeKey
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.n_zik.android.components.player.LocalPlayerCoverOverlay
import app.n_zik.android.components.player.LocalPlayerArtworkPaint
import app.n_zik.android.components.theme.LocalAmbientArtworkPaint
import app.n_zik.android.components.glass.glassOverlayCanvasViewport
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.GlassOverlayControls
import app.n_zik.android.components.glass.GlassOverlayScene
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.drawable.APP_ICON_BITMAP
import app.n_zik.android.core.coil.resize
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import painters.Painter
import painters.fft.FftBar
import painters.fft.FftCBar
import painters.fft.FftCLine
import painters.fft.FftCWave
import painters.fft.FlatWave
import painters.fft.FftLine
import painters.fft.FftWave
import painters.misc.Icon
import painters.modifier.Compose
import painters.modifier.Move
import painters.modifier.Shake
import painters.modifier.Scale
import painters.waveform.WfmAnalog
import utils.VisualizerHelper
import views.VisualizerView
import app.n_zik.android.typography
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.components.glass.GlassOverlayIconButton as IconButton
import app.n_zik.android.ui.components.themed.SecondaryTextButton
import app.n_zik.android.utils.currentVisualizerKey
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.hasPermission
import app.n_zik.android.utils.isCompositionLaunched
import app.n_zik.android.utils.rememberPreference

import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.showvisthumbnailKey
import app.n_zik.android.utils.showVisualizerButtonsKey
import app.n_zik.android.utils.visualizerEnabledKey
import painters.modifier.Rotate
import app.n_zik.android.components.glass.overlayViewportShape
import app.n_zik.android.thumbnailShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import app.n_zik.android.components.menu.visualizer.VisualizerSettingsMenu
import app.n_zik.android.components.player.rememberArtworkDisplayColors
import app.n_zik.android.components.player.rememberPlayerColorFade
import app.n_zik.android.ui.components.LocalMenuState
import androidx.compose.foundation.shape.CircleShape
import app.n_zik.android.utils.visualizerLineThicknessKey
import app.n_zik.android.utils.visualizerWhiteColorOptionKey
import app.n_zik.android.utils.visualizerCustomColorKey

@OptIn(UnstableApi::class)
@Composable
fun NextVisualizer(sceneBackground: Color = Color.Transparent, onCover: Boolean? = null) {

    val context = LocalContext.current
    val visualizerEnabled by rememberPreference(visualizerEnabledKey, false)
    val savedThumbnail by rememberPreference(showvisthumbnailKey, true)
    val showThumbnail = onCover ?: (LocalPlayerCoverOverlay.current || savedThumbnail)

    if (visualizerEnabled) {

        val permission = Manifest.permission.RECORD_AUDIO

        var relaunchPermission by remember {
            mutableStateOf(false)
        }

        var hasPermission by remember(isCompositionLaunched()) {
            mutableStateOf(context.applicationContext.hasPermission(permission))
        }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = {
                hasPermission = it
                PermissionUsageJournal.record(context, PermissionFeature.VISUALIZER, JournalPermission.MICROPHONE,
                    if (it) PermissionEventKind.GRANTED else PermissionEventKind.DENIED)
            }
        )

        if (!hasPermission) {

            LaunchedEffect(Unit, relaunchPermission) { PermissionUsageJournal.record(context, PermissionFeature.VISUALIZER, JournalPermission.MICROPHONE, PermissionEventKind.REQUESTED); launcher.launch(permission) }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(
                    2.dp,
                    Alignment.CenterVertically
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BasicText(
                    text = stringResource(R.string.visualizer_microphone_permission_message),
                    modifier = Modifier.fillMaxWidth(0.75f),
                    style = typography().xs.semiBold
                )
                Spacer(modifier = Modifier.height(20.dp))
                SecondaryTextButton(
                    text = stringResource(R.string.visualizer_action_open_permission_settings),
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                setData(Uri.fromParts("package", context.packageName, null))
                            }
                        )
                    }
                )
            }

        } else {

            val binder = LocalPlayerServiceBinder.current
            val visualizerView = remember { VisualizerView(context) }
            // Capture seam (issue #606) is wired once in MainApplication.onCreate() — guaranteed
            // to run before this or any other visualizer composable (e.g. SeekBarVisualizer).
            val helper = remember(binder?.player?.audioSessionId) {
                VisualizerHelper(binder?.player?.audioSessionId ?: 0)
            }

            val visualizerLineThickness by rememberPreference(visualizerLineThicknessKey, 6f)
            val basePainters = remember(visualizerLineThickness) {
                createBaseVisualizers(android.graphics.Color.WHITE, visualizerLineThickness)
            }
            val iconPreset = basePainters.size
            var currentVisualizer by rememberPreference(currentVisualizerKey, 0)
            if (currentVisualizer !in 0..iconPreset) currentVisualizer = 0
            val usesArtworkIcon = currentVisualizer == iconPreset
            val visualizerWhiteColorOption by rememberPreference(visualizerWhiteColorOptionKey, enums.VisualizerWhiteColorOption.White)
            val visualizerCustomColor by rememberPreference(visualizerCustomColorKey, android.graphics.Color.WHITE)
            val paletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)
            val requestedDark = paletteMode == ColorPaletteMode.Dark || (paletteMode == ColorPaletteMode.PitchBlack || paletteMode == ColorPaletteMode.PureBlack) ||
                (paletteMode == ColorPaletteMode.System && isSystemInDarkTheme())
            val scopedArtworkPaint = LocalPlayerArtworkPaint.current
            val ambientArtwork = LocalAmbientArtworkPaint.current
            val needsCoverTint = visualizerWhiteColorOption == enums.VisualizerWhiteColorOption.Cover &&
                scopedArtworkPaint == null && ambientArtwork == null
            val currentArtworkUri = if (usesArtworkIcon || needsCoverTint) rememberGlassArtwork(binder?.player)?.uri else null
            val coverTint = if (needsCoverTint) {
                val coverDisplay = rememberArtworkDisplayColors(
                    currentArtworkUri, requestedDark, awaitArtworkMetadata = true,
                )
                val target = coverDisplay?.takeIf { it.colors != null || it.forceDark }?.foreground ?: Color.White
                rememberPlayerColorFade(target, active = true, interpolate = ::lerp)
            } else null
            val themeTint = colorPalette().accent

            val selectedPainter = if (usesArtworkIcon) {
                val profile = ImageCacheFactory.rememberArtworkProfile()
                val displayedSource = rememberDisplayArtworkSource(currentArtworkUri, profile)
                val revision = ImageCacheFactory.rememberArtworkRevision(displayedSource, profile)
                val sourceIdentity = remember(displayedSource, profile) {
                    ImageCacheFactory.artworkColorIdentity(displayedSource, profile)
                }
                var circleBitmap by remember(profile, sourceIdentity) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(displayedSource, profile, revision) {
                    try {
                        val bitmap = withContext(NzikDispatchers.DATA) {
                            displayedSource?.resize(1000, 1000)
                                ?.let { ImageCacheFactory.loadBitmap(it, allowHardware = false, profile = profile) }
                                ?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
                        }
                        val circled = withContext(NzikDispatchers.MEDIA) {
                            Icon.getCircledBitmap(bitmap ?: APP_ICON_BITMAP)
                        }
                        if (getActiveProfile(context) == profile) circleBitmap = circled
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Timber.tag("NextVisualizer").w(error, "Could not load visualizer artwork")
                        if (getActiveProfile(context) == profile) circleBitmap = null
                    }
                }
                val fallback by remember { lazy { Icon.getCircledBitmap(APP_ICON_BITMAP) } }
                remember(circleBitmap, visualizerLineThickness) {
                    createArtworkVisualizer(circleBitmap ?: fallback, android.graphics.Color.WHITE, visualizerLineThickness)
                }
            } else basePainters[currentVisualizer]
            val tintPaints = remember(selectedPainter) { selectedPainter.tintPaints() }
            val visualizerCount = iconPreset + 1

            val showVisualizerButtons by rememberPreference(showVisualizerButtonsKey, true)

            var showControls by remember { mutableStateOf(true) }
            var controlsTimerKey by remember { mutableStateOf(0) }

            LaunchedEffect(controlsTimerKey) {
                showControls = true
                delay(3000)
                showControls = false
            }
            
            val menuState = LocalMenuState.current

            GlassOverlayScene(
                scope = app.n_zik.android.components.glass.GlassScope.Visualizer,
                background = sceneBackground,
                modifier = Modifier
                    .fillMaxSize()
                    .glassOverlayCanvasViewport(rememberGlassSettings(), showThumbnail = showThumbnail)
                    .clip(overlayViewportShape(showThumbnail, thumbnailShape())).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = glassGlobalIndication(null)
                    ) { controlsTimerKey++ },
                scene = {
                    LaunchedEffect(visualizerView, helper, selectedPainter) {
                        visualizerView.setup(helper, selectedPainter)
                        visualizerView.invalidate()
                    }
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                        factory = { visualizerView },
                        update = { view ->
                            val tint = when (visualizerWhiteColorOption) {
                                enums.VisualizerWhiteColorOption.White -> android.graphics.Color.WHITE
                                enums.VisualizerWhiteColorOption.Theme -> themeTint.toArgb()
                                enums.VisualizerWhiteColorOption.Cover ->
                                    (scopedArtworkPaint?.value?.visualizerForeground ?:
                                        ambientArtwork?.value?.visualizerForeground?.takeIf { scopedArtworkPaint == null } ?:
                                        coverTint?.value ?: Color.White).toArgb()
                                enums.VisualizerWhiteColorOption.Custom -> visualizerCustomColor
                            }
                            tintPaints.forEach { it.setColor(tint) }
                            view.invalidate()
                        }
                    )

                }
            ) {

                AnimatedVisibility(
                    visible = !showVisualizerButtons || showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    GlassOverlayControls(
                        showThumbnail = showThumbnail,
                        circularThumbnail = thumbnailShape() == CircleShape,
                        cornerSeatSize = 32.dp,
                        centerContent = {
                            IconButton(
                                onClick = {
                                    controlsTimerKey++
                                    if (currentVisualizer > 0) currentVisualizer--
                                    else currentVisualizer = iconPreset
                                },
                                icon = R.drawable.arrow_left,
                                color = colorPalette().text,
                                modifier = Modifier.size(32.dp)
                            )
                    
                            BasicText(
                                text = stringResource(R.string.visualizer_position_format, currentVisualizer + 1, visualizerCount),
                                style = typography().xs.semiBold.copy(color = colorPalette().text),
                            )
                    
                            IconButton(
                                onClick = {
                                    controlsTimerKey++
                                    if (currentVisualizer < iconPreset) currentVisualizer++
                                    else currentVisualizer = 0
                                },
                                icon = R.drawable.arrow_right,
                                color = colorPalette().text,
                                modifier = Modifier.size(32.dp)
                            )
                    
                        },
                    ) {
                        IconButton(
                            onClick = {
                                controlsTimerKey++
                                menuState.display {
                                    VisualizerSettingsMenu().MenuComponent()
                                }
                            },
                            icon = R.drawable.settings,
                            color = colorPalette().text,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

private class VisualizerTintPaint(private val paint: Paint) {
    // Some layers specify their own opacity independently from the selected tint.
    private val fixedAlpha = paint.alpha.takeIf { it != 255 }

    fun setColor(color: Int) {
        paint.color = color
        fixedAlpha?.let { paint.alpha = it }
    }
}

private fun Painter.tintPaints(): List<VisualizerTintPaint> = when (this) {
    is Compose -> painters.flatMap { it.tintPaints() }
    is Move -> painters.flatMap { it.tintPaints() }
    is Rotate -> painters.flatMap { it.tintPaints() }
    is Scale -> painters.flatMap { it.tintPaints() }
    is Shake -> painters.flatMap { it.tintPaints() }
    is Icon -> emptyList()
    else -> listOf(VisualizerTintPaint(paint))
}

private fun createBaseVisualizers(color: Int, visualizerLineThickness: Float): List<Painter> {
    val ampR = 6f
    val ampRfm = 3.3f
    val smoothWfm = 0.4f
    val smooth = 0.6f
    val ampRMin = 2f
    return listOf(
        // Basic components
        Move(WfmAnalog(colorPaint = color, ampR = ampRfm, smooth = smoothWfm).apply { paint.strokeWidth = visualizerLineThickness }),
        Move(FlatWave(colorPaint = color, ampR = 3f).apply { paint.strokeWidth = visualizerLineThickness }),
        Move(FftBar(colorPaint = color, ampR = ampR), yR = 0.5f),
        Move(FftBar(colorPaint = color, side = "b", ampR = ampR), yR = -0.5f),
        Compose(
            Move(FftBar(colorPaint = color, ampR = 4f), yR = 0.5f),
            Scale(Move(FftBar(colorPaint = color, side = "b", ampR = 4f), yR = -0.5f), scaleX = -1f)
        ),
        Move(FftLine(colorPaint = color, ampR = ampR), yR = 0.5f),
        Move(FftLine(colorPaint = color, side = "b", ampR = ampR), yR = -0.5f),
        Compose(
            Move(FftLine(colorPaint = color, ampR = 4f), yR = 0.5f),
            Scale(Move(FftLine(colorPaint = color, side = "b", ampR = 4f), yR = -0.5f), scaleX = -1f)
        ),

        Move(FftWave(colorPaint = color, ampR = ampR), yR = 0.5f),
        Move(FftWave(colorPaint = color, side = "b", ampR = ampR), yR = -0.5f),
        Compose(
            Move(FftWave(colorPaint = color, ampR = 4f), yR = 0.5f),
            Scale(Move(FftWave(colorPaint = color, side = "b", ampR = 4f), yR = -0.5f), scaleX = -1f)
        ),

        Move(FftBar(colorPaint = color, side = "ab", ampR = 4f), yR = 0f),
        Move(FftLine(colorPaint = color, side = "ab", ampR = 4f), yR = 0f),
        Move(FftWave(colorPaint = color, side = "ab", ampR = 4f), yR = 0f),
        Compose(
            Move(FftBar(colorPaint = color, side = "ab"), yR = -.3f),
            Move(FftLine(colorPaint = color, side = "ab"), yR = 0f),
            Move(FftWave(colorPaint = color, side = "ab"), yR = .3f)
        ),
        // Basic components (Circle)
        Move(FftCLine(colorPaint = color, ampR = ampRMin)),
        FftCWave(colorPaint = color, ampR = ampRMin),
        FftCWave(side = "b", colorPaint = color, ampR = ampRMin),
        Compose(
            Move(FftCLine(colorPaint = color, side = "b", ampR = ampRMin)),
            FftCWave(side = "b", colorPaint = color, ampR = ampRMin)
        ),
        Compose(
            Move(FftCLine(colorPaint = color, side = "ab", ampR = ampRMin)),
            FftCWave(side = "ab", colorPaint = color, ampR = ampRMin)
        ),
        Move(
            FftCBar(colorPaint = color, side = "ab", gapX = 8f, ampR = ampRMin).apply {
                paint.style = Paint.Style.FILL
            }
        ),
    )
}

private fun createArtworkVisualizer(circleBitmap: Bitmap, color: Int, lineThickness: Float): Painter =
    Compose(
        WfmAnalog(colorPaint = color, ampR = 3.3f, smooth = 0.4f).apply {
            paint.alpha = 150
            paint.strokeWidth = lineThickness
        },
        Shake(
            Compose(
                Rotate(FftCWave(colorPaint = color, ampR = 2f)),
                Icon(circleBitmap),
            ),
        ).apply {
            animX.duration = 1000
            animY.duration = 2000
        },
    )
