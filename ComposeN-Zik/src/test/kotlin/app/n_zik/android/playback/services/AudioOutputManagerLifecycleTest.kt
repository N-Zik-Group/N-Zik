package app.n_zik.android.playback.services

import android.app.Application
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AudioOutputManagerLifecycleTest {
    private var manager: AudioOutputManager? = null

    @After
    fun tearDown() {
        manager?.unregisterDeviceChanges()
        unmockkAll()
    }

    @Test
    fun replacementAndRemovalReleaseObserversAndNativeCallbacks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioManager = mockk<AudioManager>(relaxed = true)
        val carType = MutableLiveData(CarConnection.CONNECTION_TYPE_NOT_CONNECTED)
        val deviceCallbacks = mutableListOf<AudioDeviceCallback>()
        val playbackCallbacks = mutableListOf<AudioManager.AudioPlaybackCallback>()
        val activeDevices = mutableSetOf<AudioDeviceCallback>()
        val activePlayback = mutableSetOf<AudioManager.AudioPlaybackCallback>()
        every { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns emptyArray()
        every { audioManager.registerAudioDeviceCallback(any(), any()) } answers {
            firstArg<AudioDeviceCallback>().let { deviceCallbacks.add(it); activeDevices.add(it) }
            Unit
        }
        every { audioManager.unregisterAudioDeviceCallback(any()) } answers {
            activeDevices.remove(firstArg<AudioDeviceCallback>())
            Unit
        }
        every { audioManager.registerAudioPlaybackCallback(any(), any()) } answers {
            firstArg<AudioManager.AudioPlaybackCallback>().let { playbackCallbacks.add(it); activePlayback.add(it) }
            Unit
        }
        every { audioManager.unregisterAudioPlaybackCallback(any()) } answers {
            activePlayback.remove(firstArg<AudioManager.AudioPlaybackCallback>())
            Unit
        }
        val output = AudioOutputManager(context, audioManager, carType).also { manager = it }
        var oldCalls = 0
        var newCalls = 0
        output.registerDeviceChanges { oldCalls++ }
        assertTrue(carType.hasActiveObservers())
        output.registerDeviceChanges { newCalls++ }
        assertEquals(1, activeDevices.size)
        assertEquals(1, activePlayback.size)
        verify(exactly = 1) { audioManager.unregisterAudioDeviceCallback(deviceCallbacks.first()) }
        verify(exactly = 1) { audioManager.unregisterAudioPlaybackCallback(playbackCallbacks.first()) }

        val oldBeforeEvent = oldCalls
        val newBeforeEvent = newCalls
        carType.value = CarConnection.CONNECTION_TYPE_PROJECTION
        assertEquals(oldBeforeEvent, oldCalls)
        assertEquals(newBeforeEvent + 1, newCalls)
        // Simulate framework events already queued before unregistering the old listener.
        deviceCallbacks.first().onAudioDevicesAdded(emptyArray())
        playbackCallbacks.first().onPlaybackConfigChanged(mutableListOf())
        assertEquals(oldBeforeEvent, oldCalls)
        deviceCallbacks.last().onAudioDevicesAdded(emptyArray())
        playbackCallbacks.last().onPlaybackConfigChanged(mutableListOf())
        assertEquals(newBeforeEvent + 3, newCalls)

        output.unregisterDeviceChanges()
        output.unregisterDeviceChanges()
        assertFalse(carType.hasObservers())
        assertTrue(activeDevices.isEmpty())
        assertTrue(activePlayback.isEmpty())
        val callsAfterRemoval = newCalls
        carType.value = CarConnection.CONNECTION_TYPE_NOT_CONNECTED
        deviceCallbacks.last().onAudioDevicesRemoved(emptyArray())
        playbackCallbacks.last().onPlaybackConfigChanged(mutableListOf())
        assertEquals(oldBeforeEvent, oldCalls)
        assertEquals(callsAfterRemoval, newCalls)
        verify(exactly = 1) { audioManager.unregisterAudioDeviceCallback(deviceCallbacks.last()) }
        verify(exactly = 1) { audioManager.unregisterAudioPlaybackCallback(playbackCallbacks.last()) }
    }

    @Test
    fun initialReadUsesTheSameCarSourceAsLaterUpdates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioManager = mockk<AudioManager>(relaxed = true)
        val speaker = mockk<AudioDeviceInfo> {
            every { id } returns 1
            every { type } returns AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            every { isSink } returns true
            every { productName } returns "Speaker"
        }
        every { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns arrayOf(speaker)
        val carType = MutableLiveData(CarConnection.CONNECTION_TYPE_PROJECTION)
        val output = AudioOutputManager(context, audioManager, carType).also { manager = it }
        assertTrue(output.getAvailableDevices().single().isCar)
        var lastDevices = emptyList<AudioOutputManager.AudioDevice>()
        output.registerDeviceChanges { lastDevices = it }
        carType.value = CarConnection.CONNECTION_TYPE_NOT_CONNECTED
        assertFalse(lastDevices.single().isCar)
    }
}
