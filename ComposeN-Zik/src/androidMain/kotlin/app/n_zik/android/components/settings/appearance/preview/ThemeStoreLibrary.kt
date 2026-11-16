package app.n_zik.android.components.settings.appearance.preview

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

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

/** Resolves an open restored detail only after its local copy has been bound to a verified Store revision. */
internal fun SavedTheme.restoredStoreDownload(downloads: List<SavedTheme>): SavedTheme? {
    if (!imported || downloads.any { it.id == id }) return null
    val local = theme ?: return null
    val origin = storeOrigin ?: return null
    if (local.themeId == origin.themeId || origin.themeId == BundledThemeId) return null
    val appearanceHash = document.encodeToByteArray().sha256()
    return downloads.firstOrNull { saved ->
        val trusted = saved.theme
        !saved.imported && trusted != null && trusted.themeId == origin.themeId && trusted.revision == origin.revision &&
            local.copy(themeId = trusted.themeId, publishedOn = trusted.publishedOn).withMinimumCompatibility() ==
                trusted.withMinimumCompatibility() &&
            appearanceHash == trusted.appearance.sha256 && saved.document == document
    }
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

    fun observeDownloads(): Flow<List<SavedTheme>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith(downloadPrefix)) trySend(downloads())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(downloads())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate().distinctUntilChanged()

    fun restoredStoreOrigins(): List<ThemeStoreOrigin> = downloads().mapNotNull { saved ->
        saved.storeOrigin?.takeIf { origin ->
            saved.imported && saved.theme != null && saved.theme.themeId != origin.themeId &&
                origin.themeId != BundledThemeId
        }
    }.distinct()

    fun hasRestoredStoreUpdates(notificationsEnabled: Boolean): Boolean = restoredStoreOrigins().any { origin ->
        when (updateMode(origin.themeId)) {
            ThemeUpdateMode.AutoDownload -> true
            ThemeUpdateMode.Notify -> notificationsEnabled
            ThemeUpdateMode.Off -> false
        }
    }

    fun save(theme: SavedTheme) = synchronized(downloadMutationLock) {
        preferences.edit {
            putString(downloadPrefix + theme.id, Json.encodeToString(theme))
            putLong(savedTimePrefix + theme.id, System.currentTimeMillis())
        }
    }

    fun saveLocalEdit(original: SavedTheme, edited: SavedTheme): SavedTheme = synchronized(downloadMutationLock) {
        val before = requireNotNull(original.theme)
        val after = requireNotNull(edited.theme)
        require(original.imported && edited.imported && original.id == edited.id && before.themeId != BundledThemeId)
        val unchanged = before.title == after.title && before.description == after.description &&
            before.tags == after.tags && before.compatibility == after.compatibility && before.previews == after.previews &&
            Json.parseToJsonElement(original.document) == Json.parseToJsonElement(edited.document)
        val current = download(original.id)
        val local = edited.copy(storeOrigin = null)
        if (current != null) {
            check(current.imported && (current == original || current == local)) { "The local theme changed while editing" }
            if (unchanged || current == local) return@synchronized current
        } else {
            val restored = checkNotNull(original.restoredStoreDownload(downloads())) { "The local theme was removed" }
            if (unchanged) return@synchronized restored
        }
        // An explicit local edit becomes its own copy; the verified Store revision stays immutable.
        save(local)
        local
    }

    fun recentlySavedFirst(themes: List<SavedTheme>, catalog: List<StoreTheme>): List<SavedTheme> {
        val catalogOrder = catalog.mapIndexed { index, theme -> theme.themeId to index }.toMap()
        val dated = themes.map { saved ->
            val localTime = preferences.getLong(savedTimePrefix + saved.id, 0L)
            val publication = saved.theme?.publishedOn?.let { date ->
                runCatching { LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
            }
            val time = if (saved.imported) localTime.takeIf { it > 0 } ?: publication ?: 0L else publication ?: localTime
            saved to time
        }
        return dated.sortedWith(
            compareByDescending<Pair<SavedTheme, Long>> { it.second }
                .thenByDescending { (saved) -> saved.theme?.themeId?.let(catalogOrder::get) ?: -1 }
                .thenByDescending { (saved) -> saved.theme?.revision ?: 0 },
        ).map { it.first }
    }

    fun restoreImported(themes: List<SavedTheme>): Int = synchronized(downloadMutationLock) {
        require(themes.all { it.imported && it.theme != null && it.theme.themeId != BundledThemeId })
        val pending = themes.distinctBy(SavedTheme::id).filterNot { preferences.contains(downloadPrefix + it.id) }
        if (pending.isNotEmpty()) {
            val editor = preferences.edit()
            val now = System.currentTimeMillis()
            pending.forEach {
                editor.putString(downloadPrefix + it.id, Json.encodeToString(it))
                editor.putLong(savedTimePrefix + it.id, now)
            }
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
            if (theme.copy(themeId = trusted.themeId, publishedOn = trusted.publishedOn).withMinimumCompatibility() !=
                trusted.withMinimumCompatibility() ||
                local.document.encodeToByteArray().sha256() != trusted.appearance.sha256) continue

            val restored = SavedTheme(trusted.title, local.document, trusted)
            val existing = download(restored.id)
            if (existing != null && (existing.imported || existing.theme?.appearance != trusted.appearance ||
                    existing.document.encodeToByteArray().sha256() != trusted.appearance.sha256)) continue
            if (existing == null) {
                editor.putString(downloadPrefix + restored.id, Json.encodeToString(restored))
            }
            editor.remove(downloadPrefix + local.id)
            editor.remove(savedTimePrefix + local.id)
            bound++
        }
        if (bound > 0) check(editor.commit()) { "Could not update restored theme origins" }
        bound
    }

    fun removeDownload(id: String) = synchronized(downloadMutationLock) {
        preferences.edit {
            remove(downloadPrefix + id)
            remove(savedTimePrefix + id)
        }
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

    fun hasNotificationSubscriptions(): Boolean =
        preferences.all.keys.any { it.startsWith("themeStore.publications.") } ||
            downloads().any { !it.imported && it.theme?.let { theme -> updateMode(theme.themeId) == ThemeUpdateMode.Notify } == true }

    fun hasAutomaticDownloads(): Boolean =
        downloads().any { !it.imported && it.theme?.let { theme -> updateMode(theme.themeId) == ThemeUpdateMode.AutoDownload } == true }

    fun newPublications(themes: List<StoreTheme>): List<StoreTheme> = themes.filter { theme ->
        val seen = preferences.getStringSet("themeStore.publications.${theme.author.githubId}", null)
        seen != null && theme.themeId !in seen
    }

    fun markPublicationNotified(theme: StoreTheme) {
        val key = "themeStore.publications.${theme.author.githubId}"
        val seen = preferences.getStringSet(key, null) ?: return
        preferences.edit { putStringSet(key, seen + theme.themeId) }
    }

    companion object {
        fun isDownloadPreference(key: String): Boolean =
            key.startsWith(downloadPrefix) || key.startsWith(savedTimePrefix)

        private val downloadMutationLock = Any()
        private const val savedTimePrefix = "themeStore.savedAt."
        private const val downloadPrefix = "themeStore.download."
        private const val followPrefix = "themeStore.follow."
    }
}
