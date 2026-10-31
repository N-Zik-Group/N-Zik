package app.n_zik.android.components.dialog.settings

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.SettingsNestedOption
import app.n_zik.android.components.dialog.common.ContentFramedDialog
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.it.fast4x.rimusic.utils.*
import app.kreate.android.me.knighthat.utils.Toaster
import org.json.JSONArray
import sh.calvin.reorderable.rememberReorderableLazyListState
import android.content.Context

object HistorySortSettingsDialog : ContentFramedDialog {

    /** Exact searchable options from the same definitions used by this dialog. */
    internal fun searchOptions(): List<SettingsNestedOption> = sortIds.map { id -> SettingsNestedOption("hist_$id", requireNotNull(getSortLabelRes(id))) }


    private val sortIds = listOf("DATE", "ALPHABETICAL", "ARTIST")

    override val dialogTitle: String @Composable get() = stringResource(R.string.history)
    override var isActive: Boolean by mutableStateOf(false)

    private fun parseOrder(s: String): List<String> {
        if (s.isBlank()) return sortIds
        return try {
            val a = JSONArray(s)
            val savedIds = mutableListOf<String>(); val seen = mutableSetOf<String>()
            for (i in 0 until a.length()) { val id = a.getString(i); if (id in sortIds && seen.add(id)) savedIds.add(id) }
            savedIds + sortIds.filter { it !in savedIds }
        } catch (_: Exception) { sortIds }
    }
    private fun serializeOrder(order: List<String>): String { val a = JSONArray(); order.forEach { a.put(it) }; return a.toString() }

    private fun getSortIcon(id: String): Int = when (id) {
        "DATE" -> R.drawable.time
        "ALPHABETICAL" -> R.drawable.text
        "ARTIST" -> R.drawable.artist
        else -> R.drawable.text
    }
    private fun getSortLabelRes(id: String): Int? = when (id) {
        "DATE" -> R.string.date
        "ALPHABETICAL" -> R.string.alphabetical
        "ARTIST" -> R.string.artist
        else -> null
    }

    @Composable
    private fun getSortLabel(id: String): String = getSortLabelRes(id)?.let { stringResource(it) } ?: id

    @Composable
    override fun DialogBody() {
        val ctx = LocalContext.current
        val prefs = remember { ctx.getSharedPreferences("preferences", Context.MODE_PRIVATE) }

        var workingOrder by remember { mutableStateOf(parseOrder(prefs.getString(historySortMenuOrderKey, "") ?: "").toMutableList()) }
        var workingToggles by remember {
            mutableStateOf(sortIds.associateWith { id -> prefs.getBoolean("hist_sort_${id}_visible", true) }.toMutableMap())
        }

        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val o = workingOrder.toMutableList()
            val fi = o.indexOfFirst { "hist_$it" == from.key }
            val ti = o.indexOfFirst { "hist_$it" == to.key }
            if (fi != -1 && ti != -1) {
                val item = o.removeAt(fi)
                o.add(ti, item)
                workingOrder = o
            }
        }
        val items = workingOrder.distinct().map { id ->
            ToggleItem("hist_$id", getSortIcon(id), getSortLabel(id), "hist_sort_${id}_visible", true)
        }

        ToggleListDialog(
            frameTitle = dialogTitle,
            onDismiss = ::hideDialog,
            items = items, lazyListState = lazyListState, reorderableState = reorderableState, enforceMinOneChecked = true,
            checkedStatesOverride = items.map { workingToggles[it.id.removePrefix("hist_")] ?: true },
            onCheckedChange = { index, newValue ->
                val id = items[index].id.removePrefix("hist_")
                workingToggles = workingToggles.toMutableMap().apply { this[id] = newValue }
            },
            onReset = {
                workingToggles = sortIds.associateWith { true }.toMutableMap(); workingOrder = sortIds.toMutableList()
            },
            onCancel = { hideDialog() },
            onConfirm = {
                val edit = prefs.edit()
                workingToggles.forEach { (id, isChecked) -> edit.putBoolean("hist_sort_${id}_visible", isChecked) }
                edit.putString(historySortMenuOrderKey, serializeOrder(workingOrder.filter { id -> workingToggles[id] == true }))
                edit.apply(); Toaster.s(R.string.toast_preference_saved); hideDialog()
            }
        )
    }
}
