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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
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
import androidx.core.os.LocaleListCompat
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
import app.n_zik.android.components.onboarding.OnboardingNameScreen
import app.n_zik.android.components.onboarding.OnboardingScreen
import app.n_zik.android.components.ui.screens.home.OPEN_SEARCH_SHORTCUT
import app.n_zik.android.components.ui.screens.home.initialShortcutAction
import app.n_zik.android.components.ui.screens.rewind.RewindReminderWorker
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.enums.OnboardingStep
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.PlayerAwareInsetsTracker
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
import app.n_zik.android.components.player.MiniPlayerQueueOverlay
import app.n_zik.android.components.player.APP_HEADER_HEIGHT
import app.n_zik.android.components.player.miniPlayerSideInset
import app.n_zik.android.components.player.miniPlayerTopInset
import app.n_zik.android.components.player.miniPlayerTopPaddingPx
import app.n_zik.android.components.player.TOP_NAV_BAR_HEIGHT
import app.n_zik.android.components.player.showMiniplayerIfDismissed
import app.n_zik.android.components.player.PaletteFade
import app.n_zik.android.components.player.m3eDynamicColorPaletteOf
import app.n_zik.android.components.player.presentMiniplayerThenExpand
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
import app.it.fast4x.rimusic.utils.disableClosingPlayerSwipingDownKey
import app.it.fast4x.rimusic.utils.disablePlayerHorizontalSwipeKey
import app.it.fast4x.rimusic.utils.effectRotationKey
import app.it.fast4x.rimusic.utils.fontTypeKey
import app.it.fast4x.rimusic.utils.forcePlay
import app.it.fast4x.rimusic.utils.forcePlayFromBeginning
import app.it.fast4x.rimusic.utils.getEnum
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
import app.it.fast4x.rimusic.enums.TransitionEffect
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.restartActivityKey
import app.it.fast4x.rimusic.utils.hideStatusBarKey
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

/**
 * Decodes the finished-month deck target carried by the monthly rewind reminder's
 * content intent: valid extras yield the `(year, month)` pair, anything else (missing
 * extras, out-of-range values, restored process instance) yields null so the app
 * starts without a forced deck open. Top-level so the parse contract is unit-testable
 * without launching the activity (spec GH-275, re-review: consumer side untested).
 */
internal fun rewindDeckTargetFromIntent(intent: Intent?, isRestoredInstance: Boolean): Pair<Int, Int>? {
    if (isRestoredInstance) return null
    val year = intent?.getIntExtra(RewindReminderWorker.EXTRA_DECK_YEAR, 0) ?: 0
    val month = intent?.getIntExtra(RewindReminderWorker.EXTRA_DECK_MONTH, 0) ?: 0
    return if (year in 2000..2100 && month in 1..12) year to month else null
}

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
    private var shortcutIntentAction by mutableStateOf<String?>(null)

    // Finished month carried by the monthly rewind reminder's content intent. Set on cold start
    // (startApp) and warm start (onNewIntent), consumed once by the navigation effect that opens
    // the deck on that month.
    private var rewindDeckTarget by mutableStateOf<Pair<Int, Int>?>(null)

    // Current step of the first-launch onboarding flow, held by the activity so a
    // recreation (rotation) resumes the flow at the right step; null means the flow
    // is complete and the main navigation renders instead. The step is persisted in
    // prefs on every transition (see advanceOnboarding), so a process restart —
    // post-import restart or process death — resumes at the right step too. The
    // onboardingComplete flag is written once the flow is fully done, or when a restore
    // succeeds (the restart then lands directly in the app), so a mid-flow crash never
    // marks the onboarding as finished.
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

    /**
     * Marks the onboarding as complete without leaving the current step. Used by the
     * restore step (a successful import restarts the app) and by the accounts step
     * (leaving the step with a Discord token set restarts the app): the restart
     * must land directly in the main app (restored settings already contain the
     * display name and the YouTube account), so the flag is written and the
     * persisted step cleared now — the step field stays put until the restart
     * happens.
     */
    private fun completeOnboarding() {
        DataStoreUtils.saveBoolean(this, DataStoreUtils.KEY_ONBOARDING_COMPLETE, true)
        DataStoreUtils.saveString(this, DataStoreUtils.KEY_ONBOARDING_STEP, "")
        Timber.tag("MainActivity").i("Onboarding completed before restart, flag written, step cleared")
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
        monet.invokeOnReady {
            startApp(isRestoredInstance)
        }

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
                it.getBoolean("expandPlayerBottomSheet") || it.getBoolean("fromWidget")
            } ?: false

        Timber.tag("MainActivity").d("onCreate launchedFromNotification: $launchedFromNotification intent ${intent.action}")

        intentUriData = intent.data ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri()
        shortcutIntentAction = initialShortcutAction(intent.action, isRestoredInstance)
        rewindDeckTarget = rewindDeckTargetFromIntent(intent, isRestoredInstance)
        onboardingStep = if (DataStoreUtils.getBoolean(this, DataStoreUtils.KEY_ONBOARDING_COMPLETE, false)) {
            null
        } else {
            // Resume at the persisted step after a post-import restart or process death;
            // fresh installs have nothing persisted, so fall back to the first step
            val savedStep = DataStoreUtils.getString(this, DataStoreUtils.KEY_ONBOARDING_STEP, "")
            OnboardingStep.entries.firstOrNull { it.name == savedStep } ?: OnboardingStep.PERMISSIONS
        }

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
                                val lang = sharedPreferences.getEnum( languageAppKey, Languages.System )
                                val languageTag: String = lang.code.ifEmpty {
                                    AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag().orEmpty()
                                }
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags( languageTag )
                                )
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
            
            val isScrollableRoute = currentRoute == "home" ||
                    currentRoute?.startsWith("artist") == true ||
                    currentRoute?.startsWith("album") == true ||
                    currentRoute?.startsWith("playlist") == true ||
                    currentRoute?.startsWith("localPlaylist") == true ||
                    currentRoute?.startsWith("searchResults") == true ||
                    currentRoute?.startsWith("settings") == true
                    
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
                val hasNavBar = !areBarsHidden

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

                // Monthly rewind reminder notification: open the deck on the finished month
                // (extras set in startApp / onNewIntent). Consumed from an effect, like the
                // shortcut above, so the navigation happens once the graph is composed.
                // The gate is part of the key: while onboarding is up the NavHost is not
                // composed (empty graph — navigating would crash), so the target is held
                // until the flow completes and the effect re-runs.
                LaunchedEffect(rewindDeckTarget, onboardingStep == null) {
                    if (onboardingStep != null) return@LaunchedEffect
                    rewindDeckTarget?.let { (year, month) ->
                        navController.navigate("${NavRoutes.rewind.name}?year=$year&month=$month")
                        rewindDeckTarget = null
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
                            // restore -> name -> accounts -> main app) animate with the user's chosen
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
                                        // "Skip" moves on to the name step; a successful
                                        // restore completes the onboarding instead (flag
                                        // written before the restart), so the post-import
                                        // restart lands directly in the main app
                                        onComplete = { advanceOnboarding() },
                                        onRestoreDone = { completeOnboarding() }
                                    )

                                    OnboardingStep.NAME -> OnboardingNameScreen(
                                        onComplete = { advanceOnboarding() }
                                    )

                                    OnboardingStep.ACCOUNTS -> OnboardingAccountsScreen(
                                        onComplete = { advanceOnboarding() },
                                        onDiscordConnected = { completeOnboarding() }
                                    )
                                }
                            }

                            val disableClosingPlayerSwipingDown by rememberPreference(disableClosingPlayerSwipingDownKey, false)
        checkIfAppIsRunningInBackground()

                            // Reactive media-item presence: the sheet (including its
                            // collapsed hit target) must not be composed when the
                            // player has nothing to play, otherwise an invisible
                            // tappable strip stays at the bottom of the screen.
                            val currentMediaId by (binder?.player?.currentMediaItemIdAsState() ?: remember { mutableStateOf<String?>(null) })

                            // Keyed on the sheet too: a rebuilt sheet (rotation, insets) starts from
                            // its last anchor and must be re-checked against the current media
                            LaunchedEffect(currentMediaId, playerSheetState) {
                                if (currentMediaId == null) {
                                    if (!playerSheetState.isDismissed) {
                                        playerSheetState.snapTo(playerSheetState.dismissedBound)
                                    }
                                    showQueueOverlay = false
                                } else {
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

                            if (currentMediaId != null) {
                                Box(
                                    // Top anchor follows the header through topPadding instead
                                    modifier = Modifier.fillMaxSize()
                                        .graphicsLayer {
                                            translationY = rewindSheetProgress.value * 160.dp.toPx()
                                            alpha = (1f - rewindSheetProgress.value).coerceIn(0f, 1f)
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
                                            disableDismiss = disableClosingPlayerSwipingDown,
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
                    val player = binder?.player ?: return@DisposableEffect onDispose { }

                    setDynamicPalette(player.currentMediaItem?.mediaMetadata?.artworkUri?.thumbnail(1000)?.toString())

                    if (!isPlayerInitialized) {
                        isPlayerInitialized = true
                        if (player.currentMediaItem == null) {
                            if (playerSheetState.isVisible) {
                                showPlayer = false
                            }
                        } else {
                            if (launchedFromNotification) {
                                intent.replaceExtras(Bundle())
                                if (preferences.getBoolean(keepPlayerMinimizedKey, true)) {
                                    showPlayer = false
                                    // Snap to collapsed so the mini-player is at the right position
                                    playerSheetState.snapTo(playerSheetState.collapsedBound)
                                } else {
                                    showPlayer = true
                                    coroutineScope.presentMiniplayerThenExpand(playerSheetState, onPresent = { restoreHiddenBars() })
                                }
                            } else {
                                showPlayer = false
                                // Snap to collapsed so the mini-player is at the right position
                                playerSheetState.snapTo(playerSheetState.collapsedBound)
                            }
                        }
                    }

                    val listener = object : Player.Listener {
                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && mediaItem != null) {
                                if (mediaItem.mediaMetadata.extras?.getBoolean("isFromPersistentQueue") != true) {
                                    if (preferences.getBoolean(keepPlayerMinimizedKey, true)) {
                                        showPlayer = false
                                        // Ensure mini-player is at collapsed position
                                        playerSheetState.snapTo(playerSheetState.collapsedBound)
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
        setIntent(intent)
        intentUriData = intent.data ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri()
        shortcutIntentAction = intent.action
        rewindDeckTarget = rewindDeckTargetFromIntent(intent, isRestoredInstance = false)
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
