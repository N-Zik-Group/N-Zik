package app.n_zik.android.privacy

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.PackageManager
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

    @Test fun publicClearThenRecordKeepsTheNewEvent() = withPublicProfile("clear-order") { prefs ->
        synchronized(PermissionUsageJournal) {
            assertTrue(PermissionUsageJournal.record(context, PermissionFeature.PLAYBACK, JournalPermission.CAMERA, PermissionEventKind.REQUESTED))
            assertTrue(PermissionUsageJournal.clear(context))
            assertTrue(PermissionUsageJournal.record(context, PermissionFeature.VOICE_SEARCH, JournalPermission.MICROPHONE, PermissionEventKind.REQUESTED))
        }
        awaitEvents(prefs) { it.firstOrNull()?.feature == PermissionFeature.VOICE_SEARCH }
        assertEquals(listOf(PermissionFeature.VOICE_SEARCH), PermissionUsageJournal.read(prefs).map { it.feature })
    }

    @Test fun publicPauseRejectsNewEventsAndSuppressesAlreadyQueuedEvents() = withPublicProfile("pause-boundary") { prefs ->
        synchronized(PermissionUsageJournal) {
            assertTrue(PermissionUsageJournal.record(context, PermissionFeature.VOICE_SEARCH, JournalPermission.MICROPHONE, PermissionEventKind.REQUESTED))
            prefs.edit().putBoolean(PermissionUsageJournal.PAUSED_KEY, true).commit()
            assertFalse(PermissionUsageJournal.record(context, PermissionFeature.PLAYBACK, JournalPermission.CAMERA, PermissionEventKind.REQUESTED))
        }
        setActiveProfile("journal-pause-barrier", context)
        val barrierPrefs = context.preferences
        barrierPrefs.edit().clear().commit()
        try {
            assertTrue(PermissionUsageJournal.record(context, PermissionFeature.PC_SERVER, JournalPermission.LOCAL_NETWORK, PermissionEventKind.REQUESTED))
            awaitEvents(barrierPrefs) { it.isNotEmpty() }
            assertTrue(PermissionUsageJournal.read(prefs).isEmpty())
        } finally {
            barrierPrefs.edit().clear().commit()
        }
    }

    @Test fun publicBurstReportsRejectionAndRetainsNewestAcceptedEvents() = withPublicProfile("burst") { prefs ->
        val accepted = mutableListOf<PermissionFeature>()
        var rejected = 0
        synchronized(PermissionUsageJournal) {
            repeat(600) { index ->
                val feature = PermissionFeature.entries[index % PermissionFeature.entries.size]
                if (PermissionUsageJournal.record(context, feature, JournalPermission.CAMERA, PermissionEventKind.REQUESTED)) accepted += feature
                else rejected++
            }
            assertFalse(PermissionUsageJournal.clear(context))
        }
        assertTrue(accepted.size in 256..257)
        assertTrue(rejected > 0)
        // Wait for the bounded writer to drain before adding a final, distinct barrier event.
        awaitEvents(prefs) { it.size == PermissionUsageJournal.MAX_EVENTS }
        // JUnit is synchronous; bounded waiting lets the actual actor free an admission slot.
        runBlocking {
            withTimeout(5_000) {
                while (!PermissionUsageJournal.record(context, PermissionFeature.PC_SERVER, JournalPermission.LOCAL_NETWORK, PermissionEventKind.GRANTED)) delay(10)
            }
        }
        awaitEvents(prefs) { it.firstOrNull()?.permission == JournalPermission.LOCAL_NETWORK }
        val events = PermissionUsageJournal.read(prefs)
        assertEquals(PermissionUsageJournal.MAX_EVENTS, events.size)
        assertEquals(accepted.takeLast(PermissionUsageJournal.MAX_EVENTS - 1).reversed(), events.drop(1).map { it.feature })
    }

    @Test fun publicNotificationUsageRequiresGrantButRequestsAndDenialsRemain() = withPublicProfile("notification-filter") { prefs ->
        var granted = false
        val permissionContext = object : ContextWrapper(context) {
            override fun checkSelfPermission(permission: String): Int =
                if (permission == Manifest.permission.POST_NOTIFICATIONS && granted) PackageManager.PERMISSION_GRANTED
                else PackageManager.PERMISSION_DENIED
        }
        assertFalse(PermissionUsageJournal.record(permissionContext, PermissionFeature.PLAYBACK, JournalPermission.NOTIFICATIONS, PermissionEventKind.USED))
        PermissionUsageJournal.request(permissionContext, PermissionFeature.PLAYBACK, Manifest.permission.POST_NOTIFICATIONS)
        PermissionUsageJournal.result(permissionContext, PermissionFeature.PLAYBACK, Manifest.permission.POST_NOTIFICATIONS, false)
        granted = true
        assertTrue(PermissionUsageJournal.record(permissionContext, PermissionFeature.PLAYBACK, JournalPermission.NOTIFICATIONS, PermissionEventKind.USED))
        awaitEvents(prefs) { it.size == 3 }
        assertEquals(listOf(PermissionEventKind.USED, PermissionEventKind.DENIED, PermissionEventKind.REQUESTED), PermissionUsageJournal.read(prefs).map { it.kind })
    }

    private fun withPublicProfile(name: String, block: (SharedPreferences) -> Unit) {
        setActiveProfile("journal-public-$name", context)
        val prefs = context.preferences
        prefs.edit().clear().commit()
        try {
            block(prefs)
        } finally {
            prefs.edit().clear().commit()
            setActiveProfile("default", context)
        }
    }

    private fun awaitEvents(prefs: SharedPreferences, predicate: (List<PermissionUsageEvent>) -> Boolean) {
        // JUnit is synchronous; wait for persistence by the real DATA actor instead of bypassing it.
        runBlocking {
            withTimeout(5_000) {
                while (!predicate(PermissionUsageJournal.read(prefs))) delay(10)
            }
        }
    }

}
