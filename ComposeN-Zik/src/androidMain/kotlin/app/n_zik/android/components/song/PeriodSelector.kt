package app.n_zik.android.components.song

import app.n_zik.android.utils.preferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.enums.MaxTopPlaylistItems
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.enums.StatisticsType
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.Menu
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.components.menu.SelectionMenu
import app.n_zik.android.utils.MaxTopPlaylistItemsKey
import app.n_zik.android.utils.MaxTopPlaylistItemsCustomValueKey
import app.n_zik.android.utils.Preference
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.rememberPreference
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
    override val messageId: Int = R.string.statistics_title
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
    override fun ListMenu() = PeriodMenu(MenuStyle.List)

    @Composable
    override fun GridMenu() = PeriodMenu(MenuStyle.Grid)

    @Composable
    private fun readSortedEntries(): List<StatisticsType> {
        if (sortMenuOrderKey == null || sortMenuPrefix == null) {
            return StatisticsType.entries
        }
        val ctx = LocalContext.current
        val prefs = ctx.preferences
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
    override fun MenuComponent() = PeriodMenu(menuStyle)

    @Composable
    private fun PeriodMenu(style: MenuStyle) {
        val size by rememberPreference( MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10` )
        val sizeCustomValue by rememberPreference( MaxTopPlaylistItemsCustomValueKey, 10 )
        val sizeLabel = when (size) {
            MaxTopPlaylistItems.Unlimited -> stringResource(R.string.statistics_items_limit_unlimited)
            else -> size.toInt(sizeCustomValue).toString()
        }
        SelectionMenu(
            title = stringResource(R.string.statistics_top_period_title, sizeLabel),
            choices = readSortedEntries(),
            selectedChoice = period,
            label = { it.text },
            icon = { it.iconId },
            style = style,
            onSelected = ::onDismiss,
        )
    }
}
