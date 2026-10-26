package app.it.fast4x.rimusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor
import timber.log.Timber
import app.it.fast4x.rimusic.utils.hideStatusBarKey
import androidx.core.view.WindowInsetsCompat
import androidx.core.graphics.ColorUtils
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowInsetsControllerCompat
import android.view.View
import android.view.WindowManager
import android.os.Build

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
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
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
    var isComposing by remember { mutableStateOf(showSheet) }

    LaunchedEffect(showSheet) {
        if (showSheet) {
            isComposing = true
        } else {
            sheetState.hide()
            isComposing = false
        }
    }

    // Pitch black centralization: an opaque sheet gets the elevated surface
    // color (dark gray), the same treatment as the mini player; a transparent
    // sheet draws its own background and stays untouched.
    val sheetContainerColor =
        if (containerColor == Color.Transparent) containerColor else surfaceColor(containerColor)

    if (isComposing) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            shape = shape,
            containerColor = sheetContainerColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            scrimColor = scrimColor,
            dragHandle = dragHandle,
            contentWindowInsets = contentWindowInsets
        ) {
            val colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)
            val isPicthBlack = colorPaletteMode == ColorPaletteMode.PitchBlack
            val isDark =
                colorPaletteMode == ColorPaletteMode.Dark || isPicthBlack || (colorPaletteMode == ColorPaletteMode.System && isSystemInDarkTheme())

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


                content()
            }
        }
    }
}


