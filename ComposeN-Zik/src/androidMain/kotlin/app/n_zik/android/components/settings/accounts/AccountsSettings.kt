package app.n_zik.android.components.settings.accounts

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.enableYouTubeLoginKey
import app.it.fast4x.rimusic.utils.enableYouTubeSyncKey
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.useLoginForBrowseKey
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.n_zik.android.components.search.SearchHistorySection
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.search.filterSearchHistory
import app.n_zik.android.components.search.rememberSearchHistory
import app.n_zik.android.components.settings.LastFmSettingsCard
import app.n_zik.android.components.settings.ListenTogetherSettingsCard
import app.n_zik.android.components.settings.ProfileFaceCard
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsGroupSpacer
import app.n_zik.android.components.settings.common.rememberSettingsSearchFocus
import app.n_zik.android.components.settings.common.settingsSearchContent
import app.n_zik.android.components.settings.common.settingsSearchDescription
import app.n_zik.android.components.settings.common.settingsSearchTitle
import app.n_zik.android.components.settings.common.visibleCardTitleRes
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.ui.screens.profiles.ProfileAccountCard
import it.fast4x.innertube.utils.parseCookieString
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("BatteryLife")
@ExperimentalAnimationApi
@Composable
fun AccountsSettings(
    navController: NavController,
    focus: String = ""
) {
    val search = Search(initialVisible = true)
    val searchFocus = rememberSettingsSearchFocus(search.isFocused, search.inputValue.isBlank())

    val context = LocalContext.current

    val isHistoryPaused by rememberPreference(pauseSearchHistoryKey, false)
    val history = rememberSearchHistory(SearchHistoryStore.PAGE_SETTINGS_ACCOUNTS, isHistoryPaused)
    val historyMatches = filterSearchHistory(history.value, search.inputValue)
    val historyVisible = search.isFocused && historyMatches.isNotEmpty()

    fun recordSearchQuery(query: String = search.inputValue) {
        history.value = SearchHistoryStore.record(context.preferences, SearchHistoryStore.PAGE_SETTINGS_ACCOUNTS, query)
    }

    // Scroll to the Profiles / Listen Together card when the screen was opened with
    // focus=profiles / focus=listentogether (spec-customizable-burger-menu burger long press).
    val scrollState = rememberScrollState()
    var containerTopY by remember { mutableFloatStateOf(-1f) }
    var profilesCardTopY by remember { mutableFloatStateOf(-1f) }
    var listenTogetherCardTopY by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(focus) {
        if (focus != "profiles" && focus != "listentogether") return@LaunchedEffect
        // The card may be off-screen but is still laid out: wait until its position is
        // measured (read inside snapshotFlow, see the same mechanism in OtherSettings).
        val focusedProfiles = focus == "profiles"
        val offset = snapshotFlow {
            val cardTopY = if (focusedProfiles) profilesCardTopY else listenTogetherCardTopY
            if (cardTopY < 0f || containerTopY < 0f) -1f
            else cardTopY - containerTopY + scrollState.value
        }.first { it >= 0f }
        scrollState.animateScrollTo(offset.roundToInt())
    }

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .onGloballyPositioned { containerTopY = it.positionInRoot().y }
    ) {

        settingsSearchTitle(searchFocus, R.string.tab_accounts, R.drawable.person)

        settingsSearchDescription(searchFocus) {
            SettingsDescription(
                text = stringResource(R.string.accounts_settings_description),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        search.SearchBar(this, barPadding = searchFocus.barPadding, onSearch = { recordSearchQuery() })

        SearchHistorySection(
            visible = historyVisible,
            queries = historyMatches,
            onQueryClick = { search.input = TextFieldValue(it); recordSearchQuery(it); search.isFocused = false },
            onQueryDelete = {
                history.value = SearchHistoryStore.delete(context.preferences, SearchHistoryStore.PAGE_SETTINGS_ACCOUNTS, it)
            },
            onQueryDeleteAll = {
                history.value = SearchHistoryStore.deleteAll(context.preferences, SearchHistoryStore.PAGE_SETTINGS_ACCOUNTS)
            }
        )

        val hasSearchResults = search.inputValue.isBlank() || visibleCardTitleRes(context, 5, search.inputValue).isNotEmpty()
        LaunchedEffect(hasSearchResults) {
            if (!hasSearchResults) scrollState.scrollTo(0)
        }
        settingsSearchContent(searchFocus, hasResults = hasSearchResults) {
            // The face card owns the face edits; the profile card below shows the active
            // profile's state (face, data, database size) without actions. The burger
            // long press (focus=profiles) scrolls to the profile card.
            ProfileFaceCard(
                onOpenProfiles = { navController.navigate(NavRoutes.profiles.name) }
            )
            ProfileAccountCard(
                modifier = Modifier.onGloballyPositioned { profilesCardTopY = it.positionInRoot().y }
            )

            AccountsYouTubeCard(search)

            AccountsSyncCards()

            // Last.fm Section
            LastFmSettingsCard()

            // Listen Together Section (reuses the room-screen settings card, same pattern);
            // the burger long press (focus=listentogether) scrolls to it.
            Box(modifier = Modifier.onGloballyPositioned { listenTogetherCardTopY = it.positionInRoot().y }) {
                ListenTogetherSettingsCard(
                    onOpenScreen = { navController.navigate(NavRoutes.listenTogether.name) }
                )
            }

            AccountsDiscordCard(search)

            AccountsResetCard(search)

        }

        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
        )

    }
}

fun isYouTubeLoginEnabled(): Boolean {
    val isYouTubeLoginEnabled = appContext().encryptedPreferences.getBoolean(enableYouTubeLoginKey, false)
    return isYouTubeLoginEnabled
}

fun isYouTubeSyncEnabled(): Boolean {
    val isDevBuild = BuildConfig.BUILD_TYPE in listOf("debug", "dev64", "dev32", "beta64", "beta32")
    if (!isDevBuild) return false
    val isYouTubeSyncEnabled = appContext().encryptedPreferences.getBoolean(enableYouTubeSyncKey, false)
    val useLoginForBrowse = appContext().preferences.getBoolean(useLoginForBrowseKey, true)
    return isYouTubeSyncEnabled && isYouTubeLoggedIn() && isYouTubeLoginEnabled() && useLoginForBrowse
}

fun isYouTubeLoggedIn(): Boolean {
    val cookie = appContext().encryptedPreferences.getString(ytCookieKey, "")
    val isLoggedIn = cookie?.let { parseCookieString(it) }?.contains("SAPISID") == true
    return isLoggedIn
}
