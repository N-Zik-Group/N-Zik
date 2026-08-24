package app.n_zik.android

import android.content.Context
import android.content.SharedPreferences
import app.it.fast4x.rimusic.utils.pauseOnHeadphoneDisconnectKey
import app.it.fast4x.rimusic.utils.preferences
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PauseOnHeadphoneDisconnectTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)

        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context

        mockkStatic("app.it.fast4x.rimusic.utils.PreferencesKt")
        every { context.preferences } returns sharedPreferences
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `isPauseOnHeadphoneDisconnectEnabled returns true when preference is true`() {
        every { sharedPreferences.getBoolean(pauseOnHeadphoneDisconnectKey, false) } returns true

        val result = isPauseOnHeadphoneDisconnectEnabled()

        assertTrue(result)
    }

    @Test
    fun `isPauseOnHeadphoneDisconnectEnabled returns false when preference is false`() {
        every { sharedPreferences.getBoolean(pauseOnHeadphoneDisconnectKey, false) } returns false

        val result = isPauseOnHeadphoneDisconnectEnabled()

        assertFalse(result)
    }

    @Test
    fun `isPauseOnHeadphoneDisconnectEnabled returns false by default`() {
        every { sharedPreferences.getBoolean(pauseOnHeadphoneDisconnectKey, false) } returns false

        val result = isPauseOnHeadphoneDisconnectEnabled()

        assertFalse(result)
        verify { sharedPreferences.getBoolean(pauseOnHeadphoneDisconnectKey, false) }
    }
}
