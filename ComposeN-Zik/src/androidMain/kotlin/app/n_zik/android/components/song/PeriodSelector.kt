package app.n_zik.android.components.song

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.enums.MaxTopPlaylistItems
import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.enums.StatisticsType
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Menu
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.components.menu.ListMenu
import androidx.compose.material3.Icon
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsKey
import app.it.fast4x.rimusic.utils.Preference
import app.it.fast4x.rimusic.utils.menuStyleKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import org.json.JSONArray

class PeriodSelector private constructor(
    override val menuState: MenuState,
    periodState: MutableState<StatisticsType>,
    styleState: MutableState<MenuStyle>,
    private val sortMenuOrderKey: String? = null,
    private val sortMenuPrefix: String? = null
): MenuIcon, Descriptive, Menu {

    companion object {
        @Composable
        operator fun invoke(
            prefKey: Preference.Key<StatisticsType>,
            sortMenuOrderKey: String? = null,
            sortMenuPrefix: String? = null
        ): PeriodSelector =
            PeriodSelector(
                LocalMenuState.current,
                Preference.remember( prefKey ),
                rememberPreference( menuStyleKey, MenuStyle.List ),
                sortMenuOrderKey,
                sortMenuPrefix
            )
    }

    var period: StatisticsType by periodState

    override val iconId: Int = period.iconId
    override val messageId: Int = R.string.statistics
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override var menuStyle: MenuStyle by styleState

    fun onDismiss( period: StatisticsType ) {
        this.period = period
        menuState.hide()
    }

    override fun onShortClick() = openMenu()

    @Composable
    override fun ListMenu() { /* Does nothing */ }

    @Composable
    override fun GridMenu() { /* Does nothing */ }

    @Composable
    private fun readSortedEntries(): List<StatisticsType> {
        if (sortMenuOrderKey == null || sortMenuPrefix == null) {
            return StatisticsType.entries
        }
        val ctx = LocalContext.current
        val prefs = remember(ctx) { ctx.getSharedPreferences("preferences", Context.MODE_PRIVATE) }
        val savedOrderJson = remember(prefs, sortMenuOrderKey) {
            prefs.getString(sortMenuOrderKey, "") ?: ""
        }
        val allEntries = StatisticsType.entries

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

        val visibleIds = remember(allEntries, prefs, sortMenuPrefix) {
            allEntries.map { it.name }.filter { id ->
                prefs.getBoolean("${sortMenuPrefix}_sort_${id}_visible", true)
            }.toSet()
        }

        if (savedIds == null) {
            return allEntries.filter { it.name in visibleIds }
        }

        val enumMap = remember(allEntries) { allEntries.associateBy { it.name } }
        val result = mutableListOf<StatisticsType>()
        val added = mutableSetOf<String>()

        for (id in savedIds) {
            if (id in visibleIds && id in enumMap && added.add(id)) {
                result.add(enumMap[id] ?: continue)
            }
        }
        for (entry in allEntries) {
            if (entry.name in visibleIds && added.add(entry.name)) {
                result.add(entry)
            }
        }

        return result
    }

    @Composable
    fun SettingIcon(@DrawableRes icon: Int) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
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
    override fun MenuComponent() {
        val size by rememberPreference( MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10` )
        val sortedEntries = readSortedEntries()

        ListMenu.Menu(title = stringResource( R.string.header_view_top_of, size )) {
            sortedEntries.forEach {
                ListMenu.Entry(
                    text = it.text,
                    icon = { SettingIcon(it.iconId) },
                    onClick = {
                        onDismiss( it )
                    }
                )
            }
        }
    }
}

