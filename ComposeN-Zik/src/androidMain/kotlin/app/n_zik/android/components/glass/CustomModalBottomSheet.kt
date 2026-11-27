package app.n_zik.android.components.glass

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

import app.n_zik.android.utils.hideStatusBarKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.menu.ContentMenuSheetDragHandle
import app.n_zik.android.components.menu.LocalMenuSheetHandleActions
import app.n_zik.android.components.menu.MenuSheetDragHandle
import app.n_zik.android.components.menu.MenuSheetHandleActions
import app.n_zik.android.components.menu.NativeMenuSheetDragHandle
import app.n_zik.android.components.menu.menuSheetHandleOverlayIdentity
import app.n_zik.android.components.menu.rememberNativeMenuSheetDismiss
import app.n_zik.android.components.menu.straightBottomMenuShape
import app.n_zik.android.surfaceColor

import kotlinx.coroutines.flow.first

/** Hosts menu content with straight lower corners in native and shared windows. */
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
        MenuSheetDragHandle()
    },
    contentWindowInsets: @Composable () -> WindowInsets = { WindowInsets.ime },
    useSharedHost: Boolean = true,
    expandOnShow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetShape = remember(shape) { straightBottomMenuShape(shape) }
    val useContentHandleActions = dragHandle == null
    // Resolve the owner before the sheet content moves into the shared overlay host.
    val sheetContainerColor =
        if (containerColor == Color.Transparent) containerColor else surfaceColor(containerColor)
    val glass = rememberGlassSettings()
    val glassSheet = glass.usesGlassMaterial(GlassArea.Menus) && containerColor != Color.Transparent
    val host = LocalGlassMenuHost.current
    val sameWindow = host?.dialogs?.windowRoot === LocalView.current.rootView
    if (useSharedHost && canHostGlassMenuSheet(glass, glassRenderer(glass),
            sameWindow && currentGlassOverlayBackdrop() != null)) {
        SharedGlassMenuSheet(
            showSheet = showSheet,
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            scrimColor = scrimColor,
            shape = sheetShape,
            partiallyExpanded = expandOnShow,
            expandOnShow = expandOnShow,
            contentWindowInsets = contentWindowInsets,
            useContentHandleActions = useContentHandleActions,
        ) {
            if (glassSheet) GlassMaterialContainerColumn(modifier.fillMaxWidth().glassOverlayPaint(glass.forArea(GlassArea.Menus).film),
                materialSource = LocalGlassBackdrop.current,
                foregroundFill = glassFilmColor(glass.forArea(GlassArea.Menus), sheetContainerColor),
                material = { exported -> Modifier.glassMaterial(
                    glass.forArea(GlassArea.Menus), sheetShape, menu = true, filmColor = sheetContainerColor,
                    exportedBackdrop = exported,
                ) },
            ) {
                val foreground = if (contentColor.isSpecified) glassPaneForeground(contentColor, surfaceBackdropColor(),
                    TextContrast, LocalGlassInkDarkness.current ?: glassSurfaceDarkness(glass)) else LocalContentColor.current
                CompositionLocalProvider(LocalContentColor provides foreground) {
                    dragHandle?.let { ContentMenuSheetDragHandle(it) }
                    content()
                }
            } else Column(modifier.fillMaxWidth()) {
                CompositionLocalProvider(
                    LocalContentColor provides contentColor,
                    LocalGlassContainerBackdrop provides LocalGlassBackdrop.current,
                    LocalGlassMaterialSource provides LocalGlassBackdrop.current,
                ) {
                    dragHandle?.let { ContentMenuSheetDragHandle(it) }
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
        // Native windows retain transparent glass children because they cannot sample the page.
        val nativeBackdrop = rememberCanvasBackdrop { if (!glassSheet) drawRect(sheetContainerColor) }
        val nativeScrim = nativeGlassSheetScrimColor(glass, scrimColor, BottomSheetDefaults.ScrimColor)
        val nativeUnderlay = effectiveSurfaceColor(surfaceBackdropColor(), Color.Transparent, nativeScrim)
        val scheme = MaterialTheme.colorScheme
        val nativeFill = if (sheetContainerColor == scheme.surface) scheme.surfaceColorAtElevation(tonalElevation)
            else sheetContainerColor
        val foregroundFill = if (glassSheet) glassFilmColor(glass.forArea(GlassArea.Menus), sheetContainerColor) else nativeFill
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            shape = sheetShape,
            containerColor = if (glassSheet) Color.Transparent else sheetContainerColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            scrimColor = nativeScrim,
            dragHandle = null,
            contentWindowInsets = { WindowInsets(0) },
        ) {
            if (expandOnShow) {
                LaunchedEffect(sheetState) {
                    snapshotFlow { sheetState.targetValue }.first { it != SheetValue.Hidden }
                    sheetState.expand()
                }
            }
            CompositionLocalProvider(LocalGlassOverlayPaintScope provides host?.let {
                GlassOverlayPaintScope(it, overlayId) {
                    if (sheetState.currentValue == SheetValue.Expanded && sheetState.targetValue == SheetValue.Expanded) 1f else 0f
                }
            }) {
            val contentHandleActions = if (useContentHandleActions) {
                val dismissSheet = rememberNativeMenuSheetDismiss(sheetState, onDismissRequest)
                MenuSheetHandleActions(menuSheetHandleOverlayIdentity(), sheetState, dismissSheet)
            } else null
            // The native host translates its content after the caller's modifier.
            // Keep the material inside that translation and outside its content insets.
            Column(Modifier.fillMaxWidth()
                .glassOverlayPaint(if (glassSheet) glass.forArea(GlassArea.Menus).film else sheetContainerColor.alpha).then(
                if (glassSheet) Modifier.glassMaterial(
                    glass.forArea(GlassArea.Menus), sheetShape, separateWindow = true, menu = true,
                    filmColor = sheetContainerColor,
                ) else if (containerColor != Color.Transparent) Modifier.glassAreaContour(glass, GlassArea.Menus, sheetShape)
                else Modifier,
            ).windowInsetsPadding(contentWindowInsets())) {
                val view = LocalView.current
                (view.parent as? DialogWindowProvider)?.window?.let { window ->
                    val hideStatusBar by rememberPreference(hideStatusBarKey, false)
                    val lightNavigation = neutralForeground(effectiveSurfaceColor(nativeUnderlay, foregroundFill)) == Color.Black
                    val lightStatus = neutralForeground(nativeUnderlay) == Color.Black
                    DisposableEffect(window, lightNavigation, lightStatus, hideStatusBar) {

                        val applyInsets = {
                            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                window.statusBarColor = android.graphics.Color.TRANSPARENT
                                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                            }

                            val insetsController = WindowCompat.getInsetsController(window, view)
                            insetsController.isAppearanceLightNavigationBars = lightNavigation
                            insetsController.isAppearanceLightStatusBars = lightStatus

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
                    LocalGlassOpticalUnderlayCovered provides (host != null && host.overlaysAbove(overlayId).isNotEmpty()),
                    LocalGlassOpticsTransitionProgress provides FullGlassOpticsProgress,
                    LocalGlassLiveFrame provides rememberGlassModalLiveFrameIfEnabled(covered = host != null && host.nativeOverlays.lastOrNull() !== overlayId),
                    LocalGlassUnderlayCovered provides (host?.visuallyCovered(overlayId) == true),
                    LocalGlassContainerBackdrop provides nativeBackdrop,
                    LocalGlassMaterialSource provides nativeBackdrop,
                    LocalGlassBackdrop provides null,
                    LocalSurfaceBackdropColor provides nativeUnderlay,
                    LocalGlassOverlayBackdrop provides null,
                    LocalMenuSheetHandleActions provides contentHandleActions,
                ) {
                    GlassRippleProvider(glass) {
                        Column(Modifier.fillMaxWidth()) {
                            if (containerColor != Color.Transparent) {
                                val darkness = if (glassSheet) glassSurfaceDarkness(glass.forArea(GlassArea.Menus)) else null
                                SurfaceForegroundContent(effectiveSurfaceColor(nativeUnderlay, foregroundFill), glassInkDarkness = darkness) {
                                    dragHandle?.let { NativeMenuSheetDragHandle(sheetState, onDismissRequest, it) }
                                    content()
                                }
                            } else {
                                dragHandle?.let { NativeMenuSheetDragHandle(sheetState, onDismissRequest, it) }
                                content()
                            }
                        }
                    }
                }
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
