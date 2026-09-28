package app.n_zik.android.components.ui.screens.rewind.slides

import androidx.compose.ui.unit.sp
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the deck text sizing policy ([RewindTextScale]) — the calibration contract
 * of the overflow fix (spec problem-solution-2026-09-28-rewind):
 *
 * - I1 — at the reference (400dp, font scale 1.0) every size returns EXACTLY its base
 *   value, so a standard screen renders unchanged by construction;
 * - I2 — below the reference the factor is non-increasing in the width (and clamped to
 *   the tier's legibility floor); above the reference it is flat (no upscaling);
 * - I3 — at a fixed width the rendered size is non-decreasing in the font scale (the
 *   user's accessibility font choice stays effective, never cancelled);
 * - I4 — overflow guard: the effective size never exceeds `base * max(fontScale, 1) *
 *   (width / reference)`, so even a doubled font scale stays within the screen's width
 *   budget (font scales below 1.0 are clamped to the 1.0 behavior).
 *
 * Pure function under test — no Robolectric, no composition.
 */
class RewindTextScaleTest {

    /**
     * Every distinct base size (7sp-126sp) the deck actually uses — font sizes, line
     * heights and the animated-number bases — plus the 45sp TITLE/HERO tier boundary.
     */
    private val deckSizes = listOf(
        7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f,
        15f, 16f, 17f, 18f, 19f, 20f, 21f, 22f,
        23f, 24f, 25f, 26f, 28f, 29f, 30f, 31f,
        32f, 33f, 34f, 35f, 36f, 37f, 38f, 39f,
        40f, 41f, 42f, 43f, 44f, 45f, 48f, 49f,
        50f, 53f, 56f, 57f, 58f, 64f, 66f, 68f,
        69f, 70f, 76f, 78f, 80f, 84f, 92f, 94f,
        100f, 104f, 116f, 126f
    )

    /** Rendered size in dp: Compose multiplies `sp` by the font scale. */
    private fun renderedDp(scale: RewindTextScale, baseSp: Float, fontScale: Float): Float =
        scale.size(baseSp) * fontScale

    // ---- I1: calibration at the reference ------------------------------------

    @Test
    fun atReferenceEveryDeckSizeReturnsExactlyItsBase() {
        val scale = RewindTextScale(400, 1f)
        deckSizes.forEach { base ->
            assertEquals(
                "I1: ${base}sp at the reference (400dp, scale 1.0) must stay $base sp",
                base,
                scale.size(base),
                0.0f
            )
        }
    }

    @Test
    fun atReferenceSpOverloadMatches() {
        val scale = RewindTextScale(400, 1f)
        deckSizes.forEach { base ->
            assertEquals(base.sp, scale.size(base.sp))
        }
    }

    // ---- I2: width monotonicity + plateau ------------------------------------

    @Test
    fun belowReferenceSizesShrinkProportionally() {
        // 360dp wide (a compact phone) at normal font scale: 90 % of the design size
        val scale = RewindTextScale(360, 1f)
        assertEquals(45f, scale.size(50f), 0.01f)
        assertEquals(40.5f, scale.size(45f), 0.01f)
    }

    @Test
    fun factorIsNonIncreasingAsWidthDrops() {
        val fontScale = 1f
        var previous = RewindTextScale(400, fontScale).size(40f)
        for (width in listOf(390, 380, 370, 360, 350, 340, 330, 320)) {
            val current = RewindTextScale(width, fontScale).size(40f)
            assertTrue(
                "I2: at ${width}dp (${current}) must not exceed ${width + 10}dp (${previous})",
                current <= previous + 0.01f
            )
            previous = current
        }
    }

    @Test
    fun aboveReferenceSizesStayFlat() {
        val reference = RewindTextScale(400, 1f).size(42f)
        listOf(410, 480, 600, 720).forEach { width ->
            assertEquals(
                "I2: no upscaling above the reference — ${width}dp must keep the design size",
                reference,
                RewindTextScale(width, 1f).size(42f),
                0.0f
            )
        }
    }

    @Test
    fun theSmallestWidthStillKeepsTheTierLegibilityFloor() {
        // 320dp wide with a 2.0 font scale pushes the raw factor (320 / (400 * sqrt(2)))
        // to ~0.566 — below the LABEL floor, so the floor must win
        val scale = RewindTextScale(320, 2f)
        assertEquals(10f * RewindTextTier.LABEL.minFactor, scale.size(10f), 0.001f)
        // And never above the design size
        assertTrue(scale.size(10f) <= 10f)
    }

    // ---- I3: the user's font choice stays effective --------------------------

    @Test
    fun renderedSizeGrowsWithTheFontScaleAtFixedWidth() {
        val fontScales = listOf(1f, 1.1f, 1.3f, 1.5f, 2f, 3f)
        listOf(360, 400).forEach { width ->
            var previous = renderedDp(RewindTextScale(width, fontScales.first()), 34f, fontScales.first())
            fontScales.drop(1).forEach { fontScale ->
                val current = renderedDp(RewindTextScale(width, fontScale), 34f, fontScale)
                assertTrue(
                    "I3: at ${width}dp the rendered size at scale $fontScale (${current}dp) must " +
                        "not be smaller than at the previous scale (${previous}dp) — the user's " +
                        "font choice stays effective",
                    current >= previous - 0.01f
                )
                previous = current
            }
        }
    }

    @Test
    fun aDoubledFontScaleDoesNotDoubleTheRenderedSize() {
        // The absorb exponent: the rendered size grows with fontScale^0.5, not fontScale —
        // a doubled font scale on the reference screen gives +41 %, not +100 %
        val atOne = renderedDp(RewindTextScale(400, 1f), 50f, 1f)
        val atTwo = renderedDp(RewindTextScale(400, 2f), 50f, 2f)
        val expected = atOne * sqrt(2.0).toFloat()
        assertEquals(expected, atTwo, 0.5f)
    }

    // ---- I4: overflow guard ---------------------------------------------------

    @Test
    fun theEffectiveSizeNeverExceedsTheWidthBudget() {
        // I4: effectiveSp <= base * max(fontScale, 1) * (width / reference) — scales below
        // 1.0 are clamped to the 1.0 behavior, so the budget never tightens below the
        // design-size ratio.
        listOf(320, 340, 360, 380, 400).forEach { width ->
            listOf(0.8f, 1f, 1.3f, 1.5f, 2f, 3f).forEach { fontScale ->
                deckSizes.forEach { base ->
                    val budget = base * fontScale.coerceAtLeast(1f) * (width / 400f)
                    val effective = RewindTextScale(width, fontScale).size(base)
                    assertTrue(
                        "I4: ${base}sp at ${width}dp/scale $fontScale → ${effective}sp must stay " +
                            "within the width budget ${budget}sp",
                        effective <= budget + 0.01f
                    )
                }
            }
        }
    }

    // ---- tier inference & letterSpacing ---------------------------------------

    @Test
    fun tierBoundariesMatchTheDeckInventory() {
        assertEquals(RewindTextTier.CAPTION, RewindTextTier.of(7f))
        assertEquals(RewindTextTier.CAPTION, RewindTextTier.of(9.5f))
        assertEquals(RewindTextTier.LABEL, RewindTextTier.of(10f))
        assertEquals(RewindTextTier.LABEL, RewindTextTier.of(14f))
        assertEquals(RewindTextTier.BODY, RewindTextTier.of(15f))
        assertEquals(RewindTextTier.BODY, RewindTextTier.of(22f))
        assertEquals(RewindTextTier.STAT, RewindTextTier.of(24f))
        assertEquals(RewindTextTier.STAT, RewindTextTier.of(31f))
        assertEquals(RewindTextTier.TITLE, RewindTextTier.of(32f))
        assertEquals(RewindTextTier.TITLE, RewindTextTier.of(45f))
        assertEquals(RewindTextTier.HERO, RewindTextTier.of(48f))
        assertEquals(RewindTextTier.HERO, RewindTextTier.of(92f))
    }

    @Test
    fun letterSpacingScalesLikeSizes() {
        val scale = RewindTextScale(360, 1f)
        // A 1.0sp letterSpacing on a 360dp screen shrinks with the width factor (0.9)
        assertEquals(0.9f, scale.letterSpacing(1.0f.sp).value, 0.001f)
    }
}
