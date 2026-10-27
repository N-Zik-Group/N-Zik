package app.n_zik.android.privacy

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class VoicePermissionJournalTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: SharedPreferences

    @Before fun setup() {
        setActiveProfile("voice-permission-journal-test", context)
        prefs = context.preferences
        prefs.edit().clear().commit()
    }

    @After fun cleanup() {
        prefs.edit().clear().commit()
        setActiveProfile("default", context)
    }

    @Test fun denialAndLaterGrantRecordOneDecisionPerRequestWithoutInventingUsage() {
        PermissionUsageJournal.request(context, PermissionFeature.VOICE_SEARCH, Manifest.permission.RECORD_AUDIO)
        PermissionUsageJournal.result(context, PermissionFeature.VOICE_SEARCH, Manifest.permission.RECORD_AUDIO, false)
        PermissionUsageJournal.request(context, PermissionFeature.VOICE_SEARCH, Manifest.permission.RECORD_AUDIO)
        PermissionUsageJournal.result(context, PermissionFeature.VOICE_SEARCH, Manifest.permission.RECORD_AUDIO, true)
        awaitCount(4)
        val events = PermissionUsageJournal.read(prefs)
        assertEquals(listOf(PermissionEventKind.GRANTED, PermissionEventKind.REQUESTED,
            PermissionEventKind.DENIED, PermissionEventKind.REQUESTED), events.map { it.kind })
        assertEquals(setOf(PermissionFeature.VOICE_SEARCH), events.map { it.feature }.toSet())
        assertEquals(setOf(JournalPermission.MICROPHONE), events.map { it.permission }.toSet())
    }

    @Test fun pausedVoiceRequestsAndDecisionsStayAbsentAfterWriterBarrier() {
        prefs.edit().putBoolean(PermissionUsageJournal.PAUSED_KEY, true).commit()
        PermissionUsageJournal.request(context, PermissionFeature.VOICE_SEARCH, Manifest.permission.RECORD_AUDIO)
        PermissionUsageJournal.result(context, PermissionFeature.VOICE_SEARCH, Manifest.permission.RECORD_AUDIO, false)
        PermissionUsageJournal.result(context, PermissionFeature.VOICE_SEARCH, Manifest.permission.RECORD_AUDIO, true)
        prefs.edit().putBoolean(PermissionUsageJournal.PAUSED_KEY, false).commit()
        PermissionUsageJournal.request(context, PermissionFeature.PC_PAIRING, Manifest.permission.CAMERA)
        awaitCount(1)
        assertEquals(PermissionFeature.PC_PAIRING, PermissionUsageJournal.read(prefs).single().feature)
    }

    private fun awaitCount(count: Int) {
        // JUnit is synchronous; wait for the real FIFO DATA writer rather than bypassing it.
        runBlocking {
            withTimeout(5_000) {
                while (PermissionUsageJournal.read(prefs).size < count) delay(10)
            }
        }
    }
}
