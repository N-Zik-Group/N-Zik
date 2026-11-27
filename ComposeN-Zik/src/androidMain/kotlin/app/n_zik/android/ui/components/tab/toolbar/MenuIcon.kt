package app.n_zik.android.ui.components.tab.toolbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.menu.menuToggleSemantics
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.uiRoundnessShape

interface MenuIcon: Icon {

    @get:Composable
    val menuIconTitle: String
    val menuLongClick: (() -> Unit)? get() = null
    @get:Composable
    val menuLongClickLabel: String? get() = null
    val menuToggleState: Boolean? get() = null
    @get:Composable
    val menuStateDescription: String? get() = null

    @Composable
    private fun entryModifier(): Modifier {
        val toggled = modifier.menuToggleSemantics(menuToggleState)
        val description = menuStateDescription ?: return toggled
        return toggled.semantics { stateDescription = description }
    }

    @Composable
    private fun SettingIcon() {
        val useOriginalColors = color == Color.Unspecified
        
        val iconColor = when {
            !isEnabled && !useOriginalColors -> colorPalette().textDisabled
            this is DynamicColor -> if (isFirstColor) colorPalette().accent else colorPalette().textDisabled
            useOriginalColors -> Color.Unspecified
            else -> colorPalette().accent
        }
        
        Box(
            modifier = Modifier
                .size(32.dp)
                .then(
                    if (!useOriginalColors) {
                        Modifier.iconBadgeBackground(
                            color = iconColor.copy(alpha = 0.1f),
                            shape = uiRoundnessShape()
                        )
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                tint = iconColor,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    @Composable
    fun GridMenuItem() = GridMenu.Entry(
        text = menuIconTitle,
        icon = { SettingIcon() },
        modifier = entryModifier(),
        iconHasOwnBackground = color != Color.Unspecified,
        enabled = isEnabled,
        onClick = ::onShortClick,
        onLongClick = menuLongClick,
        onLongClickLabel = menuLongClickLabel,
    )

    @Composable
    fun ListMenuItem() = ListMenu.Entry(
        text = menuIconTitle,
        icon = { SettingIcon() },
        modifier = entryModifier(),
        enabled = isEnabled,
        onClick = ::onShortClick,
        onLongClick = menuLongClick,
        onLongClickLabel = menuLongClickLabel,
    )
}




