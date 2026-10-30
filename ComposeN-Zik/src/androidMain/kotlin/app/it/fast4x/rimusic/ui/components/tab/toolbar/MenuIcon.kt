package app.it.fast4x.rimusic.ui.components.tab.toolbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.uiRoundnessShape

interface MenuIcon: Icon {

    @get:Composable
    val menuIconTitle: String

    @Composable
    private fun SettingIcon() {
        val useOriginalColors = color == Color.Unspecified
        
        val iconColor = if (this is DynamicColor) {
            if (isFirstColor) colorPalette().accent else colorPalette().textDisabled
        } else {
            if (useOriginalColors) Color.Unspecified else colorPalette().accent
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
        modifier = modifier,
        iconHasOwnBackground = color != Color.Unspecified,
        enabled = isEnabled,
        onClick = ::onShortClick,
        onLongClick = if (this is Clickable) ::onLongClick else { {} }
    )

    @Composable
    fun ListMenuItem() = ListMenu.Entry(
        text = menuIconTitle,
        icon = { SettingIcon() },
        modifier = modifier,
        enabled = isEnabled,
        onClick = ::onShortClick,
        onLongClick = if (this is Clickable) ::onLongClick else { {} }
    )
}




