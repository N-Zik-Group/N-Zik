package app.n_zik.android.listentogether

import android.content.Context
import kotlinx.serialization.json.Json

import app.n_zik.android.listentogether.ListenTogetherClient.Companion.PREF_BLOCKED_USERS
import app.n_zik.android.listentogether.ListenTogetherClient.Companion.PREF_IS_HOST
import app.n_zik.android.listentogether.ListenTogetherClient.Companion.PREF_ROOM_CODE
import app.n_zik.android.listentogether.ListenTogetherClient.Companion.PREF_SESSION_TIMESTAMP
import app.n_zik.android.listentogether.ListenTogetherClient.Companion.PREF_SESSION_TOKEN
import app.n_zik.android.listentogether.ListenTogetherClient.Companion.PREF_USER_ID
import app.n_zik.android.utils.DataStoreUtils

internal class ListenTogetherSessionPreferences(private val context: Context) {
    class Session(
        val token: String,
        val roomCode: String,
        val userId: String,
        val isHost: Boolean,
        val timestamp: Long,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun read(): Session = Session(
        token = DataStoreUtils.getString(context, PREF_SESSION_TOKEN),
        roomCode = DataStoreUtils.getString(context, PREF_ROOM_CODE),
        userId = DataStoreUtils.getString(context, PREF_USER_ID),
        isHost = DataStoreUtils.getBoolean(context, PREF_IS_HOST, false),
        timestamp = DataStoreUtils.getString(context, PREF_SESSION_TIMESTAMP).toLongOrNull() ?: 0L,
    )

    fun save(
        token: String,
        roomCode: () -> String,
        userId: () -> String,
        isHost: () -> Boolean,
    ) {
        DataStoreUtils.saveString(context, PREF_SESSION_TOKEN, token)
        DataStoreUtils.saveString(context, PREF_ROOM_CODE, roomCode())
        DataStoreUtils.saveString(context, PREF_USER_ID, userId())
        DataStoreUtils.saveBoolean(context, PREF_IS_HOST, isHost())
        DataStoreUtils.saveString(context, PREF_SESSION_TIMESTAMP, System.currentTimeMillis().toString())
    }

    fun clear() {
        val editor = DataStoreUtils.prefs(context).edit()
        editor.remove(PREF_SESSION_TOKEN)
        editor.remove(PREF_ROOM_CODE)
        editor.remove(PREF_USER_ID)
        editor.remove(PREF_IS_HOST)
        editor.remove(PREF_SESSION_TIMESTAMP)
        editor.apply()
    }

    fun readBlockedUsernames(): Set<String> {
        val blockedJson = DataStoreUtils.getString(context, PREF_BLOCKED_USERS)
        return if (blockedJson.isNotEmpty()) {
            json.decodeFromString<List<String>>(blockedJson).toSet()
        } else {
            emptySet()
        }
    }

    fun saveBlockedUsernames(usernames: Set<String>) {
        val blockedJson = json.encodeToString(usernames.toList())
        DataStoreUtils.saveString(context, PREF_BLOCKED_USERS, blockedJson)
    }
}
