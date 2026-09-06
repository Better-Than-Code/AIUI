package com.cellular.rpc.domain.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.cellular.rpc.transport.queue.CarrierSafeQueueEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Central manager for user-configured AI SMS numbers.
 *
 * Fully provider-agnostic: allows users to enter any AI provider's SMS number,
 * apply it immediately, save numbers to a list, rename them, delete them,
 * and switch between them seamlessly.
 */
object CellularServiceManager {
    private const val TAG = "CellularServiceManager"
    private const val PREFS_NAME = "cellular_service_prefs"
    private const val KEY_ACTIVE_SERVICE_ID = "active_service_id"
    private const val KEY_SAVED_SERVICES_JSON = "custom_services_json"
    private const val KEY_MANUAL_OVERRIDE_PHONE = "manual_override_phone"
    private const val KEY_LAST_OUTBOUND_DESTINATION = "key_last_outbound_destination"

    private val savedProfiles = CopyOnWriteArrayList<CellularServiceProfile>()

    private val _activeService = MutableStateFlow(CellularServiceProfile.DEFAULT_AI)
    val activeServiceFlow: StateFlow<CellularServiceProfile> = _activeService.asStateFlow()

    @Volatile
    private var isInitialized = false

    private fun getPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) return
        val prefs = getPrefs(context)

        // Load saved profiles from persistent storage
        savedProfiles.clear()
        val customJson = prefs.getString(KEY_SAVED_SERVICES_JSON, null)
        if (!customJson.isNullOrBlank()) {
            try {
                val array = JSONArray(customJson)
                for (i in 0 until array.length()) {
                    CellularServiceProfile.fromJson(array.getString(i))?.let { savedProfiles.add(it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse saved services JSON", e)
            }
        }

        // If no saved profiles exist yet, initialize with the default profile
        if (savedProfiles.isEmpty()) {
            savedProfiles.add(CellularServiceProfile.DEFAULT_AI)
            persistSavedProfiles(context)
        }

        // Determine active profile
        val activeId = prefs.getString(KEY_ACTIVE_SERVICE_ID, CellularServiceProfile.DEFAULT_AI.id)
        var selected = savedProfiles.find { it.id == activeId } ?: savedProfiles.first()

        // Check if there was a manual phone override applied
        val manualPhone = prefs.getString(KEY_MANUAL_OVERRIDE_PHONE, null)
        if (!manualPhone.isNullOrBlank() && selected.phoneNumber != manualPhone) {
            selected = selected.copy(phoneNumber = manualPhone)
        }

        _activeService.value = selected
        isInitialized = true

        // Ensure CarrierSafeQueueEngine is pointing to the active phone number
        try {
            CarrierSafeQueueEngine.getInstance(context).destinationAddress = selected.phoneNumber
        } catch (ignored: Exception) {
        }
    }

    fun getActiveService(context: Context): CellularServiceProfile {
        if (!isInitialized) initialize(context)
        return _activeService.value
    }

    fun getAvailableServices(context: Context): List<CellularServiceProfile> {
        if (!isInitialized) initialize(context)
        return savedProfiles.toList()
    }

    fun setActiveService(context: Context, profile: CellularServiceProfile) {
        if (!isInitialized) initialize(context)
        _activeService.value = profile
        getPrefs(context).edit()
            .putString(KEY_ACTIVE_SERVICE_ID, profile.id)
            .putString(KEY_MANUAL_OVERRIDE_PHONE, profile.phoneNumber)
            .apply()

        try {
            CarrierSafeQueueEngine.getInstance(context).destinationAddress = profile.phoneNumber
        } catch (ignored: Exception) {
        }
        Log.i(TAG, "Active AI SMS Number set to: ${profile.name} (${profile.phoneNumber})")
    }

    /**
     * Sets or updates the AI destination phone number and applies it immediately.
     */
    fun setManualPhoneNumber(context: Context, phoneNumber: String, customName: String? = null) {
        if (!isInitialized) initialize(context)
        val cleanNumber = phoneNumber.trim()
        if (cleanNumber.isBlank()) return

        // Check if an existing profile already matches this phone number
        val existingIndex = savedProfiles.indexOfFirst {
            normalizePhoneNumber(it.phoneNumber) == normalizePhoneNumber(cleanNumber)
        }

        val profile = if (existingIndex >= 0) {
            val existing = savedProfiles[existingIndex]
            val updated = existing.copy(
                name = customName?.takeIf { it.isNotBlank() } ?: existing.name,
                phoneNumber = cleanNumber
            )
            savedProfiles[existingIndex] = updated
            persistSavedProfiles(context)
            updated
        } else {
            // Create a new saved profile
            val newProfile = CellularServiceProfile.createCustom(
                name = customName?.takeIf { it.isNotBlank() } ?: "AI (${cleanNumber.takeLast(10)})",
                phoneNumber = cleanNumber
            )
            savedProfiles.add(newProfile)
            persistSavedProfiles(context)
            newProfile
        }

        setActiveService(context, profile)
    }

    fun saveCustomService(context: Context, profile: CellularServiceProfile) {
        if (!isInitialized) initialize(context)
        savedProfiles.removeAll { it.id == profile.id }
        savedProfiles.add(profile)
        persistSavedProfiles(context)
    }

    fun renameService(context: Context, serviceId: String, newName: String) {
        if (!isInitialized) initialize(context)
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return

        val index = savedProfiles.indexOfFirst { it.id == serviceId }
        if (index >= 0) {
            val updated = savedProfiles[index].copy(name = cleanName)
            savedProfiles[index] = updated
            persistSavedProfiles(context)

            if (_activeService.value.id == serviceId) {
                _activeService.value = updated
            }
        }
    }

    fun deleteCustomService(context: Context, serviceId: String) {
        if (!isInitialized) initialize(context)
        savedProfiles.removeAll { it.id == serviceId }

        if (savedProfiles.isEmpty()) {
            savedProfiles.add(CellularServiceProfile.DEFAULT_AI)
        }
        persistSavedProfiles(context)

        if (_activeService.value.id == serviceId) {
            setActiveService(context, savedProfiles.first())
        }
    }

    private fun persistSavedProfiles(context: Context) {
        val array = JSONArray()
        for (p in savedProfiles) {
            array.put(p.toJson())
        }
        getPrefs(context).edit()
            .putString(KEY_SAVED_SERVICES_JSON, array.toString())
            .apply()
    }

    fun recordLastOutboundDestination(context: Context, destination: String) {
        val clean = normalizePhoneNumber(destination)
        if (clean.isNotBlank()) {
            getPrefs(context).edit().putString(KEY_LAST_OUTBOUND_DESTINATION, clean).apply()
        }
    }

    fun getLastOutboundDestination(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_OUTBOUND_DESTINATION, "") ?: ""
    }

    fun normalizePhoneNumber(number: String?): String {
        if (number.isNullOrBlank()) return ""
        return number.replace(Regex("[^0-9+]"), "")
    }

    fun isSenderRecognized(context: Context, senderAddress: String?): Boolean {
        if (senderAddress.isNullOrBlank()) return false
        val normalizedSender = normalizePhoneNumber(senderAddress)
        if (normalizedSender.isBlank()) return false

        // Check last texted number
        val lastOutbound = getLastOutboundDestination(context)
        if (lastOutbound.isNotBlank() && isNumberMatch(normalizedSender, lastOutbound)) {
            return true
        }

        // Check active service
        val active = getActiveService(context)
        val activeNormalized = normalizePhoneNumber(active.phoneNumber)
        if (isNumberMatch(normalizedSender, activeNormalized)) {
            return true
        }

        // Check all saved services
        for (service in getAvailableServices(context)) {
            val serviceNormalized = normalizePhoneNumber(service.phoneNumber)
            if (isNumberMatch(normalizedSender, serviceNormalized)) {
                return true
            }
        }

        return false
    }

    private fun isNumberMatch(num1: String, num2: String): Boolean {
        if (num1.isEmpty() || num2.isEmpty()) return false
        if (num1 == num2) return true

        val digits1 = num1.filter { it.isDigit() }
        val digits2 = num2.filter { it.isDigit() }
        if (digits1.isEmpty() || digits2.isEmpty()) return false

        if (digits1 == digits2) return true

        if (digits1.length >= 10 && digits2.length >= 10) {
            return digits1.takeLast(10) == digits2.takeLast(10)
        }

        if (digits1.length >= 7 && digits2.length >= 7) {
            return digits1.takeLast(7) == digits2.takeLast(7)
        }

        return false
    }
}
