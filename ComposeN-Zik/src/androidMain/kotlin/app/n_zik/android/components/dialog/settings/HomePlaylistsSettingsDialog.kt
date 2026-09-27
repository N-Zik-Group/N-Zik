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
import app.n_zik.android.components.dialog.common.Dialog
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.it.fast4x.rimusic.utils.showYtPlaylistsKey

import app.it.fast4x.rimusic.utils.showPinnedPlaylistsKey
import app.it.fast4x.rimusic.utils.showMonthlyPlaylistsKey
import app.it.fast4x.rimusic.utils.homePlaylistsOrderKey
import app.kreate.android.me.knighthat.utils.Toaster
import org.json.JSONArray
import sh.calvin.reorderable.rememberReorderableLazyListState
import android.content.Context
private val playlistsDefaultOrder = listOf("all", "pinned_playlists", "rewind", "yt_playlists")

object HomePlaylistsSettingsDialog : Dialog {
    override val dialogTitle: String @Composable get() = stringResource(R.string.home_playlists_settings)
    override var isActive: Boolean by mutableStateOf(false)

    private fun parseOrder(s: String): List<String> {
        if (s.isBlank()) return playlistsDefaultOrder
        return try { val a = JSONArray(s); val l = mutableListOf<String>(); for (i in 0 until a.length()) l.add(a.getString(i)); val r = l.filter { it in playlistsDefaultOrder }.toMutableList(); for (id in playlistsDefaultOrder) { if (id !in r) r.add(id) }; r } catch (_: Exception) { playlistsDefaultOrder }
    }
    private fun serializeOrder(order: List<String>): String { val a = JSONArray(); order.forEach { a.put(it) }; return a.toString() }

    @Composable
    override fun DialogBody() {
        val ctx = LocalContext.current
        val prefs = remember { ctx.getSharedPreferences("preferences", Context.MODE_PRIVATE) }
        var workingOrder by remember { mutableStateOf(parseOrder(prefs.getString(homePlaylistsOrderKey, "") ?: "").toMutableList()) }

        val prefKeys = mapOf(
            "yt_playlists" to showYtPlaylistsKey,
            "pinned_playlists" to showPinnedPlaylistsKey,
            "rewind" to showMonthlyPlaylistsKey
        )

        var workingToggles by remember {
            mutableStateOf(
                playlistsDefaultOrder.associateWith { id ->
                    val pk = prefKeys[id]
                    if (pk != null) prefs.getBoolean(pk, true) else true
                }.toMutableMap()
            )
        }

        val allLabel = stringResource(R.string.all)
        val ytLabel = stringResource(R.string.yt_playlists)

        val pinnedLabel = stringResource(R.string.pinned_playlists)
        val rewindLabel = stringResource(R.string.rewind)

        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val o = workingOrder.toMutableList(); val fi = o.indexOf(from.key); val ti = o.indexOf(to.key)
            if (fi != -1 && ti != -1) { val item = o.removeAt(fi); o.add(ti, item); workingOrder = o }
        }

        val items = workingOrder.map { id ->
            when (id) {
                "all" -> ToggleItem(id, R.drawable.library, allLabel, "always_true_playlists", true)
                "yt_playlists" -> ToggleItem(id, R.drawable.logo_youtube, ytLabel, showYtPlaylistsKey, true)

                "pinned_playlists" -> ToggleItem(id, R.drawable.pin_filled, pinnedLabel, showPinnedPlaylistsKey, true)
                "rewind" -> ToggleItem(id, R.drawable.calendar, rewindLabel, showMonthlyPlaylistsKey, true)
                else -> null
            }
        }.filterNotNull()

        ToggleListDialog(
            items = items, lazyListState = lazyListState, reorderableState = reorderableState,
            enforceMinOneChecked = true,
            lockedCheckedIds = setOf("all"),
            checkedStatesOverride = items.map { workingToggles[it.id] ?: true },
            onCheckedChange = { index, newValue ->
                val id = items[index].id
                val m = workingToggles.toMutableMap()
                m[id] = newValue
                workingToggles = m
            },
            onReset = {
                workingOrder = playlistsDefaultOrder.toMutableList()
                val m = playlistsDefaultOrder.associateWith { true }.toMutableMap()
                workingToggles = m
            },
            onCancel = { hideDialog() },
            onConfirm = {
                val edit = prefs.edit()
                edit.putString(homePlaylistsOrderKey, serializeOrder(workingOrder))
                prefKeys.forEach { (id, pk) ->
                    edit.putBoolean(pk, workingToggles[id] ?: true)
                }
                edit.apply()
                Toaster.s(R.string.toast_preference_saved); hideDialog()
            }
        )
    }

    fun reset(context: Context) {
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        prefs.edit()
            .putString(homePlaylistsOrderKey, serializeOrder(playlistsDefaultOrder))
            .putBoolean(showYtPlaylistsKey, true)
            .putBoolean(showPinnedPlaylistsKey, true)
            .putBoolean(showMonthlyPlaylistsKey, true)
            .apply()
    }
}
