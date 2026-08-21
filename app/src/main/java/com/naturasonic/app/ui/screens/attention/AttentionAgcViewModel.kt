package com.naturasonic.app.ui.screens.attention

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.audio.AttentionController
import com.naturasonic.app.audio.AttentionState
import com.naturasonic.app.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AttentionAgcViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val attentionController: AttentionController
) : ViewModel() {

    val attentionState: StateFlow<AttentionState> = attentionController.state

    val enabled: StateFlow<Boolean> = userPreferences.attentionAgcEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val speechBoostDb: StateFlow<Float> = userPreferences.speechBoostDb
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3.0f)

    val alertAttenuationDb: StateFlow<Float> = userPreferences.alertAttenuationDb
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4.0f)

    fun setEnabled(value: Boolean) {
        viewModelScope.launch { userPreferences.setAttentionAgcEnabled(value) }
    }

    fun setSpeechBoostDb(value: Float) {
        viewModelScope.launch { userPreferences.setSpeechBoostDb(value) }
    }

    fun setAlertAttenuationDb(value: Float) {
        viewModelScope.launch { userPreferences.setAlertAttenuationDb(value) }
    }
}
