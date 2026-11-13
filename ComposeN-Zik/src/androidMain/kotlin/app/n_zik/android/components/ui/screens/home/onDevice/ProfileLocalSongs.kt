package app.n_zik.android.components.ui.screens.home.onDevice

import android.content.Context
import android.content.ContextWrapper
import app.n_zik.android.enums.OnDeviceSongSortBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.core.utils.getLocalSongs
import app.n_zik.android.components.ui.screens.profiles.profileDataDir
import java.io.File

internal fun blockedLocalFoldersFile(context: Context, profile: String): File {
    val directory = if (profile == "default") context.filesDir else profileDataDir(context, profile)
    return directory.resolve("Blacklisted_paths.txt")
}

/** The scanner reads its folder exclusions relative to the context's files directory. */
internal fun Context.getProfileLocalSongs(sortBy: OnDeviceSongSortBy, sortOrder: SortOrder) =
    object : ContextWrapper(this) {
        override fun getFilesDir(): File =
            requireNotNull(blockedLocalFoldersFile(baseContext, getActiveProfile(baseContext)).parentFile)
    }.getLocalSongs(sortBy, sortOrder)
