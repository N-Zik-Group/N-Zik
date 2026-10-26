package app.n_zik.android.components.menu.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Clickable
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Menu
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.utils.menuStyleKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.surfaceColor
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.Languages
import app.it.fast4x.rimusic.utils.otherLanguageAppLyricsKey

@UnstableApi
class LanguagesListMenu private constructor(
    private val translateEnabled: MutableState<Boolean>,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>
) : Menu {

    companion object {
        @Composable
        operator fun invoke(
            translateEnabled: MutableState<Boolean>,
        ): LanguagesListMenu =
            LanguagesListMenu(
                translateEnabled = translateEnabled,
                menuState = LocalMenuState.current,
                styleState = rememberPreference(menuStyleKey, MenuStyle.List)
            )
    }

    private lateinit var buttons: List<Button>
    override var menuStyle: MenuStyle by styleState

    @Composable
    override fun ListMenu() = ListMenu.Menu(title = stringResource(R.string.translate_to_other_language)) {
        buttons.forEach {
            if (it is MenuIcon)
                it.ListMenuItem()
        }
    }

    @Composable
    override fun GridMenu() = GridMenu.Menu(title = stringResource(R.string.translate_to_other_language)) {
        items(buttons, Button::hashCode) {
            if (it is MenuIcon)
                it.GridMenuItem()
        }
    }

    @Composable
    override fun MenuComponent() {
        var otherLanguageApp by rememberPreference(otherLanguageAppLyricsKey, Languages.System)

        buttons = mutableListOf<Button>().apply {
            add(object : MenuIcon, Descriptive, Clickable {
                override val iconId: Int = R.drawable.translate
                override val messageId: Int = R.string.do_not_translate
                @get:Composable
                override val menuIconTitle: String get() = stringResource(messageId)
                override fun onShortClick() {
                    menuState.hide()
                    translateEnabled.value = false
                }
                override fun onLongClick() {}
            })

            add(object : MenuIcon, Descriptive, Clickable {
                override val iconId: Int = R.drawable.translate
                override val messageId: Int = R.string._default
                @get:Composable
                override val menuIconTitle: String get() = stringResource(messageId) + " (" + otherLanguageApp.text + ")"
                override fun onShortClick() {
                    menuState.hide()
                    translateEnabled.value = true
                }
                override fun onLongClick() {}
            })

            Languages.entries.forEach { lang ->
                if (lang != Languages.System) {
                    add(object : MenuIcon, Descriptive, Clickable {
                        override val iconId: Int = R.drawable.translate
                        override val messageId: Int = -1
                        @get:Composable
                        override val menuIconTitle: String get() = lang.text
                        override fun onShortClick() {
                            menuState.hide()
                            otherLanguageApp = lang
                            translateEnabled.value = true
                        }
                        override fun onLongClick() {}
                    })
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(surfaceColor(colorPalette().background1))
        ) {
            if (menuStyle == MenuStyle.List)
                ListMenu()
            else
                GridMenu()
        }
    }
}
