package app.n_zik.android.components.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassDialogOverlayHost
import app.n_zik.android.components.glass.GlassMenuHostState
import app.n_zik.android.components.glass.GlassOverlayHost
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassToastHost
import app.n_zik.android.components.glass.GlassToastOverlayScene
import app.n_zik.android.components.glass.glassContentCaptureEnabled
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.theme.AppearanceMenuSheet
import app.n_zik.android.ui.components.LocalMenuScrollState
import app.n_zik.android.ui.components.MenuState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppAppearanceMenu(menuState: MenuState, menuSheetState: SheetState) {
    AppearanceMenuSheet(
        showSheet = menuState.isDisplayed,
        onDismissRequest = menuState::hide,
        sheetState = menuSheetState,
    ) {
        AnimatedContent(
            targetState = menuState.presentationState,
            transitionSpec = {
                slideInHorizontally(animationSpec = tween(300)) { width -> width / 2 } + fadeIn(animationSpec = tween(300)) togetherWith
                slideOutHorizontally(animationSpec = tween(300)) { width -> -width / 2 } + fadeOut(animationSpec = tween(300))
            },
            label = "MenuContentTransition"
        ) { target ->
            BackHandler(enabled = menuState.hasPrevious) {
                menuState.pop()
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                target.second?.let { entry ->
                    CompositionLocalProvider(LocalMenuScrollState provides entry.scrollState) {
                        entry.content()
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppGlassOverlayScene(
    host: GlassMenuHostState,
    toastNeedsLiveBackdrop: Boolean,
    onToastPresenceChanged: (Boolean) -> Unit,
    windowsInsets: WindowInsets,
) {
    val toastGlass = rememberGlassSettings(GlassScope.Toast)
    GlassToastOverlayScene(
        backdrop = host.modalBackdrop,
        captureOverlays = toastNeedsLiveBackdrop &&
            toastGlass.usesGlassMaterial(GlassArea.Menus) &&
            glassContentCaptureEnabled(toastGlass) &&
            (host.dialogs.hasDialogs || host.entry != null),
        overlays = {
            GlassDialogOverlayHost(host)
            // Draw anchored glass menus above both navigation and the player.
            GlassOverlayHost(host)
        },
    ) { toastBackdrop ->
        GlassToastHost(
            backdrop = toastBackdrop,
            onPresenceChanged = onToastPresenceChanged,
            bottomInset = with(LocalDensity.current) {
                windowsInsets.getBottom(this).toDp()
            },
        )
    }
}
