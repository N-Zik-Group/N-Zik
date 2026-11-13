package app.n_zik.android.components.menu

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.ui.styling.favoritesIcon
import app.n_zik.android.components.ui.LeadingIconBadge
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassMenuTriggerState
import app.n_zik.android.components.glass.LocalGlassMenuTrigger
import app.n_zik.android.components.glass.PageButtonFeedback
import app.n_zik.android.uiRoundnessShape

class DropdownMenu(
    val expanded: Boolean,
    val containerColor: Color = Color.Transparent,
    val modifier: Modifier = Modifier,
    val trigger: GlassMenuTriggerState? = null,
    val onDismissRequest: () -> Unit
) {

    private val _components: MutableList<@Composable () -> Unit> = mutableListOf()

    @Composable
    fun components() = remember { _components }

    @Composable
    fun add( item: Item) = _components.add { item.Draw() }

    @Composable
    fun add( component: @Composable () -> Unit) = _components.add( component )

    @Composable
    fun Draw() {
        val shape = uiRoundnessShape()
        GlassDropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            containerColor = containerColor,
            modifier = modifier,
            shape = shape,
            trigger = trigger ?: LocalGlassMenuTrigger.current,
            content = { components().forEach { it() } }
        )
    }

    class Item(
        val iconId: Int,
        val textId: Int = 0,
        val size: Dp = 24.dp,
        val padding: Dp = Dp.Hairline,
        val colors: MenuItemColors? = null,
        val modifier: Modifier = Modifier,
        val customText: String? = null,
        val enabled: Boolean = true,
        val onClick: () -> Unit
    ) {

        companion object {

            @Composable
            fun colors(): MenuItemColors {
                return MenuItemColors(
                    leadingIconColor =  colorPalette().favoritesIcon,
                    trailingIconColor =  colorPalette().favoritesIcon,
                    textColor = colorPalette().textSecondary,
                    disabledTextColor = colorPalette().text,
                    disabledLeadingIconColor = colorPalette().text,
                    disabledTrailingIconColor = colorPalette().text,
                )
            }
        }

        @Composable
        fun Draw() {
            val icon: @Composable () -> Unit = {
                LeadingIconBadge(
                    iconRes = iconId,
                    contentDescription = null,
                    modifier = modifier.size(24.dp),
                    enabled = enabled,
                )
            }

            PageButtonFeedback(false, enabled, RectangleShape, null) { feedback, source ->
                DropdownMenuItem(
                    enabled = enabled,
                    colors = colors ?: colors(),
                    text = { Text( customText ?: if (textId != 0) stringResource(textId) else "" ) },
                    leadingIcon = icon,
                    onClick = onClick,
                    modifier = feedback,
                    interactionSource = source,
                )
            }
        }
    }
}

/** Keeps the header menu's native click and long-click on the row that owns the halo. */
@Composable
internal fun DropdownMenuRowFeedback(
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    PageButtonFeedback(false, true, RectangleShape, null) { feedback, source ->
        content(
            modifier.then(feedback).combinedClickable(
                interactionSource = source,
                indication = if (source == null) LocalIndication.current else null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
        )
    }
}


