package app.n_zik.android.components.settings.data

import android.content.Context
import android.content.pm.ApplicationInfo
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.profiles.ScopedTemporaryFiles
import app.n_zik.android.core.profiles.resolveProfileDataDirNames
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.UUID
import java.util.concurrent.TimeUnit
import app.it.fast4x.rimusic.utils.PROFILE_NAMES_FILE_NAME
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import app.n_zik.android.core.profiles.invalidateProfileStorageIdentity
import app.n_zik.android.core.profiles.profileStorageIdentity
import io.mockk.every
import io.mockk.mockk
import app.n_zik.android.core.rescue.ProfileResetSnapshot
import java.io.DataOutputStream
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ProfileStorageCleanupTargetTest {
    @TempDir
    lateinit var files: File

    @Test
    fun `confirmation for a previous generation leaves the recreated profile untouched`() {
        val context = mockk<Context>()
        every { context.filesDir } returns files
        val registry = files.resolve(PROFILE_NAMES_FILE_NAME)
        registry.writeText("travel\tTravel")
        val original = checkNotNull(profileStorageIdentity(context, "travel"))
        val directory = files.resolve("waveforms_travel").apply { mkdirs() }
        val waveform = directory.resolve("song.json")
        val confirmed = WaveformCleanupTarget(original, directory, 1, 2, emptyList())

        DatabaseRestoreJournal.withProfileStorageMutation(context) {
            invalidateProfileStorageIdentity(context, "travel")
            registry.writeText("")
        }
        registry.writeText("travel\tTravel")
        waveform.writeText("recreated")

        assertEquals(CleanupResult.SKIPPED, cleanConfirmedWaveforms(context, confirmed))
        assertEquals("recreated", waveform.readText())
    }

    @Test
    fun `inactive image cleanup clears both cache locations and only its metadata`() {
        val context = storageContext()
        val owner = checkNotNull(profileStorageIdentity(context, "travel"))
        val name = resolveProfileDataDirNames(owner.id, emptySet()).imageCacheDir
        val regular = "a".repeat(64) + ".0"
        val system = context.cacheDir.resolve(name).apply { mkdirs() }
        val private = context.filesDir.resolve(name).apply { mkdirs() }
        val toDelete = listOf(system.resolve(regular), system.resolve("journal"))
        val prefs = files.resolve("shared_prefs").apply { mkdirs() }
        val ownedMetadata = prefs.resolve(ImageCacheFactory.profileMetadataFiles(owner).first())
        val covers = context.filesDir.resolve("app_covers_travel").apply { mkdirs() }
        val preserved = listOf(
            system.resolve("unknown.jpg"), private.resolve("journal"), prefs.resolve("image_cache_metadata.xml"),
            covers.resolve("custom.jpg"), context.cacheDir.resolve("coil").apply { mkdirs() }.resolve(regular),
        )
        (toDelete + ownedMetadata + preserved).forEach { it.writeText("content") }

        val confirmed = checkNotNull(captureFiles(context, owner, CleanupAction.IMAGES))
        assertEquals(CleanupResult.COMPLETED, cleanConfirmedFiles(context, confirmed))
        (toDelete + ownedMetadata).forEach { assertFalse(it.exists()) }
        preserved.forEach { assertTrue(it.isFile) }
    }

    @Test
    fun `profile and installation temporary plans preserve recent unknown and old unowned files`() {
        val context = storageContext()
        val owner = checkNotNull(profileStorageIdentity(context, "travel"))
        val directory = ScopedTemporaryFiles.directory(context, ScopedTemporaryFiles.PRIVATE_ARTWORK, owner).apply { mkdirs() }
        val installation = ScopedTemporaryFiles.publicArtwork(context).apply { mkdirs() }
        val expired = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)
        val profileFile = directory.resolve("artwork_1.jpg")
        val installationFile = installation.resolve("artwork_2.jpg")
        val recent = directory.resolve("artwork_3.jpg")
        val unknown = directory.resolve("document.jpg")
        val oldUnowned = context.cacheDir.resolve("artwork_4.jpg")
        val abandoned = ScopedTemporaryFiles.directory(context, ScopedTemporaryFiles.IMAGE_EDITOR, owner)
            .resolve(UUID.randomUUID().toString()).apply { mkdirs() }.resolve("input.jpg")
        (listOf(profileFile, installationFile, unknown, oldUnowned)).forEach {
            it.writeText("old")
            assertTrue(it.setLastModified(expired))
        }
        recent.writeText("recent")
        abandoned.writeText("draft")

        val profilePlan = checkNotNull(captureFiles(context, owner, CleanupAction.TEMPORARY_FILES))
        assertEquals(CleanupResult.COMPLETED, cleanConfirmedFiles(context, profilePlan))
        assertFalse(profileFile.exists())
        assertFalse(abandoned.exists())
        assertTrue(installationFile.isFile)

        val installationPlan = checkNotNull(captureFiles(context, null, CleanupAction.TEMPORARY_FILES))
        assertEquals(CleanupResult.COMPLETED, cleanConfirmedFiles(context, installationPlan))
        assertFalse(installationFile.exists())
        listOf(recent, unknown, oldUnowned).forEach { assertTrue(it.isFile) }
    }

    @Test
    fun `deferred active cache cleanup skips changed files and replays an unchanged confirmation`() {
        val context = storageContext()
        val owner = checkNotNull(profileStorageIdentity(context, "default"))
        val coil = context.cacheDir.resolve("coil").apply { mkdirs() }
        val entry = coil.resolve("a".repeat(64) + ".0").apply { writeText("before") }
        val prefs = files.resolve("shared_prefs").apply { mkdirs() }
        val metadata = prefs.resolve(ImageCacheFactory.profileMetadataFiles(owner).first()).apply { writeText("metadata") }
        val unrelated = prefs.resolve("image_cache_metadata.xml").apply { writeText("legacy") }
        fun schedule() {
            val confirmed = checkNotNull(captureFiles(context, owner, CleanupAction.IMAGES, allowActive = true))
            assertTrue(DeferredProfileCleanup.schedule(context, confirmed))
        }

        schedule()
        assertEquals(setOf(owner to CleanupAction.IMAGES), DeferredProfileCleanup.pendingActions(context))
        schedule()
        assertEquals(1, context.filesDir.resolve(".profile-cache-cleanup").listFiles().orEmpty().size)
        entry.writeText("after confirmation")
        DeferredProfileCleanup.resume(context)
        assertEquals(listOf(CleanupResult.SKIPPED), DeferredProfileCleanup.lastRun.value)
        assertEquals("after confirmation", entry.readText())
        assertTrue(metadata.isFile)

        schedule()
        DeferredProfileCleanup.resume(context)
        assertEquals(listOf(CleanupResult.COMPLETED), DeferredProfileCleanup.lastRun.value)
        assertFalse(entry.exists())
        assertFalse(metadata.exists())
        assertEquals("legacy", unrelated.readText())
    }

    @Test
    fun `deferred diagnostics remove only unchanged confirmed log files`() {
        val context = storageContext()
        val owner = checkNotNull(profileStorageIdentity(context, "default"))
        val directory = context.filesDir.resolve("profiles/default/diagnostics").apply { mkdirs() }
        val debug = directory.resolve("N-Zik_log.txt").apply { writeText("debug") }
        val crash = directory.resolve("N-Zik_crash_log.txt").apply { writeText("crash") }
        val unrelated = directory.resolve("notes.txt").apply { writeText("keep") }
        fun schedule() {
            val confirmed = checkNotNull(captureFiles(context, owner, CleanupAction.DIAGNOSTIC_LOGS, allowActive = true))
            assertTrue(DeferredProfileCleanup.schedule(context, confirmed))
        }

        schedule()
        crash.appendText(" new crash")
        DeferredProfileCleanup.resume(context)
        assertEquals(listOf(CleanupResult.SKIPPED), DeferredProfileCleanup.lastRun.value)
        assertTrue(debug.isFile)
        assertEquals("crash new crash", crash.readText())

        schedule()
        DeferredProfileCleanup.resume(context)
        assertEquals(listOf(CleanupResult.COMPLETED), DeferredProfileCleanup.lastRun.value)
        assertFalse(debug.exists())
        assertFalse(crash.exists())
        assertEquals("keep", unrelated.readText())
        assertTrue(directory.isDirectory)
    }

    @Test
    fun `cache journals and empty logs do not schedule a restart by themselves`() {
        val context = storageContext()
        val owner = checkNotNull(profileStorageIdentity(context, "default"))
        val coil = context.cacheDir.resolve("coil").apply { mkdirs() }.resolve("journal").apply { writeText("header") }
        val http = context.cacheDir.resolve("nzik-profile-network/default").apply { mkdirs() }
            .resolve("journal").apply { writeText("header") }
        val log = context.filesDir.resolve("profiles/default/diagnostics").apply { mkdirs() }
            .resolve("N-Zik_log.txt").apply { writeText("") }
        for (action in listOf(CleanupAction.IMAGES, CleanupAction.NETWORK, CleanupAction.DIAGNOSTIC_LOGS)) {
            val confirmed = checkNotNull(captureFiles(context, owner, action, allowActive = true))
            assertTrue(confirmed.all { it.files.isEmpty() })
            assertFalse(DeferredProfileCleanup.canSchedule(confirmed))
        }
        assertFalse(isMeasuredCleanupPayload(StorageCategory.IMAGES, coil, coil.length()))
        assertFalse(isMeasuredCleanupPayload(StorageCategory.NETWORK, http, http.length()))
        assertFalse(isMeasuredCleanupPayload(StorageCategory.DIAGNOSTICS, log, log.length()))
        val oldPlan = context.filesDir.resolve(".profile-cache-cleanup/${UUID.randomUUID()}").apply { mkdirs() }
        val cache = checkNotNull(coil.parentFile)
        DataOutputStream(oldPlan.resolve("pending").outputStream()).use { output ->
            output.writeUTF(owner.id)
            output.writeUTF(owner.generation)
            output.writeUTF(CleanupAction.IMAGES.name)
            output.writeInt(1)
            output.writeUTF(cache.path)
            output.writeUTF(ProfileResetSnapshot.fingerprint(cache))
        }
        assertTrue(DeferredProfileCleanup.pendingActions(context).isEmpty())
        DeferredProfileCleanup.resume(context)
        assertFalse(oldPlan.exists())
        assertTrue(coil.isFile)
    }

    @Test
    fun `startup discards a former scheduled plan before touching its live files`() {
        val context = storageContext()
        val owner = checkNotNull(profileStorageIdentity(context, "default"))
        val directory = context.filesDir.resolve("profiles/default/diagnostics").apply { mkdirs() }
        val log = directory.resolve("N-Zik_log.txt").apply { writeText("keep old scheduled data") }
        val confirmed = checkNotNull(captureFiles(context, owner, CleanupAction.DIAGNOSTIC_LOGS, allowActive = true))
        assertTrue(DeferredProfileCleanup.schedule(context, confirmed))

        DeferredProfileCleanup.resume(context, discardUnstarted = true)

        assertEquals("keep old scheduled data", log.readText())
        assertTrue(DeferredProfileCleanup.pendingActions(context).isEmpty())
    }

    private fun storageContext(): Context {
        val context = mockk<Context>(relaxed = true)
        val privateFiles = files.resolve("files").apply { mkdirs() }
        every { context.filesDir } returns privateFiles
        every { context.cacheDir } returns files.resolve("cache").apply { mkdirs() }
        every { context.externalCacheDir } returns null
        every { context.applicationInfo } returns mockk<ApplicationInfo>().apply { dataDir = files.path }
        privateFiles.resolve(PROFILE_NAMES_FILE_NAME).writeText("travel\tTravel")
        return context
    }
}
