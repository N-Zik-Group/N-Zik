package app.n_zik.android.components.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop

/** Page bodies register by entry id; the panel and header belong to the navigation root. */
internal class SideRailState {
    internal class Registration(
        val owner: Any,
        val body: @Composable (LayerBackdrop) -> Unit,
    )

    private val pages = mutableStateMapOf<String, Registration>()

    fun register(pageKey: String, registration: Registration) {
        if (pages[pageKey] !== registration) pages[pageKey] = registration
    }

    fun unregister(pageKey: String, owner: Any) {
        if (pages[pageKey]?.owner === owner) pages.remove(pageKey)
    }

    fun registration(pageKey: String?): Registration? = pages[pageKey]
}

internal val LocalSideRail = staticCompositionLocalOf<SideRailState?> { null }

/** Keeps page-local owners available to a body rendered outside the animated destination. */
@Composable
internal fun RegisterSideRailBody(
    pageKey: String?,
    enabled: Boolean,
    body: @Composable (LayerBackdrop) -> Unit,
) {
    val state = LocalSideRail.current
    val owner = remember { Any() }
    val context = rememberUpdatedState(currentCompositionLocalContext)
    val latestBody = rememberUpdatedState(body)
    val registration = remember(owner) {
        SideRailState.Registration(owner) { backdrop ->
            CompositionLocalProvider(context.value) { latestBody.value(backdrop) }
        }
    }
    SideEffect {
        if (enabled && pageKey != null) state?.register(pageKey, registration)
    }
    DisposableEffect(state, pageKey, owner, enabled) {
        onDispose { if (pageKey != null) state?.unregister(pageKey, owner) }
    }
}

/** Resolve after destination composition so a returning entry has tabs on its first frame. */
@Composable
internal fun SideRailBodySubcompose(
    pageKey: () -> String?,
    expectsTabs: () -> Boolean,
    registration: (String?) -> SideRailState.Registration?,
    surfaceBackdrop: LayerBackdrop,
) {
    val committed = remember { CommittedNavigationPresentation<SideRailState.Registration>() }
    SubcomposeLayout { constraints ->
        val presentation = committed.resolve(pageKey(), expectsTabs(), registration)
        val body = subcompose(Unit) {
            Column(modifier = androidx.compose.ui.Modifier.navigationPresentationInput(presentation.interactive),
                horizontalAlignment = Alignment.CenterHorizontally) {
                presentation.registration?.body?.invoke(surfaceBackdrop)
            }
        }.single().measure(constraints)
        layout(body.width, body.height) { body.placeRelative(0, 0) }
    }
}
