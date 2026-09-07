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
import app.it.fast4x.rimusic.utils.showFavoritesArtistKey
import app.it.fast4x.rimusic.utils.showDislikedArtistKey
import app.it.fast4x.rimusic.utils.homeArtistsOrderKey
import app.kreate.android.me.knighthat.utils.Toaster
import org.json.JSONArray
import sh.calvin.reorderable.rememberReorderableLazyListState
import android.content.Context

private val artistsDefaultOrder = listOf("all", "favorites", "disliked")

object HomeArtistsSettingsDialog : Dialog {
    override val dialogTitle: String @Composable get() = stringResource(R.string.home_artists_settings)
    override var isActive: Boolean by mutableStateOf(false)

    private fun parseOrder(s: String): List<String> {
        if (s.isBlank()) return artistsDefaultOrder
        return try { val a = JSONArray(s); val l = mutableListOf<String>(); for (i in 0 until a.length()) l.add(a.getString(i)); val r = l.filter { it in artistsDefaultOrder }.toMutableList(); for (id in artistsDefaultOrder) { if (id !in r) r.add(id) }; r } catch (_: Exception) { artistsDefaultOrder }
    }
    private fun serializeOrder(order: List<String>): String { val a = JSONArray(); order.forEach { a.put(it) }; return a.toString() }

    @Composable
    override fun DialogBody() {
        val ctx = LocalContext.current
        val prefs = remember { ctx.getSharedPreferences("preferences", Context.MODE_PRIVATE) }
        var workingOrder by remember { mutableStateOf(parseOrder(prefs.getString(homeArtistsOrderKey, "") ?: "").toMutableList()) }

        val prefKeys = mapOf(
            "favorites" to showFavoritesArtistKey,
            "disliked" to showDislikedArtistKey
        )

        var workingToggles by remember {
            mutableStateOf(
                artistsDefaultOrder.associateWith { id ->
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
                "all" -> ToggleItem(id, R.drawable.people, allLabel, "always_true_artists", true)
                "favorites" -> ToggleItem(id, R.drawable.heart, favLabel, showFavoritesArtistKey, true)
                "disliked" -> ToggleItem(id, R.drawable.dislike, dislikedLabel, showDislikedArtistKey, true)
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
                workingOrder = artistsDefaultOrder.toMutableList()
                val m = artistsDefaultOrder.associateWith { true }.toMutableMap()
                workingToggles = m
            },
            onCancel = { hideDialog() },
            onConfirm = {
                val edit = prefs.edit()
                edit.putString(homeArtistsOrderKey, serializeOrder(workingOrder))
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
            .putString(homeArtistsOrderKey, serializeOrder(artistsDefaultOrder))
            .putBoolean(showFavoritesArtistKey, true)
            .putBoolean(showDislikedArtistKey, true)
            .apply()
    }
}
