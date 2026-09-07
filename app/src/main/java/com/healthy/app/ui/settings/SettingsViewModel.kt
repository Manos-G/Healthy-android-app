package com.healthy.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthy.app.data.HealthySettings
import com.healthy.app.data.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)

    val settings: StateFlow<HealthySettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthySettings())

    fun setBedtime(value: String) = viewModelScope.launch { store.setTargetBedtime(value) }

    fun setHalfLife(value: Double) = viewModelScope.launch { store.setHalfLifeHours(value) }

    fun setLimit(value: Int) = viewModelScope.launch { store.setBedtimeLimitMg(value) }

    fun setFluidTarget(value: Int) = viewModelScope.launch { store.setFluidTargetMl(value) }

    fun setUnitsPerBeer(value: Double) = viewModelScope.launch { store.setUnitsPerBeer(value) }

    fun setUnitsPerWine(value: Double) = viewModelScope.launch { store.setUnitsPerWine(value) }

    fun setTrackCycle(value: Boolean) = viewModelScope.launch { store.setTrackCycle(value) }
}
