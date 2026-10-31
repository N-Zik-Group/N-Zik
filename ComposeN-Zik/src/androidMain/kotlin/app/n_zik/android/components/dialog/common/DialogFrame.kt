package app.n_zik.android.components.dialog.common

import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassAreaContour
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.GlassDialogWindow
import app.n_zik.android.components.glass.LocalGlassMenuHost
import app.n_zik.android.components.glass.canHostGlassDialog
import app.n_zik.android.components.glass.glassRenderer
import app.n_zik.android.components.glass.GlassMaterialContainerColumn

/** Shared dialog surface with bounded width and height. */
@Composable
fun DialogFrame(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    bodyPadding: PaddingValues = PaddingValues(24.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    if (LocalDialogPresentation.current == null) {
        DialogPresence(visible = true) {
            DialogFrame(onDismiss, modifier, title, horizontalAlignment, footer, bodyPadding, content)
        }
        return
    }
    val presentation = requireNotNull(LocalDialogPresentation.current)
    val interactive = presentation.interactive
    val cardBounds = remember { mutableStateOf(Rect.Zero) }
    val glass = rememberGlassSettings()
    val glassHost = LocalGlassMenuHost.current
    val sameWindow = glassHost?.dialogs?.windowRoot === LocalView.current.rootView
    val useGlass = canHostGlassDialog(glass, glassRenderer(glass), sameWindow && glassHost?.modalBackdrop != null)
    val shape = uiRoundnessShape()
    val surface = surfaceColor(colorPalette().background1)
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val maxWidth =
        if (isLandscape) Dialog.MAX_WIDTH_LANDSCAPE else Dialog.MAX_WIDTH_PORTRAIT
    val maxHeight =
        if (isLandscape) Dialog.MAX_HEIGHT_LANDSCAPE else Dialog.MAX_HEIGHT_PORTRAIT

    GlassDialogWindow(
        useSharedHost = useGlass,
        onDismissRequest = { if (presentation.interactive) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = interactive,
            dismissOnClickOutside = interactive
        )
    ) {
        if (!useGlass) NativeDialogDimming(presentation)
        val focusManager = LocalFocusManager.current
        LaunchedEffect(interactive) { if (!interactive) focusManager.clearFocus(force = true) }
        CompositionLocalProvider(
            LocalDialogPresentation provides null,
            LocalDialogInput provides presentation
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().testTag("dialogFrameViewport")
                    .onPreviewKeyEvent { !presentation.interactive }
                    .pointerInput(onDismiss) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            if (!cardBounds.value.contains(down.position)) {
                                down.consume()
                                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                                up?.consume()
                                if (up != null && presentation.interactive) onDismiss()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = modifier
                        .testTag("dialogFrameCard")
                        .onGloballyPositioned { cardBounds.value = it.boundsInParent() }
                        .then(if (interactive) Modifier else Modifier
                            .clearAndSetSemantics { }
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                    }
                                }
                            })
                        .wrapContentSize()
                        .sizeIn(
                            maxWidth = screenWidthDp * maxWidth,
                            maxHeight = screenHeightDp * maxHeight
                        )
                        // Tight width prevents marquee children from repeatedly changing the window size.
                        .width(screenWidthDp * maxWidth)
                        .padding(16.dp)
                        .graphicsLayer {
                            val motion = presentation.motion.value
                            alpha = dialogFadeAlpha(motion)
                            scaleX = dialogScale(motion)
                            scaleY = scaleX
                        }
                        .then(if (useGlass) Modifier else Modifier.glassAreaContour(glass, GlassArea.Dialogs, shape)),
                    shape = shape,
                    colors = CardDefaults.cardColors(
                        // Elevated surface: dark gray in pitch black instead of pure black
                        containerColor = if (useGlass) Color.Transparent else surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (useGlass) 0.dp else 8.dp)
                ) {
                    GlassMaterialContainerColumn(
                        modifier = Modifier.fillMaxWidth(),
                        sectionFilm = if (useGlass) null else surface,
                        material = if (useGlass) ({ exported -> Modifier.glassMaterial(
                            glass.forArea(GlassArea.Dialogs), shape, menu = true, filmColor = surface,
                            excludeBottomRim = false,
                            exportedBackdrop = exported,
                        ) }) else null,
                    ) {
                        title?.let {
                            Column(
                                modifier = Modifier
                                    .testTag("dialogFrameHeader")
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                BasicText(
                                    text = it,
                                    modifier = Modifier.fillMaxWidth().testTag("dialogFrameTitle"),
                                    style = typography().l.semiBold.copy(
                                        color = colorPalette().text,
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .testTag("dialogFrameBody")
                                .clipToBounds()
                                .fillMaxWidth()
                                .padding(bodyPadding),
                            horizontalAlignment = horizontalAlignment,
                            content = content
                        )
                        footer?.let {
                            Column(
                                modifier = Modifier
                                    .testTag("dialogFrameFooter")
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                                horizontalAlignment = horizontalAlignment,
                                content = it
                            )
                        }
                    }
                }
            }
        }
    }
}
