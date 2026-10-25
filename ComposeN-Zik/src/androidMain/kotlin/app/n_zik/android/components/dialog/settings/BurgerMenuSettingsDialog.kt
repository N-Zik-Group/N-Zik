package app.n_zik.android.components.dialog.settings

import android.content.Context
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.Dialog
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.n_zik.android.utils.DataStoreUtils
import org.json.JSONArray
import sh.calvin.reorderable.rememberReorderableLazyListState
import timber.log.Timber

/**
 * Customizer for the hamburger menu (spec-customizable-burger-menu): the same mental pattern
 * as the home songs toolbar dialog - drag to reorder + per-item show/hide - but persisted in
 * the global `app_settings` store (the burger layout is cosmetic, not per-profile), with the
 * locked items (G3) forced on and the separator (G5 renegotiated) maskable, drag-able and
 * multiplicable (each instance gets its own draggable row with a per-row remove button,
 * one shared toggle, up to [DataStoreUtils.MAX_BURGER_MENU_SEPARATORS] instances via the
 * "Add separator" footer button).
 */
object BurgerMenuSettingsDialog : Dialog {

    /** All burger entries in the default order, the separator included. */
    val allItemIds: List<String> = DataStoreUtils.DEFAULT_BURGER_MENU_ORDER

    /** The entries the customizer cannot switch off (G3 renegotiated: Settings only). */
    val lockedIds: Set<String> = DataStoreUtils.LOCKED_BURGER_MENU_ITEMS

    /**
     * Defensive parse: blank or corrupt -> default order; every "separator" instance is
     * kept in count and position (capped at [DataStoreUtils.MAX_BURGER_MENU_SEPARATORS],
     * the first N positions kept), every other id de-duplicated, unknown ids dropped and
     * every missing id (separator included) appended at the END of the list.
     */
    fun parseOrder(saved: String): List<String> {
        val available = allItemIds
        if (saved.isBlank()) return available
        return runCatching {
            val array = JSONArray(saved)
            val order = ArrayList<String>(available.size)
            val seen = LinkedHashSet<String>()
            var separatorCount = 0
            for (i in 0 until array.length()) {
                val id = runCatching { array.getString(i) }.getOrNull() ?: continue
                if (id == "separator") {
                    // Keep the first N separator instances in their positions, trim the surplus.
                    if (separatorCount < DataStoreUtils.MAX_BURGER_MENU_SEPARATORS) {
                        order.add(id)
                        separatorCount++
                    }
                } else if (id in available && seen.add(id)) {
                    order.add(id)
                }
            }
            // A stored order without any separator gets one back at the end,
            // like every other missing id.
            order + (available - order.toSet())
        }.onFailure {
            Timber.tag("BurgerMenu").w("Stored burger order is corrupt, falling back to the default")
        }.getOrDefault(available)
    }

    fun serializeOrder(order: List<String>): String {
        val array = JSONArray()
        order.forEach { array.put(it) }
        return array.toString()
    }

    /** Stored order intersect the stored toggles: locked items always survive; the rest follow their toggle (default on). */
    fun resolveVisibleIds(order: List<String>, toggles: Map<String, Boolean>): List<String> =
        order.filter { id -> id in lockedIds || toggles[id] ?: true }

    override val dialogTitle: String @Composable get() = stringResource(R.string.burger_menu)
    override var isActive: Boolean by mutableStateOf(false)

    @Composable
    override fun DialogBody() {
        val ctx = LocalContext.current
        val prefs = remember { DataStoreUtils.prefs(ctx) }

        var workingOrder by remember {
            mutableStateOf(
                parseOrder(prefs.getString(DataStoreUtils.KEY_BURGER_MENU_ORDER, null) ?: "").toMutableList()
            )
        }
        var workingToggles by remember {
            mutableStateOf(
                allItemIds.associateWith { id ->
                    prefs.getBoolean(DataStoreUtils.burgerMenuToggleKey(id), true)
                }.toMutableMap()
            )
        }

        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val o = workingOrder.toMutableList()
            val keys = rowKeys(o)
            val fi = keys.indexOf(from.key)
            val ti = keys.indexOf(to.key)
            if (fi != -1 && ti != -1) {
                val item = o.removeAt(fi)
                o.add(ti, item)
                workingOrder = o
            }
        }

        // G5 renegotiated: every separator instance gets its own draggable row (a unique
        // row key) with a per-row remove button; the show/hide toggle still applies per
        // logical id, so all instances share one switch. The footer button multiplies
        // the instances and disables at the cap.
        val separatorCount = workingOrder.count { it == "separator" }
        val keys = rowKeys(workingOrder)
        val items = workingOrder.mapIndexedNotNull { index, id ->
            itemSpec(
                rowKey = keys[index],
                id = id,
                separatorInstance = workingOrder.take(index + 1).count { it == "separator" },
                separatorCount = separatorCount
            )
        }

        ToggleListDialog(
            items = items,
            lazyListState = lazyListState,
            reorderableState = reorderableState,
            enforceMinOneChecked = false,
            lockedCheckedIds = lockedIds,
            extraFooterButton = DialogButton(
                text = stringResource(R.string.add_separator),
                icon = R.drawable.horizontal_straight_line,
                enabled = separatorCount < DataStoreUtils.MAX_BURGER_MENU_SEPARATORS,
                onClick = {
                    // A fresh list reference: mutating in place would not invalidate the state
                    workingOrder = (workingOrder + "separator").toMutableList()
                }
            ),
            // Rows are built 1:1 with workingOrder (parseOrder only keeps known ids), so
            // the row index maps straight back to the logical id of the shared toggle.
            checkedStatesOverride = workingOrder.map { id -> workingToggles[id] ?: true },
            onCheckedChange = { index, newValue ->
                val id = workingOrder.getOrNull(index) ?: return@ToggleListDialog
                val m = workingToggles.toMutableMap()
                m[id] = newValue
                workingToggles = m
            },
            onRowAction = { index ->
                // Locked rows render no remove button, so the index is always removable.
                val o = workingOrder.toMutableList()
                o.removeAt(index)
                workingOrder = o
            },
            onReset = {
                workingToggles = allItemIds.associateWith { true }.toMutableMap()
                workingOrder = allItemIds.toMutableList()
            },
            onCancel = { hideDialog() },
            onConfirm = {
                val edit = prefs.edit()
                edit.putString(DataStoreUtils.KEY_BURGER_MENU_ORDER, serializeOrder(workingOrder))
                workingToggles.forEach { (id, isVisible) ->
                    edit.putBoolean(DataStoreUtils.burgerMenuToggleKey(id), isVisible)
                }
                edit.apply()
                Toaster.s(R.string.toast_preference_saved)
                hideDialog()
            }
        )
    }

    /**
     * Unique row key per order entry: the logical id itself, or "separator#N" (N being
     * the 1-based instance number) so every separator instance is an individually
     * draggable/removable row.
     */
    private fun rowKeys(order: List<String>): List<String> =
        order.mapIndexed { index, id ->
            if (id == "separator") "separator#${order.take(index + 1).count { it == "separator" }}" else id
        }

    @Composable
    private fun itemSpec(rowKey: String, id: String, separatorInstance: Int, separatorCount: Int): ToggleItem? = when (id) {
        "history" -> ToggleItem(rowKey, R.drawable.history, stringResource(R.string.history), DataStoreUtils.burgerMenuToggleKey(id), true)
        "statistics" -> ToggleItem(rowKey, R.drawable.stats_chart, stringResource(R.string.statistics), DataStoreUtils.burgerMenuToggleKey(id), true)
        "rewind" -> ToggleItem(rowKey, R.drawable.ic_launcher_monochrome, stringResource(R.string.rewind), DataStoreUtils.burgerMenuToggleKey(id), true)
        "offline_mode" -> ToggleItem(rowKey, R.drawable.airplane, stringResource(R.string.offline_mode), DataStoreUtils.burgerMenuToggleKey(id), true)
        "listen_together" -> ToggleItem(rowKey, R.drawable.people, stringResource(R.string.listen_together), DataStoreUtils.burgerMenuToggleKey(id), true)
        "bridge_server" -> ToggleItem(rowKey, R.drawable.devices, stringResource(R.string.bridge_server), DataStoreUtils.burgerMenuToggleKey(id), true)
        "profiles" -> ToggleItem(rowKey, R.drawable.person, stringResource(R.string.profiles), DataStoreUtils.burgerMenuToggleKey(id), true)
        "pip" -> ToggleItem(rowKey, R.drawable.images_sharp, stringResource(R.string.menu_go_to_picture_in_picture), DataStoreUtils.burgerMenuToggleKey(id), true)
        "separator" -> ToggleItem(
            rowKey,
            R.drawable.horizontal_straight_line,
            if (separatorCount > 1) stringResource(R.string.burger_menu_separator_instance, separatorInstance)
            else stringResource(R.string.burger_separator),
            DataStoreUtils.burgerMenuToggleKey(id),
            true
        )
        "settings" -> ToggleItem(rowKey, R.drawable.settings, stringResource(R.string.settings), DataStoreUtils.burgerMenuToggleKey(id), true)
        "extensions" -> ToggleItem(rowKey, R.drawable.extension, stringResource(R.string.modules_title), DataStoreUtils.burgerMenuToggleKey(id), true)
        "debug_logs" -> ToggleItem(rowKey, R.drawable.bugs, stringResource(R.string.maintenance_debug_logs), DataStoreUtils.burgerMenuToggleKey(id), true)
        "maintenance" -> ToggleItem(rowKey, R.drawable.shield_checkmark, stringResource(R.string.maintenance), DataStoreUtils.burgerMenuToggleKey(id), true)
        "rescue_center" -> ToggleItem(rowKey, R.drawable.shortcut_rescue, stringResource(R.string.rescue_center), DataStoreUtils.burgerMenuToggleKey(id), true)
        else -> null
    }

    /** Hook of the global settings reset (UI tab): default order + every toggle on. */
    fun reset(context: Context) {
        val edit = DataStoreUtils.prefs(context).edit()
        edit.putString(DataStoreUtils.KEY_BURGER_MENU_ORDER, serializeOrder(allItemIds))
        allItemIds.forEach { id ->
            edit.putBoolean(DataStoreUtils.burgerMenuToggleKey(id), true)
        }
        edit.apply()
    }
}
