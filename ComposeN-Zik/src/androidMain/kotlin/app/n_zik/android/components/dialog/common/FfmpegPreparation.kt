package app.n_zik.android.components.dialog.common

import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import app.n_zik.android.R
import app.n_zik.android.modules.FfmpegModule
import app.n_zik.android.modules.ModuleState
import app.n_zik.android.utils.coroutines.NzikDispatchers

internal class FfmpegPreparation(
    private val prepare: suspend () -> ModuleState = FfmpegModule::ensureReady,
) {
    private var worker by mutableStateOf<Deferred<ModuleState>?>(null)
    private var cancelled by mutableStateOf(false)
    private var disposed = false

    val isPreparing: Boolean get() = worker != null
    internal val showsProgress: Boolean get() = isPreparing && !cancelled

    suspend fun awaitReady(): ModuleState? = withContext(NzikDispatchers.UI) {
        if (disposed || isPreparing) return@withContext null
        supervisorScope {
            val task = async(NzikDispatchers.DATA, start = CoroutineStart.LAZY) { prepare() }
            cancelled = false
            worker = task
            try {
                task.await().takeUnless { cancelled }
            } catch (_: CancellationException) {
                ensureActive()
                null
            } finally {
                worker = null
            }
        }
    }

    fun cancel() {
        cancelled = true
        worker?.cancel()
    }

    fun dispose() {
        disposed = true
        cancel()
    }

    @Composable
    fun Render() {
        if (!showsProgress) return
        val states by FfmpegModule.manager.states.collectAsStateWithLifecycle(context = NzikDispatchers.DATA)
        val state = states[FfmpegModule.definition.id]
        val fraction = when (state) {
            is ModuleState.DOWNLOADING -> state.progress
            is ModuleState.UPDATING -> state.progress
            else -> null
        }
        val percent = fraction
            ?.takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(0f, 1f)
            ?.let { (it * 100).roundToInt() }
        InProgressDialog(
            total = if (percent == null) 0 else 100,
            done = percent ?: 0,
            text = stringResource(
                if ((state is ModuleState.DOWNLOADING || state is ModuleState.UPDATING) && percent != 100) {
                    R.string.modules_ffmpeg_downloading
                } else {
                    R.string.modules_ffmpeg_preparation
                }
            ),
            onDismiss = ::cancel,
            progressLabel = percent?.let { stringResource(R.string.modules_ffmpeg_progress_percent, it) },
        )
    }
}
