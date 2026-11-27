package app.n_zik.android.listentogether

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

import app.n_zik.android.listentogether.ListenTogetherClient.Companion.PREF_BLOCKED_USERS
import app.n_zik.android.listentogether.ListenTogetherClient.Companion.PREF_IS_HOST
import app.n_zik.android.listentogether.ListenTogetherClient.Companion.PREF_ROOM_CODE
import app.n_zik.android.listentogether.ListenTogetherClient.Companion.PREF_SESSION_TIMESTAMP
import app.n_zik.android.listentogether.ListenTogetherClient.Companion.PREF_SESSION_TOKEN
import app.n_zik.android.listentogether.ListenTogetherClient.Companion.PREF_USER_ID
import app.n_zik.android.utils.DataStoreUtils

internal class ListenTogetherSessionCredential(val token: String, val serverUrl: HttpUrl) {
    fun tokenFor(connectedServerUrl: HttpUrl): String? = token.takeIf { connectedServerUrl == serverUrl }
}

internal class ListenTogetherSessionPreferences(private val context: Context) {
    private companion object {
        const val PREF_SESSION_SERVER_URL = "listen_together_session_server_url"
    }

    class Session(
        val token: String,
        val roomCode: String,
        val userId: String,
        val isHost: Boolean,
        val timestamp: Long,
        val serverUrl: HttpUrl?,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun read(): Session {
        val values = DataStoreUtils.prefs(context).all
        return Session(
            token = (values[PREF_SESSION_TOKEN] as? String).orEmpty(),
            roomCode = (values[PREF_ROOM_CODE] as? String).orEmpty(),
            userId = (values[PREF_USER_ID] as? String).orEmpty(),
            isHost = values[PREF_IS_HOST] == true,
            timestamp = (values[PREF_SESSION_TIMESTAMP] as? String)?.toLongOrNull() ?: 0L,
            serverUrl = (values[PREF_SESSION_SERVER_URL] as? String)?.toHttpUrlOrNull(),
        )
    }

    fun save(
        token: String,
        roomCode: String,
        userId: String,
        isHost: Boolean,
        timestamp: Long,
        serverUrl: HttpUrl,
    ) {
        DataStoreUtils.prefs(context).edit()
            .putString(PREF_SESSION_TOKEN, token)
            .putString(PREF_ROOM_CODE, roomCode)
            .putString(PREF_USER_ID, userId)
            .putBoolean(PREF_IS_HOST, isHost)
            .putString(PREF_SESSION_TIMESTAMP, timestamp.toString())
            .putString(PREF_SESSION_SERVER_URL, serverUrl.toString())
            .apply()
    }

    fun clear() {
        val editor = DataStoreUtils.prefs(context).edit()
        editor.remove(PREF_SESSION_TOKEN)
        editor.remove(PREF_ROOM_CODE)
        editor.remove(PREF_USER_ID)
        editor.remove(PREF_IS_HOST)
        editor.remove(PREF_SESSION_TIMESTAMP)
        editor.remove(PREF_SESSION_SERVER_URL)
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
