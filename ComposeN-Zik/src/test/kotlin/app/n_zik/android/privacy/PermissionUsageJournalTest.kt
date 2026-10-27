package app.n_zik.android.privacy

import android.Manifest
import android.app.Application
import android.content.Context
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PermissionUsageJournalTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val first = context.getSharedPreferences("journal-first", Context.MODE_PRIVATE)
    private val second = context.getSharedPreferences("journal-second", Context.MODE_PRIVATE)
    private fun event(time: Long, kind: PermissionEventKind = PermissionEventKind.USED) =
        PermissionUsageEvent(time, PermissionFeature.MUSIC_RECOGNITION, JournalPermission.MICROPHONE, kind)

    @Before fun setup() {
        first.edit().clear().commit()
        second.edit().clear().commit()
    }

    @Test fun requestAndPermissionDecisionNeverBecomeUsageEvents() {
        PermissionEventKind.entries.forEachIndexed { index, kind -> PermissionUsageJournal.appendNow(first, event(index.toLong(), kind)) }
        assertEquals(PermissionEventKind.entries.reversed(), PermissionUsageJournal.read(first).map { it.kind })
        assertEquals(1, PermissionUsageJournal.read(first).count { it.kind == PermissionEventKind.USED })
    }

    @Test fun repeatedUsesAreCoalescedButSeparateRequestsAreKept() {
        PermissionUsageJournal.appendNow(first, event(1000))
        PermissionUsageJournal.appendNow(first, event(2000))
        PermissionUsageJournal.appendNow(first, event(31_000))
        PermissionUsageJournal.appendNow(first, event(31_001, PermissionEventKind.REQUESTED))
        PermissionUsageJournal.appendNow(first, event(31_002, PermissionEventKind.REQUESTED))
        assertEquals(listOf(31_002L, 31_001L, 31_000L, 1000L), PermissionUsageJournal.read(first).map { it.timestamp })
    }

    @Test fun pauseSuppressesQueuedEventsAndClearPreservesOtherProfileAndSettings() {
        first.edit().putString("sentinel", "keep").commit()
        PermissionUsageJournal.appendNow(first, event(1))
        PermissionUsageJournal.appendNow(second, event(2))
        first.edit().putBoolean(PermissionUsageJournal.PAUSED_KEY, true).commit()
        PermissionUsageJournal.appendNow(first, event(90_000))
        assertEquals(1, PermissionUsageJournal.read(first).size)
        PermissionUsageJournal.clearNow(first)
        assertTrue(PermissionUsageJournal.read(first).isEmpty())
        assertEquals(1, PermissionUsageJournal.read(second).size)
        assertEquals("keep", first.getString("sentinel", null))
        assertTrue(first.getBoolean(PermissionUsageJournal.PAUSED_KEY, false))
    }

    @Test fun boundedHistoryContainsOnlyAllowedMetadata() {
        repeat(250) { PermissionUsageJournal.appendNow(first, event(it * 31_000L)) }
        val events = PermissionUsageJournal.read(first)
        assertEquals(PermissionUsageJournal.MAX_EVENTS, events.size)
        assertEquals(249 * 31_000L, events.first().timestamp)
        assertEquals(50 * 31_000L, events.last().timestamp)
        val raw = first.getString(PermissionUsageJournal.EVENTS_KEY, "").orEmpty()
        assertFalse(raw.contains("path"))
        assertFalse(raw.contains("audioData"))
        assertFalse(raw.contains("account"))
    }

    @Test fun corruptAndUnknownEventsAreIgnoredWithoutBlockingNewEvents() {
        first.edit().putString(PermissionUsageJournal.EVENTS_KEY, "broken").commit()
        assertTrue(PermissionUsageJournal.read(first).isEmpty())
        PermissionUsageJournal.appendNow(first, event(1))
        assertEquals(1, PermissionUsageJournal.read(first).size)
        first.edit().putString(PermissionUsageJournal.EVENTS_KEY,
            """[{"time":1,"feature":"UNKNOWN","permission":"MICROPHONE","kind":"USED"}]""").commit()
        assertTrue(PermissionUsageJournal.read(first).isEmpty())
    }

    @Test fun runtimePermissionNamesMapWithoutStoringArbitraryPermissionPayloads() {
        assertEquals(JournalPermission.CAMERA, PermissionUsageJournal.fromAndroidPermission(Manifest.permission.CAMERA))
        assertEquals(JournalPermission.MEDIA_AUDIO, PermissionUsageJournal.fromAndroidPermission(Manifest.permission.READ_MEDIA_AUDIO))
        assertEquals(JournalPermission.MEDIA_AUDIO, PermissionUsageJournal.fromAndroidPermission(Manifest.permission.READ_EXTERNAL_STORAGE))
        assertNull(PermissionUsageJournal.fromAndroidPermission("unrecognized-private-string"))
    }
    @Test fun asynchronousEventKeepsItsOriginatingProfile() {
        // JUnit invokes a synchronous test; wait for the real DATA consumer to persist the event.
        runBlocking {
            setActiveProfile("journal-origin", context)
            val origin = context.preferences
            origin.edit().clear().commit()
            PermissionUsageJournal.record(context, PermissionFeature.VOICE_SEARCH, JournalPermission.MICROPHONE, PermissionEventKind.USED)
            setActiveProfile("journal-destination", context)
            val destination = context.preferences
            destination.edit().clear().commit()
            try {
                withTimeout(5_000) { while (PermissionUsageJournal.read(origin).isEmpty()) delay(10) }
                assertEquals(PermissionFeature.VOICE_SEARCH, PermissionUsageJournal.read(origin).single().feature)
                assertTrue(PermissionUsageJournal.read(destination).isEmpty())
            } finally {
                origin.edit().clear().commit()
                destination.edit().clear().commit()
                setActiveProfile("default", context)
            }
        }
    }

}
