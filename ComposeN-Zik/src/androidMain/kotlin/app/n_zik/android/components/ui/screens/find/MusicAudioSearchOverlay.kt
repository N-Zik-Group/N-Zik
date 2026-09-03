package app.n_zik.android.components.ui.screens.find

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.core.network.utils.isNetworkConnected
import app.n_zik.android.colorPalette
import app.n_zik.android.components.FmWaveformVisualizer
import app.n_zik.android.recognition.AudioRecorder
import app.n_zik.android.recognition.RecognizedTrack
import app.n_zik.android.recognition.ShazamRepository
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.forcePlay
import coil3.compose.AsyncImage
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.SearchBody
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.utils.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

// ─────────────────────────────────────────────────────────────────────────────
// State
// ─────────────────────────────────────────────────────────────────────────────

private sealed interface FindUiState {
    data object Idle : FindUiState
    data object Listening : FindUiState
    data class Success(val track: RecognizedTrack) : FindUiState
    data class Error(val message: String) : FindUiState
}

// ─────────────────────────────────────────────────────────────────────────────
// Root Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MusicAudioSearchOverlay(
    onDismiss: () -> Unit,
    onOpenSearch: (String) -> Unit
) {
    BackHandler { onDismiss() }

    val context       = LocalContext.current
    val scope         = rememberCoroutineScope()
    val recorder      = remember { AudioRecorder(scope) }
    val repository    = remember { ShazamRepository() }

    val duration       by recorder.duration.collectAsStateWithLifecycle(0)
    val recordedBuffer by recorder.buffer.collectAsStateWithLifecycle(ByteArray(0))
    var state          by remember { mutableStateOf<FindUiState>(FindUiState.Idle) }
    var recognitionJob by remember { mutableStateOf<Job?>(null) }
    var lastAttemptSecond    by remember { mutableStateOf(0) }
    var isMatching           by remember { mutableStateOf(false) }
    var isSpeaking           by remember { mutableStateOf(false) }
    var isPlaying            by remember { mutableStateOf(false) }
    val binder               = LocalPlayerServiceBinder.current
    LaunchedEffect(recordedBuffer) {
        if (recordedBuffer.isNotEmpty() && state is FindUiState.Listening) {
            var maxAmplitude = 0
            for (i in recordedBuffer.indices step 2) {
                if (i + 1 < recordedBuffer.size) {
                    val sample = (recordedBuffer[i].toInt() and 0xFF) or (recordedBuffer[i + 1].toInt() shl 8)
                    val absSample = Math.abs(sample.toShort().toInt())
                    if (absSample > maxAmplitude) maxAmplitude = absSample
                }
            }
            isSpeaking = maxAmplitude > 1500
        } else {
            isSpeaking = false
        }
    }

    fun stopListening(resetToIdle: Boolean = true) {
        recognitionJob?.cancel(); recognitionJob = null
        recorder.stop(); isMatching = false; lastAttemptSecond = 0
        if (resetToIdle) state = FindUiState.Idle
    }

    fun playRecognizedTrack(track: RecognizedTrack) {
        if (isPlaying) return
        isPlaying = true
        scope.launch {
            val query = listOf(track.title, track.subtitle)
                .filter { it.isNotBlank() }.joinToString(" ")
            var song: Innertube.SongItem? = null
            try {
                val searchResult = withContext(Dispatchers.IO) {
                    Innertube.searchPage(
                        body = SearchBody(
                            query = query,
                            params = Innertube.SearchFilter.Song.value
                        ),
                        fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
                    )?.getOrNull()
                }
                song = searchResult?.items?.firstOrNull()
            } catch (e: Exception) {
                Timber.tag("MusicAudioSearchOverlay").w(e, "Search failed for: $query")
            }
            if (song != null) {
                Timber.tag("MusicAudioSearchOverlay").d("Playing: ${song.title}")
                binder?.startRadio(song.asMediaItem, false, song.info?.endpoint)
                stopListening(resetToIdle = false)
                onDismiss()
            } else {
                Timber.tag("MusicAudioSearchOverlay").w("No song found for: $query")
                isPlaying = false
                state = FindUiState.Error(context.getString(R.string.find_error_could_not_identify))
            }
        }
    }

    fun startListening() {
        if (state is FindUiState.Listening) return
        if (!context.isNetworkConnected) {
            state = FindUiState.Error(context.getString(R.string.error_no_internet))
            return
        }

        state = FindUiState.Listening; isMatching = false; lastAttemptSecond = 0
        recorder.start()
        recognitionJob?.cancel()
        recognitionJob = scope.launch {
            delay(12_000L)
            if (state is FindUiState.Listening) {
                recorder.stop(); isMatching = false
                state = FindUiState.Error(context.getString(R.string.find_error_could_not_identify))
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening()
        else state = FindUiState.Error(context.getString(R.string.find_error_microphone_permission))
    }

    LaunchedEffect(duration, recordedBuffer, state) {
        if (state !is FindUiState.Listening) return@LaunchedEffect
        if (duration < 3 || recordedBuffer.isEmpty()) return@LaunchedEffect
        if (isMatching || duration == lastAttemptSecond || duration - lastAttemptSecond < 2) return@LaunchedEffect
        isMatching = true; lastAttemptSecond = duration
        scope.launch {
            Timber.tag("MusicAudioSearchOverlay").d("Starting audio analysis at duration=$duration")
            val track = withContext(Dispatchers.IO) { repository.identify(duration, recordedBuffer) }
            if (track != null) {
                Timber.tag("MusicAudioSearchOverlay").d("Track found - ${track.title}")
                recorder.stop(); recognitionJob?.cancel(); recognitionJob = null
                isMatching = false; state = FindUiState.Success(track)
            } else if (state is FindUiState.Listening) {
                Timber.tag("MusicAudioSearchOverlay").d("Aucune correspondance pour la durée $duration")
                isMatching = false
            }
        }
    }

    val handleDismiss = {
        stopListening(resetToIdle = true)
        onDismiss()
    }

    BackHandler { handleDismiss() }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startListening()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    DisposableEffect(Unit) { onDispose { recognitionJob?.cancel(); recorder.stop() } }

    // ── UI ──────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val isListening = state is FindUiState.Listening
        val isError = state is FindUiState.Error
        val isSuccess = state is FindUiState.Success

        FmWaveformVisualizer(
            isSpeaking = isSpeaking,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )

        Spacer(modifier = Modifier.height(40.dp))

        if (isError) {
            BasicText(
                text = (state as FindUiState.Error).message,
                style = TextStyle(
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp
                ),
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { handleDismiss() }
                        .padding(horizontal = 32.dp, vertical = 12.dp)
                ) {
                    BasicText(
                        text = stringResource(R.string.cancel),
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorPalette().accent)
                        .clickable { startListening() }
                        .padding(horizontal = 32.dp, vertical = 12.dp)
                ) {
                    BasicText(
                        text = stringResource(R.string.voice_search_retry),
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    )
                }
            }
        } else if (!isSuccess) {
            BasicText(
                text = if (isListening) stringResource(R.string.find_status_listening_subtitle, duration) else stringResource(R.string.find_status_ready_subtitle),
                style = TextStyle(
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 26.sp
                ),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            if (isListening) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { (duration / 12f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(0.5f),
                    color = colorPalette().accent,
                    trackColor = colorPalette().accent.copy(alpha = 0.2f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            BasicText(
                text = stringResource(R.string.find_music_hint),
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.2f))
                    .clickable { handleDismiss() }
                    .padding(horizontal = 32.dp, vertical = 12.dp)
            ) {
                BasicText(
                    text = stringResource(R.string.cancel),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp
                    )
                )
            }
        }

        // Results
        AnimatedVisibility(
            visible = isSuccess,
            enter   = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 },
            exit    = fadeOut(tween(200))
        ) {
            val success = state as? FindUiState.Success
            if (success != null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RecognizedTrackCard(
                        track    = success.track,
                        isPlaying = isPlaying,
                        onPlay   = { playRecognizedTrack(success.track) },
                        onSearch = {
                            val q = listOf(success.track.title, success.track.subtitle)
                                .filter { it.isNotBlank() }.joinToString(" ")
                            onOpenSearch(q)
                        }
                    )

                    // Close button at the bottom of results
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable { handleDismiss() }
                            .padding(horizontal = 32.dp, vertical = 12.dp)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        BasicText(
                            text = stringResource(R.string.cancel),
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Recognized Track Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecognizedTrackCard(
    track: RecognizedTrack,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colorPalette().background2)
            .border(
                1.dp,
                colorPalette().accent.copy(alpha = 0.18f),
                RoundedCornerShape(18.dp)
            )
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(14.dp))
        ) {
            AsyncImage(
                model = track.coverUrl ?: track.backgroundUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                track.title,
                style = typography().s,
                color = colorPalette().text,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.subtitle,
                style = typography().xs,
                color = colorPalette().textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            track.genre?.takeIf { it.isNotBlank() }?.let { genre ->
                Text(genre, style = typography().xxs, color = colorPalette().accent)
            }
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colorPalette().accent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onPlay
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = colorPalette().onAccent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        androidx.compose.material3.Icon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = stringResource(R.string.play),
                            tint = colorPalette().onAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colorPalette().accent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSearch
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        stringResource(R.string.find_more_details),
                        style = typography().xxs,
                        color = colorPalette().onAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
