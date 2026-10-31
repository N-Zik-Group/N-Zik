package app.it.fast4x.rimusic.ui.components.navigation.header

import app.n_zik.android.components.ui.reservedIconActionCanvas
import app.n_zik.android.components.ui.nativeControlIndication
import app.n_zik.android.components.glass.glassGlobalIndication

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.tab.toolbar.EllipsisMenuComponent
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.animation.AnimatedContent
import app.n_zik.android.components.glass.GlassToolbarIcon

object TabToolBar {

    val TOOLBAR_ICON_SIZE = 32.dp
    val HORIZONTAL_PADDING = 12.dp
    val VERTICAL_PADDING = 4.dp

    @Composable
    fun Buttons(
        buttons: List<Button>,
        horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceEvenly,
        verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
        modifier: Modifier = Modifier,
        disableAnimation: Boolean = false
    ) {
        val density = LocalDensity.current.density
        var availableWidth by remember { mutableStateOf(0.dp) }
        val sizeWithSpacing = TOOLBAR_ICON_SIZE + 15.dp
        var canDisplay by remember { mutableIntStateOf(0) }

        LaunchedEffect( availableWidth ) {
            canDisplay = (availableWidth / sizeWithSpacing).toInt()
        }

        val baseModifier = modifier.fillMaxWidth()
            .padding( HORIZONTAL_PADDING, VERTICAL_PADDING )
            .onGloballyPositioned {
                val widthDp = it.size.width / density
                availableWidth = widthDp.dp - (HORIZONTAL_PADDING * 2)
            }

        val content = @Composable { targetButtons: List<Button> ->
            if( canDisplay == 0 ) {
                Spacer(modifier = Modifier.fillMaxWidth())
            } else {
                val isClustered = targetButtons.size > canDisplay
                val ellipsisMenu = EllipsisMenuComponent.init {
                    targetButtons.takeLast(
                        (targetButtons.size - canDisplay + 1).coerceAtLeast( 0 )
                    )
                }

                Row(
                    horizontalArrangement = horizontalArrangement,
                    verticalAlignment = verticalAlignment,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    targetButtons.take(
                        if( isClustered ) canDisplay - 1 else targetButtons.size
                    ).forEach { it.ToolBarButton() }

                    if( isClustered ) ellipsisMenu.ToolBarButton()
                }
            }
        }

        if (disableAnimation) {
            Box(modifier = baseModifier) {
                content(buttons)
            }
        } else {
            AnimatedContent(
                targetState = buttons,
                contentKey = { target -> target.map { it.javaClass.name } },
                label = "ToolbarButtonsAnimation",
                modifier = baseModifier
            ) { targetButtons ->
                content(targetButtons)
            }
        }
    }

    @Composable
    fun Buttons(
        vararg buttons: Button,
        horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceEvenly,
        verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
        modifier: Modifier = Modifier,
        disableAnimation: Boolean = false
    ) = Buttons( listOf( *buttons ), horizontalArrangement, verticalAlignment, modifier, disableAnimation )

    @Composable
    fun Icon(
        icon: Painter,
        tint: Color = colorPalette().text,
        size: Dp = TOOLBAR_ICON_SIZE,
        enabled: Boolean = true,
        modifier: Modifier = Modifier,
        onClick: () -> Unit = {}
    ) {
        if (GlassToolbarIcon(icon, tint, size, enabled, modifier, onClick)) return
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier.minimumInteractiveComponentSize().reservedIconActionCanvas(
                shape = uiRoundnessShape(), interactionSource = interactionSource,
                indication = glassGlobalIndication(nativeControlIndication()),
                enabled = enabled, onClick = onClick, role = Role.Button),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                null,
                modifier.size( size )
                        .padding( horizontal = 4.dp ),
                tint
            )
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun Icon(
        icon: Painter,
        tint: Color = colorPalette().text,
        size: Dp = TOOLBAR_ICON_SIZE,
        enabled: Boolean = true,
        modifier: Modifier = Modifier,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {}
    ) {
        if (GlassToolbarIcon(icon, tint, size, enabled, modifier, onClick, onLongClick, modifierOnContainer = true)) return
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = modifier
                .minimumInteractiveComponentSize()
                .reservedIconActionCanvas(
                    shape = uiRoundnessShape(),
                    interactionSource = interactionSource,
                    indication = glassGlobalIndication(nativeControlIndication()),
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier
                    .then(Modifier.testTag("toolbar.native.glyph"))
                    .size(size)
                    .padding(horizontal = 4.dp),
                tint = if (enabled) tint else tint.copy(alpha = 0.5f)
            )
        }
    }

    @Composable
    fun Icon(
        @DrawableRes iconId: Int,
        tint: Color = colorPalette().text,
        size: Dp = TOOLBAR_ICON_SIZE,
        enabled: Boolean = true,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        if (GlassToolbarIcon(painterResource(iconId), tint, size, enabled, modifier, onClick)) return
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier.minimumInteractiveComponentSize().reservedIconActionCanvas(
                shape = uiRoundnessShape(), interactionSource = interactionSource,
                indication = glassGlobalIndication(nativeControlIndication()),
                enabled = enabled, onClick = onClick, role = Role.Button),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource( iconId ),
                tint = tint,
                contentDescription = null,
                modifier = modifier
                    .size(size)
                    .padding(horizontal = 4.dp)
            )
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun Icon(
        @DrawableRes iconId: Int,
        tint: Color = colorPalette().text,
        size: Dp = TOOLBAR_ICON_SIZE,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        onShortClick: () -> Unit,
        onLongClick: () -> Unit
    ) {
        if (GlassToolbarIcon(painterResource(iconId), tint, size, enabled, modifier, onShortClick, onLongClick, modifierOnContainer = true)) return
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = modifier
                .minimumInteractiveComponentSize()
                .reservedIconActionCanvas(
                    shape = uiRoundnessShape(),
                    interactionSource = interactionSource,
                    indication = glassGlobalIndication(nativeControlIndication()),
                    enabled = enabled,
                    onClick = onShortClick,
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconId),
                contentDescription = null,
                modifier = Modifier
                    .then(Modifier.testTag("toolbar.native.glyph"))
                    .size(size)
                    .padding(horizontal = 4.dp),
                tint = if (enabled) tint else tint.copy(alpha = 0.5f)
            )
        }
    }

    @Composable
    fun Toggleable(
        @DrawableRes onIconId: Int,
        @DrawableRes offIconId: Int,
        toggleCondition: Boolean,
        tint: Color = colorPalette().text,
        size: Dp = TOOLBAR_ICON_SIZE,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        Icon(
            iconId = if( toggleCondition ) onIconId else offIconId,
            tint = tint,
            size = size,
            modifier = modifier,
            onClick = onClick
        )
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun Toggleable(
        @DrawableRes onIconId: Int,
        @DrawableRes offIconId: Int,
        toggleCondition: Boolean,
        tint: Color = colorPalette().text,
        size: Dp = TOOLBAR_ICON_SIZE,
        modifier: Modifier = Modifier,
        onShortClick: () -> Unit,
        onLongClick: () -> Unit
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = modifier
                .minimumInteractiveComponentSize()
                .reservedIconActionCanvas(
                    shape = uiRoundnessShape(),
                    interactionSource = interactionSource,
                    indication = glassGlobalIndication(nativeControlIndication()),
                    enabled = true,
                    onClick = onShortClick,
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(if (toggleCondition) onIconId else offIconId),
                contentDescription = null,
                modifier = Modifier
                    .then(Modifier.testTag("toolbar.native.glyph"))
                    .size(size)
                    .padding(horizontal = 4.dp),
                tint = tint
            )
        }
    }

    @Composable
    fun Toggleable(
        @DrawableRes iconId: Int,
        tintOn: Color = colorPalette().text,
        tintOff: Color = colorPalette().textDisabled,
        toggleCondition: Boolean,
        enabled: Boolean,
        size: Dp = TOOLBAR_ICON_SIZE,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        Icon(
            iconId = iconId,
            tint = if( toggleCondition ) tintOn else tintOff,
            size = size,
            enabled = enabled,
            modifier = modifier,
            onClick = onClick
        )
    }

    @ExperimentalFoundationApi
    @Composable
    fun Toggleable(
        @DrawableRes iconId: Int,
        tintOn: Color = colorPalette().text,
        tintOff: Color = colorPalette().textDisabled,
        toggleCondition: Boolean,
        enabled: Boolean,
        size: Dp = TOOLBAR_ICON_SIZE,
        modifier: Modifier = Modifier,
        onShortClick: () -> Unit,
        onLongClick: () -> Unit
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = modifier
                .minimumInteractiveComponentSize()
                .reservedIconActionCanvas(
                    shape = uiRoundnessShape(),
                    interactionSource = interactionSource,
                    indication = glassGlobalIndication(nativeControlIndication()),
                    enabled = enabled,
                    onClick = onShortClick,
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconId),
                contentDescription = null,
                modifier = Modifier
                    .then(Modifier.testTag("toolbar.native.glyph"))
                    .size(size)
                    .padding(horizontal = 4.dp),
                tint = if (enabled) (if (toggleCondition) tintOn else tintOff) else colorPalette().textDisabled
            )
        }
    }
}





