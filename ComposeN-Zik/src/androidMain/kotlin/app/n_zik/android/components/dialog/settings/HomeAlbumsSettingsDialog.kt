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
import app.it.fast4x.rimusic.utils.showFavoritesAlbumKey
import app.it.fast4x.rimusic.utils.showDislikedAlbumKey
import app.it.fast4x.rimusic.utils.homeAlbumsOrderKey
import app.kreate.android.me.knighthat.utils.Toaster
import org.json.JSONArray
import sh.calvin.reorderable.rememberReorderableLazyListState
import android.content.Context

private val albumsDefaultOrder = listOf("all", "favorites", "disliked")

object HomeAlbumsSettingsDialog : Dialog {
    override val dialogTitle: String @Composable get() = stringResource(R.string.home_albums_settings)
    override var isActive: Boolean by mutableStateOf(false)

    private fun parseOrder(s: String): List<String> {
        if (s.isBlank()) return albumsDefaultOrder
        return try { val a = JSONArray(s); val l = mutableListOf<String>(); for (i in 0 until a.length()) l.add(a.getString(i)); val r = l.filter { it in albumsDefaultOrder }.toMutableList(); for (id in albumsDefaultOrder) { if (id !in r) r.add(id) }; r } catch (_: Exception) { albumsDefaultOrder }
    }
    private fun serializeOrder(order: List<String>): String { val a = JSONArray(); order.forEach { a.put(it) }; return a.toString() }

    @Composable
    override fun DialogBody() {
        val ctx = LocalContext.current
        val prefs = remember { ctx.getSharedPreferences("preferences", Context.MODE_PRIVATE) }
        var workingOrder by remember { mutableStateOf(parseOrder(prefs.getString(homeAlbumsOrderKey, "") ?: "").toMutableList()) }

        val prefKeys = mapOf(
            "favorites" to showFavoritesAlbumKey,
            "disliked" to showDislikedAlbumKey
        )

        var workingToggles by remember {
            mutableStateOf(
                albumsDefaultOrder.associateWith { id ->
                    val pk = prefKeys[id]
                    if (pk != null) prefs.getBoolean(pk, true) else true
                }.toMutableMap()
            )
        }

        val allLabel = stringResource(R.string.all)
        val favLabel = stringResource(R.string.favorites)
        val dislikedLabel = stringResource(R.string.disliked)

        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val o = workingOrder.toMutableList(); val fi = o.indexOf(from.key); val ti = o.indexOf(to.key)
            if (fi != -1 && ti != -1) { val item = o.removeAt(fi); o.add(ti, item); workingOrder = o }
        }

        val items = workingOrder.map { id ->
            when (id) {
                "all" -> ToggleItem(id, R.drawable.album, allLabel, "always_true_albums", true)
                "favorites" -> ToggleItem(id, R.drawable.heart, favLabel, showFavoritesAlbumKey, true)
                "disliked" -> ToggleItem(id, R.drawable.dislike, dislikedLabel, showDislikedAlbumKey, true)
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
                workingOrder = albumsDefaultOrder.toMutableList()
                val m = albumsDefaultOrder.associateWith { true }.toMutableMap()
                workingToggles = m
            },
            onCancel = { hideDialog() },
            onConfirm = {
                val edit = prefs.edit()
                edit.putString(homeAlbumsOrderKey, serializeOrder(workingOrder))
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
            .putString(homeAlbumsOrderKey, serializeOrder(albumsDefaultOrder))
            .putBoolean(showFavoritesAlbumKey, true)
            .putBoolean(showDislikedAlbumKey, true)
            .apply()
    }
}
