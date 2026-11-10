package app.n_zik.android.core.profiles

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.VersionTable
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.Download
import app.it.fast4x.rimusic.enums.ExoPlayerDiskDownloadCacheMaxSize
import app.it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.profilePreferences
import app.it.fast4x.rimusic.utils.readProfileIds
import app.n_zik.android.core.rescue.ProfileResetSnapshot
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import timber.log.Timber

/** Publishes a coupled legacy Media3 store only where every completed download can be attributed. */
@UnstableApi
internal object LegacyMediaPublication {
    private val sideFiles = listOf("", "-wal", "-journal")
    private val archiveName = Regex("[0-9a-f]{64}")

    data class Archive(val id: String, val sourceLocation: ExoPlayerCacheLocation)

    fun archives(context: Context): List<Archive> = archiveRoot(context).listFiles().orEmpty()
        .filter { it.isDirectory && it.canonicalFile == it.absoluteFile && archiveName.matches(it.name) }
        .sortedByDescending { it.lastModified() }
        .mapNotNull { directory ->
            runCatching { Archive(directory.name, ExoPlayerCacheLocation.valueOf(directory.resolve("source.txt").readText())) }
                .onFailure { Timber.tag("LegacyMedia").w(it, "Could not read protected Media3 archive details") }
                .getOrNull()
        }

    /** The caller holds the Rescue mutation lock and has confirmed this exact archive and profile. */
    fun recover(context: Context, archive: Archive, target: ProfileStorageIdentity) {
        check(target == profileStorageIdentity(context, target.id)) { "The destination profile changed" }
        check(target.id != DEFAULT_PROFILE_ID ||
            !LegacyMediaAccess.isProtected(context, target.id, ProfileDataItem.DOWNLOADS)
        ) { "The base profile's Media3 store is still shared" }
        require(archiveName.matches(archive.id))
        val directory = archiveRoot(context).resolve(archive.id)
        check(directory.canonicalFile == directory.absoluteFile && directory.isDirectory)
        ProfileResetSnapshot.fingerprint(directory.resolve("inventory.txt"))
        ProfileResetSnapshot.fingerprint(directory.resolve("source.txt"))
        val inventory = directory.resolve("inventory.txt").readText()
        check(directory.resolve("source.txt").readText() == archive.sourceLocation.name &&
            archive.id == archiveIdentity(inventory, archive.sourceLocation)
        ) { "The protected source archive changed" }
        val current = legacySource(context)
        val fingerprints = inventory.lineSequence().associate { entry -> entry.substringBefore(' ') to entry.substringAfter(' ') }
        check(fingerprints.keys == current.files.keys) { "Invalid protected Media3 inventory" }
        verifyArchive(directory, fingerprints)
        val root = if (archive.sourceLocation == ExoPlayerCacheLocation.System) "system" else "private"
        val work = workRoot(context)
        check(work.isDirectory || work.mkdirs())
        val operation = listOf(archive.id, archive.sourceLocation.name, target.id, target.generation).joinToString("\n")
        val pending = work.resolve("rescue-recovery.pending")
        ProfileResetSnapshot.fingerprint(pending)
        check(!pending.exists() || pending.readText() == operation) { "Another historical recovery is pending" }
        publish(
            context, target.id, current.copy(downloads = "downloads-$root", streaming = "streaming-$root"), directory, work,
            beforePublication = {
                if (!pending.exists()) {
                    val temporary = work.resolve("rescue-recovery.stage")
                    ProfileResetSnapshot.deleteVerifiedTree(temporary)
                    writeSynced(temporary, operation)
                    check(temporary.renameTo(pending)) { "Could not journal the historical recovery" }
                }
            },
        )
        check(pending.delete()) { "Could not finish the historical recovery" }
    }

    fun resumeRescueRecovery(context: Context) {
        val pending = workRoot(context).resolve("rescue-recovery.pending")
        if (!pending.exists()) return
        ProfileResetSnapshot.fingerprint(pending)
        val fields = pending.readLines()
        check(fields.size == 4) { "The historical recovery journal is invalid" }
        recover(
            context,
            Archive(fields[0], ExoPlayerCacheLocation.valueOf(fields[1])),
            ProfileStorageIdentity(fields[2], fields[3]),
        )
    }

    fun hasPendingRescueRecovery(context: Context): Boolean =
        workRoot(context).resolve("rescue-recovery.pending").exists()

    fun resume(context: Context) {
        val consumers = context.readProfileIds().filter {
            context.profileShares(ProfileDataItem.DOWNLOADS, it) || context.profileShares(ProfileDataItem.MEDIA_CACHE, it)
        }
        if (consumers.isEmpty()) return
        val work = workRoot(context)
        runCatching {
            check(work.isDirectory || work.mkdirs())
            val source = legacySource(context)
            val archive = archive(context, source)
            consumers.forEach { id ->
                runCatching {
                    val downloads = context.profileShares(ProfileDataItem.DOWNLOADS, id)
                    val streaming = context.profileShares(ProfileDataItem.MEDIA_CACHE, id)
                    if (downloads && streaming) {
                        val coupled = runCatching { publish(context, id, source, archive, work) }
                        if (coupled.isFailure) {
                            // Streaming may be independently verifiable even when an old queue cannot be attributed.
                            publishSingle(context, id, ProfileDataItem.MEDIA_CACHE, source, archive, work)
                            throw checkNotNull(coupled.exceptionOrNull())
                        }
                    } else {
                        val item = if (downloads) ProfileDataItem.DOWNLOADS else ProfileDataItem.MEDIA_CACHE
                        publishSingle(context, id, item, source, archive, work)
                    }
                }
                    .onFailure { error ->
                        recordFailure(context, id, error)
                        Timber.tag("LegacyMedia").w(error, "Legacy Media3 separation remains pending for %s", id)
                    }
            }
        }.onFailure { error ->
            consumers.forEach { recordFailure(context, it, error) }
            Timber.tag("LegacyMedia").w(error, "Could not protect the original Media3 store")
        }
    }

    private fun recordFailure(context: Context, id: String, error: Throwable) {
        LegacySeparationStatus.record(context, id, ProfileDataItem.DOWNLOADS, error)
        LegacySeparationStatus.record(context, id, ProfileDataItem.MEDIA_CACHE, error)
    }

    private data class Source(
        val files: Map<String, File>,
        val downloads: String,
        val streaming: String,
    )

    private fun legacySource(context: Context): Source {
        val cache = context.cacheDir.canonicalFile
        val files = context.filesDir.canonicalFile
        val index = context.getDatabasePath(ProfileDataDirNames.INDEX_DB_FILE)
        val indexRoot = checkNotNull(index.parentFile).canonicalFile
        val fromSystem = profileCacheBaseDir(context, DEFAULT_PROFILE_ID).canonicalFile == cache
        return Source(
            files = buildMap {
                put("downloads-system", cache.resolve(ProfileDataDirNames.DOWNLOADS_DIR))
                put("downloads-private", files.resolve(ProfileDataDirNames.DOWNLOADS_DIR))
                put("streaming-system", cache.resolve(ProfileDataDirNames.MEDIA_CACHE_DIR))
                put("streaming-private", files.resolve(ProfileDataDirNames.MEDIA_CACHE_DIR))
                sideFiles.forEach { suffix -> put("index.db$suffix", indexRoot.resolve(index.name + suffix)) }
            },
            downloads = if (fromSystem) "downloads-system" else "downloads-private",
            streaming = if (fromSystem) "streaming-system" else "streaming-private",
        )
    }

    private fun archive(context: Context, source: Source): File {
        val checksums = source.files.mapValues { (_, path) -> ProfileResetSnapshot.fingerprint(path) }
        val inventory = checksums.entries.joinToString("\n") { (key, hash) -> "$key $hash" }
        val root = archiveRoot(context)
        val location = if (source.downloads == "downloads-system") ExoPlayerCacheLocation.System else ExoPlayerCacheLocation.Private
        val identity = archiveIdentity(inventory, location)
        val published = root.resolve(identity)
        if (!published.exists()) {
            val stage = root.resolve(".prepare-$identity")
            ProfileResetSnapshot.deleteVerifiedTree(stage)
            check(stage.mkdirs())
            source.files.forEach { (name, path) ->
                ProfileResetSnapshot.copyTree(path, stage.resolve(name))
                check(ProfileResetSnapshot.fingerprint(stage.resolve(name)) == checksums.getValue(name))
                check(ProfileResetSnapshot.fingerprint(path) == checksums.getValue(name))
            }
            writeSynced(stage.resolve("inventory.txt"), inventory)
            writeSynced(stage.resolve("source.txt"), location.name)
            check(stage.renameTo(published)) { "Could not publish the protected Media3 copy" }
        }
        check(published.resolve("inventory.txt").readText() == inventory &&
            published.resolve("source.txt").readText() == location.name)
        verifyArchive(published, checksums)
        return published
    }

    private fun archiveRoot(context: Context): File =
        context.filesDir.canonicalFile.resolve("protected-profile-history/media3")

    private fun workRoot(context: Context): File = context.filesDir.canonicalFile.resolve(".legacy-media-transfer")

    private fun archiveIdentity(inventory: String, location: ExoPlayerCacheLocation): String =
        MessageDigest.getInstance("SHA-256").digest("${location.name}\n$inventory".encodeToByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun verifyArchive(published: File, checksums: Map<String, String>) {
        check(published.canonicalFile == published.absoluteFile && published.isDirectory)
        checksums.forEach { (name, hash) ->
            check(ProfileResetSnapshot.fingerprint(published.resolve(name)) == hash) {
                "The preserved Media3 source failed verification"
            }
        }
    }

    private fun publish(
        context: Context,
        id: String,
        source: Source,
        archive: File,
        work: File,
        beforePublication: () -> Unit = {},
    ) {
        val identity = checkNotNull(profileStorageIdentity(context, id))
        val roots = resolveProfileDataDirNames(id, emptySet())
        val ownRoot = profileCacheBaseDir(context, id).canonicalFile
        val ownIndex = context.getDatabasePath(roots.downloadsIndexDb).let {
            checkNotNull(it.parentFile).canonicalFile.resolve(it.name)
        }
        check(listOf("-wal", "-journal", "-shm").none { File(ownIndex.path + it).exists() }) {
            "The existing private Media3 index is in use or needs recovery"
        }
        val stage = work.resolve("profile-${ownIndex.name}")
        ProfileResetSnapshot.deleteVerifiedTree(stage)
        check(stage.mkdir())
        try {
            val index = stage.resolve("index.db")
            copyDatabase(archive.resolve(index.name), index)
            val originalDownloads = ReadOnlyProfileCache.open(archive.resolve(source.downloads), index, stage)
            val originalStreaming = ReadOnlyProfileCache.open(archive.resolve(source.streaming), index, stage)
            verifySpanInventory(archive.resolve(source.downloads), originalDownloads)
            verifySpanInventory(archive.resolve(source.streaming), originalStreaming)
            verifyIndexVersions(index, listOf(originalDownloads, originalStreaming))
            val records = completedRows(index)
            val libraryName = if (id == DEFAULT_PROFILE_ID) "data.db" else "data_$id.db"
            val library = context.getDatabasePath(libraryName).let {
                checkNotNull(it.parentFile).canonicalFile.resolve(it.name)
            }
            verifyDownloadOwnership(records, originalDownloads, library, stage)
            val settings = profileDataPrefs(context, id)
            val limit = settings.getEnum(exoPlayerDiskDownloadCacheMaxSizeKey, ExoPlayerDiskDownloadCacheMaxSize.`2GB`)
            verifyQuota(limit, originalDownloads.cacheSpace)
            val downloadDestination = ownRoot.resolve(roots.downloadsDir)
            val streamingDestination = ownRoot.resolve(roots.mediaCacheDir)
            validateDestination(archive.resolve(source.downloads), downloadDestination)
            validateDestination(archive.resolve(source.streaming), streamingDestination)
            validateDestination(index, ownIndex)
            beforePublication()
            prepareDestination(archive.resolve(source.downloads), downloadDestination)
            prepareDestination(archive.resolve(source.streaming), streamingDestination)
            prepareDestination(index, ownIndex)
            verifyCache(originalDownloads, ReadOnlyProfileCache.open(downloadDestination, ownIndex, stage))
            verifyCache(originalStreaming, ReadOnlyProfileCache.open(streamingDestination, ownIndex, stage))
            check(completedRows(ownIndex, stage) == records) { "Download rows differ after migration" }
            check(identity == profileStorageIdentity(context, id)) { "Profile identity changed during migration" }
            val receipt = archive.resolve("consumer-${ownIndex.name}-${identity.generation}.txt")
            writeSynced(receipt, "${identity.id}\n${identity.generation}\n${source.downloads}\n${source.streaming}")
            check(context.profilePreferences.edit()
                .remove(profileShareKey(ProfileDataItem.DOWNLOADS, id))
                .remove(profileShareKey(ProfileDataItem.MEDIA_CACHE, id))
                .commit()
            ) { "Could not publish the separated Media3 store" }
        } finally {
            ProfileResetSnapshot.deleteVerifiedTree(stage)
        }
    }

    /** The private index may already carry the other cache. Import only verified tables for this cache UID. */
    private fun publishSingle(
        context: Context,
        id: String,
        item: ProfileDataItem,
        source: Source,
        archive: File,
        work: File,
    ) {
        val identity = checkNotNull(profileStorageIdentity(context, id))
        val names = resolveProfileDataDirNames(id, emptySet())
        val downloads = item == ProfileDataItem.DOWNLOADS
        val oldRoot = archive.resolve(if (downloads) source.downloads else source.streaming)
        val root = profileCacheBaseDir(context, id).canonicalFile
        val newRoot = root.resolve(if (downloads) names.downloadsDir else names.mediaCacheDir)
        val indexFile = context.getDatabasePath(names.downloadsIndexDb)
        val ownIndex = checkNotNull(indexFile.parentFile).canonicalFile.resolve(indexFile.name)
        val sidecars = listOf("-wal", "-journal", "-shm").map { ownIndex.resolveSibling(ownIndex.name + it) }
        check(sidecars.all { ProfileResetSnapshot.fingerprint(it) == "-" }) {
            "The independent Media3 index is in use or needs recovery"
        }
        val stage = work.resolve("profile-${ownIndex.name}")
        ProfileResetSnapshot.deleteVerifiedTree(stage)
        check(stage.mkdir())
        try {
            val originalIndex = stage.resolve("original.db")
            copyDatabase(archive.resolve("index.db"), originalIndex)
            val original = ReadOnlyProfileCache.open(oldRoot, originalIndex, stage)
            verifySpanInventory(oldRoot, original)
            verifyIndexVersions(originalIndex, listOf(original))
            validateDestination(oldRoot, newRoot)
            val records = if (downloads) completedRows(originalIndex) else emptyList()
            if (downloads) {
                val libraryFile = context.getDatabasePath("data_$id.db")
                val library = checkNotNull(libraryFile.parentFile).canonicalFile.resolve(libraryFile.name)
                verifyDownloadOwnership(records, original, library, stage)
                val limit = profileDataPrefs(context, id)
                    .getEnum(exoPlayerDiskDownloadCacheMaxSizeKey, ExoPlayerDiskDownloadCacheMaxSize.`2GB`)
                verifyQuota(limit, original.cacheSpace)
            }
            val complementaryName = if (downloads) names.mediaCacheDir else names.downloadsDir
            val complementary = listOf(context.cacheDir, context.filesDir).map { base ->
                val path = base.canonicalFile.resolve(complementaryName)
                ProfileResetSnapshot.validateTree(path)
                path to ReadOnlyProfileCache.open(path, ownIndex, stage)
            }
            check(original.uid < 0 || complementary.none { (_, cache) -> cache.uid == original.uid }) {
                "The verified cache UID is already used by the independent cache"
            }
            val existingHash = ProfileResetSnapshot.fingerprint(ownIndex)
            val index = stage.resolve("index.db")
            if (originalIndex.exists()) {
                ProfileResetSnapshot.copyTree(ownIndex, index)
                if (mergeCacheIndex(originalIndex, index, original.uid, downloads)) {
                    complementary.forEach { (path, cache) ->
                        verifyCache(cache, ReadOnlyProfileCache.open(path, index, stage))
                    }
                }
            }
            prepareDestination(oldRoot, newRoot)
            if (index.exists() && ProfileResetSnapshot.fingerprint(index) != existingHash) {
                DatabaseRestoreJournal.install(context.filesDir, ownIndex, mapOf(ownIndex to index)) {
                    check(existingHash == ProfileResetSnapshot.fingerprint(ownIndex) &&
                        sidecars.all { ProfileResetSnapshot.fingerprint(it) == "-" }
                    ) { "The independent Media3 index changed during publication" }
                }
            }
            verifyCache(original, ReadOnlyProfileCache.open(newRoot, ownIndex, stage))
            if (downloads) {
                check(completedRows(ownIndex, stage) == records) { "Download rows differ after migration" }
            }
            check(identity == profileStorageIdentity(context, id)) { "Profile identity changed during migration" }
            val receipt = archive.resolve("consumer-${ownIndex.name}-${identity.generation}-${item.name}.txt")
            writeSynced(receipt, "${identity.id}\n${identity.generation}\n${oldRoot.name}")
            check(context.profilePreferences.edit().remove(profileShareKey(item, id)).commit()) {
                "Could not publish the separated Media3 store"
            }
        } finally {
            ProfileResetSnapshot.deleteVerifiedTree(stage)
        }
    }

    private fun mergeCacheIndex(source: File, target: File, uid: Long, downloads: Boolean): Boolean {
        val uidHex = uid.toString(16)
        val tables = buildList {
            if (uid >= 0) {
                add(Triple("ExoPlayerCacheIndex$uidHex", VersionTable.FEATURE_CACHE_CONTENT_METADATA, uidHex))
                add(Triple("ExoPlayerCacheFileMetadata$uidHex", VersionTable.FEATURE_CACHE_FILE_METADATA, uidHex))
            }
            if (downloads) add(Triple("ExoPlayerDownloads", VersionTable.FEATURE_OFFLINE, ""))
        }
        val definitions = SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READWRITE).use { original ->
            // Checkpoint only the scratch source, then close its connection before attaching it.
            original.rawQuery("PRAGMA journal_mode=DELETE", null).use { check(it.moveToFirst() && it.getString(0) == "delete") }
            tables.associate { (name, feature, instance) ->
                val definition = tableDefinition(original, name)
                if (definition != null) {
                    val version = VersionTable.getVersion(original, feature, instance)
                    check(version == if (feature == VersionTable.FEATURE_OFFLINE) 3 else 1) {
                        "The legacy Media3 table version is unverified"
                    }
                }
                name to definition
            }
        }
        if (definitions.values.none { it != null }) return false
        SQLiteDatabase.openOrCreateDatabase(target.path, null).use { merged ->
            merged.execSQL("ATTACH DATABASE ? AS source_cache", arrayOf(source.path))
            merged.beginTransaction()
            try {
                tables.forEach { (name, feature, instance) ->
                    val definition = definitions[name]
                    val existing = tableDefinition(merged, name)
                    if (definition == null) {
                        check(existing == null) { "The independent Media3 table contains unrelated data" }
                        return@forEach
                    }
                    val version = if (feature == VersionTable.FEATURE_OFFLINE) 3 else 1
                    if (existing == null) {
                        merged.execSQL(definition)
                        merged.execSQL("INSERT INTO $name SELECT * FROM source_cache.$name")
                        VersionTable.setVersion(merged, feature, instance, version)
                    } else {
                        check(VersionTable.getVersion(merged, feature, instance) == version &&
                            tableDifferenceIsEmpty(merged, "main.$name", "source_cache.$name") &&
                            tableDifferenceIsEmpty(merged, "source_cache.$name", "main.$name")
                        ) { "The independent Media3 table contains different data" }
                    }
                }
                merged.setTransactionSuccessful()
            } finally {
                merged.endTransaction()
            }
            merged.execSQL("DETACH DATABASE source_cache")
            merged.rawQuery("PRAGMA quick_check", null).use {
                check(it.moveToFirst() && it.getString(0) == "ok" && !it.moveToNext())
            }
        }
        return true
    }

    private fun tableDefinition(database: SQLiteDatabase, table: String): String? =
        database.rawQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    private fun tableDifferenceIsEmpty(database: SQLiteDatabase, left: String, right: String): Boolean =
        database.rawQuery("SELECT * FROM $left EXCEPT SELECT * FROM $right LIMIT 1", null).use { !it.moveToFirst() }

    private data class DownloadRow(val id: String, val cacheKey: String, val length: Long)

    private fun completedRows(index: File, work: File? = null): List<DownloadRow> {
        if (!index.exists()) return emptyList()
        val copy = work?.resolve("validate.db")
        if (copy != null) copyDatabase(index, copy)
        val snapshot = copy ?: index
        return SQLiteDatabase.openDatabase(snapshot.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("PRAGMA quick_check", null).use {
                check(it.moveToFirst() && it.getString(0) == "ok" && !it.moveToNext()) { "Invalid Media3 index" }
            }
            val present = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='ExoPlayerDownloads'", null)
                .use { it.moveToFirst() }
            val rows = if (!present) emptyList() else {
                check(VersionTable.getVersion(db, VersionTable.FEATURE_OFFLINE, "") == 3) {
                    "The legacy download index version has not been verified"
                }
                val provider = object : DatabaseProvider {
                    override fun getReadableDatabase(): SQLiteDatabase = db
                    override fun getWritableDatabase(): SQLiteDatabase = db
                }
                DefaultDownloadIndex(provider).getDownloads().use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            val download = cursor.download
                            check(download.state == Download.STATE_COMPLETED) { "A legacy download request has unverified ownership" }
                            val id = download.request.id
                            add(DownloadRow(id, download.request.customCacheKey ?: id, download.contentLength))
                        }
                    }
                }
            }
            db.rawQuery("PRAGMA journal_mode=DELETE", null).use {
                check(it.moveToFirst() && it.getString(0).equals("delete", ignoreCase = true))
            }
            rows
        }
    }

    private fun verifyQuota(limit: ExoPlayerDiskDownloadCacheMaxSize, used: Long) {
        val maximum = if (limit == ExoPlayerDiskDownloadCacheMaxSize.Disabled) 0L else limit.bytes
        if (limit != ExoPlayerDiskDownloadCacheMaxSize.Unlimited && used > maximum) {
            throw LegacySeparationStatus.Quota("The profile quota cannot keep all verified downloads")
        }
    }

    private fun verifyIndexVersions(index: File, caches: List<ReadOnlyProfileCache>) {
        val used = caches.filter { it.cacheSpace > 0 && it.uid >= 0 }
        if (used.isEmpty()) return
        check(index.isFile) { "The legacy cache index is missing" }
        SQLiteDatabase.openDatabase(index.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            used.forEach { cache ->
                check(VersionTable.getVersion(db, VersionTable.FEATURE_CACHE_CONTENT_METADATA, cache.uid.toString(16)) == 1) {
                    "The legacy cache content index version has not been verified"
                }
            }
        }
    }

    private fun verifyDownloadOwnership(records: List<DownloadRow>, cache: ReadOnlyProfileCache, library: File, work: File) {
        if (records.isEmpty()) return
        check(library.isFile) { "The consumer has no library to verify ownership" }
        val snapshot = work.resolve("library.db")
        copyDatabase(library, snapshot)
        SQLiteDatabase.openDatabase(snapshot.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            records.forEach { entry ->
                check(entry.length > 0 && cache.isCached(entry.cacheKey, 0, entry.length)) {
                    "A completed legacy download is not fully readable"
                }
                db.rawQuery(
                    "SELECT 1 FROM Song JOIN Format ON Format.songId = Song.id WHERE Song.id = ? AND Format.contentLength = ? LIMIT 1",
                    arrayOf(entry.id, entry.length.toString()),
                ).use { check(it.moveToFirst()) { "A legacy download is not verified for this profile" } }
            }
        }
    }

    private fun verifySpanInventory(directory: File, cache: ReadOnlyProfileCache) {
        if (!directory.exists()) return
        val bytes = directory.walkTopDown().filter { it.isFile && it.extension == "exo" }.sumOf { it.length() }
        check(cache.cacheSpace == bytes) { "Some legacy spans are absent from the Media3 index" }
    }

    private fun verifyCache(before: ReadOnlyProfileCache, after: ReadOnlyProfileCache) {
        check(before.uid == after.uid && before.keys == after.keys)
        before.keys.forEach { key ->
            fun spans(cache: ReadOnlyProfileCache) = cache.getCachedSpans(key).map { it.position to it.length }
            check(spans(before) == spans(after) && before.getContentMetadata(key) == after.getContentMetadata(key)) {
                "A published Media3 cache differs from the verified source"
            }
        }
    }

    private fun copyDatabase(source: File, destination: File) {
        sideFiles.forEach { suffix ->
            val from = checkNotNull(source.parentFile).resolve(source.name + suffix)
            val to = checkNotNull(destination.parentFile).resolve(destination.name + suffix)
            ProfileResetSnapshot.copyTree(from, to)
        }
    }

    private fun prepareDestination(source: File, destination: File) {
        val expected = ProfileResetSnapshot.fingerprint(source)
        if (expected == ProfileResetSnapshot.fingerprint(destination)) return
        check(source.exists() && !destination.exists()) { "The independent destination changed before publication" }
        val stage = checkNotNull(destination.parentFile).resolve(".nzik-separation-${destination.name}")
        ProfileResetSnapshot.deleteVerifiedTree(stage)
        ProfileResetSnapshot.copyTree(source, stage)
        check(ProfileResetSnapshot.fingerprint(stage) == expected)
        check(stage.renameTo(destination)) { "Could not publish a verified Media3 destination" }
    }

    private fun validateDestination(source: File, destination: File) {
        val expected = ProfileResetSnapshot.fingerprint(source)
        val actual = ProfileResetSnapshot.fingerprint(destination)
        if (actual != expected && actual != "-") {
            throw LegacySeparationStatus.Conflict("The independent destination contains different data")
        }
    }

    private fun writeSynced(file: File, value: String) {
        FileOutputStream(file).use { output ->
            output.write(value.encodeToByteArray())
            output.fd.sync()
        }
    }
}
