package app.n_zik.android.playback.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [RestoredQueueRules]: the persistent queue restored at
 * service start is prepared (pre-buffered) only when auto-resume is enabled,
 * so with auto-resume off the player stays IDLE and media3 shows no media
 * notification while the queue remains visible.
 */
class RestoredQueueRulesTest {

    @Test
    fun `the restored queue is prepared when auto-resume is enabled`() {
        assertTrue(RestoredQueueRules.shouldPrepareRestoredQueue(true))
    }

    @Test
    fun `the restored queue is not prepared when auto-resume is disabled`() {
        assertFalse(RestoredQueueRules.shouldPrepareRestoredQueue(false))
    }
}
