package app.n_zik.android.components.navigation.dock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.PlayerPosition

internal fun dockHasAttachedMini(hasMedia: Boolean, navigation: NavigationBarPosition, player: PlayerPosition): Boolean =
    hasMedia && when (navigation) {
        NavigationBarPosition.Top -> player == PlayerPosition.Top
        NavigationBarPosition.Bottom, NavigationBarPosition.BottomFloating -> player == PlayerPosition.Bottom
        else -> false
    }

/** Dimensions match Square's compact 36dp artwork plus two 6dp vertical margins. */
object FloatingDockDimensions {
    const val compactHeight = 48f
    const val actionWidth = 48f
    const val expandedHeight = 61f
    const val expandedMiniHeight = 72f
    const val componentGap = 14f
    const val outerMargin = 16f
    const val compactCapsuleWidth = 2f * compactHeight
    const val landscapeStandaloneMiniMaxWidth = 420f
}

/** Current-page registrations survive disposal of an outgoing animated page. */
class FloatingDockState {
    internal data class Registration(
        val owner: Any,
        val present: Boolean,
        val body: (@Composable () -> Unit)? = null,
    )
    private val pages = mutableStateMapOf<String, Registration>()
    private val target = mutableFloatStateOf(0f)
    var foldTarget: Float
        get() = target.floatValue
        set(value) { target.floatValue = value.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f }

    fun register(pageKey: String, owner: Any, buttonCount: Int, visible: Boolean,
        body: (@Composable () -> Unit)? = null,
    ) {
        val next = Registration(owner, buttonCount >= 2 && visible, body)
        if (pages[pageKey] != next) pages[pageKey] = next
    }

    internal fun registration(pageKey: String?): Registration? = pages[pageKey]

    fun unregister(pageKey: String, owner: Any) {
        if (pages[pageKey]?.owner === owner) pages.remove(pageKey)
    }

    fun hasDock(pageKey: String?): Boolean = pages[pageKey]?.present == true

    fun registeredPresence(pageKey: String?): Boolean? = pages[pageKey]?.present

    // Travel is deliberately not snapshot state: only threshold crossings animate the dock.
    private var travel = 0f
    fun onScroll(deltaY: Float, rangePx: Float) {
        if (!deltaY.isFinite() || !rangePx.isFinite() || rangePx <= 0f || deltaY == 0f) return
        if (travel * deltaY < 0f) travel = 0f
        travel += deltaY
        if (kotlin.math.abs(travel) >= rangePx) {
            foldTarget = if (travel < 0f) 1f else 0f
            travel = 0f
        }
    }

    fun expand() { travel = 0f; foldTarget = 0f }
}

/** Only BottomFloating participates, irrespective of Glass material selection. */
fun floatingDockEligible(bottomFloating: Boolean, buttonCount: Int, landscapeHidden: Boolean): Boolean =
    bottomFloating && buttonCount >= 2 && !landscapeHidden

/** Mini compression is independent of page and navigation placement. */
internal fun miniPlayerFoldEligible(playerExpanded: Boolean, queueVisible: Boolean, menuVisible: Boolean): Boolean =
    !playerExpanded && !queueVisible && !menuVisible

data class FloatingDockGeometry(
    val miniHeight: Float,
    val miniSideInset: Float,
    val miniBottomOffset: Float,
    val navHeight: Float,
    val capsuleWidth: Float,
    val combinedHeight: Float,
    val miniEndInset: Float = miniSideInset,
)

/** All values share the caller's units (normally dp); reversal retraces the same coordinates. */
fun floatingDockGeometry(
    width: Float,
    expandedNavHeight: Float,
    expandedMiniHeight: Float,
    hasBottomMini: Boolean,
    fold: Float,
    hasSearch: Boolean = true,
): FloatingDockGeometry {
    fun safe(value: Float) = value.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    fun mix(a: Float, b: Float, p: Float) = a + (b - a) * p
    val w = safe(width)
    val p = fold.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val expanded = safe(expandedNavHeight)
    val compact = FloatingDockDimensions.compactHeight
    val navProgress = if (hasBottomMini) p else 0f
    val nav = mix(expanded, compact, navProgress)
    val mini = mix(safe(expandedMiniHeight), FloatingDockDimensions.compactHeight, p)
    val spacing = FloatingDockDimensions.componentGap
    // The source measures player width against the final compact row, but positions it
    // against the current row. Preserve that slight asymmetry during the animation.
    val side = if (hasBottomMini) minOf((FloatingDockDimensions.outerMargin + nav + spacing) * p, w / 2f) else 0f
    val end = if (hasBottomMini) minOf((FloatingDockDimensions.outerMargin +
        if (hasSearch) 2f * compact + spacing - nav else 0f) * p, w / 2f) else 0f
    // Square FoldGeometry, translated from top coordinates to our existing sheet's bottom anchor.
    val bottom = if (hasBottomMini)
        nav + mix(mini + spacing, (mini - nav) / 2f, p) - mini else 0f
    return FloatingDockGeometry(mini, side, bottom, nav,
        mix(w, minOf(w, (if (hasSearch) 2f else 1f) * compact), navProgress),
        (nav + if (hasBottomMini) (mini + spacing) * (1f - p) else 0f), end)
}

/** Classic bars retain their full control row while the mini settles beside Home. */
internal fun classicDockGeometry(
    width: Float,
    navHeight: Float,
    expandedMiniHeight: Float,
    hasBottomMini: Boolean,
    fold: Float,
    expandedGap: Float = FloatingDockDimensions.componentGap,
): FloatingDockGeometry {
    fun safe(value: Float) = value.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val w = safe(width)
    val nav = safe(navHeight)
    val p = fold.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val mini = safe(expandedMiniHeight) * (1f - p) + FloatingDockDimensions.compactHeight * p
    val gap = safe(expandedGap)
    val side = if (hasBottomMini) minOf((FloatingDockDimensions.outerMargin +
        FloatingDockDimensions.compactHeight + FloatingDockDimensions.componentGap) * p, w / 2f) else 0f
    val end = if (hasBottomMini) minOf(FloatingDockDimensions.outerMargin * p, w / 2f) else 0f
    val bottom = if (hasBottomMini) (nav + gap - (gap + (mini + nav) / 2f) * p).coerceAtLeast(0f) else 0f
    return FloatingDockGeometry(mini, side, bottom, nav, w,
        if (hasBottomMini) maxOf(nav, mini + bottom) else nav, end)
}

/** One animation clock is read by navigation, collapsed sheet geometry and mini presentation. */
data class FloatingDockEnvironment(
    val state: FloatingDockState,
    val pageKey: String?,
    val enabled: Boolean,
    val progress: State<Float>,
    val hasBottomMini: Boolean,
    val pagePresence: Boolean? = null,
    val classic: Boolean = false,
    val attachedMini: Boolean? = null,
    val floating: Boolean = false,
) {
    val present: Boolean get() = enabled && (pagePresence ?: state.hasDock(pageKey))
    val hasAttachedMini: Boolean get() = attachedMini ?: hasBottomMini
    val navigationProgress: Float get() = if (floating || hasAttachedMini) progress.value else 0f
    val navigationFolded: Boolean get() = (floating || hasAttachedMini) && state.foldTarget > 0f
}

val LocalFloatingDock = staticCompositionLocalOf<FloatingDockEnvironment?> { null }

/** Pages without a dock keep their vertical anchor and fold into the same centered mini width. */
fun floatingMiniStandaloneInset(width: Float, fold: Float, isLandscape: Boolean = false): Float {
    val w = width.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val p = fold.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val baseline = FloatingDockDimensions.outerMargin + (FloatingDockDimensions.compactHeight +
        FloatingDockDimensions.componentGap) / 2f
    val targetInset = if (isLandscape) maxOf(baseline,
        (w - minOf(w, FloatingDockDimensions.landscapeStandaloneMiniMaxWidth)) / 2f) else baseline
    return minOf(targetInset * p, w / 2f)
}
