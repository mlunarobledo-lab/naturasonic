package com.naturasonic.app.ui.screens.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naturasonic.app.data.local.entity.AlertEvent
import com.naturasonic.app.data.local.entity.AlertSoundClass
import com.naturasonic.app.data.repository.AlertHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class AlertHistoryUiState(
    val alerts: Map<LocalDate, List<AlertEvent>> = emptyMap(),
    val selectedFilter: AlertSoundClass? = null,
    val isEmpty: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlertHistoryViewModel @Inject constructor(
    private val repository: AlertHistoryRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow<AlertSoundClass?>(null)
    val selectedFilter: StateFlow<AlertSoundClass?> = _selectedFilter.asStateFlow()

    val uiState: StateFlow<AlertHistoryUiState> = _selectedFilter
        .flatMapLatest { filter ->
            val flow = if (filter != null) {
                repository.getByClass(filter.key)
            } else {
                repository.getAll()
            }
            flow.map { alerts ->
                val grouped = alerts.groupBy { event ->
                    Instant.ofEpochMilli(event.detectedAt)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                }
                AlertHistoryUiState(
                    alerts = grouped,
                    selectedFilter = filter,
                    isEmpty = alerts.isEmpty()
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlertHistoryUiState())

    fun setFilter(filter: AlertSoundClass?) {
        _selectedFilter.value = filter
    }
}
