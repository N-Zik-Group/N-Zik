package app.n_zik.android

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import android.graphics.Bitmap
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.ime
import com.kieronquinn.monetcompat.core.MonetActivityAccessException
import com.kieronquinn.monetcompat.core.MonetCompat
import com.kieronquinn.monetcompat.interfaces.MonetColorsChangedListener
import com.valentinilk.shimmer.LocalShimmerTheme
import com.valentinilk.shimmer.defaultShimmerTheme
import dev.kdrag0n.monet.theme.ColorScheme
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.playlistPage
import it.fast4x.innertube.requests.song
import it.fast4x.innertube.utils.LocalePreferenceItem
import app.it.fast4x.compose.persist.PersistMap
import app.it.fast4x.compose.persist.PersistMapOwner
import app.it.fast4x.compose.persist.LocalPersistMap
import it.fast4x.innertube.utils.LocalePreferences
import it.fast4x.innertube.utils.ProxyPreferenceItem
import it.fast4x.innertube.utils.ProxyPreferences
import app.it.fast4x.rimusic.enums.AnimatedGradient
import app.it.fast4x.rimusic.enums.AudioQualityFormat
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.enums.ColorPaletteName
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.HomeScreenTabs
import app.it.fast4x.rimusic.enums.Languages
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.PipModule
import app.it.fast4x.rimusic.enums.PlayerBackgroundColors
import app.it.fast4x.rimusic.extensions.pip.PipEventContainer
import app.it.fast4x.rimusic.extensions.pip.PipModuleContainer
import app.it.fast4x.rimusic.extensions.pip.PipModuleCover
import app.n_zik.android.components.onboarding.OnboardingAccountsScreen
import app.n_zik.android.components.onboarding.OnboardingImportScreen
import app.n_zik.android.components.onboarding.OnboardingProfileScreen
import app.n_zik.android.components.onboarding.OnboardingScreen
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.dialog.settings.HomeTabsSettingsDialog
import app.n_zik.android.components.ui.screens.home.OPEN_SEARCH_SHORTCUT
import app.n_zik.android.components.ui.screens.profiles.executeProfileSwitch
import app.n_zik.android.components.ui.screens.home.activeHomeTabIds
import app.n_zik.android.components.ui.screens.home.initialShortcutAction
import app.n_zik.android.components.ui.screens.rewind.RewindReminderWorker
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.enums.OnboardingStep
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.PlayerAwareInsetsTracker
import app.n_zik.android.utils.appNavBarPresentForRoute
import app.n_zik.android.utils.localeListForAppLanguage
import app.n_zik.android.utils.shouldRecreateActivity
import app.it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.themed.CrossfadeContainer
import app.it.fast4x.rimusic.ui.screens.AppNavigation
import app.it.fast4x.rimusic.ui.screens.player.MiniPlayer
import app.it.fast4x.rimusic.ui.screens.player.Player
import app.it.fast4x.rimusic.ui.screens.player.components.YoutubePlayer
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import app.it.fast4x.rimusic.ui.screens.player.rememberPlayerSheetState
import app.n_zik.android.components.CustomBottomSheet
import app.n_zik.android.listentogether.ListenTogetherGuestGuardPlayer
import app.n_zik.android.listentogether.shouldDisablePlayerSheetDismiss
import app.n_zik.android.components.player.MiniPlayerQueueOverlay
import app.n_zik.android.components.player.APP_HEADER_HEIGHT
import app.n_zik.android.components.player.miniPlayerDismissAlpha
import app.n_zik.android.components.player.shouldComposePlayerSheet
import app.n_zik.android.components.player.miniPlayerSideInset
import app.n_zik.android.components.player.miniPlayerTopInset
import app.n_zik.android.components.player.miniPlayerTopPaddingPx
import app.n_zik.android.components.player.TOP_NAV_BAR_HEIGHT
import app.n_zik.android.components.player.showMiniplayerIfDismissed
import app.n_zik.android.components.player.PaletteFade
import app.n_zik.android.components.player.m3eDynamicColorPaletteOf
import app.n_zik.android.components.player.m3eRecapRestoredDynamicPalette
import app.n_zik.android.components.player.presentMiniplayerThenExpand
import app.n_zik.android.components.player.presentMiniplayerCollapsed
import app.n_zik.android.components.player.MINIPLAYER_APPEAR_FADE_MS
import app.n_zik.android.components.player.MINIPLAYER_APPEAR_FADE_SKIPPED_FRAMES
import app.n_zik.android.components.theme.AnimatedAppearance
import app.n_zik.android.components.theme.withColor
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.colorPaletteOf
import app.it.fast4x.rimusic.ui.styling.customColorPalette
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.InitDownloader
import app.it.fast4x.rimusic.utils.LocalMonetCompat
import app.it.fast4x.rimusic.utils.UiTypeKey
import app.it.fast4x.rimusic.utils.animatedGradientKey
import app.it.fast4x.rimusic.utils.applyFontPaddingKey
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.audioQualityFormatKey
import app.it.fast4x.rimusic.utils.backgroundProgressKey
import app.it.fast4x.rimusic.utils.playerPositionKey
import app.it.fast4x.rimusic.enums.PlayerPosition
import app.it.fast4x.rimusic.utils.closeWithBackButtonKey
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.colorPaletteNameKey
import app.it.fast4x.rimusic.utils.customColorKey
import app.it.fast4x.rimusic.utils.customThemeDark_Background0Key
import app.it.fast4x.rimusic.utils.customThemeDark_Background1Key
import app.it.fast4x.rimusic.utils.customThemeDark_Background2Key
import app.it.fast4x.rimusic.utils.customThemeDark_Background3Key
import app.it.fast4x.rimusic.utils.customThemeDark_Background4Key
import app.it.fast4x.rimusic.utils.customThemeDark_TextKey
import app.it.fast4x.rimusic.utils.customThemeDark_accentKey
import app.it.fast4x.rimusic.utils.customThemeDark_iconButtonPlayerKey
import app.it.fast4x.rimusic.utils.customThemeDark_textDisabledKey
import app.it.fast4x.rimusic.utils.customThemeDark_textSecondaryKey
import app.it.fast4x.rimusic.utils.customThemeLight_Background0Key
import app.it.fast4x.rimusic.utils.customThemeLight_Background1Key
import app.it.fast4x.rimusic.utils.customThemeLight_Background2Key
import app.it.fast4x.rimusic.utils.customThemeLight_Background3Key
import app.it.fast4x.rimusic.utils.customThemeLight_Background4Key
import app.it.fast4x.rimusic.utils.customThemeLight_TextKey
import app.it.fast4x.rimusic.utils.customThemeLight_accentKey
import app.it.fast4x.rimusic.utils.customThemeLight_iconButtonPlayerKey
import app.it.fast4x.rimusic.utils.customThemeLight_textDisabledKey
import app.it.fast4x.rimusic.utils.customThemeLight_textSecondaryKey
import app.it.fast4x.rimusic.utils.currentMediaItemIdAsState
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.disableClosingPlayerSwipingDownKey
import app.it.fast4x.rimusic.utils.disablePlayerHorizontalSwipeKey
import app.it.fast4x.rimusic.utils.effectRotationKey
import app.it.fast4x.rimusic.utils.enableQuickPicksPageKey
import app.it.fast4x.rimusic.utils.fontTypeKey
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.forcePlay
import app.it.fast4x.rimusic.utils.forcePlayFromBeginning
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.homeTabsOrderKey
import app.it.fast4x.rimusic.utils.intent
import app.it.fast4x.rimusic.utils.invokeOnReady
import app.it.fast4x.rimusic.utils.isAtLeastAndroid6
import app.it.fast4x.rimusic.utils.isAtLeastAndroid8
import app.it.fast4x.rimusic.utils.isKeepScreenOnEnabledKey
import app.it.fast4x.rimusic.utils.isProxyEnabledKey
import app.it.fast4x.rimusic.utils.isValidIP
import app.it.fast4x.rimusic.utils.isVideo
import app.it.fast4x.rimusic.utils.keepPlayerMinimizedKey
import app.it.fast4x.rimusic.utils.languageAppKey
import app.it.fast4x.rimusic.utils.miniPlayerTypeKey
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.utils.navigationBarTypeKey
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.pipModuleKey
import app.it.fast4x.rimusic.utils.playNext
import app.it.fast4x.rimusic.utils.playerBackgroundColorsKey
import app.it.fast4x.rimusic.utils.playerThumbnailSizeKey
import app.it.fast4x.rimusic.utils.playerVisualizerTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.proxyHostnameKey
import app.it.fast4x.rimusic.utils.proxyModeKey
import app.it.fast4x.rimusic.utils.proxyPortKey
import app.it.fast4x.rimusic.utils.readProfileIds
import app.it.fast4x.rimusic.enums.TransitionEffect
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.restartActivityKey
import app.it.fast4x.rimusic.utils.hideStatusBarKey
import app.it.fast4x.rimusic.utils.saveProfileLastUsed
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.it.fast4x.rimusic.utils.setDefaultPalette
import app.it.fast4x.rimusic.utils.showButtonPlayerVideoKey
import app.it.fast4x.rimusic.utils.showSearchTabKey
import app.it.fast4x.rimusic.utils.showTotalTimeQueueKey
import app.n_zik.android.core.coil.*
import app.it.fast4x.rimusic.utils.thumbnailRoundnessDpKey
import app.it.fast4x.rimusic.utils.artistThumbnailRoundnessDpKey
import app.it.fast4x.rimusic.utils.transitionEffectKey
import app.it.fast4x.rimusic.utils.useSystemFontKey
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.invidious.Invidious

import app.kreate.android.me.knighthat.utils.Toaster
import it.fast4x.innertube.Innertube.proxy
import okhttp3.OkHttpClient
import timber.log.Timber
import java.net.Proxy
import java.util.Locale

import androidx.compose.foundation.shape.RoundedCornerShape
import app.it.fast4x.rimusic.enums.UiType
import app.it.fast4x.rimusic.ui.styling.BoundedCornerSize
import app.n_zik.android.core.navigation.MiniPlayerQueueInterceptor
import app.n_zik.android.core.network.client.NetworkClientFactory
import app.n_zik.android.extensions.discord.DiscordUiState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.navigation.NavController
import androidx.compose.runtime.mutableFloatStateOf
import androidx.lifecycle.Lifecycle
import app.n_zik.android.enums.PendingMiniPlayerAction
import app.n_zik.android.core.backup.BackupManager
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.State
import androidx.core.view.WindowInsetsControllerCompat
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.n_zik.android.core.database.Database
import android.Manifest
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import kotlinx.coroutines.Job
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlin.system.exitProcess

/**
 * Decodes the deck target carried by a rewind reminder's content intent: a valid year with a
 * month 1..12 yields the finished `(year, month)` pair (monthly reminder); a valid year with
 * the month extra missing (default 0) or explicitly 0 yields the yearly target `(year, 0)`
 * (yearly reminder — its content intent carries the year extra only). Anything else (missing
 * year, out-of-range values, no extras) yields null so the app starts without a forced deck
 * open.
 *
 * The one-shot contract is layered (spec GH-275 + follow-up):
 * - [consumeRewindDeckExtras] strips the extras off the intent the activity keeps, so a
 *   singleTask relaunch re-delivering that intent decodes to null;
 * - [lastConsumedMarker] rejects an intent whose target was already consumed. That is the
 *   task record re-sending the ORIGINAL launch intent (extras intact) when the task is
 *   restored after a process death — a path the in-memory strip cannot reach, which
 *   previously re-opened the deck on every tap of any other notification (device-observed
 *   leak, fixed 2026-10-01). A fresh notification tap still decodes to its target: the tap
 *   is the user's intent (user decision 2026-10-01 — the tap always re-opens the deck).
 *
 * Top-level so the parse contract is unit-testable without launching the activity
 * (spec GH-275 — the consumer side is covered by RewindDeckDeepLinkTest).
 */
internal fun rewindDeckTargetFromIntent(
    intent: Intent?,
    lastConsumedMarker: Int = 0,
): Pair<Int, Int>? {
    val year = intent?.getIntExtra(RewindReminderWorker.EXTRA_DECK_YEAR, 0) ?: 0
    val month = intent?.getIntExtra(RewindReminderWorker.EXTRA_DECK_MONTH, 0) ?: 0
    val target: Pair<Int, Int>? = when {
        year !in 2000..2100 -> null
        month in 1..12 -> year to month
        // Yearly sentinel: the yearly reminder posts the year extra only, so the missing
        // month extra (default 0) selects the finished-year deck
        month == 0 -> year to month
        else -> null
    }
    return target?.takeUnless {
        rewindDeckTargetMarker(it.first, it.second) == lastConsumedMarker
    }
}

/**
 * Encodes a decoded deck target for the persistent consumption marker
 * ([DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED]): `year * 100 + month`, the yearly
 * sentinel (month 0) kept as is. A monthly target repeats only 12 cycles apart and a yearly
 * one yearly, so the encoding is unique per notification cycle — the marker never needs
 * resetting, only overwriting. Top-level so the encoding is unit-testable.
 */
internal fun rewindDeckTargetMarker(year: Int, month: Int): Int = year * 100 + month

/**
 * Decodes the rewind playlist deep link carried by a playlist-ready notification's content
 * intent ([RewindPlaylists.EXTRA_REWIND_PLAYLIST_ID], spec GH-275 follow-up — the playlist
 * notifications now open the generated 'Rewind — <period>' playlist directly instead of a
 * bare app open): a positive playlist id yields it, anything else (missing extra, 0,
 * negative) yields null so the app starts without a forced playlist open. [lastConsumedId]
 * rejects an intent pointing at an already-opened playlist — the task record re-sends the
 * original launch intent after a process-death restore (same guard as the deck target).
 * Top-level so the parse contract is unit-testable without launching the activity.
 */
internal fun rewindPlaylistTargetFromIntent(
    intent: Intent?,
    lastConsumedId: Long = 0L,
): Long? =
    intent?.getLongExtra(RewindPlaylists.EXTRA_REWIND_PLAYLIST_ID, 0L)
        ?.takeIf { it > 0L && it != lastConsumedId }

/**
 * One-shot consumption of the deck extras on the activity's current intent: removes
 * [RewindReminderWorker.EXTRA_DECK_YEAR] and [RewindReminderWorker.EXTRA_DECK_MONTH] so a
 * later singleTask relaunch decodes to null instead of re-opening the deck on the same
 * finished month/year. singleTask re-delivers the activity's current intent on every task
 * relaunch (recents), so a tap on the notification must strip the extras from the intent
 * kept as the current intent — otherwise every app re-foreground re-opens the deck.
 * Call it AFTER [rewindDeckTargetFromIntent] has consumed the target into the activity
 * state. Top-level so the consume-then-reparse contract is unit-testable.
 */
internal fun consumeRewindDeckExtras(intent: Intent?) {
    intent?.removeExtra(RewindReminderWorker.EXTRA_DECK_YEAR)
    intent?.removeExtra(RewindReminderWorker.EXTRA_DECK_MONTH)
}

/**
 * One-shot consumption of the playlist deep link on the activity's current intent — the
 * playlist-id twin of [consumeRewindDeckExtras] (spec GH-275 follow-up). The strip is
 * unconditional: even a rejected (already-opened) playlist id must not stay alive in the
 * kept intent for a later re-delivery.
 */
internal fun consumeRewindPlaylistExtras(intent: Intent?) {
    intent?.removeExtra(RewindPlaylists.EXTRA_REWIND_PLAYLIST_ID)
}

/**
 * Decodes the "now playing" notification tap carried by the notification's content intent
 * ([MainActivity.EXTRA_OPEN_PLAYER_TOKEN], set by PlayerServiceModern's global session
 * activity — the PendingIntent media3 uses as the notification content intent,
 * spec-notification-click-opens-player option B): a positive token (the session's one-shot
 * System.currentTimeMillis() token, static per service instance) yields it, anything else
 * (missing extra, 0, negative) yields null so the app starts without a forced player open.
 * Staleness is NOT rejected here — the task record re-sends the original launch intent
 * (extras intact) after a process-death restore, and the token is static per service, so
 * the rejection lives at deployment time as a live comparison against the current service's
 * token (see [consumeOpenPlayerDeepLink] / the cold deploy site).
 * Top-level so the parse contract is unit-testable without launching the activity.
 */
internal fun openPlayerTokenFromIntent(intent: Intent?): Long? =
    intent?.getLongExtra(MainActivity.EXTRA_OPEN_PLAYER_TOKEN, 0L)
        ?.takeIf { it > 0L }

/**
 * One-shot consumption of the "open player" extra on the intent the activity keeps — the
 * token twin of [consumeRewindPlaylistExtras] (spec-notification-click-opens-player). The
 * strip is unconditional: even a rejected (non-positive / malformed) token must not stay
 * alive in the kept intent for a later re-delivery.
 */
internal fun consumeOpenPlayerExtras(intent: Intent?) {
    intent?.removeExtra(MainActivity.EXTRA_OPEN_PLAYER_TOKEN)
}

/**
 * Builds the deck route for a decoded target (spec GH-275): the monthly target (month 1..12)
 * carries both arguments, while the yearly target (month = 0 intent sentinel) omits the month
 * argument — the route's default month=-1 then maps to `rewindMonth = null` in
 * `RewindScreen`, i.e. the year-only deck. Top-level so the monthly/yearly branch is
 * unit-testable without launching the activity.
 */
internal fun rewindDeckRoute(year: Int, month: Int): String =
    if (month == 0) {
        "${NavRoutes.rewind.name}?year=$year"
    } else {
        "${NavRoutes.rewind.name}?year=$year&month=$month"
    }

/**
 * The stable profile ID a direct profile shortcut intent points to (action
 * [MainActivity.action_profile] + [MainActivity.EXTRA_PROFILE_ID]), or null when the intent
 * carries no such target (missing action, or missing/absent extra). Top-level so the
 * cold/warm decision is unit-testable without launching the activity (spec-profile-shortcuts).
 */
internal fun profileShortcutTarget(intent: Intent?): String? =
    if (intent?.action == MainActivity.action_profile) {
        intent.getStringExtra(MainActivity.EXTRA_PROFILE_ID)
    } else {
        null
    }

/**
 * One-shot consumption of the profile-shortcut extra on the intent the activity keeps: a
 * singleTask relaunch re-delivers the kept intent, so the extra must be stripped off it —
 * the in-memory state alone cannot reach the task record (same pattern as
 * [consumeRewindDeckExtras], spec-profile-shortcuts). The strip is unconditional: even a
 * rejected (unknown profile) tap must not keep its extra alive for a later re-delivery.
 */
internal fun consumeProfileShortcutExtra(intent: Intent?) {
    intent?.removeExtra(MainActivity.EXTRA_PROFILE_ID)
}

/**
 * The profile a profile-shortcut tap should switch to, or null for a bare launch: a tap on
 * the profile that is already active (no switch, no restart) or on an unknown/deleted ID
 * (ignored — the app never points itself at a profile that does not exist) both yield null.
 * Pure so the cold/warm decision is unit-testable without an Intent or a Context
 * (spec-profile-shortcuts).
 */
internal fun profileSwitchTarget(tapId: String?, activeId: String, validIds: Collection<String>): String? =
    tapId?.takeIf { it != activeId && it in validIds }

/**
 * True when [action] is one of the two profile-shortcut actions (generic « Profiles » or a
 * direct per-profile one). Those are consumed by their own paths (spec-profile-shortcuts) and
 * must NOT feed [shortcutIntentAction] — doing so would make the home-tab effect pop the back
 * stack to home, which is not the profile shortcuts' job. Top-level so the exclusion is
 * unit-testable without launching the activity.
 */
internal fun isProfileShortcutAction(action: String?): Boolean =
    action == MainActivity.action_profiles || action == MainActivity.action_profile

@UnstableApi
class MainActivity :
//MonetCompatActivity(),
    AppCompatActivity(),
    MonetColorsChangedListener,
    PersistMapOwner
{
    var downloadHelper = MyDownloadHelper

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is PlayerServiceModern.Binder) {
                this@MainActivity.binder = service
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
        }

    }

    private var binder by mutableStateOf<PlayerServiceModern.Binder?>(null)
    private var intentUriData by mutableStateOf<Uri?>(null)

    // Observable so a shortcut tap while the app is already running (delivered via onNewIntent,
    // which never re-triggers onCreate) still recomposes the tab-navigation check below --
    // reading `intent.action` directly only ever saw the launch-time intent.
    // Not re-seeded from the launch intent when the activity is recreated (theme change, settings
    // import): the shortcut was already consumed and would otherwise pop the back stack to home.
    // Internal (not private) so the Robolectric lifecycle tests can assert the profile-shortcut
    // exclusion — a profile tap must never feed this state (it would pop the back stack to home).
    internal var shortcutIntentAction by mutableStateOf<String?>(null)

    // Finished month (or finished year, month = 0 yearly sentinel) carried by a rewind
    // reminder's content intent. Set on cold start (onCreate) and warm start (onNewIntent),
    // consumed once by the navigation effect that opens the deck on that month — or the
    // yearly deck on that year. Internal (not private) so the Robolectric lifecycle tests
    // can assert the consumed target (spec GH-275 follow-up).
    internal var rewindDeckTarget by mutableStateOf<Pair<Int, Int>?>(null)

    // Database id of the 'Rewind — <period>' playlist carried by a playlist-ready
    // notification's content intent (spec GH-275 follow-up). Set on cold start (onCreate)
    // and warm start (onNewIntent), consumed once by the navigation effect that opens the
    // playlist directly. Internal for the same reason as [rewindDeckTarget].
    internal var rewindPlaylistTarget by mutableStateOf<Long?>(null)

    // Generic « Profiles » launcher shortcut (spec-profile-shortcuts): set on cold start
    // (onCreate) and warm start (onNewIntent) when the intent action is [action_profiles],
    // consumed once by the navigation effect that opens the profiles page. Internal for the
    // same reason as [rewindDeckTarget].
    internal var openProfilesShortcut by mutableStateOf(false)

    // "Now playing" notification tap — COLD start (spec-notification-click-opens-player):
    // the tap token HELD in memory (Long?) when the launch intent carries one — no persistent
    // marker. Validated at deployment time by a live comparison against the current service's
    // token (via the Binder): equal → legitimate tap (always deploys the full player, the
    // keepPlayerMinimized setting is NOT consulted, decision 2026-10-03); different → stale
    // (process-death re-send, the service restarted and minted a new token) → discarded, no
    // deployment. A valid token with no media yet is HELD and re-checked on every
    // playerUpdateTrigger. Internal for the same reason as [rewindDeckTarget].
    internal var openPlayerColdToken by mutableStateOf<Long?>(null)

    // "Now playing" notification tap — WARM start (spec-notification-click-opens-player):
    // set on warm start (onNewIntent) when the new intent carries a fresh open-player
    // token, consumed once by the deployment effect that presents the mini-player (if the
    // sheet is dismissed) then deploys the full player. Internal for the same reason as
    // [rewindDeckTarget].
    internal var openPlayerFromNotificationWarm by mutableStateOf(false)

    // Current step of the first-launch onboarding flow, held by the activity so a
    // recreation (rotation) resumes the flow at the right step; null means the flow
    // is complete and the main navigation renders instead. The step is persisted in
    // prefs on every transition (see advanceOnboarding), so a process restart —
    // post-import restart or process death — resumes at the right step too. The
    // onboardingComplete flag is written only when the flow is fully done (leaving
    // the profile step, the last one) — a successful restore or a Discord-token
    // restart from the accounts step never writes it: the flow advances to the next
    // step before the restart, so the restart lands on that step, keeping the user
    // inside onboarding — and a mid-flow crash never marks it as finished.
    private var onboardingStep by mutableStateOf<OnboardingStep?>(null)

    /**
     * Advances the onboarding flow to the next step ([OnboardingStep.advance]). The step is
     * persisted first so a process restart (post-import restart, process death) resumes at
     * that step instead of replaying the flow. When the flow completes, the
     * onboarding-complete flag is written and the persisted step cleared.
     */
    private fun advanceOnboarding() {
        val current = onboardingStep ?: return
        val next = current.advance()
        if (next == null) {
            DataStoreUtils.saveBoolean(this, DataStoreUtils.KEY_ONBOARDING_COMPLETE, true)
            DataStoreUtils.saveString(this, DataStoreUtils.KEY_ONBOARDING_STEP, "")
            Timber.tag("MainActivity").i("Onboarding complete, flag written, step cleared")
        } else {
            DataStoreUtils.saveString(this, DataStoreUtils.KEY_ONBOARDING_STEP, next.name)
            Timber.tag("MainActivity").d("Onboarding step: ${current.name} -> ${next.name}")
        }
        onboardingStep = next
    }

    override val persistMap = PersistMap()

    private var _monet: MonetCompat? by mutableStateOf(null)
    private val monet get() = _monet ?: throw MonetActivityAccessException()

    private val pipState: MutableState<Boolean> = mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            // If permission is denied, redirect to settings
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
        }
    }

    override fun onStart() {
        super.onStart()

        runCatching {
            bindService(intent<PlayerServiceModern>(), serviceConnection, Context.BIND_AUTO_CREATE)
        }.onFailure {
            Timber.tag("MainActivity").e("onStart bindService ${it.stackTraceToString()}")
        }
    }

    /**
     * Consumes the rewind deep links carried by [intent] into the activity state (deck target
     * and/or playlist id, spec GH-275 + follow-up), records each ACCEPTED target in its
     * persistent last-consumed marker ([DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED] /
     * [DataStoreUtils.KEY_REWIND_PLAYLIST_LAST_CONSUMED]), then strips ALL the deep-link
     * extras off the intent the activity keeps.
     *
     * A target already recorded in its marker is rejected — that is the task record
     * re-sending the original launch intent (extras intact) when the task is restored after a
     * process death, and the singleTask relaunch of a kept intent: the in-memory strip alone
     * cannot reach the task record, so without the marker the deck re-opened on every tap of
     * any other notification (device-observed leak, fixed 2026-10-01). A fresh notification
     * tap always carries an unconsumed target, so it still opens (user decision 2026-10-01 —
     * the tap always re-opens the deck).
     *
     * Runs synchronously in onCreate (app off) and onNewIntent (app on) — NOT behind
     * monet.invokeOnReady/startApp — so the intent extras are consumed the moment the intent
     * arrives and the contract is unit-testable on the real activity. Known edge: a target
     * accepted while the onboarding flow is up (the navigation effect holds it) is lost if
     * the process dies before the flow completes — the re-delivered intent is then rejected
     * as already consumed and the deck/playlist does not open. Rare (a mid-onboarding tap
     * plus a process death) and non-fatal: the deck/playlist stays reachable manually.
     *
     * Internal (not private) so the Robolectric lifecycle tests can exercise the exact
     * consumption contract both launch paths share.
     */
    internal fun consumeRewindDeepLinks(intent: Intent?) {
        val deck = rewindDeckTargetFromIntent(
            intent,
            DataStoreUtils.getInt(this, DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED, 0),
        )
        rewindDeckTarget = deck ?: rewindDeckTarget
        deck?.let {
            DataStoreUtils.saveInt(
                this,
                DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED,
                rewindDeckTargetMarker(it.first, it.second),
            )
        }
        val playlist = rewindPlaylistTargetFromIntent(
            intent,
            DataStoreUtils.getLong(this, DataStoreUtils.KEY_REWIND_PLAYLIST_LAST_CONSUMED, 0L),
        )
        rewindPlaylistTarget = playlist ?: rewindPlaylistTarget
        playlist?.let {
            DataStoreUtils.saveLong(this, DataStoreUtils.KEY_REWIND_PLAYLIST_LAST_CONSUMED, it)
        }
        // The strip is unconditional (not gated on a successful parse): rejected targets
        // must not keep their extras alive in the kept intent for a later re-delivery.
        consumeRewindDeckExtras(intent)
        consumeRewindPlaylistExtras(intent)
    }

    /**
     * Consumes the "now playing" notification tap carried by [intent]
     * (spec-notification-click-opens-player): a positive token arms the matching one-shot
     * state — [openPlayerColdToken] (held in memory) on cold start,
     * [openPlayerFromNotificationWarm] on warm start — then strips the extra off the intent
     * the activity keeps.
     *
     * There is NO persistent marker here: the token is static for the service's lifetime, so
     * a "last consumed" marker would reject a legitimate second cold tap (the activity is
     * destroyed while the foreground service survives — recents swipe, memory pressure).
     * Staleness is instead rejected at deployment time by a live comparison of the held token
     * against the CURRENT service's token (via the Binder): a process-death task-record
     * re-delivery carries the OLD token, while the restarted service minted a new one →
     * mismatch → discarded, no deployment. A warm tap needs no comparison at all: it is a
     * fresh fire of the PendingIntent, and any re-delivery was already stripped off the
     * kept intent.
     *
     * Runs synchronously in onCreate (app off, [cold] = true) and onNewIntent (app on,
     * [cold] = false) — the extra is consumed the moment the intent arrives. Internal (not
     * private) so the Robolectric lifecycle tests can exercise the exact consumption
     * contract both launch paths share.
     */
    internal fun consumeOpenPlayerDeepLink(intent: Intent?, cold: Boolean) {
        val token = openPlayerTokenFromIntent(intent)
        if (token != null) {
            if (cold) {
                openPlayerColdToken = token
            } else {
                openPlayerFromNotificationWarm = true
            }
            Timber.tag("MainActivity").i("Notification tap: open-player token $token consumed (${if (cold) "cold" else "warm"})")
        }
        // The strip is unconditional (not gated on a successful parse): a rejected token
        // must not keep its extra alive in the kept intent for a later re-delivery.
        consumeOpenPlayerExtras(intent)
    }

    /**
     * The user profile IDs a profile shortcut may point to, read from the names file (the base
     * profile is never listed). A read failure degrades to "no profiles" — the tap is then a
     * bare launch — instead of crashing the cold start (spec-profile-shortcuts).
     */
    private fun validProfileIds(): List<String> =
        listOf(DEFAULT_PROFILE_ID) + runCatching { readProfileIds() }.getOrDefault(emptyList())

    /**
     * Consumes the direct profile shortcut carried by [intent] — COLD path
     * (spec-profile-shortcuts): when the tap names a valid user profile that is not the active
     * one, the active profile is set BEFORE the app reads any per-profile state — [startApp]
     * (and with it the first per-profile prefs/DB access) runs only later, via
     * `monet.invokeOnReady` — so the launch lands straight in the tapped profile. A tap on the
     * active profile, or on an unknown/deleted ID, is a bare launch (no switch, no crash).
     * The extra is one-shot: stripped off the intent the activity keeps, so the singleTask
     * task relaunch re-delivering it cannot re-apply the tap (same pattern as the rewind deep
     * links — see [consumeProfileShortcutExtra]).
     */
    internal fun consumeProfileShortcut(intent: Intent?) {
        val tapId = profileShortcutTarget(intent)
        val switchTarget = profileSwitchTarget(tapId, getActiveProfile(this), validProfileIds())
        switchTarget?.let {
            setActiveProfile(it, this)
            // The tapped profile counts as used from this launch (spec-profiles-page-face: the
            // active profile is recorded as used on every app start — the boot-time record in
            // MainApplication still names the previous profile).
            saveProfileLastUsed(it, System.currentTimeMillis())
            Timber.tag("MainActivity").i("Profile shortcut (cold): active profile set to $it before launch")
        }
        consumeProfileShortcutExtra(intent)
    }

    /**
     * Consumes the direct profile shortcut carried by [intent] — WARM path (app already
     * running, spec-profile-shortcuts): a tap on a different valid profile runs the whole
     * CAP-3 switch ([executeProfileSwitch] — same wiring as the profiles page: cancel the
     * outgoing profile's notifications, stop the services, ensure the incoming channels) and
     * exits the process, so the relaunch reads the new profile. A tap on the active profile,
     * or on an unknown/deleted ID, is a bare consumption — no switch, no restart.
     * The extra is stripped BEFORE the switch, so the kept intent never carries it again
     * (same one-shot contract as the cold path and the rewind deep links).
     */
    internal fun consumeProfileShortcutWarm(intent: Intent?) {
        val tapId = profileShortcutTarget(intent)
        val switchTarget = profileSwitchTarget(tapId, getActiveProfile(this), validProfileIds())
        consumeProfileShortcutExtra(intent)
        switchTarget?.let {
            Timber.tag("MainActivity").i("Profile shortcut (warm): switching to $it, exiting the process")
            executeProfileSwitch(it, this)
            // exit 0, like the profiles page: the new profile's stores can only be loaded in a
            // fresh process. The exit deliberately lives at the call site (not in
            // executeProfileSwitch) so the switch wiring stays pinnable under Robolectric
            // without killing the test JVM (ProfileSwitchTest).
            exitProcess(0)
        }
    }

    @ExperimentalTextApi
    @UnstableApi
    @ExperimentalComposeUiApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MonetCompat.enablePaletteCompat()

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                scrim = Color.Transparent.toArgb(),
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = Color.Transparent.toArgb(),
                darkScrim = Color.Transparent.toArgb()
            )
        )

        WindowCompat.setDecorFitsSystemWindows(window, false)

        MonetCompat.setup(this)
        _monet = MonetCompat.getInstance()
        monet.setDefaultPalette()
        monet.addMonetColorsChangedListener(
            listener = this,
            notifySelf = false
        )
        monet.updateMonetColors()

        val isRestoredInstance = savedInstanceState != null

        // Direct profile shortcut (spec-profile-shortcuts, cold path): the active profile is
        // set BEFORE startApp runs (it is dispatched after onCreate returns, via
        // monet.invokeOnReady), so the first per-profile prefs/DB access already sees the
        // tapped profile. The generic « Profiles » shortcut only records the navigation
        // target — the effect below opens the page once the graph is composed.
        consumeProfileShortcut(intent)
        // Gated on isRestoredInstance (like [shortcutIntentAction] below): a recreated/restored
        // activity is handed the kept launch intent again, whose action_profiles cannot be
        // stripped (it is the action, not an extra) — re-deriving the flag would re-open the
        // profiles page on every rotation / theme change / process-death restore.
        openProfilesShortcut = !isRestoredInstance && intent?.action == action_profiles

        monet.invokeOnReady {
            startApp(isRestoredInstance)
        }

        // Rewind notification deep links (deck / playlist): consume the launch intent's
        // targets synchronously — app OFF path. A task restored after a process death
        // re-sends its original launch intent here; the persistent last-consumed markers
        // reject an already-consumed target while a fresh tap still opens (spec GH-275
        // follow-up). The app ON path is onNewIntent, which shares the same contract.
        consumeRewindDeepLinks(intent)
        // "Now playing" notification tap (spec-notification-click-opens-player): consume the
        // launch intent's open-player token synchronously — app OFF path. A positive token is
        // held in memory ([openPlayerColdToken]) and validated at deployment time by a live
        // comparison against the current service's token (staleness is the service's live
        // token — there is no persistent marker); the extra is stripped off the kept intent
        // either way.
        consumeOpenPlayerDeepLink(intent, cold = true)

        checkIfAppIsRunningInBackground()
        // App shortcuts are now registered in MainApplication.onCreate (before Dependencies.init)
        // so they survive initialization crashes. See ShortcutIconSync.kt.
        // Verify backup location exists
        lifecycleScope.launch(NzikDispatchers.DATA) {
            BackupManager.verifyBackupLocation(this@MainActivity)
        }

        // Fetch Invidious instances
        lifecycleScope.launch(NzikDispatchers.DATA) {
            try {
                Invidious.fetchInstances()
            } catch (e: Exception) {
                Timber.tag("MainActivity").e(e, "Error fetching Invidious instances")
            }
        }

        // Ask for notification permission if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun checkIfAppIsRunningInBackground() {
        val runningAppProcessInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(runningAppProcessInfo)
        appRunningInBackground =
            runningAppProcessInfo.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipState.value = isInPictureInPictureMode
        Timber.tag("MainActivity").d("onPictureInPictureModeChanged: $isInPictureInPictureMode")
    }

    @Composable
    fun ThemeApp(
        isDark: Boolean = false,
        content: @Composable () -> Unit
    ) {
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                (view.context as Activity).window.let { window ->
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                        !isDark
                    WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars =
                        !isDark
                }
            }
            
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, isDark) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        (view.context as Activity).window.let { window ->
                            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                                !isDark
                            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars =
                                !isDark
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }
        }
        content()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("UnusedBoxWithConstraintsScope")
    @OptIn(
        ExperimentalTextApi::class,
        ExperimentalFoundationApi::class, ExperimentalAnimationApi::class,
        ExperimentalMaterial3Api::class
    )
    fun startApp(isRestoredInstance: Boolean) {

        // Check YouTube cookie status on startup — warn user if expired/invalid
        when (MainApplication.cookieStatus) {
            MainApplication.CookieStatus.INVALID -> {
                lifecycleScope.launch {
                    delay(3_000)
                    Toaster.e(R.string.error_cookie_invalid)
                }
            }
            MainApplication.CookieStatus.EXPIRED -> {
                lifecycleScope.launch {
                    delay(3_000)
                    Toaster.e(R.string.error_session_expired)
                }
            }
            MainApplication.CookieStatus.NOT_LOGGED_IN -> {
                // Silent — user may choose not to log in
            }
            MainApplication.CookieStatus.VALID -> { /* all good */ }
        }

        if (!preferences.getBoolean(closeWithBackButtonKey, false))
            if (Build.VERSION.SDK_INT >= 33) {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT
                ) {
                
                }
            }

        /*
            Instead of checking getBoolean() individually, we can use .let() to express condition.
            Or, the whole thing is 'false' if null appears in the process.
         */
        val launchedFromNotification: Boolean =
            intent?.extras?.let {
                // The AA "expandPlayerBottomSheet" producer (setSessionActivity) was removed —
                // only the widget launch still sets this extra.
                it.getBoolean("fromWidget")
            } ?: false

        Timber.tag("MainActivity").d("onCreate launchedFromNotification: $launchedFromNotification intent ${intent.action}")

        intentUriData = intent.data ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri()
        // The profile-shortcut actions have their own consumption (spec-profile-shortcuts,
        // done in onCreate / onNewIntent) and must not ride the home-tab shortcut state,
        // which would pop the back stack to home.
        shortcutIntentAction =
            initialShortcutAction(intent.action, isRestoredInstance)?.takeUnless { isProfileShortcutAction(it) }
        // Rewind deep links are consumed synchronously in onCreate / onNewIntent
        // (consumeRewindDeepLinks), before startApp runs — see those call sites.
        onboardingStep = OnboardingStep.resolveStartupStep(
            complete = DataStoreUtils.getBoolean(this, DataStoreUtils.KEY_ONBOARDING_COMPLETE, false),
            // Resume at the persisted step after a post-import restart or process death
            // (a restore never completes the onboarding, so this covers it too); fresh
            // installs have nothing persisted and fall back to the first step
            persistedStepName = DataStoreUtils.getString(this, DataStoreUtils.KEY_ONBOARDING_STEP, ""),
        )

        with(preferences) {
            if (getBoolean(isKeepScreenOnEnabledKey, false)) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            val proxy = if (getBoolean(isProxyEnabledKey, false)) {
                val hostName = getString(proxyHostnameKey, null)
                val proxyPort = getInt(proxyPortKey, 8080)
                val proxyMode = getEnum(proxyModeKey, Proxy.Type.HTTP)
                if (isValidIP(hostName) && hostName != null) {
                    it.fast4x.innertube.utils.getProxy(ProxyPreferenceItem(hostName, proxyPort, proxyMode))
                } else {
                    Timber.w("Proxy preference is null or invalid, running without proxy")
                    null
                }
            } else {
                Timber.w("Proxy preference is null, running without proxy")
                null
            }

            NetworkClientFactory.configure(
                proxy = proxy,
                cacheDir = this@MainActivity.externalCacheDir ?: this@MainActivity.cacheDir
            )
            Innertube.proxy = proxy
        }

        setContent {
            val colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)

            //TODO: Check internet connection

            val coroutineScope = rememberCoroutineScope()
            val paletteJob = remember { mutableStateOf<Job?>(null) }
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val navController = rememberNavController()
            DisposableEffect(navController) {
                val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                    DiscordUiState.currentRoute.value = destination.route
                }
                navController.addOnDestinationChangedListener(listener)
                onDispose {
                    navController.removeOnDestinationChangedListener(listener)
                }
            }
            var showPlayer by rememberSaveable { mutableStateOf(false) }
            var showQueueOverlay by rememberSaveable { mutableStateOf(false) }
            val queueInterceptor = remember(navController) {
                MiniPlayerQueueInterceptor(navController) { showQueueOverlay = true }
            }
            DisposableEffect(navController) {
                queueInterceptor.attach()
                onDispose { queueInterceptor.detach() }
            }
            var switchToAudioPlayer by rememberSaveable { mutableStateOf(false) }
            val pendingMiniPlayerAction = remember { mutableStateOf<PendingMiniPlayerAction?>(null) }
            val isShowingLyrics = rememberSaveable { mutableStateOf(false) }
            val isShowingVisualizer = rememberSaveable { mutableStateOf(false) }
            var animatedGradient by rememberPreference(animatedGradientKey, AnimatedGradient.M3EMorphingCover)
            var customColor by rememberPreference(customColorKey, Color.Green.hashCode())
            val lightTheme = colorPaletteMode == ColorPaletteMode.Light || (colorPaletteMode == ColorPaletteMode.System && (!isSystemInDarkTheme()))


            LocalePreferences.preference =
                LocalePreferenceItem(
                    hl = Locale.getDefault().language,
                    gl = Locale.getDefault().country
                )

            preferences.getEnum(audioQualityFormatKey, AudioQualityFormat.Auto)

            fun computeAppearance(): Appearance = with(preferences) {
                val colorPaletteName =
                    getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic)
                val colorPaletteMode = getEnum(colorPaletteModeKey, ColorPaletteMode.Dark)
                val thumbnailRoundnessDp = getFloat(thumbnailRoundnessDpKey, 12f)
                val artistThumbnailRoundnessDp = getFloat(artistThumbnailRoundnessDpKey, 48f)
                val uiRoundnessDp = getFloat("uiRoundnessDpKey", 25f)
                val useSystemFont = getBoolean(useSystemFontKey, false)
                val applyFontPadding = getBoolean(applyFontPaddingKey, false)

                var colorPalette =
                    colorPaletteOf(colorPaletteName, colorPaletteMode, !lightTheme)

                val fontType = getEnum(fontTypeKey, FontType.Rubik)

                if (colorPaletteName == ColorPaletteName.MaterialYou) {
                    colorPalette = dynamicColorPaletteOf(
                        Color(monet.getAccentColor(this@MainActivity)),
                        !lightTheme
                    )
                }
                if (colorPaletteName == ColorPaletteName.CustomColor) {
                    colorPalette = dynamicColorPaletteOf(
                        Color(customColor),
                        !lightTheme
                    )
                }

                Appearance(
                    colorPalette = colorPalette,
                    typography = typographyOf(
                        colorPalette.text,
                        useSystemFont,
                        applyFontPadding,
                        fontType
                    ),
                    thumbnailShape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f)),
                    uiRoundnessShape = RoundedCornerShape(BoundedCornerSize(uiRoundnessDp.dp, 0.4f)),
                    artistThumbnailShape = if (artistThumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(artistThumbnailRoundnessDp.dp, 0.25f))
                )
            }

            var appearance by rememberSaveable(stateSaver = Appearance.Companion) {
                mutableStateOf(computeAppearance())
            }

            var fadeFromAppearance by remember { mutableStateOf<Appearance?>(null) }

            fun updateAppearance(newAppearance: Appearance, animateGlobal: Boolean = false) {
                if (animateGlobal) fadeFromAppearance = appearance
                appearance = newAppearance
            }

            // Re-cap the restored dynamic palette once at startup (spec-achromatic-ramp-luminance-cap,
            // loopback 2, EC-1): the Saver ColorPalette.Companion persists only accent + isDark and
            // rebuilds through the legacy non-capped dynamicColorPaletteOf, so a process death can
            // restore an uncapped achromatic ramp (near-white app in dark mode, or the mirror)
            // until the next extraction. No-op same-instance for matching/in-range tones, and
            // skipped entirely for static palettes (saved name != Dynamic).
            LaunchedEffect(Unit) {
                val savedColorPaletteName =
                    preferences.getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic)
                if (savedColorPaletteName == ColorPaletteName.Dynamic) {
                    val themeIsDark =
                        colorPaletteMode == ColorPaletteMode.Dark ||
                                colorPaletteMode == ColorPaletteMode.PitchBlack ||
                                (colorPaletteMode == ColorPaletteMode.System && isSystemInDarkTheme)
                    val recapped =
                        appearance.colorPalette.m3eRecapRestoredDynamicPalette(savedColorPaletteName, themeIsDark)
                    if (recapped !== appearance.colorPalette) {
                        updateAppearance(
                            appearance.copy(
                                colorPalette = recapped,
                                typography = appearance.typography.withColor(recapped.text)
                            )
                        )
                    }
                }
            }

            fun setDynamicPalette(url: String?, animateTheme: Boolean = false) {
                val playerBackgroundColors = preferences.getEnum(
                    playerBackgroundColorsKey,
                    PlayerBackgroundColors.BlurredCoverColor
                )
                val colorPaletteName =
                    preferences.getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic)
                val isDynamicPalette = colorPaletteName == ColorPaletteName.Dynamic
                val isCoverColor =
                    playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient ||
                            playerBackgroundColors == PlayerBackgroundColors.CoverColor ||
                            animatedGradient == AnimatedGradient.FluidCoverColorGradient

                if (!isDynamicPalette) {
                    return
                }

                // If the URL is null, empty, or the default fallback icon, use violet accent
                if (url.isNullOrEmpty() || url.contains("ic_launcher_box")) {
                    val colorPaletteMode = preferences.getEnum(colorPaletteModeKey, ColorPaletteMode.Dark)
                    val isPicthBlack = colorPaletteMode == ColorPaletteMode.PitchBlack
                    val isDark = colorPaletteMode == ColorPaletteMode.Dark || isPicthBlack || (colorPaletteMode == ColorPaletteMode.System && isSystemInDarkTheme)
                    
                    val violetAccent = Color(0.54509807f, 0.36078432f, 0.9647059f)
                    val defaultColorPalette = dynamicColorPaletteOf(violetAccent, isDark)
                    val targetPalette = if (!isPicthBlack) defaultColorPalette else defaultColorPalette.copy(
                        background0 = Color.Black,
                        background1 = Color.Black,
                        background2 = Color.Black,
                        background3 = Color.Black,
                        background4 = Color.Black,
                    )
                    setSystemBarAppearance(defaultColorPalette.isDark)
                    val oldPalette = appearance.colorPalette
                    if (oldPalette == targetPalette) return
                    // Single global mutation (the perceived fade happens locally
                    // in the player scope, see PaletteFade) — mutating the
                    // global palette N times per track invalidated the whole UI
                    updateAppearance(
                        appearance.copy(
                            colorPalette = targetPalette,
                            typography = appearance.typography.withColor(targetPalette.text)
                        ),
                        animateTheme
                    )
                    return
                }

                val colorPaletteMode =
                    preferences.getEnum(colorPaletteModeKey, ColorPaletteMode.Dark)
                paletteJob.value?.cancel()
                paletteJob.value = coroutineScope.launch(NzikDispatchers.DATA) {
                    try {
                        val bitmap: Bitmap? = ImageCacheFactory.loadBitmap(url, allowHardware = false)

                        
                        val isPicthBlack = colorPaletteMode == ColorPaletteMode.PitchBlack
                        val isDark =
                            colorPaletteMode == ColorPaletteMode.Dark || isPicthBlack || (colorPaletteMode == ColorPaletteMode.System && isSystemInDarkTheme)

                        if (bitmap != null) {
                            val paletteResult = m3eDynamicColorPaletteOf(bitmap, isDark)
                            if (paletteResult != null) {
                                val finalPalette = if (!isPicthBlack) paletteResult else paletteResult.copy(
                                    isDark = true,
                                    background0 = Color.Black,
                                    background1 = Color.Black,
                                    background2 = Color.Black,
                                    background3 = Color.Black,
                                    background4 = Color.Black,
                                    text = Color.White
                                )
                                val oldPalette = appearance.colorPalette
                                if (oldPalette == finalPalette) {
                                    savePaletteForWidget(finalPalette)
                                    return@launch
                                }
                                withContext(NzikDispatchers.UI) {
                                    setSystemBarAppearance(finalPalette.isDark)
                                    updateAppearance(
                                        appearance.copy(
                                            colorPalette = finalPalette,
                                            typography = appearance.typography.withColor(finalPalette.text)
                                        ),
                                        animateTheme
                                    )
                                    savePaletteForWidget(finalPalette)
                                }
                            } else {
                                val defaultColorPalette = dynamicColorPaletteOf(Color(0.54509807f, 0.36078432f, 0.9647059f), isDark)
                                val targetPalette = if (!isPicthBlack) defaultColorPalette else defaultColorPalette.copy(
                                    background0 = Color.Black, background1 = Color.Black,
                                    background2 = Color.Black, background3 = Color.Black, background4 = Color.Black,
                                )
                                withContext(NzikDispatchers.UI) {
                                    setSystemBarAppearance(defaultColorPalette.isDark)
                                    updateAppearance(
                                        appearance.copy(
                                            colorPalette = targetPalette,
                                            typography = appearance.typography.withColor(targetPalette.text)
                                        ),
                                        animateTheme
                                    )
                                }
                            }
                        } else {
                            val violetAccent = Color(0.54509807f, 0.36078432f, 0.9647059f)
                            val defaultColorPalette = dynamicColorPaletteOf(violetAccent, isDark)
                            val targetPalette = if (!isPicthBlack) defaultColorPalette else defaultColorPalette.copy(
                                background0 = Color.Black,
                                background1 = Color.Black,
                                background2 = Color.Black,
                                background3 = Color.Black,
                                background4 = Color.Black,
                            )
                            withContext(NzikDispatchers.UI) {
                                setSystemBarAppearance(defaultColorPalette.isDark)
                                updateAppearance(
                                    appearance.copy(
                                        colorPalette = targetPalette,
                                        typography = appearance.typography.withColor(targetPalette.text)
                                    ),
                                    animateTheme
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Timber.tag("MainActivity").e(e, "Error loading appearance")
                    }
                }
            }

            var hasInitializedAppearance by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(isSystemInDarkTheme) {
                if (!hasInitializedAppearance) {
                    hasInitializedAppearance = true
                    val computed = computeAppearance()
                    if (appearance.colorPalette != computed.colorPalette) {
                        appearance = computed
                    }
                    return@LaunchedEffect
                }
                val colorPaletteName = preferences.getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic)
                if (colorPaletteName == ColorPaletteName.Dynamic) {
                    setDynamicPalette(
                        binder?.player?.currentMediaItem?.mediaMetadata?.artworkUri?.thumbnail(1000)?.toString(),
                        animateTheme = true
                    )
                } else {
                    val computed = computeAppearance()
                    if (appearance.colorPalette != computed.colorPalette) {
                        updateAppearance(computed, animateGlobal = true)
                    }
                }
            }

            DisposableEffect(binder) {
                /*
            var bitmapListenerJob: Job? = null

            fun setDynamicPalette(colorPaletteMode: ColorPaletteMode) {
                val isDark =
                    colorPaletteMode == ColorPaletteMode.Dark || (colorPaletteMode == ColorPaletteMode.System && isSystemInDarkTheme)
                val isPicthBlack = colorPaletteMode == ColorPaletteMode.PitchBlack

                binder?.setBitmapListener { bitmap: Bitmap? ->
                    if (bitmap == null) {
                        val colorPalette =
                            colorPaletteOf(
                                ColorPaletteName.Dynamic,
                                colorPaletteMode,
                                isSystemInDarkTheme
                            )

                        setSystemBarAppearance(colorPalette.isDark)

                        appearance = appearance.copy(
                            colorPalette = colorPalette,
                            typography = appearance.typography.copy(colorPalette.text)
                        )

                        return@setBitmapListener
                    }

                    bitmapListenerJob = coroutineScope.launch(NzikDispatchers.DATA) {
                        dynamicColorPaletteOf(bitmap, isDark, isPicthBlack)?.let {
                            withContext(NzikDispatchers.UI) {
                                setSystemBarAppearance(it.isDark)
                            }
                            appearance = appearance.copy(
                                colorPalette = it,
                                typography = appearance.typography.copy(it.text)
                            )
                        }
                    }
                }
            }
            */

                val listener =
                    SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                        when (key) {

                            languageAppKey -> {
                                // AppCompat recreates the activity to apply the new locale (locale is not in the
                                // activity-level configChanges), so the language switches live without a restart.
                                val lang = sharedPreferences.getEnum( languageAppKey, Languages.System )
                                if (shouldRecreateActivity(Database.isClosed)) {
                                    AppCompatDelegate.setApplicationLocales( localeListForAppLanguage( lang ) )
                                    Timber.tag("MainActivity").d("Language changed: $lang")
                                } else {
                                    Timber.tag("MainActivity").w("Language changed: $lang — locale apply skipped, database closed")
                                }
                            }

                            effectRotationKey, playerThumbnailSizeKey,
                            playerVisualizerTypeKey,
                            UiTypeKey,
                            disablePlayerHorizontalSwipeKey,
                            disableClosingPlayerSwipingDownKey,
                            showSearchTabKey,
                            navigationBarPositionKey,
                            navigationBarTypeKey,
                            showTotalTimeQueueKey,
                            backgroundProgressKey,
                            transitionEffectKey,
                            playerBackgroundColorsKey,
                            miniPlayerTypeKey,
                            hideStatusBarKey,
                            restartActivityKey
                                -> {
                                if (shouldRecreateActivity(Database.isClosed)) {
                                    this@MainActivity.recreate()
                                    Timber.tag("MainActivity").d("recreate()")
                                } else {
                                    Timber.tag("MainActivity").w("recreate() skipped for $key: database closed")
                                }
                            }

                            isProxyEnabledKey, proxyHostnameKey, proxyPortKey, proxyModeKey -> {
                                val isProxyEnabled = sharedPreferences.getBoolean(isProxyEnabledKey, false)
                                var proxy: Proxy? = null
                                
                                if (isProxyEnabled) {
                                    val hostName = sharedPreferences.getString(proxyHostnameKey, null)
                                    val proxyPort = sharedPreferences.getInt(proxyPortKey, 8080)
                                    val proxyMode = sharedPreferences.getEnum(proxyModeKey, Proxy.Type.HTTP)
                                    if (isValidIP(hostName)) {
                                        hostName?.let { hName ->
                                            ProxyPreferences.preference = ProxyPreferenceItem(hName, proxyPort, proxyMode)
                                            proxy = ProxyPreferences.preference?.let { pref -> it.fast4x.innertube.utils.getProxy(pref) }
                                        }
                                    } else {
                                        Toaster.e(R.string.invalid_proxy_hostname)
                                    }
                                } else {
                                    ProxyPreferences.preference = null
                                }
                                
                                NetworkClientFactory.configure(
                                    proxy = proxy,
                                    cacheDir = this@MainActivity.externalCacheDir ?: this@MainActivity.cacheDir
                                )
                                Innertube.proxy = proxy
                            }

                            colorPaletteNameKey, colorPaletteModeKey, customColorKey,
                            customThemeLight_Background0Key,
                            customThemeLight_Background1Key,
                            customThemeLight_Background2Key,
                            customThemeLight_Background3Key,
                            customThemeLight_Background4Key,
                            customThemeLight_TextKey,
                            customThemeLight_textSecondaryKey,
                            customThemeLight_textDisabledKey,
                            customThemeLight_iconButtonPlayerKey,
                            customThemeLight_accentKey,
                            customThemeDark_Background0Key,
                            customThemeDark_Background1Key,
                            customThemeDark_Background2Key,
                            customThemeDark_Background3Key,
                            customThemeDark_Background4Key,
                            customThemeDark_TextKey,
                            customThemeDark_textSecondaryKey,
                            customThemeDark_textDisabledKey,
                            customThemeDark_iconButtonPlayerKey,
                            customThemeDark_accentKey,
                                -> {
                                val colorPaletteName =
                                    sharedPreferences.getEnum(
                                        colorPaletteNameKey,
                                        ColorPaletteName.Dynamic
                                    )

                                val colorPaletteMode =
                                    sharedPreferences.getEnum(
                                        colorPaletteModeKey,
                                        ColorPaletteMode.System
                                    )

                                val newIsDark = colorPaletteMode == ColorPaletteMode.Dark ||
                                        colorPaletteMode == ColorPaletteMode.PitchBlack ||
                                        (colorPaletteMode == ColorPaletteMode.System && isSystemInDarkTheme)
                                val newIsPitchBlack = colorPaletteMode == ColorPaletteMode.PitchBlack

                                var colorPalette = colorPaletteOf(
                                    colorPaletteName,
                                    colorPaletteMode,
                                    isSystemInDarkTheme
                                )

                                if (colorPaletteName == ColorPaletteName.Dynamic) {
                                    // Always call setDynamicPalette when switching to the dynamic theme
                                    val currentArtworkUri = binder?.player?.currentMediaItem?.mediaMetadata?.artworkUri?.thumbnail(1000)?.toString()
                                    setDynamicPalette(currentArtworkUri, animateTheme = true)
                                } else {
                                    //bitmapListenerJob?.cancel()
                                    //binder?.setBitmapListener(null)

                                    if (colorPaletteName == ColorPaletteName.MaterialYou) {
                                        colorPalette = dynamicColorPaletteOf(
                                            Color(monet.getAccentColor(this@MainActivity)),
                                            newIsDark
                                        )
                                    }

                                    if (colorPaletteName == ColorPaletteName.Customized) {
                                        colorPalette = customColorPalette(
                                            colorPalette,
                                            this@MainActivity,
                                            isSystemInDarkTheme
                                        )
                                    }
                                    if (colorPaletteName == ColorPaletteName.CustomColor) {
                                        val newCustomColor = sharedPreferences.getInt(customColorKey, Color.Green.hashCode())
                                        colorPalette = dynamicColorPaletteOf(
                                            Color(newCustomColor),
                                            newIsDark
                                        )
                                    }

                                    setSystemBarAppearance(colorPalette.isDark)

                                    updateAppearance(
                                        appearance.copy(
                                            colorPalette = if (!newIsPitchBlack) colorPalette else colorPalette.copy(
                                                background0 = Color.Black,
                                                background1 = Color.Black,
                                                background2 = Color.Black,
                                                background3 = Color.Black,
                                                background4 = Color.Black,
                                                text = Color.White
                                            ),
                                            typography = appearance.typography.withColor(if (!newIsPitchBlack) colorPalette.text else Color.White),
                                        ),
                                        animateGlobal = true
                                    )
                                }
                            }

                            thumbnailRoundnessDpKey -> {
                                val thumbnailRoundnessDp =
                                    sharedPreferences.getFloat(thumbnailRoundnessDpKey, 12f)

                                appearance = appearance.copy(
                                    thumbnailShape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f))
                                )
                            }
                            
                            artistThumbnailRoundnessDpKey -> {
                                val artistThumbnailRoundnessDp =
                                    sharedPreferences.getFloat(artistThumbnailRoundnessDpKey, 48f)

                                appearance = appearance.copy(
                                    artistThumbnailShape = if (artistThumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(artistThumbnailRoundnessDp.dp, 0.25f))
                                )
                            }

                            "uiRoundnessDpKey" -> {
                                val uiRoundnessDp = sharedPreferences.getFloat(key, 25f)
                                appearance = appearance.copy(
                                    uiRoundnessShape = RoundedCornerShape(BoundedCornerSize(uiRoundnessDp.dp, 0.4f))
                                )
                            }

                            useSystemFontKey, applyFontPaddingKey, fontTypeKey -> {
                                val useSystemFont =
                                    sharedPreferences.getBoolean(useSystemFontKey, false)
                                val applyFontPadding =
                                    sharedPreferences.getBoolean(applyFontPaddingKey, false)
                                val fontType =
                                    sharedPreferences.getEnum(fontTypeKey, FontType.Rubik)

                                appearance = appearance.copy(
                                    typography = typographyOf(
                                        appearance.colorPalette.text,
                                        useSystemFont,
                                        applyFontPadding,
                                        fontType
                                    ),
                                )
                            }
                        }
                    }

                with(preferences) {
                    registerOnSharedPreferenceChangeListener(listener)

                    val colorPaletteName =
                        getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic)
                    if (colorPaletteName == ColorPaletteName.Dynamic) {
                        val currentArtworkUri = binder?.player?.currentMediaItem?.mediaMetadata?.artworkUri?.thumbnail(1000)?.toString()
                        setDynamicPalette(currentArtworkUri)
                    }

                    onDispose {
                        //bitmapListenerJob?.cancel()
                        //binder?.setBitmapListener(null)
                        unregisterOnSharedPreferenceChangeListener(listener)
                    }
                }
            }

            val rippleConfiguration =
                remember(appearance.colorPalette.text, appearance.colorPalette.isDark) {
                    RippleConfiguration(color = appearance.colorPalette.text)
                }

            val shimmerTheme = remember {
                defaultShimmerTheme.copy(
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 800,
                            easing = LinearEasing,
                            delayMillis = 250,
                        ),
                        repeatMode = RepeatMode.Restart
                    ),
                    shaderColors = listOf(
                        Color.Unspecified.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.50f),
                        Color.Unspecified.copy(alpha = 0.25f),
                    ),
                )
            }

            LaunchedEffect(Unit) {
                val colorPaletteName =
                    preferences.getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic)
                if (colorPaletteName == ColorPaletteName.Customized) {
                    val customPalette = customColorPalette(
                        appearance.colorPalette,
                        this@MainActivity,
                        isSystemInDarkTheme
                    )
                    updateAppearance(
                        appearance.copy(
                            colorPalette = customPalette,
                            typography = appearance.typography.withColor(customPalette.text)
                        )
                    )
                }
            }


            // Using appearance directly, Pitch Black is managed in setDynamicPalette
            val finalAppearance = appearance

            SideEffect {
                setSystemBarAppearance(finalAppearance.colorPalette.isDark)
            }

            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            // Phone in landscape on the Songs/Album/Artist/Library tabs: app header and nav bar
            // are hidden until the toggle button reveals them, so scroll-hide does not apply there
            val isLandscapeBarless = app.it.fast4x.rimusic.utils.isLandscapeBarlessScreen()
            val areBarsHidden = app.it.fast4x.rimusic.utils.hideBarsInLandscapeMobile()

            // Every time such a screen is entered (or left) the bars start put away again
            LaunchedEffect(isLandscapeBarless) {
                app.it.fast4x.rimusic.utils.LandscapeBars.hide()
            }
            val uiType by rememberPreference(UiTypeKey, UiType.RiMusic)
            val isViMusic = uiType == UiType.ViMusic
            // A top nav bar leaves with the header, so the scroll-hide travels that much further
            val topNavBarPx = if (NavigationBarPosition.Top.isCurrent()) {
                with(LocalDensity.current) { TOP_NAV_BAR_HEIGHT.roundToPx() }
            } else 0

            val bottomBarHeightPx = with(LocalDensity.current) { 240.dp.roundToPx().toFloat() } // Enough to hide floating bar + miniplayer
            var isBarsVisible by remember { mutableStateOf(true) }
            var topBarOffset by remember { mutableFloatStateOf(0f) }
            var bottomBarOffset by remember { mutableFloatStateOf(0f) }
            val offsetAnimationJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

            // Scroll-hide guard input: whether the full player sheet is currently on screen.
            // Declared before nestedScrollConnection (which reads it live at event time);
            // synced reactively once playerSheetState exists further down in composition.
            val isPlayerSheetExpanded = remember { mutableStateOf(false) }

            // Opening the player must restore the hidden bars: while the player is open
            // the scroll-hide re-show is disabled (showPlayer guard), so hidden bars would
            // otherwise stay hidden forever and the player container would keep its offset,
            // leaving an unrecoverable UI state (player mis-positioned, no nav bars).
            fun restoreHiddenBars() {
                if (topBarOffset == 0f && bottomBarOffset == 0f) return
                isBarsVisible = true
                offsetAnimationJob.value?.cancel()
                offsetAnimationJob.value = coroutineScope.launch {
                    // 800ms (user-tuned): the bars finish settling exactly when the auto-expansion
                    // starts (MINIPLAYER_AUTOEXPAND_DELAY_MS), so restore + deploy read as one
                    // continuous motion instead of two separate jumps.
                    launch { androidx.compose.animation.core.Animatable(topBarOffset).animateTo(0f, androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { topBarOffset = value } }
                    launch { androidx.compose.animation.core.Animatable(bottomBarOffset).animateTo(0f, androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { bottomBarOffset = value } }
                }
                Timber.tag("MainActivity").d("restoreHiddenBars: restoring hidden bars before player auto-launch")
            }

            val density = LocalDensity.current
            val safeDrawingInsets = WindowInsets.safeDrawing

            val currentRoute by app.n_zik.android.extensions.discord.DiscordUiState.currentRoute.collectAsStateWithLifecycle()
            
            // Listen Together scrolls its cards list, so it joins the scroll-hide routes:
            // the header and the floating bar / mini-player slide on scroll there too.
            val isScrollableRoute = currentRoute == "home" ||
                    currentRoute?.startsWith("artist") == true ||
                    currentRoute?.startsWith("album") == true ||
                    currentRoute?.startsWith("playlist") == true ||
                    currentRoute?.startsWith("localPlaylist") == true ||
                    currentRoute?.startsWith("searchResults") == true ||
                    currentRoute?.startsWith("settings") == true ||
                    currentRoute == "listenTogether"
                    
            LaunchedEffect(isLandscape, isLandscapeBarless, isViMusic, isScrollableRoute, density, safeDrawingInsets) {
                topBarOffset = 0f
                bottomBarOffset = 0f

                isBarsVisible = true
            }

            val nestedScrollConnection = remember(isLandscape, isLandscapeBarless, isViMusic, topNavBarPx, isScrollableRoute, density, safeDrawingInsets, showQueueOverlay) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        // Disable scroll-hide while the full player sheet is on screen or the queue is open
                        if (isPlayerSheetExpanded.value || showQueueOverlay) return Offset.Zero
                        // Bars here are driven by the toggle button, not by scrolling: a slide only
                        // puts them away (nothing scroll-related brings them back) and the scroll
                        // itself must stay whole for the list
                        if (isLandscapeBarless) {
                            if (available.y != 0f && source == NestedScrollSource.UserInput) {
                                app.it.fast4x.rimusic.utils.LandscapeBars.hide()
                            }
                            return Offset.Zero
                        }

                        val shouldHideOnScroll = isLandscape || isScrollableRoute

                        if (!shouldHideOnScroll || isViMusic) return Offset.Zero

                        val statusBarsTopPx = safeDrawingInsets.getTop(density)
                        val topBarHeightPx = with(density) { 64.dp.roundToPx() } + statusBarsTopPx + topNavBarPx

                        val delta = available.y
                        if (delta == 0f) return Offset.Zero

                        // Cancel ongoing fling animation when user touches again
                        offsetAnimationJob.value?.cancel()
                        offsetAnimationJob.value = null

                        // Move bars with finger in both directions synchronously
                        val previousTopOffset = topBarOffset
                        topBarOffset = (topBarOffset + delta).coerceIn(-topBarHeightPx.toFloat(), 0f)
                        val consumedY = topBarOffset - previousTopOffset

                        bottomBarOffset = (bottomBarOffset - delta).coerceIn(0f, bottomBarHeightPx)

                        return Offset(0f, consumedY)
                    }

                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        return Offset.Zero
                    }

                    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                        // Disable scroll-hide while the full player sheet is on screen or the queue is open
                        if (isPlayerSheetExpanded.value || showQueueOverlay || isLandscapeBarless) return super.onPostFling(consumed, available)

                        val statusBarsTopPx = safeDrawingInsets.getTop(density)
                        val topBarHeightPx = with(density) { 64.dp.roundToPx() } + statusBarsTopPx + topNavBarPx

                        val currentTopOffset = topBarOffset
                        val threshold = -topBarHeightPx / 2f

                        offsetAnimationJob.value?.cancel()
                        offsetAnimationJob.value = coroutineScope.launch {
                            if (currentTopOffset < threshold) {
                                launch { androidx.compose.animation.core.Animatable(topBarOffset).animateTo(-topBarHeightPx.toFloat(),androidx.compose.animation.core.tween(150, easing = androidx.compose.animation.core.LinearEasing)) { topBarOffset = value } }
                                launch { androidx.compose.animation.core.Animatable(bottomBarOffset).animateTo(bottomBarHeightPx, androidx.compose.animation.core.tween(150, easing = androidx.compose.animation.core.LinearEasing)) { bottomBarOffset = value } }
                                isBarsVisible = false
                            } else {
                                launch { androidx.compose.animation.core.Animatable(topBarOffset).animateTo(0f, androidx.compose.animation.core.tween(150, easing = androidx.compose.animation.core.LinearEasing)) { topBarOffset = value } }
                                launch { androidx.compose.animation.core.Animatable(bottomBarOffset).animateTo(0f, androidx.compose.animation.core.tween(150, easing = androidx.compose.animation.core.LinearEasing)) { bottomBarOffset = value } }
                                isBarsVisible = true
                            }
                        }

                        return super.onPostFling(consumed, available)
                    }
                }
            }

            val topBarOffsetState = derivedStateOf { topBarOffset }
            val bottomBarOffsetState = derivedStateOf { bottomBarOffset }

            AnimatedAppearance(
                target = appearance,
                fadeFrom = fadeFromAppearance,
                onFadeComplete = { fadeFromAppearance = null }
            ) { _ ->
                val rootBackgroundColor by animateColorAsState(
                    targetValue = appearance.colorPalette.background0,
                    animationSpec = tween(350, easing = FastOutSlowInEasing),
                    label = "rootBackground"
                )
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(rootBackgroundColor)
                        .nestedScroll(nestedScrollConnection)
                ) {


                val density = LocalDensity.current
                val windowsInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                val bottomDp = with(density) { windowsInsets.getBottom(density).toDp() }

                // Calculate player padding before initializing playerSheetState
                val isFloatingNavBar = NavigationBarPosition.BottomFloating.isCurrent()
                val isIconOnlyNav = app.it.fast4x.rimusic.enums.NavigationBarType.IconOnly.isCurrent()
                val navBarBottomPad = Dimensions.navBarBottomPadding(isFloatingNavBar)
                // Route-aware: only screens that actually render the app nav bar reserve its
                // height under the mini-player, so it drops to the screen edge on bar-less pages.
                // Sourced from the nav controller rather than DiscordUiState so layout never
                // depends on the RPC feature's destination listener
                val playerRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                // Home draws one nav bar button per active tab and only shows the bar with two
                // or more, so pass the real tab count: the same preferences HomeScreen reads,
                // kept reactive so a tab toggled in settings moves the mini-player live.
                val quickPicksTabEnabled by rememberPreference(enableQuickPicksPageKey, true)
                val songsTabEnabled by rememberPreference("hometab_songs_enabled", true)
                val artistsTabEnabled by rememberPreference("hometab_artists_enabled", true)
                val albumsTabEnabled by rememberPreference("hometab_albums_enabled", true)
                val playlistsTabEnabled by rememberPreference("hometab_playlists_enabled", true)
                val homeTabsOrderSerialized by rememberPreference(homeTabsOrderKey, "")
                val activeHomeTabCount = remember(homeTabsOrderSerialized, quickPicksTabEnabled, songsTabEnabled, artistsTabEnabled, albumsTabEnabled, playlistsTabEnabled) {
                    activeHomeTabIds(
                        HomeTabsSettingsDialog.parseOrder(homeTabsOrderSerialized),
                        quickPicksTabEnabled,
                        songsTabEnabled,
                        artistsTabEnabled,
                        albumsTabEnabled,
                        playlistsTabEnabled,
                    ).size
                }
                val hasNavBar = !areBarsHidden && appNavBarPresentForRoute(playerRoute, activeHomeTabCount)

                val playerPos by rememberPreference(playerPositionKey, PlayerPosition.Bottom)
                val targetPlayerPadBottom = if (playerPos == PlayerPosition.Bottom) {
                    if (isFloatingNavBar) {
                        if (hasNavBar) {
                            val barHeight = if (isIconOnlyNav) Dimensions.floatingNavBarIconOnlyHeight else Dimensions.floatingNavBarHeight
                            barHeight + navBarBottomPad + 4.dp
                        } else {
                            navBarBottomPad
                        }
                    } else {
                        if (hasNavBar && NavigationBarPosition.Bottom.isCurrent()) {
                            Dimensions.standardNavBarHeight + navBarBottomPad + 4.dp
                        } else {
                            navBarBottomPad + 5.dp
                        }
                    }
                } else 5.dp
                // Follows the nav bar sliding in/out so the mini player glides with it instead of
                // jumping. Kept as a State and only read at draw time (see CustomBottomSheet): reading
                // it here would recompose this whole screen on every frame of the slide.
                val playerPadBottom = androidx.compose.animation.core.animateDpAsState(
                    targetValue = targetPlayerPadBottom,
                    animationSpec = tween(
                        app.it.fast4x.rimusic.utils.LANDSCAPE_BARS_ANIMATION_MS,
                        easing = FastOutSlowInEasing
                    ),
                    label = "playerPadBottom"
                )

                // Top anchor: the mini-player sits under the app header (and the top nav bar).
                // The provider is read at draw time so the scroll-hide offset moves it without
                // recomposing this screen on every frame.
                val isTopPlayer = playerPos == PlayerPosition.Top
                val statusBarTopPx = safeDrawingInsets.getTop(density)
                val playerTopInsetPx = with(density) {
                    miniPlayerTopInset(
                        statusBarTop = statusBarTopPx.toDp(),
                        barsHidden = areBarsHidden,
                        hasTopNavBar = NavigationBarPosition.Top.isCurrent(),
                    ).toPx()
                }
                val collapsedPlayerHeight = Dimensions.collapsedPlayer
                // Side system bars (status bar at the left, nav bar at the right in landscape,
                // cutout) and a navigation rail on a side: the collapsed mini-player narrows to
                // stay clear of them. Both are put away with the other bars on the bar-less
                // landscape screens.
                val railWidth = if (areBarsHidden) 0.dp else Dimensions.navigationRailWidth
                val playerStartInset = miniPlayerSideInset(
                    railWidth = if (NavigationBarPosition.Left.isCurrent()) railWidth else 0.dp,
                    safeInset = with(density) { safeDrawingInsets.getLeft(density, LayoutDirection.Ltr).toDp() },
                )
                val playerEndInset = miniPlayerSideInset(
                    railWidth = if (NavigationBarPosition.Right.isCurrent()) railWidth else 0.dp,
                    safeInset = with(density) { safeDrawingInsets.getRight(density, LayoutDirection.Ltr).toDp() },
                )
                // Same travel as the header's scroll-hide (64dp bar + status bar + top nav bar)
                val headerHideRangePx = with(density) { APP_HEADER_HEIGHT.toPx() } + statusBarTopPx + topNavBarPx
                val playerTopPadding: (() -> Dp)? = if (isTopPlayer) {
                    {
                        with(density) {
                            miniPlayerTopPaddingPx(
                                insetPx = playerTopInsetPx,
                                scrollOffsetPx = topBarOffsetState.value,
                                hideRangePx = headerHideRangePx,
                                collapsedHeightPx = collapsedPlayerHeight.toPx(),
                            ).toDp()
                        }
                    }
                } else null

                val playerSheetState = rememberPlayerSheetState(
                    dismissedBound = 0.dp,
                    collapsedBound = Dimensions.collapsedPlayer + bottomDp,
                    expandedBound = maxHeight,
                )

                // Keep the scroll-hide guard in sync with the sheet's real on-screen state.
                // showPlayer alone is not enough: it stays true after a back-collapsed
                // player, which would permanently block re-hiding the bars while music plays.
                LaunchedEffect(playerSheetState) {
                    snapshotFlow { playerSheetState.progress > 0.5f }
                        .distinctUntilChanged()
                        .collect { isPlayerSheetExpanded.value = it }
                }

                // NOT keyed on playerSheetState.value: that changes every drag frame,
                // which used to recreate the derived state and re-provide
                // LocalPlayerAwareWindowInsets per frame, recomposing every screen
                // that consumes the insets while the mini-player is dragged.
                // The tracker returns the same WindowInsets instance while the
                // clamped bottom is stable, so no consumer is invalidated per frame.
                val playerAwareWindowInsets by remember(bottomDp, playerSheetState.collapsedBound) {
                    val insetsTracker = PlayerAwareInsetsTracker(
                        baseInsets = windowsInsets.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                        ),
                        lowerBound = bottomDp,
                        upperBound = playerSheetState.collapsedBound,
                    )
                    derivedStateOf { insetsTracker.resolve(playerSheetState.value) }
                }

                val openTabFromShortcut = when (shortcutIntentAction) {
                    action_songs -> HomeScreenTabs.Songs.index
                    action_albums -> HomeScreenTabs.Albums.index
                    actions_artists -> HomeScreenTabs.Artists.index
                    action_library -> HomeScreenTabs.Playlists.index
                    action_search -> OPEN_SEARCH_SHORTCUT
                    else -> -1
                }
                // Consuming (resetting) shortcutIntentAction synchronously during composition
                // raced with HomeScreen's own LaunchedEffect(openTabFromShortcut): both the -1->X
                // and the immediate X->-1 recompositions could settle before that effect ever got
                // to run, silently dropping the navigation (verified on-device). Resetting from an
                // effect instead -- same pattern as `intentUriData` below -- lets every consumer
                // downstream observe the value for this composition pass before it's cleared.
                //
                // A shortcut tapped while some other screen (search, an album, ...) is on top of
                // the back stack never reached HomeScreen at all -- it isn't part of the
                // composition while a different destination is active, so its shortcut-handling
                // effects never ran (verified on-device: a tab shortcut tapped from the search
                // screen did nothing). Popping back to home first, and only clearing the sentinel
                // once we've actually arrived there, gives HomeScreen a real chance to observe it.
                val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                LaunchedEffect(shortcutIntentAction, currentRoute) {
                    if (shortcutIntentAction != null) {
                        if (currentRoute?.startsWith(NavRoutes.home.name) == true) {
                            shortcutIntentAction = null
                        } else {
                            navController.popBackStack(NavRoutes.home.name, inclusive = false)
                        }
                    }
                }

                // Rewind reminder notification: open the deck on the finished month (monthly)
                // or the yearly deck on the finished year (yearly, month = 0 sentinel — extras
                // set in onCreate / onNewIntent by consumeRewindDeepLinks). Consumed from an
                // effect, like the shortcut above, so the navigation happens once the graph
                // is composed. The gate is part of the key: while onboarding is up the
                // NavHost is not composed (empty graph — navigating would crash), so the
                // target is held until the flow completes and the effect re-runs.
                LaunchedEffect(rewindDeckTarget, onboardingStep == null) {
                    if (onboardingStep != null) return@LaunchedEffect
                    rewindDeckTarget?.let { (year, month) ->
                        // rewindDeckRoute omits the month argument for the yearly target:
                        // the route's default month=-1 maps to rewindMonth = null in
                        // RewindScreen, i.e. the year-only deck
                        navController.navigate(rewindDeckRoute(year, month))
                        rewindDeckTarget = null
                    }
                }

                // Rewind playlist-ready notification: open the generated 'Rewind — <period>'
                // playlist directly (spec GH-275 follow-up — renegotiated: the playlist
                // notifications deep-link, they are no longer a bare app open). Consumed from
                // an effect, like the deck above: the navigation happens once the graph is
                // composed, and the onboarding gate holds the target until the flow completes.
                LaunchedEffect(rewindPlaylistTarget, onboardingStep == null) {
                    if (onboardingStep != null) return@LaunchedEffect
                    rewindPlaylistTarget?.let { playlistId ->
                        navController.navigate("${NavRoutes.localPlaylist.name}/$playlistId")
                        rewindPlaylistTarget = null
                    }
                }

                // "Now playing" notification tap — WARM start (spec-notification-click-opens-player):
                // present the mini-player (if the sheet is dismissed) then deploy the full
                // player, restoring the hidden bars first — the same pacing as the existing
                // launchedFromNotification path (presentMiniplayerThenExpand). Consumed from an
                // effect, like the rewind targets above, and gated on onboarding the same way:
                // while onboarding is up the NavHost is not composed, so the target is held
                // until the flow completes and the effect re-runs. The token was already
                // consumed off the intent in onNewIntent (one-shot contract).
                LaunchedEffect(openPlayerFromNotificationWarm, onboardingStep == null) {
                    if (onboardingStep != null) return@LaunchedEffect
                    if (openPlayerFromNotificationWarm) {
                        openPlayerFromNotificationWarm = false
                        showPlayer = true
                        // Anti-flicker: a sheet already above the expanded bound is left alone
                        // (a tap while the full player is open must not cause a visible
                        // collapse→expand).
                        if (playerSheetState.value < playerSheetState.expandedBound) {
                            // On the composable's rememberCoroutineScope(), NOT this effect's
                            // scope: the flag reset above changes the effect key → recomposition
                            // cancels the effect scope, which would kill the delayed
                            // launch { delay(800); expandSoft() } child before it runs
                            // (device bug: the mini bar appeared, the expansion never did).
                            coroutineScope.presentMiniplayerThenExpand(playerSheetState, onPresent = { restoreHiddenBars() })
                        }
                    }
                }

                // Generic « Profiles » launcher shortcut (spec-profile-shortcuts): open the
                // profiles page once the graph is composed (cold: after the normal start).
                // Consumed from an effect, like the rewind targets above, and gated on
                // onboarding the same way: while onboarding is up the NavHost is not
                // composed, so the target is held until the flow completes and the effect
                // re-runs. The direct per-profile shortcut does NOT navigate — it only
                // launches in (cold) or switches to (warm) its profile.
                LaunchedEffect(openProfilesShortcut, onboardingStep == null) {
                    if (onboardingStep != null) return@LaunchedEffect
                    if (openProfilesShortcut) {
                        navController.navigate(NavRoutes.profiles.name)
                        openProfilesShortcut = false
                    }
                }

                        CrossfadeContainer(state = pipState.value) { isCurrentInPip ->
                            Timber.tag("MainActivity").d("pipState ${pipState.value} CrossfadeContainer isCurrentInPip $isCurrentInPip ")
                            val pipModule by rememberPreference(pipModuleKey, PipModule.Cover)
                    if (isCurrentInPip) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Transparent)
                        ) {
                            when (pipModule) {
                                PipModule.Cover -> {
                                    PipModuleContainer {
                                        PipModuleCover(
                                            url = binder?.player?.currentMediaItem?.mediaMetadata?.artworkUri
                                                ?.toString()
                                                ?.resize(1000, 1000)
                                                .orEmpty()
                                        )
                                    }
                                }

                            }

                        }

                    } else
                             CompositionLocalProvider(
                             LocalIndication provides ripple(bounded = true),
                            LocalRippleConfiguration provides rippleConfiguration,
                            LocalShimmerTheme provides shimmerTheme,
                            LocalPlayerServiceBinder provides binder,
                            LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                            LocalLayoutDirection provides LayoutDirection.Ltr,
                            LocalDownloadHelper provides downloadHelper,
                            LocalPlayerSheetState provides playerSheetState,
                            LocalMonetCompat provides monet,
                            LocalPersistMap provides persistMap,
                            LocalPendingMiniPlayerAction provides pendingMiniPlayerAction,
                            LocalIsShowingLyrics provides isShowingLyrics,
                            LocalIsShowingVisualizer provides isShowingVisualizer,
                            LocalTopBarOffset provides topBarOffsetState,
                            LocalBottomBarOffset provides bottomBarOffsetState
                            //LocalInternetConnected provides internetConnected
                        ) {
                            // First-launch onboarding: rendered instead of the main
                            // navigation while the flag is false. Page changes (permissions ->
                            // restore -> accounts -> profile -> main app) animate with the user's chosen
                            // transition effect, same spec as AppNavigation
                            val transitionEffect by rememberPreference(transitionEffectKey, TransitionEffect.Fade)
                            AnimatedContent(
                                targetState = onboardingStep,
                                transitionSpec = {
                                    when (transitionEffect) {
                                        TransitionEffect.None ->
                                            EnterTransition.None togetherWith ExitTransition.None

                                        TransitionEffect.Expand ->
                                            scaleIn(animationSpec = tween(350), initialScale = 2.0f) togetherWith
                                            scaleOut(animationSpec = tween(350), targetScale = 2.0f)

                                        TransitionEffect.Fade ->
                                            fadeIn(animationSpec = tween(350)) togetherWith
                                            fadeOut(animationSpec = tween(350))

                                        TransitionEffect.Scale ->
                                            scaleIn(animationSpec = tween(350)) togetherWith
                                            scaleOut(animationSpec = tween(350))

                                        TransitionEffect.SlideVertical ->
                                            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up) togetherWith
                                            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Up)

                                        TransitionEffect.SlideHorizontal ->
                                            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left) togetherWith
                                            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left)
                                    }
                                },
                                label = "onboardingPhase"
                            ) { phase ->
                                when (phase) {
                                    null -> AppNavigation(
                                        navController = navController,
                                        miniPlayer = {},
                                        openTabFromShortcut = openTabFromShortcut
                                    )

                                    // Step transitions, the step persistence and the flag timing
                                    // are all handled by advanceOnboarding() + OnboardingStep.advance()
                                    OnboardingStep.PERMISSIONS -> OnboardingScreen(
                                        onComplete = { advanceOnboarding() }
                                    )

                                    OnboardingStep.IMPORT -> OnboardingImportScreen(
                                        // "Skip" and a successful restore both advance to
                                        // the accounts step; a restore additionally restarts
                                        // the app (flag unwritten), so the restart lands
                                        // on the next step — the user stays inside onboarding
                                        onComplete = { advanceOnboarding() }
                                    )

                                    // The accounts step is no longer the last one — the
                                    // profile step follows: leaving (even with a Discord
                                    // token set) advances to NAME, which is persisted
                                    // before any restart, so the restart lands on the
                                    // profile step with the complete flag unwritten
                                    OnboardingStep.ACCOUNTS -> OnboardingAccountsScreen(
                                        onComplete = { advanceOnboarding() }
                                    )

                                    // The profile step: the base clone's identity (name,
                                    // face name source, photo, face avatar source) — the
                                    // last step. The enum value stays NAME (the
                                    // persisted step is the enum name — renaming it would
                                    // restart an in-flight onboarding at PERMISSIONS)
                                    OnboardingStep.NAME -> OnboardingProfileScreen(
                                        onComplete = { advanceOnboarding() }
                                    )
                                }
                            }

                            // The restart prompt (a Discord-token restart from the
                            // accounts step, a post-import restart) must survive the
                            // step transition: it is composed in the onboarding
                            // container, not in the leaving step screen — a
                            // screen-local Render would uncompose with the
                            // AnimatedContent exit (0 ms for TransitionEffect.None)
                            // and the prompt would vanish before the user sees it.
                            // Gated on the flow being alive: once complete, the
                            // settings screen composes its own Render
                            if (onboardingStep != null) RestartAppDialog.Render()

                            val disableClosingPlayerSwipingDown by rememberPreference(disableClosingPlayerSwipingDownKey, false)
                            // Listen Together guest lock (spec-listen-together-guest-lock-hardening,
                            // matrix row MINIPLAYER_DISMISS_GUEST): the dismiss-swipe's onDismiss is
                            // destructive for the room — it clears the queue synced from the host,
                            // stops the radio and stops the player service. While a guest is locked,
                            // the gesture itself is disabled, so the room cannot be "cleaned" from
                            // a guest device.
                            val ltGuestLocked by app.n_zik.android.listentogether.listenTogetherGuestLock
        checkIfAppIsRunningInBackground()

                            // Reactive media-item presence: the sheet (including its
                            // collapsed hit target) must not be composed when the
                            // player has nothing to play, otherwise an invisible
                            // tappable strip stays at the bottom of the screen.
                            val currentMediaId by (binder?.player?.currentMediaItemIdAsState() ?: remember { mutableStateOf<String?>(null) })

                            // Debounced media presence: Listen Together replaces the whole queue on
                            // every track change (setMediaItems), which briefly clears the current
                            // media item. Reacting to that transient null would close the player
                            // sheet on each track change (user-reported), so "no media" is only
                            // accepted once the absence persists.
                            var mediaPresent by remember(binder) {
                                mutableStateOf(binder?.player?.currentMediaItem != null)
                            }

                            // Keyed on the sheet too: a rebuilt sheet (rotation, insets) starts from
                            // its last anchor and must be re-checked against the current media
                            LaunchedEffect(currentMediaId, playerSheetState) {
                                if (currentMediaId == null) {
                                    delay(400)
                                    if (binder?.player?.currentMediaItem != null) return@LaunchedEffect
                                    mediaPresent = false
                                    if (!playerSheetState.isDismissed) {
                                        // Animated dismiss instead of the old instant snap: the sheet
                                        // slides out of the screen while the dismissed-zone alpha fades the
                                        // mini-player, instead of vanishing in place.
                                        playerSheetState.dismiss()
                                    }
                                    showQueueOverlay = false
                                } else {
                                    mediaPresent = true
                                    // After recreate() the player service is not bound yet, so media
                                    // reads as absent and the sheet is dismissed above; bring the
                                    // mini-player back once media returns
                                    playerSheetState.showMiniplayerIfDismissed()
                                }
                            }

                            // Single CustomBottomSheet — only rendered while there's media.
                            // Handles both collapsed (mini-player) and expanded (player) states
                            // in a single composition tree, preventing animation jumps.
                            // Rewind is a full-screen deck: the player sheet (mini-player included) slides
                            // down out of the screen with the same jelly spring as the header, and returns
                            // with the same bounce when leaving the deck
                            val isRewindDeck = currentRoute?.let { route ->
                                route == NavRoutes.rewind.name || route.startsWith("${NavRoutes.rewind.name}?")
                            } ?: false
                            val rewindSheetProgress = animateFloatAsState(
                                targetValue = if (isRewindDeck) 1f else 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "rewindSheetDismiss"
                            )

                            // The sheet subtree stays composed until the animated dismiss has reached
                            // the dismissed bound, so the slide-out is never cut short. The fades come
                            // from the appearance + dismissed-zone alphas below — no AnimatedVisibility:
                            // it would add a second full-screen layer with the default Auto strategy,
                            // i.e. an offscreen buffer of the whole sheet on every frame (lag).
                            if (shouldComposePlayerSheet(mediaPresent, playerSheetState.isDismissed)) {
                                // Appearance fade, replayed each time the sheet leaves the dismissed
                                // bound. The mini-player is only composed once the sheet is out of
                                // dismissed, and that composition (plus the full player's) is heavy:
                                // a fade started in the same frame has its first frames swallowed and
                                // the mini-player pops in. So wait for the sheet to leave dismissed,
                                // let the heavy frames land, then start the fade.
                                val appearAlpha = remember { Animatable(0f) }
                                LaunchedEffect(playerSheetState) {
                                    snapshotFlow { playerSheetState.isDismissed }.collectLatest { dismissed ->
                                        if (dismissed) {
                                            appearAlpha.snapTo(0f)
                                        } else if (appearAlpha.value < 1f) {
                                            repeat(MINIPLAYER_APPEAR_FADE_SKIPPED_FRAMES) { withFrameNanos { } }
                                            appearAlpha.animateTo(1f, tween(MINIPLAYER_APPEAR_FADE_MS.toInt()))
                                        }
                                    }
                                }
                                Box(
                                    // Top anchor follows the header through topPadding instead
                                    modifier = Modifier.fillMaxSize()
                                        .graphicsLayer {
                                            translationY = rewindSheetProgress.value * 160.dp.toPx()
                                            // Appearance fade x rewind-deck fade x dismissed-zone fade: the
                                            // mini-player fades with the sheet's value, which follows the
                                            // finger on a drag and the tween on an animated dismiss.
                                            alpha = appearAlpha.value *
                                                (1f - rewindSheetProgress.value).coerceIn(0f, 1f) *
                                                miniPlayerDismissAlpha(
                                                    value = playerSheetState.value,
                                                    dismissedBound = playerSheetState.dismissedBound,
                                                    collapsedBound = playerSheetState.collapsedBound,
                                                )
                                            // This layer spans the full screen: with the default Auto strategy,
                                            // an alpha below 1 would render the whole sheet subtree into a
                                            // full-screen offscreen buffer on every frame of a drag (lag).
                                            // ModulateAlpha applies the alpha per draw op instead. Trade-off:
                                            // semi-transparent elements modulate individually rather than as a
                                            // unit — imperceptible on a fast dismiss fade.
                                            compositingStrategy = CompositingStrategy.ModulateAlpha
                                        }
                                        .offset { IntOffset(0, if (isTopPlayer) 0 else bottomBarOffsetState.value.roundToInt()) }
                                ) {
                                    // Palette fade scope: the global palette switches in one
                                    // step, only this subtree (mini-player + full player)
                                    // animates the transition
                                    PaletteFade {
                                        CustomBottomSheet(
                                            state = playerSheetState,
                                            modifier = Modifier.fillMaxWidth(),
                                            onDismiss = {
                                                binder?.stopRadio()
                                                binder?.player?.clearMediaItems()
                                                showPlayer = false
                                                switchToAudioPlayer = false
                                                runCatching {
                                                    this@MainActivity.stopService(this@MainActivity.intent<app.n_zik.android.playback.services.PlayerServiceModern>())
                                                }
                                            },
                                            bottomPadding = { playerPadBottom.value },
                                            topPadding = playerTopPadding,
                                            collapsedStartInset = playerStartInset,
                                            collapsedEndInset = playerEndInset,
                                            collapsedContentHeight = Dimensions.collapsedPlayer,
                                            disableDismiss = shouldDisablePlayerSheetDismiss(disableClosingPlayerSwipingDown, ltGuestLocked),
                                            // Guest lock: a blocked "clean the player" attempt is explained by the
                                            // shared throttled toast (the destructive onDismiss is unreachable
                                            // while disabled). The "disable closing swiping down" setting alone
                                            // stays silent — it is the user's own choice.
                                            onDismissBlocked = {
                                                if (ltGuestLocked) ListenTogetherGuestGuardPlayer.reportUiBlockedOp(this@MainActivity)
                                            },
                                            collapsedContent = {
                                                MiniPlayer(
                                                    showPlayer = {
                                                        showPlayer = true
                                                        playerSheetState.expandSoft()
                                                    },
                                                    hidePlayer = {
                                                        coroutineScope.launch {
                                                            playerSheetState.hide()
                                                            showPlayer = false
                                                        }
                                                    },
                                                    navController = navController
                                                )
                                            }
                                        ) {
                                            Player(navController) {
                                                coroutineScope.launch {
                                                    playerSheetState.hide()
                                                    showPlayer = false
                                                    switchToAudioPlayer = false
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            MiniPlayerQueueOverlay(
                                showSheet = showQueueOverlay,
                                navController = navController,
                                onDismiss = { showQueueOverlay = false }
                            )

                            val isVideo = binder?.player?.currentMediaItem?.isVideo ?: false
                            val isVideoEnabled =
                                preferences.getBoolean(showButtonPlayerVideoKey, false)

                            val youtubePlayer: @Composable () -> Unit = {
                                binder?.player?.currentMediaItem?.mediaId?.let {
                                    YoutubePlayer(
                                        ytVideoId = it,
                                        lifecycleOwner = LocalLifecycleOwner.current,
                                        onCurrentSecond = {},
                                        showPlayer = showPlayer,
                                        onSwitchToAudioPlayer = {
                                            showPlayer = false
                                            switchToAudioPlayer = true
                                        }
                                    )
                                }
                            }

                            CustomModalBottomSheet(
                                showSheet = isVideo && isVideoEnabled && showPlayer,
                                onDismissRequest = { showPlayer = false },
                                containerColor = finalAppearance.colorPalette.background0,
                                contentColor = finalAppearance.colorPalette.background0,
                                modifier = Modifier.fillMaxWidth(),
                                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                                dragHandle = {
                                    Surface(
                                        modifier = Modifier.padding(vertical = 0.dp),
                                        color = finalAppearance.colorPalette.background0,
                                        shape = uiRoundnessShape()
                                    ) {}
                                },
                                shape = (uiRoundnessShape() as? RoundedCornerShape)?.let {
                                    RoundedCornerShape(
                                        topStart = it.topStart,
                                        topEnd = it.topEnd,
                                        bottomStart = CornerSize(0.dp),
                                        bottomEnd = CornerSize(0.dp)
                                    )
                                } ?: uiRoundnessShape()
                            ) {
                                youtubePlayer()
                            }

                            val menuState = LocalMenuState.current
                            val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
                            CustomModalBottomSheet(
                                showSheet = menuState.isDisplayed,
                                onDismissRequest = menuState::hide,
                                containerColor = Color.Transparent,
                                modifier = Modifier.statusBarsPadding(),
                                sheetState = menuSheetState,
                                dragHandle = {
                                    Surface(
                                        modifier = Modifier.padding(vertical = 0.dp),
                                        color = Color.Transparent,
                                    ) {}
                                },
                                shape = (uiRoundnessShape() as? RoundedCornerShape)?.let {
                                    RoundedCornerShape(
                                        topStart = it.topStart,
                                        topEnd = it.topEnd,
                                        bottomStart = CornerSize(0.dp),
                                        bottomEnd = CornerSize(0.dp)
                                    )
                                } ?: uiRoundnessShape()
                            ) {
                                AnimatedContent(
                                    targetState = menuState.contentState,
                                    transitionSpec = {
                                        slideInHorizontally(animationSpec = tween(300)) { width -> width / 2 } + fadeIn(animationSpec = tween(300)) togetherWith 
                                        slideOutHorizontally(animationSpec = tween(300)) { width -> -width / 2 } + fadeOut(animationSpec = tween(300))
                                    },
                                    label = "MenuContentTransition"
                                ) { target ->
                                    BackHandler(enabled = menuState.hasPrevious) {
                                        menuState.pop()
                                    }
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        target.second()
                                    }
                                }
                            }

                        }

                }
                var isPlayerInitialized by rememberSaveable { mutableStateOf(false) }
                val playerUpdateTrigger by binder?.playerUpdateTrigger?.collectAsStateWithLifecycle(0) ?: remember { mutableStateOf(0) }
                DisposableEffect(binder?.player, playerUpdateTrigger) {
                    val currentBinder = binder ?: return@DisposableEffect onDispose { }
                    val player = currentBinder.player

                    setDynamicPalette(player.currentMediaItem?.mediaMetadata?.artworkUri?.thumbnail(1000)?.toString())

                    // "Now playing" notification tap — COLD start (spec-notification-click-opens-player):
                    // the held tap token is validated against the CURRENT service's token (via
                    // the Binder) on EVERY run of this effect, not only at init: a valid token
                    // with no media yet is HELD and re-checked when media arrives (queue
                    // restore / early binder attach); a stale token (task-record re-send after
                    // a process death — the restarted service minted a new token) is
                    // discarded, no deployment. There is no persistent marker: staleness is
                    // the service's live token, not a stored value.
                    val heldOpenPlayerToken = openPlayerColdToken
                    var deployedNow = false
                    if (heldOpenPlayerToken != null) {
                        if (heldOpenPlayerToken != currentBinder.openPlayerToken) {
                            // Stale (process-death re-send: the service restarted and minted a
                            // new token) → discard, no deployment.
                            openPlayerColdToken = null
                        } else if (player.currentMediaItem != null) {
                            openPlayerColdToken = null
                            // Decision 2026-10-03 (spec-notification-click-opens-player): the
                            // notification tap ALWAYS deploys the full player — the
                            // keepPlayerMinimized setting is NOT consulted on this path
                            // (a deliberate tap, distinct from the widget/historical
                            // cold start that honors the setting).
                            showPlayer = true
                            // Anti-flicker: a sheet already above the expanded bound is left
                            // alone (no visible collapse→expand on a tap while the full player
                            // is open).
                            if (playerSheetState.value < playerSheetState.expandedBound) {
                                coroutineScope.presentMiniplayerThenExpand(playerSheetState, onPresent = { restoreHiddenBars() })
                            }
                            deployedNow = true
                        }
                        // valid + no media: held — re-checked on the next playerUpdateTrigger.
                    }

                    if (!isPlayerInitialized) {
                        isPlayerInitialized = true
                        if (player.currentMediaItem == null) {
                            if (playerSheetState.isVisible) {
                                showPlayer = false
                            }
                        } else if (deployedNow) {
                            // The deployment above owns the presentation: the default collapsed
                            // presentation must NOT run in the same effect run (it would undo
                            // the deployment).
                        } else if (launchedFromNotification) {
                            intent.replaceExtras(Bundle())
                            if (preferences.getBoolean(keepPlayerMinimizedKey, true)) {
                                showPlayer = false
                                // Collapsed position; pops with a fade when coming from the dismissed zone
                                playerSheetState.presentMiniplayerCollapsed()
                            } else {
                                showPlayer = true
                                coroutineScope.presentMiniplayerThenExpand(playerSheetState, onPresent = { restoreHiddenBars() })
                            }
                        } else {
                            showPlayer = false
                            // Collapsed position; pops with a fade when coming from the dismissed zone
                            playerSheetState.presentMiniplayerCollapsed()
                        }
                    }

                    val listener = object : Player.Listener {
                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && mediaItem != null) {
                                if (mediaItem.mediaMetadata.extras?.getBoolean("isFromPersistentQueue") != true) {
                                    if (preferences.getBoolean(keepPlayerMinimizedKey, true)) {
                                        showPlayer = false
                                        // Collapsed position; pops with a fade when coming from the dismissed zone
                                        playerSheetState.presentMiniplayerCollapsed()
                                    } else {
                                        showPlayer = true
                                        coroutineScope.presentMiniplayerThenExpand(playerSheetState, onPresent = { restoreHiddenBars() })
                                    }
                                }
                            }

                                                         setDynamicPalette(mediaItem?.mediaMetadata?.artworkUri?.thumbnail(1000)?.toString())
                        }


                    }

                    player.addListener(listener)

                    onDispose { player.removeListener(listener) }
                }

                InitDownloader()

                }
            }

            // Same gate guard as the rewind reminder effect above: the NavHost is only
            // composed once onboarding is done — a shared / deep-linked URL must not
            // navigate into an empty graph while the gate is up
            LaunchedEffect(intentUriData, onboardingStep == null) {
                if (onboardingStep != null) return@LaunchedEffect
                val uri = intentUriData ?: return@LaunchedEffect

                Toaster.n(
                    "${BuildConfig.APP_NAME} ${this@MainActivity.resources.getString( R.string.opening_url )}",
                    duration = Toast.LENGTH_LONG
                )

                lifecycleScope.launch(NzikDispatchers.UI) {
                    when (val path = uri.pathSegments.firstOrNull()) {
                        "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                            val browseId = "VL$playlistId"

                            if (playlistId.startsWith("OLAK5uy_")) {
                                Innertube.playlistPage(browseId = browseId)
                                    ?.getOrNull()?.let {
                                        it.songsPage?.items?.firstOrNull()?.album?.endpoint?.browseId?.let { browseId ->
                                            navController.navigate(route = "${NavRoutes.album.name}/$browseId")

                                        }
                                    }
                            } else {
                                navController.navigate(route = "${NavRoutes.playlist.name}/$browseId")
                            }
                        }

                        "channel", "c" -> uri.lastPathSegment?.let { channelId ->
                            try {
                                navController.navigate(route = "${NavRoutes.artist.name}/$channelId")
                            } catch (e: Exception) {
                            Timber.tag("MainActivity").e("onCreate intentUriData ${e.stackTraceToString()}")
                            }
                        }

                        "search" -> uri.getQueryParameter("q")?.let { query ->
                            navController.navigate(route = "${NavRoutes.searchResults.name}/$query")
                        }

                        "localPlaylist" -> uri.lastPathSegment?.let { playlistId ->
                            navController.navigate(route = "${NavRoutes.localPlaylist.name}/$playlistId")
                        }

                        "playFavorites" -> {
                            lifecycleScope.launch(NzikDispatchers.DATA) {
                                val favorites = Database.songTable.allFavorites().first()
                                if (favorites.isNotEmpty()) {
                                    val mediaItems = favorites.map { it.asMediaItem }
                                    withContext(NzikDispatchers.UI) {
                                        val validBinder = snapshotFlow { binder }.filterNotNull().first()
                                        validBinder.player.forcePlayFromBeginning(mediaItems)
                                    }
                                }
                            }
                        }

                        "album" -> uri.lastPathSegment?.let { albumId ->
                            navController.navigate(route = "${NavRoutes.album.name}/$albumId")
                        }

                        else -> when {
                            path == "watch" -> uri.getQueryParameter("v")
                            uri.host == "youtu.be" -> path
                            else -> null
                        }?.let { videoId ->
                            Innertube.song(videoId)?.getOrNull()?.let { song ->
                                val binder = snapshotFlow { binder }.filterNotNull().first()
                                withContext(NzikDispatchers.UI) {
                                    if (song.explicit && preferences.getBoolean(
                                            parentalControlEnabledKey,
                                            false
                                        )
                                    ) {
                                        Toaster.w( R.string.parental_control_is_enabled )
                                    } else {
                                        binder?.player?.forcePlay(song.asMediaItem)
                                    }
                                }
                            }
                        }
                    }
                }
                intentUriData = null
            }


            //throw RuntimeException("This is a simulated exception to crash");
        }
    }


    override fun onResume() {
        super.onResume()
        appRunningInBackground = false
    }

    override fun onPause() {
        super.onPause()
        appRunningInBackground = true
    }

    @UnstableApi
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intentUriData = intent.data ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri()
        // The profile-shortcut actions have their own consumption (spec-profile-shortcuts) and
        // must not ride the home-tab shortcut state (which would pop the back stack to home).
        shortcutIntentAction = intent.action?.takeUnless { isProfileShortcutAction(it) }
        // Generic « Profiles » shortcut (warm): record the navigation target without
        // clobbering a pending one (the navigation effect consumes it, gated on onboarding).
        openProfilesShortcut = openProfilesShortcut || (intent.action == action_profiles)
        // Direct profile shortcut (warm, app ON path): a different valid profile runs the full
        // CAP-3 switch + process exit; the same/unknown one is a bare consumption — the extras
        // are stripped before the intent is kept, so the next singleTask re-delivery decodes to
        // nothing (same contract as onCreate and the rewind deep links).
        consumeProfileShortcutWarm(intent)
        // Rewind notification deep links (deck / playlist): consume the fresh intent's
        // targets BEFORE keeping it as the current intent — app ON path, same contract as
        // onCreate (spec GH-275 follow-up): an unconsumed target opens, an already-consumed
        // one (task record re-send / singleTask relaunch) is rejected, and the extras are
        // stripped so the kept intent decodes to nothing on the next re-delivery.
        consumeRewindDeepLinks(intent)
        // "Now playing" notification tap (spec-notification-click-opens-player): consume
        // the fresh intent's open-player token BEFORE keeping it as the current intent —
        // app ON path, same contract as onCreate: a positive token arms the warm
        // deployment flag (always fresh — a warm tap is a fresh fire of the PendingIntent,
        // no live comparison needed), and the extra is stripped either way.
        consumeOpenPlayerDeepLink(intent, cold = false)
        setIntent(intent)
    }

    override fun onStop() {
        runCatching {
            unbindService(serviceConnection)
        }.onFailure {
            Timber.tag("MainActivity").e("onStop unbindService ${it.stackTraceToString()}")
        }
        super.onStop()
    }

    @UnstableApi
    override fun onDestroy() {
        super.onDestroy()

        runCatching {
            monet.removeMonetColorsChangedListener(this)
            _monet = null
            // NzikDispatchers is process-lifetime and shared with PlayerServiceModern's
            // StreamResolver, which can outlive this Activity — never close it here.
        }.onFailure {
            Timber.tag("MainActivity").e("onDestroy removeMonetColorsChangedListener ${it.stackTraceToString()}")
        }

    }

    private fun savePaletteForWidget(palette: ColorPalette) {
        preferences.edit()
            .putInt("widget_palette_accent", palette.accent.toArgb())
            .putInt("widget_palette_background1", palette.background1.toArgb())
            .putInt("widget_palette_background2", palette.background2.toArgb())
            .putInt("widget_palette_text", palette.text.toArgb())
            .putInt("widget_palette_textSecondary", palette.textSecondary.toArgb())
            .putBoolean("widget_palette_isDark", palette.isDark)
            .putLong("widget_palette_timestamp", System.currentTimeMillis())
            .apply()
    }

    private var lastSystemBarIsDark: Boolean? = null

    private fun setSystemBarAppearance(isDark: Boolean) {
        // The bars only depend on the light/dark state; re-applying the same
        // state on every track change costs a window-level insets update
        // (the hideStatusBar pref change triggers recreate(), so no forced
        // re-apply path is needed)
        if (lastSystemBarIsDark == isDark) return
        lastSystemBarIsDark = isDark
        val hideStatusBar = preferences.getBoolean(hideStatusBarKey, false)
        with(WindowCompat.getInsetsController(window, window.decorView)) {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
            if (hideStatusBar) {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.statusBars())
            } else {
                show(WindowInsetsCompat.Type.statusBars())
            }
        }

        if (!isAtLeastAndroid6) {
            @Suppress("DEPRECATION")
            window.statusBarColor =
                (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }

        if (!isAtLeastAndroid8) {
            @Suppress("DEPRECATION")
            window.navigationBarColor =
                (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }

    companion object {
        const val action_search = "app.it.fast4x.rimusic.action.search"
        const val action_songs = "app.it.fast4x.rimusic.action.songs"
        const val action_albums = "app.it.fast4x.rimusic.action.albums"
        const val actions_artists = "app.it.fast4x.rimusic.action.artists"
        const val action_library = "app.it.fast4x.rimusic.action.library"
        // Profile shortcuts (spec-profile-shortcuts): the generic one opens the profiles page;
        // the direct per-profile one carries the stable profile ID in [EXTRA_PROFILE_ID] and
        // launches in it (cold) or switches to it (warm).
        const val action_profiles = "app.it.fast4x.rimusic.action.profiles"
        const val action_profile = "app.it.fast4x.rimusic.action.profile"
        const val EXTRA_PROFILE_ID = "profileId"
        // "Now playing" notification tap (spec-notification-click-opens-player): the
        // notification's content intent — PlayerServiceModern's global session activity
        // (the PendingIntent media3 uses as the notification content intent, option B) —
        // carries this one-shot token. The tap always deploys the full player, regardless
        // of the keepPlayerMinimized setting.
        const val EXTRA_OPEN_PLAYER_TOKEN = "openPlayerToken"
    }


    override fun onMonetColorsChanged(
        monet: MonetCompat,
        monetColors: ColorScheme,
        isInitialChange: Boolean
    ) {
        val colorPaletteName =
            preferences.getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic)
        if (!isInitialChange && colorPaletteName == ColorPaletteName.MaterialYou &&
            shouldRecreateActivity(Database.isClosed)
        ) {
            /*
            monet.updateMonetColors()
            monet.invokeOnReady {
                startApp()
            }
             */
            this@MainActivity.recreate()
        }
    }


}

var appRunningInBackground: Boolean = false

val LocalPlayerServiceBinder = staticCompositionLocalOf<PlayerServiceModern.Binder?> { null }

val LocalPlayerAwareWindowInsets = staticCompositionLocalOf<WindowInsets> { TODO() }

val LocalDownloadHelper = staticCompositionLocalOf<MyDownloadHelper> { error("No Downloader provided") }

val LocalPlayerSheetState =
    staticCompositionLocalOf<PlayerSheetState> { error("No player sheet state provided") }

//val LocalInternetConnected = staticCompositionLocalOf<Boolean> { error("No Network Status provided") }

val LocalPendingMiniPlayerAction = staticCompositionLocalOf<MutableState<PendingMiniPlayerAction?>> { error("No PendingMiniPlayerAction state provided") }
val LocalIsShowingLyrics = staticCompositionLocalOf<MutableState<Boolean>> { error("No LocalIsShowingLyrics provided") }
val LocalIsShowingVisualizer = staticCompositionLocalOf<MutableState<Boolean>> { error("No LocalIsShowingVisualizer provided") }
val LocalTopBarOffset = staticCompositionLocalOf<State<Float>> { mutableStateOf(0f) }
val LocalBottomBarOffset = staticCompositionLocalOf<State<Float>> { mutableStateOf(0f) }
val LocalDownloadStatesMap = staticCompositionLocalOf<Map<String, app.it.fast4x.rimusic.enums.DownloadedStateMedia>> { emptyMap() }
