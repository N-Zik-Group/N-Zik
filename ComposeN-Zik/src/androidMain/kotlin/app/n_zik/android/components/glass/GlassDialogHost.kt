package app.n_zik.android.components.glass

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.window.DialogProperties
import app.n_zik.android.components.dialog.common.animateDialogMotion
import app.n_zik.android.components.dialog.common.NativeDialogDimming
import app.n_zik.android.components.dialog.common.LocalDialogPresentation
import app.n_zik.android.components.dialog.common.dialogFadeAlpha
import app.n_zik.android.components.dialog.common.dialogScale
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCombinedBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop

@Stable
internal class GlassDialogEntry(
    val id: Any,
    val locals: CompositionLocalContext,
    val onDismiss: () -> Unit,
    val properties: () -> DialogProperties,
    val edgeToEdge: () -> Boolean,
    val content: @Composable () -> Unit,
    val backdrop: () -> Backdrop?,
    val dimmingAlpha: () -> Float,
    val scrimProgress: () -> Float,
    val scrimColor: () -> Color = { Color.Black },
) {
    internal val exposure = Animatable(1f)
}

/** Dialogs are registered by their owners and drawn outside the captured page. */
@Stable
class GlassDialogHostState {
    internal val entries = mutableStateListOf<GlassDialogEntry>()
    internal var windowRoot by mutableStateOf<View?>(null)
    val hasDialogs: Boolean get() = entries.isNotEmpty()

    internal fun add(entry: GlassDialogEntry) { entries.add(entry) }
    internal fun remove(id: Any) { entries.removeAll { it.id == id } }
}

internal fun canHostGlassDialog(settings: GlassSettings, renderer: GlassRenderer, hasBackdrop: Boolean): Boolean =
    settings.usesGlassMaterial(GlassArea.Dialogs) && renderer != GlassRenderer.Tint && hasBackdrop

internal fun canHostGlassMenuSheet(settings: GlassSettings, renderer: GlassRenderer, hasBackdrop: Boolean): Boolean =
    settings.usesGlassMaterial(GlassArea.Menus) && renderer != GlassRenderer.Tint && hasBackdrop

/** Uses the Android window whenever the shared host cannot sample a compatible backdrop. */
@Composable
internal fun GlassDialogWindow(
    useSharedHost: Boolean,
    onDismissRequest: () -> Unit,
    properties: DialogProperties,
    edgeToEdge: Boolean = false,
    area: GlassArea = GlassArea.Dialogs,
    scrimVisible: Boolean = true,
    scrimColor: Color = Color.Unspecified,
    scrimAnimation: Animatable<Float, AnimationVector1D>? = null,
    content: @Composable () -> Unit,
) {
    val host = LocalGlassMenuHost.current
    val presentation = LocalDialogPresentation.current
    val settings = rememberGlassSettings()
    if (!useSharedHost || host == null || properties.securePolicy != SecureFlagPolicy.Inherit) {
        val id = remember { Any() }
        NativeGlassOverlayCoverage(host, id)
        Dialog(onDismissRequest, properties = properties) {
            CompositionLocalProvider(
                LocalGlassOpticsEnabled provides true,
                LocalGlassOpticsTransitionProgress provides 1f,
                LocalGlassUnderlayCovered provides (host != null && host.nativeOverlays.lastOrNull() !== id),
            ) {
                if (presentation == null || (useSharedHost && host != null && properties.securePolicy != SecureFlagPolicy.Inherit)) {
                    NativeDialogDimming(presentation, area, scrimColor, scrimVisible)
                }
                GlassRippleProvider(settings) { content() }
            }
        }
        return
    }
    val id = remember { Any() }
    val currentDimming = rememberUpdatedState(if (scrimColor.isSpecified) 1f
        else if (area == GlassArea.Menus) settings.menuDimming else settings.dialogDimming)
    val currentScrimColor = rememberUpdatedState(if (scrimColor.isSpecified) scrimColor else Color.Black)
    val scrimMotion = scrimAnimation ?: remember { Animatable(0f) }
    val motionAllowed = isGlassMotionAllowed()
    LaunchedEffect(presentation, scrimVisible, motionAllowed) {
        if (presentation == null) {
            val target = if (scrimVisible) 1f else 0f
            if (motionAllowed) animateDialogMotion(scrimMotion, target) else scrimMotion.snapTo(target)
        }
    }
    val currentScrimProgress = rememberUpdatedState<() -> Float> {
        presentation?.motion?.value ?: scrimMotion.value
    }
    val locals = currentCompositionLocalContext
    val currentContent = rememberUpdatedState(content)
    val currentDismiss = rememberUpdatedState(onDismissRequest)
    val currentProperties = rememberUpdatedState(properties)
    val currentEdgeToEdge = rememberUpdatedState(edgeToEdge)
    val currentBackdrop = rememberUpdatedState(currentGlassOverlayBackdrop())
    val dropdownOwner = LocalGlassDropdownBackdropOwner.current
    DisposableEffect(host, id, locals) {
        host.dialogs.add(GlassDialogEntry(id, locals, { currentDismiss.value() },
            { currentProperties.value }, { currentEdgeToEdge.value }, { currentContent.value() }, {
                if (dropdownOwner != null) dropdownOwner.resolve(currentBackdrop.value) else currentBackdrop.value
            }, { currentDimming.value }, { currentScrimProgress.value() }, { currentScrimColor.value }))
        onDispose { host.dialogs.remove(id) }
    }
}

/** Removes covered content from accessibility and keyboard traversal while keeping it drawn. */
@Composable
fun GlassModalContent(
    host: GlassMenuHostState,
    manageFocus: Boolean = true,
    focusSessionKey: Any? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val blocked = host.hasOverlays
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var previouslyBlocked by remember { mutableStateOf(false) }
    var blockedSession by remember { mutableStateOf(focusSessionKey) }
    var savedPageFocus by remember { mutableStateOf(false) }
    LaunchedEffect(blocked, manageFocus, focusSessionKey) {
        if (blocked && !previouslyBlocked) {
            blockedSession = focusSessionKey
            // Save before the modal takes focus, never during its disposal handoff.
            savedPageFocus = manageFocus && focus.saveFocusedChild()
        }
        if (manageFocus && previouslyBlocked && !blocked) {
            val restored = savedPageFocus && blockedSession == focusSessionKey && focus.restoreFocusedChild()
            // Removing the modal can automatically focus the page's first input.
            if (!restored) focusManager.clearFocus(force = true)
        }
        previouslyBlocked = blocked
    }
    val covered = LocalGlassUnderlayCovered.current || blocked
    CompositionLocalProvider(LocalGlassUnderlayCovered provides covered) {
        Box(Modifier.fillMaxSize().blockGlassCoveredInput(blocked)
            .then(if (manageFocus) Modifier
                .then(if (blocked) Modifier.clearAndSetSemantics { } else Modifier)
                .focusRequester(focus)
                .focusProperties {
                    onEnter = {
                        if (blocked || (previouslyBlocked &&
                                (!savedPageFocus || blockedSession != focusSessionKey)) ||
                            (savedPageFocus && blockedSession == focusSessionKey && focus.restoreFocusedChild())) {
                            cancelFocusChange()
                        }
                    }
                }
                .focusGroup() else Modifier), content = content)
    }
}

/** The top dialog owns input; retained lower dialogs remain visible for nested dismissal. */
@Composable
fun GlassDialogOverlayHost(host: GlassMenuHostState) {
    val view = LocalView.current
    SideEffect { host.dialogs.windowRoot = view.rootView }
    DisposableEffect(host) { onDispose { host.dialogs.windowRoot = null } }
    host.dialogs.entries.forEach { entry ->
        key(entry.id) {
            val source = entry.backdrop() ?: host.modalBackdrop
            val dimmingAlpha = entry.dimmingAlpha() * entry.scrimProgress().coerceIn(0f, 1f)
            val scrimColor = entry.scrimColor()
            val dimmedSource = rememberGlassDimmedBackdrop(source, dimmingAlpha, scrimColor)
            val visibleDialog = rememberLayerBackdrop()
            val completedBackdrop = if (source != null) rememberCombinedBackdrop(source, visibleDialog) else visibleDialog
            val topmost = entry === host.dialogs.entries.lastOrNull() && host.entry?.visible != true
            val properties = entry.properties()
            val focus = remember { FocusRequester() }
            LaunchedEffect(topmost) { if (topmost) focus.requestFocus() }
            LaunchedEffect(topmost) { animateDialogMotion(entry.exposure, if (topmost) 1f else 0f) }
            BackHandler(enabled = topmost) {
                if (properties.dismissOnBackPress) entry.onDismiss()
            }
            CompositionLocalProvider(entry.locals) {
                CompositionLocalProvider(
                    LocalGlassOpticsEnabled provides true,
                    LocalGlassOpticsTransitionProgress provides 1f,
                    LocalGlassUnderlayCovered provides (
                        entry !== host.dialogs.entries.lastOrNull() || host.entry != null || host.nativeOverlays.isNotEmpty()
                    ),
                    LocalGlassBackdrop provides dimmedSource,
                    LocalGlassOverlayBackdrop provides completedBackdrop,
                    LocalGlassDropdownBackdropOwner provides null,
                ) {
                    Box(Modifier.fillMaxSize().layerBackdrop(visibleDialog)
                        .testTag("glassDialogOverlay")
                        .blockGlassCoveredInput(!topmost)
                        .onPreviewKeyEvent { !topmost }
                        .then(if (topmost) Modifier.semantics { dialog() }
                            else Modifier.clearAndSetSemantics { })
                        .focusRequester(focus)
                        .focusProperties {
                            onEnter = {
                                if (!topmost || focus.restoreFocusedChild()) cancelFocusChange()
                            }
                            onExit = {
                                focus.saveFocusedChild()
                                if (topmost) cancelFocusChange()
                            }
                        }
                        .focusGroup()) {
                        Box(Modifier.matchParentSize()
                            .testTag("glassDialogScrim")
                            .glassScrimInput()
                            .background(scrimColor.copy(alpha = scrimColor.alpha * dimmingAlpha))
                            .clickable(indication = null,
                                interactionSource = remember { MutableInteractionSource() }) {
                                if (topmost && properties.dismissOnClickOutside) entry.onDismiss()
                            }.clearAndSetSemantics { })
                        Box(Modifier.fillMaxSize()
                            .then(if (entry.edgeToEdge()) Modifier else Modifier.safeDrawingPadding())
                            .imePadding()
                            .graphicsLayer {
                                alpha = dialogFadeAlpha(entry.exposure.value)
                                scaleX = dialogScale(entry.exposure.value)
                                scaleY = scaleX
                            },
                            contentAlignment = Alignment.Center) {
                            entry.content()
                        }
                    }
                }
            }
        }
    }
}

/** Native windows still reduce the activity's optics until their animated content is disposed. */
@Composable
internal fun NativeGlassOverlayCoverage(host: GlassMenuHostState?, id: Any) {
    DisposableEffect(host, id) {
        host?.nativeOverlays?.add(id)
        onDispose { host?.nativeOverlays?.remove(id) }
    }
}
