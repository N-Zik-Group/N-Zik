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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.playback.utils.PlaybackDispatchers
import android.graphics.Bitmap
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.ime
import androidx.palette.graphics.Palette
import com.kieronquinn.monetcompat.core.MonetActivityAccessException
import com.kieronquinn.monetcompat.core.MonetCompat
import com.kieronquinn.monetcompat.interfaces.MonetColorsChangedListener
import com.valentinilk.shimmer.LocalShimmerTheme
import com.valentinilk.shimmer.defaultShimmerTheme
import dev.kdrag0n.monet.theme.ColorScheme
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.BrowseBody
import it.fast4x.innertube.requests.playlistPage
import it.fast4x.innertube.requests.song
import it.fast4x.innertube.utils.LocalePreferenceItem
import app.it.fast4x.compose.persist.PersistMap
import app.it.fast4x.compose.persist.PersistMapOwner
import app.it.fast4x.compose.persist.LocalPersistMap
import it.fast4x.innertube.utils.LocalePreferences
import it.fast4x.innertube.utils.NewPipeDownloaderImpl
import it.fast4x.innertube.utils.ProxyPreferenceItem
import it.fast4x.innertube.utils.ProxyPreferences
import app.it.fast4x.rimusic.enums.AnimatedGradient
import app.it.fast4x.rimusic.enums.AudioQualityFormat
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.enums.ColorPaletteName
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.HomeScreenTabs
import app.it.fast4x.rimusic.enums.Languages
import app.it.fast4x.rimusic.enums.LogType
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.PipModule
import app.it.fast4x.rimusic.enums.PlayerBackgroundColors
import app.it.fast4x.rimusic.extensions.pip.PipEventContainer
import app.it.fast4x.rimusic.extensions.pip.PipModuleContainer
import app.it.fast4x.rimusic.extensions.pip.PipModuleCover
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.themed.CrossfadeContainer
import app.it.fast4x.rimusic.ui.screens.AppNavigation
import app.it.fast4x.rimusic.ui.screens.player.MiniPlayer
import app.it.fast4x.rimusic.ui.screens.player.Player
import app.it.fast4x.rimusic.ui.screens.player.components.YoutubePlayer
import app.it.fast4x.rimusic.ui.screens.player.rememberPlayerSheetState
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
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
import app.it.fast4x.rimusic.utils.loadAppLog
import app.it.fast4x.rimusic.utils.loadedDataKey
import app.it.fast4x.rimusic.utils.logDebugEnabledKey
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
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.restartActivityKey
import app.it.fast4x.rimusic.utils.hideStatusBarKey
import app.it.fast4x.rimusic.utils.setDefaultPalette
import app.it.fast4x.rimusic.utils.showButtonPlayerVideoKey
import app.it.fast4x.rimusic.utils.showSearchTabKey
import app.it.fast4x.rimusic.utils.showTotalTimeQueueKey
import app.it.fast4x.rimusic.utils.textCopyToClipboard
import app.n_zik.android.core.coil.*
import app.it.fast4x.rimusic.utils.thumbnailRoundnessDpKey
import app.it.fast4x.rimusic.utils.transitionEffectKey
import app.it.fast4x.rimusic.utils.useSystemFontKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.invidious.Invidious

import app.kreate.android.me.knighthat.utils.Toaster
import it.fast4x.innertube.Innertube.proxy
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import timber.log.Timber
import java.net.Proxy
import java.util.Locale

import kotlin.system.exitProcess
import androidx.compose.foundation.shape.RoundedCornerShape
import app.it.fast4x.rimusic.enums.UiType
import org.woheller69.freeDroidWarn.FreeDroidWarn
import app.it.fast4x.rimusic.ui.styling.BoundedCornerSize
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.State
import androidx.core.view.WindowInsetsControllerCompat
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.n_zik.android.core.database.Database
import android.Manifest
import kotlinx.coroutines.Job

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
        FreeDroidWarn.showWarningOnUpgrade(this, BuildConfig.VERSION_CODE)
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

        monet.invokeOnReady {
            startApp()
        }

        checkIfAppIsRunningInBackground()
        // Verify backup location exists
        lifecycleScope.launch(Dispatchers.IO) {
            BackupManager.verifyBackupLocation(this@MainActivity)
        }

        // Fetch Invidious instances
        lifecycleScope.launch(Dispatchers.IO) {
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
    fun startApp() {

        // Check YouTube cookie status on startup — warn user if expired/invalid
        when (MainApplication.cookieStatus) {
            MainApplication.CookieStatus.INVALID -> {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    Toaster.e(R.string.error_cookie_invalid)
                }, 3000)
            }
            MainApplication.CookieStatus.EXPIRED -> {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    Toaster.e(R.string.error_session_expired)
                }, 3000)
            }
            MainApplication.CookieStatus.NOT_LOGGED_IN -> {
                // Silent — user may choose not to log in
            }
            MainApplication.CookieStatus.VALID -> { /* all good */ }
        }

        // Used in QuickPics for load data from remote instead of last saved in SharedPreferences
        preferences.edit(commit = true) { putBoolean(loadedDataKey, false) }

        if (!preferences.getBoolean(closeWithBackButtonKey, false))
            if (Build.VERSION.SDK_INT >= 33) {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT
                ) {
                    //Log.d("onBackPress", "yeah")
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
                } else null
            } else null

            NetworkClientFactory.configure(
                proxy = proxy,
                cacheDir = this@MainActivity.externalCacheDir ?: this@MainActivity.cacheDir
            )
            Innertube.proxy = proxy
        }

        setContent {
            val colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)
            val isPicthBlack = colorPaletteMode == ColorPaletteMode.PitchBlack
//            val isDark =
//                colorPaletteMode == ColorPaletteMode.Dark || isPicthBlack || (colorPaletteMode == ColorPaletteMode.System && isSystemInDarkTheme())

            // Valid to get log when app crash
            if (intent.action == action_copy_crash_log) {
                preferences.edit(commit = true) {
                    putBoolean(logDebugEnabledKey, true)
                }
                loadAppLog(this@MainActivity, type = LogType.Crash).let {
                    if (it != null) textCopyToClipboard(it, this@MainActivity)
                }
                LaunchedEffect(Unit) {
                    delay(5000)
                    exitProcess(0)
                }
            }

            //TODO: Check internet connection
//            val internetConnectivityObserver = InternetConnectivityObserver(this)
//            val internetConnected by internetConnectivityObserver.networkStatus.collectAsState(false)
//            if (internetConnected) downloadHelper.resumeDownloads(this)

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

            var appearance by rememberSaveable(
                !lightTheme,
                stateSaver = Appearance.Companion
            ) {
                with(preferences) {
                    val colorPaletteName =
                        getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic)
                    val colorPaletteMode = getEnum(colorPaletteModeKey, ColorPaletteMode.Dark)
                    val thumbnailRoundnessDp = getFloat(thumbnailRoundnessDpKey, 12f)
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

                    setSystemBarAppearance(colorPalette.isDark)

                    mutableStateOf(
                        Appearance(
                            colorPalette = colorPalette,
                            typography = typographyOf(
                                colorPalette.text,
                                useSystemFont,
                                applyFontPadding,
                                fontType
                            ),
                            thumbnailShape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f)),
                            uiRoundnessShape = RoundedCornerShape(BoundedCornerSize(uiRoundnessDp.dp, 0.4f))
                        )
                    )
                }


            }

            fun setDynamicPalette(url: String?) {
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
                    
                    // Create a palette with fixed violet accent
                    val violetAccent = Color(0.54509807f, 0.36078432f, 0.9647059f) // Couleur violette
                    val defaultColorPalette = dynamicColorPaletteOf(violetAccent, isDark)
                    setSystemBarAppearance(defaultColorPalette.isDark)
                    appearance = appearance.copy(
                        colorPalette = if (!isPicthBlack) defaultColorPalette else defaultColorPalette.copy(
                            background0 = Color.Black,
                            background1 = Color.Black,
                            background2 = Color.Black,
                            background3 = Color.Black,
                            background4 = Color.Black,
                        ),
                        typography = appearance.typography.copy(defaultColorPalette.text)
                    )
                    return
                }

                val colorPaletteMode =
                    preferences.getEnum(colorPaletteModeKey, ColorPaletteMode.Dark)
                paletteJob.value?.cancel()
                paletteJob.value = coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val bitmap: Bitmap? = ImageCacheFactory.loadBitmap(url, allowHardware = false)

                        
                        val isPicthBlack = colorPaletteMode == ColorPaletteMode.PitchBlack
                        val isDark =
                            colorPaletteMode == ColorPaletteMode.Dark || isPicthBlack || (colorPaletteMode == ColorPaletteMode.System && isSystemInDarkTheme)

                        if (bitmap != null) {
                            val palette: Palette = Palette
                                .from(bitmap)
                                .maximumColorCount(8)
                                .addFilter(if (isDark) ({ _: Int, hsl: FloatArray -> hsl[0] !in 36f..100f }) else null)
                                .generate()


                            val paletteResult = dynamicColorPaletteOf(bitmap, isDark)
                            if (paletteResult != null) {
                                withContext(Dispatchers.Main) {
                                    val finalPalette = if (!isPicthBlack) paletteResult else paletteResult.copy(
                                        isDark = true,
                                        background0 = Color.Black,
                                        background1 = Color.Black,
                                        background2 = Color.Black,
                                        background3 = Color.Black,
                                        background4 = Color.Black,
                                        text = Color.White
                                    )
                                    setSystemBarAppearance(finalPalette.isDark)
                                    val newAppearance = appearance.copy(
                                        colorPalette = finalPalette,
                                        typography = appearance.typography.copy(finalPalette.text)
                                    )
                                    appearance = newAppearance
                                    savePaletteForWidget(finalPalette)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    val defaultColorPalette = dynamicColorPaletteOf(Color(0.54509807f, 0.36078432f, 0.9647059f), isDark)
                                    setSystemBarAppearance(defaultColorPalette.isDark)
                                    appearance = appearance.copy(
                                        colorPalette = if (!isPicthBlack) defaultColorPalette else defaultColorPalette.copy(
                                            background0 = Color.Black, background1 = Color.Black,
                                            background2 = Color.Black, background3 = Color.Black, background4 = Color.Black,
                                        ),
                                        typography = appearance.typography.copy(defaultColorPalette.text)
                                    )
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                val violetAccent = Color(0.54509807f, 0.36078432f, 0.9647059f)
                                val defaultColorPalette = dynamicColorPaletteOf(violetAccent, isDark)
                                setSystemBarAppearance(defaultColorPalette.isDark)
                                appearance = appearance.copy(
                                    colorPalette = if (!isPicthBlack) defaultColorPalette else defaultColorPalette.copy(
                                        background0 = Color.Black,
                                        background1 = Color.Black,
                                        background2 = Color.Black,
                                        background3 = Color.Black,
                                        background4 = Color.Black,
                                    ),
                                    typography = appearance.typography.copy(defaultColorPalette.text)
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Timber.tag("MainActivity").e(e, "Error loading appearance")
                    }
                }
            }


            DisposableEffect(binder, !lightTheme) {
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

                    bitmapListenerJob = coroutineScope.launch(Dispatchers.IO) {
                        dynamicColorPaletteOf(bitmap, isDark, isPicthBlack)?.let {
                            withContext(Dispatchers.Main) {
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
                                this@MainActivity.recreate()
                                Timber.tag("MainActivity").d("recreate()")
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
                                            proxy = it.fast4x.innertube.utils.getProxy(ProxyPreferences.preference!!)
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

                                var colorPalette = colorPaletteOf(
                                    colorPaletteName,
                                    colorPaletteMode,
                                    !lightTheme
                                )

                                if (colorPaletteName == ColorPaletteName.Dynamic) {
                                    // Always call setDynamicPalette when switching to the dynamic theme
                                    val currentArtworkUri = binder?.player?.currentMediaItem?.mediaMetadata?.artworkUri?.thumbnail(1000)?.toString()
                                    setDynamicPalette(currentArtworkUri)
                                } else {
                                    //bitmapListenerJob?.cancel()
                                    //binder?.setBitmapListener(null)

                                    if (colorPaletteName == ColorPaletteName.MaterialYou) {
                                        colorPalette = dynamicColorPaletteOf(
                                            Color(monet.getAccentColor(this@MainActivity)),
                                            !lightTheme
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
                                            !lightTheme
                                        )
                                    }

                                    setSystemBarAppearance(colorPalette.isDark)

                                    appearance = appearance.copy(
                                        colorPalette = if (!isPicthBlack) colorPalette else colorPalette.copy(
                                            background0 = Color.Black,
                                            background1 = Color.Black,
                                            background2 = Color.Black,
                                            background3 = Color.Black,
                                            background4 = Color.Black,
                                            text = Color.White
                                        ),
                                        typography = appearance.typography.copy(if (!isPicthBlack) colorPalette.text else Color.White),
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
                    appearance = appearance.copy(
                        colorPalette = customColorPalette(
                            appearance.colorPalette,
                            this@MainActivity,
                            isSystemInDarkTheme
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
            val uiType by rememberPreference(UiTypeKey, UiType.RiMusic)
            val isViMusic = uiType == UiType.ViMusic
            
            val bottomBarHeightPx = with(LocalDensity.current) { 240.dp.roundToPx().toFloat() } // Enough to hide floating bar + miniplayer
            var topBarOffsetHeightPx by remember { mutableFloatStateOf(0f) }
            var bottomBarOffsetHeightPx by remember { mutableFloatStateOf(0f) }
            
            LaunchedEffect(isLandscape, isViMusic) {
                topBarOffsetHeightPx = 0f
                bottomBarOffsetHeightPx = 0f
            }

            val density = LocalDensity.current
            val safeDrawingInsets = WindowInsets.safeDrawing
            val nestedScrollConnection = remember(isLandscape, isViMusic, density, safeDrawingInsets) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (!isLandscape || isViMusic) return Offset.Zero
                        
                        val statusBarsTopPx = safeDrawingInsets.getTop(density)
                        val topBarHeightPx = with(density) { 64.dp.roundToPx() } + statusBarsTopPx
                        
                        val delta = available.y
                        val previousTopOffset = topBarOffsetHeightPx
                        val newTopOffset = topBarOffsetHeightPx + delta
                        topBarOffsetHeightPx = newTopOffset.coerceIn(-topBarHeightPx.toFloat(), 0f)
                        val topConsumed = topBarOffsetHeightPx - previousTopOffset
                        
                        val newBottomOffset = bottomBarOffsetHeightPx - delta
                        bottomBarOffsetHeightPx = newBottomOffset.coerceIn(0f, bottomBarHeightPx)
                        
                        return Offset(0f, topConsumed)
                    }
                }
            }

            val topBarOffsetState = derivedStateOf { topBarOffsetHeightPx }
            val bottomBarOffsetState = derivedStateOf { bottomBarOffsetHeightPx }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(finalAppearance.colorPalette.background0)
                    .nestedScroll(nestedScrollConnection)
            ) {


                val density = LocalDensity.current
                val windowsInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                val bottomDp = with(density) { windowsInsets.getBottom(density).toDp() }

                val playerSheetState = rememberPlayerSheetState(
                    dismissedBound = 0.dp,
                    collapsedBound = Dimensions.collapsedPlayer + bottomDp,
                    expandedBound = maxHeight,
                )

                val playerState =
                    rememberModalBottomSheetState(skipPartiallyExpanded = true)

                val playerAwareWindowInsets by remember(
                    bottomDp,
                    playerSheetState.value
                ) {
                    derivedStateOf {
                        val bottom = playerSheetState.value.coerceIn(
                            bottomDp,
                            playerSheetState.collapsedBound
                        )

                        windowsInsets
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                            .add(WindowInsets(bottom = bottom))
                    }
                }

                var openTabFromShortcut = remember { -1 }
                if (intent.action in arrayOf(
                        action_songs,
                        action_albums,
                        actions_artists,
                        action_library,
                        action_search
                    )
                ) {
                    openTabFromShortcut =
                        when (intent?.action) {
                            action_songs -> HomeScreenTabs.Songs.index
                            action_albums -> HomeScreenTabs.Albums.index
                            actions_artists -> HomeScreenTabs.Artists.index
                            action_library -> HomeScreenTabs.Playlists.index
                            action_search -> -2
                                    else -> -1
                                }
                            intent.action = null
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
                             LocalAppearance provides finalAppearance,
                            LocalIndication provides ripple(bounded = true),
                            LocalRippleConfiguration provides rippleConfiguration,
                            LocalShimmerTheme provides shimmerTheme,
                            LocalPlayerServiceBinder provides binder,
                            LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                            LocalLayoutDirection provides LayoutDirection.Ltr,
                            LocalDownloadHelper provides downloadHelper,
                            LocalPlayerSheetState provides playerState,
                            LocalMonetCompat provides monet,
                            LocalPersistMap provides persistMap,
                            LocalPendingMiniPlayerAction provides pendingMiniPlayerAction,
                            LocalIsShowingLyrics provides isShowingLyrics,
                            LocalIsShowingVisualizer provides isShowingVisualizer,
                            LocalTopBarOffset provides topBarOffsetState,
                            LocalBottomBarOffset provides bottomBarOffsetState
                            //LocalInternetConnected provides internetConnected
                        ) {

                            AppNavigation(
                                navController = navController,
                                miniPlayer = {
                                    MiniPlayer(
                                        showPlayer = { showPlayer = true },
                                        hidePlayer = {
                                            coroutineScope.launch {
                                                if (playerState.isVisible) playerState.hide()
                                                showPlayer = false
                                            }
                                        },
                                        navController = navController
                                    )
                                },
                                openTabFromShortcut = openTabFromShortcut
                            )

                            checkIfAppIsRunningInBackground()


                            

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

                            PipEventContainer(
                                enable = true,
                                onPipOutAction = {
                                    showPlayer = false
                                    switchToAudioPlayer = false
                                }
                            ) {
                                CustomModalBottomSheet(
                                    showSheet = switchToAudioPlayer || showPlayer,
                                    onDismissRequest = {
                                        showPlayer = false
                                        switchToAudioPlayer = false
                                    },
                                    containerColor = finalAppearance.colorPalette.background0,
                                    contentColor = finalAppearance.colorPalette.background0,
                                    modifier = Modifier.fillMaxWidth(),
                                    sheetState = playerState,
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
                                    Player( navController ) { 
                                        coroutineScope.launch {
                                            if (playerState.isVisible) playerState.hide()
                                            showPlayer = false
                                            switchToAudioPlayer = false
                                        }
                                    }
                                }
                            }

                            CustomModalBottomSheet(
                                showSheet = isVideo && isVideoEnabled && showPlayer,
                                onDismissRequest = { showPlayer = false },
                                containerColor = finalAppearance.colorPalette.background0,
                                contentColor = finalAppearance.colorPalette.background0,
                                modifier = Modifier.fillMaxWidth(),
                                sheetState = playerState,
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
                val playerUpdateTrigger by binder?.playerUpdateTrigger?.collectAsState(0) ?: remember { mutableStateOf(0) }
                DisposableEffect(binder?.player, playerUpdateTrigger) {
                    val player = binder?.player ?: return@DisposableEffect onDispose { }

                    setDynamicPalette(player.currentMediaItem?.mediaMetadata?.artworkUri?.thumbnail(1000)?.toString())

                    if (!isPlayerInitialized) {
                        isPlayerInitialized = true
                        if (player.currentMediaItem == null) {
                            if (playerState.isVisible) {
                                showPlayer = false
                            }
                        } else {
                            if (launchedFromNotification) {
                                intent.replaceExtras(Bundle())
                                if (preferences.getBoolean(keepPlayerMinimizedKey, true))
                                    showPlayer = false
                                else showPlayer = true
                            } else {
                                showPlayer = false
                            }
                        }
                    }

                    val listener = object : Player.Listener {
                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && mediaItem != null) {
                                if (mediaItem.mediaMetadata.extras?.getBoolean("isFromPersistentQueue") != true) {
                                    if (preferences.getBoolean(keepPlayerMinimizedKey, true))
                                        showPlayer = false
                                    else showPlayer = true
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

            LaunchedEffect(intentUriData) {
                val uri = intentUriData ?: return@LaunchedEffect

                Toaster.n(
                    "${BuildConfig.APP_NAME} ${this@MainActivity.resources.getString( R.string.opening_url )}",
                    duration = Toast.LENGTH_LONG
                )

                lifecycleScope.launch(Dispatchers.Main) {
                    when (val path = uri.pathSegments.firstOrNull()) {
                        "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                            val browseId = "VL$playlistId"

                            if (playlistId.startsWith("OLAK5uy_")) {
                                Innertube.playlistPage(BrowseBody(browseId = browseId))
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
                            lifecycleScope.launch(Dispatchers.IO) {
                                val favorites = Database.songTable.allFavorites().first()
                                if (favorites.isNotEmpty()) {
                                    val mediaItems = favorites.map { it.asMediaItem }
                                    withContext(Dispatchers.Main) {
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
                                withContext(Dispatchers.Main) {
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

            // Close threads
            PlaybackDispatchers.STREAM_RESOLVER.close()
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

    private fun setSystemBarAppearance(isDark: Boolean) {
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
        const val action_copy_crash_log = "app.it.fast4x.rimusic.action.copy_crash_log"
    }


    override fun onMonetColorsChanged(
        monet: MonetCompat,
        monetColors: ColorScheme,
        isInitialChange: Boolean
    ) {
        val colorPaletteName =
            preferences.getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic)
        if (!isInitialChange && colorPaletteName == ColorPaletteName.MaterialYou) {
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

@OptIn(ExperimentalMaterial3Api::class)
val LocalPlayerSheetState =
    staticCompositionLocalOf<SheetState> { error("No player sheet state provided") }

//val LocalInternetConnected = staticCompositionLocalOf<Boolean> { error("No Network Status provided") }

val LocalPendingMiniPlayerAction = staticCompositionLocalOf<MutableState<PendingMiniPlayerAction?>> { error("No PendingMiniPlayerAction state provided") }
val LocalIsShowingLyrics = staticCompositionLocalOf<MutableState<Boolean>> { error("No LocalIsShowingLyrics provided") }
val LocalIsShowingVisualizer = staticCompositionLocalOf<MutableState<Boolean>> { error("No LocalIsShowingVisualizer provided") }
val LocalTopBarOffset = staticCompositionLocalOf<State<Float>> { mutableStateOf(0f) }
val LocalBottomBarOffset = staticCompositionLocalOf<State<Float>> { mutableStateOf(0f) }
