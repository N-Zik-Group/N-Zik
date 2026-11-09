package app.n_zik.android.components.settings.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataStorageBreakdownTest {
    @Test
    fun countsEmptyCacheInfrastructureAsOtherNzikData() {
        assertEquals(StorageCategory.OTHER_NZIK, storageAllocationCategory(StorageCategory.IMAGES, "journal", false))
        assertEquals(StorageCategory.OTHER_NZIK, storageAllocationCategory(StorageCategory.STREAMING, "ab12.uid", false))
        assertEquals(StorageCategory.OTHER_NZIK, storageAllocationCategory(StorageCategory.DOWNLOADS, "exo_downloads", true))
        assertEquals(StorageCategory.IMAGES, storageAllocationCategory(StorageCategory.IMAGES, "artwork.0", false))
        assertEquals(StorageCategory.DOWNLOADS, storageAllocationCategory(StorageCategory.DOWNLOADS, "audio.v3.exo", false))
    }

    @Test
    fun partitionsCapacityWithDisjointAppDataAndResidual() {
        val app = mapOf(
            StorageCategory.IMAGES to 10L,
            StorageCategory.STREAMING to 15L,
            StorageCategory.DOWNLOADS to 20L,
            StorageCategory.COVERS to 25L,
            StorageCategory.WAVEFORMS to 30L,
            StorageCategory.OTHER_NZIK to 35L,
        )

        val result = requireNotNull(partitionStorage(totalBytes = 500L, freeBytes = 150L, appBytes = app))

        assertEquals(app, result.bytes.filterKeys { it in app })
        assertEquals(215L, result.bytes.getValue(StorageCategory.OTHER_USED))
        assertEquals(150L, result.bytes.getValue(StorageCategory.FREE))
        assertEquals(135L, result.nzikBytes)
        assertEquals(result.totalBytes, result.bytes.values.sum())
    }

    @Test
    fun keepsZeroSizeCategoriesInLegendOrder() {
        val result = requireNotNull(partitionStorage(100L, 100L, emptyMap()))

        assertEquals(StorageCategory.entries.toList(), result.bytes.keys.toList())
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 0L, 0L, 100L), result.bytes.values.toList())
        assertNotNull(partitionStorage(100L, 0L, mapOf(StorageCategory.OTHER_NZIK to 100L)))
    }

    @Test
    fun rejectsInvalidVolumeSamples() {
        assertNull(partitionStorage(0L, 0L, emptyMap()))
        assertNull(partitionStorage(100L, -1L, emptyMap()))
        assertNull(partitionStorage(100L, 101L, emptyMap()))
    }

    @Test
    fun rejectsInconsistentAppReadingsWithoutOverflow() {
        assertNull(partitionStorage(100L, 20L, mapOf(StorageCategory.DOWNLOADS to 81L)))
        assertNull(partitionStorage(100L, 20L, mapOf(StorageCategory.COVERS to -1L)))
        assertNull(
            partitionStorage(
                Long.MAX_VALUE,
                0L,
                mapOf(StorageCategory.DOWNLOADS to Long.MAX_VALUE, StorageCategory.COVERS to 1L),
            ),
        )
    }

    @Test
    fun classifiesActiveSharedAndLegacyCacheNamesInBothInternalRoots() {
        val directories = StorageDirectories(listOf("Work"))

        assertEquals(StorageCategory.STREAMING, directories.category("exoplayer", false, true, false))
        assertEquals(StorageCategory.STREAMING, directories.category("exoplayer", true, false, false))
        assertEquals(StorageCategory.IMAGES, directories.category("coil_Work", true, false, false))
        assertEquals(StorageCategory.DOWNLOADS, directories.category("exo_downloads_Previous Profile", false, true, false))
        assertEquals(StorageCategory.COVERS, directories.category("app_covers_Work", true, false, false))
        assertEquals(StorageCategory.WAVEFORMS, directories.category("waveforms", true, false, false))
        assertEquals(StorageCategory.OTHER_NZIK, directories.category("app_covers", false, true, false))
        assertEquals(StorageCategory.OTHER_NZIK, directories.category("exo_downloads", false, false, false))
    }

    @Test
    fun distinguishesKnownAudioTempPrefixesFromOtherFolders() {
        val directories = StorageDirectories(listOf("Work"))

        assertEquals(StorageCategory.DOWNLOADS, directories.category("exo_downloads18446744073709551615", false, true, true))
        assertEquals(StorageCategory.STREAMING, directories.category("exoplayer_Work3512175432", false, true, true))
        assertEquals(StorageCategory.OTHER_NZIK, directories.category("exoplayer3512175432", false, true, false))
        assertEquals(StorageCategory.OTHER_NZIK, directories.category("exoplayer123extra", false, true, true))
    }

    @Test
    fun samplesImmediatelyAndEveryTwoSecondsAndSkipsFailuresUntilCancelled() = runTest {
        val first = requireNotNull(partitionStorage(100L, 80L, emptyMap()))
        val afterDownload = requireNotNull(partitionStorage(100L, 70L, mapOf(StorageCategory.DOWNLOADS to 10L)))
        val received = mutableListOf<StorageBreakdown>()
        val readings = listOf(null, first, null, afterDownload, first)
        var attempts = 0
        val sample: suspend () -> StorageBreakdown? = { readings[attempts++] }
        val collection = launch { storageSamples(sample).collect { received += it } }

        runCurrent()
        assertEquals(1, attempts)
        assertEquals(emptyList<StorageBreakdown>(), received)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf(first), received)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf(first), received)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf(first, afterDownload), received)

        collection.cancelAndJoin()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(4, attempts)

        // A cleanup restarts collection and does not wait for the next timer tick.
        val afterCleanup = launch { storageSamples(sample).collect { received += it } }
        runCurrent()
        assertEquals(listOf(first, afterDownload, first), received)
        afterCleanup.cancelAndJoin()
    }
}
