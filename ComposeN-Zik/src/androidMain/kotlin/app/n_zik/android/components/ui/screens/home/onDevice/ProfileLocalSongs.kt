package app.n_zik.android.components.ui.screens.home.onDevice

import android.content.Context
import android.content.ContextWrapper
import app.n_zik.android.enums.OnDeviceSongSortBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.core.utils.getLocalSongs
import app.n_zik.android.components.ui.screens.profiles.profileDataDir
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.updateAndGet
import java.io.File

internal fun blockedLocalFoldersFile(context: Context, profile: String): File {
    val directory = if (profile == DEFAULT_PROFILE_ID) context.filesDir else profileDataDir(context, profile)
    return directory.resolve("Blacklisted_paths.txt")
}

internal fun Context.getProfileLocalSongs(
    sortBy: OnDeviceSongSortBy,
    sortOrder: SortOrder,
    profile: String = getActiveProfile(this),
) = withLocalFolderProfile(profile).getLocalSongs(sortBy, sortOrder)

internal fun Context.withLocalFolderProfile(profile: String): Context = LocalFolderProfileContext(this, profile)

internal class LocalFolderProfileContext(base: Context, val profile: String) : ContextWrapper(base) {
    override fun getFilesDir(): File =
        requireNotNull(blockedLocalFoldersFile(baseContext, profile).parentFile)
}

internal class LocalSongRefreshRequests {
    private val revisions = MutableStateFlow<Map<String, Long>>(emptyMap())

    fun current(profile: String): Long = revisions.value[profile] ?: 0L

    fun forProfile(profile: String) = revisions.map { it[profile] ?: 0L }.distinctUntilChanged()

    fun request(profile: String): Long =
        revisions.updateAndGet { current -> current + (profile to ((current[profile] ?: 0L) + 1L)) }
            .getValue(profile)
}

internal val localSongRefreshRequests = LocalSongRefreshRequests()
