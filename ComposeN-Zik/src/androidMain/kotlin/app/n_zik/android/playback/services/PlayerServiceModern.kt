package app.n_zik.android.playback.services

import app.n_zik.android.appTextResources
import app.n_zik.android.playback.utils.playNextIfAvailable
import app.n_zik.android.utils.playbackDurationKey
import kotlin.math.roundToInt
import app.n_zik.android.playback.utils.playPreviousIfAvailable
import app.n_zik.android.playback.services.automotive.models.AutoAccessPolicy
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.activeProfileKey
import app.n_zik.android.utils.profilePreferences
import app.n_zik.android.utils.cropVideoThumbnailsKey
import app.n_zik.android.utils.uiRoundnessDpKey
import app.n_zik.android.utils.thumbnailRoundnessDpKey
import app.n_zik.android.utils.artistThumbnailRoundnessDpKey
import app.n_zik.android.core.network.fullOfflineModeKey
import app.n_zik.android.privacy.PermissionUsageJournal
import app.n_zik.android.privacy.PermissionFeature
import app.n_zik.android.privacy.JournalPermission
import app.n_zik.android.privacy.PermissionEventKind

import app.n_zik.android.playback.services.automotive.browse.handlers.isAutoHomeBrowsePreference
import app.n_zik.android.playback.services.automotive.browse.handlers.isAutoRewindPreference
import app.n_zik.android.playback.services.automotive.session.AutoSessionCallback
import app.n_zik.android.core.database.Database
import app.n_zik.android.listentogether.ListenTogetherClient
import app.n_zik.android.listentogether.ListenTogetherGuestGuardPlayer
import app.n_zik.android.listentogether.ListenTogetherManager
import app.n_zik.android.listentogether.ListenTogetherPlayerBridge
import app.n_zik.android.listentogether.listenTogetherGuestLock
import app.n_zik.android.core.sync.YouTubeSync

import app.n_zik.android.MainApplication
import app.n_zik.android.awaitLibraryReady
import app.n_zik.android.core.backup.LibraryStartupException
import app.n_zik.android.core.backup.LibraryStartupState
import app.n_zik.android.core.backup.isLibraryReady
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.coroutines.runPeriodically
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.content.pm.ServiceInfo
import androidx.annotation.MainThread
import androidx.car.app.connection.CarConnection
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioOffloadSupportProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import app.n_zik.android.bridge.AudioOutput
import app.n_zik.android.bridge.BridgeServerController
import app.n_zik.android.playback.audio.MuteAudioProcessor
import app.n_zik.android.playback.audio.MuteAudioProcessorChain
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import app.n_zik.android.playback.utils.isSmartShuffleRecommendation
import app.n_zik.android.playback.utils.spreadSmartShuffleRecommendations
import app.n_zik.android.playback.utils.withoutSmartShuffleSessionPrefix
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaNotification.Provider
import androidx.media3.session.MediaSession
import app.n_zik.android.repository.QuickPicksRepository
import app.n_zik.android.R
import app.n_zik.android.components.menu.menuLikeActionLabel
import app.n_zik.android.enums.DislikeMode
import app.n_zik.android.utils.excludeDislikedSongsKey

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import app.n_zik.android.components.song.isArtistIdForSong
import app.n_zik.android.utils.isGenericReleaseChannel
import app.n_zik.android.core.network.client.Store
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YouTubeSession
import app.n_zik.innertube.hasYouTubeAuthCookie
import io.ktor.client.call.body
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.android.MainActivity
import app.n_zik.android.appContext
import app.n_zik.android.cleanPrefix
import app.n_zik.android.enums.AudioQualityFormat
import app.n_zik.android.enums.DownloadQualityFormat
import app.n_zik.android.enums.downloadQualityFormatKey
import app.n_zik.android.enums.ImageQualityFormat
import app.n_zik.android.enums.DurationInMilliseconds
import app.n_zik.android.enums.ExoPlayerDiskCacheMaxSize
import app.n_zik.android.enums.ExoPlayerMinTimeForEvent
import app.n_zik.android.enums.NotificationButtons
import app.n_zik.android.enums.PauseBetweenSongs
import app.n_zik.android.enums.PresetsReverb
import app.n_zik.android.enums.QueueLoopType
import app.n_zik.android.extensions.audiovolume.AudioVolumeObserver
import app.n_zik.android.extensions.audiovolume.OnAudioVolumeChangedListener
import app.n_zik.android.core.notifications.channelId
import app.n_zik.android.core.notifications.ensureStartupNotificationChannel
import app.n_zik.android.core.profiles.profileStorageIdentity
import app.n_zik.android.core.profiles.ProfileDataDirNames
import app.n_zik.android.core.profiles.cacheDatabaseProvider
import app.n_zik.android.core.profiles.mediaCacheDir
import app.n_zik.android.core.profiles.profileSharedItems
import app.n_zik.android.core.profiles.LegacyMediaAccess
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.core.profiles.resolveProfileDataDirNames
import app.n_zik.android.extensions.discord.DiscordAdvancedSettings
import app.n_zik.android.extensions.discord.DiscordPresenceManager
import app.n_zik.android.extensions.discord.discordAdvancedSettingKeys
import app.n_zik.android.extensions.lastfm.LastFmScrobbleManager
import app.n_zik.lastfm.LastFm
import app.n_zik.android.isHandleAudioFocusEnabled
import app.n_zik.android.isPauseOnHeadphoneDisconnectEnabled
import app.n_zik.android.models.Event
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.diagnostics.DiagExoPlayer
import app.n_zik.android.playback.services.diagnostics.CrossfadeAudioDiagnostics
import app.n_zik.android.playback.services.diagnostics.PLAYBACK_DIAG_TAG
import app.n_zik.android.playback.services.diagnostics.PlaybackServiceDiagnostics
import app.n_zik.android.playback.services.diagnostics.discontinuityReasonName
import app.n_zik.android.playback.utils.BitmapProvider
import app.n_zik.android.playback.utils.NZikRadio
import app.n_zik.android.download.utils.DownloadValidator
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.download.services.MyDownloadService
import app.n_zik.android.playback.utils.SessionBitmapLoader
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.audioQualityFormatKey
import app.n_zik.android.utils.durationTextToMillis
import app.n_zik.android.utils.formatAsDuration
import app.n_zik.android.utils.imageQualityFormatKey
import app.n_zik.android.utils.audioReverbPresetKey
import app.n_zik.android.utils.bassboostEnabledKey
import app.n_zik.android.utils.bassboostLevelKey
import app.n_zik.android.utils.broadCastPendingIntent
import app.n_zik.android.utils.canPushToYTM
import app.n_zik.android.utils.closebackgroundPlayerKey
import app.n_zik.android.utils.discordPersonalAccessTokenKey
import app.n_zik.android.utils.encryptedPreferences
import app.n_zik.android.utils.exoPlayerCustomCacheKey
import app.n_zik.android.utils.exoPlayerDiskCacheMaxSizeKey
import app.n_zik.android.utils.exoPlayerMinTimeForEventKey
import app.n_zik.android.utils.fadeInEffect
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.isExplicit
import app.n_zik.android.utils.intent
import app.n_zik.android.utils.isAtLeastAndroid10
import app.n_zik.android.utils.isAtLeastAndroid6
import app.n_zik.android.utils.isAtLeastAndroid7
import app.n_zik.android.utils.discoverKey
import app.n_zik.android.utils.isPauseOnVolumeZeroEnabledKey
import app.n_zik.android.utils.loudnessBaseGainKey
import app.n_zik.android.utils.manageDownload
import app.n_zik.android.utils.minimumSilenceDurationKey
import app.n_zik.android.utils.notificationPlayerFirstIconKey
import app.n_zik.android.utils.notificationPlayerSecondIconKey
import app.n_zik.android.utils.pauseListenHistoryKey
import app.n_zik.android.utils.persistentQueueKey
import app.n_zik.android.utils.playNext
import app.n_zik.android.utils.playbackFadeAudioDurationKey
import app.n_zik.android.utils.playbackPitchKey
import app.n_zik.android.utils.playbackSpeedKey
import app.n_zik.android.utils.playbackVolumeKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.syncPushHistoryKey
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.utils.ytCookieExpiredKey
import app.n_zik.android.utils.putEnum
import app.n_zik.android.utils.queueLoopTypeKey
import app.n_zik.android.utils.resumePlaybackWhenDeviceConnectedKey
import app.n_zik.android.utils.setGlobalVolume
import app.n_zik.android.utils.showDownloadButtonBackgroundPlayerKey
import app.n_zik.android.utils.showLikeButtonBackgroundPlayerKey
import app.n_zik.android.utils.skipMediaOnErrorKey
import app.n_zik.android.utils.skipSilenceKey
import app.n_zik.android.utils.toggleRepeatMode
import app.n_zik.android.utils.shuffleQueue
import app.n_zik.android.utils.volumeNormalizationKey
import app.n_zik.android.utils.volumeBoostLevelKey
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.player.MenuQueueEdits
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import app.n_zik.android.core.utils.Toaster
import timber.log.Timber
import java.io.File
import java.util.IdentityHashMap
import kotlin.io.path.createTempDirectory
import kotlin.system.exitProcess
import android.os.Binder as AndroidBinder
import app.n_zik.android.utils.isDiscordPresenceEnabledKey
import app.n_zik.android.extensions.lastfm.isLastFmConfigKey
import app.n_zik.android.extensions.lastfm.isLastFmSetupKey
import app.n_zik.android.extensions.lastfm.isLastfmScrobblingEnabledKey
import app.n_zik.android.extensions.lastfm.lastfmSessionKey
import app.n_zik.android.BuildConfig
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.playback.services.automotive.models.ArtworkRevisions
import app.n_zik.android.playback.exceptions.ExplicitContentException
import app.n_zik.android.playback.exceptions.LoginRequiredException
import app.n_zik.android.playback.exceptions.UnmatchedSongException
import androidx.media3.datasource.HttpDataSource
import app.n_zik.android.utils.crossfadeGaplessKey
import app.n_zik.android.utils.crossfadeDurationKey
import androidx.compose.runtime.Stable
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.components.theme.dynamicThemeRespectBrightnessKey
import app.n_zik.android.widget.MusicWidgetReceiver
import app.n_zik.android.widget.TurntableWidgetReceiver
import app.n_zik.android.utils.crossfadeEnabledKey
import app.n_zik.android.utils.pauseBetweenSongsKey


const val LOCAL_KEY_PREFIX = "local:"

val MediaItem.isLocal get() = mediaId.contains(LOCAL_KEY_PREFIX)
val Song.isLocal get() = id.contains(LOCAL_KEY_PREFIX)

val Song.isUnmatched: Boolean
    get() = (id.length != 11 || (durationText == "00:00" && totalPlayTimeMs == 1L))
            && !id.startsWith(LOCAL_KEY_PREFIX)

/**
 * The resolved (cache dir, index DB name) of a profile's streaming media cache
 * (spec-profile-data-separation): [dir] is null when the media cache is Disabled —
 * the caller then uses a process-local temp dir named [tempDirName] that deletes itself
 * after close. [indexDbName] is the base `exoplayer_internal.db` when the media cache is
 * shared, the suffixed `exoplayer_internal_<id>.db` when it is separate.
 */
internal data class MediaCacheLocation(
    val dir: File?,
    val tempDirName: String,
    val indexDbName: String,
)

/**
 * The (cache dir, index DB name) of the ACTIVE profile's streaming media cache
 * (spec-profile-data-separation): the profile's own suffixed folder + suffixed index
 * when the media cache is separate, the base folder + base index when it is shared
 * (governed by the BASE profile's settings — see [mediaCacheDir]); [MediaCacheLocation.dir]
 * is null when the (effective) media cache is Disabled — the caller then uses a
 * process-local temp dir named [MediaCacheLocation.tempDirName] that deletes itself
 * after close. Extracted from [PlayerServiceModern.onCreate] so the resolution is
 * unit-testable.
 */
internal fun mediaCacheLocation(context: Context): MediaCacheLocation {
    val profileId = getActiveProfile(context)
    val names = resolveProfileDataDirNames(profileId, context.profileSharedItems(profileId))
    return MediaCacheLocation(mediaCacheDir(context, profileId), names.mediaCacheDir, names.mediaCacheIndexDb)
}

@UnstableApi
class PlayerServiceModern : MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback,
    SharedPreferences.OnSharedPreferenceChangeListener,
    OnAudioVolumeChangedListener {

    private val coroutineScope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
    private var medleyJob: Job? = null
    private val trackBoundaryPause = TrackBoundaryPause(CoroutineScope(coroutineScope.coroutineContext + NzikDispatchers.UI))
    private lateinit var mediaSession: MediaLibrarySession
    private val playbackReady = CompletableDeferred<Unit>()
    private val destructionCompleted = CompletableDeferred<Unit>()

    /**
     * One-shot "open player" token of the session activity (spec-notification-click-opens-player,
     * option B): minted ONCE per service instance — static for the session's lifetime, a service
     * restart mints a fresh one. Carried in the notification content intent's extra; the consumer
     * live-compares the tap token against this value at deployment: equal → legitimate tap
     * (including a second cold tap while the service lives); different → stale re-delivery
     * (process death → restarted service) → rejected. Exposed via [Binder.openPlayerToken].
     */
    private val openPlayerToken = System.currentTimeMillis()
    private var mediaLibrarySessionCallback: AutoSessionCallback =
        AutoSessionCallback(this, Database, MyDownloadHelper, playbackReady)
    lateinit var player: ExoPlayer
    private var internalEffectsSessionId = C.AUDIO_SESSION_ID_UNSET
    private var bassBoostReady = false
    private var reverbReady = false
    private var normalizationReady = false
    private var audioEffectPlayer: ExoPlayer? = null
    private var audioEffectListener: Player.Listener? = null
    private val audioEffectSession = AudioEffectSessionController { opened, sessionId ->
        sendAudioEffectIntent(opened, sessionId)
    }
    private val crossfadeAudioDiagnostics = IdentityHashMap<ExoPlayer, CrossfadeAudioDiagnostics>()
    val playerUpdateTrigger = MutableStateFlow(0)

    /**
     * Guarded delegation facade for every EXTERNAL entry point (UI via [Binder.player],
     * notification buttons, MediaSession / lockscreen / automotive) — Listen Together
     * guest-lock policy (spec-listen-together-guest-lock-hardening, spine AD-1/AD-2/AD-6).
     * Rebuilt on every crossfade swap so the guard always tracks the current [player] (AD-5).
     * The raw [player] is reached only by the service's internal logic and the host sync
     * bridge [ListenTogetherPlayerBridge] (AD-4) — never by external callers.
     */
    private lateinit var guestGuardPlayer: ListenTogetherGuestGuardPlayer
    private val menuQueueEdits = MenuQueueEdits(
        currentPlayer = { if (::guestGuardPlayer.isInitialized && !isDestroying) guestGuardPlayer else null },
        setShuffleOrder = { order -> player.setShuffleOrder(DefaultShuffleOrder(order, System.currentTimeMillis())) },
    )
    private var guestGuardPlayerTarget: Player? = null

    /**
     * Issue #881 (gh-881), Phase 3 Fix B (review patch): the seek coalescer backing the current
     * [guestGuardPlayer]. Held by the service (not the guard) so it can be detached when the
     * facade is rebuilt on a crossfade swap — without the reference the orphaned wrapper's
     * settle job could fire against the fading player (released later by `cleanupCrossfade()`,
     * which bypasses the wrapper chain entirely).
     */
    private var currentSeekCoalescer: SeekCoalescingPlayer? = null

    /** Rebuilds the notification when the guest lock toggles (guest: only play/pause visible). */
    private var guestLockObserverJob: Job? = null
    /**
     * Issue #866 (gh-866): re-renders the command buttons (media button preferences) whenever the
     * radio starts/stops, so its icon + label follow [NZikRadio.isRadioActive] — explicit start/stop
     * as well as the implicit auto-fill path. Same observation pattern as [guestLockObserverJob].
     */
    private var radioStateObserverJob: Job? = null
    /**
     * Issue #866 (gh-866): transient "shuffle registered" confirmation — when any shuffle
     * button is pressed (Android Auto / media notification / in-app UI), every shuffle
     * button's icon shows `R.drawable.shuffle_ok` for `SHUFFLE_OK_FLASH_MS`, then the base
     * icon is restored. Compose state so the in-app buttons re-render reactively; the AA /
     * notification path re-renders explicitly via `updateDefaultNotification()`.
     */
    private val shuffleOkFlashActive = mutableStateOf(false)
    private var shuffleOkFlashJob: Job? = null

    lateinit var cache: Cache
    private var boundStreamingCache: ActiveStreamingCleanup.BoundCache? = null
    lateinit var downloadCache: Cache
    private lateinit var audioVolumeObserver: AudioVolumeObserver
    private lateinit var bitmapProvider: BitmapProvider
    private var volumeNormalizationJob: Job? = null

    private val encryptedPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == isDiscordPresenceEnabledKey || key == discordPersonalAccessTokenKey) {
            updateDiscordPresenceManager()
        }
        // A reset can remove advanced settings in the same edit that disables presence.
        if (key in discordAdvancedSettingKeys && encryptedPreferences.getBoolean(isDiscordPresenceEnabledKey, false)) {
            discordPresenceManager?.onAdvancedSettingsChanged()
        }
        if (isLastFmSetupKey(key)) {
            maybeSetupLastFmScrobbleManager()
        }
        // Sub-options apply at runtime without recreating the manager
        if (isLastFmConfigKey(key)) {
            lastFmScrobbleManager?.onConfigChanged()
        }
    }
    private var isPersistentQueueEnabled: Boolean = false
    private var isclosebackgroundPlayerEnabled = false
    private var audioManager: AudioManager? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null


    /**
     * Discord presence
     */
    private var discordPresenceManager: DiscordPresenceManager? = null

    /** Item 5: last playback speed seen (upstream parity MS L464/L2886) — detects speed changes in onPlaybackParametersChanged to re-send the presence. */
    private var lastPlaybackSpeed = 1.0f

    /**
     * Last.fm scrobbling
     */
    private var lastFmScrobbleManager: LastFmScrobbleManager? = null

    var loudnessEnhancer: LoudnessEnhancer? = null
    private var binder = Binder()
    private var bassBoost: BassBoost? = null
    private var reverbPreset: PresetReverb? = null
    private var showLikeButton = true
    private var showDownloadButton = true

    lateinit var audioQualityFormat: AudioQualityFormat
    lateinit var imageQualityFormat: ImageQualityFormat
    private val sleepTimer = PlaybackSleepTimer(coroutineScope)
    lateinit var nzikRadio: NZikRadio

    internal class CurrentMediaItem {
        // Media3 equality excludes Bundle contents. Publish link-only changes too.
        private class Snapshot(val item: MediaItem?)
        private val state = MutableStateFlow(Snapshot(null))
        val changes = state.map { it.item }
        var value: MediaItem?
            get() = state.value.item
            set(item) {
                if (item !== value) state.value = Snapshot(item)
            }
    }

    internal val currentMediaItem = CurrentMediaItem()

    private val currentSong = notificationSongStates(currentMediaItem.changes) { songId ->
        Database.songTable.findById(songId)
    }.stateIn(coroutineScope, SharingStarted.Lazily, NotificationSongState(null, null))

    var currentSongStateDownload = MutableStateFlow(Download.STATE_STOPPED)

    private var connectivityJob: Job? = null
    private val isNetworkAvailable = MutableStateFlow(true)
    private val waitingForNetwork = MutableStateFlow(false)

    var preferredDeviceId: Int? = null

    private var notificationManager: NotificationManager? = null

    private lateinit var notificationActionReceiver: NotificationActionReceiver

    private var carConnection: LiveData<Int>? = null
    private var wasProjectingToCar = false
    private val carConnectionObserver = Observer<Int> { type ->
        val disconnected = wasProjectingToCar && type == CarConnection.CONNECTION_TYPE_NOT_CONNECTED
        wasProjectingToCar = type == CarConnection.CONNECTION_TYPE_PROJECTION
        if (disconnected && guestGuardPlayer.playWhenReady && isPauseOnHeadphoneDisconnectEnabled()) {
            Timber.tag("PlayerServiceModern").d("Android Auto disconnected: pausing playback per user setting")
            guestGuardPlayer.pause()
        }
    }

    private val audioBecomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && isPauseOnHeadphoneDisconnectEnabled()) {
                Timber.tag("PlayerServiceModern").d("Audio becoming noisy: pausing playback per user setting")
                // Guarded facade: for a locked Listen Together guest the pause intent is
                // registered, so the local pause survives the host's next PLAY heartbeat
//
                // (spec-listen-together-guest-lock-hardening). Hosts / out-of-room: passthrough.
                guestGuardPlayer.pause()
            }
        }
    }

    /** Issue #881 (gh-881): screen on/off markers — correlate the stall with the off→on cycle reported by the reporter (spec S5). */
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> Timber.tag(PLAYBACK_DIAG_TAG).i("SCREEN_ON")
                Intent.ACTION_SCREEN_OFF -> Timber.tag(PLAYBACK_DIAG_TAG).i("SCREEN_OFF")
            }
        }
    }

    private val playbackDiagnosticsListener = PlaybackServiceDiagnostics(
        coroutineScope = coroutineScope,
        player = { player },
        isNetworkAvailable = { isNetworkAvailable.value },
    )

    @kotlin.OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        if (isLibraryReady()) {
            initializePlayback()
            return
        }
        coroutineScope.launch(NzikDispatchers.UI) {
            try {
                awaitLibraryReady()
            } catch (error: LibraryStartupException) {
                Timber.tag("PlayerServiceModern").e(error, "Library is unavailable for playback")
                val startup = (application as? MainApplication)?.libraryStartup
                if (startup == null) {
                    removeStartupPlaybackNotification()
                    playbackReady.completeExceptionally(error)
                    stopSelf()
                    return@launch
                }
                // A bound controller can keep this service alive through a recovery retry.
                startup.state.first { state ->
                    if (state is LibraryStartupState.Failed) removeStartupPlaybackNotification()
                    state == LibraryStartupState.Ready
                }
            }
            if (!playbackInitializationStarted) initializePlaybackAndReplay()
        }
    }

    private var playbackInitializationStarted = false
    private data class PendingPlaybackStart(val intent: Intent?, val flags: Int, val id: Int)
    private val pendingPlaybackStarts = ArrayDeque<PendingPlaybackStart>()
    private var startupForeground = false

    private fun initializePlaybackAndReplay() {
        initializePlayback()
        while (pendingPlaybackStarts.isNotEmpty()) {
            val pending = pendingPlaybackStarts.removeFirst()
            dispatchPlaybackStart(pending.intent, pending.flags, pending.id)
        }
        if (startupForeground) {
            coroutineScope.launch(NzikDispatchers.UI) {
                delay(30_000L)
                // A failed resume might never emit a Media3 notification update.
                removeStartupPlaybackNotification()
            }
        }
    }

    private fun removeStartupPlaybackNotification() {
        if (!startupForeground) return
        startupForeground = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun showStartupPlaybackNotification() {
        val channel = ensureStartupNotificationChannel(this, NotificationChannelId, getActiveProfile(this))
        val strings = appTextResources()
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(strings.getString(R.string.player_title))
            .setContentText(strings.getString(R.string.backup_startup_preparing_library))
            .setContentIntent(PendingIntent.getActivity(
                this,
                OpenLibraryStartupActivityRequestCode,
                Intent(this, MainActivity::class.java).addFlags(FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            ))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        startupForeground = true
    }

    @kotlin.OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun initializePlayback() {
        playbackInitializationStarted = true
        isRunning = true

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Network status observation
        connectivityJob?.cancel()
        connectivityJob = coroutineScope.launch {
            NetworkQualityHelper.observeConnection(this@PlayerServiceModern).collect { isAvailable ->
                isNetworkAvailable.value = isAvailable
                Timber.tag("PlayerServiceModern").d("network status: $isAvailable")
                // Issue #881 (gh-881): network markers correlate the stall with connectivity loss
                // after the screen off→on cycle (spec S5).
                Timber.tag(PLAYBACK_DIAG_TAG).i("NETWORK_%s", if (isAvailable) "AVAILABLE" else "LOST")
                if (isAvailable && waitingForNetwork.value) {
                    waitingForNetwork.value = false
                    if (player.playWhenReady && player.playbackState != Player.STATE_IDLE) {
                        withContext(NzikDispatchers.UI) {
                            binder.gracefulPlay()
                        }
                    }
                }
            }
        }

        // DEFAULT NOTIFICATION PROVIDER MODDED (the only notification type)
        setMediaNotificationProvider(CustomMediaNotificationProvider(this)
            .apply {
                setSmallIcon(R.drawable.ic_launcher_monochrome)
            }
        )

        runCatching {
            bitmapProvider = BitmapProvider(
                scope = coroutineScope,
                colorProvider = { isSystemInDarkMode ->
                    if (isSystemInDarkMode) Color.BLACK else Color.WHITE
                }
            )
        }.onFailure {
            Timber.tag("PlayerServiceModern").e("Failed init bitmap provider ${it.stackTraceToString()}")
        }

        boundSessionPreferences = preferences
        boundSessionPreferences?.registerOnSharedPreferenceChangeListener(this)
        profilePreferences.registerOnSharedPreferenceChangeListener(sessionProfileListener)
        DataStoreUtils.prefs(this).registerOnSharedPreferenceChangeListener(autoRewindPreferenceListener)

        isPersistentQueueEnabled = preferences.getBoolean(persistentQueueKey, false)

        audioQualityFormat = preferences.getEnum(audioQualityFormatKey, AudioQualityFormat.Auto)
        imageQualityFormat = preferences.getEnum(imageQualityFormatKey, ImageQualityFormat.Auto)
        Timber.tag("NZik_Network").i("PlayerServiceModern: Initialized with Audio Quality: $audioQualityFormat, Image Quality: $imageQualityFormat, Download Quality: ${preferences.getEnum(downloadQualityFormatKey, DownloadQualityFormat.Auto)}")

        showLikeButton = preferences.getBoolean(showLikeButtonBackgroundPlayerKey, true)
        showDownloadButton = preferences.getBoolean(showDownloadButtonBackgroundPlayerKey, true)

        val cacheSize =
            preferences.getEnum(exoPlayerDiskCacheMaxSizeKey, ExoPlayerDiskCacheMaxSize.`2GB`)

        val cacheEvictor = when (cacheSize) {
            ExoPlayerDiskCacheMaxSize.Unlimited -> NoOpCacheEvictor()

            ExoPlayerDiskCacheMaxSize.Custom -> {
                val customCacheSize = preferences.getInt(exoPlayerCustomCacheKey, 32) * 1000 * 1000L
                LeastRecentlyUsedCacheEvictor(customCacheSize)
            }

            else -> LeastRecentlyUsedCacheEvictor(cacheSize.bytes)
        }

        // The streaming cache of the ACTIVE profile (spec-profile-data-separation): its own
        // suffixed folder + index when separate, the base folder + index when shared.
        val ownerId = getActiveProfile(applicationContext)
        val owner = profileStorageIdentity(applicationContext, ownerId)
        val location = mediaCacheLocation(applicationContext)

        cache = LegacyMediaAccess.reader(applicationContext, ownerId, ProfileDataItem.MEDIA_CACHE)
            ?: run {
                val cacheDir = location.dir ?: createTempDirectory(location.tempDirName).toFile()
                cacheDir.mkdirs()
                SimpleCache(cacheDir, cacheEvictor, cacheDatabaseProvider(applicationContext, location.indexDbName)).also {
                    if (owner != null && location.dir != null) {
                        val expected = checkNotNull(cacheDir.parentFile).canonicalFile.resolve(cacheDir.name)
                        if (cacheDir.canonicalFile == expected) {
                            boundStreamingCache = ActiveStreamingCleanup.BoundCache(owner, expected, it)
                        }
                    }
                }
            }
        downloadCache = MyDownloadHelper.getDownloadCache(applicationContext)

        // Pre-load persisted stream client data into playbackDataCache
        // so StatsForNerds shows correct streamClient after cache wipe
        coroutineScope.launch {
            PlaybackDataStore.loadStreamClients(applicationContext).forEach { (videoId, streamClient) ->
                playbackDataCache.putIfAbsent(videoId, PlaybackData(
                    streamUrl = "",
                    format = null,
                    loudnessDb = null,
                    videoDetails = null,
                    playbackTracking = null,
                    streamExpiresInSeconds = null,
                    streamClient = streamClient,
                ))
            }
        }


        // Metrolist model: each player keeps its own ExoPlayer-allocated audio session;
        // effects follow the active player via explicit session open/close broadcasts.
        player = DiagExoPlayer(
            createPlaybackEngineBuilder(
                context = this,
                mediaSourceFactory = createMediaSourceFactory(),
                renderersFactory = createRendersFactory(),
                handleAudioFocus = isHandleAudioFocusEnabled(),
            )
                .setSeekBackIncrementMs(5000)
                .setSeekForwardIncrementMs(5000)
                .build()
                .apply {
                    addListener(this@PlayerServiceModern)
                    addAnalyticsListener(PlaybackStatsListener(false, this@PlayerServiceModern))
                },
            onTransportRequested = ::cancelTrackBoundaryPauseFor,
        )
        attachCrossfadeAudioDiagnostics(player)
        bindAudioEffectPlayer(player)

        // Guarded delegation facade for all external entry points (spec-listen-together-guest-lock-hardening,
        // spine AD-1): MediaSession / lockscreen / automotive commands route through it, so a
        // Listen Together guest can only play/pause. The "all commands" behavior (pre-Android-13
        // compatibility) is preserved inside the guard.
        guestGuardPlayer = createGuestGuardPlayer(player)

        mediaLibrarySessionCallback.apply {
            binder = this@PlayerServiceModern.binder
            toggleLike = ::toggleLike
            toggleDownload = ::toggleDownload
            toggleRepeat = ::toggleRepeat
            toggleShuffle = ::toggleShuffle
            startRadio = ::startRadio
            callPause = binder::gracefulPause
            actionSearch = ::actionSearch
            // Issue #866 (gh-866): discover command button (notification + AA overflow).
            toggleDiscover = ::toggleDiscover
        }

        // Build the media library session on the guarded facade (AD-6).
        // The GLOBAL session activity is the "now playing" notification's content
        // intent (spec-notification-click-opens-player, option B): media3 1.10.1 hard-wires
        // the notification content intent to the global session activity (no overridable
        // hook — DefaultMediaNotificationProvider.createNotification is final), so it points
        // at MainActivity with the one-shot "open player" token ([openPlayerToken], minted
        // once per service instance — static for the session's lifetime; a service restart
        // mints a fresh token and the MainActivity consumer live-compares against it).
        // Dedicated request code (501, NOT 0): request code 0 + a filter-equal intent is the
        // SAME system PendingIntent slot as NZikWidgetManager.getOpenAppIntent() (request
        // code 0, bare Intent to MainActivity) — Intent.filterEquals ignores extras and
        // activity flags, so the widget's 1-second refresh loop overwrote this token ~1s
        // after playback started and the tap delivered an intent WITHOUT the token.
        // Repo precedent: 500 = PlaylistWidgetManager's open-app Pi.
        // Android Auto is protected separately — its companion controller is sent null via
        // the per-controller override in AutoSessionCallback (the official media3
        // per-controller API), so AA falls back to the standard session UI: browse root
        // (QuickPicks + the five categories) by default, search on demand, now-playing only
        // while something actually plays — never the in-app screen (search bar + "your
        // selection").
        val sessionPlayer = SessionArtworkPlayer(mediaLibrarySessionCallback.wrapPlayer(guestGuardPlayer))
        if (::mediaSession.isInitialized) {
            // Keep the token and legacy browser binder acquired during startup.
            mediaSession.player = sessionPlayer
        } else {
            mediaSession = buildLibrarySession(sessionPlayer)
        }
        bindPlaybackTrackIdentity(sessionPlayer)

        mediaLibrarySessionCallback.observeRepository(mediaSession) {
            (mediaSession.player as? SessionArtworkPlayer)?.refreshArtwork()
            updateDefaultNotification()
        }
        ResolvedSessionDuration.bind(coroutineScope, playerUpdateTrigger) { mediaSession.player }
        bindStoredDurationSelections(coroutineScope, playerUpdateTrigger) { guestGuardPlayer }

        observeSessionArtwork()

        player.skipSilenceEnabled = preferences.getBoolean(skipSilenceKey, false)
        player.addListener(this@PlayerServiceModern)
        // Issue #881 (gh-881): transition journal + stall-watchdog sampling (spec S1/S2)
        player.addListener(playbackDiagnosticsListener)

        player.repeatMode = preferences.getEnum(queueLoopTypeKey, QueueLoopType.Default).type
        refreshTrackBoundaryPause()

        // Internal service init MUST bypass the guard (spine AD-7) — the raw player is used.
        applyPlaybackTuning(preferences)

        // Register for media notifications without binding the service to itself.
        addSession(mediaSession)

        audioVolumeObserver = AudioVolumeObserver(this)
        audioVolumeObserver.register(AudioManager.STREAM_MUSIC, this)

        MyDownloadHelper.getDownloadManager(this)

        notificationActionReceiver = NotificationActionReceiver()

        // Listen Together guest lock (AD-6): rebuild the notification whenever the lock toggles —
        // a guest only sees play/pause (next/prev/shuffle hidden); the guarded available commands
        // are re-announced so the lockscreen / automotive transport buttons follow.
        guestLockObserverJob?.cancel()
        guestLockObserverJob = coroutineScope.launch(NzikDispatchers.UI) {
            snapshotFlow { listenTogetherGuestLock.value }
                .distinctUntilChanged()
                .collect { guestLocked ->
                    if (guestLocked) trackBoundaryPause.cancel()
                    refreshTrackBoundaryPause()
                    scheduleCrossfade()
                    updateDefaultNotification()
                    guestGuardPlayer.announceAvailableCommandsChanged()
                }
        }

        QuickPicksRepository.refreshIfNeeded()

        nzikRadio = NZikRadio(this, binder, coroutineScope) { indices ->
            player.setShuffleOrder(DefaultShuffleOrder(indices, System.currentTimeMillis()))
        }

        // Issue #866 (gh-866): observe the radio state from the service side (NZikRadio stays
        // untouched) — re-render the command buttons on every start/stop (explicit or implicit
        // auto-fill) so the radio button's icon + label follow NZikRadio.isRadioActive.
        radioStateObserverJob?.cancel()
        radioStateObserverJob = coroutineScope.launch(NzikDispatchers.UI) {
            snapshotFlow { nzikRadio.isRadioActive }
                .distinctUntilChanged()
                .collect {
                    mediaLibrarySessionCallback.refreshAccessCommands(mediaSession)
                    updateDefaultNotification()
                }
        }

        val filter = IntentFilter().apply {
            addAction(Action.play.value)
            addAction(Action.pause.value)
            addAction(Action.playPause.value)
            addAction(Action.next.value)
            addAction(Action.previous.value)
            addAction(Action.like.value)
            addAction(Action.download.value)
            addAction(Action.playradio.value)
            addAction(Action.discover.value)
            addAction(Action.shuffle.value)
            addAction(Action.repeat.value)
            addAction(Action.search.value)
        }

        ContextCompat.registerReceiver(
            this,
            notificationActionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        if (isAtLeastAndroid7) {
            val noisyFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            ContextCompat.registerReceiver(
                this,
                audioBecomingNoisyReceiver,
                noisyFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        runCatching {
            carConnection = CarConnection(applicationContext).type
            carConnection?.observeForever(carConnectionObserver)
        }.onFailure {
            Timber.tag("PlayerServiceModern").w(it, "Failed to observe Android Auto connection")
        }

        // Issue #881 (gh-881): screen on/off markers for stall correlation (spec S5)
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        observeNotificationState()

        maybeRestorePlayerQueue()

        maybeResumePlaybackWhenDeviceConnected()

        maybeBassBoost()

        maybeReverb()

        if (isPersistentQueueEnabled) {
            coroutineScope.launch(NzikDispatchers.UI) { runPeriodically(30_000) { maybeSavePlayerQueue() } }
        }

        updateDiscordPresenceManager()

        /**
         * Last.fm scrobbling
         */
        maybeSetupLastFmScrobbleManager()

        encryptedPreferences.registerOnSharedPreferenceChangeListener(encryptedPrefsListener)
        playbackReady.complete(Unit)
    }

    internal fun observeSessionArtwork() {
        sessionArtworkJob?.cancel()
        sessionArtworkJob = coroutineScope.launch(NzikDispatchers.UI) {
            ArtworkRevisions.changes.collect {
                (mediaSession.player as? SessionArtworkPlayer)?.refreshChangedArtwork()
                mediaLibrarySessionCallback.refreshChangedArtwork(mediaSession)
            }
        }
    }

    private fun bindPlaybackTrackIdentity(sessionPlayer: SessionArtworkPlayer) {
        playbackIdentityJob?.cancel()
        val profile = getActiveProfile(this)
        if (Database.FILE_NAME != Database.fileNameForProfile(profile)) return
        playbackIdentityJob = player.bindPlaybackIdentity(
            scope = coroutineScope,
            songs = Database.songTable,
            ownsProfile = { getActiveProfile(this) == profile },
            unknownArtist = { appTextResources().getString(R.string.metadata_unknown_artist) },
            originalId = { preferences.getString("old_browse_id_$it", null) },
            onCurrentItem = { item -> refreshCurrentTrackIdentity(item, sessionPlayer) },
        )
    }

    private fun buildLibrarySession(sessionPlayer: Player): MediaLibrarySession {
        val openPlayerSessionActivity = PendingIntent.getActivity(
            this,
            OpenPlayerSessionActivityRequestCode,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_PLAYER_TOKEN, openPlayerToken),
            FLAG_ACTIVITY_NEW_TASK or PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return MediaLibrarySession.Builder(this, sessionPlayer, mediaLibrarySessionCallback)
            .setBitmapLoader(
                SessionBitmapLoader(this, coroutineScope, 250 * resources.displayMetrics.density.toInt())
            )
            .setPeriodicPositionUpdateEnabled(false)
            .setSessionActivity(openPlayerSessionActivity)
            .build()
    }

    private fun updateDiscordPresenceManager() {
        discordPresenceManager?.onStop()
        discordPresenceManager = null
        if (!encryptedPreferences.getBoolean(isDiscordPresenceEnabledKey, false)) return
        val token = encryptedPreferences.getString(discordPersonalAccessTokenKey, null)
            ?.takeIf(String::isNotEmpty) ?: return
        discordPresenceManager = DiscordPresenceManager(
            context = this,
            getToken = { token },
            getAdvancedSettings = { DiscordAdvancedSettings.read(encryptedPreferences) },
        )
    }

    /** Creates (or tears down) the LastFM scrobble manager based on the current settings: toggle ON + session key + API keys all required. */
    private fun maybeSetupLastFmScrobbleManager() {
        val enabled = encryptedPreferences.getBoolean(isLastfmScrobblingEnabledKey, false)
        val sessionKey = encryptedPreferences.getString(lastfmSessionKey, null).orEmpty()
        val ready = enabled &&
            sessionKey.isNotEmpty() &&
            BuildConfig.LASTFM_API_KEY.isNotEmpty() &&
            BuildConfig.LASTFM_API_SECRET.isNotEmpty()

        if (ready) {
            LastFm.initialize(BuildConfig.LASTFM_API_KEY, BuildConfig.LASTFM_API_SECRET)
            LastFm.sessionKey = sessionKey
            if (lastFmScrobbleManager == null) {
                lastFmScrobbleManager = LastFmScrobbleManager(context = this)
            }
        } else {
            lastFmScrobbleManager?.destroy()
            lastFmScrobbleManager = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!playbackInitializationStarted) {
            if (isLibraryReady()) {
                initializePlaybackAndReplay()
            } else {
                pendingPlaybackStarts.addLast(PendingPlaybackStart(intent, flags, startId))
                if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
                    if (!startupForeground) showStartupPlaybackNotification()
                    val startup = (application as? MainApplication)?.libraryStartup
                    if (startup?.state?.value is LibraryStartupState.Failed) startup.start()
                }
                // Re-delivering an already replayed play/pause toggle could reverse its action.
                return START_STICKY
            }
        }
        return dispatchPlaybackStart(intent, flags, startId)
    }

    private fun dispatchPlaybackStart(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == MusicWidgetReceiver.ACTION_UPDATE_WIDGET ||
            intent?.action == TurntableWidgetReceiver.ACTION_UPDATE_TURNTABLE_WIDGET
        ) {
            refreshArtworkWidgets()
        }
        return try {
            super.onStartCommand(intent, flags, startId)
        } catch (e: Exception) {
            Timber.tag("PlayerServiceModern").e(e, "Failed to start service safely (ForegroundServiceStartNotAllowedException)")
            START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (!playbackInitializationStarted && isLibraryReady()) initializePlaybackAndReplay()
        return super.onBind(intent) ?: binder.takeIf { playbackInitializationStarted }
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        if (!playbackInitializationStarted) return
        // The Media3 provider now replaces or removes the startup notification.
        startupForeground = false
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        if (isDestroying || playbackReady.isCancelled) return null
        if (!playbackInitializationStarted && isLibraryReady()) initializePlaybackAndReplay()
        if (!::mediaSession.isInitialized) {
            // Legacy MediaBrowser needs a session binder and a synchronous root on connect.
            // Keep Play available for saved queues so Media3 can wait for resumption.
            val commands = if (preferences.getBoolean(persistentQueueKey, false)) {
                Player.Commands.Builder()
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_SET_MEDIA_ITEM)
                    .build()
            } else Player.Commands.EMPTY
            val waitingPlayer = object : SimpleBasePlayer(Looper.getMainLooper()) {
                override fun getState(): State = State.Builder().setAvailableCommands(commands).build()

                override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> =
                    Futures.immediateVoidFuture()
            }
            mediaSession = buildLibrarySession(waitingPlayer)
        }
        return mediaSession
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
            scheduleTrackBoundaryResume()
        } else {
            trackBoundaryPause.cancel()
        }
        maybeSavePlayerQueue()
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        refreshTrackBoundaryPause()
        scheduleCrossfade()
        updateDefaultNotification()
        preferences.edit {
            putEnum(queueLoopTypeKey, QueueLoopType.from(repeatMode))
        }
    }




    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats
    ) {
        try {
            val mediaItem =
                eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem

            val totalPlayTimeMs = playbackStats.totalPlayTimeMs
            val songId = mediaItem.mediaId.split("/").lastOrNull() ?: mediaItem.mediaId

            val minTimeForEvent =
                preferences.getEnum(exoPlayerMinTimeForEventKey, ExoPlayerMinTimeForEvent.`20s`)

            val pauseHistory = preferences.getBoolean(pauseListenHistoryKey, false)

            // Local DB history (gated by pauseListenHistoryKey; Listen Together items are
            // recorded only when the user enabled the LT history option — their metadata
//
            // belongs to the host app, so only a browseId-only placeholder Song row is
            // written for the Event foreign key, no host metadata)
            val isListenTogether = ListenTogetherPlayerBridge.isListenTogetherItem(mediaItem)
            val listenTogetherHistoryEnabled =
                DataStoreUtils.getBoolean(this, ListenTogetherClient.PREF_HISTORY, false)

            if (ListenTogetherPlayerBridge.shouldRecordLocalHistory(
                    isListenTogether, pauseHistory, listenTogetherHistoryEnabled
                )
            ) {
                Database.asyncTransaction {
                    if ( totalPlayTimeMs > 5000 ) {
                        songTable.updateTotalPlayTime( songId, totalPlayTimeMs, true )
                    }

                    if ( totalPlayTimeMs > minTimeForEvent.asMillis ) {
                        if (isListenTogether) {
                            // Event has an FK to Song: ensure a browseId-only row exists
                            // (the app's own metadata repair fills in the names later)
                            songTable.insertIgnore( Song.makePlaceholder( songId ) )
                        } else {
                            insertIgnore(mediaItem)
                        }

                        eventTable.insertIgnore(
                            Event(
                                songId = songId,
                                timestamp = System.currentTimeMillis(),
                                playTime = totalPlayTimeMs
                            )
                        )
                    }
                }
            }

            val pushHistoryEnabled = preferences.getBoolean(syncPushHistoryKey, false)
            if (totalPlayTimeMs > minTimeForEvent.asMillis && pushHistoryEnabled &&
                isYouTubeSyncEnabled() && canPushToYTM() && isNetworkConnected(this@PlayerServiceModern)
            ) {
                coroutineScope.launch(NzikDispatchers.DATA) {
                    if (!canPushToYTM()) return@launch
                    val playbackData = playbackDataCache[songId]
                    val streamClient = playbackData?.streamClient
                    val originalUrl = playbackData?.playbackTracking?.videostatsPlaybackUrl?.baseUrl

                    if (originalUrl == null) {
                        Timber.tag("PlayerServiceModern").w("No playback tracking URL for $songId (streamClient=$streamClient); skipping YouTube history registration")
                        return@launch
                    }

                    var playbackUrl = originalUrl

                    // VISIONOS playback tracking URL is not compatible with WEB_REMIX registerPlayback.
                    // When stream was resolved by non-WEB_REMIX client, re-fetch via WEB_REMIX player request.
                    if (streamClient != "WEB_REMIX") {
                        Timber.tag("PlayerServiceModern").d("Stream client is $streamClient for $songId, re-fetching playback URL via WEB_REMIX")
                        runCatching {
                            Timber.tag("PlayerServiceModern").d("onPlaybackStatsReady: calling Innertube.player for $songId")
                            val httpResponse = Innertube.player(videoId = songId)
                            val playerResponse = httpResponse.body<app.n_zik.innertube.models.PlayerResponse>()
                            val remixUrl = playerResponse.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                            if (remixUrl != null) {
                                playbackUrl = remixUrl
                                Timber.tag("PlayerServiceModern").d("Got WEB_REMIX playback URL for $songId")
                            } else {
                                Timber.tag("PlayerServiceModern").w("WEB_REMIX player response has no playbackTracking for $songId, using original URL")
                            }
                        }.onFailure { e ->
                            Timber.tag("PlayerServiceModern").w(e, "Failed to fetch WEB_REMIX player response for $songId, using original URL")
                        }
                    }

                    // The tracking URL request may have suspended while the sync direction changed.
                    if (!canPushToYTM()) return@launch
                    Timber.tag("PlayerServiceModern").d("registerPlayback for $songId: streamClient=$streamClient")
                    runCatching {
                        Timber.tag("PlayerServiceModern").d("onPlaybackStatsReady: calling Innertube.registerPlayback for $songId")
                        // playbackUrl is guaranteed non-null here: originalUrl was null-checked above (return@launch),
                        // and the WEB_REMIX re-fetch only reassigns it when remixUrl != null
                        val response = Innertube.registerPlayback(url = playbackUrl!!, cpn = "")
                        val statusCode = response.status.value
                        if (statusCode !in 200..299) {
                            Timber.tag("PlayerServiceModern").w("registerPlayback failed for $songId: HTTP $statusCode")
                        } else {
                            Timber.tag("PlayerServiceModern").d("History pushed to YTM: $songId (status=$statusCode)")
                        }
                    }.onFailure { e ->
                        Timber.tag("PlayerServiceModern").e(e, "registerPlayback FAILED for $songId")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("PlayerServiceModern").e(e, "Error in onPlaybackStatsReady")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!playbackInitializationStarted) {
            super.onTaskRemoved(rootIntent)
            return
        }
        isclosebackgroundPlayerEnabled = preferences.getBoolean(closebackgroundPlayerKey, false)
        if (isclosebackgroundPlayerEnabled) {
            broadCastPendingIntent<NotificationDismissReceiver>().send()
            this.stopService(this.intent<MyDownloadService>())
            this.stopService(this.intent<PlayerServiceModern>())
            onDestroy()
        }
        super.onTaskRemoved(rootIntent)
    }

    @UnstableApi
    override fun onDestroy() {
        if (isDestroying) return
        isDestroying = true
        carConnection?.removeObserver(carConnectionObserver)
        carConnection = null
        trackBoundaryPause.cancel()
        medleyJob?.cancel()
        unregisterPlaybackDeviceCallback()
        pendingPlaybackStarts.clear()
        playbackReady.cancel()
        if (!playbackInitializationStarted) {
            if (::mediaSession.isInitialized) mediaSession.release()
            mediaLibrarySessionCallback.release()
            coroutineScope.cancel()
            super.onDestroy()
            destructionCompleted.complete(Unit)
            return
        }
        runCatching { maybeSavePlayerQueue(finalSnapshot = true) }.onFailure {
            Timber.tag("PlayerServiceModern").e(it, "Failed to capture queue during destruction")
        }
        isRunning = false
        playbackIdentityJob?.cancel()
        sessionRebindRecoveryJob?.cancel()
        sessionRebindRecoveryJob = null
        closeAudioEffectSession()
        cancelCrossfadeAndReset()
        runCatching {
            if (encryptedPreferences.getBoolean(isDiscordPresenceEnabledKey, false)) {
                Toaster.i(R.string.discord_presence_closed)
            }
            discordPresenceManager?.onStop()
            /**
             * Last.fm scrobbling cleanup
             */
            lastFmScrobbleManager?.destroy()
            lastFmScrobbleManager = null
            sessionArtworkJob?.cancel()
            profilePreferences.unregisterOnSharedPreferenceChangeListener(sessionProfileListener)
            boundSessionPreferences?.unregisterOnSharedPreferenceChangeListener(this)
            encryptedPreferences.unregisterOnSharedPreferenceChangeListener(encryptedPrefsListener)
            DataStoreUtils.prefs(this).unregisterOnSharedPreferenceChangeListener(autoRewindPreferenceListener)
            autoBrowseRefreshJob?.cancel()
            stopService(intent<MyDownloadService>())
            stopService(intent<PlayerServiceModern>())
            mediaLibrarySessionCallback.release()
            releasePlaybackPlayersOnDestroy()
            try{
                unregisterReceiver(notificationActionReceiver)
            } catch (e: Exception){
                Timber.tag("PlayerServiceModern").e("onDestroy unregisterReceiver notificationActionReceiver "+e.stackTraceToString())
            }
            try{
                unregisterReceiver(audioBecomingNoisyReceiver)
            } catch (e: Exception){
                Timber.tag("PlayerServiceModern").e("onDestroy unregisterReceiver audioBecomingNoisyReceiver "+e.stackTraceToString())
            }
            try{
                unregisterReceiver(screenStateReceiver)
            } catch (e: Exception){
                Timber.tag("PlayerServiceModern").e("onDestroy unregisterReceiver screenStateReceiver "+e.stackTraceToString())
            }
            boundStreamingCache = null
            cache.release()
            //downloadCache.release()
            loudnessEnhancer?.release()
            audioVolumeObserver.unregister()
            notificationManager?.cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
            notificationManager?.cancel(SleepTimerNotificationId)
            notificationManager = null
            guestLockObserverJob?.cancel()
            guestLockObserverJob = null
            radioStateObserverJob?.cancel()
            radioStateObserverJob = null
            shuffleOkFlashJob?.cancel()
            shuffleOkFlashJob = null
            // Issue #881 (gh-881): stop the stall-watchdog sampling loop
            playbackDiagnosticsListener.stopStallWatchdogSampling()
            coroutineScope.cancel()

        }.onFailure {
            Timber.tag("PlayerServiceModern").e("Failed onDestroy "+it.stackTraceToString())
        }
        // Teardown must also run when an unrelated, partially initialized dependency failed.
        sleepTimer.cancel()
        releasePlaybackPlayersOnDestroy()
        coroutineScope.cancel()
        super.onDestroy()
        destructionCompleted.complete(Unit)
    }

    private fun releasePlaybackPlayersOnDestroy() {
        if (playbackPlayersReleased) return
        // Detach the session before releasing either the current or retained raw player.
        if (::mediaSession.isInitialized && runCatching { mediaSession.release() }.isFailure) return
        playbackPlayersReleased = true
        currentSeekCoalescer?.detach()
        val owned = IdentityHashMap<ExoPlayer, Boolean>()
        if (::player.isInitialized) owned[player] = true
        pendingSessionPlayerRelease?.let { owned[it] = true }
        pendingSessionPlayerRelease = null
        owned.keys.forEach { target ->
            runCatching {
                target.removeListener(this)
                target.removeListener(playbackDiagnosticsListener)
                detachCrossfadeAudioDiagnostics(target)
                target.stop()
            }.onFailure { Timber.tag("PlayerServiceModern").e(it, "Failed to stop player during destruction") }
            runCatching {
                if (::guestGuardPlayer.isInitialized && guestGuardPlayerTarget === target) {
                    guestGuardPlayer.release()
                } else {
                    target.release()
                }
            }.onFailure { Timber.tag("PlayerServiceModern").e(it, "Failed to release player during destruction") }
        }
    }


    private var sessionArtworkJob: Job? = null
    private var playbackIdentityJob: Job? = null
    private var boundSessionPreferences: SharedPreferences? = null
    private var autoBrowseRefreshJob: Job? = null
    private val autoRewindPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (isAutoRewindPreference(key)) refreshAutomotiveBrowse()
    }
    private val sessionProfileListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == activeProfileKey) {
            menuQueueEdits.invalidate()
            trackBoundaryPause.cancel()
            boundSessionPreferences?.unregisterOnSharedPreferenceChangeListener(this)
            boundSessionPreferences = preferences
            boundSessionPreferences?.registerOnSharedPreferenceChangeListener(this)
            refreshTrackBoundaryPause()
            refreshSessionAccess()
            refreshArtworkWidgets()
        }
    }

    private fun refreshAutomotiveBrowse() {
        if (!::mediaSession.isInitialized || isDestroying) return
        autoBrowseRefreshJob?.cancel()
        autoBrowseRefreshJob = coroutineScope.launch(NzikDispatchers.UI) {
            // The editor and reset write several related keys as one operation.
            delay(100)
            mediaLibrarySessionCallback.invalidatePublication()
            mediaLibrarySessionCallback.publishAccess(mediaSession)
        }
    }

    private fun refreshSessionAccess() {
        if (!::mediaSession.isInitialized) return
        mediaLibrarySessionCallback.invalidatePublication()
        coroutineScope.launch(NzikDispatchers.UI) {
            (mediaSession.player as? SessionArtworkPlayer)?.refreshArtwork()
            mediaLibrarySessionCallback.publishAccess(mediaSession)
            updateDefaultNotification()
        }
    }

    private fun applyPlaybackTuning(source: SharedPreferences, key: String? = null) {
        if (key == null || key == playbackSpeedKey || key == playbackPitchKey) {
            player.playbackParameters = PlaybackParameters(
                source.getFloat(playbackSpeedKey, 1f),
                source.getFloat(playbackPitchKey, 1f),
            )
        }
        if (key == null || key == playbackVolumeKey) {
            val volume = source.getFloat(playbackVolumeKey, 1f)
            player.setGlobalVolume(volume)
            if (!isCrossfading) player.volume = volume
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (isAutoHomeBrowsePreference(key)) refreshAutomotiveBrowse()
        if (sharedPreferences != null &&
            (key == null || key == playbackSpeedKey || key == playbackPitchKey || key == playbackVolumeKey)
        ) {
            applyPlaybackTuning(sharedPreferences, key)
        }
        if (key == null || key == playbackDurationKey) refreshMedley()
        when (key) {
            dynamicThemeRespectBrightnessKey, colorPaletteModeKey,
            uiRoundnessDpKey, thumbnailRoundnessDpKey, artistThumbnailRoundnessDpKey, null -> refreshArtworkWidgets()
            cropVideoThumbnailsKey -> coroutineScope.launch(NzikDispatchers.UI) {
                refreshArtworkWidgets()
                (mediaSession.player as? SessionArtworkPlayer)?.refreshArtwork()
                mediaLibrarySessionCallback.refreshArtwork(mediaSession)
            }
            fullOfflineModeKey -> refreshSessionAccess()
            pauseBetweenSongsKey -> refreshTrackBoundaryPause()
            crossfadeEnabledKey, crossfadeDurationKey, crossfadeGaplessKey -> {
                if (key == crossfadeEnabledKey) refreshTrackBoundaryPause()
                armCrossfade(reusePendingPreparation = true)
            }
            // Issue #866 (gh-866): the discover state lives in this preference — NZikRadio
            // .toggleDiscover (in-app buttons, AA, notification), the settings toggle, and the
//
            // showReminderIfNeeded auto-correction all write it in-process, so one listener keeps
            // the discover command button icon (discover / discover_stop) in sync on every path.
            discoverKey, notificationPlayerFirstIconKey, notificationPlayerSecondIconKey ->
                updateDefaultNotification()

            persistentQueueKey -> if (sharedPreferences != null) {
                isPersistentQueueEnabled =
                    sharedPreferences.getBoolean(key, isPersistentQueueEnabled)
            }

            volumeNormalizationKey, loudnessBaseGainKey, volumeBoostLevelKey -> maybeNormalizeVolume()

            resumePlaybackWhenDeviceConnectedKey -> maybeResumePlaybackWhenDeviceConnected()

            skipSilenceKey -> if (sharedPreferences != null) {
                player.skipSilenceEnabled = sharedPreferences.getBoolean(key, false)
            }

            queueLoopTypeKey -> {
                player.repeatMode =
                    sharedPreferences?.getEnum(queueLoopTypeKey, QueueLoopType.Default)?.type
                        ?: QueueLoopType.Default.type
            }

            bassboostLevelKey, bassboostEnabledKey -> maybeBassBoost()
            audioReverbPresetKey -> maybeReverb()
            audioQualityFormatKey -> {
                audioQualityFormat = sharedPreferences?.getEnum(audioQualityFormatKey, AudioQualityFormat.Auto)
                    ?: AudioQualityFormat.Auto
                Timber.tag("NZik_Network").i("PlayerServiceModern: Audio Quality CHANGED to: $audioQualityFormat")
                
                // Force update UI and internal state
                updateDefaultNotification()
                updateWidgets()
            }
            imageQualityFormatKey -> {
                imageQualityFormat = sharedPreferences?.getEnum(imageQualityFormatKey, ImageQualityFormat.Auto)
                    ?: ImageQualityFormat.Auto
                Timber.tag("NZik_Network").i("PlayerServiceModern: Image Quality CHANGED to: $imageQualityFormat")
            }
            downloadQualityFormatKey -> {
                val downloadQuality = sharedPreferences?.getEnum(downloadQualityFormatKey, DownloadQualityFormat.Auto)
                    ?: DownloadQualityFormat.Auto
                Timber.tag("NZik_Network").i("PlayerServiceModern: Download Quality CHANGED to: $downloadQuality")
            }
        }
    }

    private var pausedByZeroVolume = false
    override fun onAudioVolumeChanged(currentVolume: Int, maxVolume: Int) {
        if (preferences.getBoolean(isPauseOnVolumeZeroEnabledKey, false)) {
            if (player.isPlaying && currentVolume < 1) {
                binder.gracefulPause()
                pausedByZeroVolume = true
            } else if (pausedByZeroVolume && currentVolume >= 1) {
                binder.gracefulPlay()
                pausedByZeroVolume = false
            }
        }
    }

    override fun onAudioVolumeDirectionChanged(direction: Int) {
    }

    private fun publishCurrentMediaItem(mediaItem: MediaItem?) {
        currentMediaItem.value = mediaItem
        updateDownloadedState()
    }

    private fun refreshCurrentTrackIdentity(item: MediaItem?, sessionPlayer: SessionArtworkPlayer? = null) {
        val previous = currentMediaItem.value
        if (item == null || item === previous || item.mediaId != previous?.mediaId) return
        publishCurrentMediaItem(item)
        sessionPlayer?.refreshTrackIdentity()
        updateWidgets()
        discordPresenceManager?.onPlayingStateChanged(
            item,
            player.isPlaying,
            player.currentPosition,
            player.duration,
            System.currentTimeMillis(),
            playbackSpeed = effectivePlaybackSpeed(),
            getCurrentPosition = { player.currentPosition },
            isPlayingProvider = { player.isPlaying },
        )
        lastFmScrobbleManager?.onTrackMetadataChanged(item)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        trackBoundaryPause.cancel()
        refreshTrackBoundaryPause()
        // P16 (2026-10-23): refresh the main-thread locality snapshot read by the load gate.
        val wasLocal = currentMediaIsLocal
        currentMediaIsLocal = mediaItem?.isLocal == true
        if ((wasLocal != currentMediaIsLocal || (mediaItem == null) != (currentMediaItem.value == null)) &&
            ::mediaSession.isInitialized
        ) {
            mediaLibrarySessionCallback.refreshAccessCommands(mediaSession)
            updateDefaultNotification()
        }
        if (!isInternalCrossfadeSeek && (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK || reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)) {
            cancelCrossfadeAndReset()
        }
        scheduleCrossfade()

        val audioQuality = audioQualityFormat
        val imageQuality = imageQualityFormat
        val downloadQuality = preferences.getEnum(downloadQualityFormatKey, DownloadQualityFormat.Auto)
        coroutineScope.launch(NzikDispatchers.DATA) {
            val networkQuality = NetworkQualityHelper.getCurrentNetworkQuality(this@PlayerServiceModern)
            val effectiveAudio = if (audioQuality == AudioQualityFormat.Auto) "Auto→$networkQuality" else audioQuality.name
            val effectiveImage = if (imageQuality == ImageQualityFormat.Auto) "Auto→$networkQuality" else imageQuality.name
            val effectiveDownload = if (downloadQuality == DownloadQualityFormat.Auto) "Auto→$networkQuality" else downloadQuality.name
            Timber.tag("NZik_Network").i("onMediaItemTransition - Network: $networkQuality | Audio: $effectiveAudio | Image: $effectiveImage | Download: $effectiveDownload")
        }

        // Clear recovery counter for the new media item (fresh start)
        mediaItem?.mediaId?.let { recoveryAttempts.remove(it) }

        // S2 (Phase 1 download fix, 2026-10-21): a track may be marked downloaded in
        // the index while its files were wiped by the OS/OEM (see DownloadValidator) —
//
        // [selfHealStaleDownloadEntry] re-checks the files at transition and removes
        // the stale index entry before the playback attempt; the transition still proceeds.
        mediaItem?.let { item ->
            selfHealStaleDownloadEntry(this, item, downloadCache)
        }

        // Safety net: detect if ExoPlayer auto-advanced despite skipMediaOnError being OFF.
        // This should not happen with our fixes, but if it does, show a visible warning.
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            && !preferences.getBoolean(skipMediaOnErrorKey, false)
            && player.playerError != null
        ) {
            Timber.tag("PlayerServiceModern").e("UNEXPECTED auto-skip detected with skipMediaOnError=OFF! error=${player.playerError?.errorCodeName}")
            Toaster.w(R.string.network_stream_unexpected_skip)
        }

        publishCurrentMediaItem(mediaItem)

        // Immediately persist the full MediaItem metadata to the DB.
        // This seeds the Song row with real data (title, artist, thumbnail)
//
        // before StreamResolver can insert a blank placeholder for FK satisfaction.
        // insertIgnore uses merge logic that preserves existing non-empty fields.
//
        // Listen Together items are excluded: they carry the host app's (Metrolist)
        // metadata, which can differ from N-Zik's YouTube resolution (e.g. a literal
//
        // "Titre" byline) — persisting it, and the YTM auto-fix that would follow,
        // would pollute the library with garbage Artist/Album rows. Their metadata
//
        // reaches the library through StreamResolver.upsertSongInfo instead: the
        // track is checked against YouTube first, and only the verified data is
//
        // upserted (the same flow as every regular stream).
        mediaItem?.takeUnless { ListenTogetherPlayerBridge.isListenTogetherItem(it) }?.let { item ->
            try {
                // Background Auto-Fix: Fetch missing album/artist metadata silently
                // Uses the service's coroutineScope so it gets cancelled properly on service destroy
                coroutineScope.launch(NzikDispatchers.DATA) {
                    // Immediately persist the full MediaItem metadata to the DB.
                    // This seeds the Song row with real data (title, artist, thumbnail)
//
                    // before StreamResolver can insert a blank placeholder for FK satisfaction.
                    val artwork = Database.prepareArtworkForInsert(item)
                    Database.transaction {
                        insertIgnore(item, autoFix = true, preparedArtwork = artwork, reconcileExistingIdentity = false)
                    }

                    try {
                        val songId = item.mediaId
                        // Skip local songs and invalid IDs
                        if (songId.startsWith("local:") || songId.length != 11) return@launch
                        
                        val albumId = item.mediaMetadata.extras?.getString("albumId")
                        val hasAlbum = !albumId.isNullOrBlank() ||
                            Database.albumTable.findBySongIdDirect(songId) != null
                        val hasArtist = Database.artistTable.findBySongIdDirect(songId)
                            .any { isArtistIdForSong(it.id, local = false) && !isGenericReleaseChannel(it.name) }
                        if (!hasAlbum || !hasArtist) upsertSongInfo(songId, albumId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag("auto_fix").e(e, "Error in background auto-fix")
                    }
                }
            } catch (e: Exception) {
                Timber.tag("PlayerServiceModern").e(e, "Error in onMediaItemTransition DB insert")
            }
        }

        nzikRadio.showReminderIfNeeded()
        maybeRecoverPlaybackError()
        maybeNormalizeVolume()
        loadFromRadio(reason)
        refreshArtworkWidgets()

        /**
         * Discord presence
         */
        val title = mediaItem?.mediaMetadata?.title ?: "<none>"
        val duration = player.duration
        val now = System.currentTimeMillis()
        if (encryptedPreferences.getBoolean(isDiscordPresenceEnabledKey, false)) {
            val token = encryptedPreferences.getString(discordPersonalAccessTokenKey, "")
            if (token?.isNotEmpty() == true) {
                // Item 8: live providers (not captured values) so the 5 s refresh tick
                // reads the real position/play state.
                discordPresenceManager?.onPlayingStateChanged(
                    mediaItem,
                    player.isPlaying,
                    player.currentPosition,
                    duration,
                    now,
                    playbackSpeed = effectivePlaybackSpeed(),
                    getCurrentPosition = { player.currentPosition },
                    isPlayingProvider = { player.isPlaying }
                )
            }
        }

        /**
         * Last.fm scrobbling
         */
        lastFmScrobbleManager?.onTrackTransition(mediaItem, duration)
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        refreshTrackBoundaryPause()
        scheduleCrossfade()
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            persistentQueue.onPlaylistChanged()
            maybeSavePlayerQueue(saveEmpty = true)
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateDefaultNotification()
        val currentIndex = player.currentMediaItemIndex
        if (shuffleModeEnabled && currentIndex in 0 until player.mediaItemCount) {
            val remaining = (0 until player.mediaItemCount).filter { it != currentIndex }.shuffled()
            val distributed = spreadSmartShuffleRecommendations(remaining) { index ->
                player.getMediaItemAt(index).isSmartShuffleRecommendation
            }
            val order = IntArray(player.mediaItemCount) { index ->
                if (index == 0) currentIndex else distributed[index - 1]
            }
            player.setShuffleOrder(DefaultShuffleOrder(order, System.currentTimeMillis()))
        }
        refreshTrackBoundaryPause()
        scheduleCrossfade()
    }



    /**
     * Discord presence
     */
    @UnstableApi
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            scheduleCrossfade()
            widgetUpdates.startWidgetUpdates()
        } else {
            if (!isCrossfading) discardCrossfadePreparation()
            widgetUpdates.stopWidgetUpdates()
        }
        
        val item = player.currentMediaItem
        val title = item?.mediaMetadata?.title ?: "<none>"
        val duration = player.duration
        val now = System.currentTimeMillis()
        
        if (encryptedPreferences.getBoolean(isDiscordPresenceEnabledKey, false)) {
            val token = encryptedPreferences.getString(discordPersonalAccessTokenKey, "")
            if (token?.isNotEmpty() == true) {
                // Item 8: live providers (not captured values) so the 5 s refresh tick
                // reads the real position/play state.
                discordPresenceManager?.onPlayingStateChanged(
                    item,
                    isPlaying,
                    player.currentPosition,
                    duration,
                    now,
                    playbackSpeed = effectivePlaybackSpeed(),
                    getCurrentPosition = { player.currentPosition },
                    isPlayingProvider = { player.isPlaying }
                )
            }
        }

        /**
         * Last.fm scrobbling
         */
        lastFmScrobbleManager?.onPlayingStateChanged(isPlaying, item, duration)
    }

    /** Refreshes crossfade timing and Discord presence after a speed change. */
    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        super.onPlaybackParametersChanged(playbackParameters)
        if (playbackParameters.speed != lastPlaybackSpeed) {
            lastPlaybackSpeed = playbackParameters.speed
            armCrossfade(reusePendingPreparation = true)
            if (encryptedPreferences.getBoolean(isDiscordPresenceEnabledKey, false)) {
                coroutineScope.launch {
                    delay(1000L)
                    // gh-881 (Phase 3, Fix D): player state must be read on the main thread
                    // (ExoPlayer verifyApplicationThread) — this scope runs on DATA (IO).
                    val activelyPlaying = withContext(NzikDispatchers.UI) {
                        player.playWhenReady && player.playbackState == Player.STATE_READY
                    }
                    if (activelyPlaying) {
                        discordPresenceManager?.onPlaybackSpeedChanged(playbackParameters.speed)
                    }
                }
            }
        }
    }

    /** Item 5: the effective playback speed — the player's live value, with the preference as fallback (the player value is set from the same pref at startup). */
    private fun effectivePlaybackSpeed(): Float {
        val speed = player.playbackParameters.speed
        return if (speed > 0f) speed else preferences.getFloat(playbackSpeedKey, 1f)
    }

    /**
     * Tracks recovery attempts per media item to prevent infinite retry loops.
     * Key = mediaId, Value = number of recovery attempts already made.
     */
    private val recoveryAttempts = mutableMapOf<String, Int>()
    private val MAX_RECOVERY_ATTEMPTS = 3
    private val MAX_RETRY_PER_SONG = 3
    private val RETRY_DELAY_MS = 1000L
    private val recentlyFailedSongs = mutableSetOf<String>()

    // --- Error classification helpers (mirrors Metrolist's MusicService) ---

    private fun getHttpResponseCode(error: PlaybackException): Int? {
        val rootCause = generateSequence<Throwable>(error) { it.cause }.firstOrNull {
            it is HttpDataSource.InvalidResponseCodeException
        }
        return (rootCause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
    }

    private fun isExpiredUrlError(error: PlaybackException): Boolean {
        val code = getHttpResponseCode(error)
        return code == 401 || code == 403 || code == 410
    }

    private fun isRangeNotSatisfiableError(error: PlaybackException): Boolean = getHttpResponseCode(error) == 416

    private fun isPageReloadError(error: PlaybackException): Boolean {
        val reloadKeywords = listOf(
            "page needs to be reloaded",
            "page must be reloaded",
            "reload",
            "pagina deve essere ricaricata",
            "la pagina deve essere ricaricata",
            "ricaricata",
        )
        val errorMessage = error.message?.lowercase() ?: ""
        val causeMessage = error.cause?.message?.lowercase() ?: ""
        val innerCauseMessage = error.cause?.cause?.message?.lowercase() ?: ""
        return reloadKeywords.any { keyword ->
            errorMessage.contains(keyword) || causeMessage.contains(keyword) || innerCauseMessage.contains(keyword)
        }
    }

    private fun isRemotePlaybackError(error: PlaybackException): Boolean =
        error.errorCode == PlaybackException.ERROR_CODE_REMOTE_ERROR

    private fun isAudioRendererError(error: PlaybackException): Boolean =
        error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
            (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
            (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED

    private fun isFileNotFoundError(error: PlaybackException): Boolean =
        error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND

    private fun hasExceededRetryLimit(mediaId: String): Boolean {
        val currentRetries = recoveryAttempts.getOrDefault(mediaId, 0)
        return currentRetries >= MAX_RETRY_PER_SONG || mediaId in recentlyFailedSongs
    }

    private fun markSongAsFailed(mediaId: String) {
        recentlyFailedSongs.add(mediaId)
        recoveryAttempts.remove(mediaId)
    }

    private fun performAggressiveCacheClear(mediaId: String) {
        Timber.tag("PlayerServiceModern").d("Performing aggressive cache clear for $mediaId")
        streamUrlCache.invalidate(mediaId)
        try {
            cache.removeResource(mediaId)
            Timber.tag("PlayerServiceModern").d("Cleared player cache for $mediaId")
        } catch (e: Exception) {
            Timber.tag("PlayerServiceModern").w(e, "Failed to clear player cache for $mediaId")
        }
        try {
            MyDownloadHelper.songUrlCache.invalidate(mediaId)
            Timber.tag("PlayerServiceModern").d("Cleared download URL cache for $mediaId")
        } catch (e: Exception) {
            Timber.tag("PlayerServiceModern").w(e, "Failed to clear download URL cache for $mediaId")
        }
    }

    // P16 (2026-10-23): main-thread snapshot of the current item's locality, read by the
    // load-gate lambda (ExoPlayer playback thread, where a player read would throw).
    @Volatile
    private var currentMediaIsLocal: Boolean = false

    // A3 (offline audit, 2026-10-22): stale-monitor hole — raw connection failure while the monitor
    // is stale-online; a fresh check decides. Remote media only (local I/O errors keep their path).
//
    // P16 (2026-10-23): the remote-media check is passed in — the load-gate lambda runs on the
    // ExoPlayer playback thread, where reading the player (currentMediaItem) throws (main-thread-only).
    private fun isStaleMonitorOfflineFailure(exception: Throwable, isRemoteMedia: Boolean): Boolean =
        isRemoteMedia &&
            isRawConnectionFailure(exception) &&
            !isNetworkConnected(applicationContext)

    override fun onPlayerError(error: PlaybackException) {
        trackBoundaryPause.cancel()
        super.onPlayerError(error)

        // Extract meaningful error detail from the exception chain
        val errorDetail = error.message
            ?: error.cause?.message
            ?: error.cause?.cause?.message
            ?: error.errorCodeName
        Timber.tag("PlayerServiceModern").e("onPlayerError code=${error.errorCode} (${error.errorCodeName}) detail=[$errorDetail] cause=${error.cause} rootCause=${error.cause?.cause}")

        val currentMediaId = player.currentMediaItem?.mediaId

        // S5/S7 (offline gate, 2026-10-21/22): a DNS failure at any depth or the fast-fail
        // marker must wait for the network, not the retry path; the decision is logged.
//
        // BEFORE the retry limit: an offline error must park + report, never be silently
        // skipped — the counter only increments after this gate (review P4, 2026-10-22).
        val dnsFailure = isDnsFailure(error)
        // A3 (offline audit, 2026-10-22): stale-monitor hole — a raw connection failure while
        // the monitor is stale-online used to burn the recovery storm ("Retrying…" → "failed").
        val staleMonitorOffline = isStaleMonitorOfflineFailure(error, player.currentMediaItem?.isLocal != true)
        if (shouldWaitForNetwork(error, isNetworkAvailable.value) || staleMonitorOffline) {
            Timber.tag("PlayerServiceModern").d(
                "Offline gate decision: networkAvailable=${isNetworkAvailable.value} " +
                    "dnsFailure=$dnsFailure " +
                    "staleMonitorOffline=$staleMonitorOffline " +
                    "connectionCode=${(error.cause?.cause as? PlaybackException)?.errorCode} " +
                    "summary=${diagnosticExceptionSummary(error)} -> waiting for network"
            )
            waitingForNetwork.value = true
            Toaster.noInternet()
            return
        }

        // Per-song retry limit — prevents infinite loops on permanently broken streams
        if (currentMediaId != null && hasExceededRetryLimit(currentMediaId)) {
            Timber.tag("PlayerServiceModern").w("Song $currentMediaId exceeded retry limit, skipping")
            markSongAsFailed(currentMediaId)
            if (player.hasNextMediaItem()) {
                player.playNext()
            } else {
                player.pause()
            }
            return
        }

        if (error.cause.isFatalCustomException()) {
            val rootCause = generateSequence<Throwable>(error) { it.cause }.firstOrNull { it is ExplicitContentException }
            if (rootCause != null) {
                Toaster.w(R.string.parental_control_is_enabled)
            }
            val unmatchedCause = generateSequence<Throwable>(error) { it.cause }.firstOrNull { it is UnmatchedSongException }
            if (unmatchedCause != null) {
                Toaster.w(R.string.player_matching_required_message)
            }
            // LoginRequiredException — show YouTube's reason (e.g. "Sign in to confirm your age")
            val loginCause = generateSequence<Throwable>(error) { it.cause }.firstOrNull { it is LoginRequiredException }
            if (loginCause != null) {
                val loginReason = loginCause.message?.removePrefix("Login required: ") ?: ""
                val isAgeRestricted = loginReason.contains("age", ignoreCase = true) ||
                    loginReason.contains("confirm your age", ignoreCase = true)
                val displayMessage = when {
                    isAgeRestricted -> getString(R.string.player_error_age_restricted_format, loginReason)
                    loginReason.isNotEmpty() -> getString(R.string.player_error_login_required_format, loginReason)
                    else -> getString(R.string.player_error_youtube_authentication)
                }
                Toaster.w(displayMessage)
                Timber.tag("PlayerServiceModern").w("LoginRequired: reason=$loginReason isAgeRestricted=$isAgeRestricted")
            }
            player.pause()
            return
        }

        // Recoverable errors: specialized handling per error type
        val recoverableErrors = listOf(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            PlaybackException.ERROR_CODE_REMOTE_ERROR,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        )

        if (error.errorCode in recoverableErrors && currentMediaId != null) {
            val attempts = recoveryAttempts.getOrDefault(currentMediaId, 0)

            if (attempts < MAX_RECOVERY_ATTEMPTS) {
                recoveryAttempts[currentMediaId] = attempts + 1
                Timber.tag("PlayerServiceModern").e("onPlayerError attempting recovery ${attempts + 1}/$MAX_RECOVERY_ATTEMPTS for ${error.errorCodeName} cause ${error.cause?.cause}")

                // --- Specialized error handling (mirrors Metrolist) ---

                when {
                    // 403 Forbidden — expired/forbidden stream URL
                    isExpiredUrlError(error) -> {
                        val httpCode = getHttpResponseCode(error)
                        Timber.tag("PlayerServiceModern").d("Handling $httpCode expired URL error for $currentMediaId")
                        // 401 = cookie expired server-side, not just stream URL
                        if (httpCode == 401 && hasYouTubeAuthCookie(Innertube.cookie)) {
                            MainApplication.cookieStatus = MainApplication.CookieStatus.EXPIRED
                            preferences.edit().putBoolean(ytCookieExpiredKey, true).apply()
                            Innertube.useLoginForBrowse = false
                            Innertube.useLoginForSearch = false
                            Store.setSession(YouTubeSession(null, null, null, null))
                            Timber.tag("PlayerServiceModern").w("Cookie expired (401) — marking session as EXPIRED")
                            Toaster.w(R.string.account_youtube_session_expired)
                        }
                        streamUrlCache.invalidate(currentMediaId)
                        try {
                            cache.removeResource(currentMediaId)
                        } catch (error: Exception) {
                            Timber.tag("PlayerServiceModern").w("Unable to evict cached audio during recovery (%s)", error.javaClass.simpleName)
                        }
                        val streamClient = playbackDataCache[currentMediaId]?.streamClient ?: "WEB_REMIX"
                        markClientFailed(streamClient, currentMediaId)
                        coroutineScope.launch {
                            if (InnerTubeXPlayer.refreshAfterStreamRejection()) {
                                clearAllFailures()
                            }
                        }
                        markClientFailed("WEB_REMIX", currentMediaId)
                    }

                    // 416 Range Not Satisfiable — cached data doesn't match stream size
                    isRangeNotSatisfiableError(error) -> {
                        Timber.tag("PlayerServiceModern").d("Handling 416 range error for $currentMediaId — retrying from position 0")
                        performAggressiveCacheClear(currentMediaId)
                    }

                    // Page reload error — YouTube internal state issue
                    isPageReloadError(error) -> {
                        Timber.tag("PlayerServiceModern").d("Handling page reload error for $currentMediaId")
                        performAggressiveCacheClear(currentMediaId)
                    }

                    // Remote playback error — treat as expired URL
                    isRemotePlaybackError(error) -> {
                        Timber.tag("PlayerServiceModern").d("Handling remote playback error for $currentMediaId — treating as expired URL")
                        streamUrlCache.invalidate(currentMediaId)
                        try { cache.removeResource(currentMediaId) } catch (error: Exception) {
                            Timber.tag("PlayerServiceModern").w("Unable to evict cached audio during recovery (%s)", error.javaClass.simpleName)
                        }
                        val streamClient = playbackDataCache[currentMediaId]?.streamClient ?: "WEB_REMIX"
                        markClientFailed(streamClient, currentMediaId)
                        coroutineScope.launch {
                            if (InnerTubeXPlayer.refreshAfterStreamRejection()) {
                                clearAllFailures()
                            }
                        }
                        markClientFailed("WEB_REMIX", currentMediaId)
                    }

                    // Audio renderer error — corrupted audio track state
                    isAudioRendererError(error) -> {
                        Timber.tag("PlayerServiceModern").d("Handling audio renderer error for $currentMediaId — extra delay")
                        streamUrlCache.invalidate(currentMediaId)
                        try { cache.removeResource(currentMediaId) } catch (error: Exception) {
                            Timber.tag("PlayerServiceModern").w("Unable to evict cached audio during recovery (%s)", error.javaClass.simpleName)
                        }
                    }

                    // File not found — cache eviction or corruption
                    isFileNotFoundError(error) -> {
                        Timber.tag("PlayerServiceModern").d("Handling file-not-found error for $currentMediaId")
                        performAggressiveCacheClear(currentMediaId)
                    }

                    // Generic recoverable error
                    else -> {
                        performAggressiveCacheClear(currentMediaId)
                    }
                }

                // Delay varies by error type: audio renderer needs longer, page reload needs 2x
                val retryDelay = when {
                    isAudioRendererError(error) -> RETRY_DELAY_MS * 3
                    isPageReloadError(error) -> RETRY_DELAY_MS * 2
                    else -> RETRY_DELAY_MS
                }

                // 416 and file-not-found: seek to 0 to avoid range issues
                val seekToZero = isRangeNotSatisfiableError(error) || isFileNotFoundError(error)

                // Save playWhenReady BEFORE pausing - pause() clears it
                val wasPlaying = player.playWhenReady
                player.pause()
                coroutineScope.launch(NzikDispatchers.UI) {
                    delay(retryDelay)
                    val currentIndex = player.currentMediaItemIndex
                    if (currentIndex != C.INDEX_UNSET) {
                        player.seekTo(currentIndex, if (seekToZero) 0 else player.currentPosition)
                    }
                    player.prepare()
                    if (wasPlaying) {
                        player.play()
                    }
                }
                Toaster.w(R.string.network_stream_retrying_playback)
                return
            } else {
                Timber.tag("PlayerServiceModern").e("onPlayerError recovery exhausted ($MAX_RECOVERY_ATTEMPTS attempts) for $currentMediaId")
                recoveryAttempts.remove(currentMediaId)
            }
        }

        // Non-recoverable, non-network error: only skip if the option is ON
        if (!preferences.getBoolean(skipMediaOnErrorKey, false) || !player.hasNextMediaItem()) {
            Toaster.e(R.string.player_error_unknown)
            return
        }

        // Clean up recovery counter for the track we're about to skip
        currentMediaId?.let { recoveryAttempts.remove(it) }

        val prev = player.currentMediaItem ?: return
        //player.seekToNextMediaItem()
        player.playNext()

        showSmartMessage(
            message = getString(
                R.string.player_song_playback_error_format,
                prev.mediaMetadata.title
            )
        )

    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) && !events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
            refreshCurrentTrackIdentity(player.currentMediaItem)
        }
        if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
            if (player.playbackState == Player.STATE_READY && player.playWhenReady && events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                Timber.tag("PlayerServiceModern").i("PLAYBACK OK - Streaming successfully [${player.currentMediaItem?.mediaMetadata?.title}]")
            }
            if (!player.playWhenReady) {
                waitingForNetwork.value = false
            }
        }
        if (player.playbackState == Player.STATE_READY && events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION,
            )
        ) {
            persistMissingPlaybackDuration(player)
        }
        if (events.containsAny(
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_TIMELINE_CHANGED,
            )
        ) {
            updateWidgets()
            refreshMedley()
        }
    }

    private fun persistMissingPlaybackDuration(player: Player) {
        val item = player.currentMediaItem ?: return
        if (item.isLocal || ListenTogetherPlayerBridge.isListenTogetherItem(item) || player.isCurrentMediaItemLive) return
        val declaredDuration = item.mediaMetadata.extras?.getString("durationText")
        if (declaredDuration != null && durationTextToMillis(declaredDuration) > 0) return
        val durationMs = player.duration
        if (durationMs < 1000) return
        val id = item.sessionArtistTrackId()
        val profile = getActiveProfile(this)
        coroutineScope.launch(NzikDispatchers.DATA) {
            if (getActiveProfile(this@PlayerServiceModern) != profile ||
                Database.FILE_NAME != Database.fileNameForProfile(profile)
            ) return@launch
            val songs = Database.songTable
            val song = songs.findByIdDirect(id) ?: return@launch
            if ((song.durationText?.let(::durationTextToMillis) ?: 0L) > 0L) return@launch
            songs.updateDurationIfUnchanged(id, song.durationText, formatAsDuration(durationMs))
        }
    }


    private fun maybeRecoverPlaybackError() {
        if (player.playerError != null) {
            player.prepare()
        }
    }

    private fun loadFromRadio( reason: Int ) {
        val isRepeatTransition = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        if (!isRepeatTransition && player.mediaItemCount > 1) {
            nzikRadio.autoFillQueue()
        }
    }

    private fun maybeBassBoost() {
        bassBoostReady = false
        if (player.audioSessionId <= 0) return
        if (!preferences.getBoolean(bassboostEnabledKey, false)) {
            runCatching {
                bassBoost?.enabled = false
                bassBoost?.release()
            }
            bassBoost = null
            bassBoostReady = true
            maybeNormalizeVolume()
            return
        }

        runCatching {
            if (bassBoost == null) bassBoost = BassBoost(0, player.audioSessionId)
            val bassboostLevel =
                (preferences.getFloat(bassboostLevelKey, 0f) * 1000f).toInt().toShort()
            Timber.tag("PlayerServiceModern").d("maybeBassBoost bassboostLevel $bassboostLevel")
            bassBoost?.enabled = false
            bassBoost?.setStrength(bassboostLevel)
            bassBoost?.enabled = true
            bassBoostReady = true
        }.onFailure {
            Toaster.e( R.string.audio_bass_boost_unavailable_message )
        }
    }

    private fun maybeReverb() {
        reverbReady = false
        if (player.audioSessionId <= 0) return
        val presetType = preferences.getEnum(audioReverbPresetKey, PresetsReverb.NONE)

        if (presetType == PresetsReverb.NONE) {
            runCatching {
                reverbPreset?.enabled = false
                player.clearAuxEffectInfo()
                reverbPreset?.release()
            }
            reverbPreset = null
            reverbReady = true
            return
        }

        runCatching {
            if (reverbPreset == null) reverbPreset = PresetReverb(1, player.audioSessionId)

            reverbPreset?.enabled = false
            reverbPreset?.preset = presetType.preset
            reverbPreset?.enabled = true
            reverbPreset?.id?.let { player.setAuxEffectInfo(AuxEffectInfo(it, 1f)) }
            reverbReady = true
        }.onFailure {
            Timber.tag("AudioEffectSession").w(it, "Failed to initialize reverb")
        }
    }

    @UnstableApi
    private fun maybeNormalizeVolume() {
        normalizationReady = false
        if (player.audioSessionId <= 0) return
        if (!preferences.getBoolean(volumeNormalizationKey, false)) {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            volumeNormalizationJob?.cancel()
            normalizationReady = true
            return
        }

        runCatching {
            if (loudnessEnhancer == null) {
                loudnessEnhancer = LoudnessEnhancer(player.audioSessionId)
            }
        }.onFailure {
            Timber.tag("PlayerServiceModern").e("maybeNormalizeVolume load loudnessEnhancer ${it.stackTraceToString()}")
            return
        }

        normalizationReady = true
        val baseGain = preferences.getFloat(loudnessBaseGainKey, 5.00f)
        val volumeBoostLevel = preferences.getFloat(volumeBoostLevelKey, 0f)
        player.currentMediaItem?.mediaId?.let { songId ->
            volumeNormalizationJob?.cancel()
            volumeNormalizationJob = coroutineScope.launch(NzikDispatchers.UI) {
                fun Float?.toMb() = ((this ?: 0f) * 100).toInt()

                Database.formatTable
                        .findBySongId( songId )
                        .cancellable()
                        .collectLatest { format ->
                            val loudnessMb = format?.loudnessDb.toMb().let {
                                if (it !in -2000..2000) {
                                    Toaster.w( R.string.audio_extreme_loudness_warning )

                                    0
                                } else
                                    it
                            }

                            try {
                                loudnessEnhancer?.setTargetGain(baseGain.toMb() + volumeBoostLevel.toMb() - loudnessMb)
                                loudnessEnhancer?.enabled = true
                                normalizationReady = true
                            } catch (e: Exception) {
                                normalizationReady = false
                                Timber.tag("PlayerServiceModern").e("maybeNormalizeVolume apply targetGain ${e.stackTraceToString()}")
                            }
                        }
            }
        }
    }


    @SuppressLint("NewApi")
    private fun maybeResumePlaybackWhenDeviceConnected() {
        if (!isAtLeastAndroid6) return

        if (preferences.getBoolean(resumePlaybackWhenDeviceConnectedKey, false)) {
            if (audioDeviceCallback != null) return
            val manager = audioManager ?: (getSystemService(AUDIO_SERVICE) as? AudioManager)?.also { audioManager = it } ?: return
            val connectedIds = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).mapTo(mutableSetOf()) { it.id }
            audioDeviceCallback = object : AudioDeviceCallback() {
                private fun canPlayMusic(audioDeviceInfo: AudioDeviceInfo): Boolean {
                    if (!audioDeviceInfo.isSink) return false

                    return audioDeviceInfo.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            audioDeviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            audioDeviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            audioDeviceInfo.type == AudioDeviceInfo.TYPE_USB_HEADSET
                }

                override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                    val newlyConnected = addedDevices.filter { connectedIds.add(it.id) }
                    if (!isDestroying && !player.isPlaying && newlyConnected.any(::canPlayMusic)) {
                        // Guarded facade: for a locked Listen Together guest the play intent is
                        // registered, so the manager resyncs them to the host position
//
                        // (spec-listen-together-guest-lock-hardening). Hosts / out-of-room: passthrough.
                        guestGuardPlayer.play()
                    }
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                    removedDevices.forEach { connectedIds.remove(it.id) }
                }
            }

            manager.registerAudioDeviceCallback(audioDeviceCallback, null) // null = main looper

        } else {
            unregisterPlaybackDeviceCallback()
        }
    }

    @SuppressLint("NewApi")
    private fun unregisterPlaybackDeviceCallback() {
        if (!isAtLeastAndroid6) return
        audioDeviceCallback?.let { audioManager?.unregisterAudioDeviceCallback(it) }
        audioDeviceCallback = null
    }

    private fun createRendersFactory() = object : DefaultRenderersFactory(this) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): AudioSink {
            val minimumSilenceDuration = preferences.getLong(
                minimumSilenceDurationKey, 2_000_000L
            ).coerceIn(1000L..2_000_000L)

            // Captured once per sink: the mute predicate runs on the audio thread, per buffer
            val audioOutput = BridgeServerController.audioOutput
            return DefaultAudioSink.Builder(applicationContext)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioOffloadSupportProvider(
                    DefaultAudioOffloadSupportProvider(applicationContext)
                )
                .setAudioProcessorChain(
                    // PC bridge (contract §8.5): silent while the audio output is the PC, last in
                    // the chain; both the main and the crossfade player are built here
                    MuteAudioProcessorChain(
                        DefaultAudioProcessorChain(
                            arrayOf(),
                            SilenceSkippingAudioProcessor(
                                /* minimumSilenceDurationUs = */ minimumSilenceDuration,
                                /* silenceRetentionRatio = */ 0.01f,
                                /* maxSilenceToKeepDurationUs = */ minimumSilenceDuration,
                                /* minVolumeToKeepPercentageWhenMuting = */ 0,
                                /* silenceThresholdLevel = */ 256
                            ),
                            SonicAudioProcessor()
                        ),
                        MuteAudioProcessor { audioOutput.value == AudioOutput.PC }
                    )
                )
                .build()
                .apply {
                    if (isAtLeastAndroid10) setOffloadMode(AudioSink.OFFLOAD_MODE_DISABLED)
                }
        }
    }

    private fun createMediaSourceFactory() = DefaultMediaSourceFactory(
        createDataSourceFactory(),
        DefaultExtractorsFactory()
    ).setLoadErrorHandlingPolicy(
        PlaybackLoadErrorPolicy(
            shouldSkipOnError = { preferences.getBoolean(skipMediaOnErrorKey, false) },
            onRetryCount = { playbackDiagnosticsListener.diagnosticRetryCount = it },
            isNetworkAvailable = { isNetworkAvailable.value },
            isStaleMonitorOfflineFailure = { isStaleMonitorOfflineFailure(it, !currentMediaIsLocal) },
        )
    )

    /**
     * Issue #866 (gh-866): radio state as seen by the command buttons. `nzikRadio` is lateinit
     * (created in onStart), so the read is guarded until it exists — mirrors the
     * `::binder.isInitialized` pattern used in AutoSessionCallback.
     */
    private val isRadioActiveForCommandButtons: Boolean
        get() = if (::nzikRadio.isInitialized) nzikRadio.isRadioActive else false

    /** Issue #866 (gh-866): discover state as seen by the command buttons — the discover filter lives in the `discoverKey` preference (NZikRadio.isDiscoverEnabled reads it live), so the read is guarded until `nzikRadio` (lateinit, created in onStart) exists. */
    private val isDiscoverEnabledForCommandButtons: Boolean
        get() = if (::nzikRadio.isInitialized) nzikRadio.isDiscoverEnabled else false

    private fun commandButtonLabelRes(button: NotificationButtons): Int = when (button) {
        NotificationButtons.Radio -> radioCommandButtonLabelRes(isRadioActiveForCommandButtons)
        NotificationButtons.Favorites -> {
            val likedAt = currentSong.value.takeIf { it.mediaId == currentMediaItem.value?.mediaId }?.likedAt
            val state = when {
                likedAt == -1L -> false
                likedAt != null && likedAt > 0L -> true
                else -> null
            }
            val dislikesEnabled = preferences.getEnum(excludeDislikedSongsKey, DislikeMode.Enabled).isEnabled
            menuLikeActionLabel(state, dislikesEnabled,
                R.string.library_favorites_action_add, R.string.library_favorites_action_remove)
        }
        else -> button.textId
    }

    /**
     * Issue #866 (gh-866): resolve the state icon published by the command buttons. All buttons
     * resolve through the existing legacy [NotificationButtons.getStateIcon] (like / download /
     * repeat / shuffle states); the radio button additionally switches to the filled icon while
     * active, so on/off is distinguishable in the Android Auto overflow and the notification.
     */
    private fun commandButtonIconRes(button: NotificationButtons): Int {
        val stateIcon = button.getStateIcon(
            button,
            currentSong.value.takeIf { it.mediaId == currentMediaItem.value?.mediaId }?.likedAt,
            currentDownloadState(currentMediaItem.value),
            player.repeatMode,
            player.shuffleModeEnabled
        )
        return when {
            button == NotificationButtons.Shuffle -> shuffleOkFlashIconRes(shuffleOkFlashActive.value, stateIcon)
            button == NotificationButtons.Radio -> radioCommandButtonIconRes(stateIcon, isRadioActiveForCommandButtons)
            button == NotificationButtons.Discover -> discoverCommandButtonIconRes(stateIcon, isDiscoverEnabledForCommandButtons)
            else -> stateIcon
        }
    }

    /** Issue #866 (gh-866): shows the transient shuffle confirmation icon (`R.drawable.shuffle_ok`) on every shuffle button — the Android Auto overflow, the media notification (via `updateDefaultNotification`) and the in-app UI (reactive Compose state) — for `SHUFFLE_OK_FLASH_MS`, then restores the base icon. */
    private fun triggerShuffleOkFlash() {
        shuffleOkFlashJob?.cancel()
        shuffleOkFlashActive.value = true
        shuffleOkFlashJob = coroutineScope.launch(NzikDispatchers.UI) {
            delay(SHUFFLE_OK_FLASH_MS)
            shuffleOkFlashActive.value = false
            shuffleOkFlashJob = null
            updateDefaultNotification()
        }
    }



    private fun buildCustomCommandButtons(): MutableList<CommandButton> {
        // Listen Together guest (AD-6): the notification shows only play/pause — the custom
        // buttons below (like/download/repeat/shuffle/…) are hidden; next/prev are hidden via the
//
        // guarded available commands. Rebuilt on every lock change (guestLockObserverJob).
        if (listenTogetherGuestLock.value) return mutableListOf()
        val notificationPlayerFirstIcon = preferences.getEnum(notificationPlayerFirstIconKey, NotificationButtons.Download)
        val notificationPlayerSecondIcon = preferences.getEnum(notificationPlayerSecondIconKey, NotificationButtons.Favorites)

        val commandButtonsList = playbackNotificationButtons(
            first = notificationPlayerFirstIcon,
            second = notificationPlayerSecondIcon,
            allowDownload = !currentMediaIsLocal,
            labelRes = ::commandButtonLabelRes,
            iconRes = ::commandButtonIconRes,
        )

        val access = AutoAccessPolicy(this, Database, MyDownloadHelper, binder)
        return commandButtonsList.filter { button -> button.sessionCommand?.let { access.allowsCommand(it.customAction) } != false }.toMutableList()
    }

    private fun updateDefaultNotification() {
        coroutineScope.launch(NzikDispatchers.UI) {
            // Issue #866 (gh-866): published through setMediaButtonPreferences instead of the legacy
            // customLayout channel (setCustomLayout) — the channel Android Auto (17.x) consumes for its
//
            // overflow buttons, and the one the media notification reads via MediaNotificationManager →
            // MediaController.getMediaButtonPreferences — so the state icons built by
//
            // buildCustomCommandButtons now reach both.
            mediaSession.setMediaButtonPreferences( buildCustomCommandButtons() )
        }

    }

    fun toggleLike() {
        binder.toggleLike()
    }

    fun toggleDownload() {
        if (!NetworkQualityHelper.isNetworkConnected(this)) return
        binder.toggleDownload()
    }

    fun toggleRepeat() {
        binder.toggleRepeat()
    }

    fun toggleShuffle() {
        binder.toggleShuffle()
    }

    fun startRadio() {
        if (!::nzikRadio.isInitialized) return
        if (nzikRadio.isRadioActive) {
            nzikRadio.stopRadio(showToast = true)
        } else {
            player.currentMediaItem?.takeUnless { it.isLocal }?.let { binder.startRadio(it, isExplicit = true) }
        }
    }

    private fun isRadioRunning(): Boolean = ::nzikRadio.isInitialized && nzikRadio.isRadioActive

    // Issue #866 (gh-866): discover command button (notification + AA overflow) — routed through
    // the central NZikRadio.toggleDiscover() so its guest-lock and radio/auto-fill precheck
//
    // (error toast) apply exactly as in-app.
    fun toggleDiscover() {
        if (!NetworkQualityHelper.isNetworkConnected(this)) return
        if (::nzikRadio.isInitialized) nzikRadio.toggleDiscover()
    }

    private fun showSmartMessage( message: String ) = Toaster.i(message)

    private val widgetUpdates = PlaybackWidgetUpdates(
        context = this,
        coroutineScope = coroutineScope,
        player = { player },
        widgetPlayer = { binder.player },
        bitmapProvider = { bitmapProvider },
        isDestroying = { isDestroying },
        requestRender = ::updateWidgets,
    )

    /** Re-render paused widgets without starting playback or waking a stopped service. */
    private fun refreshArtworkWidgets(mediaId: String? = null) {
        coroutineScope.launch(NzikDispatchers.UI) {
            if (isDestroying || !::bitmapProvider.isInitialized || !::guestGuardPlayer.isInitialized) return@launch
            if (mediaId != null && binder.player.currentMediaItem?.mediaId != mediaId) return@launch
            widgetUpdates.refreshArtworkWidgets()
        }
    }

    @MainThread
    fun updateWidgets() {
        if (isDestroying || !::guestGuardPlayer.isInitialized) return
        widgetUpdates.updateWidgets()
    }

    private fun attachCrossfadeAudioDiagnostics(target: ExoPlayer) {
        if (crossfadeAudioDiagnostics.containsKey(target)) return
        val diagnostics = CrossfadeAudioDiagnostics(target)
        crossfadeAudioDiagnostics[target] = diagnostics
        target.addAnalyticsListener(diagnostics)
        diagnostics.mark("PLAYER_CREATED")
    }

    private fun detachCrossfadeAudioDiagnostics(target: ExoPlayer) {
        crossfadeAudioDiagnostics.remove(target)?.let { diagnostics ->
            diagnostics.mark("PLAYER_RELEASE_REQUEST")
            target.removeAnalyticsListener(diagnostics)
        }
    }

    private fun markCrossfadeAudio(event: String, outgoing: ExoPlayer, incoming: ExoPlayer) {
        crossfadeAudioDiagnostics[outgoing]?.mark("${event}_OUTGOING")
        crossfadeAudioDiagnostics[incoming]?.mark("${event}_INCOMING")
    }

    private fun bindAudioEffectPlayer(target: ExoPlayer) {
        audioEffectListener?.let { listener -> audioEffectPlayer?.removeListener(listener) }
        audioEffectPlayer = target
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_AUDIO_SESSION_ID,
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    )
                ) {
                    syncAudioEffectSession(target, "player-event")
                }
            }
        }
        audioEffectListener = listener
        target.addListener(listener)
        syncAudioEffectSession(target, "bind")
        // Auxiliary reverb attachment belongs to the bound player's own AudioTrack.
        runCatching {
            reverbPreset?.id?.let { target.setAuxEffectInfo(AuxEffectInfo(it, 1f)) }
        }.onFailure {
            reverbReady = false
            Timber.tag("AudioEffectSession").w(it, "Failed to attach reverb to the bound player")
        }
    }

    private fun syncAudioEffectSession(target: ExoPlayer, reason: String) {
        // Media3 reports its raw player, so ownership must use the captured diagnostic wrapper.
        if (audioEffectPlayer !== target || player !== target) return
        val sessionId = target.audioSessionId
        val shouldOpen = target.playWhenReady &&
            (target.playbackState == Player.STATE_BUFFERING || target.playbackState == Player.STATE_READY)
        Timber.tag("AudioEffectSession").i(
            "SYNC player=%d session=%d playing=%s reason=%s",
            System.identityHashCode(target), sessionId, shouldOpen, reason,
        )
        runCatching { audioEffectSession.sync(sessionId, shouldOpen) }.onFailure {
            Timber.tag("AudioEffectSession").w(it, "Failed to reconcile audio effects session")
        }
        runCatching { refreshInternalAudioEffects(sessionId) }.onFailure {
            Timber.tag("AudioEffectSession").w(it, "Failed to rebind internal audio effects")
        }
    }

    private fun closeAudioEffectSession() {
        val target = audioEffectPlayer
        val listener = audioEffectListener
        audioEffectPlayer = null
        audioEffectListener = null
        if (listener != null) target?.removeListener(listener)
        runCatching { audioEffectSession.close() }.onFailure {
            Timber.tag("AudioEffectSession").w(it, "Failed to close audio effects session")
        }
        releaseInternalAudioEffects()
    }

    private fun refreshInternalAudioEffects(sessionId: Int) {
        if (internalEffectsSessionId != sessionId) {
            releaseInternalAudioEffects()
            internalEffectsSessionId = sessionId
        }
        if (sessionId <= 0) return
        // Successful effects stay bound; failed initialization remains eligible on later events.
        if (!bassBoostReady) maybeBassBoost()
        if (!reverbReady) maybeReverb()
        if (!normalizationReady) maybeNormalizeVolume()
    }

    private fun releaseInternalAudioEffects() {
        volumeNormalizationJob?.cancel()
        volumeNormalizationJob = null
        if (reverbPreset != null && ::player.isInitialized) {
            runCatching { player.clearAuxEffectInfo() }.onFailure {
                Timber.tag("AudioEffectSession").w(it, "Failed to detach released reverb")
            }
        }
        listOf(bassBoost, reverbPreset, loudnessEnhancer).forEach { effect ->
            runCatching { effect?.release() }.onFailure {
                Timber.tag("AudioEffectSession").w(it, "Failed to release internal audio effect")
            }
        }
        bassBoost = null
        reverbPreset = null
        loudnessEnhancer = null
        internalEffectsSessionId = C.AUDIO_SESSION_ID_UNSET
        bassBoostReady = false
        reverbReady = false
        normalizationReady = false
    }

    private fun sendAudioEffectIntent(opened: Boolean, sessionId: Int) {
        val action = if (opened) AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
        else AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
        sendBroadcast(
            Intent(action).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                if (opened) putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
        )
        Timber.tag("AudioEffectSession").i("%s session=%d", if (opened) "OPEN" else "CLOSE", sessionId)
    }

    private fun actionSearch() {
        binder.actionSearch()
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        Timber.tag("PlayerServiceModern").d("onPositionDiscontinuity oldPosition ${oldPosition.mediaItemIndex} newPosition ${newPosition.mediaItemIndex} reason $reason")
        // Issue #881 (gh-881): full discontinuity journal (spec S2) — positions in ms + the NAMED
        // reason (media3 1.10.1: SEEK=1, SEEK_ADJUSTMENT=2, … — the legacy line above prints the
//
        // raw int and the index only, which reads misleadingly).
        Timber.tag(PLAYBACK_DIAG_TAG).d(
            "DISCONTINUITY old=%d@%d new=%d@%d reason=%s playerIdentity=%d",
            oldPosition.mediaItemIndex,
            oldPosition.positionMs,
            newPosition.mediaItemIndex,
            newPosition.positionMs,
            discontinuityReasonName(reason),
            System.identityHashCode(player),
        )

        if (!isInternalCrossfadeSeek && (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT || reason == Player.DISCONTINUITY_REASON_SKIP)) {
            trackBoundaryPause.cancel()
            cancelCrossfadeAndReset()
        }

        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            scheduleCrossfade()
        }

        // Discord presence: update on seek/skip
        if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SKIP) {
            if (encryptedPreferences.getBoolean(isDiscordPresenceEnabledKey, false)) {
                val token = encryptedPreferences.getString(discordPersonalAccessTokenKey, "")
                if (token?.isNotEmpty() == true) {
                    // Item 8: live providers (not captured values) so the 5 s refresh tick
                    // reads the real position/play state.
                    val currentMediaItem = player.currentMediaItem
                    val duration = player.duration
                    val now = System.currentTimeMillis()
                    discordPresenceManager?.onPlayingStateChanged(
                        currentMediaItem,
                        player.isPlaying,
                        player.currentPosition,
                        duration,
                        now,
                        playbackSpeed = effectivePlaybackSpeed(),
                        getCurrentPosition = { player.currentPosition },
                        isPlayingProvider = { player.isPlaying }
                    )
                }
            }
        }
        super.onPositionDiscontinuity(oldPosition, newPosition, reason)
    }

    private val persistentQueue = PlaybackPersistentQueue(
        context = this,
        coroutineScope = coroutineScope,
        player = { player },
        isDestroying = { isDestroying },
        isPersistentQueueEnabled = { isPersistentQueueEnabled },
        restoreStartupQueueIfUnowned = { mediaLibrarySessionCallback.restoreStartupQueueIfUnowned(it) },
        resumePlayback = { binder.gracefulPlay() },
    )

    @MainThread
    private fun maybeSavePlayerQueue(finalSnapshot: Boolean = false, saveEmpty: Boolean = false) {
        if (!isPersistentQueueEnabled || !::player.isInitialized || (isDestroying && !finalSnapshot)) return
        persistentQueue.save(player, saveEmpty)
    }

    private fun maybeRestorePlayerQueue() {
        if (!isPersistentQueueEnabled) return
        persistentQueue.restore()
    }

    private fun observeNotificationState() {
        coroutineScope.launch(NzikDispatchers.UI) {
            currentSong.collect {
                updateDefaultNotification()
                updateWidgets()
            }
        }
        coroutineScope.launch(NzikDispatchers.UI) {
            combine(currentMediaItem.changes, MyDownloadHelper.downloads) { item, downloads ->
                item?.mediaId to (downloads[item?.mediaId]?.state ?: Download.STATE_STOPPED)
            }.distinctUntilChanged().collect {
                updateDownloadedState()
            }
        }
    }

    private fun currentDownloadState(mediaItem: MediaItem?): Int =
        MyDownloadHelper.downloads.value[mediaItem?.mediaId]?.state ?: Download.STATE_STOPPED

    /** Refreshes download presentation even when the current track has no Room row. */
    fun updateDownloadedState() {
        currentSongStateDownload.value = currentDownloadState(currentMediaItem.value)
        updateDefaultNotification()
    }

    /**
     * This method should ONLY be called when the application (sc. activity) is in the foreground!
     */
    fun restartForegroundOrStop() {
        binder.restartForegroundOrStop()
    }

    @UnstableApi
    class CustomMediaNotificationProvider(private val context: Context) :
        DefaultMediaNotificationProvider(
            context,
            DefaultMediaNotificationProvider.NotificationIdProvider { session -> DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID },
            // The main player notification is built from the channel ID captured at construction
            // time (media3 1.10.x: createNotification() is final and uses the private channelId
//
            // field, never getNotificationChannelInfo()): the channel of the active profile. The
            // player service is stopped and the process exits on every profile switch, so the
//
            // construction-time profile is always the active one at emission.
            channelId(NotificationChannelId, getActiveProfile(context)),
            R.string.player_title,
        ) {

        /**
         * The channel info of the active profile (Media3 consumes it, e.g. for the shutdown
         * notification): the suffixed ID + the base channel's display name (the profile is not
         * shown in the channel name; the channels are separated by ID). The channel itself is
         * created by ensureProfileChannels (boot / switch) and Media3 re-creates it on the fly
         * when missing.
         */
        override fun getNotificationChannelInfo(): Provider.NotificationChannelInfo =
            Provider.NotificationChannelInfo(
                channelId(NotificationChannelId, getActiveProfile(context)),
                context.getString(R.string.player_title),
            )

        override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence? {
            val sessionTitle = metadata.title?.toString().orEmpty()
            if (sessionTitle != sessionTitle.withoutSmartShuffleSessionPrefix()) {
                return super.getNotificationContentTitle(metadata)
            }
            val isExplicit = metadata.extras?.getBoolean("isExplicit") == true ||
                             metadata.extras?.getBoolean("androidx.media3.session.EXTRAS_KEY_IS_EXPLICIT") == true
            val title = cleanPrefix(metadata.title?.toString() ?: "").ifBlank { context.getString(R.string.metadata_unknown_title) }.let {
                if (isExplicit) "\uD83C\uDD74 $it" else it
            }
            val customMetadata = MediaMetadata.Builder()
                .setTitle(title)
                .build()
            return super.getNotificationContentTitle(customMetadata)
        }

        override fun getNotificationContentText(metadata: MediaMetadata): CharSequence? {
            val cleaned = cleanPrefix(metadata.artist?.toString() ?: "")
            if (cleaned.isNotBlank() && cleaned != "null") return cleaned
            val albumCleaned = cleanPrefix(metadata.albumTitle?.toString() ?: "")
            if (albumCleaned.isNotBlank() && albumCleaned != "null") return albumCleaned
            return context.getString(R.string.metadata_unknown_artist)
        }
    }


    class NotificationDismissReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            kotlin.runCatching {
                context.stopService(context.intent<MyDownloadService>())
            }.onFailure {
                Timber.tag("PlayerServiceModern").e("NotificationDismissReceiver stopService (MyDownloadService) ${it.stackTraceToString()}")
            }
            kotlin.runCatching {
                context.stopService(context.intent<PlayerServiceModern>())
            }.onFailure {
                Timber.tag("PlayerServiceModern").e("NotificationDismissReceiver stopService (PlayerServiceModern) ${it.stackTraceToString()}")
            }
        }
    }

    /**
     * Notification / widget transport buttons. Every player operation routes through the guarded
     * facade ([guestGuardPlayer]) — read dynamically, never cached: the facade is rebuilt on each
     * crossfade player swap (AD-5), so a reference captured at construction time would point at
     * the released old player. For a Listen Together guest, next/prev are no-oped by the guard.
     */
    inner class NotificationActionReceiver : BroadcastReceiver() {

        private val player: Player
            get() = this@PlayerServiceModern.guestGuardPlayer

        @ExperimentalCoroutinesApi
        @FlowPreview
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Action.pause.value -> binder.gracefulPause()
                Action.play.value -> binder.gracefulPlay()
                Action.playPause.value -> {
                    if (player.isPlaying) binder.gracefulPause() else binder.gracefulPlay()
                }
                // Reject unavailable commands before seek/prepare/resume side effects.
                Action.next.value -> player.playNextIfAvailable()
                Action.previous.value -> player.playPreviousIfAvailable()
                Action.like.value -> {
                    binder.toggleLike()
                }

                Action.download.value -> {
                    binder.toggleDownload()
                }

                Action.playradio.value -> startRadio()

                // Issue #866 (gh-866): discover command button (notification + AA overflow).
                Action.discover.value -> toggleDiscover()

                Action.shuffle.value -> {
                    binder.toggleShuffle()
                }

                Action.search.value -> {
                    binder.actionSearch()
                }

                Action.repeat.value -> {
                    binder.toggleRepeat()
                }


            }

        }

    }

    @Stable
    open inner class Binder : AndroidBinder() {
        fun refreshArtworkWidgets(mediaId: String? = null) = this@PlayerServiceModern.refreshArtworkWidgets(mediaId)

        val service: PlayerServiceModern
            get() = this@PlayerServiceModern

        val bitmap: Bitmap
            get() = bitmapProvider.bitmap


        /**
         * The guarded player facade — every external entry point (UI, menus, library, widgets,
         * notification buttons) consumes the [Player] interface through it, so a Listen Together
         * guest can only play/pause (spine AD-1/AD-2/AD-7). The raw [ExoPlayer] is intentionally
         * NOT exposed here; internal service logic uses `this@PlayerServiceModern.player`.
         */
        val player: Player
            get() = this@PlayerServiceModern.guestGuardPlayer

        internal fun setQueueSmartShuffleOrder(expectedPlayer: Player, order: IntArray) {
            if (player === expectedPlayer && !listenTogetherGuestLock.value) {
                this@PlayerServiceModern.player.setShuffleOrder(DefaultShuffleOrder(order, System.currentTimeMillis()))
            }
        }

        val playerUpdateTrigger: StateFlow<Int>
            get() = this@PlayerServiceModern.playerUpdateTrigger

        /**
         * The session's "open player" token (spec-notification-click-opens-player, option B) —
         * static for the service's lifetime. The consumer live-compares a tap token against
         * this at deployment: equal → legitimate tap; different → stale task-record
         * re-delivery (the service restarted and minted a new token) → no deployment.
         */
        val openPlayerToken: Long
            get() = this@PlayerServiceModern.openPlayerToken

        val cache: Cache
            get() = this@PlayerServiceModern.cache

        internal val streamingCleanupCache: ActiveStreamingCleanup.BoundCache?
            get() = this@PlayerServiceModern.boundStreamingCache

        val downloadCache: Cache
            get() = this@PlayerServiceModern.downloadCache

        val sleepTimerMillisLeft: StateFlow<Long?>
            get() = sleepTimer.millisLeft

        fun startSleepTimer(delayMillis: Long) {
            sleepTimer.start(
                delayMillis,
                onExpiration = {
                    val notification = NotificationCompat
                        .Builder(this@PlayerServiceModern, channelId(SleepTimerNotificationChannelId, getActiveProfile(this@PlayerServiceModern)))
                        .setContentTitle(appTextResources().getString(R.string.player_sleep_timer_finished_message))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .setOnlyAlertOnce(true)
                        .setShowWhen(true)
                        .setSmallIcon(R.drawable.ic_launcher_monochrome)
                        .build()

                    notificationManager?.let { manager ->
                        manager.notify(SleepTimerNotificationId, notification)
                        PermissionUsageJournal.record(this@PlayerServiceModern, PermissionFeature.PLAYBACK, JournalPermission.NOTIFICATIONS, PermissionEventKind.USED)
                    }
                },
                onShutdown = {
                    stopSelf()
                    exitProcess(0)
                },
            )
        }

        fun cancelSleepTimer() {
            sleepTimer.cancel()
        }

        val isLoadingRadio: Boolean
            get() = nzikRadio.isLoading

        val isRadioActive: Boolean
            get() = isRadioRunning()

        val radioActionTextRes: Int
            get() = nzikRadio.radioActionTextRes

        /** Issue #866 (gh-866): app-wide shuffle confirmation flash state — read by the in-app shuffle buttons (and the AA / notification command buttons via `updateDefaultNotification`). */
        val shuffleOkFlashActive: Boolean
            get() = this@PlayerServiceModern.shuffleOkFlashActive.value

        /** Issue #866 (gh-866): transient `shuffle_ok` confirmation flash (~1 s) on every shuffle button. */
        fun triggerShuffleOkFlash() {
            this@PlayerServiceModern.triggerShuffleOkFlash()
        }

        fun startRadio(
            mediaItem: MediaItem,
            append: Boolean = false,
            endpoint: NavigationEndpoint.Endpoint.Watch? = null,
            isExplicit: Boolean = false
        ) {
            nzikRadio.startRadio(mediaItem, append, endpoint, isExplicit)
        }

        fun startRadio(
            song: Song,
            append: Boolean = false,
            endpoint: NavigationEndpoint.Endpoint.Watch? = null,
            isExplicit: Boolean = false
        ) = startRadio( song.asMediaItem, append, endpoint, isExplicit )

        fun stopRadio() {
            nzikRadio.stopRadio(showToast = false)
        }

        @MainThread
        internal fun captureMenuQueueEdit(): MenuQueueEdits.Request? = menuQueueEdits.capture()

        @MainThread
        internal fun onExplicitQueueClear() {
            menuQueueEdits.invalidate()
            stopRadio()
        }

        private val audioOutputSwitch = AudioOutputSwitch(
            CoroutineScope(coroutineScope.coroutineContext + NzikDispatchers.UI),
        ) { player }

        @MainThread
        fun setPreferredAudioDevice(deviceId: Int?): Boolean {
            if (listenTogetherGuestLock.value) {
                guestGuardPlayer.reportBlockedOp()
                return false
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
            val manager = audioManager ?: (getSystemService(AUDIO_SERVICE) as? AudioManager) ?: return false
            val device = deviceId?.let { id ->
                manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id } ?: return false
            }
            return audioOutputSwitch.apply {
                // Clear the current device to force ExoPlayer to re-evaluate the route.
                this@PlayerServiceModern.player.setPreferredAudioDevice(null)
                if (device != null) this@PlayerServiceModern.player.setPreferredAudioDevice(device)
                preferredDeviceId = deviceId
                true
            }
        }

        /** Pause with fade out effect */
        @MainThread
        fun gracefulPause() {
            val duration = preferences.getEnum( playbackFadeAudioDurationKey, DurationInMilliseconds.Disabled )
            // The fade runs on the guarded facade, not the raw player: the fade uses only
            // play/pause/volume (AD-2 pass-through ops, so host behavior is unchanged), but the
//
            // facade's pause() is the observation point for the guest's pause intent
            // (onGuestPlayPause → guestPlayPause(false) → guestLocalPause). On the raw player the
//
            // flag was never set and the host's next PLAY resumed an in-app-paused guest
            // (spec-listen-together-guest-lock-hardening).
            this@PlayerServiceModern.guestGuardPlayer.pauseWithFade( duration.asMillis )
        }

        /**
         * Start playing with fade in effect
         */
        @MainThread
        fun gracefulPlay() {
            val duration = preferences.getEnum( playbackFadeAudioDurationKey, DurationInMilliseconds.Disabled )
            // Same reason as [gracefulPause]: the facade's play() registers the guest's play
            // intent (onGuestPlayPause → guestPlayPause(true) → requestSync, resync to the host
//
            // position). Raw player would resume from a stale local position without resync.
            this@PlayerServiceModern.guestGuardPlayer.fadeInEffect( duration.asMillis )
        }

        /**
         * This method should ONLY be called when the application (sc. activity) is in the foreground!
         */
        internal val canRestartFromSettings: Boolean
            get() = ::mediaSession.isInitialized &&
                mediaSession.connectedControllers.none { mediaSession.isAutoCompanionController(it) }

        internal suspend fun awaitDestruction() = destructionCompleted.await()

        fun restartForegroundOrStop() {
            player.pause()
            stopSelf()
        }

        fun toggleLike() {
            val mediaItem = currentMediaItem.value ?: return

            coroutineScope.launch(NzikDispatchers.DATA) {
                YouTubeSync.rotateSongLikeState( this@PlayerServiceModern, mediaItem )
            }
        }

        fun toggleDownload() {
            if (!NetworkQualityHelper.isNetworkConnected(this@PlayerServiceModern)) return
            val mediaItem = currentMediaItem.value?.takeUnless { it.isLocal } ?: return
            val downloadState = currentDownloadState(mediaItem)
            manageDownload(
                context = this@PlayerServiceModern,
                mediaItem = mediaItem,
                downloadState = downloadState == Download.STATE_COMPLETED
            )
        }

        fun toggleRepeat() {
            // External command (notification / automotive): route through the guarded facade so a
            // Listen Together guest is no-oped + toasted (spine AD-1/AD-2).
            guestGuardPlayer.toggleRepeatMode()
            updateDefaultNotification()
        }

        fun toggleShuffle() {
            // External command (notification / automotive).
            // Issue #866 (gh-866): shuffle the ACTUAL queue — same behavior as the in-app queue
//
            // button (Shuffler.queue: current song first, rest shuffled). The previous
            // toggleShuffleMode() only flipped the player's internal shuffle mode, which has no
//
            // visible effect on N-Zik's dynamic (auto-filled) queue. Routed through the guarded
            // facade so a Listen Together guest is no-oped + toasted (spine AD-1/AD-2).
            guestGuardPlayer.shuffleQueue()
            // Transient "shuffle registered" confirmation on the command buttons (Android Auto +
            // media notification only, see triggerShuffleOkFlash) — skipped for a blocked guest,
//
            // whose operation was no-oped by the guarded facade.
            if (!listenTogetherGuestLock.value) triggerShuffleOkFlash()
            updateDefaultNotification()
        }

        fun actionSearch() {
            // No FLAG_ACTIVITY_CLEAR_TASK — matches the search shortcut intent: with the singleTask MainActivity, CLEAR_TASK relaunches the task and re-delivers the stale intent, dropping action_search.
            startActivity(Intent(applicationContext, MainActivity::class.java)
                .setAction(MainActivity.action_search)
                .setFlags(FLAG_ACTIVITY_NEW_TASK))
            Timber.tag("PlayerServiceModern").d("actionSearch")
        }
    }

    @JvmInline
    value class Action(val value: String) {
        val pendingIntent: PendingIntent
            get() = PendingIntent.getBroadcast(
                appContext(),
                100,
                Intent(value).setPackage(appContext().packageName),
                PendingIntent.FLAG_UPDATE_CURRENT.or(if (isAtLeastAndroid6) PendingIntent.FLAG_IMMUTABLE else 0)
            )

        companion object {

            val pause = Action("app.n_zik.android.pause")
            val play = Action("app.n_zik.android.play")
            val playPause = Action("app.n_zik.android.playPause")
            val next = Action("app.n_zik.android.next")
            val previous = Action("app.n_zik.android.previous")
            val like = Action("app.n_zik.android.like")
            val download = Action("app.n_zik.android.download")
            val playradio = Action("app.n_zik.android.playradio")
            val discover = Action("app.n_zik.android.discover")
            val shuffle = Action("app.n_zik.android.shuffle")
            val search = Action("app.n_zik.android.search")
            val repeat = Action("app.n_zik.android.repeat")

        }
    }

    // --- Crossfade Logic ---
    private var isCrossfading = false
    private var crossfadeClockMs: () -> Long = SystemClock::elapsedRealtime
    private var crossfadeJob: Job? = null
    private val crossfadePositionScheduler = CrossfadePositionScheduler()
    private var fadingPlayer: ExoPlayer? = null
    private var pendingSessionPlayerRelease: ExoPlayer? = null
    private var sessionRebindRecoveryJob: Job? = null
    private var isDestroying = false
    private var playbackPlayersReleased = false
    private class CrossfadeHandoff(
        val incoming: ExoPlayer,
        val outgoing: ExoPlayer,
        val startVolume: Float,
    ) {
        var timeline: CrossfadeTimeline? = null
        var outgoingEnded = false
        // True once the MediaSession actually wraps the incoming player; false means the
        // session still wraps the outgoing one and cleanup must re-bind it before release.
        var sessionSwapped = false
        lateinit var syncListener: Player.Listener
        lateinit var endListener: Player.Listener
    }
    private var activeCrossfade: CrossfadeHandoff? = null
    private var secondaryPlayer: ExoPlayer? = null
    private var isInternalCrossfadeSeek = false

    private val crossfadeDuration: Int
        get() = preferences.getInt(crossfadeDurationKey, 3000)

    private val crossfadeGapless: Boolean
        get() = preferences.getBoolean(crossfadeGaplessKey, false)

    private val crossfadeEnabled: Boolean
        get() = preferences.getBoolean(crossfadeEnabledKey, false)

    private fun configuredTrackPauseMs(): Long = trackBoundaryPauseMs(
        configuredMs = preferences.getEnum(pauseBetweenSongsKey, PauseBetweenSongs.`0`).asMillis,
        crossfadeEnabled = crossfadeEnabled,
        guestLocked = listenTogetherGuestLock.value,
    )

    private fun nextTrackIndex(owner: Player): Int =
        if (owner.repeatMode == Player.REPEAT_MODE_ONE) owner.currentMediaItemIndex else owner.nextMediaItemIndex

    private fun refreshMedley() {
        medleyJob?.cancel()
        if (isDestroying || !::player.isInitialized || listenTogetherGuestLock.value) return
        val duration = preferences.getFloat(playbackDurationKey, 0f)
        if (!duration.isFinite() || duration < 1f) return
        val owner = player
        if (!owner.isPlaying || !owner.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) return
        val limitMs = duration.roundToInt().toLong() * 1_000
        medleyJob = coroutineScope.launch(NzikDispatchers.UI) {
            while (owner === player && owner.isPlaying && !listenTogetherGuestLock.value) {
                val remaining = limitMs - owner.currentPosition
                if (remaining <= 0) {
                    owner.playNextIfAvailable()
                    break
                }
                delay((remaining / owner.playbackParameters.speed).toLong().coerceAtLeast(50))
            }
        }
    }

    private fun refreshTrackBoundaryPause() {
        if (isDestroying || !::player.isInitialized) return
        val next = nextTrackIndex(player)
        player.pauseAtEndOfMediaItems = configuredTrackPauseMs() > 0 && next in 0 until player.mediaItemCount
    }

    private fun trackBoundary(owner: Player): Pair<Any, Any>? {
        val timeline = owner.currentTimeline
        val current = owner.currentMediaItemIndex
        val next = nextTrackIndex(owner)
        if (current !in 0 until timeline.windowCount || next !in 0 until timeline.windowCount) return null
        val window = Timeline.Window()
        val currentUid = timeline.getWindow(current, window).uid
        return currentUid to timeline.getWindow(next, window).uid
    }

    private fun scheduleTrackBoundaryResume() {
        if (isDestroying || listenTogetherGuestLock.value) return
        val owner = player
        val boundary = trackBoundary(owner) ?: return
        trackBoundaryPause.schedule(configuredTrackPauseMs()) {
            if (player === owner && !isDestroying && !listenTogetherGuestLock.value &&
                !owner.playWhenReady && owner.playbackState == Player.STATE_READY &&
                owner.playerError == null && trackBoundary(owner) == boundary
            ) {
                owner.play()
            }
        }
    }

    private fun cancelTrackBoundaryPauseFor(owner: ExoPlayer) {
        if (::player.isInitialized && player === owner) trackBoundaryPause.cancel()
    }

    private val secondaryPlayerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            discardCrossfadePreparation()
        }
    }

    private fun discardCrossfadePreparation() {
        crossfadePositionScheduler.cancel()
        val preload = secondaryPlayer ?: return
        secondaryPlayer = null
        preload.removeListener(secondaryPlayerListener)
        detachCrossfadeAudioDiagnostics(preload)
        preload.release()
    }

    private fun scheduleCrossfade() = armCrossfade(reusePendingPreparation = false)

    private fun armCrossfade(reusePendingPreparation: Boolean) {
        if (isCrossfading || !::player.isInitialized) return
        val ownsPreviousSchedule = reusePendingPreparation && crossfadePositionScheduler.stillOwnsSchedule()
        val duration = crossfadeDuration
        val owner = player
        if (isDestroying || !crossfadeEnabled || duration <= 0 || !owner.isPlaying || listenTogetherGuestLock.value) {
            return discardCrossfadePreparation()
        }
        val mediaDuration = owner.duration
        if (mediaDuration == C.TIME_UNSET || mediaDuration <= duration || (crossfadeGapless && isNextItemGapless())) {
            return discardCrossfadePreparation()
        }

        val trigger = mediaDuration - duration
        if (!ownsPreviousSchedule && owner.currentPosition >= trigger) return discardCrossfadePreparation()
        val currentIndex = owner.currentMediaItemIndex
        val repeatMode = owner.repeatMode
        val shuffle = owner.shuffleModeEnabled
        fun nextIndex() = if (repeatMode == Player.REPEAT_MODE_ONE) currentIndex else owner.nextMediaItemIndex
        val next = nextIndex()
        if (next == C.INDEX_UNSET || currentIndex == C.INDEX_UNSET) return discardCrossfadePreparation()

        if (!ownsPreviousSchedule) discardCrossfadePreparation()
        val timeline = owner.currentTimeline
        val artwork = owner.getMediaItemAt(next).mediaMetadata.artworkUri?.toString()
        crossfadePositionScheduler.arm(
            scope = coroutineScope,
            triggerPosition = trigger,
            position = { owner.currentPosition },
            speed = { owner.playbackParameters.speed },
            isStillOwner = {
                player === owner && owner.isPlaying && !listenTogetherGuestLock.value &&
                    owner.currentTimeline === timeline && owner.currentMediaItemIndex == currentIndex &&
                    owner.repeatMode == repeatMode && owner.shuffleModeEnabled == shuffle && nextIndex() == next
            },
            prepareAudio = ::preloadCrossfade,
            prefetchArtwork = {
                if (artwork != null) {
                    try {
                        withContext(NzikDispatchers.DATA) { ImageCacheFactory.preloadImage(artwork) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Timber.tag("PlayerServiceModern").w(error, "Could not preload crossfade artwork")
                    }
                }
            },
            beginFade = { startCrossfade(duration) },
            onOwnershipLost = ::discardCrossfadePreparation,
        )
    }

    private fun isNextItemGapless(): Boolean {
        val current = player.currentMediaItem ?: return false
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return false
        val next = player.getMediaItemAt(nextIndex)

        val currentMeta = current.mediaMetadata
        val nextMeta = next.mediaMetadata

        // 1. Check explicitly set albumId in extras
        val currentAlbumId = currentMeta.extras?.getString("albumId")
        val nextAlbumId = nextMeta.extras?.getString("albumId")
        if (!currentAlbumId.isNullOrBlank() && currentAlbumId == nextAlbumId) return true

        // 2. Check explicitly set albumTitle
        val currentAlbumTitle = currentMeta.albumTitle?.toString()
        val nextAlbumTitle = nextMeta.albumTitle?.toString()
        if (!currentAlbumTitle.isNullOrBlank() && currentAlbumTitle == nextAlbumTitle) return true

        // 3. Fallback: YouTube Music albums often lack album metadata but share the exact same artwork URL
        val currentArtwork = currentMeta.artworkUri?.toString()
        val nextArtwork = nextMeta.artworkUri?.toString()
        if (!currentArtwork.isNullOrBlank() && !currentArtwork.startsWith("android.resource")) {
            val baseCurrent = currentArtwork.substringBefore("=").substringBefore("?")
            val baseNext = nextArtwork?.substringBefore("=")?.substringBefore("?")
            if (baseCurrent == baseNext) return true
        }

        return false
    }

    private fun createCrossfadeExoPlayer(): ExoPlayer {
        // Issue #881 (gh-881): the secondary (crossfade) player is wrapped in the diagnostics
        // facade too — a seek landing on it must be visible in the same SEEK_CALL journal (spec S1).
        return DiagExoPlayer(
            createPlaybackEngineBuilder(
                context = this,
                mediaSourceFactory = createMediaSourceFactory(),
                renderersFactory = createRendersFactory(),
                handleAudioFocus = false,
            )
                .build(),
            onTransportRequested = ::cancelTrackBoundaryPauseFor,
        ).also { attachCrossfadeAudioDiagnostics(it) }
    }

    /** Builds the guarded delegation facade for [targetPlayer]: the legacy "all commands" forwarding behavior (pre-Android-13 compatibility) plus the Listen Together guest-lock policy (spec-listen-together-guest-lock-hardening, spine AD-1/AD-2/AD-6). */
    private fun createGuestGuardPlayer(targetPlayer: Player): ListenTogetherGuestGuardPlayer {
        // Issue #881 (Phase 3, Fix B): coalesce rapid absolute seeks while the player is still
        // recovering from the previous one — one full data-source re-open + buffer refill per
//
        // seek kept the progress bar frozen in a user↔player feedback loop (field logs
        // 2026-10-03). The guard stays outermost so the guest lock vetoes seeks before they
//
        // reach the coalescer; the coalescer wraps the diagnostics facade so SEEK_CALL still
        // sees every seek that actually lands on the player.
//
        // Review patch: keep the coalescer reachable so the crossfade swap can detach the
        // OLD wrapper before this facade is rebuilt over a new player.
        val coalescer = SeekCoalescingPlayer(targetPlayer)
        currentSeekCoalescer = coalescer
        val guard = ListenTogetherGuestGuardPlayer(coalescer, applicationContext)
        // Guest play/pause intent (only the guest's own taps reach the guarded facade): play
        // resyncs the guest to the host's position, pause is kept across host skips
//
        // (spec-listen-together-guest-lock-hardening).
        guard.onGuestPlayPause = { playWhenReady ->
            ListenTogetherManager.getInstance()?.guestPlayPause(playWhenReady)
        }
        guestGuardPlayerTarget = targetPlayer
        return guard
    }

    private fun preloadCrossfade() {
        if (isCrossfading || secondaryPlayer != null || pendingSessionPlayerRelease != null) return

        val currentIndex = player.currentMediaItemIndex
        if (currentIndex == C.INDEX_UNSET) return

        val nextIndex = if (player.repeatMode == Player.REPEAT_MODE_ONE) {
            currentIndex
        } else {
            player.nextMediaItemIndex
        }
        if (nextIndex == C.INDEX_UNSET) return

        secondaryPlayer = createCrossfadeExoPlayer()
        val secPlayer = secondaryPlayer ?: return
        secPlayer.addListener(secondaryPlayerListener)
        secPlayer.addAnalyticsListener(PlaybackStatsListener(false, this@PlayerServiceModern))

        val itemCount = player.mediaItemCount
        val items = mutableListOf<MediaItem>()
        for (i in 0 until itemCount) {
            items.add(player.getMediaItemAt(i))
        }

        secPlayer.setMediaItems(items, nextIndex, 0L)
        secPlayer.volume = 0f
        secPlayer.repeatMode = player.repeatMode
        secPlayer.shuffleModeEnabled = player.shuffleModeEnabled
        secPlayer.shuffleOrder = player.shuffleOrder
        secPlayer.playbackParameters = player.playbackParameters
        secPlayer.skipSilenceEnabled = player.skipSilenceEnabled

        secPlayer.playWhenReady = false
        secPlayer.prepare()
        crossfadeAudioDiagnostics[secPlayer]?.mark("PRELOAD_PREPARED")
    }

    private fun startCrossfade(armedDuration: Int) {
        if (isCrossfading) return
        if (!crossfadeEnabled || crossfadeDuration != armedDuration || (crossfadeGapless && isNextItemGapless())) {
            return discardCrossfadePreparation()
        }

        val nextPlayer = secondaryPlayer ?: return
        if (nextPlayer.playbackState != Player.STATE_READY || nextPlayer.playerError != null) {
            return discardCrossfadePreparation()
        }
        markCrossfadeAudio("START_ATTEMPT", player, nextPlayer)
        isCrossfading = true
        
        // Guard the entire swap so listener callbacks don't kill the crossfade
        isInternalCrossfadeSeek = true

        val startVolume = player.volume
        
        // Setup the swap
        val currentPlayer = player
        val handoff = CrossfadeHandoff(nextPlayer, currentPlayer, startVolume)
        activeCrossfade = handoff
        // Read current settings at handoff: they may have changed after preloading.
        val repeatMode = currentPlayer.repeatMode
        nextPlayer.repeatMode = repeatMode
        nextPlayer.shuffleModeEnabled = currentPlayer.shuffleModeEnabled
        currentPlayer.removeListener(this)
        currentPlayer.removeListener(playbackDiagnosticsListener)
        // The outgoing track must never advance into the already-playing incoming track.
        currentPlayer.pauseAtEndOfMediaItems = true
        currentPlayer.repeatMode = Player.REPEAT_MODE_OFF
        nextPlayer.playbackParameters = currentPlayer.playbackParameters
        nextPlayer.skipSilenceEnabled = currentPlayer.skipSilenceEnabled
        fadingPlayer = currentPlayer
        player = nextPlayer
        playerUpdateTrigger.value++
        secondaryPlayer = null
        
        // Keep listeners tied to this pair; later handoffs must never inherit them.
        handoff.endListener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (activeCrossfade === handoff && events.contains(Player.EVENT_AUDIO_SESSION_ID)) {
                    syncAudioEffectSession(nextPlayer, "outgoing-session-event")
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    handoff.outgoingEnded = true
                }
            }
        }
        currentPlayer.addListener(handoff.endListener)
        handoff.syncListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (activeCrossfade === handoff) {
                    // The fade clock only elapses while the incoming track actually plays audio;
                    // a buffering or paused incoming track freezes the fade instead of burning it.
                    handoff.timeline?.setPlaying(isPlaying, crossfadeClockMs())
                }
                if (activeCrossfade !== handoff) return
                if (isPlaying && !handoff.outgoingEnded && currentPlayer.playbackState != Player.STATE_ENDED) {
                    currentPlayer.play()
                } else {
                    currentPlayer.pause()
                }
            }
        }
        nextPlayer.addListener(handoff.syncListener)

        // Register listeners to the new primary player
        nextPlayer.removeListener(secondaryPlayerListener)
        nextPlayer.addListener(this)
        // Issue #881 (gh-881): diagnostics follow the PRIMARY player across the swap
        nextPlayer.addListener(playbackDiagnosticsListener)

        // Update MediaSession to show the new song in the UI: the guarded facade is rebuilt and
        // re-attached to the new player (AD-5: the guest lock must survive crossfade swaps).
        // A failed swap would leave the session wrapping the outgoing player that
        // cleanupCrossfade() releases, orphaning the notification, so one retry is forced and
        // the outcome is tracked on the handoff for the orphan-proof cleanup.
        if (rebindMediaSessionPlayer(player)) {
            handoff.sessionSwapped = true
        } else {
            Timber.tag("PlayerServiceModern").e("Failed to swap player in MediaSession, retrying")
            handoff.sessionSwapped = rebindMediaSessionPlayer(player)
        }

        // Issue #881 (gh-881): instance identity across the crossfade swap (spec S6) — a mismatch
        // between the facade/UI trigger and the raw player would prove the UI kept driving an
//
        // old instance (the "UI attached to the old player" hypothesis).
        Timber.tag(PLAYBACK_DIAG_TAG).i(
            "CROSSFADE_SWAP oldPlayerIdentity=%s newPlayerIdentity=%s facadeIdentity=%s trigger=%d",
            System.identityHashCode(currentPlayer),
            System.identityHashCode(nextPlayer),
            System.identityHashCode(guestGuardPlayer),
            playerUpdateTrigger.value,
        )

        nextPlayer.volume = 0f
        nextPlayer.playWhenReady = currentPlayer.playWhenReady
        bindAudioEffectPlayer(nextPlayer)
        // Include transition publication and dispatcher lateness in the fade's elapsed time.
        // Metrolist model: the fade duration is corrected by the playback speed.
        val fadeSpeed = nextPlayer.playbackParameters.speed.coerceAtLeast(0.1f)
        val durationMs = (armedDuration.toLong() / fadeSpeed).toLong().coerceAtLeast(0L)
        val timeline = CrossfadeTimeline(crossfadeClockMs(), nextPlayer.isPlaying)
        handoff.timeline = timeline
        markCrossfadeAudio("FADE_BEGIN", currentPlayer, nextPlayer)
        if (activeCrossfade !== handoff) {
            isInternalCrossfadeSeek = false
            return
        }
        // Preparation happened before the primary listener was attached. Publish the same
        // transition as normal playback, including repeat-one's radio suppression.
        try {
            val reason = if (repeatMode == Player.REPEAT_MODE_ONE) {
                Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            } else {
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            }
            onMediaItemTransition(nextPlayer.currentMediaItem, reason)
        } catch (e: Exception) {
            markCrossfadeAudio("TRANSITION_ERROR", currentPlayer, nextPlayer)
            Timber.tag("PlayerServiceModern").e(e, "Failed to publish crossfade transition")
            if (activeCrossfade === handoff) {
                try {
                    if (player === nextPlayer) nextPlayer.volume = startVolume
                } finally {
                    cleanupCrossfade()
                }
            }
            return
        } finally {
            isInternalCrossfadeSeek = false
        }

        crossfadeJob = coroutineScope.launch(NzikDispatchers.UI) {
            var completed = false
            try {
                // Metrolist model: 20 fixed steps over the speed-corrected duration.
                val updateIntervalMs = (durationMs / 20L).coerceAtLeast(1L)
                var firstStep = true
                var midpointMarked = false
                while (true) {
                    if (activeCrossfade !== handoff) return@launch
                    // The fade clock only advances while the incoming track actually plays audio;
                    // a buffering or paused incoming track freezes the gains in place.
                    timeline.setPlaying(nextPlayer.isPlaying, crossfadeClockMs())
                    // End, error and explicit stop cannot resume this fade. Pause/buffering can.
                    if (nextPlayer.playbackState == Player.STATE_ENDED ||
                        nextPlayer.playbackState == Player.STATE_IDLE || nextPlayer.playerError != null
                    ) return@launch
                    if (!nextPlayer.isPlaying) {
                        delay(100L)
                        continue
                    }
                    val elapsedMs = timeline.elapsedMs(crossfadeClockMs())
                    val progress = if (durationMs == 0L) 1f else {
                        (elapsedMs.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat()
                    }
                    val fadeIn = 1f - (1f - progress) * (1f - progress)
                    nextPlayer.volume = startVolume * fadeIn
                    currentPlayer.volume = startVolume * (1f - fadeIn)
                    if (firstStep) {
                        markCrossfadeAudio("FADE_FIRST_STEP", currentPlayer, nextPlayer)
                        firstStep = false
                    }
                    if (!midpointMarked && progress >= 0.5f) {
                        markCrossfadeAudio("FADE_MIDPOINT", currentPlayer, nextPlayer)
                        midpointMarked = true
                    }
                    if (elapsedMs >= durationMs) break
                    // A delayed update catches up instead of extending all remaining steps.
                    val remainingMs = durationMs - timeline.elapsedMs(crossfadeClockMs())
                    if (remainingMs > 0L) delay(minOf(updateIntervalMs, remainingMs))
                }
                nextPlayer.volume = startVolume
                currentPlayer.volume = 0f
                markCrossfadeAudio("FADE_COMPLETE", currentPlayer, nextPlayer)
                completed = true
            } catch (e: CancellationException) {
                markCrossfadeAudio("FADE_CANCELLED", currentPlayer, nextPlayer)
                throw e
            } catch (e: Exception) {
                markCrossfadeAudio("FADE_ERROR", currentPlayer, nextPlayer)
                Timber.tag("PlayerServiceModern").e(e, "Error during crossfade")
            } finally {
                // Cancellation can finish after a new pair has started. Only its owner cleans up.
                if (activeCrossfade === handoff) {
                    try {
                        if (!completed && player === nextPlayer) nextPlayer.volume = startVolume
                    } finally {
                        cleanupCrossfade()
                        crossfadeJob = null
                    }
                    if (completed && !isDestroying) scheduleCrossfade()
                }
            }
        }
    }

    private fun cancelCrossfadeAndReset() {
        crossfadeJob?.cancel()
        crossfadeJob = null
        crossfadePositionScheduler.cancel()
        if (isCrossfading) player.volume = preferences.getFloat(playbackVolumeKey, 1f)
        cleanupCrossfade()
    }

    /**
     * Rebuilds the guarded facade over [target] and re-attaches it to the MediaSession.
     * Returns whether the session now wraps [target]; a failed re-attach leaves the session
     * on its previous player so the caller can retry or re-bind later.
     */
    private fun rebindMediaSessionPlayer(target: Player): Boolean {
        if (isDestroying || target !== player) return false
        // Issue #881 (gh-881), Phase 3 Fix B (review patch): detach the OLD coalescer before
        // rebuilding the facade - its settle job (<= 1 s) could otherwise fire against the
        // fading player, which cleanupCrossfade() releases later bypassing the wrapper chain.
        val rebound = runCatching {
            currentSeekCoalescer?.detach()
            guestGuardPlayer = createGuestGuardPlayer(target)
            notifyStoredDurationPlayerChanged()
            val sessionPlayer = SessionArtworkPlayer(mediaLibrarySessionCallback.wrapPlayer(guestGuardPlayer))
            mediaSession.player = sessionPlayer
            bindPlaybackTrackIdentity(sessionPlayer)
            ResolvedSessionDuration.notifyResolved()
        }.onFailure {
            Timber.tag("PlayerServiceModern").e(it, "Failed to rebind MediaSession player")
        }.isSuccess
        if (rebound) releasePendingSessionPlayer(target)
        return rebound
    }

    /** Retry independently of preloading, which is blocked while the old session target is retained. */
    private fun scheduleSessionRebindRecovery() {
        if (isDestroying || sessionRebindRecoveryJob?.isActive == true) return
        val retained = pendingSessionPlayerRelease ?: return
        sessionRebindRecoveryJob = coroutineScope.launch(NzikDispatchers.UI) {
            try {
                for (backoffMs in listOf(250L, 500L, 1000L, 2000L, 4000L)) {
                    delay(backoffMs)
                    if (isDestroying || pendingSessionPlayerRelease !== retained) return@launch
                    // Resolve the live target on every attempt; never reattach a superseded player.
                    val target = player
                    if (rebindMediaSessionPlayer(target)) {
                        scheduleCrossfade()
                        return@launch
                    }
                }
                Timber.tag("PlayerServiceModern").e("MediaSession recovery exhausted; retaining its player until destruction")
            } finally {
                sessionRebindRecoveryJob = null
            }
        }
    }

    /** Releases the retired player only after the session has moved away or has been released. */
    private fun releasePendingSessionPlayer(retainedPlayer: Player? = null) {
        val pending = pendingSessionPlayerRelease ?: return
        if (pending === retainedPlayer) {
            pendingSessionPlayerRelease = null
            return
        }
        pendingSessionPlayerRelease = null
        runCatching {
            pending.stop()
            detachCrossfadeAudioDiagnostics(pending)
        }.onFailure {
            Timber.tag("PlayerServiceModern").e(it, "Failed to stop retired session player")
        }
        runCatching { pending.release() }.onFailure {
            Timber.tag("PlayerServiceModern").e(it, "Failed to release retired session player")
        }
    }

    /** Closes the outgoing player's effect session so external effect apps drop the dead session. */
    private fun closeCrossfadeEffectSession(handoff: CrossfadeHandoff) {
        runCatching {
            val sessionId = handoff.outgoing.audioSessionId
            if (sessionId > 0 && sessionId != handoff.incoming.audioSessionId) {
                sendAudioEffectIntent(false, sessionId)
            }
        }.onFailure {
            Timber.tag("AudioEffectSession").w(it, "Failed to close outgoing crossfade effect session")
        }
    }

    private fun cleanupCrossfade() {
        val handoff = activeCrossfade
        activeCrossfade = null
        if (handoff != null) {
            markCrossfadeAudio("PAIR_CLEANUP", handoff.outgoing, handoff.incoming)
            handoff.incoming.removeListener(handoff.syncListener)
            handoff.outgoing.removeListener(handoff.endListener)
            if (!handoff.sessionSwapped && !rebindMediaSessionPlayer(player)) {
                // Failed rebinds keep the session on the first retired player. Later outgoing
                // players were never session-bound and do not need another retained slot.
                if (pendingSessionPlayerRelease == null) pendingSessionPlayerRelease = handoff.outgoing
                Timber.tag("PlayerServiceModern").e("Retaining outgoing player after MediaSession rebind failure")
                scheduleSessionRebindRecovery()
            }
            closeCrossfadeEffectSession(handoff)
            detachCrossfadeAudioDiagnostics(handoff.outgoing)
            if (pendingSessionPlayerRelease === handoff.outgoing) {
                runCatching {
                    handoff.outgoing.volume = 0f
                    handoff.outgoing.pause()
                }.onFailure {
                    Timber.tag("PlayerServiceModern").e(it, "Failed to silence retired session player")
                }
            } else {
                handoff.outgoing.stop()
                handoff.outgoing.release()
            }
        }
        fadingPlayer = null
        secondaryPlayer?.let {
            it.removeListener(secondaryPlayerListener)
            it.stop()
            detachCrossfadeAudioDiagnostics(it)
            it.release()
        }
        secondaryPlayer = null
        isCrossfading = false
    }
    // --- End Crossfade Logic ---

    companion object {
        var isRunning = false

        // Issue #866 (gh-866): transient shuffle confirmation duration on the command buttons.
        private const val SHUFFLE_OK_FLASH_MS = 1000L

        const val NotificationChannelId = "default_channel_id"

        /**
         * PendingIntent request code of the session activity (the "now playing" notification
         * content intent, spec-notification-click-opens-player). It must be DEDICATED: request
         * code 0 is the widget Pi slot (NZikWidgetManager.getOpenAppIntent — bare Intent to
         * MainActivity) and Intent.filterEquals ignores extras + activity flags, so the widget
         * refresh loop (1s while playing) would overwrite the session Pi and strip the token.
         * Precedent: 500 = PlaylistWidgetManager open-app Pi.
         */
        const val OpenPlayerSessionActivityRequestCode = 501
        private const val OpenLibraryStartupActivityRequestCode = 502

        const val SleepTimerNotificationId = 1002
        const val SleepTimerNotificationChannelId = "sleep_timer_channel_id"

        val PlayerErrorsToReload = arrayOf(416, 4003)
        val PlayerErrorsToSkip = arrayOf(2000)

        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"
        const val SEARCHED = "searched"

        /** The base (unsuffixed) streaming cache directory name — see [ProfileDataDirNames.MEDIA_CACHE_DIR]. */
        const val CACHE_DIRNAME = ProfileDataDirNames.MEDIA_CACHE_DIR

        private val STATS_HOSTS = setOf("s.youtube.com", "www.youtube.com", "music.youtube.com")
        private val STATS_PATHS = setOf("/api/stats/playback", "/api/stats/watchtime")

        fun isValidStatsUrl(url: String): Boolean {
            return try {
                val parsed = io.ktor.http.Url(url)
                parsed.protocol.name == "https" &&
                    parsed.port == 443 &&
                    parsed.host in STATS_HOSTS &&
                    parsed.encodedPath in STATS_PATHS &&
                    parsed.user == null
            } catch (_: Exception) { false }
        }
    }

}
