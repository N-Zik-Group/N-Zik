package app.it.fast4x.rimusic.ui.components

import app.n_zik.android.components.glass.glassGlobalIndication

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import app.n_zik.android.components.ui.UiSmallFloatingActionButton as SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.utils.multiFloatActionIconOffsetXkey
import app.it.fast4x.rimusic.utils.multiFloatActionIconOffsetYkey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassFloatingActionIcon
import app.n_zik.android.components.glass.glassNativeActionContour
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.navigation.FloatingMultiActionSize
import app.n_zik.android.components.navigation.movableFloatingAction
import app.n_zik.android.surfaceColor
import app.n_zik.android.uiRoundnessShape

enum class MultiFabState {
    Collapsed, Expanded
}
class FabItem(
    val icon: Painter,
    val label: String,
    val onFabItemClicked: () -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MultiFloatingActionsButton (
    useAsActionsMenu: Boolean = false,
    fabIcon: Painter,
    items: List<FabItem>,
    showLabels: Boolean = true,
    onStateChanged: ((state: MultiFabState) -> Unit)? = null,
    onClick: () -> Unit
) {
    val glassSettings = rememberGlassSettings()
    val glassFloating = glassSettings.usesGlassMaterial(GlassArea.ActionButtons)
    var currentState by remember { mutableStateOf(MultiFabState.Collapsed) }
    val stateTransition: Transition<MultiFabState> =
        updateTransition(targetState = currentState, label = "")
    val stateChange: () -> Unit = {
        currentState = if (stateTransition.currentState == MultiFabState.Expanded) {
            MultiFabState.Collapsed
        } else MultiFabState.Expanded
        onStateChanged?.invoke(currentState)
    }
    val rotation: Float by stateTransition.animateFloat(
        transitionSpec = {
            if (targetState == MultiFabState.Expanded) {
                spring(stiffness = Spring.StiffnessLow)
            } else {
                spring(stiffness = Spring.StiffnessMedium)
            }
        },
        label = ""
    ) { state ->
        if (state == MultiFabState.Expanded) 45f else 0f
    }
    val isEnable = currentState == MultiFabState.Expanded
    val mainClick: () -> Unit = {
        if (!useAsActionsMenu) {
            if (currentState == MultiFabState.Collapsed) onClick() else stateChange()
        } else stateChange()
    }

    BackHandler(isEnable) {
        currentState = MultiFabState.Collapsed
    }

    val modifier = if (currentState == MultiFabState.Expanded)
        Modifier
            .fillMaxSize()
            .clip(uiRoundnessShape()).clickable(indication = glassGlobalIndication(null),
                interactionSource = remember { MutableInteractionSource() }) {
                currentState = MultiFabState.Collapsed
            } else Modifier.fillMaxSize()


    val offsetX = rememberPreference(multiFloatActionIconOffsetXkey, 0F )
    val offsetY = rememberPreference(multiFloatActionIconOffsetYkey, 0F )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            if (currentState == MultiFabState.Expanded) {
                val color = colorPalette().favoritesIcon.copy(0.85f)
                Canvas(modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 2.2f
                        scaleY = 2.1f
                    }) {
                    translate(150f, top = 300f) {
                        scale(5f) {}
                        drawCircle( color, radius = 200.dp.toPx() )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Bottom,
            ) {
                items.forEach { item ->
                    SmallFloatingActionButtonRow(
                        item = item,
                        stateTransition = stateTransition,
                        showLabel = showLabels
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                Box(
                    modifier = Modifier
                        .movableFloatingAction(offsetX, offsetY)
                        .then(if (glassFloating) Modifier.size(FloatingMultiActionSize) else Modifier.glassNativeActionContour(glassSettings).clip(uiRoundnessShape())
                        .background(surfaceColor(colorPalette().background2))
                        .height(FloatingMultiActionSize)
                        .width(FloatingMultiActionSize)
                        .clip(uiRoundnessShape()).combinedClickable(
                            onClick = mainClick,
                        ))


                ) {
                    if (!GlassFloatingActionIcon(
                            icon = if (!useAsActionsMenu) fabIcon else painterResource(R.drawable.menu),
                            onClick = mainClick,
                            movable = true,
                            size = FloatingMultiActionSize,
                            iconSize = 24.dp,
                            iconModifier = Modifier.rotate(rotation),
                            settings = glassSettings,
                            filmColor = surfaceColor(colorPalette().background2),
                        )) Image(
                        painter = if (!useAsActionsMenu) fabIcon else painterResource(R.drawable.menu),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette().text),
                        modifier = Modifier
                            .rotate(rotation)
                            .align(Alignment.Center)
                            .size(24.dp)
                    )
                }
            }

        }
    }

    }


@Composable
fun SmallFloatingActionButtonRow(
    item: FabItem,
    showLabel: Boolean,
    stateTransition: Transition<MultiFabState>
) {
    val alpha: Float by stateTransition.animateFloat(
        transitionSpec = {
            tween(durationMillis = 50)
        }, label = ""
    ) { state ->
        if (state == MultiFabState.Expanded) 1f else 0f
    }
    val scale: Float by stateTransition.animateFloat(
        label = ""
    ) { state ->
        if (state == MultiFabState.Expanded) 1.0f else 0f
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .alpha(animateFloatAsState((alpha), label = "").value)
            .scale(animateFloatAsState(targetValue = scale, label = "").value)
    ) {
        if (showLabel) {
            Text(
                text = item.label,
                modifier = Modifier
                    .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                    .clip(uiRoundnessShape()).clickable(onClick = { item.onFabItemClicked() })
            )
        }
        if (!GlassFloatingActionIcon(
                icon = item.icon,
                onClick = item.onFabItemClicked,
                modifier = Modifier.padding(4.dp),
                size = 40.dp,
                iconSize = 24.dp,
                tint = colorPalette().favoritesIcon,
                contentDescription = item.label,
                filmColor = surfaceColor(colorPalette().background2),
            )) SmallFloatingActionButton(
            shape = uiRoundnessShape(),
            modifier = Modifier
                .padding(4.dp).glassNativeActionContour(),
            onClick = { item.onFabItemClicked() },
            containerColor = surfaceColor(colorPalette().background2),
            contentColor = colorPalette().favoritesIcon
        ) {
            Icon(
                painter = item.icon,
                contentDescription = item.label,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
