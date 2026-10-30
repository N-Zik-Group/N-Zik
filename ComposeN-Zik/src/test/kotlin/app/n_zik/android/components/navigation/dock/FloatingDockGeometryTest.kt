package app.n_zik.android.components.navigation.dock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FloatingDockGeometryTest {
    @Test
    fun `integer geometry matches actual Square positions including rounding through the fold`() {
        // SquareApp: 360dp screen minus two 16dp margins; five tabs, third selected.
        assertEquals(
            FoldGeometry(131, 70, 328, 61, 262, 134, 0, 0),
            referenceRow(fold = 0f, playerHeight = 56),
        )
        assertEquals(
            FoldGeometry(88, 33, 192, 55, 269, 68, 35, 1),
            referenceRow(fold = .5f, playerHeight = 52),
        )
        assertEquals(
            FoldGeometry(48, 0, 48, 48, 280, 0, 62, 0),
            referenceRow(fold = 1f, playerHeight = 48),
        )
    }

    @Test
    fun `shared dock dimensions preserve original expanded mini and Square compact endpoints`() {
        assertEquals(61f, FloatingDockDimensions.expandedHeight)
        assertEquals(72f, FloatingDockDimensions.expandedMiniHeight)
        assertEquals(48f, FloatingDockDimensions.compactHeight)
        assertEquals(96f, FloatingDockDimensions.compactCapsuleWidth)
        assertEquals(14f, FloatingDockDimensions.componentGap)
        assertEquals(16f, FloatingDockDimensions.outerMargin)
        val expanded = floatingDockGeometry(360f, FloatingDockDimensions.expandedHeight,
            FloatingDockDimensions.expandedMiniHeight, true, 0f)
        assertEquals(72f, expanded.miniHeight)
        assertEquals(147f, expanded.combinedHeight)
        val compact = floatingDockGeometry(360f, FloatingDockDimensions.expandedHeight,
            FloatingDockDimensions.expandedMiniHeight, true, 1f)
        assertEquals(48f, compact.miniHeight)
        assertEquals(48f, compact.combinedHeight)
    }

    @Test
    fun `standalone mini shrinks symmetrically to the dock compact width and clamps narrow screens`() {
        assertEquals(0f, floatingMiniStandaloneInset(360f, 0f))
        assertEquals(23.5f, floatingMiniStandaloneInset(360f, .5f))
        val foldedInset = floatingMiniStandaloneInset(360f, 1f)
        assertEquals(47f, foldedInset)
        assertEquals(266f, 360f - 2f * foldedInset)
        val alongsideHome = floatingDockGeometry(360f, 61f, 72f, true, 1f, hasSearch = false)
        assertEquals(alongsideHome.miniSideInset + alongsideHome.miniEndInset, 2f * foldedInset)
        assertEquals(40f, floatingMiniStandaloneInset(80f, 1f))
        assertEquals(0f, floatingMiniStandaloneInset(Float.NaN, 1f))
        assertEquals(0f, floatingMiniStandaloneInset(360f, Float.NaN))
    }

    @Test
    fun `landscape standalone mini interpolates from original width to 420dp while portrait remains unchanged`() {
        assertEquals(420f, FloatingDockDimensions.landscapeStandaloneMiniMaxWidth)
        listOf(0f to 900f, .5f to 660f, 1f to 420f).forEach { (fold, expectedWidth) ->
            val side = floatingMiniStandaloneInset(900f, fold, isLandscape = true)
            assertEquals(expectedWidth, 900f - 2f * side)
        }
        assertEquals(47f, floatingMiniStandaloneInset(900f, 1f, isLandscape = false))
        assertEquals(47f, floatingMiniStandaloneInset(360f, 1f, isLandscape = true))
        assertEquals(40f, floatingMiniStandaloneInset(80f, 1f, isLandscape = true))
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f).forEach { invalid ->
            assertEquals(0f, floatingMiniStandaloneInset(invalid, 1f, isLandscape = true))
        }
        assertEquals(0f, floatingMiniStandaloneInset(900f, Float.NaN, isLandscape = true))
    }

    @Test
    fun `mini compression is allowed independently of navigation and blocked by full player or overlays`() {
        assertTrue(miniPlayerFoldEligible(playerExpanded = false, queueVisible = false, menuVisible = false))
        assertFalse(miniPlayerFoldEligible(playerExpanded = true, queueVisible = false, menuVisible = false))
        assertFalse(miniPlayerFoldEligible(playerExpanded = false, queueVisible = true, menuVisible = false))
        assertFalse(miniPlayerFoldEligible(playerExpanded = false, queueVisible = false, menuVisible = true))
    }

    @Test
    fun `classic mini keeps its centered width while height compresses from 72 to 48`() {
        listOf(Triple(0f, 72f, 360f), Triple(.5f, 60f, 313f), Triple(1f, 48f, 266f)).forEach { (fold, height, width) ->
            val geometry = floatingDockGeometry(360f, 61f, 72f, true, fold, hasSearch = false)
            assertEquals(height, geometry.miniHeight)
            val side = floatingMiniStandaloneInset(360f, fold)
            assertEquals(width, 360f - 2f * side)
        }
    }

    @Test
    fun `classic row stays full width and height while the mini docks beside Home above system padding`() {
        listOf(54f to 14f, 64f to 18f).forEach { (rowHeight, gap) ->
            val expanded = classicDockGeometry(360f, rowHeight, 72f, true, 0f, gap)
            assertEquals(72f, expanded.miniHeight)
            assertEquals(rowHeight + gap, expanded.miniBottomOffset)
            val halfway = classicDockGeometry(360f, rowHeight, 72f, true, .5f, gap)
            assertEquals(60f, halfway.miniHeight)
            assertEquals(39f, halfway.miniSideInset)
            assertEquals(8f, halfway.miniEndInset)
            assertEquals(if (rowHeight == 54f) 32.5f else 42f, halfway.miniBottomOffset)
            val folded = classicDockGeometry(360f, rowHeight, 72f, true, 1f, gap)
            assertEquals(48f, folded.miniHeight)
            assertEquals(78f, folded.miniSideInset)
            assertEquals(16f, folded.miniEndInset)
            assertEquals((rowHeight - 48f) / 2f, folded.miniBottomOffset)
            assertEquals(rowHeight, folded.combinedHeight)
            listOf(expanded, halfway, folded).forEach { geometry ->
                assertEquals(rowHeight, geometry.navHeight)
                assertEquals(360f, geometry.capsuleWidth)
            }
        }
    }

    @Test
    fun `classic row retains its bounds without a bottom mini and clamps narrow geometry`() {
        val noMini = classicDockGeometry(360f, 54f, 72f, false, 1f)
        assertEquals(54f, noMini.navHeight)
        assertEquals(54f, noMini.combinedHeight)
        assertEquals(360f, noMini.capsuleWidth)
        assertEquals(0f, noMini.miniBottomOffset)
        assertEquals(0f, noMini.miniSideInset)
        val narrow = classicDockGeometry(80f, 54f, 72f, true, 1f)
        assertTrue(80f - narrow.miniSideInset - narrow.miniEndInset >= 0f)
    }

    @Test
    fun `dock without Search reserves only Home and leaves no empty trailing control slot`() {
        val withMini = floatingDockGeometry(360f, 61f, 72f, true, 1f, hasSearch = false)
        assertEquals(78f, withMini.miniSideInset)
        assertEquals(16f, withMini.miniEndInset)
        assertEquals(48f, withMini.capsuleWidth)
        val withoutMini = floatingDockGeometry(360f, 61f, 72f, false, 1f, hasSearch = false)
        assertEquals(360f, withoutMini.capsuleWidth)
        assertEquals(61f, withoutMini.navHeight)
        assertEquals(0f, withoutMini.miniEndInset)
    }

    @Test
    fun `bottom mini matches Square expanded and compact coordinates`() {
        val expanded = geometry(bottomMini = true, fold = 0f)
        val compact = geometry(bottomMini = true, fold = 1f)
        assertEquals(0f, expanded.miniSideInset)
        assertEquals(75f, expanded.miniBottomOffset)
        assertEquals(61f, expanded.navHeight)
        assertEquals(56f, expanded.miniHeight)
        assertEquals(131f, expanded.combinedHeight)
        assertEquals(0f, compact.miniBottomOffset)
        assertEquals(48f, compact.miniHeight)
        assertEquals(compact.navHeight, compact.miniHeight)
        assertEquals(78f, compact.miniSideInset)
        assertEquals(78f, compact.miniEndInset)
        assertEquals(compact.navHeight, compact.combinedHeight)
        assertTrue(compact.combinedHeight < expanded.combinedHeight)
    }

    @Test
    fun `top mini and absent media leave navigation expanded while mini geometry can still compress`() {
        val compact = geometry(bottomMini = false, fold = 1f)
        assertEquals(360f, compact.capsuleWidth)
        assertEquals(61f, compact.navHeight)
        assertEquals(48f, compact.miniHeight)
        assertEquals(0f, compact.miniSideInset)
        assertEquals(0f, compact.miniBottomOffset)
        assertEquals(compact.navHeight, compact.combinedHeight)
        val left = (360f - compact.capsuleWidth) / 2f
        assertEquals(left, 360f - left - compact.capsuleWidth)
    }

    @Test
    fun `reversing progress has identical geometry`() {
        listOf(0f, .2f, .5f, .8f, 1f).forEach { fold ->
            val forward = geometry(bottomMini = true, fold = fold)
            val backward = geometry(bottomMini = true, fold = 1f - (1f - fold))
            assertEquals(forward.miniHeight, backward.miniHeight, .0001f)
            assertEquals(forward.miniBottomOffset, backward.miniBottomOffset, .0001f)
            assertEquals(forward.miniSideInset, backward.miniSideInset, .0001f)
            assertEquals(forward.miniEndInset, backward.miniEndInset, .0001f)
            assertEquals(forward.combinedHeight, backward.combinedHeight, .0001f)
        }
    }

    @Test
    fun `native and Glass navigation heights use the same folded bounds`() {
        val native = floatingDockGeometry(360f, 60f, 56f, true, 1f)
        val glass = floatingDockGeometry(360f, 64f, 56f, true, 1f)
        assertEquals(native, glass)
    }

    @Test
    fun `narrow widths and cutout-reduced width clamp insets rather than creating negative mini width`() {
        listOf(0f, 96f, 188f, 240f, 360f, 800f).forEach { availableWidth ->
            listOf(0f, .5f, 1f).forEach { fold ->
                val result = floatingDockGeometry(availableWidth, 64f, 56f, true, fold)
                assertTrue(result.miniSideInset >= 0f)
                assertTrue(result.miniEndInset >= 0f)
                assertTrue(availableWidth - result.miniSideInset - result.miniEndInset >= 0f)
                assertTrue(result.capsuleWidth in 0f..availableWidth)
                assertTrue(result.miniSideInset <= minOf(78f, availableWidth / 2f))
                assertTrue(result.miniEndInset <= minOf(78f, availableWidth / 2f))
            }
        }
    }

    @Test
    fun `invalid dimensions and progress produce finite nonnegative geometry`() {
        listOf(Float.NaN, Float.NEGATIVE_INFINITY, -100f, 0f).forEach { invalid ->
            val result = floatingDockGeometry(invalid, invalid, invalid, true, invalid)
            listOf(result.miniHeight, result.miniSideInset, result.miniEndInset, result.miniBottomOffset,
                result.navHeight, result.capsuleWidth, result.combinedHeight).forEach {
                assertTrue(it.isFinite() && it >= 0f)
            }
        }
        assertEquals(geometry(true, 0f), geometry(true, -1f))
        assertEquals(geometry(true, 1f), geometry(true, 2f))
    }

    @Test
    fun `Square fold narrows and descends simultaneously at quarter half and three quarter progress`() {
        // Reference samples: Square FoldGeometry, 61dp row, 14dp gap, 56dp expanded player.
        // Insets include SquareApp's 16dp outer margin because this API receives the screen width.
        val samples = listOf(
            floatArrayOf(.25f, 54f, 57.75f, 21.9375f, 54.28125f, 108.75f),
            floatArrayOf(.5f, 52f, 54.5f, 42.25f, 34.875f, 87.5f),
            floatArrayOf(.75f, 50f, 51.25f, 60.9375f, 16.78125f, 67.25f),
        )
        samples.forEach { sample ->
            val fold = sample[0]
            val result = geometry(bottomMini = true, fold = fold)
            assertEquals(sample[1], result.miniHeight, .0001f, "mini height at $fold")
            assertEquals(sample[2], result.navHeight, .0001f, "nav height at $fold")
            assertEquals(sample[3], result.miniSideInset, .0001f, "side inset at $fold")
            assertEquals(sample[4], result.miniBottomOffset, .0001f, "bottom offset at $fold")
            assertEquals(sample[5], result.combinedHeight, .0001f, "reserved height at $fold")
        }
    }

    @Test
    fun `mini keeps Square margins and asymmetric midpoint placement after existing content padding`() {
        // The sheet insets add to MiniPlayer's existing 16dp padding, which vanishes when folded.
        val expanded = geometry(bottomMini = true, fold = 0f)
        assertEquals(16f, expanded.miniSideInset + 16f)
        assertEquals(344f, 360f - expanded.miniEndInset - 16f)
        val halfway = geometry(bottomMini = true, fold = .5f)
        assertEquals(50.25f, halfway.miniSideInset + 8f)
        assertEquals(316.25f, 360f - halfway.miniEndInset - 8f)
        val compact = geometry(bottomMini = true, fold = 1f)
        assertEquals(78f, compact.miniSideInset)
        assertEquals(282f, 360f - compact.miniEndInset)
    }

    private fun geometry(bottomMini: Boolean, fold: Float) =
        floatingDockGeometry(360f, 61f, 56f, bottomMini, fold)

    private fun referenceRow(fold: Float, playerHeight: Int) = foldGeometry(
        width = 328, expandedRowHeight = 61, spacing = 14, playerHeight = playerHeight,
        hasPlayer = true, fold = fold, search = 0f, tabsCount = 5, selectedIndex = 2,
        inset = 4f, collapsedRowHeight = 48,
    )
}
