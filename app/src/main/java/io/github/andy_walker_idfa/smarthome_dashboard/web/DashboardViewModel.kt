package io.github.andy_walker_idfa.smarthome_dashboard.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.andy_walker_idfa.smarthome_dashboard.di.AppContainer

/** Keeps [DashboardController] alive across activity re-creation, scoped to the dashboard screen. */
class DashboardViewModel(container: AppContainer) : ViewModel() {
    val controller =
        DashboardController(
            scope = viewModelScope,
            settingsRepository = container.settingsRepository,
            networkMonitor = container.networkMonitor,
            commandBus = container.webCommandBus,
            clock = container.clock,
            statusSink = container.dashboardStatus,
            recovery = container.recoveryStore
        ).also { it.start() }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DashboardViewModel(container) }
        }
    }
}
