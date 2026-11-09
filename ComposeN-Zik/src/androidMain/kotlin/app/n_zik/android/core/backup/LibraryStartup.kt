package app.n_zik.android.core.backup

import android.os.Handler
import android.os.Looper
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

internal sealed interface LibraryStartupState {
    data object Waiting : LibraryStartupState
    data object Ready : LibraryStartupState
    data class Failed(val cause: Exception) : LibraryStartupState
}

internal class LibraryStartupException(cause: Exception) :
    IllegalStateException("Could not recover the library at startup", cause)

internal class LibraryStartup(
    private val recover: () -> Unit,
    private val initialize: () -> Unit,
) {
    private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow<LibraryStartupState>(LibraryStartupState.Waiting)
    val state = mutableState.asStateFlow()

    @Volatile
    private var recovered = false
    private var started = false

    @Synchronized
    fun start() {
        if (started && mutableState.value !is LibraryStartupState.Failed) return
        started = true
        mutableState.value = LibraryStartupState.Waiting
        scope.launch {
            try {
                recover()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.tag("LibraryStartup").e(error, "Library recovery failed")
                mutableState.value = LibraryStartupState.Failed(error)
                return@launch
            }
            // Keep initialization on the application thread, with its normal crash handling.
            mainHandler.post {
                recovered = true
                initialize()
                mutableState.value = LibraryStartupState.Ready
            }
        }
    }

    suspend fun awaitReady() {
        when (val outcome = state.first { it != LibraryStartupState.Waiting }) {
            LibraryStartupState.Ready -> Unit
            is LibraryStartupState.Failed -> throw LibraryStartupException(outcome.cause)
            LibraryStartupState.Waiting -> error("Library startup is still waiting")
        }
    }

    fun requireRecovered() {
        check(recovered) { "Library access attempted before startup recovery finished" }
    }
}
