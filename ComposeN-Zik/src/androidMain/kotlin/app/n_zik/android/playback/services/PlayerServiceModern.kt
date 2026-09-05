package app.n_zik.android.playback.services

import app.n_zik.android.playback.services.automotive.session.AutoSessionCallback
import app.n_zik.android.core.database.Database
import app.kreate.android.me.knighthat.sync.YouTubeSync

import app.n_zik.android.MainApplication
import app.n_zik.android.utils.artistTextOrDb
import app.n_zik.android.utils.albumTitleOrDb
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.app.WallpaperManager.FLAG_LOCK
import android.app.WallpaperManager.FLAG_SYSTEM
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
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
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionToken
import app.it.fast4x.rimusic.repository.QuickPicksRepository
import app.n_zik.android.R
import app.n_zik.android.playback.services.createDataSourceFactory
import app.n_zik.android.playback.services.streamUrlCache
import app.n_zik.android.playback.services.markWebRemixFailed
import app.n_zik.android.playback.services.clearWebRemixFailures

import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.MoreExecutors
import it.fast4x.innertube.Innertube
import io.ktor.client.call.body
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.requests.nextPage
import app.n_zik.android.MainActivity
import app.n_zik.android.appContext
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.enums.AudioQualityFormat
import app.it.fast4x.rimusic.enums.ImageQualityFormat
import app.it.fast4x.rimusic.enums.DurationInMilliseconds
import app.it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import app.it.fast4x.rimusic.enums.ExoPlayerDiskCacheMaxSize
import app.it.fast4x.rimusic.enums.ExoPlayerMinTimeForEvent
import app.it.fast4x.rimusic.enums.NotificationButtons
import app.it.fast4x.rimusic.enums.NotificationType
import app.it.fast4x.rimusic.enums.PresetsReverb
import app.it.fast4x.rimusic.enums.QueueLoopType
import app.it.fast4x.rimusic.enums.WallpaperType
import app.it.fast4x.rimusic.extensions.audiovolume.AudioVolumeObserver
import app.it.fast4x.rimusic.extensions.audiovolume.OnAudioVolumeChangedListener
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.extensions.discord.DiscordPresenceManager
import app.n_zik.android.isHandleAudioFocusEnabled
import app.n_zik.android.isPauseOnHeadphoneDisconnectEnabled
import app.it.fast4x.rimusic.models.Event
import app.it.fast4x.rimusic.models.PersistentQueue
import app.it.fast4x.rimusic.models.PersistentSong
import app.it.fast4x.rimusic.models.QueuedMediaItem
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.asMediaItem
import app.n_zik.android.playback.utils.BitmapProvider
import app.n_zik.android.playback.utils.PlaybackDispatchers
import app.n_zik.android.playback.utils.SleepTimer
import app.n_zik.android.playback.utils.NZikRadio
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.download.services.MyDownloadService
import app.it.fast4x.rimusic.utils.CoilBitmapLoader
import app.it.fast4x.rimusic.utils.TimerJob
import app.it.fast4x.rimusic.utils.activityPendingIntent
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.audioQualityFormatKey
import app.it.fast4x.rimusic.utils.imageQualityFormatKey
import app.it.fast4x.rimusic.utils.audioReverbPresetKey
import app.it.fast4x.rimusic.utils.autoLoadSongsInQueueKey
import app.it.fast4x.rimusic.utils.bassboostEnabledKey
import app.it.fast4x.rimusic.utils.bassboostLevelKey
import app.it.fast4x.rimusic.utils.broadCastPendingIntent
import app.it.fast4x.rimusic.utils.closebackgroundPlayerKey
import app.it.fast4x.rimusic.utils.collect
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.utils.from
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import app.it.fast4x.rimusic.utils.enableWallpaperKey
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.exoPlayerCacheLocationKey
import app.it.fast4x.rimusic.utils.exoPlayerCustomCacheKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerMinTimeForEventKey
import app.it.fast4x.rimusic.utils.fadeInEffect
import app.it.fast4x.rimusic.utils.fadeOutEffect
import app.it.fast4x.rimusic.utils.forcePlay
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.intent
import app.it.fast4x.rimusic.utils.isAtLeastAndroid10
import app.it.fast4x.rimusic.utils.isAtLeastAndroid6
import app.it.fast4x.rimusic.utils.isAtLeastAndroid7
import app.it.fast4x.rimusic.utils.isAtLeastAndroid8
import app.it.fast4x.rimusic.utils.isPauseOnVolumeZeroEnabledKey
import app.it.fast4x.rimusic.utils.loudnessBaseGainKey
import app.it.fast4x.rimusic.utils.manageDownload
import app.it.fast4x.rimusic.utils.mediaItems
import app.it.fast4x.rimusic.utils.minimumSilenceDurationKey
import app.it.fast4x.rimusic.utils.notificationPlayerFirstIconKey
import app.it.fast4x.rimusic.utils.notificationPlayerSecondIconKey
import app.it.fast4x.rimusic.utils.notificationTypeKey
import app.it.fast4x.rimusic.utils.pauseListenHistoryKey
import app.it.fast4x.rimusic.utils.persistentQueueKey
import app.it.fast4x.rimusic.utils.playNext
import app.it.fast4x.rimusic.utils.playPrevious
import app.it.fast4x.rimusic.utils.playbackFadeAudioDurationKey
import app.it.fast4x.rimusic.utils.playbackPitchKey
import app.it.fast4x.rimusic.utils.playbackSpeedKey
import app.it.fast4x.rimusic.utils.playbackVolumeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.syncImportHistoryKey
import app.it.fast4x.rimusic.utils.syncPushHistoryKey
import app.it.fast4x.rimusic.utils.getSyncDirection
import app.it.fast4x.rimusic.utils.isNetworkConnected
import app.it.fast4x.rimusic.enums.SyncDirection
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import app.it.fast4x.rimusic.utils.ytCookieExpiredKey
import app.it.fast4x.rimusic.utils.putEnum
import app.it.fast4x.rimusic.utils.queueLoopTypeKey
import app.it.fast4x.rimusic.utils.resumePlaybackOnStartKey
import app.it.fast4x.rimusic.utils.resumePlaybackWhenDeviceConnectedKey
import app.it.fast4x.rimusic.utils.setGlobalVolume
import app.it.fast4x.rimusic.utils.showDownloadButtonBackgroundPlayerKey
import app.it.fast4x.rimusic.utils.showLikeButtonBackgroundPlayerKey
import app.it.fast4x.rimusic.utils.skipMediaOnErrorKey
import app.it.fast4x.rimusic.utils.skipSilenceKey
import app.it.fast4x.rimusic.utils.timer
import app.it.fast4x.rimusic.utils.toggleRepeatMode
import app.it.fast4x.rimusic.utils.toggleShuffleMode
import app.it.fast4x.rimusic.utils.volumeNormalizationKey
import app.it.fast4x.rimusic.utils.volumeBoostLevelKey
import app.it.fast4x.rimusic.utils.wallpaperTypeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import android.os.Binder as AndroidBinder
import androidx.compose.ui.util.fastMap
import app.it.fast4x.rimusic.utils.isDiscordBrowsingEnabledKey
import app.it.fast4x.rimusic.utils.isDiscordPresenceEnabledKey
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.playback.exceptions.ExplicitContentException
import app.n_zik.android.playback.exceptions.LoginRequiredException
import app.n_zik.android.playback.exceptions.PlayableFormatNonSupported
import app.n_zik.android.playback.exceptions.UnmatchedSongException
import app.n_zik.android.playback.exceptions.UnplayableException
import app.n_zik.android.playback.exceptions.VideoIdMismatchException
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import androidx.media3.datasource.HttpDataSource
import app.it.fast4x.rimusic.utils.crossfadeGaplessKey
import app.it.fast4x.rimusic.utils.crossfadeDurationKey
import androidx.compose.runtime.Stable
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.n_zik.android.widget.NZikWidgetManager
import app.it.fast4x.rimusic.utils.crossfadeEnabledKey
import java.net.UnknownHostException
import java.net.ConnectException
import java.nio.channels.UnresolvedAddressException


const val LOCAL_KEY_PREFIX = "local:"

val MediaItem.isLocal get() = mediaId.contains(LOCAL_KEY_PREFIX)
val Song.isLocal get() = id.contains(LOCAL_KEY_PREFIX)

val Song.isUnmatched: Boolean
    get() = (id.length != 11 || (durationText == "00:00" && totalPlayTimeMs == 1L))
            && !id.startsWith(LOCAL_KEY_PREFIX)

@UnstableApi
class PlayerServiceModern : MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback,
    SharedPreferences.OnSharedPreferenceChangeListener,
    OnAudioVolumeChangedListener {

    private val coroutineScope = CoroutineScope(Dispatchers.IO) + Job()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var mediaSession: MediaLibrarySession
    private var mediaLibrarySessionCallback: AutoSessionCallback =
        AutoSessionCallback(this, Database, MyDownloadHelper)
    private var sessionController: MediaController? = null
    lateinit var player: ExoPlayer
    val playerUpdateTrigger = MutableStateFlow(0)
    lateinit var cache: Cache
    lateinit var downloadCache: Cache
    private lateinit var audioVolumeObserver: AudioVolumeObserver
    private lateinit var bitmapProvider: BitmapProvider
    private var volumeNormalizationJob: Job? = null

    private val encryptedPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == isDiscordBrowsingEnabledKey) {
            discordPresenceManager?.onBrowsingSettingChanged()
        }
    }
    private var isPersistentQueueEnabled: Boolean = false
    private var isclosebackgroundPlayerEnabled = false
    private var audioManager: AudioManager? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null
    private lateinit var downloadListener: DownloadManager.Listener


    /**
     * Discord presence
     */
    private var discordPresenceManager: DiscordPresenceManager? = null

    var loudnessEnhancer: LoudnessEnhancer? = null
    private var binder = Binder()
    private var bassBoost: BassBoost? = null
    private var reverbPreset: PresetReverb? = null
    private var showLikeButton = true
    private var showDownloadButton = true

    lateinit var audioQualityFormat: AudioQualityFormat
    lateinit var imageQualityFormat: ImageQualityFormat
    lateinit var sleepTimer: SleepTimer
    private var timerJob: TimerJob? = null
    private var widgetProgressJob: Job? = null
    lateinit var nzikRadio: NZikRadio

    val currentMediaItem = MutableStateFlow<MediaItem?>(null)

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    private val currentSong = currentMediaItem.flatMapLatest { mediaItem ->
        val songId = mediaItem?.mediaId?.split("/")?.lastOrNull() ?: mediaItem?.mediaId ?: ""
        Database.songTable.findById( songId )
    }.stateIn(coroutineScope, SharingStarted.Lazily, null)

    var currentSongStateDownload = MutableStateFlow(Download.STATE_STOPPED)

    private var connectivityJob: Job? = null
    private val isNetworkAvailable = MutableStateFlow(true)
    private val waitingForNetwork = MutableStateFlow(false)

    var preferredDeviceId: Int? = null

    private var notificationManager: NotificationManager? = null

    private lateinit var notificationActionReceiver: NotificationActionReceiver

    private val audioBecomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && isPauseOnHeadphoneDisconnectEnabled()) {
                Timber.tag("PlayerServiceModern").d("Audio becoming noisy: pausing playback per user setting")
                player.pause()
            }
        }
    }

    @kotlin.OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        isRunning = true

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Network status observation
        connectivityJob?.cancel()
        connectivityJob = coroutineScope.launch {
            NetworkQualityHelper.observeConnection(this@PlayerServiceModern).collect { isAvailable ->
                isNetworkAvailable.value = isAvailable
                Timber.tag("PlayerServiceModern").d("network status: $isAvailable")
                if (isAvailable && waitingForNetwork.value) {
                    waitingForNetwork.value = false
                    if (player.playWhenReady && player.playbackState != Player.STATE_IDLE) {
                        withContext(Dispatchers.Main) {
                            binder.gracefulPlay()
                        }
                    }
                }
            }
        }

        val notificationType = preferences.getEnum(notificationTypeKey, NotificationType.Default)
        when (notificationType) {
            NotificationType.Default -> {
                // DEFAULT NOTIFICATION PROVIDER MODDED
                setMediaNotificationProvider(CustomMediaNotificationProvider(this)
                    .apply {
                        setSmallIcon(R.drawable.ic_launcher_monochrome)
                    }
                )
            }

            NotificationType.Advanced -> {
                // CUSTOM NOTIFICATION PROVIDER -> CUSTOM NOTIFICATION PROVIDER WITH ACTIONS AND PENDING INTENT
                // ACTUALLY NOT STABLE
                setMediaNotificationProvider(object : MediaNotification.Provider {
                    
                    private val defaultProvider = DefaultMediaNotificationProvider(this@PlayerServiceModern)

                    override fun createNotification(
                        mediaSession: MediaSession,
                        customLayout: ImmutableList<CommandButton>,
                        actionFactory: MediaNotification.ActionFactory,
                        onNotificationChangedCallback: MediaNotification.Provider.Callback
                    ): MediaNotification {
                        return updateCustomNotification(mediaSession)
                    }

                    override fun handleCustomCommand(
                        session: MediaSession,
                        action: String,
                        extras: Bundle
                    ): Boolean {
                        return false
                    }
                    
                    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo {
                        return defaultProvider.getNotificationChannelInfo()
                    }
                })
            }
        }

        runCatching {
            bitmapProvider = BitmapProvider(
                scope = coroutineScope,
                bitmapSize = (1200 * resources.displayMetrics.density).roundToInt(),
                colorProvider = { isSystemInDarkMode ->
                    if (isSystemInDarkMode) Color.BLACK else Color.WHITE
                }
            )
        }.onFailure {
            Timber.tag("PlayerServiceModern").e("Failed init bitmap provider ${it.stackTraceToString()}")
        }

        preferences.registerOnSharedPreferenceChangeListener(this)

        isPersistentQueueEnabled = preferences.getBoolean(persistentQueueKey, false)

        audioQualityFormat = preferences.getEnum(audioQualityFormatKey, AudioQualityFormat.Auto)
        imageQualityFormat = preferences.getEnum(imageQualityFormatKey, ImageQualityFormat.Auto)
        Timber.tag("NZik_Network").i("PlayerServiceModern: Initialized with Audio Quality: $audioQualityFormat, Image Quality: $imageQualityFormat")

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

        val cacheDir = when (cacheSize) {
            // Temporary directory deletes itself after close
            // It means songs remain on device as long as it's open
            ExoPlayerDiskCacheMaxSize.Disabled -> createTempDirectory(CACHE_DIRNAME).toFile()

            else ->
                // Looks a bit ugly but what it does is
                // check location set by user and return
                // appropriate path with [CACHE_DIRNAME] appended.
                when (preferences.getEnum(exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System)) {
                    ExoPlayerCacheLocation.System -> super.getCacheDir()
                    ExoPlayerCacheLocation.Private -> filesDir
                }.resolve(CACHE_DIRNAME)
        }

        // Ensure this location exists
        cacheDir.mkdirs()

        cache = SimpleCache(cacheDir, cacheEvictor, StandaloneDatabaseProvider(this))
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


        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(createMediaSourceFactory())
            .setRenderersFactory(createRendersFactory())
            .setHandleAudioBecomingNoisy(false)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                isHandleAudioFocusEnabled()
            )
            .setUsePlatformDiagnostics(false)
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .build()
            .apply {
                addListener(this@PlayerServiceModern)
                sleepTimer = SleepTimer(coroutineScope, this)
                addListener(sleepTimer)
                addAnalyticsListener(PlaybackStatsListener(false, this@PlayerServiceModern))
            }

        // Force player to add all commands available, prior to android 13
        val forwardingPlayer = createForwardingPlayer(player)

        mediaLibrarySessionCallback.apply {
            binder = this@PlayerServiceModern.binder
            toggleLike = ::toggleLike
            toggleDownload = ::toggleDownload
            toggleRepeat = ::toggleRepeat
            toggleShuffle = ::toggleShuffle
            startRadio = ::startRadio
            callPause = binder::gracefulPause
            actionSearch = ::actionSearch
        }

        // Build the media library session
        mediaSession =
            MediaLibrarySession.Builder(this, forwardingPlayer, mediaLibrarySessionCallback)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java)
                            .putExtra("expandPlayerBottomSheet", true),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setBitmapLoader(
                    CoilBitmapLoader(
                        this,
                        coroutineScope,
                        250 * resources.displayMetrics.density.toInt()
                    )
                )
                .setPeriodicPositionUpdateEnabled(false)
                .build()
        
        mediaLibrarySessionCallback.observeRepository(mediaSession)

        player.skipSilenceEnabled = preferences.getBoolean(skipSilenceKey, false)
        player.addListener(this@PlayerServiceModern)

        player.repeatMode = preferences.getEnum(queueLoopTypeKey, QueueLoopType.Default).type

        binder.player.playbackParameters = PlaybackParameters(
            preferences.getFloat(playbackSpeedKey, 1f),
            preferences.getFloat(playbackPitchKey, 1f)
        )
        binder.player.volume = preferences.getFloat(playbackVolumeKey, 1f)
        binder.player.setGlobalVolume(binder.player.volume)

        // Keep a connected controller so that notification works
        val sessionToken = SessionToken(this, ComponentName(this, PlayerServiceModern::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            sessionController = controllerFuture.get()
        }, MoreExecutors.directExecutor())

        audioVolumeObserver = AudioVolumeObserver(this)
        audioVolumeObserver.register(AudioManager.STREAM_MUSIC, this)

        // Download listener help to notify download change to UI
        downloadListener = object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) = run {
                if (download.request.id != currentMediaItem.value?.mediaId) return@run
                Timber.tag("PlayerServiceModern").d("onDownloadChanged current song ${currentMediaItem.value?.mediaId} state ${download.state} key ${download.request.id}")
                updateDownloadedState()
            }
        }
        MyDownloadHelper.getDownloadManager(this).addListener(downloadListener)

        notificationActionReceiver = NotificationActionReceiver(player)

        QuickPicksRepository.refreshIfNeeded()

        nzikRadio = NZikRadio(this, binder, coroutineScope)

        val filter = IntentFilter().apply {
            addAction(Action.play.value)
            addAction(Action.pause.value)
            addAction(Action.playPause.value)
            addAction(Action.next.value)
            addAction(Action.previous.value)
            addAction(Action.like.value)
            addAction(Action.download.value)
            addAction(Action.playradio.value)
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

        // Ensure that song is updated
        coroutineScope.launch {
            currentSong.debounce(1000).collect { song ->
                updateDownloadedState()

                updateDefaultNotification()
                withContext(Dispatchers.Main) {
                    updateWidgets()
                }
            }
        }

        maybeRestorePlayerQueue()

        maybeResumePlaybackWhenDeviceConnected()

        maybeBassBoost()

        maybeReverb()

        /* Queue is saved in events without scheduling it (remove this in future)*/
        // Load persistent queue when start activity and save periodically in background
        if (isPersistentQueueEnabled) {
            maybeResumePlaybackOnStart()

            val scheduler = Executors.newScheduledThreadPool(1)
            scheduler.scheduleWithFixedDelay({
                maybeSavePlayerQueue()
            }, 0, 30, TimeUnit.SECONDS)

        }

        /**
         * Discord presence
         */
        if (encryptedPreferences.getBoolean(isDiscordPresenceEnabledKey, false)) {
            val token = encryptedPreferences.getString(discordPersonalAccessTokenKey, "")
            if (token?.isNotEmpty() == true) {
                discordPresenceManager = DiscordPresenceManager(
                    context = this,
                    getToken = { token },
                    getBrowsingEnabled = { encryptedPreferences.getBoolean(isDiscordBrowsingEnabledKey, true) },
                )
            }
        }
        
        encryptedPreferences.registerOnSharedPreferenceChangeListener(encryptedPrefsListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            super.onStartCommand(intent, flags, startId)
        } catch (e: Exception) {
            Timber.tag("PlayerServiceModern").e(e, "Failed to start service safely (ForegroundServiceStartNotAllowedException)")
            START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        mediaSession

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        maybeSavePlayerQueue()
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
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

            // Local DB history (gated by pauseListenHistoryKey)
            if (!pauseHistory) {
                Database.asyncTransaction {
                    if ( totalPlayTimeMs > 5000 ) {
                        songTable.updateTotalPlayTime( songId, totalPlayTimeMs, true )
                    }

                    if ( totalPlayTimeMs > minTimeForEvent.asMillis ) {
                        insertIgnore(mediaItem)

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

            // YTM history push (always runs, like Metrolist — NOT gated by pauseListenHistoryKey)
            if (totalPlayTimeMs > minTimeForEvent.asMillis) {
                coroutineScope.launch(Dispatchers.IO) {
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
                            val httpResponse = Innertube.player(videoId = songId)
                            val playerResponse = httpResponse.body<it.fast4x.innertube.models.PlayerResponse>()
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

                    Timber.tag("PlayerServiceModern").d("registerPlayback for $songId: streamClient=$streamClient, url=${playbackUrl?.take(120)}...")
                    runCatching {
                        val response = Innertube.registerPlayback(url = playbackUrl!!, cpn = "")
                        val statusCode = response.status.value
                        if (statusCode !in 200..299) {
                            Timber.tag("PlayerServiceModern").w("registerPlayback failed for $songId: HTTP $statusCode")
                        } else {
                            Timber.tag("PlayerServiceModern").d("History pushed to YTM: $songId (status=$statusCode)")
                        }
                    }.onFailure { e ->
                        Timber.tag("PlayerServiceModern").e(e, "registerPlayback failed for $songId")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("PlayerServiceModern").e(e, "Error in onPlaybackStatsReady")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
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
        isRunning = false
        runCatching {
            /**
             * Discord presence cleanup
             */
            if (encryptedPreferences.getBoolean(isDiscordPresenceEnabledKey, false)) {
                Toaster.i(R.string.discord_presence_closed)
                discordPresenceManager?.onStop()
            }
            maybeSavePlayerQueue()
            preferences.unregisterOnSharedPreferenceChangeListener(this)
            encryptedPreferences.unregisterOnSharedPreferenceChangeListener(encryptedPrefsListener)
            stopService(intent<MyDownloadService>())
            stopService(intent<PlayerServiceModern>())
            player.removeListener(this)
            player.stop()
            player.release()
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
            mediaLibrarySessionCallback.release()
            mediaSession.release()
            sessionController?.release()
            sessionController = null
            cache.release()
            //downloadCache.release()
            MyDownloadHelper.getDownloadManager(this).removeListener(downloadListener)
            loudnessEnhancer?.release()
            audioVolumeObserver.unregister()
            timerJob?.cancel()
            timerJob = null
            notificationManager?.cancel(NotificationId)
            notificationManager?.cancelAll()
            notificationManager = null
            coroutineScope.cancel()

        }.onFailure {
            Timber.tag("PlayerServiceModern").e("Failed onDestroy "+it.stackTraceToString())
        }
        super.onDestroy()
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
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
        /*
        if (direction == 0) {
            binder.player.seekToPreviousMediaItem()
        } else {
            binder.player.seekToNextMediaItem()
        }

         */
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (!isInternalCrossfadeSeek && (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK || reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)) {
            cancelCrossfadeAndReset()
        }
        scheduleCrossfade()

        val networkQuality = NetworkQualityHelper.getCurrentNetworkQuality(this)
        val effectiveAudio = if (audioQualityFormat == AudioQualityFormat.Auto) "Auto→$networkQuality" else audioQualityFormat.name
        val effectiveImage = if (imageQualityFormat == ImageQualityFormat.Auto) "Auto→$networkQuality" else imageQualityFormat.name
        Timber.tag("NZik_Network").i("onMediaItemTransition - Network: $networkQuality | Audio: $effectiveAudio | Image: $effectiveImage")

        // Clear recovery counter for the new media item (fresh start)
        mediaItem?.mediaId?.let { recoveryAttempts.remove(it) }

        // Safety net: detect if ExoPlayer auto-advanced despite skipMediaOnError being OFF.
        // This should not happen with our fixes, but if it does, show a visible warning.
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            && !preferences.getBoolean(skipMediaOnErrorKey, false)
            && player.playerError != null
        ) {
            Timber.tag("PlayerServiceModern").e("UNEXPECTED auto-skip detected with skipMediaOnError=OFF! error=${player.playerError?.errorCodeName}")
            Toaster.w(R.string.stream_unexpected_skip)
        }

        currentMediaItem.update { mediaItem }

        // Immediately persist the full MediaItem metadata to the DB.
        // This seeds the Song row with real data (title, artist, thumbnail)
        // before StreamResolver can insert a blank placeholder for FK satisfaction.
        // insertIgnore uses merge logic that preserves existing non-empty fields.
        mediaItem?.let { item ->
            try {
                // Background Auto-Fix: Fetch missing album/artist metadata silently
                // Uses the service's coroutineScope so it gets cancelled properly on service destroy
                coroutineScope.launch(Dispatchers.IO) {
                    // Immediately persist the full MediaItem metadata to the DB.
                    // This seeds the Song row with real data (title, artist, thumbnail)
                    // before StreamResolver can insert a blank placeholder for FK satisfaction.
                    Database.transaction {
                        insertIgnore(item)
                    }

                    try {
                        val songId = item.mediaId
                        // Skip local songs and invalid IDs
                        if (songId.startsWith("local:") || songId.length != 11) return@launch
                        
                        val existingAlbum = Database.albumTable.findBySongIdDirect(songId)
                        // Skip if album exists AND has a real YouTube album ID (valid Base64URL)
                        val hasValidAlbum = existingAlbum?.id != null && existingAlbum.id.removePrefix(MODIFIED_PREFIX).let { it.length > 11 && it.matches("^[A-Za-z0-9_-]+\$".toRegex()) }
                        if (hasValidAlbum) return@launch
                        
                        val dbSong = Database.songTable.findByIdDirect(songId) ?: return@launch
                        Timber.tag("auto_fix").d("Song '%s' has no album, attempting background fix...", dbSong.title)
                        
                        // First try the official nextPage API (most reliable)
                        val nextPageResult = Innertube.nextPage(videoId = songId)
                            ?.getOrNull()
                            ?.itemsPage
                            ?.items
                            ?.firstOrNull { it.key == songId }
                        
                        if (nextPageResult?.album?.endpoint?.browseId != null) {
                            Timber.tag("auto_fix").d("nextPage found album '%s' for song", nextPageResult.album?.name)
                            Database.upsert(nextPageResult)
                            return@launch
                        }
                        
                        // Fallback: search by title + artist
                        val query = "${dbSong.cleanTitle()} ${dbSong.cleanArtistsText()}".trim()
                        Timber.tag("auto_fix").d("Falling back to search: %s", query)
                        
                        val searchResult = Innertube.searchPage<Innertube.SongItem>(
                            query = query, params = Innertube.SearchFilter.Song.value,
                            fromMusicShelfRendererContent = { content -> Innertube.SongItem.from(content) }
                        )?.getOrNull()
                        
                        // ONLY use exact ID match to avoid false positives
                        val foundSong = searchResult?.items?.firstOrNull { it.key == songId }
                        
                        if (foundSong?.album?.endpoint?.browseId != null) {
                            Timber.tag("auto_fix").d("Search found album '%s' for song", foundSong.album?.name)
                            Database.upsert(foundSong)
                        } else {
                            Timber.tag("auto_fix").d("No album found for song '%s'", dbSong.title)
                        }
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
        // Update bitmap with proper fallback handling
        val artworkUri = binder.player.currentMediaItem?.mediaMetadata?.artworkUri
        if (artworkUri != null) {
            bitmapProvider.load(artworkUri) {
                updateDefaultNotification()
                updateWidgets()
            }
        } else {
            // If no artwork, force the use of the default bitmap
            bitmapProvider.load(null) {
                updateDefaultNotification()
                updateWidgets()
            }
        }

        /**
         * Discord presence
         */
        val title = mediaItem?.mediaMetadata?.title ?: "<none>"
        val duration = player.duration
        val now = System.currentTimeMillis()
        if (encryptedPreferences.getBoolean(isDiscordPresenceEnabledKey, false)) {
            val token = encryptedPreferences.getString(discordPersonalAccessTokenKey, "")
            if (token?.isNotEmpty() == true) {
                // Capture current values to avoid thread safety issues
                val currentPosition = player.currentPosition
                val isPlaying = player.isPlaying
                discordPresenceManager?.onPlayingStateChanged(
                    mediaItem,
                    isPlaying,
                    currentPosition,
                    duration,
                    now,
                    getCurrentPosition = { currentPosition },
                    isPlayingProvider = { isPlaying }
                )
            }
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        scheduleCrossfade()
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            maybeSavePlayerQueue()
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateDefaultNotification()
        if (shuffleModeEnabled) {
            val shuffledIndices = IntArray(player.mediaItemCount) { it }
            shuffledIndices.shuffle()
            shuffledIndices[shuffledIndices.indexOf(player.currentMediaItemIndex)] = shuffledIndices[0]
            shuffledIndices[0] = player.currentMediaItemIndex
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        }
    }



    /**
     * Discord presence
     */
    @UnstableApi
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            scheduleCrossfade()
            startWidgetUpdates()
        } else {
            stopWidgetUpdates()
        }
        
        val item = player.currentMediaItem
        val title = item?.mediaMetadata?.title ?: "<none>"
        val duration = player.duration
        val now = System.currentTimeMillis()
        
        if (encryptedPreferences.getBoolean(isDiscordPresenceEnabledKey, false)) {
            val token = encryptedPreferences.getString(discordPersonalAccessTokenKey, "")
            if (token?.isNotEmpty() == true) {
                // Capture current values to avoid thread safety issues
                val currentPosition = player.currentPosition
                discordPresenceManager?.onPlayingStateChanged(
                    item,
                    isPlaying,
                    currentPosition,
                    duration,
                    now,
                    getCurrentPosition = { currentPosition },
                    isPlayingProvider = { isPlaying }
                )
            }
        }
        updateWidgets()

        // Start/stop the per-second widget progress refresh
        if (isPlaying) {
            if (widgetProgressJob?.isActive != true) {
                widgetProgressJob = coroutineScope.launch(Dispatchers.Main) {
                    while (true) {
                        delay(1000)
                        if (player.isPlaying) {
                            updateWidgets()
                        } else {
                            break
                        }
                    }
                }
            }
        } else {
            widgetProgressJob?.cancel()
            widgetProgressJob = null
        }
    }

    /**
     * Tracks recovery attempts per media item to prevent infinite retry loops.
     * Key = mediaId, Value = number of recovery attempts already made.
     */
    private val recoveryAttempts = mutableMapOf<String, Int>()
    private val MAX_RECOVERY_ATTEMPTS = 7
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

    private fun isNetworkRelatedError(error: PlaybackException): Boolean {
        if (isExpiredUrlError(error) || isRangeNotSatisfiableError(error) || isPageReloadError(error)) return false
        return error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ||
            error.cause is ConnectException ||
            error.cause is UnknownHostException ||
            (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
    }

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
        try {
            clearStreamCaches()
            Timber.tag("PlayerServiceModern").d("Cleared stream caches for $mediaId")
        } catch (e: Exception) {
            Timber.tag("PlayerServiceModern").w(e, "Failed to clear stream caches for $mediaId")
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)

        // Extract meaningful error detail from the exception chain
        val errorDetail = error.message
            ?: error.cause?.message
            ?: error.cause?.cause?.message
            ?: error.errorCodeName
        Timber.tag("PlayerServiceModern").e("onPlayerError code=${error.errorCode} (${error.errorCodeName}) detail=[$errorDetail] cause=${error.cause} rootCause=${error.cause?.cause}")

        val currentMediaId = player.currentMediaItem?.mediaId

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

        val playbackConnectionExeptionList = listOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, //primary error code to manage
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        )

        // check if error is caused by internet connection
        val isConnectionError = (error.cause?.cause is PlaybackException && (error.cause?.cause as PlaybackException).errorCode in playbackConnectionExeptionList)
                || error.cause is UnknownHostException
                || error.cause is UnresolvedAddressException

        if (!isNetworkAvailable.value || isConnectionError) {
            waitingForNetwork.value = true
            Toaster.noInternet()
            return
        }

        if (error.cause.isFatalCustomException()) {
            val rootCause = generateSequence<Throwable>(error) { it.cause }.firstOrNull { it is ExplicitContentException }
            if (rootCause != null) {
                Toaster.w(R.string.parental_control_is_enabled)
            }
            val unmatchedCause = generateSequence<Throwable>(error) { it.cause }.firstOrNull { it is UnmatchedSongException }
            if (unmatchedCause != null) {
                Toaster.w(R.string.playback_blocked_match_first)
            }
            // LoginRequiredException — show YouTube's reason (e.g. "Sign in to confirm your age")
            val loginCause = generateSequence<Throwable>(error) { it.cause }.firstOrNull { it is LoginRequiredException }
            if (loginCause != null) {
                val loginReason = loginCause.message?.removePrefix("Login required: ") ?: ""
                val isAgeRestricted = loginReason.contains("age", ignoreCase = true) ||
                    loginReason.contains("confirm your age", ignoreCase = true)
                val displayMessage = when {
                    isAgeRestricted -> getString(R.string.error_age_restricted, loginReason)
                    loginReason.isNotEmpty() -> getString(R.string.error_login_required, loginReason)
                    else -> getString(R.string.error_youtube_login)
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
                        if (httpCode == 401) {
                            MainApplication.cookieStatus = MainApplication.CookieStatus.EXPIRED
                            preferences.edit().putBoolean(ytCookieExpiredKey, true).apply()
                            Timber.tag("PlayerServiceModern").w("Cookie expired (401) — marking session as EXPIRED")
                            Toaster.w(R.string.error_session_expired)
                        }
                        streamUrlCache.invalidate(currentMediaId)
                        try {
                            cache.removeResource(currentMediaId)
                        } catch (_: Exception) {}
                        val streamClient = playbackDataCache[currentMediaId]?.streamClient ?: "WEB_REMIX"
                        markClientFailed(streamClient, currentMediaId)
                        coroutineScope.launch {
                            if (InnerTubeXPlayer.refreshAfterStreamRejection()) {
                                clearWebRemixFailures()
                            }
                        }
                        markWebRemixFailed(currentMediaId)
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
                        try { cache.removeResource(currentMediaId) } catch (_: Exception) {}
                        val streamClient = playbackDataCache[currentMediaId]?.streamClient ?: "WEB_REMIX"
                        markClientFailed(streamClient, currentMediaId)
                        coroutineScope.launch {
                            if (InnerTubeXPlayer.refreshAfterStreamRejection()) {
                                clearWebRemixFailures()
                            }
                        }
                        markWebRemixFailed(currentMediaId)
                    }

                    // Audio renderer error — corrupted audio track state
                    isAudioRendererError(error) -> {
                        Timber.tag("PlayerServiceModern").d("Handling audio renderer error for $currentMediaId — extra delay")
                        streamUrlCache.invalidate(currentMediaId)
                        try { cache.removeResource(currentMediaId) } catch (_: Exception) {}
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
                coroutineScope.launch(Dispatchers.Main) {
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
                Toaster.w(R.string.stream_error_retrying, formatArgs = arrayOf(errorDetail.take(80)))
                return
            } else {
                Timber.tag("PlayerServiceModern").e("onPlayerError recovery exhausted ($MAX_RECOVERY_ATTEMPTS attempts) for $currentMediaId")
                recoveryAttempts.remove(currentMediaId)
            }
        }

        // Non-recoverable, non-network error: only skip if the option is ON
        if (!preferences.getBoolean(skipMediaOnErrorKey, false) || !player.hasNextMediaItem()) {
            // Show error toast with code name for debugging
            val codeName = getErrorCodeName(error.errorCode)
            Toaster.e(R.string.error_playback_failed_with_code, formatArgs = arrayOf(errorDetail.take(80), codeName))
            return
        }

        // Clean up recovery counter for the track we're about to skip
        currentMediaId?.let { recoveryAttempts.remove(it) }

        val prev = player.currentMediaItem ?: return
        //player.seekToNextMediaItem()
        player.playNext()

        showSmartMessage(
            message = getString(
                R.string.skip_media_on_error_message,
                prev.mediaMetadata.title
            )
        )

    }

//    override fun onPlaybackStateChanged(playbackState: Int) {
//        if (playbackState == STATE_IDLE) {
//            player.shuffleModeEnabled = false
//            //player.clearMediaItems()
//        }
//    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
            val isBufferingOrReady = player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            
            if (player.playbackState == Player.STATE_READY && player.playWhenReady && events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                Timber.tag("PlayerServiceModern").i("PLAYBACK OK - Streaming successfully [${player.currentMediaItem?.mediaMetadata?.title}]")
            }

            if (isBufferingOrReady && player.playWhenReady) {
                sendOpenEqualizerIntent()
            } else {
                sendCloseEqualizerIntent()
                if (!player.playWhenReady) {
                    waitingForNetwork.value = false
                }
            }
        }

//        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
//            currentMediaItem.value = player.currentMediaItem
//        }
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
        if (!preferences.getBoolean(bassboostEnabledKey, false)) {
            runCatching {
                bassBoost?.enabled = false
                bassBoost?.release()
            }
            bassBoost = null
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
        }.onFailure {
            Toaster.e( R.string.cant_enable_bass_boost )
        }
    }

    private fun maybeReverb() {
        val presetType = preferences.getEnum(audioReverbPresetKey, PresetsReverb.NONE)

        if (presetType == PresetsReverb.NONE) {
            runCatching {
                reverbPreset?.enabled = false
                player.clearAuxEffectInfo()
                reverbPreset?.release()
            }
                reverbPreset = null
            return
        }

        runCatching {
            if (reverbPreset == null) reverbPreset = PresetReverb(1, player.audioSessionId)

            reverbPreset?.enabled = false
            reverbPreset?.preset = presetType.preset
            reverbPreset?.enabled = true
            reverbPreset?.id?.let { player.setAuxEffectInfo(AuxEffectInfo(it, 1f)) }
        }
    }

    @UnstableApi
    private fun maybeNormalizeVolume() {
        if (!preferences.getBoolean(volumeNormalizationKey, false)) {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            volumeNormalizationJob?.cancel()
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

        val baseGain = preferences.getFloat(loudnessBaseGainKey, 5.00f)
        val volumeBoostLevel = preferences.getFloat(volumeBoostLevelKey, 0f)
        player.currentMediaItem?.mediaId?.let { songId ->
            volumeNormalizationJob?.cancel()
            volumeNormalizationJob = coroutineScope.launch(Dispatchers.Main) {
                fun Float?.toMb() = ((this ?: 0f) * 100).toInt()

                Database.formatTable
                        .findBySongId( songId )
                        .cancellable()
                        .collectLatest { format ->
                            val loudnessMb = format?.loudnessDb.toMb().let {
                                if (it !in -2000..2000) {
                                    Toaster.w( R.string.extreme_loudness_detected )

                                    0
                                } else
                                    it
                            }

                            try {
                                loudnessEnhancer?.setTargetGain(baseGain.toMb() + volumeBoostLevel.toMb() - loudnessMb)
                                loudnessEnhancer?.enabled = true
                            } catch (e: Exception) {
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
            if (audioManager == null) {
                audioManager = getSystemService(AUDIO_SERVICE) as AudioManager?
            }

            audioDeviceCallback = object : AudioDeviceCallback() {
                private fun canPlayMusic(audioDeviceInfo: AudioDeviceInfo): Boolean {
                    if (!audioDeviceInfo.isSink) return false

                    return audioDeviceInfo.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            audioDeviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            audioDeviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            audioDeviceInfo.type == AudioDeviceInfo.TYPE_USB_HEADSET
                }

                override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                    if (!player.isPlaying && addedDevices.any(::canPlayMusic)) {
                        player.play()
                    }
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) = Unit
            }

            audioManager?.registerAudioDeviceCallback(audioDeviceCallback, handler)

        } else {
            audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
            audioDeviceCallback = null
        }
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

            return DefaultAudioSink.Builder(applicationContext)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioOffloadSupportProvider(
                    DefaultAudioOffloadSupportProvider(applicationContext)
                )
                .setAudioProcessorChain(
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
        object : DefaultLoadErrorHandlingPolicy() {
            // No MediaSource-level fallback exists - returning true here causes here causes
            // ExoPlayer to attempt a nonexistent fallback, then skip the track.
            override fun isEligibleForFallback(exception: IOException) = false

            override fun getRetryDelayMsFor(
                loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo
            ): Long {
                // Invalidate cached stream URL on every retry so we fetch fresh URLs
                val mediaId = runCatching<String?> {
                    loadErrorInfo.loadEventInfo.dataSpec.key
                        ?: loadErrorInfo.loadEventInfo.dataSpec.uri.toString().substringAfter("watch?v=", "").takeIf { it.isNotEmpty() }
                }.getOrNull()
                if (mediaId != null) {
                    streamUrlCache.invalidate(mediaId)
                }

                val skipOnError = preferences.getBoolean(skipMediaOnErrorKey, false)
                val count = loadErrorInfo.errorCount

                if (loadErrorInfo.exception.isFatalCustomException()) {
                    return C.TIME_UNSET
                }

                return if (count <= 7) {
                    // Normal exponential backoff up to 7 retries
                    (count * 2000L).coerceAtMost(10_000L)
                } else if (!skipOnError) {
                    // User wants to NEVER skip: keep retrying with a long delay.
                    // Show a toast so the user always knows something is happening.
                    Toaster.w(R.string.stream_still_retrying, formatArgs = arrayOf(count.toString()))
                    // Returning a positive value ensures ExoPlayer never gives up
                    // on this media item (C.TIME_UNSET would cause a skip).
                    15_000L
                } else {
                    C.TIME_UNSET // skipOnError is ON - let ExoPlayer give up and trigger onPlayerError give up and trigger onPlayerError
                }
            }
        }
    )


    private fun buildCustomCommandButtons(): MutableList<CommandButton> {
        val notificationPlayerFirstIcon = preferences.getEnum(notificationPlayerFirstIconKey, NotificationButtons.Download)
        val notificationPlayerSecondIcon = preferences.getEnum(notificationPlayerSecondIconKey, NotificationButtons.Favorites)

        val commandButtonsList = mutableListOf<CommandButton>()
        val firstCommandButton = NotificationButtons.entries.let { buttons ->
            buttons
                .filter { it == notificationPlayerFirstIcon }
                .map {
                    val displayName = appContext().resources.getString( it.textId )

                    CommandButton.Builder()
                        .setDisplayName( displayName )
                        .setIconResId(
                            it.getStateIcon(
                                it,
                                currentSong.value?.likedAt,
                                currentSongStateDownload.value,
                                player.repeatMode,
                                player.shuffleModeEnabled
                            )
                        )
                        .setSessionCommand(it.sessionCommand)
                        .build()
                }
        }

        val secondCommandButton =  NotificationButtons.entries.let { buttons ->
            buttons
                .filter { it == notificationPlayerSecondIcon }
                .map {
                    val displayName = appContext().resources.getString( it.textId )

                    CommandButton.Builder()
                        .setDisplayName( displayName )
                        .setIconResId(
                            it.getStateIcon(
                                it,
                                currentSong.value?.likedAt,
                                currentSongStateDownload.value,
                                player.repeatMode,
                                player.shuffleModeEnabled
                            )
                        )
                        .setSessionCommand(it.sessionCommand)
                        .build()
                }
        }

        val otherCommandButtons = NotificationButtons.entries.let { buttons ->
            buttons
                .filterNot { it == notificationPlayerFirstIcon || it == notificationPlayerSecondIcon }
                .map {
                    val displayName = appContext().resources.getString( it.textId )

                    CommandButton.Builder()
                        .setDisplayName( displayName )
                        .setIconResId(
                            it.getStateIcon(
                                it,
                                currentSong.value?.likedAt,
                                currentSongStateDownload.value,
                                player.repeatMode,
                                player.shuffleModeEnabled
                            )
                        )
                        .setSessionCommand(it.sessionCommand)
                        .build()
                }
        }

        commandButtonsList += firstCommandButton + secondCommandButton + otherCommandButtons

        return commandButtonsList
    }

    private fun updateCustomNotification(session: MediaSession): MediaNotification {

        val playIntent = Action.play.pendingIntent
        val pauseIntent = Action.pause.pendingIntent
        val nextIntent = Action.next.pendingIntent
        val prevIntent = Action.previous.pendingIntent

        val mediaMetadata = player.mediaMetadata

        val mediaItem = binder?.player?.currentMediaItem
        val artistTextRaw = mediaItem?.artistTextOrDb()
        val artistText = if (artistTextRaw.isNullOrBlank() || artistTextRaw == "null") getString(R.string.unknown_artist) else artistTextRaw
        val albumText = mediaItem?.albumTitleOrDb()?.takeIf { it != "null" } ?: ""

        // Load bitmap with proper fallback handling
        bitmapProvider.load(mediaMetadata.artworkUri) {
            // Callback is called with the final bitmap (including fallback)
        }

        val customNotify = if (isAtLeastAndroid8) {
            NotificationCompat.Builder(this, NotificationChannelId)
        } else {
            NotificationCompat.Builder(this)
        }
            .setContentTitle(
                cleanPrefix(player.mediaMetadata.title?.toString() ?: "").let { if (it.isBlank() || it == "null") getString(R.string.unknown_title) else it }.let {
                    if (player.currentMediaItem?.mediaMetadata?.extras?.getBoolean("isExplicit") == true ||
                        player.currentMediaItem?.mediaMetadata?.extras?.getBoolean("androidx.media3.session.EXTRAS_KEY_IS_EXPLICIT") == true) {
                        "\uD83C\uDD74 $it"
                    } else it
                }
            )
            .setContentText(
                if (albumText.isNotBlank() && artistText.isNotBlank()) {
                    "$artistText | $albumText"
                } else {
                    artistText
                }
            )
            .setSubText(
                if (albumText.isNotBlank() && artistText.isNotBlank()) {
                    "$artistText | $albumText"
                } else {
                    artistText
                }
            )
            .setLargeIcon(bitmapProvider.bitmap)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSmallIcon(player.playerError?.let { R.drawable.alert_circle }
                ?: R.drawable.ic_launcher_monochrome)
            .setOngoing(false)
            .setContentIntent(activityPendingIntent<MainActivity>(
                flags = PendingIntent.FLAG_UPDATE_CURRENT
            ) {
                putExtra("expandPlayerBottomSheet", true)
            })
            .setDeleteIntent(broadCastPendingIntent<NotificationDismissReceiver>())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session))
            .addAction(R.drawable.play_skip_back, getString(R.string.notification_skip_back), prevIntent)
            .addAction(
                if (player.isPlaying) R.drawable.pause else R.drawable.play,
                if (player.isPlaying) getString(R.string.notification_pause) else getString(R.string.notification_play),
                if (player.isPlaying) pauseIntent else playIntent
            )
            .addAction(R.drawable.play_skip_forward, getString(R.string.notification_skip_forward), nextIntent)

        //***********************
        val notificationPlayerFirstIcon = preferences.getEnum(notificationPlayerFirstIconKey, NotificationButtons.Download)
        val notificationPlayerSecondIcon = preferences.getEnum(notificationPlayerSecondIconKey, NotificationButtons.Favorites)

        NotificationButtons.entries.let { buttons ->
            buttons
                .filter { it == notificationPlayerFirstIcon }
                .map {
                    customNotify.addAction(
                        it.getStateIcon(
                            it,
                            currentSong.value?.likedAt,
                            currentSongStateDownload.value,
                            player.repeatMode,
                            player.shuffleModeEnabled
                        ),
                        appContext().resources.getString( it.textId ),
                        it.pendingIntent
                    )
                }
        }

        NotificationButtons.entries.let { buttons ->
            buttons
                .filter { it == notificationPlayerSecondIcon }
                .map {
                    customNotify.addAction(
                        it.getStateIcon(
                            it,
                            currentSong.value?.likedAt,
                            currentSongStateDownload.value,
                            player.repeatMode,
                            player.shuffleModeEnabled
                        ),
                        appContext().resources.getString( it.textId ),
                        it.pendingIntent
                    )
                }
        }

        NotificationButtons.entries.let { buttons ->
            buttons
                .filterNot { it == notificationPlayerFirstIcon || it == notificationPlayerSecondIcon }
                .map {
                    customNotify.addAction(
                        it.getStateIcon(
                            it,
                            currentSong.value?.likedAt,
                            currentSongStateDownload.value,
                            player.repeatMode,
                            player.shuffleModeEnabled
                        ),
                        appContext().resources.getString( it.textId ),
                        it.pendingIntent
                    )
                }
        }
        //***********************

        updateWallpaper()

        return MediaNotification(NotificationId, customNotify.build())
    }

    private fun updateWallpaper() {
        val wallpaperEnabled = preferences.getBoolean(enableWallpaperKey, false)
        val wallpaperType = preferences.getEnum(wallpaperTypeKey, WallpaperType.Lockscreen)
        if (isAtLeastAndroid7 && wallpaperEnabled) {
            coroutineScope.launch(Dispatchers.IO) {
                val wpManager = WallpaperManager.getInstance(this@PlayerServiceModern)
                wpManager.setBitmap(bitmapProvider.bitmap, null, true,
                    when (wallpaperType) {
                        WallpaperType.Both -> (FLAG_LOCK or FLAG_SYSTEM)
                        WallpaperType.Lockscreen -> FLAG_LOCK
                        WallpaperType.Home -> FLAG_SYSTEM
                    }
                )
            }
        }
    }

    private fun updateDefaultNotification() {
        coroutineScope.launch(Dispatchers.Main) {
            mediaSession.setCustomLayout( buildCustomCommandButtons() )
        }

    }

    fun toggleLike() {
        binder.toggleLike()
    }

    fun toggleDownload() {
        binder.toggleDownload()
    }

    fun toggleRepeat() {
        binder.toggleRepeat()
    }

    fun toggleShuffle() {
        binder.toggleShuffle()
    }

    fun startRadio() {
        player.currentMediaItem?.let { binder.startRadio(it, false, null, true) }
    }

    private fun showSmartMessage( message: String ) = Toaster.i(message)

    private var widgetUpdateJob: Job? = null

    private fun startWidgetUpdates() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = coroutineScope.launch(Dispatchers.Main) {
            while (isActive) {
                if (player.isPlaying) {
                    updateWidgets()
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun stopWidgetUpdates() {
        widgetUpdateJob?.cancel()
    }

    @MainThread
    fun updateWidgets() {
        val currentMediaId = binder.player.currentMediaItem?.mediaId
        if (currentMediaId == null) {
            coroutineScope.launch(Dispatchers.IO) {
                NZikWidgetManager.updateIdleWidgets(applicationContext)
            }
            return
        }

        val status = Triple(
            binder.player.mediaMetadata.title?.toString() ?: "",
            binder.player.mediaMetadata.artist?.toString() ?: "",
            binder.player.isPlaying
        )

        val actions = Triple(
            if( status.third ) binder::gracefulPause else binder::gracefulPlay,
            binder.player::seekToPrevious,
            binder.player::seekToNext
        )

        val playerDuration = binder.player.duration.coerceAtLeast(0)
        val playerPosition = binder.player.currentPosition.coerceAtLeast(0)
        val currentBitmap = bitmapProvider.bitmap

        coroutineScope.launch(Dispatchers.IO) {
            // Save bitmap to file for backward compatibility or other widgets
            val file = File( cacheDir, "widget_thumbnail.png" )
            FileOutputStream(file).use { outStream ->
                currentBitmap.compress( Bitmap.CompressFormat.PNG, 50, outStream )
            }
            
            // New Widget architecture (NZik)
            val songId = currentMediaId?.split("/")?.lastOrNull() ?: currentMediaId
            val currentSong = if (songId != null) Database.songTable.findByIdDirect(songId) else null
            val isLiked = currentSong?.likedAt != null

            NZikWidgetManager.updateWidgets(
                context = applicationContext,
                title = status.first,
                artist = status.second,
                artworkBitmap = currentBitmap,
                isPlaying = status.third,
                isLiked = isLiked,
                duration = playerDuration,
                currentPosition = playerPosition
            )

            // Remove old widget update calls when deleting old widgets.
            // (Widget.Horizontal and Widget.Vertical were deleted)
        }
    }

    @UnstableApi
    private fun sendOpenEqualizerIntent() {
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
        )
    }


    @UnstableApi
    private fun sendCloseEqualizerIntent() {
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            }
        )
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
        
        if (!isInternalCrossfadeSeek && (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT || reason == Player.DISCONTINUITY_REASON_SKIP)) {
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
                    // Capture current values to avoid thread safety issues
                    val currentMediaItem = player.currentMediaItem
                    val isPlaying = player.isPlaying
                    val currentPosition = player.currentPosition
                    val duration = player.duration
                    val now = System.currentTimeMillis()
                    discordPresenceManager?.onPlayingStateChanged(
                        currentMediaItem,
                        isPlaying,
                        currentPosition,
                        duration,
                        now,
                        getCurrentPosition = { currentPosition },
                        isPlayingProvider = { isPlaying }
                    )
                }
            }
        }
        super.onPositionDiscontinuity(oldPosition, newPosition, reason)
    }

    private fun maybeSavePlayerQueue() {

        if (!isPersistentQueueEnabled) return
        Timber.tag("PlayerServiceModern").d("onCreate savePersistentQueue is enabled")

        coroutineScope.launch(Dispatchers.Main) {
            val mediaItems = player.currentTimeline.mediaItems
            val mediaItemIndex = player.currentMediaItemIndex
            val mediaItemPosition = player.currentPosition

            if (mediaItems.isEmpty()) return@launch


            mediaItems.mapIndexed { index, mediaItem ->
                QueuedMediaItem(
                    mediaItem = mediaItem,
                    position = if (index == mediaItemIndex) mediaItemPosition else null
                )
            }.let { queuedMediaItems ->
                if (queuedMediaItems.isEmpty()) return@let

                Database.asyncTransaction {
                    queueTable.deleteAll()
                    queueTable.insert( queuedMediaItems )
                }

                Timber.tag("PlayerServiceModern").d("QueuePersistentEnabled Saved queue")
            }

        }
    }

    private fun maybeResumePlaybackOnStart() {
        if( isPersistentQueueEnabled && preferences.getBoolean(resumePlaybackOnStartKey, false) )
            binder.gracefulPlay()
    }

    @ExperimentalCoroutinesApi
    @FlowPreview
    @UnstableApi
    private fun maybeRestorePlayerQueue() {
        if (!isPersistentQueueEnabled) return

        val parentalControlEnabled = preferences.getBoolean(parentalControlEnabledKey, false)

        Database.asyncQuery {
            val queuedSong = queueTable.allDirect()

            if (queuedSong.isEmpty()) return@asyncQuery

            // Filter explicit content if parental control is enabled
            val filteredQueuedSong = if (parentalControlEnabled) {
                queuedSong.filter { !(it.mediaItem.mediaMetadata.title?.startsWith(EXPLICIT_PREFIX, true) ?: false) }
            } else queuedSong

            if (filteredQueuedSong.isEmpty()) return@asyncQuery

            val index = filteredQueuedSong.indexOfFirst { it.position != null }.coerceAtLeast(0)

            coroutineScope.launch(Dispatchers.Main) {
                player.setMediaItems(
                    filteredQueuedSong.map { mediaItem ->
                        mediaItem.mediaItem.buildUpon()
                            .setUri(mediaItem.mediaItem.mediaId)
                            .setCustomCacheKey(mediaItem.mediaItem.mediaId)
                            .build().apply {
                                mediaMetadata.extras?.putBoolean("isFromPersistentQueue", true)
                            }
                    },
                    index,
                    filteredQueuedSong[index].position ?: C.TIME_UNSET
                )
                player.prepare()
            }
        }

    }

    @ExperimentalCoroutinesApi
    @FlowPreview
    @UnstableApi
    private fun maybeRestoreFromDiskPlayerQueue() {
        //if (!isPersistentQueueEnabled) return
        //Log.d("mediaItem", "QueuePersistentEnabled Restore Initial")

        val parentalControlEnabled = preferences.getBoolean(parentalControlEnabledKey, false)

        runCatching {
            filesDir.resolve("persistentQueue.data").inputStream().use { fis ->
                ObjectInputStream(fis).use { oos ->
                    oos.readObject() as PersistentQueue
                }
            }
        }.onSuccess { queue ->
            //Log.d("mediaItem", "QueuePersistentEnabled Restored queue $queue")
            //Log.d("mediaItem", "QueuePersistentEnabled Restored ${queue.songMediaItems.size}")

            // Filter explicit content if parental control is enabled
            val filteredItems = if (parentalControlEnabled) {
                queue.songMediaItems.filter { !(it.asMediaItem.mediaMetadata.title?.startsWith(EXPLICIT_PREFIX, true) ?: false) }
            } else queue.songMediaItems

            if (filteredItems.isEmpty()) return@onSuccess

            coroutineScope.launch(Dispatchers.Main) {
                player.setMediaItems(
                    filteredItems.map { song ->
                        song.asMediaItem.buildUpon()
                            .setUri(song.asMediaItem.mediaId)
                            .setCustomCacheKey(song.asMediaItem.mediaId)
                            .build().apply {
                                mediaMetadata.extras?.putBoolean("isFromPersistentQueue", true)
                            }
                    },
                    queue.mediaItemIndex.coerceAtMost(filteredItems.size - 1),
                    queue.position
                )

                player.prepare()
            }

        }.onFailure {
            //it.printStackTrace()
            Timber.tag("PlayerServiceModern").e(it.stackTraceToString())
        }

        //Log.d("mediaItem", "QueuePersistentEnabled Restored ${player.currentTimeline.mediaItems.size}")

    }

    private fun maybeSaveToDiskPlayerQueue() {

        //if (!isPersistentQueueEnabled) return
        //Log.d("mediaItem", "QueuePersistentEnabled Save ${player.currentTimeline.mediaItems.size}")

        val persistentQueue = PersistentQueue(
            title = getString(R.string.txt_title),
            songMediaItems = player.currentTimeline.mediaItems.map {
                PersistentSong(
                    id = it.mediaId,
                    title = it.mediaMetadata.title.toString(),
                    durationText = it.mediaMetadata.extras?.getString("durationText").toString(),
                    thumbnailUrl = it.mediaMetadata.artworkUri.toString()
                )
            },
            mediaItemIndex = player.currentMediaItemIndex,
            position = player.currentPosition
        )

        runCatching {
            filesDir.resolve("persistentQueue.data").outputStream().use { fos ->
                ObjectOutputStream(fos).use { oos ->
                    oos.writeObject(persistentQueue)
                }
            }
        }.onFailure {
            //it.printStackTrace()
            Timber.tag("PlayerServiceModern").e(it.stackTraceToString())

        }.onSuccess {
            Timber.tag("PlayerServiceModern").d("QueuePersistentEnabled Saved %s", persistentQueue)
        }

    }

    fun updateDownloadedState() {
        val mediaId = currentSong.value?.id ?: return
        val downloads = MyDownloadHelper.downloads.value
        currentSongStateDownload.value = downloads[mediaId]?.state ?: Download.STATE_STOPPED
        /*
        if (downloads[currentSong.value?.id]?.state == Download.STATE_COMPLETED) {
            currentSongIsDownloaded.value = true
        } else {
            currentSongIsDownloaded.value = false
        }
        */

        updateDefaultNotification()

    }

    /**
     * This method should ONLY be called when the application (sc. activity) is in the foreground!
     */
    fun restartForegroundOrStop() {
        binder.restartForegroundOrStop()
    }

    @UnstableApi
    class CustomMediaNotificationProvider(private val context: Context) : DefaultMediaNotificationProvider(context) {
        override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence? {
            val isExplicit = metadata.extras?.getBoolean("isExplicit") == true ||
                             metadata.extras?.getBoolean("androidx.media3.session.EXTRAS_KEY_IS_EXPLICIT") == true
            val title = cleanPrefix(metadata.title?.toString() ?: "").ifBlank { context.getString(R.string.unknown_title) }.let {
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
            return context.getString(R.string.unknown_artist)
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

    inner class NotificationActionReceiver(private val player: Player) : BroadcastReceiver() {


        @ExperimentalCoroutinesApi
        @FlowPreview
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Action.pause.value -> binder.gracefulPause()
                Action.play.value -> binder.gracefulPlay()
                Action.playPause.value -> {
                    if (player.isPlaying) binder.gracefulPause() else binder.gracefulPlay()
                }
                Action.next.value -> player.playNext()
                Action.previous.value -> player.playPrevious()
                Action.like.value -> {
                    binder.toggleLike()
                }

                Action.download.value -> {
                    binder.toggleDownload()
                }

                Action.playradio.value -> startRadio()

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
        val service: PlayerServiceModern
            get() = this@PlayerServiceModern

        /*
        fun setBitmapListener(listener: ((Bitmap?) -> Unit)?) {
            bitmapProvider.listener = listener
        }

        */
        val bitmap: Bitmap
            get() = bitmapProvider.bitmap


        val player: ExoPlayer
            get() = this@PlayerServiceModern.player
            
        val playerUpdateTrigger: StateFlow<Int>
            get() = this@PlayerServiceModern.playerUpdateTrigger

        val cache: Cache
            get() = this@PlayerServiceModern.cache

        val downloadCache: Cache
            get() = this@PlayerServiceModern.downloadCache

        val sleepTimerMillisLeft: StateFlow<Long?>?
            get() = timerJob?.millisLeft

        fun startSleepTimer(delayMillis: Long) {
            timerJob?.cancel()

            timerJob = coroutineScope.timer(delayMillis) {
                val notification = NotificationCompat
                    .Builder(this@PlayerServiceModern, SleepTimerNotificationChannelId)
                    .setContentTitle(getString(R.string.sleep_timer_ended))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(true)
                    .setSmallIcon(R.drawable.ic_launcher_monochrome)
                    .build()

                notificationManager?.notify(SleepTimerNotificationId, notification)

                coroutineScope.launch {
                    delay(1000)
                    stopSelf()
                    exitProcess(0)
                }
            }
        }

        fun cancelSleepTimer() {
            timerJob?.cancel()
            timerJob = null
        }

        val isLoadingRadio: Boolean
            get() = nzikRadio.isLoading

        val isRadioActive: Boolean
            get() = nzikRadio.isRadioActive

        val radioActionTextRes: Int
            get() = nzikRadio.radioActionTextRes

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

        fun setPreferredAudioDevice(deviceId: Int?) {
            preferredDeviceId = deviceId
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val am = audioManager ?: (getSystemService(AUDIO_SERVICE) as? AudioManager) ?: return
                // Always clear first to force ExoPlayer to re-evaluate
                player.setPreferredAudioDevice(null)
                if (deviceId != null) {
                    val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    val deviceInfo = devices.find { it.id == deviceId }
                    if (deviceInfo != null) {
                        Timber.tag("PlayerServiceModern").d("setPreferredAudioDevice: switching to id=$deviceId type=${deviceInfo.type} name=${deviceInfo.productName}")
                        player.setPreferredAudioDevice(deviceInfo)
                    } else {
                        Timber.tag("PlayerServiceModern").w("setPreferredAudioDevice: deviceId=$deviceId NOT FOUND. Available: ${devices.map { "id=${it.id} type=${it.type} name=${it.productName}" }}")
                    }
                } else {
                    Timber.tag("PlayerServiceModern").d("setPreferredAudioDevice: cleared (null)")
                }
            }
        }

        /**
         * Pause with fade out effect
         */
        @MainThread
        fun gracefulPause() {
            val duration = preferences.getEnum( playbackFadeAudioDurationKey, DurationInMilliseconds.Disabled )
            player.fadeOutEffect( duration.asMillis )
        }

        /**
         * Start playing with fade in effect
         */
        @MainThread
        fun gracefulPlay() {
            val duration = preferences.getEnum( playbackFadeAudioDurationKey, DurationInMilliseconds.Disabled )
            player.fadeInEffect( duration.asMillis )
        }

        /**
         * This method should ONLY be called when the application (sc. activity) is in the foreground!
         */
        fun restartForegroundOrStop() {
            player.pause()
            stopSelf()
        }

        fun toggleLike() {
            val mediaItem = currentMediaItem.value ?: return

            coroutineScope.launch(Dispatchers.IO) {
                YouTubeSync.rotateSongLikeState( this@PlayerServiceModern, mediaItem )
            }
        }

        fun toggleDownload() {
    
            manageDownload(
                context = this@PlayerServiceModern,
                mediaItem = currentMediaItem.value ?: return,
                downloadState = currentSongStateDownload.value == Download.STATE_COMPLETED
            )
        }

        fun toggleRepeat() {
            player.toggleRepeatMode()
            updateDefaultNotification()
        }

        fun toggleShuffle() {
            player.toggleShuffleMode()
            updateDefaultNotification()
        }

        fun actionSearch() {
            startActivity(Intent(applicationContext, MainActivity::class.java)
                .setAction(MainActivity.action_search)
                .setFlags(FLAG_ACTIVITY_NEW_TASK + FLAG_ACTIVITY_CLEAR_TASK))
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

            val pause = Action("app.it.fast4x.rimusic.pause")
            val play = Action("app.it.fast4x.rimusic.play")
            val playPause = Action("app.it.fast4x.rimusic.playPause")
            val next = Action("app.it.fast4x.rimusic.next")
            val previous = Action("app.it.fast4x.rimusic.previous")
            val like = Action("app.it.fast4x.rimusic.like")
            val download = Action("app.it.fast4x.rimusic.download")
            val playradio = Action("app.it.fast4x.rimusic.playradio")
            val shuffle = Action("app.it.fast4x.rimusic.shuffle")
            val search = Action("app.it.fast4x.rimusic.search")
            val repeat = Action("app.it.fast4x.rimusic.repeat")

        }
    }

    // --- Crossfade Logic ---
    private var isCrossfading = false
    private var crossfadeJob: Job? = null
    private var crossfadeTriggerJob: Job? = null
    private var fadingPlayer: ExoPlayer? = null
    private var secondaryPlayer: ExoPlayer? = null
    private var isInternalCrossfadeSeek = false

    private val crossfadeDuration: Int
        get() = preferences.getInt(crossfadeDurationKey, 3000)

    private val crossfadeGapless: Boolean
        get() = preferences.getBoolean(crossfadeGaplessKey, false)

    private val crossfadeEnabled: Boolean
        get() = preferences.getBoolean(crossfadeEnabledKey, false)

    private val secondaryPlayerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            cleanupCrossfade()
        }
    }

    private val crossfadeSyncListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (isCrossfading && secondaryPlayer != null) {
                secondaryPlayer?.playWhenReady = playWhenReady
            }
        }
    }

    private fun scheduleCrossfade() {
        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null
        
        if (!isCrossfading && secondaryPlayer != null) {
            secondaryPlayer?.removeListener(secondaryPlayerListener)
            secondaryPlayer?.release()
            secondaryPlayer = null
        }

        if (!crossfadeEnabled) return
        if (player.duration == C.TIME_UNSET) return
        if (player.duration <= crossfadeDuration) return
        if (crossfadeGapless && isNextItemGapless()) return
        if (!player.hasNextMediaItem() && player.repeatMode != Player.REPEAT_MODE_ONE) return

        val triggerTime = player.duration - crossfadeDuration.toLong()
        val delayMs = triggerTime - player.currentPosition
        if (delayMs <= 0) return

        val preloadAdvanceMs = 15000L
        val delayUntilPreload = maxOf(0L, delayMs - preloadAdvanceMs)

        val targetMediaId = player.currentMediaItem?.mediaId

        val nextIndex = if (player.repeatMode == Player.REPEAT_MODE_ONE) {
            player.currentMediaItemIndex
        } else {
            player.nextMediaItemIndex
        }
        val nextArtworkUri = if (nextIndex != C.INDEX_UNSET) {
            player.getMediaItemAt(nextIndex).mediaMetadata.artworkUri?.toString()
        } else null

        crossfadeTriggerJob =
            coroutineScope.launch(Dispatchers.Main) {
                delay(delayUntilPreload)
                if (isActive && player.isPlaying && player.currentMediaItem?.mediaId == targetMediaId) {
                    preloadCrossfade(triggerTime)
                    
                    if (nextArtworkUri != null) {
                        try {
                            // Preload cover art to avoid UI lag on switch
                            ImageCacheFactory.preloadImage(nextArtworkUri)
                        } catch (e: Exception) {
                            Timber.tag("PlayerServiceModern").e(e, "Crossfade: Failed to preload cover art")
                        }
                    }
                    
                    val remainingDelay = triggerTime - player.currentPosition
                    if (remainingDelay > 0) {
                        delay(remainingDelay)
                    }
                    
                    if (isActive && player.isPlaying && player.currentMediaItem?.mediaId == targetMediaId) {
                        startCrossfade()
                    }
                }
            }
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
        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(createMediaSourceFactory())
            .setRenderersFactory(createRendersFactory())
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false // NEVER handle audio focus for secondary player
            )
            .setUsePlatformDiagnostics(false)
            .build()
    }

    private fun createForwardingPlayer(targetPlayer: Player): ForwardingPlayer {
        return object : ForwardingPlayer(targetPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands()
                    .buildUpon()
                    .addAllCommands()
                    .build()
            }
        }
    }

    private fun preloadCrossfade(triggerTime: Long) {
        if (isCrossfading || secondaryPlayer != null) return

        val currentIndex = player.currentMediaItemIndex
        if (currentIndex == C.INDEX_UNSET) return

        secondaryPlayer = createCrossfadeExoPlayer()
        val secPlayer = secondaryPlayer ?: return
        secPlayer.addListener(secondaryPlayerListener)
        secPlayer.addAnalyticsListener(PlaybackStatsListener(false, this@PlayerServiceModern))

        val itemCount = player.mediaItemCount
        val items = mutableListOf<MediaItem>()
        for (i in 0 until itemCount) {
            items.add(player.getMediaItemAt(i))
        }

        val nextIndex = if (player.repeatMode == Player.REPEAT_MODE_ONE) {
            currentIndex
        } else {
            player.nextMediaItemIndex
        }
        if (nextIndex == C.INDEX_UNSET) return

        secPlayer.setMediaItems(items, nextIndex, 0L)
        secPlayer.volume = 0f
        secPlayer.repeatMode = player.repeatMode
        secPlayer.shuffleModeEnabled = player.shuffleModeEnabled

        secPlayer.prepare()
        secPlayer.playWhenReady = false
    }

    private fun startCrossfade() {
        if (isCrossfading) return

        val nextPlayer = secondaryPlayer ?: return
        isCrossfading = true
        
        // Guard the entire swap so listener callbacks don't kill the crossfade
        isInternalCrossfadeSeek = true

        val startVolume = player.volume
        
        // Setup the swap
        val currentPlayer = player
        fadingPlayer = currentPlayer
        player = nextPlayer
        playerUpdateTrigger.value++
        secondaryPlayer = null
        
        // Unregister listeners from the old player
        fadingPlayer?.removeListener(this)
        fadingPlayer?.removeListener(sleepTimer)

        // Sync play/pause state between new and fading player
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isCrossfading && fadingPlayer != null) {
                        if (isPlaying) {
                            fadingPlayer?.play()
                        } else {
                            fadingPlayer?.pause()
                        }
                    } else {
                        player.removeListener(this)
                    }
                }
            }
        )

        // Register listeners to the new primary player
        nextPlayer.removeListener(secondaryPlayerListener)
        nextPlayer.addListener(this)
        nextPlayer.addListener(sleepTimer)

        sleepTimer.player = player

        // Update MediaSession to show the new song in the UI
        try {
            mediaSession.player = createForwardingPlayer(player)
        } catch (e: Exception) {
            Timber.tag("PlayerServiceModern").e(e, "Failed to swap player in MediaSession")
        }

        nextPlayer.volume = 0f
        nextPlayer.playWhenReady = fadingPlayer?.playWhenReady ?: false
        
        // Swap complete, allow listener callbacks again
        isInternalCrossfadeSeek = false

        crossfadeJob = coroutineScope.launch(Dispatchers.Main) {
            try {
                val steps = 50
                val durationMs = crossfadeDuration.toLong()
                val stepTime = durationMs / steps

                for (i in 1..steps) {
                    if (!coroutineScope.isActive) break
                    while (!player.playWhenReady && coroutineScope.isActive) {
                        delay(100)
                    }

                    val progress = i / steps.toFloat()
                    val fadeIn = kotlin.math.sqrt(progress)
                    val fadeOut = kotlin.math.sqrt(1.0f - progress)

                    try {
                        player.volume = startVolume * fadeIn // Next song fades in
                        fadingPlayer?.volume = startVolume * fadeOut // Current song fades out
                    } catch (e: Exception) {
                        break
                    }
                    delay(stepTime)
                }

                // Crossfade finished!
                player.volume = startVolume
                fadingPlayer?.volume = 0f
                
            } catch (e: Exception) {
                Timber.tag("PlayerServiceModern").e(e, "Error during crossfade")
            } finally {
                cleanupCrossfade()
            }
        }
    }

    private fun cancelCrossfadeAndReset() {
        if (isCrossfading || crossfadeTriggerJob != null) {
            crossfadeJob?.cancel()
            crossfadeJob = null
            crossfadeTriggerJob?.cancel()
            crossfadeTriggerJob = null
            player.volume = preferences.getFloat(playbackVolumeKey, 1f)
            cleanupCrossfade()
        }
    }

    private fun cleanupCrossfade() {
        fadingPlayer?.stop()
        fadingPlayer?.release()
        fadingPlayer = null
        
        secondaryPlayer?.stop()
        secondaryPlayer?.release()
        secondaryPlayer = null
        
        isCrossfading = false
    }
    // --- End Crossfade Logic ---

    companion object {
        var isRunning = false
        const val NotificationId = 1001
        const val NotificationChannelId = "default_channel_id"

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

        const val CACHE_DIRNAME = "exoplayer"

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

fun Throwable?.isFatalCustomException(): Boolean {
    return generateSequence<Throwable>(this) { it.cause }
        .any {
            it is ExplicitContentException ||
            it is LoginRequiredException ||
            it is PlayableFormatNonSupported ||
            it is UnplayableException ||
            it is VideoIdMismatchException ||
            it is UnmatchedSongException
        }
}

fun getErrorCodeName(errorCode: Int): String = when (errorCode) {
    PlaybackException.ERROR_CODE_UNSPECIFIED -> "UNSPECIFIED"
    PlaybackException.ERROR_CODE_REMOTE_ERROR -> "REMOTE_ERROR"
    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "BEHIND_LIVE_WINDOW"
    PlaybackException.ERROR_CODE_TIMEOUT -> "TIMEOUT"
    PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK -> "FAILED_RUNTIME_CHECK"
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "IO_UNSPECIFIED"
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "IO_NETWORK_CONNECTION_FAILED"
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "IO_NETWORK_CONNECTION_TIMEOUT"
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "IO_INVALID_HTTP_CONTENT_TYPE"
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "IO_BAD_HTTP_STATUS"
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "IO_FILE_NOT_FOUND"
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "IO_NO_PERMISSION"
    PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "IO_CLEARTEXT_NOT_PERMITTED"
    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> "IO_READ_POSITION_OUT_OF_RANGE"
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "PARSING_CONTAINER_MALFORMED"
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "PARSING_MANIFEST_MALFORMED"
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "PARSING_CONTAINER_UNSUPPORTED"
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "PARSING_MANIFEST_UNSUPPORTED"
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "DECODER_INIT_FAILED"
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> "DECODER_QUERY_FAILED"
    PlaybackException.ERROR_CODE_DECODING_FAILED -> "DECODING_FAILED"
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "DECODING_FORMAT_EXCEEDS_CAPABILITIES"
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "DECODING_FORMAT_UNSUPPORTED"
    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> "AUDIO_TRACK_INIT_FAILED"
    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> "AUDIO_TRACK_WRITE_FAILED"
    PlaybackException.ERROR_CODE_DRM_UNSPECIFIED -> "DRM_UNSPECIFIED"
    PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED -> "DRM_SCHEME_UNSUPPORTED"
    PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED -> "DRM_PROVISIONING_FAILED"
    PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR -> "DRM_CONTENT_ERROR"
    PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> "DRM_LICENSE_ACQUISITION_FAILED"
    PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION -> "DRM_DISALLOWED_OPERATION"
    PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR -> "DRM_SYSTEM_ERROR"
    PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED -> "DRM_DEVICE_REVOKED"
    PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED -> "DRM_LICENSE_EXPIRED"
    else -> "UNKNOWN_ERROR_$errorCode"
}
