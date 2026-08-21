package com.naturasonic.app.ui.screens.soundscape

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.audio.DosimetryManager
import com.naturasonic.app.audio.DosimetrySnapshot
import com.naturasonic.app.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SoundscapeViewModel @Inject constructor(
    private val dosimetryManager: DosimetryManager,
    private val userPreferences: UserPreferences
) : ViewModel() {

    val snapshot: StateFlow<DosimetrySnapshot> = dosimetryManager.snapshot
    val dbaHistory: StateFlow<List<Float>> = dosimetryManager.dbaHistory

    val dosimetryEnabled: StateFlow<Boolean> = userPreferences.dosimetryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val calibrationOffset: StateFlow<Float> = userPreferences.calibrationOffset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 94f)

    fun setDosimetryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setDosimetryEnabled(enabled)
        }
    }

    fun setCalibrationOffset(offset: Float) {
        viewModelScope.launch {
            userPreferences.setCalibrationOffset(offset)
        }
    }
}
