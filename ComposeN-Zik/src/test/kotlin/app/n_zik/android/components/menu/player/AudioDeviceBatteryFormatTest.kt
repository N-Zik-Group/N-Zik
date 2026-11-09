package app.n_zik.android.components.menu.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "ro")
class AudioDeviceBatteryFormatTest {
    @Test
    fun `Romanian battery level formats a literal percent sign`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Baterie: 73%", context.getString(R.string.audio_device_battery_format, 73))
    }
}
