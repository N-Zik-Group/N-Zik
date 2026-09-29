package app.n_zik.android.components.ui.screens.rewind

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.n_zik.android.Dependencies
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.profiles.loadActiveProfileFace
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.FaceAvatar
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * UI state of the Rewind deck screen. [data] is always non-null once loading
 * finished (a failed fetch produces an empty payload, never a blank screen).
 */
internal data class RewindDeckUiState(
    val isLoading: Boolean = true,
    val username: String = "",
    val faceAvatar: FaceAvatar? = null,
    val data: RewindData? = null
)

/**
 * Loads [RewindData] for the requested [RewindPeriod].
 *
 * The fetch runs on [dataDispatcher] (DATA in production, an unconfined test
 * dispatcher in unit tests); failures are logged and turned into
 * [emptyRewindData] so [state] always exposes non-null [RewindDeckUiState.data]
 * after loading (spec GH-275, patch "No ViewModel").
 */
internal class RewindDeckViewModel(
    private val fetcher: RewindDataFetcher,
    private val application: Application,
    private val dataDispatcher: CoroutineDispatcher = NzikDispatchers.DATA
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(RewindDeckUiState())
    val state: StateFlow<RewindDeckUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /** (Re)loads the deck for [period], cancelling any in-flight load. */
    fun load(period: RewindPeriod) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = RewindDeckUiState(isLoading = true, data = null)
            try {
                val (username, faceAvatar) = withContext(dataDispatcher) {
                    val defaultName = application.getString(R.string.profile_base_name)
                    // The deck shows the active profile's face: its display name or a
                    // logged-in account source (spec-profiles-page-face), with the legacy
                    // custom name and the default app name as fallbacks. A failing read
                    // falls back to the default name without an avatar.
                    runCatching {
                        val face = loadActiveProfileFace(application, defaultName)
                        face.name to face.avatar
                    }.getOrElse { error ->
                        Timber.tag("Rewind").e(error, "Failed to resolve the rewind face")
                        defaultName to null
                    }
                }
                val data = fetcher.getRewindData(period)
                if (isActive) {
                    _state.value = RewindDeckUiState(
                        isLoading = false,
                        username = username,
                        faceAvatar = faceAvatar,
                        data = data
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("Rewind").e(e, "Failed to build Rewind data for %s", period.fileNameToken())
                if (isActive) {
                    _state.value = RewindDeckUiState(
                        isLoading = false,
                        username = _state.value.username,
                        faceAvatar = _state.value.faceAvatar,
                        data = emptyRewindData(period)
                    )
                }
            }
        }
    }

    companion object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RewindDeckViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return RewindDeckViewModel(
                    RewindDataFetcher(Database.eventTable),
                    Dependencies.application
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
