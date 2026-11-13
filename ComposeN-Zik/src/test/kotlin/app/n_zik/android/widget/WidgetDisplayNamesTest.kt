package app.n_zik.android.widget

import android.app.Application
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.R
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetDisplayNamesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun options(height: Int) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 340)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 340)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
    }

    // Rendering RemoteViews is suspend-only; Robolectric owns the synchronous boundary.
    @Test fun `full and compact widgets clean metadata and keep exactly one explicit badge`() = runBlocking {
        for (height in listOf(100, 240)) {
            for (title in listOf("modified:e:Title: live", "\uD83C\uDD74 Title: live")) {
                val views = NZikWidgetManager.createRemoteViewsForSize(
                    context, options(height), title, "modified:Artist: collective", null,
                    false, true, 120_000, 10_000, DefaultDarkColorPalette, null,
                )
                val root = views.apply(context, FrameLayout(context))
                val titleId = if (height == 100) R.id.widget_wide_song_title else R.id.widget_song_title
                val artistId = if (height == 100) R.id.widget_wide_artist_name else R.id.widget_artist_name
                assertEquals("\uD83C\uDD74 Title: live", root.findViewById<TextView>(titleId).text.toString())
                assertEquals("Artist: collective", root.findViewById<TextView>(artistId).text.toString())
            }
        }
    }

    // Exercise the same renderer used by both playlist widget updates and restoration.
    @Test fun `playlist widget cleans current track and quick pick without changing their targets`() = runBlocking {
        val intent = PendingIntent.getActivity(context, 12, Intent("widget-test"), PendingIntent.FLAG_IMMUTABLE)
        val pick = PlaylistWidgetManager.QuickPick("modified:e:Pick: live", null, intent)
        mockkObject(PlaylistWidgetManager)
        coEvery { PlaylistWidgetManager.getQuickPicks(any(), any()) } returns listOf(pick)

        val views = PlaylistWidgetManager.createRemoteViews(
            context, options(300), "modified:Title: live", "modified:Artist", null,
            false, true, 120_000, 10_000, DefaultDarkColorPalette, null,
        )
        val root = views.apply(context, FrameLayout(context))
        assertEquals("Title: live", root.findViewById<TextView>(R.id.widget_playlist_song_title).text.toString())
        assertEquals("Artist", root.findViewById<TextView>(R.id.widget_playlist_artist_name).text.toString())
        assertEquals("\uD83C\uDD74 Pick: live", root.findViewById<TextView>(R.id.widget_playlist_card_1_title).text.toString())
        assertEquals("modified:e:Pick: live", pick.title)
        assertEquals(intent, pick.targetIntent)
    }
}
