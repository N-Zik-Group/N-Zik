package app.n_zik.android.components.dialog.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.Dialog
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.it.fast4x.rimusic.utils.quickPicksSectionOrderKey
import app.it.fast4x.rimusic.utils.showTipsKey
import app.it.fast4x.rimusic.utils.showChartsKey
import app.it.fast4x.rimusic.utils.showRelatedAlbumsKey
import app.it.fast4x.rimusic.utils.showSimilarArtistsKey
import app.it.fast4x.rimusic.utils.showNewAlbumsArtistsKey
import app.it.fast4x.rimusic.utils.showNewAlbumsKey
import app.it.fast4x.rimusic.utils.showPlaylistMightLikeKey
import app.it.fast4x.rimusic.utils.showMoodsAndGenresKey
import app.it.fast4x.rimusic.utils.showMyTopPlaylistKey
import app.it.fast4x.rimusic.utils.showFreshFindsOldFavoritesKey
import app.it.fast4x.rimusic.utils.showMixedForYouKey
import app.it.fast4x.rimusic.utils.showForgottenFavoritesKey
import app.it.fast4x.rimusic.utils.showYourDailyDiscoverKey
import app.it.fast4x.rimusic.utils.showFreshNewMusicKey
import app.it.fast4x.rimusic.utils.showNewReleasesKey
import app.it.fast4x.rimusic.utils.showAlbumsForYouKey
import app.it.fast4x.rimusic.utils.showTodaysBiggestHitsKey
import app.it.fast4x.rimusic.utils.showAllHitsKey
import app.it.fast4x.rimusic.utils.showFeaturedPlaylistsKey
import app.it.fast4x.rimusic.utils.showTrendingCommunityPlaylistsKey
import app.it.fast4x.rimusic.utils.showFromTheCommunityKey
import app.it.fast4x.rimusic.utils.showTrendingSongsForYouKey
import app.it.fast4x.rimusic.utils.showTopMusicVideosKey
import app.it.fast4x.rimusic.utils.showCoverAndRemixesKey
import app.it.fast4x.rimusic.utils.showTrendingInShortsKey
import app.it.fast4x.rimusic.utils.showMusicVideosForYouKey
import app.it.fast4x.rimusic.utils.showLivePerformancesKey
import app.it.fast4x.rimusic.utils.showMoodsKey
import app.it.fast4x.rimusic.utils.showGenericYtmSectionsKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.kreate.android.me.knighthat.utils.Toaster
import org.json.JSONArray
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.SharedPreferences

private val defaultSectionOrder = listOf(
    "tips",
    "fresh_finds_old_favorites",
    "mixed_for_you",
    "forgotten_favorites",
    "your_daily_discover",
    "fresh_new_music",
    "new_releases",
    "new_albums_artists",
    "new_albums",
    "albums_for_you",
    "related_albums",
    "my_top",
    "similar_artists",
    "todays_biggest_hits",
    "all_hits",
    "playlists_might_like",
    "featured_playlists",
    "trending_community_playlists",
    "from_the_community",
    "trending_songs_for_you",
    "top_music_videos",
    "cover_and_remixes",
    "trending_in_shorts",
    "music_videos_for_you",
    "live_performances",
    "moods",
    "moods_genres",
    "generic_ytm_sections",
    "charts"
)

private data class QuickPicksSectionDef(
    val id: String,
    val preferenceKey: String,
    val iconRes: Int,
    val labelRes: Int,
    val defaultValue: Boolean
)

private fun buildSectionDefs(): Map<String, QuickPicksSectionDef> = mapOf(
    "tips" to QuickPicksSectionDef("tips", showTipsKey, R.drawable.person, R.string.tips, true),
    "charts" to QuickPicksSectionDef("charts", showChartsKey, R.drawable.trending, R.string.charts, true),
    "related_albums" to QuickPicksSectionDef("related_albums", showRelatedAlbumsKey, R.drawable.album, R.string.related_albums, true),
    "similar_artists" to QuickPicksSectionDef("similar_artists", showSimilarArtistsKey, R.drawable.people, R.string.similar_artists, true),
    "new_albums_artists" to QuickPicksSectionDef("new_albums_artists", showNewAlbumsArtistsKey, R.drawable.alternative_version, R.string.new_albums_of_your_artists, true),
    "new_albums" to QuickPicksSectionDef("new_albums", showNewAlbumsKey, R.drawable.album, R.string.new_albums, true),
    "playlists_might_like" to QuickPicksSectionDef("playlists_might_like", showPlaylistMightLikeKey, R.drawable.playlist, R.string.playlists_you_might_like, true),
    "moods_genres" to QuickPicksSectionDef("moods_genres", showMoodsAndGenresKey, R.drawable.moods, R.string.moods_and_genres, true),
    "my_top" to QuickPicksSectionDef("my_top", showMyTopPlaylistKey, R.drawable.person, R.string.my_top, true),
    "fresh_finds_old_favorites" to QuickPicksSectionDef("fresh_finds_old_favorites", showFreshFindsOldFavoritesKey, R.drawable.trending, R.string.fresh_finds_old_favorites, true),
    "mixed_for_you" to QuickPicksSectionDef("mixed_for_you", showMixedForYouKey, R.drawable.playlist, R.string.mixed_for_you, true),
    "forgotten_favorites" to QuickPicksSectionDef("forgotten_favorites", showForgottenFavoritesKey, R.drawable.person, R.string.forgotten_favorites, true),
    "your_daily_discover" to QuickPicksSectionDef("your_daily_discover", showYourDailyDiscoverKey, R.drawable.discover, R.string.your_daily_discover, true),
    "fresh_new_music" to QuickPicksSectionDef("fresh_new_music", showFreshNewMusicKey, R.drawable.trending, R.string.fresh_new_music, true),
    "new_releases" to QuickPicksSectionDef("new_releases", showNewReleasesKey, R.drawable.album, R.string.new_releases, true),
    "albums_for_you" to QuickPicksSectionDef("albums_for_you", showAlbumsForYouKey, R.drawable.album, R.string.albums_for_you, true),
    "todays_biggest_hits" to QuickPicksSectionDef("todays_biggest_hits", showTodaysBiggestHitsKey, R.drawable.trending, R.string.todays_biggest_hits, true),
    "all_hits" to QuickPicksSectionDef("all_hits", showAllHitsKey, R.drawable.trending, R.string.all_hits, true),
    "featured_playlists" to QuickPicksSectionDef("featured_playlists", showFeaturedPlaylistsKey, R.drawable.featured_playlist, R.string.featured_playlists_for_you, true),
    "trending_community_playlists" to QuickPicksSectionDef("trending_community_playlists", showTrendingCommunityPlaylistsKey, R.drawable.playlist, R.string.trending_community_playlists, true),
    "from_the_community" to QuickPicksSectionDef("from_the_community", showFromTheCommunityKey, R.drawable.people, R.string.from_the_community, true),
    "trending_songs_for_you" to QuickPicksSectionDef("trending_songs_for_you", showTrendingSongsForYouKey, R.drawable.trending, R.string.trending_songs_for_you, true),
    "top_music_videos" to QuickPicksSectionDef("top_music_videos", showTopMusicVideosKey, R.drawable.video, R.string.top_music_videos, true),
    "cover_and_remixes" to QuickPicksSectionDef("cover_and_remixes", showCoverAndRemixesKey, R.drawable.musical_notes, R.string.cover_and_remixes, true),
    "trending_in_shorts" to QuickPicksSectionDef("trending_in_shorts", showTrendingInShortsKey, R.drawable.trending, R.string.trending_in_shorts, true),
    "music_videos_for_you" to QuickPicksSectionDef("music_videos_for_you", showMusicVideosForYouKey, R.drawable.video, R.string.music_videos_for_you, true),
    "live_performances" to QuickPicksSectionDef("live_performances", showLivePerformancesKey, R.drawable.person, R.string.live_performances, true),
    "moods" to QuickPicksSectionDef("moods", showMoodsKey, R.drawable.moods, R.string.moods, true),
    "generic_ytm_sections" to QuickPicksSectionDef("generic_ytm_sections", showGenericYtmSectionsKey, R.drawable.trending, R.string.generic_ytm_sections, true)
)

object QuickPicksContentSettingsDialog : Dialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.quick_picks_content)

    override var isActive: Boolean by mutableStateOf(false)

    private fun parseOrder(serialized: String): List<String> {
        if (serialized.isBlank()) return defaultSectionOrder
        return try {
            val arr = JSONArray(serialized)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            val validIds = defaultSectionOrder.filter { it in buildSectionDefs().keys }
            val result = list.filter { it in validIds }.toMutableList()
            for (id in validIds) {
                if (id !in result) result.add(id)
            }
            result.remove("tips")
            result.add(0, "tips")
            result
        } catch (_: Exception) {
            defaultSectionOrder
        }
    }

    private fun serializeOrder(order: List<String>): String {
        val arr = JSONArray()
        order.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun loadPrefs(prefs: SharedPreferences): Pair<MutableList<String>, MutableList<Boolean>> {
        val orderSerialized = prefs.getString(quickPicksSectionOrderKey, "") ?: ""
        val order = parseOrder(orderSerialized).toMutableList()
        val toggles = order.map { id ->
            val def = buildSectionDefs()[id]
            if (def != null) prefs.getBoolean(def.preferenceKey, def.defaultValue) else true
        }.toMutableList()
        return order to toggles
    }

    private fun savePrefs(prefs: SharedPreferences, order: List<String>, toggles: Map<String, Boolean>) {
        val editor = prefs.edit()
        editor.putString(quickPicksSectionOrderKey, serializeOrder(order))
        buildSectionDefs().forEach { (id, def) ->
            editor.putBoolean(def.preferenceKey, toggles[id] ?: def.defaultValue)
        }
        editor.apply()
    }

    @Composable
    override fun DialogBody() {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences("preferences", Context.MODE_PRIVATE) }
        val sectionDefs = remember { buildSectionDefs() }

        val initial = remember { loadPrefs(prefs) }

        var workingOrder by remember { mutableStateOf(initial.first) }
        var workingToggles by remember { mutableStateOf(initial.second) }

        val lazyListState = rememberLazyListState()

        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val order = workingOrder.toMutableList()
            val toggles = workingToggles.toMutableList()
            val fromIndex = order.indexOf(from.key)
            val toIndex = order.indexOf(to.key)
            if (fromIndex != -1 && toIndex != -1) {
                val item = order.removeAt(fromIndex)
                order.add(toIndex, item)
                val checkedItem = toggles.removeAt(fromIndex)
                toggles.add(toIndex, checkedItem)
                workingOrder = order
                workingToggles = toggles
            }
        }

        val sectionItems = workingOrder.mapIndexed { index, sectionId ->
            val def = sectionDefs[sectionId] ?: return@mapIndexed null
            ToggleItem(
                id = def.id,
                iconRes = def.iconRes,
                label = stringResource(def.labelRes),
                preferenceKey = def.preferenceKey,
                defaultValue = def.defaultValue
            )
        }.filterNotNull()

        ToggleListDialog(
            items = sectionItems,
            lazyListState = lazyListState,
            reorderableState = reorderableState,
            pinnedItemCount = 1,
            enforceMinOneChecked = true,
            checkedStatesOverride = workingToggles.toList(),
            onCheckedChange = { index, newValue ->
                val newToggles = workingToggles.toMutableList()
                newToggles[index] = newValue
                workingToggles = newToggles
            },
            onReset = {
                workingOrder = defaultSectionOrder.toMutableList()
                workingToggles = defaultSectionOrder.map { id ->
                    sectionDefs[id]?.defaultValue ?: true
                }.toMutableList()
            },
            onCancel = {
                hideDialog()
            },
            onConfirm = {
                val toggleMap = mutableMapOf<String, Boolean>()
                workingOrder.forEachIndexed { index, id ->
                    toggleMap[id] = workingToggles[index]
                }
                savePrefs(prefs, workingOrder, toggleMap)
                Toaster.s(R.string.toast_preference_saved)
                hideDialog()
            }
        )
    }
    fun reset(context: Context) {
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val defaultToggles = buildSectionDefs().mapValues { it.value.defaultValue }
        savePrefs(prefs, defaultSectionOrder, defaultToggles)
    }
}
