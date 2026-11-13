package app.n_zik.android.core.profiles

import android.content.Context
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.core.rescue.ProfileResetSnapshot
import java.io.File
import java.util.UUID
import timber.log.Timber

/** Holds the process's immutable view of the old Media3 roots while separation is pending. */
@UnstableApi
internal object LegacyMediaAccess {
    private data class Prepared(
        val profileId: String,
        val downloads: ReadOnlyProfileCache?,
        val streaming: ReadOnlyProfileCache?,
        val downloadIndex: String?,
    )

    @Volatile
    private var prepared: Prepared? = null

    fun isProtected(context: Context, id: String, item: ProfileDataItem): Boolean =
        if (id == DEFAULT_PROFILE_ID) {
            isLegacyStorageShared(context, id, ProfileDataItem.DOWNLOADS) ||
                isLegacyStorageShared(context, id, ProfileDataItem.MEDIA_CACHE)
        } else isLegacyStorageShared(context, id, item)

    fun reader(context: Context, id: String, item: ProfileDataItem): ReadOnlyProfileCache? {
        if (!isProtected(context, id, item)) return null
        val current = prepared?.takeIf { it.profileId == id }
        return (if (item == ProfileDataItem.DOWNLOADS) current?.downloads else current?.streaming)
            ?: ReadOnlyProfileCache.empty()
    }

    fun downloadIndex(context: Context, id: String, normalName: String): String {
        if (!isProtected(context, id, ProfileDataItem.DOWNLOADS)) return normalName
        return checkNotNull(prepared?.takeIf { it.profileId == id }?.downloadIndex) {
            "Could not prepare a private view of the legacy download index"
        }
    }

    /** Runs before the app's Media3 services can open their cache roots. */
    fun prepare(context: Context, id: String) {
        val readDownloads = isProtected(context, id, ProfileDataItem.DOWNLOADS)
        val readStreaming = isProtected(context, id, ProfileDataItem.MEDIA_CACHE)
        if (!readDownloads && !readStreaming) return
        val root = context.cacheDir.canonicalFile.resolve(".legacy-media")
        runCatching {
            ProfileResetSnapshot.deleteVerifiedTree(root)
            check(root.mkdir())
        }.onFailure { Timber.tag("LegacyMediaAccess").w(it, "Could not clear legacy Media3 read preparation") }
        val index = context.getDatabasePath(ProfileDataDirNames.INDEX_DB_FILE)

        fun read(directory: File?): ReadOnlyProfileCache? {
            if (directory == null) return null
            val parent = checkNotNull(directory.parentFile).canonicalFile
            return runCatching { ReadOnlyProfileCache.open(parent.resolve(directory.name), index, root) }
                .onFailure { Timber.tag("LegacyMediaAccess").w(it, "Could not prepare a legacy Media3 reader") }
                .getOrNull()
        }

        val downloadIndex = if (readDownloads) runCatching {
            val snapshot = root.resolve("downloads-${UUID.randomUUID()}")
            check(snapshot.mkdirs())
            val source = checkNotNull(index.parentFile).canonicalFile
            listOf("", "-wal", "-journal").forEach { suffix ->
                val file = index.name + suffix
                ProfileResetSnapshot.copyTree(source.resolve(file), snapshot.resolve(file))
            }
            snapshot.resolve(index.name).path
        }.onFailure { Timber.tag("LegacyMediaAccess").w(it, "Could not read the legacy download index") }
            .getOrNull() else null

        prepared = Prepared(
            id,
            if (readDownloads) read(downloadsDir(context, id)) else null,
            if (readStreaming) read(mediaCacheDir(context, id)) else null,
            downloadIndex,
        )
    }
}
