package app.n_zik.android.components.player.lyrics

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.enums.*
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.enums.lyrics.LyricsBackground
import app.n_zik.android.enums.lyrics.LyricsColor
import app.n_zik.android.enums.lyrics.LyricsOutline
import app.n_zik.android.enums.lyrics.LyricsFontSize
import app.n_zik.android.enums.lyrics.LyricsHighlight
import app.n_zik.android.enums.lyrics.LyricsAlignment
import app.n_zik.android.models.Lyrics
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.it.fast4x.rimusic.utils.lyricsTypeKey
import app.it.fast4x.rimusic.ui.components.themed.LyricsSizeDialog
import app.it.fast4x.rimusic.ui.components.themed.TextPlaceholder
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import app.it.fast4x.rimusic.ui.styling.onOverlayShimmer
import app.it.fast4x.rimusic.utils.*
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.song.EditLyricsDialog
import app.n_zik.android.core.database.Database
import app.n_zik.android.thumbnailShape
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.typography
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.Float.Companion.POSITIVE_INFINITY
import app.n_zik.android.components.menu.lyrics.LyricsSettingsMenu
import app.n_zik.android.components.dialog.player.ShowOffsetDialog
import dev.rebelonion.translator.Translator
import androidx.compose.animation.fadeOut
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.text.BasicText
import androidx.compose.animation.AnimatedVisibility
import app.it.fast4x.rimusic.utils.getBitmapFromUrl
import androidx.compose.animation.scaleIn
import kotlinx.coroutines.Dispatchers
import dev.rebelonion.translator.Language
import java.util.Locale
import android.graphics.Bitmap


@UnstableApi
@Composable
fun LyricsScreen(
    mediaId: String,
    isDisplayed: Boolean,
    onDismiss: () -> Unit,
    size: Dp,
    mediaMetadataProvider: () -> MediaMetadata,
    durationProvider: () -> Long,
    ensureSongInserted: () -> Unit,
    modifier: Modifier = Modifier,
    clickLyricsText: Boolean,
    trailingContent: (@Composable () -> Unit)? = null,
    isLandscape: Boolean,
) {
    AnimatedVisibility(
        visible = isDisplayed,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current
        val menuState = LocalMenuState.current
        val currentView = LocalView.current
        val binder = LocalPlayerServiceBinder.current

        var showlyricsthumbnail by rememberPreference(showlyricsthumbnailKey, true)
        var lyricsType by rememberPreference(lyricsTypeKey, LyricsType.Auto)
        var invalidLrc by remember(mediaId, lyricsType) { mutableStateOf(false) }
        var isPicking by remember(mediaId, lyricsType) { mutableStateOf(false) }
        var lyricsColor by rememberPreference(lyricsColorKey, LyricsColor.White)
        var lyricsOutline by rememberPreference(lyricsOutlineKey, LyricsOutline.None)
        val playerBackgroundColors by rememberPreference(playerBackgroundColorsKey, PlayerBackgroundColors.AnimatedGradient)
        var lyricsFontSize by rememberPreference(lyricsFontSizeKey, LyricsFontSize.Large)

        val thumbnailSize = Dimensions.thumbnails.player.song
        val colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)

        var checkLyrics by remember { mutableStateOf(false) }
        var isFetching by remember(mediaId, checkLyrics, lyricsType) { mutableStateOf(true) }
        var lyrics by remember { mutableStateOf<Lyrics?>(null) }

        LaunchedEffect(mediaId) {
            lyrics = null
        }

        val actualLyricsType = remember(lyrics, lyricsType) {
            lyrics?.type?.let { runCatching { LyricsType.valueOf(it) }.getOrNull() }
        }

        val editLyricsDialog = EditLyricsDialog(
            mediaId = mediaId,
            lyricsType = actualLyricsType ?: (if (lyricsType != LyricsType.Auto) lyricsType else LyricsType.Karaoke),
            getLyrics = { lyrics },
            ensureSongInserted = { ensureSongInserted() }
        )


        val text = lyrics?.data

        var isError by remember(mediaId, lyricsType) { mutableStateOf(false) }
        var isErrorSync by remember(mediaId, lyricsType) { mutableStateOf(false) }

        val translateEnabledState = remember { mutableStateOf(false) }
        var translateEnabled by translateEnabledState

        var romanizationEnabled by rememberPreference(romanizationEnabledKey, true)
        var showIntervalIndicator by rememberPreference(lyricsIntervalIndicatorKey, true)
        var showSecondLine by rememberPreference(showSecondLineKey, false)
        var otherLanguageApp by rememberPreference(otherLanguageAppLyricsKey, Languages.System)
        var lyricsBackground by rememberPreference(lyricsBackgroundKey, LyricsBackground.Black)
        val languageDestination = remember(otherLanguageApp) {
            if (otherLanguageApp != Languages.System) {
                otherLanguageApp.translatorLanguage
            } else {
                val systemCode = Locale.getDefault().language
                Languages.entries.find { it.code == systemCode }?.translatorLanguage ?: Language.ENGLISH
            }
        }

        var copyToClipboard by remember { mutableStateOf(false) }
        if (copyToClipboard) text?.let { textCopyToClipboard(it, context) }

        var fontSize by rememberPreference(lyricsFontSizeKey, LyricsFontSize.Large)
        val showBackgroundLyrics by rememberPreference(showBackgroundLyricsKey, false)
        val playerEnableLyricsPopupMessage by rememberPreference(playerEnableLyricsPopupMessageKey, true)
        var expandedplayer by rememberPreference(expandedplayerKey, false)

        var checkedLyricsLrc by remember { mutableStateOf(false) }
        var checkedLyricsKugou by remember { mutableStateOf(false) }
        var checkedLyricsInnertube by remember { mutableStateOf(false) }


        var lyricsHighlight by rememberPreference(lyricsHighlightKey, LyricsHighlight.None)
        var lyricsAlignment by rememberPreference(lyricsAlignmentKey, LyricsAlignment.Center)
        var karaokeRespectAgentPosition by rememberPreference(karaokeRespectAgentPositionKey, true)
        var lyricsSizeAnimate by rememberPreference(lyricsSizeAnimateKey, false)
        val mediaMetadata = mediaMetadataProvider()
        var editedArtistName by rememberSaveable { mutableStateOf<String?>(null) }
        var editedTitle by rememberSaveable { mutableStateOf<String?>(null) }
        var artistName = editedArtistName ?: cleanPrefix(mediaMetadata.artist?.toString().orEmpty())
        var title = editedTitle ?: cleanPrefix(mediaMetadata.title?.toString().orEmpty())
        var lyricsSize by rememberPreference(lyricsSizeKey, 20f)
        var lyricsSizeL by rememberPreference(lyricsSizeLKey, 20f)
        var customSize = if (isLandscape) lyricsSizeL else lyricsSize
        var showLyricsSizeDialog by rememberSaveable { mutableStateOf(false) }
        var isAutoScrollEnabled by remember { mutableStateOf(true) }

        // Reset auto-scroll whenever the mode changes so lyrics re-center automatically
        LaunchedEffect(lyricsType) {
            isAutoScrollEnabled = true
        }
        val lyricsOffsetState = rememberPreference("lyricsOffset_$mediaId", 0L)
        val lightTheme = colorPaletteMode == ColorPaletteMode.Light || (colorPaletteMode == ColorPaletteMode.System && (!isSystemInDarkTheme()))
        val translator = remember { Translator() }
        val effectRotationEnabled by rememberPreference(effectRotationKey, false)
        var landscapeControls by rememberPreference(landscapeControlsKey, true)
        var jumpPrevious by rememberPreference(jumpPreviousKey,"3")
        var isRotated by rememberSaveable { mutableStateOf(false) }
        val rotationAngle by animateFloatAsState(targetValue = if (isRotated) 360F else 0f, animationSpec = tween(durationMillis = 200), label = "")
        val colorPaletteName by rememberPreference(colorPaletteNameKey, ColorPaletteName.Dynamic)

        if (showLyricsSizeDialog) {
            LyricsSizeDialog(
                onDismiss = { showLyricsSizeDialog = false},
                sizeValue = { lyricsSize = it },
                sizeValueL = { lyricsSizeL = it}
            )
        }

        val showOffsetDialog = ShowOffsetDialog(mediaId = mediaId)
        showOffsetDialog.Render()

        LaunchedEffect(mediaId, mediaMetadata.title, mediaMetadata.artist) {
            editedArtistName = null
            editedTitle = null
            lyrics = null
            checkedLyricsLrc = false
            checkedLyricsKugou = false
            checkedLyricsInnertube = false
        }

        var lyricsCustomColor by rememberPreference(lyricsCustomColorKey, android.graphics.Color.WHITE)
        var bitmapCover by remember { mutableStateOf<Bitmap?>(null) }
        var dominantColor by remember { mutableStateOf(android.graphics.Color.DKGRAY) }

        LaunchedEffect(mediaMetadata.artworkUri) {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                bitmapCover = try {
                    getBitmapFromUrl(context, mediaMetadata.artworkUri.toString())
                } catch (_: Exception) {
                    // Custom cover (content:// URI) or network failure – fall back to no bitmap
                    null
                }
            }
        }
        LaunchedEffect(bitmapCover, lightTheme) {
            val palette = try {
                bitmapCover?.let { dynamicColorPaletteOf(it, !lightTheme) }
            } catch (_: Exception) {
                null
            }
            dominantColor = palette?.accent?.toArgb() ?: android.graphics.Color.DKGRAY
        }

        LyricsFetcher(
            mediaId = mediaId,
            lyricsType = lyricsType,
            checkLyrics = checkLyrics,
            artistName = artistName,
            title = title,
            mediaMetadata = mediaMetadata,
            durationProvider = durationProvider,
            coroutineScope = coroutineScope,
            playerEnableLyricsPopupMessage = playerEnableLyricsPopupMessage,
            onLyricsUpdated = { lyrics = it },
            onErrorUpdated = { isError = it },
            onCheckedLrcUpdated = { checkedLyricsLrc = checkedLyricsLrc || it },
            onCheckedKugouUpdated = { checkedLyricsKugou = checkedLyricsKugou || it },
            onCheckedInnertubeUpdated = { checkedLyricsInnertube = checkedLyricsInnertube || it },
            onFetchingStateChanged = { isFetching = it }
        )

        editLyricsDialog.Render()

        if (isPicking) {
            LyricsTrackSelector(
                mediaId = mediaId,
                lyrics = lyrics,
                initialTitle = title,
                initialArtistName = artistName,
                onTitleChange = { editedTitle = it },
                onArtistNameChange = { editedArtistName = it },
                playerEnableLyricsPopupMessage = playerEnableLyricsPopupMessage,
                coroutineScope = coroutineScope,
                onSearchRetry = {
                    isPicking = false
                    menuState.hide()
                    isPicking = true
                },
                onDismiss = { isPicking = false }
            )
        }

        if (lyricsType != LyricsType.Unsynced) {
            DisposableEffect(Unit) {
                currentView.keepScreenOn = true
                onDispose {
                    currentView.keepScreenOn = false
                }
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
                .fillMaxSize()
                .background(if (!showlyricsthumbnail) Color.Transparent else Color.Black.copy(0.8f))
                .clip(thumbnailShape())
        ) {
            val allSyncedSourcesChecked = checkedLyricsLrc && checkedLyricsKugou && checkedLyricsInnertube
            val noLyricsFound = text == null && allSyncedSourcesChecked

            AnimatedVisibility(
                visible = ((isError && text == null) || (invalidLrc && lyricsType != LyricsType.Unsynced)) && !noLyricsFound && !isFetching,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                BasicText(
                    text = stringResource(R.string.an_error_has_occurred_while_fetching_the_lyrics),
                    style = typography().xs.center.medium.color(PureBlackColorPalette.text),
                    modifier = Modifier
                        .background(if (!showlyricsthumbnail) Color.Transparent else Color.Black.copy(0.4f))
                        .padding(all = 8.dp)
                        .fillMaxWidth()
                )
            }

            if (text?.isEmpty() == true && !checkedLyricsLrc && !checkedLyricsKugou && !checkedLyricsInnertube) {
                checkLyrics = !checkLyrics
            }

            if (text?.isNotEmpty() == true) {
                val hasWordTimings = text.lines().any { it.trim().startsWith("<") && it.contains(":") && it.contains(">") }
                when {
                    actualLyricsType == LyricsType.Karaoke && hasWordTimings -> {
                        KaraokeLyricsView(
                            text = text,
                            currentPositionProvider = { (binder?.player?.currentPosition ?: 0L) + lyricsOffsetState.value },
                            isPlayingProvider = { binder?.player?.isPlaying == true },
                            onSeekTo = { binder?.player?.seekTo(it) },
                            showlyricsthumbnail = showlyricsthumbnail,
                            isLandscape = isLandscape,
                            trailingContent = trailingContent,
                            showBackgroundLyrics = showBackgroundLyrics,
                            lyricsBackground = lyricsBackground,
                            showSecondLine = showSecondLine,
                            translateEnabled = translateEnabled,
                            romanizationEnabled = romanizationEnabled,
                            languageDestination = languageDestination,
                            translator = translator,
                            lyricsOutline = lyricsOutline,
                            colorPaletteMode = colorPaletteMode,
                            fontSize = lyricsFontSize,
                            customSize = customSize,
                            lyricsSizeAnimate = lyricsSizeAnimate,
                            lyricsColor = lyricsColor,
                            lyricsCustomColor = lyricsCustomColor,
                            isAutoScrollEnabled = isAutoScrollEnabled,
                            onAutoScrollEnabledChange = { isAutoScrollEnabled = it },
                            dominantColor = dominantColor,
                            lyricsHighlight = lyricsHighlight,
                            lyricsAlignment = lyricsAlignment,
                            clickLyricsText = clickLyricsText,
                            karaokeRespectAgentPosition = karaokeRespectAgentPosition,
                            thumbnailSize = thumbnailSize,
                            isDisplayed = isDisplayed,
                            onDismiss = onDismiss,
                            onInvalidLrc = { invalidLrc = it },
                            showIntervalIndicator = showIntervalIndicator
                        )
                    }

                    actualLyricsType == LyricsType.Synced || (actualLyricsType == LyricsType.Karaoke && !hasWordTimings) -> {
                        SyncedLyricsView(
                            text = text,
                            currentPositionProvider = { (binder?.player?.currentPosition ?: 0L) + lyricsOffsetState.value },
                            onSeekTo = { binder?.player?.seekTo(it) },
                            showlyricsthumbnail = showlyricsthumbnail,
                            isLandscape = isLandscape,
                            trailingContent = trailingContent,
                            showBackgroundLyrics = showBackgroundLyrics,
                            lyricsBackground = lyricsBackground,
                            showSecondLine = showSecondLine,
                            translateEnabled = translateEnabled,
                            romanizationEnabled = romanizationEnabled,
                            languageDestination = languageDestination,
                            translator = translator,
                            lyricsOutline = lyricsOutline,
                            colorPaletteMode = colorPaletteMode,
                            fontSize = lyricsFontSize,
                            customSize = customSize,
                            lyricsAlignment = lyricsAlignment,
                            lyricsSizeAnimate = lyricsSizeAnimate,
                            lyricsColor = lyricsColor,
                            lyricsCustomColor = lyricsCustomColor,
                            isAutoScrollEnabled = isAutoScrollEnabled,
                            onAutoScrollEnabledChange = { isAutoScrollEnabled = it },
                            dominantColor = dominantColor,
                            lyricsHighlight = lyricsHighlight,
                            clickLyricsText = clickLyricsText,
                            thumbnailSize = thumbnailSize,
                            isDisplayed = isDisplayed,
                            onDismiss = onDismiss,
                            onInvalidLrc = { invalidLrc = it },
                            showIntervalIndicator = showIntervalIndicator,
                            karaokeRespectAgentPosition = karaokeRespectAgentPosition
                        )
                    }
                    actualLyricsType == LyricsType.Unsynced -> {
                        UnsyncedLyricsView(
                            text = text,
                            showlyricsthumbnail = showlyricsthumbnail,
                            isDisplayed = isDisplayed,
                            showSecondLine = showSecondLine,
                            translateEnabled = translateEnabled,
                            romanizationEnabled = romanizationEnabled,
                            languageDestination = languageDestination,
                            translator = translator,
                            lyricsBackground = lyricsBackground,
                            lyricsOutline = lyricsOutline,
                            colorPaletteMode = colorPaletteMode,
                            fontSize = lyricsFontSize,
                            customSize = customSize,
                            lyricsAlignment = lyricsAlignment,
                            lyricsColor = lyricsColor,
                            lyricsCustomColor = lyricsCustomColor,
                            dominantColor = dominantColor,
                            lyricsHighlight = lyricsHighlight,
                            thumbnailSize = thumbnailSize,
                            clickLyricsText = clickLyricsText,
                            onDismiss = onDismiss
                        )
                    }
                }
            }

            if (noLyricsFound && !isPicking && !isFetching) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp)
                ) {
                    BasicText(
                        text = stringResource(R.string.lyrics_not_available),
                        style = typography().s.center.medium.color(PureBlackColorPalette.text),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            icon = R.drawable.refresh,
                            color = colorPalette().accent,
                            enabled = true,
                            onClick = {
                                checkedLyricsLrc = false
                                checkedLyricsKugou = false
                                checkedLyricsInnertube = false
                                resetGlobalAttemptForType(lyricsType)
                                checkLyrics = !checkLyrics
                            },
                            modifier = Modifier
                                .padding(all = 8.dp)
                                .size(24.dp)
                        )
                        IconButton(
                            icon = R.drawable.pencil,
                            color = colorPalette().accent,
                            enabled = true,
                            onClick = { editLyricsDialog.onShortClick() },
                            modifier = Modifier
                                .padding(all = 8.dp)
                                .size(24.dp)
                        )
                    }
                }
            }

            if ((text == null && !isError && !noLyricsFound) || isFetching) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.shimmer()
                ) {
                    repeat(4) {
                        TextPlaceholder(
                            color = colorPalette().onOverlayShimmer,
                            modifier = Modifier.alpha(1f - it * 0.1f)
                        )
                    }
                }
            }

            if (trailingContent != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (thumbnailShape() == CircleShape) 80.dp else 0.dp)
                        .fillMaxWidth(0.4f)
                ) {
                    trailingContent()
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = if (thumbnailShape() == CircleShape) 16.dp else 0.dp)
                    .fillMaxWidth(if (thumbnailShape() == CircleShape) 0.5f else if (trailingContent == null) 0.30f else 0.22f)
            ) {
                if (isLandscape && !showlyricsthumbnail)
                    IconButton(
                        icon = R.drawable.chevron_back,
                        color = colorPalette().accent,
                        enabled = true,
                        onClick = onDismiss,
                        modifier = Modifier
                            .padding(all = 8.dp)
                            .align(Alignment.BottomStart)
                            .size(30.dp)
                    )


            }
            if (!showlyricsthumbnail && isDisplayed && isLandscape && landscapeControls) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent,if (lightTheme) Color.White.copy(0.5f) else Color.Black.copy(0.5f)),
                                startY = 0f,
                                endY = POSITIVE_INFINITY
                            ),
                        )
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                ){
                    Image(
                        painter = painterResource(R.drawable.play_skip_back),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette().text),
                        modifier = Modifier
                            .clip(uiRoundnessShape()).clickable(
                                indication = ripple(bounded = false),
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = {
                                    if (jumpPrevious == "") jumpPrevious = "0"
                                    if(binder?.player?.hasPreviousMediaItem() == false || (jumpPrevious != "0" && (binder?.player?.currentPosition ?: 0) > jumpPrevious.toInt() * 1000)
                                    ){
                                        binder?.player?.seekTo(0)
                                    }
                                    else binder?.player?.playPrevious()
                                    if (effectRotationEnabled) isRotated = !isRotated
                                }
                            )
                            .rotate(rotationAngle)
                            .padding(horizontal = 15.dp)
                            .size(30.dp)

                    )
                    Box {
                        Box(modifier = Modifier
                            .align(Alignment.Center)
                            .size(45.dp)
                            .background(colorPalette().accent, uiRoundnessShape())
                        ){}
                        Image(
                            painter = painterResource(if (binder?.player?.isPlaying == true) R.drawable.pause else R.drawable.play),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(if (colorPaletteName == ColorPaletteName.PureBlack) Color.Black else colorPalette().text),
                            modifier = Modifier
                                .clip(uiRoundnessShape()).clickable(
                                    indication = ripple(bounded = false),
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = {
                                        if (binder?.player?.isPlaying == true) {
                                            binder.gracefulPause()
                                        } else {
                                            binder?.player?.play()
                                        }
                                    },
                                )
                                .align(Alignment.Center)
                                .rotate(rotationAngle)
                                .padding(horizontal = 15.dp)
                                .size(36.dp)

                        )
                    }
                    Image(
                        painter = painterResource(R.drawable.play_skip_forward),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette().text),
                        modifier = Modifier
                            .clip(uiRoundnessShape()).clickable(
                                indication = ripple(bounded = false),
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = {
                                    binder?.player?.playNext()
                                    if (effectRotationEnabled) isRotated = !isRotated
                                }
                            )
                            .rotate(rotationAngle)
                            .padding(horizontal = 15.dp)
                            .size(30.dp)

                    )
                }

            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = if (thumbnailShape() == CircleShape) 16.dp else 0.dp)
                    .fillMaxWidth(if (thumbnailShape() == CircleShape) 0.5f else 0.35f)
            ) {

                Row(
                    modifier = Modifier
                        .align(if (thumbnailShape() == CircleShape) Alignment.BottomStart else Alignment.BottomEnd)
                        .padding(start = if (thumbnailShape() == CircleShape) 48.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedVisibility(
                        visible = !isAutoScrollEnabled,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        Image(
                            painter = painterResource(R.drawable.locate),
                            contentDescription = stringResource(R.string.cd_recenter),
                            colorFilter = ColorFilter.tint(DefaultDarkColorPalette.text),
                            modifier = Modifier
                                .padding(all = 4.dp)
                                .clip(uiRoundnessShape()).clickable(
                                    indication = ripple(bounded = false),
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = {
                                        isAutoScrollEnabled = true
                                    }
                                )
                                .padding(all = 8.dp)
                                .size(20.dp)
                        )
                    }

                    Image(
                        painter = painterResource(R.drawable.settings),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(DefaultDarkColorPalette.text),
                        modifier = Modifier
                            .padding(all = 4.dp)
                            .clip(uiRoundnessShape()).clickable(
                            indication = ripple(bounded = false),
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {
                                menuState.display {
                                    LyricsSettingsMenu(
                                        isLandscape = isLandscape,
                                        translateEnabled = translateEnabledState,
                                        isLyricsNotNull = { lyrics != null },
                                        actualLyricsType = {
                                            lyrics?.type?.let { typeString ->
                                                LyricsType.entries.find { it.name == typeString }
                                            }
                                        },
                                        onShowLyricsSizeDialog = { showLyricsSizeDialog = !showLyricsSizeDialog },
                                        onEditLyrics = { editLyricsDialog.onShortClick() },
                                        onCopyLyrics = { copyToClipboard = true },
                                        onSearchLyricsOnline = {
                                            val mediaMetadata = mediaMetadataProvider()
                                            try {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_WEB_SEARCH).apply {
                                                        putExtra(
                                                            SearchManager.QUERY,
                                                            "${cleanPrefix(mediaMetadata.title?.toString() ?: "").let { if (it == "null") "" else it }} ${mediaMetadata.artist?.toString()?.let { if (it == "null") "" else it } ?: ""} lyrics"
                                                        )
                                                    }
                                                )
                                            } catch (e: ActivityNotFoundException) {
                                                Toaster.e( R.string.info_not_find_app_browse_internet )
                                            }
                                        },
                                        onFetchLyricsAgain = {
                                            resetGlobalAttemptForType(lyricsType)
                                            checkedLyricsLrc = false
                                            checkedLyricsKugou = false
                                            checkedLyricsInnertube = false
                                            checkLyrics = !checkLyrics
                                            val targetType = lyrics?.type ?: lyricsType.name
                                            Database.asyncTransaction {
                                                lyricsTable.upsert(
                                                    Lyrics(
                                                        songId = mediaId,
                                                        type = targetType,
                                                        data = null
                                                    )
                                                )
                                            }
                                        },
                                        onPickFromLrcLib = { isPicking = true },
                                        onShowOffsetDialog = { showOffsetDialog.onShortClick() }
                                    ).MenuComponent()
                                }
                            }
                        )
                        .padding(all = 8.dp)
                        .size(20.dp)
                    )
                }
            }
        }
    }
}

