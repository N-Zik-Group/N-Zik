package app.n_zik.android.download.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.isCurrentProcessStorageProfile
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.core.profiles.profileStorageIdentity
import app.n_zik.android.core.profiles.settingsProfileFor

@UnstableApi
internal data class ProfileDownloadCommands(
    val consumer: ProfileStorageIdentity,
    val owner: ProfileStorageIdentity,
) {
    private val pauseKey get() = "downloads_paused_${owner.generation}"

    fun isPaused(context: Context): Boolean =
        profileDataPrefs(context, owner.id).getBoolean(pauseKey, false)

    fun setPaused(context: Context, paused: Boolean) {
        profileDataPrefs(context, owner.id).edit { putBoolean(pauseKey, paused) }
    }

    fun intent(context: Context, action: String): Intent =
        Intent(context, MyDownloadService::class.java).setAction(action).setData(identityUri())

    fun matches(intent: Intent): Boolean = intent.data == identityUri()

    fun stamp(intent: Intent): Intent = Intent(intent).setData(identityUri())

    fun sendAdd(context: Context, request: DownloadRequest): Result<Unit> = runCatching {
        check(this == capture(context)) { "The download profile changed" }
        val intent = DownloadService.buildAddDownloadIntent(context, MyDownloadService::class.java, request, true)
            .setData(identityUri())
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (_: IllegalStateException) {
            context.startService(intent.putExtra(DownloadService.KEY_FOREGROUND, false))
        }
        Unit
    }

    fun sendRemove(context: Context, mediaId: String): Result<Unit> = runCatching {
        check(this == capture(context)) { "The download profile changed" }
        val intent = DownloadService.buildRemoveDownloadIntent(context, MyDownloadService::class.java, mediaId, false)
            .setData(identityUri())
        context.startService(intent)
        Unit
    }

    private fun identityUri(): Uri = Uri.Builder().scheme("nzik-download")
        .authority("profile")
        .appendPath(consumer.id)
        .appendPath(consumer.generation)
        .appendPath(owner.id)
        .appendPath(owner.generation)
        .build()

    companion object {
        fun capture(context: Context): ProfileDownloadCommands? {
            val active = getActiveProfile(context)
            val consumer = profileStorageIdentity(context, active) ?: return null
            if (!isCurrentProcessStorageProfile(context, consumer)) return null
            val ownerId = settingsProfileFor(context, active, ProfileDataItem.DOWNLOADS)
            val owner = profileStorageIdentity(context, ownerId) ?: return null
            return ProfileDownloadCommands(consumer, owner)
        }
    }
}

/** Tags Media3 item commands from old UI callers before Android can queue them across profiles. */
@UnstableApi
internal fun Context.withProfileDownloadCommandOwner(intent: Intent): Intent {
    val component = intent.component ?: return intent
    if (component.packageName != packageName || component.className != MyDownloadService::class.java.name ||
        intent.data != null ||
        (intent.action != DownloadService.ACTION_ADD_DOWNLOAD && intent.action != DownloadService.ACTION_REMOVE_DOWNLOAD)
    ) return intent
    return ProfileDownloadCommands.capture(this)?.stamp(intent) ?: intent
}
