package app.n_zik.android.components.ui.screens.home.onDevice

import android.content.Context
import android.content.ContextWrapper
import app.it.fast4x.rimusic.enums.OnDeviceSongSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.kreate.android.me.knighthat.utils.getLocalSongs
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
