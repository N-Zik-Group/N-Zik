package app.n_zik.android.utils

import app.it.fast4x.rimusic.models.Info
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Contract for [alignInfosToDisplayOrder]: the profile-photo pager must follow
 * the artist order shown in the player (bug 2026-09-26: the pager order came
 * from the link query, which has no ORDER BY, and disagreed with the displayed
 * artist line).
 *
 * - tokens of the displayed line pick their info in order (whole token, ignore case);
 * - infos no token claimed keep their relative order at the end;
 * - nothing matches / short inputs → the original list is returned untouched;
 * - a `modified:` name on either side still matches (names are cleaned).
 */
class AlignInfosToDisplayOrderTest {

    private fun info(id: String, name: String?) = Info(id = id, name = name)

    @Test
    fun `infos are reordered to follow the displayed artist line`() {
        val infos = listOf(info("UC_Y", "Yunosuke"), info("UC_H", "Hatsune Miku"))
        val display = "Hatsune Miku, Yunosuke"

        val ordered = alignInfosToDisplayOrder(infos, display)

        assertEquals(listOf("UC_H", "UC_Y"), ordered.map { it.id })
    }

    @Test
    fun `ordering matches whole tokens ignoring case`() {
        val infos = listOf(info("UC_A", "Aran"), info("UC_O", "Aran One"), info("UC_B", "B"))
        val display = "B, aran one, ARAN"

        val ordered = alignInfosToDisplayOrder(infos, display)

        assertEquals(listOf("UC_B", "UC_O", "UC_A"), ordered.map { it.id })
    }

    @Test
    fun `unmatched infos keep their relative order at the end`() {
        val infos = listOf(info("UC_1", "First"), info("UC_2", "Second"), info("UC_3", "Third"))
        val display = "Third"

        val ordered = alignInfosToDisplayOrder(infos, display)

        assertEquals(listOf("UC_3", "UC_1", "UC_2"), ordered.map { it.id })
    }

    @Test
    fun `prefixed names on both sides still match`() {
        val infos = listOf(info("UC_Y", "Yunosuke"), info("UC_H", "modified:Hatsune Miku"))
        val display = "Hatsune Miku, Yunosuke"

        val ordered = alignInfosToDisplayOrder(infos, display)

        assertEquals(listOf("UC_H", "UC_Y"), ordered.map { it.id })
    }

    @Test
    fun `no token match returns the original order`() {
        val infos = listOf(info("UC_1", "First"), info("UC_2", "Second"))
        val display = "Unknown Artist"

        assertSame(infos, alignInfosToDisplayOrder(infos, display))
    }

    @Test
    fun `blank or missing display text returns the original order`() {
        val infos = listOf(info("UC_1", "First"), info("UC_2", "Second"))

        assertSame(infos, alignInfosToDisplayOrder(infos, null))
        assertSame(infos, alignInfosToDisplayOrder(infos, ""))
        assertSame(infos, alignInfosToDisplayOrder(infos, "   "))
    }

    @Test
    fun `single info or empty list is returned untouched`() {
        val single = listOf(info("UC_1", "First"))

        assertSame(single, alignInfosToDisplayOrder(single, "Whatever"))
        assertSame(emptyList<Info>(), alignInfosToDisplayOrder(emptyList(), "Whatever"))
    }

    @Test
    fun `duplicate names are consumed once per token`() {
        val infos = listOf(info("UC_1", "BlackY"), info("UC_2", "BlackY"), info("UC_3", "Other"))
        val display = "BlackY, BlackY, Other"

        val ordered = alignInfosToDisplayOrder(infos, display)

        assertEquals(listOf("UC_1", "UC_2", "UC_3"), ordered.map { it.id })
    }
}
