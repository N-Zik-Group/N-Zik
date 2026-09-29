package app.n_zik.android.playback.utils

import android.content.Context
import android.content.SharedPreferences
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.listentogether.ListenTogetherGuestGuardPlayer
import app.n_zik.android.listentogether.listenTogetherGuestLock
import app.n_zik.android.playback.services.PlayerServiceModern
import app.it.fast4x.rimusic.utils.discoverKey
import app.it.fast4x.rimusic.utils.autoLoadSongsInQueueKey
import app.it.fast4x.rimusic.utils.preferences
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class NZikRadioTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var binder: PlayerServiceModern.Binder
    private lateinit var mockPlayer: ExoPlayer
    private lateinit var nZikRadio: NZikRadio

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        // Mock binder and player
        binder = mockk(relaxed = true)
        mockPlayer = mockk<ExoPlayer>(relaxed = true)
        every { binder.player } returns mockPlayer
        every { mockPlayer.currentMediaItem } returns null

        // Mock static extension property Context.preferences
        mockkStatic("app.it.fast4x.rimusic.utils.PreferencesKt")
        every { context.preferences } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.apply() } just Runs

        // The blocked-op toast throttle is process-wide real-time state; reset it
        // so each guest-lock test starts with a clean 2s window.
        ListenTogetherGuestGuardPlayer.clearUiBlockedOpThrottleForTests()

        // Mock Toaster object
        mockkObject(Toaster)
        every { Toaster.i(any<Int>()) } just Runs
        every { Toaster.i(any<String>()) } just Runs
        every { Toaster.e(any<Int>()) } just Runs

        nZikRadio = NZikRadio(context, binder, CoroutineScope(Dispatchers.Unconfined))
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    private fun setupState(autoFillEnabled: Boolean, discoverEnabled: Boolean) {
        every { sharedPreferences.getBoolean(autoLoadSongsInQueueKey, true) } returns autoFillEnabled
        every { sharedPreferences.getBoolean(discoverKey, false) } returns discoverEnabled
    }

    private fun createMediaItem(): MediaItem {
        return MediaItem.Builder().setMediaId("test_id").build()
    }

    // --- MATRIX CASES ---

    // 1: ON, ON, ON -> Radio started (Discoveries only)
    @Test
    fun `case 1 - startRadio with Radio ON, AutoFill ON, Discover ON`() {
        setupState(autoFillEnabled = true, discoverEnabled = true)
        
        nZikRadio.startRadio(createMediaItem(), isExplicit = true)
        
        verify { Toaster.i(R.string.state_radio_on_discover) }
        assertTrue(nZikRadio.isRadioActive)
    }

    // 2: ON, ON, OFF -> Radio started (Includes known songs)
    @Test
    fun `case 2 - startRadio with Radio ON, AutoFill ON, Discover OFF`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)
        
        nZikRadio.startRadio(createMediaItem(), isExplicit = true)
        
        verify { Toaster.i(R.string.state_radio_on_classic) }
    }

    // 3: ON, OFF, ON -> Radio started (Discoveries only)
    @Test
    fun `case 3 - startRadio with Radio ON, AutoFill OFF, Discover ON`() {
        setupState(autoFillEnabled = false, discoverEnabled = true)
        
        nZikRadio.startRadio(createMediaItem(), isExplicit = true)
        
        verify { Toaster.i(R.string.state_radio_on_discover) }
    }

    // 4: ON, OFF, OFF -> Radio started (Includes known songs)
    @Test
    fun `case 4 - startRadio with Radio ON, AutoFill OFF, Discover OFF`() {
        setupState(autoFillEnabled = false, discoverEnabled = false)
        
        nZikRadio.startRadio(createMediaItem(), isExplicit = true)
        
        verify { Toaster.i(R.string.state_radio_on_classic) }
    }

    // --- AUTO-CORRECTION AND ERROR CASES ---

    @Test
    fun `toggleDiscover when Radio OFF and AutoFill OFF shows error`() {
        setupState(autoFillEnabled = false, discoverEnabled = false)
        
        nZikRadio.toggleDiscover()
        
        verify { Toaster.e(R.string.state_discover_error) }
        verify(exactly = 0) { editor.putBoolean(discoverKey, any()) } // State shouldn't change
    }

    @Test
    fun `toggleDiscover toggles state and shows correct toast`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)
        
        nZikRadio.toggleDiscover()
        
        verify { editor.putBoolean(discoverKey, true) }
        verify { Toaster.i(R.string.state_discover_on) }
    }

    // Reminder matrix: AutoPlay ON + Discover ON -> Reminder: Auto-play is ON
    @Test
    fun `showReminderIfNeeded with AutoPlay ON and Discover ON`() {
        setupState(autoFillEnabled = true, discoverEnabled = true)
        
        nZikRadio.showReminderIfNeeded()
        
        verify { Toaster.i(R.string.state_reminder_discover) }
    }

    // Reminder matrix: AutoPlay OFF + Discover ON -> Auto-correction (Option C)
    @Test
    fun `showReminderIfNeeded with AutoPlay OFF and Discover ON triggers AutoCorrection`() {
        setupState(autoFillEnabled = false, discoverEnabled = true)
        
        nZikRadio.showReminderIfNeeded()
        
        // Verifies no toast is shown
        verify(exactly = 0) { Toaster.i(any<Int>()) }
        // Verifies Auto-Correction happened (Discover turned OFF)
        verify { editor.putBoolean(discoverKey, false) }
    }

    // --- GUEST LOCK (spec-listen-together-guest-lock-hardening) ---

    @Test
    fun `startRadio while guest locked is a no-op (RADIO_GUEST)`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)
        listenTogetherGuestLock.value = true

        try {
            nZikRadio.startRadio(createMediaItem(), isExplicit = true)

            assertFalse(nZikRadio.isRadioActive, "no radio state change for a locked guest")
            // No "Radio ON" toast, only the shared blocked toast (String overload).
            verify(exactly = 0) { Toaster.i(any<Int>()) }
            verify { Toaster.i(any<String>()) }
            // The blocked op stays atomic: no forcePlay, no queue injection.
            verify(exactly = 0) { mockPlayer.setMediaItem(any()) }
            verify(exactly = 0) { mockPlayer.addMediaItems(any()) }
        } finally {
            listenTogetherGuestLock.value = false
        }
    }

    @Test
    fun `toggleDiscover while guest locked is a no-op (DISCOVER_GUEST)`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)
        listenTogetherGuestLock.value = true

        try {
            nZikRadio.toggleDiscover()

            // No preference write, no "Discover ON/OFF" toast — only the shared blocked toast.
            verify(exactly = 0) { editor.putBoolean(discoverKey, any()) }
            verify(exactly = 0) { Toaster.i(any<Int>()) }
            verify { Toaster.i(any<String>()) }
        } finally {
            listenTogetherGuestLock.value = false
        }
    }

    @Test
    fun `autoFillQueue while guest locked skips the fetch and injection (QUIET_GUEST)`() {
        // The silent guard returns before any Innertube fetch / queue injection — in a room the
        // host owns the queue, and the auto-fill would only be no-op'd by the central guard
        // (a toast + a wasted fetch on every host-side track transition).
        setupState(autoFillEnabled = true, discoverEnabled = false)
        every { mockPlayer.currentMediaItem } returns createMediaItem()
        listenTogetherGuestLock.value = true

        try {
            nZikRadio.autoFillQueue()

            verify(exactly = 0) { mockPlayer.addMediaItems(any()) }
            verify(exactly = 0) { mockPlayer.setMediaItem(any()) }
            verify(exactly = 0) { Toaster.i(any<Int>()) }
            verify(exactly = 0) { Toaster.i(any<String>()) }
        } finally {
            listenTogetherGuestLock.value = false
        }
    }

    @Test
    fun `showReminderIfNeeded while guest locked performs no auto-correction (QUIET_GUEST)`() {
        // Discover ON + AutoFill OFF is the auto-correction case (it would flip the host-owned
        // Discover setting off) — a locked guest must get no state change and no toast at all.
        setupState(autoFillEnabled = false, discoverEnabled = true)
        listenTogetherGuestLock.value = true

        try {
            nZikRadio.showReminderIfNeeded()

            verify(exactly = 0) { editor.putBoolean(discoverKey, any()) }
            verify(exactly = 0) { Toaster.i(any<Int>()) }
            verify(exactly = 0) { Toaster.i(any<String>()) }
        } finally {
            listenTogetherGuestLock.value = false
        }
    }
}
