package app.n_zik.android.modules

import android.os.Build
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.utils.coroutines.NzikDispatchers
import com.arthenica.ffmpegkit.FFmpegKit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * The FFmpeg extension module - the first concrete [ModuleManager] module
 * (spec-ffmpeg-extension-module).
 *
 * The fork AAR (`io.github.maxrave-dev:ffmpeg-kit-audio`) - the same artifact as the
 * app's compile-time `libs.ffmpegKitAudio` dependency, published by the fork author on
 * Maven Central - is downloaded on first use, SHA-256 verified, and its .so entries
 * under `jni/{abi}/` are extracted. Loading (round 3d spike-verified): every .so is
 * dlopen'd by full path in dependency order into the app's classloader namespace via
 * a JNI helper, the module's `libc++_shared.so` carrying `DF_1_GLOBAL` so libavcodec's
 * `__gxx_personality_v0` resolves from the namespace global group; FFmpegKit's own
 * `System.loadLibrary` calls then no-op against the preloaded libraries. x86 32-bit
 * devices have no module build (no x86 .so in the AAR) and get the "not available"
 * failure instead of a download.
 */
object FfmpegModule {

    private const val TAG = "FfmpegModule"

    // AAR facts (verified against Maven Central, 6.0.1):
    // SHA-256 9d87c166e5738fdfedad884e878494943e63f8a0c8232d9369e4c2ea1a5a3487,
    // 37 261 319 bytes, ABIs arm64-v8a / armeabi-v7a / x86_64 (NO x86).
    // The archive is the fork author's Maven Central publication - the same artifact
    // Gradle resolves for the compile-time `libs.ffmpegKitAudio` dependency.
    val definition = ModuleDefinition(
        id = "ffmpeg",
        displayNameRes = R.string.module_ffmpeg,
        descriptionRes = R.string.module_ffmpeg_description,
        version = BuildConfig.FFMPEG_MODULE_VERSION,
        abis = setOf("arm64-v8a", "armeabi-v7a", "x86_64"),
        downloadUrl = "https://repo1.maven.org/maven2/io/github/maxrave-dev/ffmpeg-kit-audio/" +
            "${BuildConfig.FFMPEG_MODULE_VERSION}/ffmpeg-kit-audio-${BuildConfig.FFMPEG_MODULE_VERSION}.aar",
        sha256 = "9d87c166e5738fdfedad884e878494943e63f8a0c8232d9369e4c2ea1a5a3487",
        // System.load order (spike-verified): the C++ runtime FIRST - the C++ FFmpeg
        // libraries need __gxx_personality_v0 from libc++_shared - then the leaf
        // FFmpeg libraries, the FFmpegKit glue last (its DT_NEEDED entries must
        // already be in the dynamic linker)
        soFiles = mapOf(
            "arm64-v8a" to listOf(
                "libc++_shared.so",
                "libavutil.so", "libswresample.so", "libswscale.so",
                "libavcodec.so", "libavformat.so", "libavfilter.so",
                "libavdevice.so",
                "libffmpegkit.so", "libffmpegkit_abidetect.so"
            ),
            "x86_64" to listOf(
                "libc++_shared.so",
                "libavutil.so", "libswresample.so", "libswscale.so",
                "libavcodec.so", "libavformat.so", "libavfilter.so",
                "libavdevice.so",
                "libffmpegkit.so", "libffmpegkit_abidetect.so"
            ),
            "armeabi-v7a" to listOf(
                "libc++_shared.so",
                "libavutil_neon.so", "libswresample_neon.so", "libswscale_neon.so",
                "libavcodec_neon.so", "libavformat_neon.so", "libavfilter_neon.so",
                "libavdevice_neon.so",
                "libffmpegkit_armv7a_neon.so", "libffmpegkit_abidetect.so"
            ),
        ),
        // Free-space gate: the AAR tmp (~37 MB) and the staged extraction
        // (~35 MB) coexist on disk during a download, hence ~70 MB
        requiredFreeSpaceBytes = 70L * 1024 * 1024,
        downloadSizeBytes = 37_261_319L,
    )

    /** Production manager: app filesDir + OkHttp source + device ABI detection. */
    val manager: ModuleManager by lazy {
        ModuleManager(
            filesDir = appContext().filesDir,
            source = OkHttpModuleSource,
            deviceAbiProvider = ::deviceAbiFor,
            failureMessages = AppModuleFailureMessages,
            listener = AndroidModuleDownloadListener,
        )
    }

    /** True when the device can run the module (false on x86 32-bit). */
    fun isAvailableOnDevice(): Boolean = deviceAbiFor(definition) != null

    /** Launch-time update check (detection only - the download happens via [ensureReady]). */
    fun checkForUpdate(): ModuleUpdateStatus = manager.checkForUpdate(definition)

    /**
     * Makes the module ready for use and loads its native libraries. Call this before
     * touching ANY FFmpegKit class - the first FFmpegKit access after a process start
     * triggers its private NativeLoader, which must find the preloaded libraries.
     * Returns [ModuleState.FAILED] when the native load fails (the download itself
     * succeeded - the module stays on disk).
     */
    suspend fun ensureReady(): ModuleState {
        val state = manager.ensureReady(definition)
        if (state is ModuleState.READY) {
            // The ELF patch + dlopen tree is multi-second blocking work: keep it
            // off the caller's dispatcher (callers run on Main)
            val loaded = withContext(NzikDispatchers.DATA) { ensureLoaded() }
            if (!loaded) {
                return ModuleState.FAILED(appContext().getString(R.string.module_load_failed))
            }
        }
        return state
    }

    /**
     * FFmpeg's own version banner read from the LOCAL module: version, build
     * configuration and per-library versions from the `-version` output.
     * Never downloads - the module must already be on disk; only the native
     * load happens here. Throws when the module is missing or cannot be
     * loaded. The synchronous work runs off Main on [NzikDispatchers.DATA].
     */
    suspend fun fetchVersionInfo(): String {
        val dir = manager.installedDir(definition)
            ?: throw IllegalStateException("FFmpeg module is not installed")
        val loaded = withContext(NzikDispatchers.DATA) { ensureLoaded() }
        if (!loaded) {
            throw IllegalStateException("FFmpeg module failed to load")
        }
        return withContext(NzikDispatchers.DATA) {
            val session = FFmpegKit.execute("-version")
            // The banner prints to stdout: the fork captures it in getOutput()
            // (the stderr log list stays empty), fall back to the logs
            val raw = session.output.ifEmpty { session.logsAsString }
            Timber.tag(TAG).d("FFmpeg -version raw banner:\n$raw")
            // Fall back to the raw banner when the filter matches nothing, so
            // the Info dialog never renders empty
            FfmpegVersionLog.parse(raw).ifEmpty { raw.trim() }
                .ifEmpty { error("FFmpeg -version output is empty") }
        }
    }

    /** ABI the device can run the module with, or null (x86-only devices). */
    private fun deviceAbiFor(def: ModuleDefinition): String? =
        Build.SUPPORTED_ABIS.firstOrNull { it in def.abis }

    private val loadLock = Any()
    @Volatile
    private var loadedVersion: String? = null

    /**
     * Patches the module's C++ runtime (DF_1_GLOBAL) and dlopens its .so tree
     * in dependency order. Idempotent per process: the module version only
     * changes with an app update (new process), so the flag never goes stale.
     * Returns false when the native load failed.
     */
    private fun ensureLoaded(): Boolean {
        synchronized(loadLock) {
            if (loadedVersion == definition.version) return true
            val abi = deviceAbiFor(definition) ?: return false
            val dir = manager.installedDir(definition) ?: return false
            return runCatching {
                patchCxxShared(File(dir, "libc++_shared.so"))
                val report = ModuleNativeLoader.loadTree(dir.absolutePath, definition.soFiles.getValue(abi))
                val failed = report.lineSequence().firstOrNull { "FAILED" in it }
                failed?.let { error("module tree load failed: $it\n$report") }
                // Success flag: set only when the whole tree loaded, so a failed
                // attempt is retried on the next call
                loadedVersion = definition.version
            }.onFailure {
                Timber.tag(TAG).e(it, "FFmpeg module load failed (${definition.version}, $abi)")
            }.isSuccess
        }
    }

    // The extracted .so are 0555; the DF_1_GLOBAL patch needs write access for
    // a moment (re-applied right after - the linker refuses writable files).
    private fun patchCxxShared(cxx: File) {
        val data = cxx.readBytes()
        if (Df1GlobalPatcher.isPatched(data)) return
        cxx.setWritable(true)
        try {
            cxx.writeBytes(Df1GlobalPatcher.patch(data))
        } finally {
            cxx.setWritable(false)
        }
        // The patch changed the on-disk bytes after install: re-record the
        // manifest hash, otherwise verifyInstalled sees a mismatch on the next
        // ensureReady and re-downloads the module on every use.
        manager.recordPatchedHash(definition, "libc++_shared.so")
    }
}
