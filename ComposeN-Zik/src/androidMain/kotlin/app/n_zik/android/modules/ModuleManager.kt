package app.n_zik.android.modules

import app.n_zik.android.R
import app.n_zik.android.appContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import app.n_zik.android.utils.coroutines.NzikDispatchers
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Localized failure messages for module states (injected so tests can fake them). */
interface ModuleFailureMessages {
    fun unavailable(): String
    fun insufficientSpace(): String
    fun downloadFailed(cause: Throwable): String
    /** First-use failure: the module was never installed on this device. */
    fun downloadNeeded(): String
}

/** Production [ModuleFailureMessages] - resolved strings via the app context. */
object AppModuleFailureMessages : ModuleFailureMessages {
    override fun unavailable(): String = appContext().getString(R.string.module_not_available_arch)
    override fun insufficientSpace(): String = appContext().getString(R.string.module_insufficient_space)
    override fun downloadFailed(cause: Throwable): String = appContext().getString(R.string.module_download_failed)
    override fun downloadNeeded(): String = appContext().getString(R.string.module_download_needed)
}

/**
 * Generic manager for downloadable extension modules (spec-ffmpeg-extension-module).
 *
 * A module is an archive (AAR/zip) pinned by [ModuleDefinition]: it is downloaded on
 * first use, whole-archive SHA-256 verified, and its .so entries under `jni/{abi}/` are
 * extracted into `filesDir/modules/{id}/{version}-{abi}/`. The old version dir is only
 * deleted after the new one is fully staged and verified (a failed update never breaks
 * the installed module). Cancellation: [cancelDownload] stops the owning coroutine and
 * the underlying OkHttp call; partial files are always cleaned up.
 *
 * Construction is dependency-injected ([filesDir], [source], [deviceAbiProvider],
 * [failureMessages]) so the whole state machine is unit-testable on the JVM.
 */
class ModuleManager(
    private val filesDir: File,
    private val source: ModuleSource,
    /** ABI the device can run for a given module; null when none (module unavailable). */
    private val deviceAbiProvider: (ModuleDefinition) -> String?,
    private val failureMessages: ModuleFailureMessages,
    private val listener: ModuleDownloadListener = NoopModuleDownloadListener,
) {

    private companion object {
        const val TAG = "ModuleManager"
        const val MANIFEST_NAME = "manifest.sha256"
    }

    private val _states = MutableStateFlow<Map<String, ModuleState>>(emptyMap())

    /** Live state per module (keyed by [ModuleDefinition.id]). */
    val states: StateFlow<Map<String, ModuleState>> = _states.asStateFlow()
    // Thread-safe map: written on DATA by ensureReady, removed on Main by
    // cancelDownload (a plain LinkedHashMap would race across the two)
    private val jobs = ConcurrentHashMap<String, Job>()
    // id -> definition: lets cancelDownload tell whether a previous version is
    // still installed (an interrupted update must keep reporting READY)
    private val modules = ConcurrentHashMap<String, ModuleDefinition>()
    private val mutexes = mutableMapOf<String, Mutex>()

    fun stateOf(def: ModuleDefinition): ModuleState =
        _states.value[def.id] ?: (if (installedDir(def) != null) ModuleState.READY else ModuleState.NOT_DOWNLOADED)

    fun checkForUpdate(def: ModuleDefinition): ModuleUpdateStatus {
        val installed = installedVersion(def) ?: return ModuleUpdateStatus.NotInstalled
        return if (compareVersions(installed, def.version) >= 0) {
            ModuleUpdateStatus.UpToDate
        } else {
            ModuleUpdateStatus.UpdateAvailable(installed)
        }
    }

    /** Verified installed dir for the pinned version, or null. */
    fun installedDir(def: ModuleDefinition): File? {
        val abi = deviceAbiProvider(def) ?: return null
        val dir = moduleDir(def, abi)
        return if (hasInstalledDir(def, abi, dir)) dir else null
    }

    /** Total bytes on disk for the installed module, or null. */
    fun installedSize(def: ModuleDefinition): Long? =
        installedDir(def)?.walkBottomUp()?.filter { it.isFile }?.sumOf { it.length() }

    /**
     * Re-records the manifest hash of [soName] after an intentional post-install
     * modification (the DF_1_GLOBAL patch applied to `libc++_shared.so`). The
     * archive SHA-256 already guaranteed the original content at download time;
     * without this, [verifyInstalled] would see a mismatch on the next
     * [ensureReady] and treat the cache as corrupted, re-downloading the whole
     * module on every use.
     */
    fun recordPatchedHash(def: ModuleDefinition, soName: String) {
        val abi = deviceAbiProvider(def) ?: return
        val dir = moduleDir(def, abi)
        val file = File(dir, soName)
        val manifest = File(dir, MANIFEST_NAME)
        if (!file.exists() || !manifest.exists()) return
        val lines = manifest.readLines().map { line ->
            val sep = line.indexOf("  ")
            if (sep < 0 || line.substring(sep + 2) != soName) line else sha256Hex(file) + "  " + soName
        }
        manifest.writeText(lines.joinToString("\n"))
    }

    /**
     * Makes the pinned module ready: downloads (or re-downloads a corrupted cache),
     * verifies, extracts and reports [ModuleState.READY]. A device without a matching
     * ABI, or without free space, ends in [ModuleState.FAILED]. Cancellable via
     * [cancelDownload].
     */
    suspend fun ensureReady(def: ModuleDefinition): ModuleState = withContext(NzikDispatchers.DATA) {
        modules[def.id] = def
        val abi = deviceAbiProvider(def)
        if (abi == null) {
            return@withContext updateState(def.id, ModuleState.FAILED(failureMessages.unavailable()))
        }
        mutexFor(def.id).withLock {
            // Orphans from an interrupted download (killed process, cancel race):
            // safe here, the per-module lock excludes any concurrent download
            sweepOrphans(moduleRoot(def))
            val pinnedDir = moduleDir(def, abi)
            if (pinnedDir.exists() && verifyInstalled(def, abi, pinnedDir)) {
                return@withLock updateState(def.id, ModuleState.READY)
            }
            // Corrupted cache (manifest or hashes gone): delete + re-download
            if (pinnedDir.exists()) {
                Timber.tag(TAG).e("Module ${def.id} cache corrupted, re-downloading")
                pinnedDir.deleteRecursively()
            }
            if (filesDir.usableSpace < def.requiredFreeSpaceBytes) {
                return@withLock updateState(def.id, ModuleState.FAILED(failureMessages.insufficientSpace()))
            }

            val isUpdate = installedVersion(def) != null
            if (isUpdate) updateState(def.id, ModuleState.UPDATING)

            // Register this download so cancelDownload() can stop it: the Job of this
            // withContext block is the cancellation unit for the whole download.
            // (Always present - withContext creates a child coroutine.)
            jobs[def.id] = requireNotNull(coroutineContext[Job]) { "ensureReady must run in a coroutine" }

            val tmp = File(moduleRoot(def), ".tmp-${def.id}-${System.currentTimeMillis()}.aar")
            // First install: the module root may not exist yet - the manager
            // guarantees it regardless of whether the source implementation does
            tmp.parentFile?.mkdirs()
            var staging: File? = null
            try {
                listener.onDownloadStarted(def, isUpdate)
                // Throttle: one emission per ~1% step, a full download would
                // otherwise emit per 8 KB chunk (thousands of StateFlow updates)
                var lastEmitted = -1f
                source.download(def, tmp) { progress ->
                    if (progress - lastEmitted >= 0.01f || progress >= 1f) {
                        lastEmitted = progress
                        // An update keeps the flat UPDATING state (the old module keeps working)
                        if (!isUpdate) updateState(def.id, ModuleState.DOWNLOADING(progress))
                        listener.onDownloadProgress(def, progress)
                    }
                }
                if (sha256Hex(tmp) != def.sha256) {
                    throw IOException("SHA-256 mismatch for module ${def.id}")
                }
                staging = File(moduleRoot(def), ".staging-${def.version}-${abi}")
                extractSoFiles(def, abi, tmp, staging)
                writeManifest(staging, def.soFiles.getValue(abi))
                // Staged swap: the old module dir disappears only once the new one is
                // fully extracted and manifest-verified
                pinnedDir.deleteRecursively()
                if (!staging.renameTo(pinnedDir)) {
                    staging.copyRecursively(pinnedDir, overwrite = true)
                    staging.deleteRecursively()
                }
                staging = null
                tmp.delete()
                deleteStaleVersionDirs(def, abi, def.version)
                listener.onDownloadCompleted(def)
                updateState(def.id, ModuleState.READY)
                return@withLock ModuleState.READY
            } catch (e: CancellationException) {
                listener.onDownloadCancelled(def)
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Module ${def.id} ${def.version} download failed")
                listener.onDownloadFailed(def, e)
                // First use: the module was never installed, point to the download
                val message = if (isUpdate) failureMessages.downloadFailed(e) else failureMessages.downloadNeeded()
                return@withLock updateState(def.id, ModuleState.FAILED(message))
            } finally {
                jobs.remove(def.id)
                tmp.delete()
                staging?.deleteRecursively()
            }
        }
    }

    /** Cancels the in-flight download of module [id] (no-op when none is running). */
    fun cancelDownload(id: String) {
        val job = jobs.remove(id) ?: return
        job.cancel()
        source.cancelActiveDownload()
        // An interrupted update keeps the previous version on disk and the module
        // keeps working: report what is installed, not a blind NOT_DOWNLOADED
        val stillInstalled = modules[id]?.let { installedVersion(it) } != null
        updateState(id, if (stillInstalled) ModuleState.READY else ModuleState.NOT_DOWNLOADED)
    }

    /** Deletes the whole module (all versions) and frees its space. */
    suspend fun uninstall(def: ModuleDefinition) = withContext(NzikDispatchers.DATA) {
        cancelDownload(def.id)
        moduleRoot(def).deleteRecursively()
        updateState(def.id, ModuleState.NOT_DOWNLOADED)
        listener.onUninstalled(def)
    }

    // region internals

    /** Installed version for the device ABI (any verified dir), or null. */
    private fun installedVersion(def: ModuleDefinition): String? {
        val abi = deviceAbiProvider(def) ?: return null
        return moduleRoot(def)
            .listFiles { f -> f.isDirectory && f.name.endsWith("-$abi") }
            ?.mapNotNull { dir -> dir.name.removeSuffix("-$abi").takeIf { hasInstalledDir(def, abi, dir) } }
            ?.maxWithOrNull { a, b -> compareVersions(a, b) }
    }

    private fun mutexFor(id: String): Mutex = synchronized(mutexes) { mutexes.getOrPut(id) { Mutex() } }

    private fun updateState(id: String, state: ModuleState): ModuleState {
        _states.update { it + (id to state) }
        return state
    }

    private fun moduleRoot(def: ModuleDefinition): File = File(filesDir, "modules/${def.id}")

    private fun moduleDir(def: ModuleDefinition, abi: String): File =
        File(moduleRoot(def), "${def.version}-$abi")

    /** Deletes orphan `.tmp-*` / `.staging-*` entries left by an interrupted download. */
    private fun sweepOrphans(root: File) {
        root.listFiles { f -> f.name.startsWith(".tmp-") || f.name.startsWith(".staging-") }
            ?.forEach { file ->
                runCatching { if (file.isDirectory) file.deleteRecursively() else file.delete() }
                    .onFailure { Timber.tag(TAG).w(it, "Could not sweep orphan module file ${file.name}") }
            }
    }

    /** UI-safe check: manifest + every pinned .so present (no hashing). */
    private fun hasInstalledDir(def: ModuleDefinition, abi: String, dir: File): Boolean {
        if (!File(dir, MANIFEST_NAME).exists()) return false
        return def.soFiles[abi]?.all { File(dir, it).exists() } ?: false
    }

    /** Full verification: every .so hash matches the manifest (runs on the DATA dispatcher). */
    private fun verifyInstalled(def: ModuleDefinition, abi: String, dir: File): Boolean {
        val names = def.soFiles[abi] ?: return false
        val manifest = File(dir, MANIFEST_NAME)
        if (!manifest.exists()) return false
        val recorded = manifest.readLines().mapNotNull { line ->
            val sep = line.indexOf("  ")
            if (sep < 0) null else line.substring(sep + 2) to line.substring(0, sep)
        }.toMap()
        return names.all { name ->
            val file = File(dir, name)
            file.exists() && recorded[name] == sha256Hex(file)
        }
    }

    /**
     * Copies the pinned .so entries under `jni/{abi}/` out of the archive. Entry names come
     * from the developer-pinned [ModuleDefinition.soFiles] (never from user input), so
     * no zip-slip guard is needed. Every extracted file is set to POSIX 0555 - Android's
     * Runtime refuses `System.load()` on a writable library (spike-verified).
     */
    private fun extractSoFiles(def: ModuleDefinition, abi: String, archive: File, dest: File) {
        dest.mkdirs()
        val zip = java.util.zip.ZipFile(archive)
        try {
            def.soFiles.getValue(abi).forEach { name ->
                val entry = zip.getEntry("jni/$abi/$name")
                    ?: throw IOException("Missing archive entry: jni/$abi/$name")
                val file = File(dest, name)
                zip.getInputStream(entry).use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                setReadOnly(file)
            }
        } finally {
            zip.close()
        }
    }

    /**
     * 0555 on a staged .so - a writable file cannot be `System.load`-ed on Android.
     * File-level permission methods cover API 24+ (`File.toPath()` needs API 26+);
     * they no-op on non-POSIX test JVMs.
     */
    private fun setReadOnly(file: File) {
        file.setExecutable(true, false)
        file.setWritable(false, false)
        file.setReadable(true, false)
    }

    private fun writeManifest(dir: File, soNames: List<String>) {
        File(dir, MANIFEST_NAME).writeText(
            soNames.joinToString("\n") { sha256Hex(File(dir, it)) + "  " + it }
        )
    }

    /** Deletes version dirs for [abi] other than [keep] (old versions after an update). */
    private fun deleteStaleVersionDirs(def: ModuleDefinition, abi: String, keep: String) {
        val root = moduleRoot(def)
        root.listFiles { f -> f.isDirectory && f.name.endsWith("-$abi") && f.name != "$keep-$abi" }
            ?.forEach { dir ->
                runCatching { dir.deleteRecursively() }
                    .onFailure { Timber.tag(TAG).w(it, "Could not delete stale module dir ${dir.name}") }
            }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun compareVersions(a: String, b: String): Int {
        val partsA = a.split(".").map { it.toIntOrNull() ?: 0 }
        val partsB = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(partsA.size, partsB.size)) {
            val x = partsA.getOrNull(i) ?: 0
            val y = partsB.getOrNull(i) ?: 0
            if (x != y) return x.compareTo(y)
        }
        return a.compareTo(b)
    }

    // endregion
}
