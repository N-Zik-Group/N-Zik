package app.n_zik.android.components.dialog.settings

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ContentFramedDialog
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.it.fast4x.rimusic.utils.homeTabsOrderKey
import app.it.fast4x.rimusic.utils.enableQuickPicksPageKey
import app.kreate.android.me.knighthat.utils.Toaster
import org.json.JSONArray
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.SharedPreferences

val defaultHomeTabsOrder = listOf(
    "quickpicks",
    "songs",
    "artists",
    "albums",
    "playlists"
)

data class HomeTabDef(
    val id: String,
    val preferenceKey: String?,
    val iconRes: Int,
    val labelRes: Int,
    val defaultValue: Boolean
)

fun buildHomeTabDefs(): Map<String, HomeTabDef> = mapOf(
    "quickpicks" to HomeTabDef("quickpicks", enableQuickPicksPageKey, R.drawable.sparkles, R.string.quick_picks, true),
    "songs" to HomeTabDef("songs", null, R.drawable.musical_notes, R.string.songs, true),
    "artists" to HomeTabDef("artists", null, R.drawable.people, R.string.artists, true),
    "albums" to HomeTabDef("albums", null, R.drawable.album, R.string.albums, true),
    "playlists" to HomeTabDef("playlists", null, R.drawable.library, R.string.playlists, true)
)

object HomeTabsSettingsDialog : ContentFramedDialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.home_tabs_settings)

    override var isActive: Boolean by mutableStateOf(false)

    fun parseOrder(serialized: String): List<String> {
        if (serialized.isBlank()) return defaultHomeTabsOrder
        return try {
            val arr = JSONArray(serialized)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            val validIds = defaultHomeTabsOrder.filter { it in buildHomeTabDefs().keys }
            val result = list.filter { it in validIds }.toMutableList()
            for (id in validIds) {
                if (id !in result) result.add(id)
            }
            result
        } catch (_: Exception) {
            defaultHomeTabsOrder
        }
    }

    private fun serializeOrder(order: List<String>): String {
        val arr = JSONArray()
        order.forEach { arr.put(it) }
        return arr.toString()
    }

    fun loadPrefs(prefs: SharedPreferences): Pair<MutableList<String>, MutableList<Boolean>> {
        val orderSerialized = prefs.getString(homeTabsOrderKey, "") ?: ""
        val order = parseOrder(orderSerialized).toMutableList()
        val toggles = order.map { id ->
            val def = buildHomeTabDefs()[id]
            if (def != null) {
                if (def.preferenceKey != null) {
                    prefs.getBoolean(def.preferenceKey, def.defaultValue)
                } else {
                    prefs.getBoolean("hometab_${def.id}_enabled", def.defaultValue)
                }
            } else true
        }.toMutableList()
        return order to toggles
    }

    private fun savePrefs(prefs: SharedPreferences, order: List<String>, toggles: Map<String, Boolean>) {
        val editor = prefs.edit()
        editor.putString(homeTabsOrderKey, serializeOrder(order))
        buildHomeTabDefs().forEach { (id, def) ->
            if (def.preferenceKey != null) {
                editor.putBoolean(def.preferenceKey, toggles[id] ?: def.defaultValue)
            } else {
                editor.putBoolean("hometab_${id}_enabled", toggles[id] ?: def.defaultValue)
            }
        }
        editor.apply()
    }

    @Composable
    override fun DialogBody() {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences("preferences", Context.MODE_PRIVATE) }
        val tabDefs = remember { buildHomeTabDefs() }

        val initial = remember { loadPrefs(prefs) }

        var workingOrder by remember { mutableStateOf(initial.first) }
        var workingToggles by remember { mutableStateOf(initial.second) }

        val lazyListState = rememberLazyListState()

        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val order = workingOrder.toMutableList()
            val toggles = workingToggles.toMutableList()
            val fromIndex = order.indexOf(from.key)
            val toIndex = order.indexOf(to.key)
            if (fromIndex != -1 && toIndex != -1) {
                val item = order.removeAt(fromIndex)
                order.add(toIndex, item)
                val checkedItem = toggles.removeAt(fromIndex)
                toggles.add(toIndex, checkedItem)
                workingOrder = order
                workingToggles = toggles
            }
        }

        val tabItems = workingOrder.mapIndexed { index, tabId ->
            val def = tabDefs[tabId] ?: return@mapIndexed null
            ToggleItem(
                id = def.id,
                iconRes = def.iconRes,
                label = stringResource(def.labelRes),
                preferenceKey = def.preferenceKey ?: "hometab_${def.id}_enabled",
                defaultValue = def.defaultValue
            )
        }.filterNotNull()

        ToggleListDialog(
            frameTitle = dialogTitle,
            onDismiss = ::hideDialog,
            items = tabItems,
            lazyListState = lazyListState,
            reorderableState = reorderableState,
            pinnedItemCount = 0,
            enforceMinOneChecked = true,
            checkedStatesOverride = workingToggles.toList(),
            onCheckedChange = { index, newValue ->
                val newToggles = workingToggles.toMutableList()
                newToggles[index] = newValue
                workingToggles = newToggles
            },
            onReset = {
                workingOrder = defaultHomeTabsOrder.toMutableList()
                workingToggles = defaultHomeTabsOrder.map { id ->
                    tabDefs[id]?.defaultValue ?: true
                }.toMutableList()
            },
            onCancel = {
                hideDialog()
            },
            onConfirm = {
                val toggleMap = mutableMapOf<String, Boolean>()
                workingOrder.forEachIndexed { index, id ->
                    toggleMap[id] = workingToggles[index]
                }
                savePrefs(prefs, workingOrder, toggleMap)
                Toaster.s(R.string.toast_preference_saved)
                hideDialog()
            }
        )
    }
    fun reset(context: Context) {
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val defaultToggles = buildHomeTabDefs().mapValues { it.value.defaultValue }
        savePrefs(prefs, defaultHomeTabsOrder, defaultToggles)
    }
}
