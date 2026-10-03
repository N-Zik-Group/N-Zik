package app.n_zik.android.bridge

import app.n_zik.android.bridge.state.BridgeStateHub
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Entry-point contract of [BridgeServerController.selectAudioOutput] (contract §8.5): the
 * device menu toasts a refusal only when the Boolean is false, so the no-server and the
 * no-session paths must be pinned at this level, not only on the executor.
 */
class BridgeServerControllerTest {

    @Test
    fun `without a running server pc is refused and phone is accepted`() = runTest {
        BridgeServerController.attachOutputs(null)

        assertFalse(BridgeServerController.selectAudioOutput(AudioOutput.PC))
        assertTrue(BridgeServerController.selectAudioOutput(AudioOutput.PHONE))
    }

    @Test
    fun `pc is refused without an active session, phone is still accepted`() = runTest {
        val outputs = AudioOutputController(BridgeStateHub(), null, { false })
        BridgeServerController.attachOutputs(outputs)
        try {
            assertFalse(BridgeServerController.selectAudioOutput(AudioOutput.PC))
            assertTrue(BridgeServerController.selectAudioOutput(AudioOutput.PHONE))
        } finally {
            BridgeServerController.attachOutputs(null)
        }
    }
}
