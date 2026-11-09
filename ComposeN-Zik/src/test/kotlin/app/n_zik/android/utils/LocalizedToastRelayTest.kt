package app.n_zik.android.utils

import android.content.Context
import androidx.core.app.AppLocalesStorageHelper
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "en")
class LocalizedToastRelayTest {
    @Test
    @Config(sdk = [32])
    fun `background resource toasts use the saved app language before Android 13`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        val previous = AppLocalesStorageHelper.readLocales(context)
        val messages = mutableListOf<ToastRelay.Relayed>()
        val listener = ToastRelay.Listener { messages += it }
        ToastRelay.addListener(listener)
        try {
            AppLocalesStorageHelper.persistLocales(context, "fr")
            assertEquals("Cancel", context.getString(R.string.common_action_cancel))
            assertEquals("Annuler", ToastRelay.legacyResource(R.string.common_action_cancel, emptyArray()))
            ToastRelay.onLegacyToast("Annuler", "NORMAL")
            ToastRelay.successWithPlural(R.plurals.library_song_count, R.string.song_metadata_updated_count, 3)
            assertEquals("updated_songs_count", messages.last().key)
            assertEquals(listOf("3"), messages.last().args)
            val french = context.createConfigurationContext(
                android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.FRENCH) },
            )
            assertEquals(french.resources.getQuantityString(R.plurals.library_song_count, 3, 3), messages.last().message)
        } finally {
            ToastRelay.removeListener(listener)
            AppLocalesStorageHelper.persistLocales(context, previous)
        }
    }

    @Test
    fun `localized warnings keep the published resource key and arguments`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        val resources = context.resources
        val message = resources.getQuantityString(R.plurals.downloads_stale_startup_removed_count, 1, 1)
        val received = mutableListOf<ToastRelay.Relayed>()
        val listener = ToastRelay.Listener { received += it }
        ToastRelay.addListener(listener)
        try {
            ToastRelay.warningWithResourceIdentity(message, R.string.downloads_stale_startup_removed, 1)
            ToastRelay.loadingWarning()
        } finally {
            ToastRelay.removeListener(listener)
        }
        assertEquals(
            listOf(
                ToastRelay.Relayed(
                    "download_stale_startup_removed",
                    listOf("1"),
                    ToastRelay.Kind.WARNING,
                    "1 downloaded song is missing its file and was removed. Please download it again.",
                ),
                ToastRelay.Relayed("opening_url", emptyList(), ToastRelay.Kind.WARNING, "Loading…"),
            ),
            received,
        )
    }
}
