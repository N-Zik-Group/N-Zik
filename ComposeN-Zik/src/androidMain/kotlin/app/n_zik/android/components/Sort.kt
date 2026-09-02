package app.n_zik.android.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.enums.Drawable
import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Clickable
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Menu
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.utils.Preference
import app.it.fast4x.rimusic.utils.menuStyleKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.kreate.android.me.knighthat.enums.TextView
import org.json.JSONArray
import androidx.compose.animation.fadeOut
import androidx.compose.material3.RadioButton
import androidx.compose.animation.fadeIn
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.animation.scaleOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import android.content.Context

open class Sort<T: Enum<T>> (
    override val menuState: MenuState,
    sortByState: MutableState<T>,
    sortOrderState: MutableState<SortOrder>,
    styleState: MutableState<MenuStyle>,
    private val sortMenuOrderKey: String? = null,
    private val sortMenuPrefix: String? = null
): MenuIcon, Clickable, Menu {

    companion object {
        @Composable
        inline operator fun<reified T: Enum<T>> invoke(
            sortByPrefKey: Preference.Key<T>,
            sortOrderPrefKey: Preference.Key<SortOrder>,
            sortMenuOrderKey: String? = null,
            sortMenuPrefix: String? = null
        ) = Sort(
            LocalMenuState.current,
            Preference.remember( sortByPrefKey ),
            Preference.remember( sortOrderPrefKey ),
            rememberPreference( menuStyleKey, MenuStyle.List ),
            sortMenuOrderKey,
            sortMenuPrefix
        )
    }

    open val arrowDirection: State<Float>
        @Composable
        get() = animateFloatAsState(
            targetValue = sortOrder.rotationZ,
            animationSpec = tween(durationMillis = 400, easing = LinearEasing),
            label = ""
        )
    override val iconId: Int = R.drawable.arrow_up
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.sorting_order )

    open var sortBy: T by sortByState
    open var sortOrder: SortOrder by sortOrderState
    override var menuStyle: MenuStyle by styleState

    override fun onShortClick() { sortOrder = !sortOrder }
    override fun onLongClick() = openMenu()

    @Suppress("UNCHECKED_CAST")
    private fun getEnumConstants(): List<T> {
        return (sortBy.javaClass.enumConstants as? Array<T>)?.toList() ?: emptyList()
    }

    @Composable
    private fun readSortedEnumConstants(): List<T> {
        if (sortMenuOrderKey == null || sortMenuPrefix == null) {
            return getEnumConstants()
        }
        val ctx = LocalContext.current
        val prefs = remember(ctx) { ctx.getSharedPreferences("preferences", Context.MODE_PRIVATE) }
        val savedOrderJson = remember(prefs, sortMenuOrderKey) {
            prefs.getString(sortMenuOrderKey, "") ?: ""
        }
        val allConstants = getEnumConstants()

        val savedIds = remember(savedOrderJson) {
            if (savedOrderJson.isBlank()) null
            else try {
                val a = JSONArray(savedOrderJson)
                val ids = mutableListOf<String>()
                val seen = mutableSetOf<String>()
                for (i in 0 until a.length()) {
                    val id = a.getString(i)
                    if (seen.add(id)) ids.add(id)
                }
                ids
            } catch (_: Exception) { null }
        }

        val visibleIds = remember(allConstants, prefs, sortMenuPrefix) {
            allConstants.map { it.name }.filter { id ->
                prefs.getBoolean("${sortMenuPrefix}_sort_${id}_visible", true)
            }.toSet()
        }

        if (savedIds == null) {
            return allConstants.filter { it.name in visibleIds }
        }

        val enumMap: Map<String, T> = remember(allConstants) { allConstants.associateBy { it.name } }
        val result = mutableListOf<T>()
        val added = mutableSetOf<String>()

        for (id in savedIds) {
            if (id in visibleIds && id in enumMap && added.add(id)) {
                result.add(enumMap[id] ?: continue)
            }
        }
        for (id in allConstants.map { it.name }) {
            if (id in visibleIds && added.add(id)) {
                result.add(enumMap[id] ?: continue)
            }
        }

        return result
    }

    @Composable
    override fun ListMenu() {
        val sortedConstants = readSortedEnumConstants()
        ListMenu.Menu(title = menuIconTitle) {
            sortedConstants.forEach {
                val isSelected = it == sortBy
                ListMenu.Entry(
                    text = if (it is TextView) it.text else it.name,
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else colorPalette().accent.copy(alpha = 0.1f),
                                    shape = uiRoundnessShape()
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter =
                                    if( it is Drawable )
                                        it.icon
                                    else
                                        painterResource( R.drawable.close ),
                                contentDescription = it.name,
                                tint = colorPalette().accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    modifier = if (isSelected) Modifier.background(colorPalette().accent.copy(alpha = 0.1f), uiRoundnessShape()) else Modifier,
                    trailingContent = {
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            RadioButton(
                                selected = true,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = colorPalette().accent,
                                    unselectedColor = colorPalette().textSecondary
                                )
                            )
                        }
                    },
                    onClick = {
                        menuState.hide()
                        sortBy = it
                    }
                )
            }
        }
    }

    @Composable
    override fun GridMenu() {
        val sortedConstants = readSortedEnumConstants()
        GridMenu.Menu(title = menuIconTitle) {
            items(
                items = sortedConstants,
                key = Enum<T>::ordinal
            ) {
                val isSelected = it == sortBy
                GridMenu.Entry(
                    text = if (it is TextView) it.text else it.name,
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else colorPalette().accent.copy(alpha = 0.1f),
                                    shape = uiRoundnessShape()
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter =
                                    if( it is Drawable )
                                        it.icon
                                    else
                                        painterResource( R.drawable.close ),
                                contentDescription = it.name,
                                tint = colorPalette().accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    trailingContent = {
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            RadioButton(
                                selected = true,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = colorPalette().accent,
                                    unselectedColor = colorPalette().textSecondary
                                )
                            )
                        }
                    },
                    onClick = {
                        menuState.hide()
                        sortBy = it
                    }
                )
            }
        }
    }

    @Composable
    override fun MenuComponent() {
        if( menuStyle == MenuStyle.List )
            ListMenu()
        else
            GridMenu()
    }

    @Composable
    override fun ToolBarButton() {
        val animatedArrow by arrowDirection

        TabToolBar.Icon(
            icon,
            color,
            sizeDp,
            isEnabled,
            this.modifier.graphicsLayer { rotationZ = animatedArrow },
            this::onShortClick,
            this::onLongClick
        )
    }
}
