package app.n_zik.android.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor
import app.it.fast4x.rimusic.utils.hideStatusBarKey
import androidx.core.view.WindowInsetsCompat
import androidx.core.graphics.ColorUtils
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowInsetsControllerCompat
import android.view.View
import android.view.WindowManager
import android.os.Build
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomModalBottomSheet(
    showSheet: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    ),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = BottomSheetDefaults.Elevation,
    scrimColor: Color = Color.Unspecified,
    dragHandle: @Composable (() -> Unit)? = {
        Box(
            modifier = Modifier
                .padding(top = 18.dp, bottom = 6.dp)
                .size(width = 40.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White)
        )
    },
    contentWindowInsets: @Composable () -> WindowInsets = { WindowInsets.ime },
    content: @Composable ColumnScope.() -> Unit,
) {
    // Resolve the owner before the sheet content moves into the shared overlay host.
    val sheetContainerColor =
        if (containerColor == Color.Transparent) containerColor else surfaceColor(containerColor)
    val glass = rememberGlassSettings()
    val glassSheet = glass.usesGlassMaterial(GlassArea.Menus) && containerColor != Color.Transparent
    val host = LocalGlassMenuHost.current
    val sameWindow = host?.dialogs?.windowRoot === LocalView.current.rootView
    if (canHostGlassMenuSheet(glass, glassRenderer(glass),
            sameWindow && currentGlassOverlayBackdrop() != null)) {
        SharedGlassMenuSheet(
            showSheet = showSheet,
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            scrimColor = scrimColor,
            shape = shape,
            contentWindowInsets = contentWindowInsets,
        ) {
            if (glassSheet) GlassMaterialContainerColumn(modifier.fillMaxWidth(),
                material = { exported -> Modifier.glassMaterial(
                    glass.forArea(GlassArea.Menus), shape, menu = true, filmColor = sheetContainerColor,
                    exportedBackdrop = exported,
                ) },
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    dragHandle?.invoke()
                    content()
                }
            } else Column(modifier.fillMaxWidth()) {
                CompositionLocalProvider(
                    LocalContentColor provides contentColor,
                    LocalGlassContainerBackdrop provides LocalGlassBackdrop.current,
                ) {
                    dragHandle?.invoke()
                    content()
                }
            }
        }
        return
    }
    var isComposing by remember { mutableStateOf(showSheet) }

    LaunchedEffect(showSheet) {
        if (showSheet) {
            isComposing = true
        } else {
            sheetState.hide()
            isComposing = false
        }
    }

    if (isComposing) {
        val overlayId = remember { Any() }
        NativeGlassOverlayCoverage(host, overlayId)
        val nativeBackdrop = rememberCanvasBackdrop { drawRect(sheetContainerColor) }
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = if (glassSheet) modifier.glassMaterial(
                glass.forArea(GlassArea.Menus), shape, separateWindow = true, menu = true,
                filmColor = sheetContainerColor,
            )
                else if (containerColor != Color.Transparent) modifier.glassAreaContour(glass, GlassArea.Menus, shape) else modifier,
            sheetState = sheetState,
            shape = shape,
            containerColor = if (glassSheet) Color.Transparent else sheetContainerColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            scrimColor = nativeGlassSheetScrimColor(glass, scrimColor, BottomSheetDefaults.ScrimColor),
            dragHandle = dragHandle,
            contentWindowInsets = contentWindowInsets
        ) {
            Column {

                val view = LocalView.current
                val colorPalette = colorPalette()
                (view.parent as? DialogWindowProvider)?.window?.let { window ->
                    val hideStatusBar by rememberPreference(hideStatusBarKey, false)
                    DisposableEffect(window, sheetContainerColor, colorPalette, hideStatusBar) {
                        val luminance = ColorUtils.calculateLuminance(sheetContainerColor.toArgb())
                        val isLightBackground = luminance > 0.5 

                        val applyInsets = {
                            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                window.statusBarColor = android.graphics.Color.TRANSPARENT
                                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                            }

                            val insetsController = WindowCompat.getInsetsController(window, view)
                            insetsController.isAppearanceLightNavigationBars = isLightBackground
                            insetsController.isAppearanceLightStatusBars = isLightBackground
                            
                            if (hideStatusBar) {
                                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                insetsController.hide(WindowInsetsCompat.Type.statusBars())
                            } else {
                                insetsController.show(WindowInsetsCompat.Type.statusBars())
                            }
                        }

                        if (view.isAttachedToWindow) {
                            applyInsets()
                            view.post { applyInsets() }
                        }

                        val listener = object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: View) {
                                applyInsets()
                                view.post { applyInsets() }
                            }
                            override fun onViewDetachedFromWindow(v: View) {}
                        }
                        view.addOnAttachStateChangeListener(listener)

                        onDispose {
                            view.removeOnAttachStateChangeListener(listener)
                        }
                    }
                }


                CompositionLocalProvider(
                    LocalGlassOpticsEnabled provides true,
                    LocalGlassOpticsTransitionProgress provides 1f,
                    LocalGlassUnderlayCovered provides (host != null && host.nativeOverlays.lastOrNull() !== overlayId),
                    LocalGlassContainerBackdrop provides nativeBackdrop,
                ) {
                    GlassRippleProvider(glass) { content() }
                }
            }
        }
    }
}



/** Material dimming remains user-controlled even when the renderer needs a native window. */
internal fun nativeGlassSheetScrimColor(settings: GlassSettings, scrimOverride: Color, native: Color): Color =
    if (scrimOverride.isSpecified) scrimOverride
    else if (settings.usesGlassMaterial(GlassArea.Menus)) Color.Black.copy(alpha = settings.menuDimming)
    else native
