package app.n_zik.android.components.ui.screens.rewind

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.n_zik.android.Dependencies
import app.n_zik.android.core.database.Database
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * UI state of the "My Rewind" home screen: the year rows (newest first) plus
 * the all-time row (null until the first load, or forever when the history is
 * empty).
 */
internal data class RewindHomeUiState(
    val isLoading: Boolean = true,
    val years: List<RewindHomeYear> = emptyList(),
    val global: RewindHomeGlobal? = null
)

/**
 * Loads [RewindHomeData] once per ViewModel (the state survives recompositions,
 * so leaving and re-entering the home does not refetch — spec GH-275, patch
 * "No ViewModel"). Failures are logged and turned into an empty payload.
 */
internal class RewindHomeViewModel(
    private val fetcher: RewindDataFetcher,
    application: Application
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(RewindHomeUiState())
    val state: StateFlow<RewindHomeUiState> = _state.asStateFlow()

    private var loadStarted = false

    /** Loads the home data (guarded: one load per ViewModel). */
    fun load() {
        if (loadStarted) return
        loadStarted = true
        viewModelScope.launch {
            try {
                val data = fetcher.getRewindHomeData()
                if (isActive) {
                    _state.value = RewindHomeUiState(isLoading = false, years = data.years, global = data.global)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("RewindHome").e(e, "Failed to build Rewind home data")
                if (isActive) {
                    _state.value = RewindHomeUiState(isLoading = false, years = emptyList(), global = null)
                }
            }
        }
    }

    companion object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RewindHomeViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return RewindHomeViewModel(
                    RewindDataFetcher(Database.eventTable),
                    Dependencies.application
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
