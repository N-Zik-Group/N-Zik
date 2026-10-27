package app.n_zik.android.components.dialog.settings

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.components.dialog.common.ContentFramedDialog
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.it.fast4x.rimusic.utils.streamClientWebRemixEnabledKey
import app.it.fast4x.rimusic.utils.streamClientVisionosEnabledKey
import app.it.fast4x.rimusic.utils.streamClientTvEmbeddedEnabledKey
import app.it.fast4x.rimusic.utils.streamClientTvSimplyEnabledKey
import app.it.fast4x.rimusic.utils.streamClientTvHtml5EnabledKey
import app.it.fast4x.rimusic.utils.streamClientAndroidVrEnabledKey
import app.it.fast4x.rimusic.utils.streamClientAndroidCreatorEnabledKey
import app.it.fast4x.rimusic.utils.streamClientIosEnabledKey
import app.it.fast4x.rimusic.utils.streamClientIpadosEnabledKey
import app.it.fast4x.rimusic.utils.streamClientWebEnabledKey
import app.it.fast4x.rimusic.utils.streamClientWebCreatorEnabledKey
import app.it.fast4x.rimusic.utils.streamClientMobileEnabledKey
import app.it.fast4x.rimusic.utils.streamClientAndroidEnabledKey
import app.it.fast4x.rimusic.utils.streamClientRestartNeededKey
import app.it.fast4x.rimusic.utils.streamClientsOrderKey
import app.n_zik.android.playback.services.clearStreamCaches
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.kreate.android.me.knighthat.utils.Toaster
import kotlinx.coroutines.launch
import org.json.JSONArray
import sh.calvin.reorderable.rememberReorderableLazyListState
import android.content.Context

private val defaultClientOrder = listOf(
    "web_remix",
    "android_vr",
    "visionos",
    "tv_embedded",
    "tv_simply",
    "tv_html5",
    "android_creator",
    "android",
    "ios",
    "ipados",
    "web",
    "web_creator",
    "mobile"
)

private data class ClientDef(
    val id: String,
    val preferenceKey: String,
    val iconRes: Int,
    val labelRes: Int,
    val defaultValue: Boolean,
    val descriptionRes: Int? = null
)

private fun buildClientDefs(): Map<String, ClientDef> = mapOf(
    "web_remix" to ClientDef("web_remix", streamClientWebRemixEnabledKey, R.drawable.ytmusic, R.string.stream_client_web_remix, true, R.string.client_youtube_music_web_desc),
    "android_vr" to ClientDef("android_vr", streamClientAndroidVrEnabledKey, R.drawable.musical_notes, R.string.stream_client_android_vr, true, R.string.client_android_vr_desc),
    "visionos" to ClientDef("visionos", streamClientVisionosEnabledKey, R.drawable.musical_notes, R.string.stream_client_visionos, true, R.string.client_visionos_desc),
    "tv_embedded" to ClientDef("tv_embedded", streamClientTvEmbeddedEnabledKey, R.drawable.video, R.string.stream_client_tv_embedded, true, R.string.client_tv_embedded_desc),
    "tv_simply" to ClientDef("tv_simply", streamClientTvSimplyEnabledKey, R.drawable.video, R.string.stream_client_tv_simply, true, R.string.client_tv_simply_desc),
    "tv_html5" to ClientDef("tv_html5", streamClientTvHtml5EnabledKey, R.drawable.video, R.string.stream_client_tv_html5, true, R.string.client_tv_html5_desc),
    "android_creator" to ClientDef("android_creator", streamClientAndroidCreatorEnabledKey, R.drawable.musical_notes, R.string.stream_client_android_creator, true, R.string.client_android_creator_desc),
    "android" to ClientDef("android", streamClientAndroidEnabledKey, R.drawable.musical_notes, R.string.stream_client_android, true, R.string.client_android_desc),
    "ios" to ClientDef("ios", streamClientIosEnabledKey, R.drawable.musical_notes, R.string.stream_client_ios, true, R.string.client_ios_desc),
    "ipados" to ClientDef("ipados", streamClientIpadosEnabledKey, R.drawable.musical_notes, R.string.stream_client_ipados, true, R.string.client_ipados_desc),
    "web" to ClientDef("web", streamClientWebEnabledKey, R.drawable.musical_notes, R.string.stream_client_web, true, R.string.client_web_desc),
    "web_creator" to ClientDef("web_creator", streamClientWebCreatorEnabledKey, R.drawable.musical_notes, R.string.stream_client_web_creator, true, R.string.client_web_creator_desc),
    "mobile" to ClientDef("mobile", streamClientMobileEnabledKey, R.drawable.musical_notes, R.string.stream_client_mobile, true, R.string.client_mobile_desc)
)

object StreamClientsSettingsDialog : ContentFramedDialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.disabled_stream_clients_title)

    override var isActive: Boolean by mutableStateOf(false)

    private fun parseOrder(serialized: String): List<String> {
        if (serialized.isBlank()) return defaultClientOrder
        return try {
            val arr = JSONArray(serialized)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            val validIds = defaultClientOrder.filter { it in buildClientDefs().keys }
            val result = list.filter { it in validIds }.toMutableList()
            for (id in validIds) {
                if (id !in result) result.add(id)
            }
            result
        } catch (_: Exception) {
            defaultClientOrder
        }
    }

    private fun serializeOrder(order: List<String>): String {
        val arr = JSONArray()
        order.forEach { arr.put(it) }
        return arr.toString()
    }

    @Composable
    override fun DialogBody() {
        val context = LocalContext.current
        val binder = LocalPlayerServiceBinder.current
        val prefs = remember { context.getSharedPreferences("preferences", Context.MODE_PRIVATE) }
        val clientDefs = remember { buildClientDefs() }

        val initial = remember {
            val orderSerialized = prefs.getString(streamClientsOrderKey, "") ?: ""
            val order = parseOrder(orderSerialized).toMutableList()
            val toggles = order.map { id ->
                val def = clientDefs[id]
                if (def != null) prefs.getBoolean(def.preferenceKey, def.defaultValue) else true
            }.toMutableList()
            order to toggles
        }

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

        val items = workingOrder.mapNotNull { clientId ->
            val def = clientDefs[clientId] ?: return@mapNotNull null
            ToggleItem(
                id = def.id,
                iconRes = def.iconRes,
                label = stringResource(def.labelRes),
                preferenceKey = def.preferenceKey,
                defaultValue = def.defaultValue,
                description = def.descriptionRes?.let { stringResource(it) }
            )
        }

        ToggleListDialog(
            frameTitle = dialogTitle,
            onDismiss = ::hideDialog,
            items = items,
            lazyListState = lazyListState,
            reorderableState = reorderableState,
            enforceMinOneChecked = true,
            checkedStatesOverride = workingToggles.toList(),
            onCheckedChange = { index, newValue ->
                val newToggles = workingToggles.toMutableList()
                newToggles[index] = newValue
                workingToggles = newToggles
            },
            onReset = {
                workingOrder = defaultClientOrder.toMutableList()
                workingToggles = defaultClientOrder.map { id ->
                    clientDefs[id]?.defaultValue ?: true
                }.toMutableList()
            },
            onCancel = {
                hideDialog()
            },
            onConfirm = {
                // Detect if any enabled/disabled state changed to trigger stream restart
                val initialById = initial.first.zip(initial.second).toMap()
                val hasChanges = workingOrder.any { id ->
                    val idx = workingOrder.indexOf(id)
                    workingToggles.getOrElse(idx) { true } != (initialById[id] ?: true)
                }
                val editor = prefs.edit()
                editor.putString(streamClientsOrderKey, serializeOrder(workingOrder))
                workingOrder.forEachIndexed { index, id ->
                    val def = clientDefs[id] ?: return@forEachIndexed
                    editor.putBoolean(def.preferenceKey, workingToggles[index])
                }
                if (hasChanges) {
                    editor.putBoolean(streamClientRestartNeededKey, true)
                    clearStreamCaches()
                    NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                        clearAudioCache(binder?.cache)
                    }
                    Toaster.i(R.string.preferred_stream_client_changed)
                    Toaster.w(R.string.stream_client_redownload_recommendation)
                }
                editor.apply()
                hideDialog()
            }
        )
    }

    fun reset(context: Context) {
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(streamClientsOrderKey, serializeOrder(defaultClientOrder))
        buildClientDefs().forEach { (_, def) ->
            editor.putBoolean(def.preferenceKey, def.defaultValue)
        }
        editor.apply()
    }
}
