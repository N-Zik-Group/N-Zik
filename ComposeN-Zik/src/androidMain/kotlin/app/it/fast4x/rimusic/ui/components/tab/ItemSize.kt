package app.it.fast4x.rimusic.ui.components.tab

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.enums.HomeItemSize
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.components.menu.ListMenu
import androidx.compose.material3.Icon
import app.it.fast4x.rimusic.utils.Preference

class ItemSize private constructor(
    val menuState: MenuState,
    private val sizeState: MutableState<HomeItemSize>
): MenuIcon, Descriptive {

    companion object {
        @JvmStatic
        @Composable
        fun init(key: Preference.Key<HomeItemSize>): ItemSize =
            ItemSize(
                LocalMenuState.current,
                Preference.remember(key)
            )
    }

    override val iconId: Int = R.drawable.resize
    override val messageId: Int = R.string.size
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.size )

    val size: HomeItemSize
        get() = sizeState.value

    @Composable
    fun SettingIcon(@DrawableRes icon: Int) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .iconBadgeBackground(
                    color = colorPalette().accent.copy(alpha = 0.1f),
                    shape = uiRoundnessShape()
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                tint = colorPalette().accent,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    @Composable
    private fun Entry( size: HomeItemSize) {
        ListMenu.Entry(
            text = size.text,
            icon = { SettingIcon(size.iconId) },
            onClick = {
                sizeState.value = size
                menuState.hide()
            }
        )
    }

    override fun onShortClick() {
        menuState.display {
            ListMenu.Menu(title = stringResource(R.string.size)) {
                HomeItemSize.entries.forEach { Entry(it) }
            }
        }
    }
}


