package com.naturasonic.app.ui.screens.transientlimiter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransientLimiterUiState(
    val enabled: Boolean = false,
    val thresholdDb: Float = -6.0f
)

@HiltViewModel
class TransientLimiterViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    val uiState: StateFlow<TransientLimiterUiState> = combine(
        userPreferences.transientLimiterEnabled,
        userPreferences.transientLimiterThreshold
    ) { enabled, threshold ->
        TransientLimiterUiState(enabled = enabled, thresholdDb = threshold)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransientLimiterUiState())

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setTransientLimiterEnabled(enabled)
        }
    }

    fun setThreshold(thresholdDb: Float) {
        viewModelScope.launch {
            userPreferences.setTransientLimiterThreshold(thresholdDb)
        }
    }
}
