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
import androidx.compose.runtime.remember
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
import app.n_zik.android.ui.components.LocalMenuBack
import app.n_zik.android.ui.components.LocalMenuScrollState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.core.navigation.screenPopEnter
import app.n_zik.android.core.navigation.screenPopExit
import app.n_zik.android.enums.TransitionEffect
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.transitionEffectKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppAppearanceMenu(menuState: MenuState, menuSheetState: SheetState) {
    val transitionEffect = rememberPreference(transitionEffectKey, TransitionEffect.Fade).value
    AppearanceMenuSheet(
        showSheet = menuState.isDisplayed,
        onDismissRequest = menuState::hide,
        sheetState = menuSheetState,
    ) {
        AnimatedContent(
            targetState = menuState.presentationState,
            transitionSpec = {
                val returning = (targetState.second?.depth ?: 0) < (initialState.second?.depth ?: 0)
                if (returning) {
                    screenPopEnter(transitionEffect) togetherWith screenPopExit(transitionEffect)
                } else {
                    slideInHorizontally(animationSpec = tween(300)) { width -> width / 2 } +
                        fadeIn(animationSpec = tween(300)) togetherWith
                        slideOutHorizontally(animationSpec = tween(300)) { width -> -width / 2 } +
                        fadeOut(animationSpec = tween(300))
                }
            },
            label = "MenuContentTransition"
        ) { target ->
            BackHandler(enabled = menuState.isDisplayed && menuState.canGoBack) {
                menuState.pop()
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                target.second?.let { entry ->
                    val back = remember(menuState, entry) {
                        if (entry.hasPrevious || entry.onReturn != null) {
                            {
                                if (menuState.isDisplayed && menuState.presentationState.second === entry) {
                                    menuState.pop()
                                }
                            }
                        } else null
                    }
                    CompositionLocalProvider(
                        LocalMenuScrollState provides entry.scrollState,
                        LocalMenuBack provides back,
                    ) {
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
