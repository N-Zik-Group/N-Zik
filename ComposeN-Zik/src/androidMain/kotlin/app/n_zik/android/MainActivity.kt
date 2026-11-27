package app.n_zik.android

import kotlin.system.exitProcess

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.kieronquinn.monetcompat.core.MonetActivityAccessException
import com.kieronquinn.monetcompat.core.MonetCompat
import com.kieronquinn.monetcompat.interfaces.MonetColorsChangedListener
import dev.kdrag0n.monet.theme.ColorScheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.components.onboarding.persistNextOnboardingStep
import app.n_zik.android.components.onboarding.readOnboardingStep
import app.n_zik.android.components.glass.GlassMenuHostState
import app.n_zik.android.components.settings.appearance.preview.ThemeStoreAlertWorker
import app.n_zik.android.components.theme.configuredApplicationAppearance
import app.n_zik.android.components.ui.app.MainActivityContent
import app.n_zik.android.components.ui.screens.home.initialShortcutAction
import app.n_zik.android.components.ui.screens.profiles.executeRegisteredProfileSwitch
import app.n_zik.android.components.ui.screens.rescue.LibraryStartupScreen
import app.n_zik.android.compose.persist.PersistMap
import app.n_zik.android.compose.persist.PersistMapOwner
import app.n_zik.android.core.backup.BackupManager
import app.n_zik.android.core.backup.LibraryStartupState
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.profiles.selectColdStorageProfile
import app.n_zik.android.core.network.client.applyProfileProxy
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.download.services.withProfileDownloadCommandOwner
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.enums.OnboardingStep
import app.n_zik.android.enums.PendingMiniPlayerAction
import app.n_zik.android.intents.DeferredLibraryIntents
import app.n_zik.android.intents.DeliverLibraryUri
import app.n_zik.android.intents.DrainDeferredLibraryIntents
import app.n_zik.android.intents.LibraryLinkDestination
import app.n_zik.android.intents.PendingLibraryNavigation
import app.n_zik.android.intents.consumeOpenPlayerExtras
import app.n_zik.android.intents.consumeProfileShortcutExtra
import app.n_zik.android.intents.consumeRewindReminderIntent
import app.n_zik.android.intents.initialLibraryUri
import app.n_zik.android.intents.isSelfConsumedShortcutAction
import app.n_zik.android.intents.libraryLinkDestination
import app.n_zik.android.intents.openPlayerTokenFromIntent
import app.n_zik.android.intents.profileShortcutTarget
import app.n_zik.android.intents.profileSwitchTarget
import app.n_zik.android.listentogether.inviteAwareShortcutAction
import app.n_zik.android.listentogether.listenTogetherInviteUri
import app.n_zik.android.listentogether.openListenTogetherInvite
import app.n_zik.android.listentogether.parseListenTogetherInvite
import app.n_zik.android.listentogether.saveListenTogetherInvite
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.diagnostics.PLAYBACK_DIAG_TAG
import app.n_zik.android.privacy.PermissionFeature
import app.n_zik.android.privacy.PermissionUsageJournal
import app.n_zik.android.startup.applyKeepScreenOnPreference
import app.n_zik.android.startup.initializeLaunchPreferences
import app.n_zik.android.startup.registerStartupBackCallback
import app.n_zik.android.startup.registerStartupCookieWarning
import app.n_zik.android.ui.components.InterfaceLayoutDirection
import app.n_zik.android.ui.screens.player.PlayerSheetState
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.utils.ToastRelay
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.forcePlay
import app.n_zik.android.utils.forcePlayFromBeginning
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.hideStatusBarKey
import app.n_zik.android.utils.intent
import app.n_zik.android.utils.invokeOnReady
import app.n_zik.android.utils.isAtLeastAndroid6
import app.n_zik.android.utils.isAtLeastAndroid8
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.readProfileIds
import app.n_zik.android.utils.saveProfileLastUsed
import app.n_zik.android.utils.setDefaultPalette
import app.n_zik.android.utils.shouldRecreateActivity
import app.n_zik.android.intents.remoteAlbumLinkRoute
import app.n_zik.android.intents.resolveRemoteTrackLink

@UnstableApi
class MainActivity :
    AppCompatActivity(),
    MonetColorsChangedListener,
    PersistMapOwner
{
    var downloadHelper = MyDownloadHelper

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is PlayerServiceModern.Binder) {
                this@MainActivity.binder = service
                if (widgetLocaleRefreshPending) {
                    widgetLocaleRefreshPending = false
                    service.refreshArtworkWidgets()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
        }

    }

    internal var forceLoadingShimmer by mutableStateOf(false)
        private set

    internal var binder by mutableStateOf<PlayerServiceModern.Binder?>(null)
        private set
    private var widgetLocaleRefreshPending = false
    private var intentUriData by mutableStateOf<Uri?>(null)

    // Observable so a shortcut tap while the app is already running (delivered via onNewIntent,
    // which never re-triggers onCreate) still recomposes the tab-navigation check below --
    // reading `intent.action` directly only ever saw the launch-time intent.
    // Not re-seeded from the launch intent when the activity is recreated (theme change, settings
    // import): the shortcut was already consumed and would otherwise pop the back stack to home.
    // Internal (not private) so the Robolectric lifecycle tests can assert the self-consumed
    // exclusion: a profile tap (or the debug updater backdoor) must never feed this state
    // (it would pop the back stack to home).
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

    // Debug-only backdoor (see [action_debug_open_updater]): the OTA updater screen has no
    // entry point in debug builds, so a debug build accepts the intent action and opens the
    // route directly. Set on cold start (onCreate) and warm start (onNewIntent), consumed
    // once by the navigation effect that opens the updater route.
    internal var openUpdaterDebugShortcut by mutableStateOf(false)

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

    internal var onboardingStep by mutableStateOf<OnboardingStep?>(null)
        private set

    internal fun advanceOnboarding() {
        val current = onboardingStep ?: return
        onboardingStep = persistNextOnboardingStep(current)
    }

    override val persistMap = PersistMap()

    private var _monet: MonetCompat? by mutableStateOf(null)
    internal val monet get() = _monet ?: throw MonetActivityAccessException()

    private val mutablePipState = mutableStateOf(false)
    internal val pipState: State<Boolean> get() = mutablePipState

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        PermissionUsageJournal.result(this, PermissionFeature.ONBOARDING, Manifest.permission.POST_NOTIFICATIONS, isGranted)
        if (!isGranted) {
            // If permission is denied, redirect to settings
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
        }
    }

    private var screenCapturePolicy: app.n_zik.android.privacy.ScreenCapturePolicy? = null

    private var libraryUiStarted = false
    private val deferredLaunchIntents = DeferredLibraryIntents()
    private var waitingColdLaunch = false
    private var awaitingDeferredNavigation = false
    internal var libraryIntentDelivery by mutableStateOf(0L)
        private set
    private var savedLibraryNavigation: Bundle? = null

    override fun onStart() {
        super.onStart()
        ThemeStoreAlertWorker.schedule(applicationContext)
        if (libraryUiStarted) bindLibraryUi()
    }

    private fun bindLibraryUi() {
        if (screenCapturePolicy == null) screenCapturePolicy = app.n_zik.android.privacy.ScreenCapturePolicy.bind(this)

        runCatching {
            bindService(intent<PlayerServiceModern>(), serviceConnection, Context.BIND_AUTO_CREATE)
        }.onFailure {
            Timber.tag("MainActivity").e("onStart bindService ${it.stackTraceToString()}")
        }
    }

    internal fun consumeRewindDeepLinks(intent: Intent?) {
        consumeRewindReminderIntent(
            intent,
            onDeck = { rewindDeckTarget = it },
            onPlaylist = { rewindPlaylistTarget = it },
        )
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

    override fun startService(service: Intent): ComponentName? =
        super.startService(withProfileDownloadCommandOwner(service))

    override fun startForegroundService(service: Intent): ComponentName? =
        super.startForegroundService(withProfileDownloadCommandOwner(service))

    /** Selects the tapped profile before storage binds, or performs the normal profile handoff. */
    internal fun consumeProfileShortcut(intent: Intent?, handoff: (String) -> Unit = ::handoffProfileShortcut) {
        val tapId = profileShortcutTarget(intent)
        val switchTarget = profileSwitchTarget(tapId, getActiveProfile(this), validProfileIds())
        consumeProfileShortcutExtra(intent)
        switchTarget?.let {
            if (!selectColdStorageProfile(this, it)) {
                handoff(it)
                return
            }
            saveProfileLastUsed(it, System.currentTimeMillis())
            Timber.tag("MainActivity").i("Profile shortcut (cold): active profile set to $it before launch")
        }
    }

    /**
     * Consumes the direct profile shortcut carried by [intent] — WARM path (app already
     * running, spec-profile-shortcuts): a tap on a different valid profile runs the whole
     * CAP-3 switch ([executeRegisteredProfileSwitch] — same wiring as the profiles page: cancel the
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
        switchTarget?.let(::handoffProfileShortcut)
    }

    private fun handoffProfileShortcut(profileId: String) {
        if (!executeRegisteredProfileSwitch(profileId, this)) return
        Timber.tag("MainActivity").i("Profile shortcut: switched to $profileId, exiting the process")
        exitProcess(0)
    }

    @ExperimentalTextApi
    @UnstableApi
    @ExperimentalComposeUiApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forceLoadingShimmer = BuildConfig.DEBUG && intent.getBooleanExtra("nzik_debug_force_shimmer", false)
        val startup = (application as? MainApplication)?.libraryStartup
        @Suppress("DEPRECATION")
        val savedLaunchIntents = savedInstanceState?.getParcelableArrayList<Intent>(DEFERRED_LAUNCH_INTENTS)
        savedLaunchIntents?.forEach(deferredLaunchIntents::offer)
        waitingColdLaunch = savedInstanceState?.getBoolean(WAITING_COLD_LAUNCH) == true
        awaitingDeferredNavigation = savedInstanceState?.getBoolean(AWAITING_DEFERRED_NAVIGATION) == true
        libraryIntentDelivery = savedInstanceState?.getLong(LIBRARY_INTENT_DELIVERY) ?: 0L
        savedLibraryNavigation = savedInstanceState?.getBundle(PENDING_LIBRARY_NAVIGATION)
        if (startup == null || startup.state.value == LibraryStartupState.Ready) {
            startLibraryUi(savedInstanceState)
            return
        }

        if (savedInstanceState == null) {
            waitingColdLaunch = true
        }
        awaitingDeferredNavigation = true
        setInterfaceContent {
            val state by startup.state.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppearance provides configuredAppearance(isSystemInDarkTheme())) {
                LibraryStartupScreen(state, startup::start, useAppAppearance = true)
            }
        }
        lifecycleScope.launch(NzikDispatchers.UI) {
            startup.state.first { it == LibraryStartupState.Ready }
            lifecycle.withStarted {
                startLibraryUi(savedInstanceState)
                bindLibraryUi()
                consumePendingStaleStartupWarning()
            }
        }
    }

    @OptIn(ExperimentalTextApi::class, UnstableApi::class, ExperimentalComposeUiApi::class)
    private fun startLibraryUi(savedInstanceState: Bundle?) {
        libraryUiStarted = true
        val isRestoredInstance = savedInstanceState != null && !waitingColdLaunch
        val restoredNavigation = savedLibraryNavigation
            ?.takeIf { isRestoredInstance }?.let(PendingLibraryNavigation::fromBundle)
        savedLibraryNavigation = null
        intentUriData = restoredNavigation?.uri ?: if (restoredNavigation == null) {
            initialLibraryUri(intent, savedInstanceState, waitingColdLaunch)
        } else null
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

        // Consume the direct shortcut before creating profile-bound activity content.
        consumeProfileShortcut(intent)
        // Gated on isRestoredInstance (like [shortcutIntentAction] below): a recreated/restored
        // activity is handed the kept launch intent again, whose action_profiles cannot be
        // stripped (it is the action, not an extra) — re-deriving the flag would re-open the
        // profiles page on every rotation / theme change / process-death restore.
        openProfilesShortcut = restoredNavigation?.openProfiles ?: (!isRestoredInstance && intent.action == action_profiles)
        // Debug-only updater backdoor: same restored-instance gating as [openProfilesShortcut]
        // (a re-created activity re-receives the kept launch intent).
        openUpdaterDebugShortcut = restoredNavigation?.openUpdater
            ?: (BuildConfig.DEBUG && !isRestoredInstance && intent.action == action_debug_open_updater)
        if (restoredNavigation != null) {
            shortcutIntentAction = restoredNavigation.shortcut
            rewindDeckTarget = restoredNavigation.rewindDeck
            rewindPlaylistTarget = restoredNavigation.rewindPlaylist
            openPlayerColdToken = restoredNavigation.coldPlayerToken
            openPlayerFromNotificationWarm = restoredNavigation.openWarmPlayer
            if (restoredNavigation.hasPending) awaitingDeferredNavigation = true
        }

        monet.invokeOnReady {
            if (!isDestroyed) startApp(isRestoredInstance)
        }

        // Rewind notification deep links (deck / playlist): consume the launch intent's
        // targets synchronously — app OFF path. A task restored after a process death
        // re-sends its original launch intent here; the persistent last-consumed markers
        // reject an already-consumed target while a fresh tap still opens (spec GH-275
        // follow-up). The app ON path is onNewIntent, which shares the same contract.
        if (restoredNavigation == null) consumeRewindDeepLinks(intent)
        // "Now playing" notification tap (spec-notification-click-opens-player): consume the
        // launch intent's open-player token synchronously — app OFF path. A positive token is
        // held in memory ([openPlayerColdToken]) and validated at deployment time by a live
        // comparison against the current service's token (staleness is the service's live
        // token — there is no persistent marker); the extra is stripped off the kept intent
        // either way.
        if (restoredNavigation == null) consumeOpenPlayerDeepLink(intent, cold = true)

        checkIfAppIsRunningInBackground()
        // Verify the backup location once startup has recovered the library.
        lifecycleScope.launch(NzikDispatchers.DATA) {
            BackupManager.verifyBackupLocation(this@MainActivity)
        }

        // Ask for notification permission if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionUsageJournal.request(this, PermissionFeature.ONBOARDING, Manifest.permission.POST_NOTIFICATIONS)
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    internal fun checkIfAppIsRunningInBackground() {
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
        mutablePipState.value = isInPictureInPictureMode
        Timber.tag("MainActivity").d("onPictureInPictureModeChanged: $isInPictureInPictureMode")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun startApp(isRestoredInstance: Boolean) {
        initializeLaunchPreferences()
        registerStartupCookieWarning()

        // The download manager may report stale downloads after the first window focus.
        lifecycleScope.launch {
            MyDownloadHelper.pendingStaleStartupCount.collect { count ->
                if (count > 0) consumePendingStaleStartupWarning()
            }
        }

        registerStartupBackCallback()

        val launchedFromNotification = intent?.extras?.getBoolean("fromWidget") ?: false

        Timber.tag("MainActivity").d("onCreate launchedFromNotification: $launchedFromNotification intent ${intent.action}")

        // Activity-consumed actions must stay out of the Home shortcut path, which pops the stack.
        if (!isRestoredInstance) {
            shortcutIntentAction = initialShortcutAction(inviteAwareShortcutAction(intent), isRestoredInstance)
                ?.takeUnless { isSelfConsumedShortcutAction(it) }
        }
        onboardingStep = readOnboardingStep()

        val startupPreferences = preferences
        applyKeepScreenOnPreference(window, startupPreferences)
        applyProfileProxy(this)

        setInterfaceContent { MainActivityContent(launchedFromNotification) }
    }

    @Composable
    internal fun DeliverPendingLibraryNavigation(navController: NavController) {
    DrainDeferredLibraryIntents(
        lifecycle = lifecycle,
        navController = navController,
        navigationReady = onboardingStep == null,
        queue = deferredLaunchIntents,
        awaitRestoredNavigation = {
            if (awaitingDeferredNavigation) {
                snapshotFlow(::libraryIntentConsumersIdle).first { it }
                awaitingDeferredNavigation = false
            }
        },
        consumersIdle = ::libraryIntentConsumersIdle,
        dispatch = ::consumeNewLibraryIntent,
    )
    DeliverLibraryUri(
        lifecycle = lifecycle,
        navController = navController,
        uri = intentUriData,
        delivery = libraryIntentDelivery,
        navigationReady = onboardingStep == null,
        isStillPending = { uri, delivery -> intentUriData == uri && libraryIntentDelivery == delivery },
        open = { openLibraryUri(navController, it) },
        onDelivered = { intentUriData = null },
    )
    }

    @OptIn(UnstableApi::class)
    private suspend fun openLibraryUri(navController: NavController, uri: Uri) {
        val destination = libraryLinkDestination(uri)
        if (destination !is LibraryLinkDestination.Theme && destination !is LibraryLinkDestination.Invite) {
            Toaster.n(
                getString(R.string.common_external_link_loading_format, BuildConfig.APP_NAME),
                duration = Toast.LENGTH_LONG,
            )
        }

        when (destination) {
            is LibraryLinkDestination.Theme -> destination.route?.let { route ->
                navController.navigate(route) { launchSingleTop = true }
            }
            is LibraryLinkDestination.Invite -> openListenTogetherInvite(navController, destination.roomCode)
            is LibraryLinkDestination.Route -> navController.navigate(destination.route)
            is LibraryLinkDestination.RemoteAlbum -> {
                navController.navigate(remoteAlbumLinkRoute(destination.playlistBrowseId))
            }
            LibraryLinkDestination.Favorites -> {
                val mediaItems = withContext(NzikDispatchers.DATA) {
                    Database.songTable.allFavorites().first().map { it.asMediaItem }
                }
                if (mediaItems.isNotEmpty()) {
                    val validBinder = snapshotFlow { binder }.filterNotNull().first()
                    validBinder.player.forcePlayFromBeginning(mediaItems)
                }
            }
            is LibraryLinkDestination.Video -> {
                val track = withContext(NzikDispatchers.DATA) { resolveRemoteTrackLink(destination.id) } ?: return
                if (track.explicit != false && preferences.getBoolean(parentalControlEnabledKey, false)) {
                    Toaster.w(R.string.parental_control_is_enabled)
                } else {
                    val validBinder = snapshotFlow { binder }.filterNotNull().first()
                    validBinder.player.forcePlay(track.item)
                }
            }
            LibraryLinkDestination.Unsupported -> Unit
        }
    }

    override fun onResume() {
        super.onResume()
        val mainApplication = application as? MainApplication
        mainApplication?.createNotificationChannels(resources)
        if (mainApplication?.refreshShortcutLanguage(this) == true) {
            app.n_zik.android.widget.PlaylistWidgetRefreshWorker.schedule(applicationContext)
            val player = binder
            if (player != null) player.refreshArtworkWidgets()
            else if (PlayerServiceModern.isRunning) widgetLocaleRefreshPending = true
            else lifecycleScope.launch(NzikDispatchers.DATA) {
                app.n_zik.android.widget.NZikWidgetManager.updateIdleWidgets(this@MainActivity)
            }
        }
        appRunningInBackground = false
    }

    override fun onPause() {
        super.onPause()
        appRunningInBackground = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        saveListenTogetherInvite(outState, intentUriData)
        val navigation = if (libraryUiStarted) {
            PendingLibraryNavigation(
                uri = intentUriData,
                shortcut = shortcutIntentAction,
                rewindDeck = rewindDeckTarget,
                rewindPlaylist = rewindPlaylistTarget,
                coldPlayerToken = openPlayerColdToken,
                openWarmPlayer = openPlayerFromNotificationWarm,
                openProfiles = openProfilesShortcut,
                openUpdater = openUpdaterDebugShortcut,
            ).toBundle()
        } else savedLibraryNavigation
        if (navigation != null) outState.putBundle(PENDING_LIBRARY_NAVIGATION, navigation)
        if (deferredLaunchIntents.hasPending) {
            outState.putParcelableArrayList(DEFERRED_LAUNCH_INTENTS, deferredLaunchIntents.snapshot())
        }
        if (!libraryUiStarted) {
            outState.putBoolean(WAITING_COLD_LAUNCH, waitingColdLaunch)
        }
        if (awaitingDeferredNavigation) outState.putBoolean(AWAITING_DEFERRED_NAVIGATION, true)
        outState.putLong(LIBRARY_INTENT_DELIVERY, libraryIntentDelivery)
        super.onSaveInstanceState(outState)
    }

    @UnstableApi
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (BuildConfig.DEBUG && intent.hasExtra("nzik_debug_force_shimmer")) {
            forceLoadingShimmer = intent.getBooleanExtra("nzik_debug_force_shimmer", false)
            if (intent.action == null && intent.data == null &&
                intent.extras?.keySet() == setOf("nzik_debug_force_shimmer")
            ) return
        }
        if (!libraryUiStarted || awaitingDeferredNavigation || !libraryIntentConsumersIdle() || deferredLaunchIntents.hasPending) {
            deferredLaunchIntents.offer(intent)
            return
        }
        consumeNewLibraryIntent(intent)
    }

    private fun libraryIntentConsumersIdle(): Boolean =
        intentUriData == null && shortcutIntentAction == null &&
            rewindDeckTarget == null && rewindPlaylistTarget == null &&
            !openProfilesShortcut && !openUpdaterDebugShortcut && !openPlayerFromNotificationWarm

    @OptIn(UnstableApi::class)
    private fun consumeNewLibraryIntent(intent: Intent) {
        // Identical consecutive payloads still need a fresh effect, even if Compose coalesces the clear.
        libraryIntentDelivery++
        intentUriData = listenTogetherInviteUri(intent)
            ?: (intent.data ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri())
                ?.takeUnless { parseListenTogetherInvite(it) != null }
        // The profile-shortcut actions have their own consumption (spec-profile-shortcuts) and
        // must not ride the home-tab shortcut state (which would pop the back stack to home).
        shortcutIntentAction = inviteAwareShortcutAction(intent)?.takeUnless { isSelfConsumedShortcutAction(it) }
        // Generic « Profiles » shortcut (warm): record the navigation target without
        // clobbering a pending one (the navigation effect consumes it, gated on onboarding).
        openProfilesShortcut = openProfilesShortcut || (intent.action == action_profiles)
        // Debug-only updater backdoor (warm, app ON path).
        openUpdaterDebugShortcut = openUpdaterDebugShortcut ||
            (BuildConfig.DEBUG && intent.action == action_debug_open_updater)
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

    // S1 (Phase 1 download fix, 2026-10-22): the stale-download startup warning is consumed
    // on the first window focus (see consumePendingStaleStartupWarning).
    private var hasWindowFocus = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        hasWindowFocus = hasFocus
        // Issue #881 (gh-881), Phase 3.1: correlates window focus with the lifecycle state —
        // a focused window whose lifecycle is below STARTED keeps Compose's frame clock paused.
        Timber.tag(PLAYBACK_DIAG_TAG).i(
            "UI_WINDOW_FOCUS hasFocus=%b lifecycle=%s", hasFocus, lifecycle.currentState
        )
        if (hasFocus && libraryUiStarted) consumePendingStaleStartupWarning()
    }

    /**
     * S1 (Phase 1 download fix, 2026-10-22): shows the startup stale-download warning parked
     * in [MyDownloadHelper.pendingStaleStartupCount] — the re-validation runs before the app
     * window is visible, where the system silently drops toasts. Shown (and the counter
     * reset) only while the window has focus.
     */
    private fun consumePendingStaleStartupWarning() {
        val count = MyDownloadHelper.pendingStaleStartupCount.value
        if (count > 0 && hasWindowFocus) {
            MyDownloadHelper.pendingStaleStartupCount.value = 0
            ToastRelay.warningWithResourceIdentity(
                resources.getQuantityString(R.plurals.downloads_stale_startup_removed_count, count, count),
                R.string.downloads_stale_startup_removed,
                count,
                duration = Toast.LENGTH_LONG
            )
        }
    }

    override fun onStop() {
        runCatching {
            if (libraryUiStarted) unbindService(serviceConnection)
        }.onFailure {
            Timber.tag("MainActivity").e("onStop unbindService ${it.stackTraceToString()}")
        }
        super.onStop()
    }

    @UnstableApi
    override fun onDestroy() {
        screenCapturePolicy?.close()
        screenCapturePolicy = null
        super.onDestroy()

        runCatching {
            _monet?.removeMonetColorsChangedListener(this)
            _monet = null
            // NzikDispatchers is process-lifetime and shared with PlayerServiceModern's
            // StreamResolver, which can outlive this Activity — never close it here.
        }.onFailure {
            Timber.tag("MainActivity").e("onDestroy removeMonetColorsChangedListener ${it.stackTraceToString()}")
        }

    }

    internal fun savePaletteForWidget(palette: ColorPalette) {
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

    private val systemBarAppearanceOwner = app.n_zik.android.components.theme.SystemBarAppearanceOwner()
    private data class PlayerSystemBarWindowState(val statusColor: Int, val navigationColor: Int,
        val statusContrast: Boolean, val navigationContrast: Boolean)
    private var playerSystemBarWindowState: PlayerSystemBarWindowState? = null

    private fun setInterfaceContent(content: @Composable () -> Unit) {
        setContent { InterfaceLayoutDirection(content) }
    }

    internal fun configuredAppearance(isSystemInDarkTheme: Boolean): Appearance =
        configuredApplicationAppearance(this, isSystemInDarkTheme) {
            Color(monet.getAccentColor(this))
        }

    @Composable
    internal fun PageSystemBarAppearance(glassMenuHost: GlassMenuHostState) {
        val background = LocalAppearance.current.colorPalette.background0
        val dark = app.n_zik.android.components.glass.neutralForeground(
            glassMenuHost.dialogs.systemBarSurface(background),
        ) == Color.White
        SideEffect { setSystemBarAppearance(dark) }
    }

    private fun setSystemBarAppearance(isDark: Boolean) {
        systemBarAppearanceOwner.updatePage(isDark)?.let { applySystemBarAppearance(it, updateVisibility = true) }
    }

    @Suppress("DEPRECATION") // Pre-edge-to-edge Android versions still honor these window colors.
    internal fun setPlayerSystemBarAppearance(appearance: app.n_zik.android.components.theme.SystemBarAppearance?) {
        if (appearance != null && playerSystemBarWindowState == null) {
            playerSystemBarWindowState = PlayerSystemBarWindowState(window.statusBarColor, window.navigationBarColor,
                Build.VERSION.SDK_INT >= 29 && window.isStatusBarContrastEnforced,
                Build.VERSION.SDK_INT >= 29 && window.isNavigationBarContrastEnforced)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        } else if (appearance == null) {
            playerSystemBarWindowState?.let { previous ->
                window.statusBarColor = previous.statusColor
                window.navigationBarColor = previous.navigationColor
                if (Build.VERSION.SDK_INT >= 29) {
                    window.isStatusBarContrastEnforced = previous.statusContrast
                    window.isNavigationBarContrastEnforced = previous.navigationContrast
                }
            }
            playerSystemBarWindowState = null
        }
        systemBarAppearanceOwner.updatePlayer(appearance)?.let { applySystemBarAppearance(it, updateVisibility = false) }
    }

    private fun applySystemBarAppearance(appearance: app.n_zik.android.components.theme.SystemBarAppearance, updateVisibility: Boolean) {
        val hideStatusBar = preferences.getBoolean(hideStatusBarKey, false)
        with(WindowCompat.getInsetsController(window, window.decorView)) {
            isAppearanceLightStatusBars = !appearance.statusIsDark
            isAppearanceLightNavigationBars = !appearance.navigationIsDark
            if (updateVisibility && hideStatusBar) {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.statusBars())
            } else if (updateVisibility) {
                show(WindowInsetsCompat.Type.statusBars())
            }
        }

        if (!isAtLeastAndroid6) {
            @Suppress("DEPRECATION")
            window.statusBarColor =
                (if (appearance.statusIsDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }

        if (!isAtLeastAndroid8) {
            @Suppress("DEPRECATION")
            window.navigationBarColor =
                (if (appearance.navigationIsDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }

    companion object {
        const val action_search = "app.n_zik.android.action.search"
        const val action_songs = "app.n_zik.android.action.songs"
        const val action_albums = "app.n_zik.android.action.albums"
        const val actions_artists = "app.n_zik.android.action.artists"
        const val action_library = "app.n_zik.android.action.library"
        // Profile shortcuts (spec-profile-shortcuts): the generic one opens the profiles page;
        // the direct per-profile one carries the stable profile ID in [EXTRA_PROFILE_ID] and
        // launches in it (cold) or switches to it (warm).
        const val action_profiles = "app.n_zik.android.action.profiles"
        const val action_profile = "app.n_zik.android.action.profile"

        // Debug-only backdoor to the OTA updater screen (unreachable in debug builds, where
        // IS_AUTOUPDATE is false and the About card is not clickable):
        // adb shell am start -n <pkg>/<activity> -a app.n_zik.android.action.debug_open_updater
        const val action_debug_open_updater = "app.n_zik.android.action.debug_open_updater"
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
            this@MainActivity.recreate()
        }
    }


}

private const val DEFERRED_LAUNCH_INTENTS = "libraryStartup.deferredLaunchIntents"
private const val WAITING_COLD_LAUNCH = "libraryStartup.waitingColdLaunch"
private const val AWAITING_DEFERRED_NAVIGATION = "libraryStartup.awaitingDeferredNavigation"
private const val LIBRARY_INTENT_DELIVERY = "libraryStartup.intentDelivery"
private const val PENDING_LIBRARY_NAVIGATION = "libraryStartup.pendingNavigation"

// Player rendering observes this flag to resume decorations when the activity returns.
var appRunningInBackground: Boolean by mutableStateOf(false)

val LocalPlayerServiceBinder = staticCompositionLocalOf<PlayerServiceModern.Binder?> { null }

val LocalPlayerAwareWindowInsets = staticCompositionLocalOf<WindowInsets> { TODO() }

val LocalDownloadHelper = staticCompositionLocalOf<MyDownloadHelper> { error("No Downloader provided") }

val LocalPlayerSheetState =
    staticCompositionLocalOf<PlayerSheetState> { error("No player sheet state provided") }

val LocalPendingMiniPlayerAction = staticCompositionLocalOf<MutableState<PendingMiniPlayerAction?>> { error("No PendingMiniPlayerAction state provided") }
val LocalIsShowingLyrics = staticCompositionLocalOf<MutableState<Boolean>> { error("No LocalIsShowingLyrics provided") }
val LocalIsShowingVisualizer = staticCompositionLocalOf<MutableState<Boolean>> { error("No LocalIsShowingVisualizer provided") }
val LocalTopBarOffset = staticCompositionLocalOf<State<Float>> { mutableStateOf(0f) }
val LocalBottomBarOffset = staticCompositionLocalOf<State<Float>> { mutableStateOf(0f) }
val LocalDownloadStatesMap = staticCompositionLocalOf<Map<String, app.n_zik.android.enums.DownloadedStateMedia>> { emptyMap() }

/**
 * Routes where the player sheet and mini-player slide out of view.
 */
internal fun isMiniplayerHiddenRoute(currentRoute: String?): Boolean =
    currentRoute?.let { route ->
        route == NavRoutes.rewind.name ||
            route.startsWith("${NavRoutes.rewind.name}?") ||
            route?.substringBefore('?') == NavRoutes.updater.name ||
            route == NavRoutes.notifications.name ||
            route.substringBefore('?') == NavRoutes.themeStore.name ||
            route.substringBefore('?') == NavRoutes.themeStoreNew.name ||
            route.substringBefore('/') == NavRoutes.themeStoreDetail.name ||
            route.substringBefore('/') == NavRoutes.themeStoreCreator.name ||
            route == NavRoutes.themeStoreCreatorSettings.name ||
            route.substringBefore('/') == NavRoutes.themeStoreSubmission.name
    } ?: false
