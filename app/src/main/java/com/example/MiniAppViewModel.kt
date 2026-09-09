package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cellular.rpc.data.local.AppBlueprintEntity
import com.cellular.rpc.data.local.AppDatabase
import com.cellular.rpc.data.local.DynamicFeatureEntity
import com.cellular.rpc.domain.dynamic.DynamicFeatureManager
import com.cellular.rpc.domain.miniapp.MiniAppBlueprint
import com.cellular.rpc.domain.miniapp.MiniAppDeckManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MiniAppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dynamicFeatureDao = db.dynamicFeatureDao()
    private val appBlueprintDao = db.appBlueprintDao()

    val dynamicFeatures: StateFlow<List<DynamicFeatureEntity>> = dynamicFeatureDao.getAllFeaturesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installedMiniApps: StateFlow<List<AppBlueprintEntity>> = appBlueprintDao.getAllInstalledAppsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedMiniApp = MutableStateFlow<AppBlueprintEntity?>(null)
    val selectedMiniApp: StateFlow<AppBlueprintEntity?> = _selectedMiniApp.asStateFlow()

    private val _selectedFeature = MutableStateFlow<DynamicFeatureEntity?>(null)
    val selectedFeature: StateFlow<DynamicFeatureEntity?> = _selectedFeature.asStateFlow()

    fun selectMiniApp(app: AppBlueprintEntity?) {
        _selectedMiniApp.value = app
    }

    fun installMiniApp(blueprint: MiniAppBlueprint, state: Map<String, Any?>) {
        viewModelScope.launch(Dispatchers.IO) {
            MiniAppDeckManager.installApp(getApplication(), blueprint, state)
        }
    }

    fun updateMiniAppState(appId: String, newState: Map<String, Any?>) {
        viewModelScope.launch(Dispatchers.IO) {
            MiniAppDeckManager.updateState(getApplication(), appId, newState)
        }
    }

    fun uninstallMiniApp(appId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            MiniAppDeckManager.uninstallApp(getApplication(), appId)
            if (_selectedMiniApp.value?.appId == appId) {
                _selectedMiniApp.value = null
            }
        }
    }

    fun seedSampleMiniApps() {
        viewModelScope.launch(Dispatchers.IO) {
            MiniAppDeckManager.seedSampleMiniAppsIfEmpty(getApplication())
        }
    }

    fun selectDynamicFeature(feature: DynamicFeatureEntity?) {
        _selectedFeature.value = feature
    }

    fun deleteDynamicFeature(featureId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dynamicFeatureDao.deleteFeature(featureId)
            if (_selectedFeature.value?.featureId == featureId) {
                _selectedFeature.value = null
            }
        }
    }

    fun deploySampleFeature(sampleType: String = "solar") {
        viewModelScope.launch(Dispatchers.IO) {
            DynamicFeatureManager.seedSampleFeaturesIfEmpty(getApplication())
        }
    }
}
