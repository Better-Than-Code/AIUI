package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.cellular.rpc.domain.service.CellularServiceManager
import com.cellular.rpc.domain.service.CellularServiceProfile
import com.cellular.rpc.transport.queue.CarrierSafeQueueEngine
import com.cellular.rpc.transport.service.CellularRpcForegroundService
import com.cellular.rpc.widget.WidgetPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ServiceViewModel(application: Application) : AndroidViewModel(application) {

    private val queueEngine: CarrierSafeQueueEngine
        get() = CellularRpcForegroundService.activeEngine ?: CellularRpcApp.instance.queueEngine

    private val _isServiceActive = MutableStateFlow(CellularRpcForegroundService.isRunning())
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    private val _pallyPhoneNumber = MutableStateFlow(
        CellularServiceManager.getActiveService(application).phoneNumber
    )
    val pallyPhoneNumber: StateFlow<String> = _pallyPhoneNumber.asStateFlow()

    val activeServiceProfile: StateFlow<CellularServiceProfile> =
        CellularServiceManager.activeServiceFlow

    private val _availableServices = MutableStateFlow<List<CellularServiceProfile>>(emptyList())
    val availableServices: StateFlow<List<CellularServiceProfile>> = _availableServices.asStateFlow()

    private val _isLoopbackSimulation = MutableStateFlow(
        WidgetPreferences.isLoopbackSimulationEnabled(application)
    )
    val isLoopbackSimulation: StateFlow<Boolean> = _isLoopbackSimulation.asStateFlow()

    private val _isAdminApprovalMode = MutableStateFlow(
        WidgetPreferences.isAdminApprovalModeEnabled(application)
    )
    val isAdminApprovalMode: StateFlow<Boolean> = _isAdminApprovalMode.asStateFlow()

    init {
        CellularServiceManager.initialize(application)
        refreshServices()
    }

    fun refreshServices() {
        val app = getApplication<Application>()
        val active = CellularServiceManager.getActiveService(app)
        _availableServices.value = CellularServiceManager.getAvailableServices(app)
        _pallyPhoneNumber.value = active.phoneNumber
        queueEngine.destinationAddress = active.phoneNumber
        queueEngine.loopbackEnabled = _isLoopbackSimulation.value
    }

    fun selectService(service: CellularServiceProfile) {
        val app = getApplication<Application>()
        CellularServiceManager.setActiveService(app, service)
        refreshServices()
    }

    fun renameService(serviceId: String, newName: String) {
        val app = getApplication<Application>()
        CellularServiceManager.renameService(app, serviceId, newName)
        refreshServices()
    }

    fun updatePallyPhoneNumber(number: String, customName: String? = null) {
        val trimmed = number.trim()
        if (trimmed.isNotEmpty()) {
            val app = getApplication<Application>()
            CellularServiceManager.setManualPhoneNumber(app, trimmed, customName)
            setLoopbackSimulation(false)
            refreshServices()
        }
    }

    fun saveCustomService(service: CellularServiceProfile) {
        val app = getApplication<Application>()
        CellularServiceManager.saveCustomService(app, service)
        refreshServices()
    }

    fun deleteCustomService(serviceId: String) {
        val app = getApplication<Application>()
        CellularServiceManager.deleteCustomService(app, serviceId)
        refreshServices()
    }

    fun toggleLoopbackSimulation() {
        setLoopbackSimulation(!_isLoopbackSimulation.value)
    }

    fun setLoopbackSimulation(enabled: Boolean) {
        val app = getApplication<Application>()
        WidgetPreferences.setLoopbackSimulationEnabled(app, enabled)
        _isLoopbackSimulation.value = enabled
        queueEngine.loopbackEnabled = enabled
    }

    fun toggleAdminApprovalMode() {
        val app = getApplication<Application>()
        val newState = !_isAdminApprovalMode.value
        WidgetPreferences.setAdminApprovalModeEnabled(app, newState)
        _isAdminApprovalMode.value = newState
    }

    fun toggleForegroundService() {
        val app = getApplication<Application>()
        if (CellularRpcForegroundService.isRunning()) {
            val intent = android.content.Intent(app, CellularRpcForegroundService::class.java).apply {
                action = CellularRpcForegroundService.ACTION_STOP
            }
            app.stopService(intent)
            _isServiceActive.value = false
        } else {
            val intent = android.content.Intent(app, CellularRpcForegroundService::class.java).apply {
                action = CellularRpcForegroundService.ACTION_START
            }
            androidx.core.content.ContextCompat.startForegroundService(app, intent)
            _isServiceActive.value = true
        }
    }
}
