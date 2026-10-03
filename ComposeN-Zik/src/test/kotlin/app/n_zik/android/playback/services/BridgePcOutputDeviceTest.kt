package app.n_zik.android.playback.services

import android.media.AudioDeviceInfo
import app.n_zik.android.bridge.ActiveDevice
import app.n_zik.android.bridge.AudioOutput
import app.n_zik.android.components.menu.player.AudioDevice
import app.n_zik.android.components.menu.player.AudioDeviceType
import app.n_zik.android.components.menu.player.withBridgePcEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** The connected PC as an audio output of the phone (bridge contract §8.5). */
class BridgePcOutputDeviceTest {

    private val speaker = AudioOutputManager.AudioDevice(1, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Speaker", isCurrentlyActive = true)
    private val headset = AudioOutputManager.AudioDevice(2, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Buds", isCurrentlyActive = false)
    private val pc = ActiveDevice("Ab3dE5fG7hI", "PC-SALON")

    @Test
    fun `no connected PC leaves the phone outputs untouched`() {
        val devices = listOf(speaker, headset)

        assertSame(devices, AudioOutputManager.withBridgePc(devices, null, AudioOutput.PHONE, "Connected PC"))
    }

    @Test
    fun `a connected PC is listed, inactive while the phone sounds`() {
        val result = AudioOutputManager.withBridgePc(listOf(speaker, headset), pc, AudioOutput.PHONE, "Connected PC")

        assertEquals(listOf(1, 2, AudioOutputManager.TYPE_BRIDGE_PC), result.map { it.id })
        assertEquals(listOf(true, false, false), result.map { it.isCurrentlyActive })
        assertEquals("PC-SALON", result.last().name)
    }

    @Test
    fun `on the pc output the PC is the only active device, first`() {
        val result = AudioOutputManager.withBridgePc(listOf(speaker, headset), pc.copy(deviceName = ""), AudioOutput.PC, "Connected PC")

        assertEquals(AudioOutputManager.TYPE_BRIDGE_PC, result.first().type)
        assertEquals("Connected PC", result.first().name)
        assertEquals(listOf(true, false, false), result.map { it.isCurrentlyActive })
    }

    @Test
    fun `audio device menu gets a PC entry that is the only active one on the pc output`() {
        val phone = AudioDevice("This phone", AudioDeviceType.PHONE_SPEAKER, isConnected = true, isActive = true, deviceId = 1)

        assertEquals(listOf(phone), withBridgePcEntry(listOf(phone), null, AudioOutput.PC, "Connected PC"))
        val onPhone = withBridgePcEntry(listOf(phone), pc, AudioOutput.PHONE, "Connected PC")
        assertEquals(listOf(true, false), onPhone.map { it.isActive })
        assertEquals(AudioDeviceType.PC, onPhone.last().type)
        val onPc = withBridgePcEntry(listOf(phone), pc, AudioOutput.PC, "Connected PC")
        assertEquals(listOf(false, true), onPc.map { it.isActive })
    }
}
