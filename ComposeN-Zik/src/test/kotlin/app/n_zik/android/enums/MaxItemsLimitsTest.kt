package app.n_zik.android.enums

import app.it.fast4x.rimusic.enums.MaxStatisticsItems
import app.it.fast4x.rimusic.enums.MaxTopPlaylistItems
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests the effective item limit resolution of [MaxStatisticsItems] and [MaxTopPlaylistItems].
 *
 * Both enums share the same values plus Custom/Unlimited; `toInt(customValue)` must resolve
 * to the effective SQL limit for the "Max number of items" settings.
 */
class MaxItemsLimitsTest {

    private val numericValues = intArrayOf(10, 20, 30, 40, 50, 70, 90, 100, 150, 200)

    @Test
    fun `numeric statistics values should map to their own number`() {
        val numeric = MaxStatisticsItems.values()
            .filter { it != MaxStatisticsItems.Custom && it != MaxStatisticsItems.Unlimited }
        assertEquals(numericValues.size, numeric.size)
        numeric.forEachIndexed { index, item ->
            assertEquals(numericValues[index], item.toInt(10))
        }
    }

    @Test
    fun `numeric top playlist values should map to their own number`() {
        val numeric = MaxTopPlaylistItems.values()
            .filter { it != MaxTopPlaylistItems.Custom && it != MaxTopPlaylistItems.Unlimited }
        assertEquals(numericValues.size, numeric.size)
        numeric.forEachIndexed { index, item ->
            assertEquals(numericValues[index], item.toInt(10))
        }
    }

    @Test
    fun `custom should use the stored custom value`() {
        assertEquals(25, MaxStatisticsItems.Custom.toInt(25))
        assertEquals(1, MaxStatisticsItems.Custom.toInt(1))
        assertEquals(999_999_999, MaxStatisticsItems.Custom.toInt(999_999_999))
        assertEquals(25, MaxTopPlaylistItems.Custom.toInt(25))
    }

    @Test
    fun `custom below one should be clamped to one`() {
        assertEquals(1, MaxStatisticsItems.Custom.toInt(0))
        assertEquals(1, MaxStatisticsItems.Custom.toInt(-42))
        assertEquals(1, MaxTopPlaylistItems.Custom.toInt(0))
        assertEquals(1, MaxTopPlaylistItems.Custom.toInt(-1))
    }

    @Test
    fun `unlimited should resolve to Int max value for both enums`() {
        assertEquals(Int.MAX_VALUE, MaxStatisticsItems.Unlimited.toInt(10))
        assertEquals(Int.MAX_VALUE, MaxTopPlaylistItems.Unlimited.toInt(10))
    }

    @Test
    fun `unlimited should ignore the custom value`() {
        assertEquals(Int.MAX_VALUE, MaxStatisticsItems.Unlimited.toInt(0))
        assertEquals(Int.MAX_VALUE, MaxTopPlaylistItems.Unlimited.toInt(999_999_999))
    }
}
