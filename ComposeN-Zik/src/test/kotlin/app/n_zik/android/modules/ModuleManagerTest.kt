package app.n_zik.android.modules

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * JVM tests for [ModuleManager] - the download → SHA-256 → extract → READY
 * state machine, with a fake [ModuleSource] (no network).
 */
class ModuleManagerTest {

    @TempDir
    lateinit var filesDir: File

    private var downloadCount = 0

    private val fakeSource = object : ModuleSource {
        override suspend fun download(def: ModuleDefinition, destination: File, onProgress: (Float) -> Unit) {
            downloadCount++
            destination.writeBytes(fakeArchive(def.abis.first(), def.soFiles.getValue(def.abis.first()).first()))
            onProgress(1f)
        }

        override fun cancelActiveDownload() {}
    }

    private val failureMessages = object : ModuleFailureMessages {
        override fun unavailable() = "unavailable"
        override fun insufficientSpace() = "insufficient space"
        override fun downloadFailed(cause: Throwable) = "download failed: ${cause.message}"
        override fun downloadNeeded() = "download needed"
    }

    private fun manager(
        abi: String? = "x86_64",
        source: ModuleSource = fakeSource,
        listener: ModuleDownloadListener = NoopModuleDownloadListener,
    ) = ModuleManager(
        filesDir = filesDir,
        source = source,
        deviceAbiProvider = { abi },
        failureMessages = failureMessages,
        listener = listener,
    )

    // A minimal AAR-shaped archive: the pinned .so under jni/{abi}/
    private fun fakeArchive(abi: String, soName: String): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("jni/$abi/$soName"))
            zip.write("fake-$soName".toByteArray())
            zip.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun def(
        version: String = "1.0.0",
        abi: String = "x86_64",
        soName: String = "libfoo.so",
        sha256: String = sha256Hex(fakeArchive(abi, soName)),
    ) = ModuleDefinition(
        id = "testmod",
        displayNameRes = 0,
        descriptionRes = 0,
        version = version,
        abis = setOf(abi),
        downloadUrl = "https://example.invalid/testmod.aar",
        sha256 = sha256,
        soFiles = mapOf(abi to listOf(soName)),
        requiredFreeSpaceBytes = 1L,
        downloadSizeBytes = 1L,
    )

    @Test
    fun `ensureReady should download verify extract and report READY`() = runBlocking {
        val manager = manager()
        val def = def()

        val state = manager.ensureReady(def)

        assertTrue(state is ModuleState.READY)
        val dir = manager.installedDir(def)!!
        assertTrue(File(dir, "libfoo.so").exists())
        assertTrue(File(dir, "manifest.sha256").exists())
        // No tmp/staging residue
        val root = File(filesDir, "modules/testmod")
        assertTrue(root.listFiles().orEmpty().none { it.name.startsWith(".") })
        assertEquals(ModuleState.READY, manager.stateOf(def))
    }

    @Test
    fun `ensureReady should report FAILED and clean up on SHA-256 mismatch`() = runBlocking {
        val manager = manager()
        val def = def(sha256 = "deadbeef".repeat(8))

        val state = manager.ensureReady(def)

        assertTrue(state is ModuleState.FAILED)
        assertNull(manager.installedDir(def))
        val root = File(filesDir, "modules/testmod")
        assertTrue(root.listFiles().orEmpty().none { it.name.contains(".tmp-") })
    }

    @Test
    fun `ensureReady should not re-download when the module is already installed`() = runBlocking {
        val manager = manager()
        val def = def()

        manager.ensureReady(def)
        val downloadsAfterFirst = downloadCount
        val state = manager.ensureReady(def)

        assertTrue(state is ModuleState.READY)
        assertEquals(downloadsAfterFirst, downloadCount)
    }

    @Test
    fun `ensureReady should report FAILED when no ABI matches the device`() = runBlocking {
        val manager = manager(abi = null)

        val state = manager.ensureReady(def())

        assertTrue(state is ModuleState.FAILED)
        assertEquals("unavailable", (state as ModuleState.FAILED).message)
    }

    @Test
    fun `uninstall should remove the module and reset the state`() = runBlocking {
        val manager = manager()
        val def = def()

        manager.ensureReady(def)
        manager.uninstall(def)

        assertNull(manager.installedDir(def))
        assertEquals(ModuleState.NOT_DOWNLOADED, manager.stateOf(def))
    }

    @Test
    fun `ensureReady should not re-download when a post-install hash change was recorded`() = runBlocking {
        val manager = manager()
        val def = def()
        manager.ensureReady(def)
        val downloadsAfterFirst = downloadCount

        // Simulate the DF_1_GLOBAL patch: the .so bytes change on disk after
        // install, then the new hash is re-recorded in the manifest.
        val dir = requireNotNull(manager.installedDir(def)) { "module should be installed" }
        val soFile = File(dir, "libfoo.so")
        soFile.setWritable(true)
        soFile.writeBytes("patched-bytes".toByteArray())
        manager.recordPatchedHash(def, "libfoo.so")

        val state = manager.ensureReady(def)

        assertTrue(state is ModuleState.READY)
        assertEquals(downloadsAfterFirst, downloadCount)
    }

    @Test
    fun `uninstall should notify the listener`() = runBlocking {
        var uninstalled: ModuleDefinition? = null
        val listener = object : ModuleDownloadListener {
            override fun onUninstalled(def: ModuleDefinition) {
                uninstalled = def
            }
        }
        val manager = manager(listener = listener)
        val def = def()

        manager.ensureReady(def)
        manager.uninstall(def)

        assertEquals(def.id, uninstalled?.id)
    }

    @Test
    fun `checkForUpdate should detect an older installed version`() = runBlocking {
        val manager = manager()
        val def = def()

        manager.ensureReady(def)

        assertEquals(ModuleUpdateStatus.UpdateAvailable("1.0.0"), manager.checkForUpdate(def(version = "1.1.0")))
        assertEquals(ModuleUpdateStatus.UpToDate, manager.checkForUpdate(def))
    }

    @Test
    fun `ensureReady should fail with insufficient space without downloading`() = runBlocking {
        val manager = manager()
        val def = def().copy(requiredFreeSpaceBytes = Long.MAX_VALUE)

        val state = manager.ensureReady(def)

        assertTrue(state is ModuleState.FAILED)
        assertEquals("insufficient space", (state as ModuleState.FAILED).message)
        assertEquals(0, downloadCount)
    }

    @Test
    fun `ensureReady should re-download a corrupted cache`() = runBlocking {
        val manager = manager()
        val def = def()
        manager.ensureReady(def)
        val downloadsAfterFirst = downloadCount

        // Corrupt the .so without re-recording the manifest hash (recordPatchedHash)
        val dir = requireNotNull(manager.installedDir(def)) { "module should be installed" }
        val soFile = File(dir, "libfoo.so")
        soFile.setWritable(true)
        soFile.writeBytes("corrupted-bytes".toByteArray())
        soFile.setWritable(false)

        val state = manager.ensureReady(def)

        assertTrue(state is ModuleState.READY)
        assertEquals(downloadsAfterFirst + 1, downloadCount)
        val reinstalled = requireNotNull(manager.installedDir(def)) { "module should be re-installed" }
        assertTrue(File(reinstalled, "libfoo.so").readBytes().contentEquals("fake-libfoo.so".toByteArray()))
    }

    @Test
    fun `cancelDownload should stop the download clean up and report NOT_DOWNLOADED`() = runBlocking {
        var cancelled = false
        val listener = object : ModuleDownloadListener {
            override fun onDownloadCancelled(def: ModuleDefinition) {
                cancelled = true
            }
        }
        var downloadStarted = false
        val source = object : ModuleSource {
            override suspend fun download(def: ModuleDefinition, destination: File, onProgress: (Float) -> Unit) {
                downloadCount++
                downloadStarted = true
                // Stay suspended until the coroutine is cancelled
                suspendCancellableCoroutine<Unit> { }
            }

            override fun cancelActiveDownload() {}
        }
        val manager = manager(source = source, listener = listener)
        val def = def()

        val job = launch { manager.ensureReady(def) }
        while (!downloadStarted) yield()
        manager.cancelDownload(def.id)
        job.join()

        assertEquals(ModuleState.NOT_DOWNLOADED, manager.stateOf(def))
        assertTrue(cancelled)
        val root = File(filesDir, "modules/testmod")
        assertTrue(root.listFiles().orEmpty().none { it.name.startsWith(".tmp-") })
    }

    @Test
    fun `ensureReady should replace the old version dir after an update`() = runBlocking {
        val manager = manager()
        val old = def()
        val updated = def(version = "1.1.0")
        val root = File(filesDir, "modules/testmod")

        manager.ensureReady(old)
        assertTrue(File(root, "1.0.0-x86_64").exists())

        val state = manager.ensureReady(updated)

        assertTrue(state is ModuleState.READY)
        assertTrue(File(root, "1.1.0-x86_64").exists())
        assertFalse(File(root, "1.0.0-x86_64").exists())
        assertTrue(root.listFiles().orEmpty().none { it.name.startsWith(".") })
    }

    @Test
    fun `ensureReady should report downloadNeeded on first use and downloadFailed on an update`() = runBlocking {
        val failingSource = object : ModuleSource {
            override suspend fun download(def: ModuleDefinition, destination: File, onProgress: (Float) -> Unit) {
                throw IOException("network down")
            }

            override fun cancelActiveDownload() {}
        }
        val def = def()

        val first = manager(source = failingSource).ensureReady(def)
        assertTrue(first is ModuleState.FAILED)
        assertEquals("download needed", (first as ModuleState.FAILED).message)

        // Pin a newer version on top of the installed one: the download is an
        // update, so its failure reports the generic downloadFailed message
        manager().ensureReady(def)
        val second = manager(source = failingSource).ensureReady(def(version = "1.1.0"))

        assertTrue(second is ModuleState.FAILED)
        assertEquals("download failed: network down", (second as ModuleState.FAILED).message)
    }
}
