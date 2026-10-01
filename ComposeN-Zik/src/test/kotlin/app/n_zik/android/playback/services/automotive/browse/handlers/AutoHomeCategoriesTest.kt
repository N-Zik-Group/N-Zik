package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.preferences
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [homeCategoryOrder]: the automotive home categories must be
 * displayed in the same order as the in-app settings — the phone's
 * Home*SettingsDialog stores a JSON array of category ids under its order
 * key, and the automotive handlers reuse it.
 *
 * `sdk = [33]` like the other automotive Robolectric classes: on this JVM the
 * default-SDK sandbox fails at environment setup (`ApplicationSharedMemory`
 * creation reaches `jdk.internal.access.SharedSecrets`, which `java.base`
 * does not export to the unnamed module).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoHomeCategoriesTest {

    private lateinit var context: Context

    private val songsDefaultOrder = listOf("all", "favorites", "disliked", "cached", "downloaded", "top", "on_device")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `missing stored order falls back to the default order`() {
        assertEquals(songsDefaultOrder, homeCategoryOrder(context, "homeSongsOrder", songsDefaultOrder))
    }

    @Test
    fun `stored order is honored and missing ids are appended in default order`() {
        context.preferences.edit().putString("homeSongsOrder", """["top", "all", "disliked"]""").apply()
        assertEquals(listOf("top", "all", "disliked", "favorites", "cached", "downloaded", "on_device"), homeCategoryOrder(context, "homeSongsOrder", songsDefaultOrder))
    }

    @Test
    fun `unknown stored ids are dropped`() {
        context.preferences.edit().putString("homeSongsOrder", """["bogus", "top"]""").apply()
        assertEquals(listOf("top", "all", "favorites", "disliked", "cached", "downloaded", "on_device"), homeCategoryOrder(context, "homeSongsOrder", songsDefaultOrder))
    }

    @Test
    fun `corrupted stored value falls back to the default order`() {
        context.preferences.edit().putString("homeSongsOrder", "not json").apply()
        assertEquals(songsDefaultOrder, homeCategoryOrder(context, "homeSongsOrder", songsDefaultOrder))
    }
}
