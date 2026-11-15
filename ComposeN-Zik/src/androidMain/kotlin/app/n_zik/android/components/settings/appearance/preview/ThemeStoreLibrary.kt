package app.n_zik.android.components.settings.appearance.preview

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class ThemeStoreOrigin(val themeId: String, val revision: Int)

@Serializable
internal data class SavedTheme(
    val name: String,
    val document: String,
    val theme: StoreTheme? = null,
    val imported: Boolean = false,
    val storeOrigin: ThemeStoreOrigin? = null,
) {
    val id: String get() = theme?.let { "${it.themeId}:${it.revision}" }
        ?: document.encodeToByteArray().sha256()
}

internal enum class ThemeUpdateMode { AutoDownload, Notify, Off }

internal class ThemeStoreLibrary(private val preferences: SharedPreferences) {
    fun updateMode(themeId: String): ThemeUpdateMode =
        ThemeUpdateMode.entries.firstOrNull { it.name == preferences.getString("themeStore.updateMode.$themeId", null) }
            ?: ThemeUpdateMode.Notify

    fun setUpdateMode(themeId: String, mode: ThemeUpdateMode) {
        preferences.edit { putString("themeStore.updateMode.$themeId", mode.name) }
    }

    fun downloads(): List<SavedTheme> =
        preferences.all.filterKeys { it.startsWith(downloadPrefix) }.values.mapNotNull { value ->
            (value as? String)?.let { raw ->
                runCatching { Json.decodeFromString<SavedTheme>(raw) }.getOrNull()
            }
        }.sortedBy { it.name }

    fun download(id: String): SavedTheme? =
        preferences.getString(downloadPrefix + id, null)?.let { raw ->
            runCatching { Json.decodeFromString<SavedTheme>(raw) }.getOrNull()
        }

    fun save(theme: SavedTheme) = synchronized(downloadMutationLock) {
        preferences.edit { putString(downloadPrefix + theme.id, Json.encodeToString(theme)) }
    }

    fun restoreImported(themes: List<SavedTheme>): Int = synchronized(downloadMutationLock) {
        require(themes.all { it.imported && it.theme != null && it.theme.themeId != BundledThemeId })
        val pending = themes.distinctBy(SavedTheme::id).filterNot { preferences.contains(downloadPrefix + it.id) }
        if (pending.isNotEmpty()) {
            val editor = preferences.edit()
            pending.forEach { editor.putString(downloadPrefix + it.id, Json.encodeToString(it)) }
            check(editor.commit()) { "Could not save restored themes" }
        }
        pending.size
    }

    fun bindTrustedStoreOrigins(trustedThemes: List<StoreTheme>): Int = synchronized(downloadMutationLock) {
        val revisions = trustedThemes.associateBy { ThemeStoreOrigin(it.themeId, it.revision) }
        val editor = preferences.edit()
        var bound = 0
        for (local in downloads()) {
            val origin = local.storeOrigin ?: continue
            val theme = local.theme ?: continue
            if (!local.imported || theme.themeId == origin.themeId || origin.themeId == BundledThemeId) continue
            val trusted = revisions[origin] ?: continue
            if (theme.copy(themeId = trusted.themeId, publishedOn = trusted.publishedOn) != trusted ||
                local.document.encodeToByteArray().sha256() != trusted.appearance.sha256) continue

            val restored = SavedTheme(trusted.title, local.document, trusted)
            val existing = download(restored.id)
            if (existing != null && (existing.imported || existing.theme?.appearance != trusted.appearance ||
                    existing.document.encodeToByteArray().sha256() != trusted.appearance.sha256)) continue
            if (existing == null) {
                editor.putString(downloadPrefix + restored.id, Json.encodeToString(restored))
            }
            editor.remove(downloadPrefix + local.id)
            bound++
        }
        if (bound > 0) check(editor.commit()) { "Could not update restored theme origins" }
        bound
    }

    fun removeDownload(id: String) {
        preferences.edit { remove(downloadPrefix + id) }
    }

    fun downloadedThemeUpdates(themes: List<StoreTheme>, mode: ThemeUpdateMode): List<StoreTheme> {
        val downloadedRevisions = downloads().filterNot { it.imported }.mapNotNull { it.theme }
            .groupBy { it.themeId }
            .mapValues { (_, revisions) -> revisions.maxOf { it.revision } }
        return themes.filter { theme ->
            val installed = downloadedRevisions[theme.themeId] ?: return@filter false
            updateMode(theme.themeId) == mode && theme.revision > installed &&
                (mode != ThemeUpdateMode.Notify || theme.revision > preferences.getInt("themeStore.updateSeen.${theme.themeId}", 0))
        }
    }

    fun markThemeUpdateNotified(theme: StoreTheme) {
        preferences.edit { putInt("themeStore.updateSeen.${theme.themeId}", theme.revision) }
    }

    fun follows(): List<StoreAuthor> =
        preferences.all.filterKeys { it.startsWith(followPrefix) }.mapNotNull { (key, value) ->
            val id = key.removePrefix(followPrefix).toLongOrNull() ?: return@mapNotNull null
            (value as? String)?.let { StoreAuthor(id, it) }
        }.sortedBy { it.login }

    fun setFollowed(author: StoreAuthor, followed: Boolean) {
        val key = followPrefix + author.githubId
        preferences.edit {
            if (followed) putString(key, author.login) else remove(key)
        }
    }

    fun publicationsEnabled(id: Long): Boolean = preferences.contains("themeStore.publications.$id")

    fun setPublicationsEnabled(id: Long, enabled: Boolean, themes: List<StoreTheme>) {
        preferences.edit {
            if (enabled) putStringSet("themeStore.publications.$id", themes.filter { it.author.githubId == id }.map { it.themeId }.toSet())
            else remove("themeStore.publications.$id")
        }
    }

    fun newPublications(themes: List<StoreTheme>): List<StoreTheme> {
        val published = mutableListOf<StoreTheme>()
        for ((id, authored) in themes.groupBy { it.author.githubId }) {
            val key = "themeStore.publications.$id"
            val seen = preferences.getStringSet(key, null) ?: continue
            val new = authored.filter { it.themeId !in seen }
            if (new.isNotEmpty()) {
                preferences.edit { putStringSet(key, seen + new.map { it.themeId }) }
                published += new
            }
        }
        return published
    }

    private companion object {
        val downloadMutationLock = Any()
        const val downloadPrefix = "themeStore.download."
        const val followPrefix = "themeStore.follow."
    }
}
