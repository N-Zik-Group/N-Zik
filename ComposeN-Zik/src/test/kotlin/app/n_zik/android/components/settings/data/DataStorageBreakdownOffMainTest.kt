package app.n_zik.android.components.settings.data

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DataStorageBreakdownOffMainTest {
    @Test
    fun `opens the private storage root off the calling thread and rejects access failures`() = runTest {
        val callingThread = Thread.currentThread()
        var measuringThread: Thread? = null
        val context = mockk<Context>()
        every { context.applicationInfo } answers {
            measuringThread = Thread.currentThread()
            throw SecurityException("Storage unavailable")
        }

        assertNull(measureStorage(context))
        assertNotNull(measuringThread)
        assertNotEquals(callingThread, measuringThread)
    }
}
