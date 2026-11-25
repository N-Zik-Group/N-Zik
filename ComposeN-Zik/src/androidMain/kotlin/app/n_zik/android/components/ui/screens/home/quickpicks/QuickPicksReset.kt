package app.n_zik.android.components.ui.screens.home.quickpicks

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import app.n_zik.android.compose.persist.PersistMap

private const val cachePrefix = "home/quickpicks/"
private const val revisionKey = "${cachePrefix}reset-revision"

internal fun quickPicksResetRevision(cache: PersistMap): MutableIntState =
    cache.getOrPut(revisionKey) { mutableIntStateOf(0) } as MutableIntState

internal fun resetQuickPicks(cache: PersistMap) {
    val revision = quickPicksResetRevision(cache)
    cache.keys.removeAll { it.startsWith(cachePrefix) && it != revisionKey }
    revision.intValue++
}
