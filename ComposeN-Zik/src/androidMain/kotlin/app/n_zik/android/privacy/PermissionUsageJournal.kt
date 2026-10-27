package app.n_zik.android.privacy

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.content.Context
import android.content.SharedPreferences
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/** Only coarse operation metadata is stored: never device names, files, audio or account data. */
enum class PermissionFeature { MUSIC_RECOGNITION, VOICE_SEARCH, VISUALIZER, LOCAL_LIBRARY, AUDIO_DEVICES, PLAYBACK, DOWNLOADS, REWIND, LISTEN_TOGETHER, ONBOARDING, PC_PAIRING, PC_SERVER }
enum class JournalPermission { MICROPHONE, MEDIA_AUDIO, BLUETOOTH, NOTIFICATIONS, CAMERA, LOCAL_NETWORK }
enum class PermissionEventKind { REQUESTED, GRANTED, DENIED, USED }
data class PermissionUsageEvent(val timestamp: Long, val feature: PermissionFeature, val permission: JournalPermission, val kind: PermissionEventKind)

object PermissionUsageJournal {
    const val PAUSED_KEY = "permissionUsageJournalPaused"
    internal const val EVENTS_KEY = "permissionUsageJournalEvents"
    const val MAX_EVENTS = 200
    private const val DEDUP_WINDOW_MS = 30_000L
    private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
    private data class Command(val prefs: SharedPreferences, val event: PermissionUsageEvent?)
    private val commands = Channel<Command>(capacity = 256)

    init {
        scope.launch {
            for (command in commands) runCatching {
                if (command.event == null) clearNow(command.prefs) else appendNow(command.prefs, command.event)
            }.onFailure { Timber.tag("PermissionJournal").w(it, "Could not persist permission metadata") }
        }
    }

    /**
     * Best-effort, nonblocking admission to the bounded DATA writer. Returns false when
     * paused, filtered, or full; accepted events still follow the 200-event retention policy.
     */
    fun record(context: Context, feature: PermissionFeature, permission: JournalPermission, kind: PermissionEventKind): Boolean {
        if (kind == PermissionEventKind.USED && permission == JournalPermission.NOTIFICATIONS &&
            Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        // Capture the originating profile before dispatch; switching profiles cannot reassign an event.
        val prefs = context.preferences
        if (prefs.getBoolean(PAUSED_KEY, false)) return false
        return enqueue(Command(prefs, PermissionUsageEvent(System.currentTimeMillis(), feature, permission, kind)))
    }

    fun request(context: Context, feature: PermissionFeature, permission: String) {
        fromAndroidPermission(permission)?.let { record(context, feature, it, PermissionEventKind.REQUESTED) }
    }

    fun result(context: Context, feature: PermissionFeature, permission: String, granted: Boolean) {
        fromAndroidPermission(permission)?.let {
            record(context, feature, it, if (granted) PermissionEventKind.GRANTED else PermissionEventKind.DENIED)
        }
    }

    internal fun fromAndroidPermission(permission: String): JournalPermission? = when (permission) {
        Manifest.permission.RECORD_AUDIO -> JournalPermission.MICROPHONE
        Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE -> JournalPermission.MEDIA_AUDIO
        Manifest.permission.BLUETOOTH_CONNECT -> JournalPermission.BLUETOOTH
        Manifest.permission.POST_NOTIFICATIONS -> JournalPermission.NOTIFICATIONS
        Manifest.permission.CAMERA -> JournalPermission.CAMERA
        "android.permission.ACCESS_LOCAL_NETWORK" -> JournalPermission.LOCAL_NETWORK
        else -> null
    }

    /** Returns whether the clear was queued; callers must not report success on rejection. */
    fun clear(context: Context): Boolean = enqueue(Command(context.preferences, null))

    private fun enqueue(command: Command): Boolean {
        // Both public operations enter the same FIFO directly, preserving clear/record order.
        val accepted = commands.trySend(command).isSuccess
        if (!accepted) Timber.tag("PermissionJournal").w("Permission metadata queue full; operation rejected")
        return accepted
    }

    fun observe(prefs: SharedPreferences): Flow<List<PermissionUsageEvent>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == EVENTS_KEY) trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate().map { withContext(NzikDispatchers.DATA) { read(prefs) } }

    internal fun read(prefs: SharedPreferences): List<PermissionUsageEvent> = runCatching {
        val array = JSONArray(prefs.getString(EVENTS_KEY, "[]"))
        (0 until minOf(array.length(), MAX_EVENTS)).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                PermissionUsageEvent(item.getLong("time"), PermissionFeature.valueOf(item.getString("feature")),
                    JournalPermission.valueOf(item.getString("permission")), PermissionEventKind.valueOf(item.getString("kind")))
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    @Synchronized internal fun appendNow(prefs: SharedPreferences, event: PermissionUsageEvent) {
        if (prefs.getBoolean(PAUSED_KEY, false)) return
        val events = read(prefs)
        if (event.kind == PermissionEventKind.USED && events.any {
            it.feature == event.feature && it.permission == event.permission && it.kind == event.kind &&
                event.timestamp - it.timestamp in 0 until DEDUP_WINDOW_MS
        }) return
        val array = JSONArray()
        (listOf(event) + events).take(MAX_EVENTS).forEach {
            array.put(JSONObject().put("time", it.timestamp).put("feature", it.feature.name)
                .put("permission", it.permission.name).put("kind", it.kind.name))
        }
        prefs.edit().putString(EVENTS_KEY, array.toString()).commit()
    }

    @Synchronized internal fun clearNow(prefs: SharedPreferences) {
        prefs.edit().remove(EVENTS_KEY).commit()
    }
}
