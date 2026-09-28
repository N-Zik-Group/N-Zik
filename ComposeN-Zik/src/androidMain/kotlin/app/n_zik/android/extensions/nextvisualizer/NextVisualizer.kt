package app.n_zik.android.extensions.nextvisualizer

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.kreate.android.drawable.APP_ICON_BITMAP
import app.n_zik.android.core.coil.resize
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import painters.Painter
import painters.fft.FftBar
import painters.fft.FftCBar
import painters.fft.FftCLine
import painters.fft.FftCWave
import painters.fft.FftCWaveRgb
import painters.fft.FlatWave
import painters.fft.FftLine
import painters.fft.FftWave
import painters.fft.FftWaveRgb
import painters.misc.Gradient
import painters.misc.Icon
import painters.modifier.Beat
import painters.modifier.Blend
import painters.modifier.Compose
import painters.modifier.Glitch
import painters.modifier.Move
import painters.modifier.Shake
import painters.modifier.Scale
import painters.waveform.WfmAnalog
import utils.Preset
import utils.VisualizerHelper
import views.VisualizerView
import app.n_zik.android.typography
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.it.fast4x.rimusic.ui.components.themed.SecondaryTextButton
import app.it.fast4x.rimusic.utils.DisposableListener
import app.it.fast4x.rimusic.utils.currentVisualizerKey
import app.it.fast4x.rimusic.utils.getBitmapFromUrl
import app.it.fast4x.rimusic.utils.hasPermission
import app.it.fast4x.rimusic.utils.isCompositionLaunched
import app.it.fast4x.rimusic.utils.rememberPreference

import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.showVisualizerButtonsKey
import app.it.fast4x.rimusic.utils.visualizerEnabledKey
import painters.modifier.Rotate
import app.n_zik.android.thumbnailShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import app.n_zik.android.components.menu.visualizer.VisualizerSettingsMenu
import app.n_zik.android.components.player.extractM3ECoverColors
import app.n_zik.android.components.player.m3eCoverForegroundArgb
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import androidx.compose.foundation.shape.CircleShape
import app.it.fast4x.rimusic.utils.visualizerLineThicknessKey
import app.it.fast4x.rimusic.utils.visualizerWhiteColorOptionKey
import app.it.fast4x.rimusic.utils.visualizerCustomColorKey
import androidx.palette.graphics.Palette

@OptIn(UnstableApi::class)
@Composable
fun NextVisualizer() {

    val context = LocalContext.current
    val visualizerEnabled by rememberPreference(visualizerEnabledKey, false)

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
            onResult = { hasPermission = it }
        )

        if (!hasPermission) {

            LaunchedEffect(Unit, relaunchPermission) { launcher.launch(permission) }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(
                    2.dp,
                    Alignment.CenterVertically
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BasicText(
                    text = stringResource(R.string.require_mic_permission),
                    modifier = Modifier.fillMaxWidth(0.75f),
                    style = typography().xs.semiBold
                )
                Spacer(modifier = Modifier.height(20.dp))
                SecondaryTextButton(
                    text = stringResource(R.string.open_permission_settings),
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

            var bitmapCover by remember { mutableStateOf(APP_ICON_BITMAP) }
            var circleBitmap by remember { mutableStateOf(Icon.getCircledBitmap(APP_ICON_BITMAP)) }
            val visualizerWhiteColorOption by rememberPreference(visualizerWhiteColorOptionKey, enums.VisualizerWhiteColorOption.White)
            val visualizerCustomColor by rememberPreference(visualizerCustomColorKey, android.graphics.Color.WHITE)
            var dominantColor by remember { mutableStateOf(android.graphics.Color.WHITE) }
            
            val isDarkTheme = colorPalette().isDark
            LaunchedEffect(bitmapCover, isDarkTheme) {
                kotlinx.coroutines.withContext(NzikDispatchers.MEDIA) {
                    try {
                        val coverColors = extractM3ECoverColors(bitmapCover, isDarkTheme)
                        if (coverColors != null) {
                            // OQ2=B (spec `spec-fix-palette-extractor-riplay-reference`): the "cover"
                            // color renders the dominant swatch, the same hue the app's dynamic
                            // accent is built from (RiPlay reference).
                            dominantColor = m3eCoverForegroundArgb(coverColors.dominant, !isDarkTheme)
                        } else {
                            val palette = Palette.from(bitmapCover).generate()
                            dominantColor = palette.getDominantColor(android.graphics.Color.WHITE)
                        }
                    } catch (e: Exception) {
                        dominantColor = android.graphics.Color.WHITE
                    }
                }
            }
            
            val accentColor = colorPalette().accent
            val color = remember(visualizerWhiteColorOption, visualizerCustomColor, accentColor, dominantColor) {
                when (visualizerWhiteColorOption) {
                    enums.VisualizerWhiteColorOption.White -> android.graphics.Color.WHITE
                    enums.VisualizerWhiteColorOption.Theme -> accentColor.toArgb()
                    enums.VisualizerWhiteColorOption.Cover -> dominantColor
                    enums.VisualizerWhiteColorOption.Custom -> visualizerCustomColor
                }
            }

            val coroutineScope = rememberCoroutineScope()
            val currentArtworkUri = binder?.player?.currentMediaItem?.mediaMetadata?.artworkUri

            LaunchedEffect(currentArtworkUri) {
                withContext(NzikDispatchers.DATA) {
                    try {
                        val bitmap = getBitmapFromUrl(
                            context,
                            currentArtworkUri.toString().resize(1000, 1000)
                        ) ?: throw Exception("Bitmap is null")
                        val circled = withContext(NzikDispatchers.MEDIA) { Icon.getCircledBitmap(bitmap) }
                        withContext(NzikDispatchers.UI) {
                            bitmapCover = bitmap
                            circleBitmap = circled
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag("NextVisualizer").e("Failed to get bitmap in NextVisualizer ${e.stackTraceToString()}")
                        val circled = withContext(NzikDispatchers.MEDIA) { Icon.getCircledBitmap(APP_ICON_BITMAP) }
                        withContext(NzikDispatchers.UI) {
                            bitmapCover = APP_ICON_BITMAP
                            circleBitmap = circled
                        }
                    }
                }
            }

            binder?.player?.DisposableListener {
                object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        coroutineScope.launch(NzikDispatchers.DATA) {
                            try {
                                val bitmap = getBitmapFromUrl(
                                    context,
                                    mediaItem?.mediaMetadata?.artworkUri.toString()
                                        .resize(1000, 1000)
                                ) ?: throw Exception("Bitmap is null")
                                val circled = withContext(NzikDispatchers.MEDIA) { Icon.getCircledBitmap(bitmap) }
                                withContext(NzikDispatchers.UI) {
                                    bitmapCover = bitmap
                                    circleBitmap = circled
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                val circled = withContext(NzikDispatchers.MEDIA) { Icon.getCircledBitmap(APP_ICON_BITMAP) }
                                withContext(NzikDispatchers.UI) {
                                    bitmapCover = APP_ICON_BITMAP
                                    circleBitmap = circled
                                }
                                Timber.tag("NextVisualizer").e("Failed to get bitmap in NextVisualizer ${e.stackTraceToString()}")
                            }
                        }
                    }
                }
            }
            
            val visualizerLineThickness by rememberPreference(visualizerLineThicknessKey, 6f)
            
            val visualizersList = remember(bitmapCover, circleBitmap, color, visualizerLineThickness) {
                createVisualizersList(bitmapCover, circleBitmap, color, visualizerLineThickness)
            }
            
            var currentVisualizer by rememberPreference(currentVisualizerKey, 0)
            if (currentVisualizer < 0 || currentVisualizer >= visualizersList.size) currentVisualizer = 0

            val showVisualizerButtons by rememberPreference(showVisualizerButtonsKey, true)

            var showControls by remember { mutableStateOf(true) }
            var controlsTimerKey by remember { mutableStateOf(0) }

            LaunchedEffect(controlsTimerKey) {
                showControls = true
                delay(3000)
                showControls = false
            }
            
            val menuState = LocalMenuState.current

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(thumbnailShape()).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { controlsTimerKey++ }
            ) {

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    factory = { visualizerView },
                    update = {
                        // Read states to trigger real-time updates
                        visualizerLineThickness.hashCode()
                        visualizerWhiteColorOption.hashCode()
                        visualizerCustomColor.hashCode()
                        dominantColor.hashCode()
                        bitmapCover.hashCode()

                        it.setup(helper, visualizersList[currentVisualizer])
                    }
                )

                AnimatedVisibility(
                    visible = !showVisualizerButtons || showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Row(
                        horizontalArrangement = if (thumbnailShape() == CircleShape) Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally) else Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(
                                bottom = if (thumbnailShape() == CircleShape) 16.dp else 0.dp
                            )
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                    IconButton(
                        onClick = {
                            controlsTimerKey++
                            if (currentVisualizer > 0) currentVisualizer--
                            else currentVisualizer = visualizersList.lastIndex
                        },
                        icon = R.drawable.arrow_left,
                        color = colorPalette().text,
                        modifier = Modifier.size(32.dp)
                    )
                    
                    BasicText(
                        text = "${currentVisualizer + 1}/${visualizersList.size}",
                        style = typography().xs.semiBold.copy(color = colorPalette().text),
                    )
                    
                    IconButton(
                        onClick = {
                            controlsTimerKey++
                            if (currentVisualizer < visualizersList.lastIndex) currentVisualizer++
                            else currentVisualizer = 0
                        },
                        icon = R.drawable.arrow_right,
                        color = colorPalette().text,
                        modifier = Modifier.size(32.dp)
                    )
                    
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

fun createVisualizersList(background: Bitmap, circleBitmap: Bitmap, color: Int, visualizerLineThickness: Float = 6f): List<Painter> {
    val ampR = 6f
    val ampRfm = 3.3f
    val smoothWfm = 0.4f
    val smooth = 0.6f
    val ampRMin = 2f
    val yR = 0f
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
        // Composition
        Compose(
            WfmAnalog(colorPaint = color, ampR = ampRfm, smooth = smoothWfm).apply { paint.alpha = 150; paint.strokeWidth = visualizerLineThickness },
            Shake(
                Compose(
                    Rotate(FftCWave(colorPaint = color, ampR = ampRMin)),
                    painters.misc.Icon(circleBitmap)
                )
            ).apply {
                animX.duration = 1000
                animY.duration = 2000
            }
        )
    )
}





