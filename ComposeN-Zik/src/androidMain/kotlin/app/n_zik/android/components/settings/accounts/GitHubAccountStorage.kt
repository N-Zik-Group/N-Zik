package app.n_zik.android.components.settings.accounts

import android.content.SharedPreferences
import org.json.JSONObject

/** Keeps the account identity, credentials and logout boundary with the same profile. */
internal class GitHubAccountStorage(
    private val activeProfile: () -> String,
    private val identityStore: (String) -> SharedPreferences,
    private val encryptedStore: (String) -> SharedPreferences,
    private val legacyIdentityStore: SharedPreferences,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class CachedSession(val profile: String, val id: Long, val token: String, val expires: Long)
    @Volatile private var cachedSession: CachedSession? = null

    private fun identity(): JSONObject? {
        val profile = activeProfile()
        val preferences = identityStore(profile)
        parseIdentity(preferences.getString("identity", null))?.let { return it }
        if (preferences.getBoolean("signedOut", false)) return null
        val restored = runCatching {
            val encrypted = encryptedStore(profile)
            parseIdentity(encrypted.getString("githubBackupIdentity", null)) ?: run {
                val legacy = parseIdentity(legacyIdentityStore.getString("identity", null)) ?: return@run null
                val credentials = encrypted.getString("githubPublicationSession", null)?.let(::JSONObject)
                legacy.takeIf { credentials?.optLong("id") == it.getLong("id") }
            }
        }.getOrNull() ?: return null
        preferences.edit().putString("identity", restored.toString()).apply()
        return restored
    }

    fun userId(): Long? = identity()?.getLong("id")
    fun login(): String? = identity()?.getString("login")

    fun tokenFor(id: Long): String? {
        if (userId() != id) return null
        val profile = activeProfile()
        if (identityStore(profile).getBoolean("publicationTokenInvalidated", false)) return null
        cachedSession?.takeIf { it.profile == profile && it.id == id && it.expires > now() }?.let { return it.token }
        return runCatching {
            val session = JSONObject(encryptedStore(profile).getString("githubPublicationSession", null) ?: return null)
            session.getString("token").takeIf { session.getLong("id") == id && session.getLong("expires") > now() }
        }.getOrNull()
    }

    fun save(id: Long, login: String, token: String, expires: Long, profile: String = activeProfile()): Boolean {
        if (activeProfile() != profile) return false
        val identity = checkNotNull(parseIdentity(JSONObject().put("id", id).put("login", login).toString())).toString()
        val credentials = JSONObject().put("id", id).put("token", token).put("expires", expires)
        runCatching {
            encryptedStore(profile).edit().putString("githubPublicationSession", credentials.toString())
                .putString("githubBackupIdentity", identity).apply()
        }
        identityStore(profile).edit().putString("identity", identity).remove("signedOut")
            .remove("publicationTokenInvalidated").apply()
        cachedSession = CachedSession(profile, id, token, expires)
        return true
    }

    fun clearPublicationToken(profile: String = activeProfile()) {
        if (cachedSession?.profile == profile) cachedSession = null
        identityStore(profile).edit().putBoolean("publicationTokenInvalidated", true).apply()
        runCatching { encryptedStore(profile).edit().remove("githubPublicationSession").apply() }
    }

    fun needsWorkflow(id: Long): Boolean = identityStore(activeProfile()).getBoolean("publicationWorkflowScope:$id", false)

    fun requireWorkflow() {
        val id = userId() ?: return
        identityStore(activeProfile()).edit().putBoolean("publicationWorkflowScope:$id", true).apply()
        clearPublicationToken()
    }

    fun disconnect() {
        // Keep a durable boundary even if the encrypted store cannot currently be opened.
        identityStore(activeProfile()).edit().clear().putBoolean("signedOut", true).apply()
        clearPublicationToken()
        runCatching { encryptedStore(activeProfile()).edit().remove("githubBackupIdentity").apply() }
    }

    /** An explicit backup import may restore identity, but never reuses an old publication token. */
    fun restoreIdentity(value: String, profile: String = activeProfile()) {
        val identity = parseIdentity(value) ?: return
        clearPublicationToken(profile)
        check(identityStore(profile).edit().putString("identity", identity.toString()).remove("signedOut").commit()) {
            "Could not restore the GitHub account identity"
        }
    }

    private fun parseIdentity(value: String?): JSONObject? = runCatching {
        value?.let(::JSONObject)?.takeIf {
            it.getLong("id") > 0 && it.getString("login").matches(Regex("[A-Za-z0-9-]{1,39}"))
        }
    }.getOrNull()
}
