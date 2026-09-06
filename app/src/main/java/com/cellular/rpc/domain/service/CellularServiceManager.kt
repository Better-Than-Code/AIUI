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
 * Central manager for pluggable AI SMS service configurations.
 *
 * Handles switching active AI SMS providers, adding custom services,
 * manually setting destination phone numbers, and validating incoming
 * SMS messages from any configured AI SMS service.
 */
object CellularServiceManager {
    private const val TAG = "CellularServiceManager"
    private const val PREFS_NAME = "cellular_service_prefs"
    private const val KEY_ACTIVE_SERVICE_ID = "active_service_id"
    private const val KEY_CUSTOM_SERVICES_JSON = "custom_services_json"
    private const val KEY_MANUAL_OVERRIDE_PHONE = "manual_override_phone"

    private val builtInPresets = listOf(
        CellularServiceProfile.DEFAULT_PALLY,
        CellularServiceProfile.PRESET_TWILIO_AI,
        CellularServiceProfile.PRESET_LOCAL_LLM
    )

    private val customProfiles = CopyOnWriteArrayList<CellularServiceProfile>()

    private val _activeService = MutableStateFlow(CellularServiceProfile.DEFAULT_PALLY)
    val activeServiceFlow: StateFlow<CellularServiceProfile> = _activeService.asStateFlow()

    @Volatile
    private var isInitialized = false

    private fun getPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) return
        val prefs = getPrefs(context)

        // Load custom profiles
        customProfiles.clear()
        val customJson = prefs.getString(KEY_CUSTOM_SERVICES_JSON, null)
        if (!customJson.isNullOrBlank()) {
            try {
                val array = JSONArray(customJson)
                for (i in 0 until array.length()) {
                    CellularServiceProfile.fromJson(array.getString(i))?.let { customProfiles.add(it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse custom services JSON", e)
            }
        }

        // Determine active profile
        val activeId = prefs.getString(KEY_ACTIVE_SERVICE_ID, CellularServiceProfile.DEFAULT_PALLY.id)
        val allProfiles = builtInPresets + customProfiles
        var selected = allProfiles.find { it.id == activeId } ?: CellularServiceProfile.DEFAULT_PALLY

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
        return builtInPresets + customProfiles
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
        Log.i(TAG, "Active AI SMS Service set to: ${profile.name} (${profile.phoneNumber})")
    }

    /**
     * Manually sets or overrides the destination phone number.
     * This creates or updates a custom service profile so any arbitrary number can be used.
     */
    fun setManualPhoneNumber(context: Context, phoneNumber: String, customName: String? = null) {
        if (!isInitialized) initialize(context)
        val cleanNumber = phoneNumber.trim()
        if (cleanNumber.isBlank()) return

        val current = _activeService.value
        val updated = if (current.isBuiltIn && current.phoneNumber != cleanNumber) {
            // Create a custom profile so built-in preset is preserved
            val newProfile = CellularServiceProfile.createCustom(
                name = customName ?: "Manual (${cleanNumber.takeLast(10)})",
                phoneNumber = cleanNumber,
                description = "Manually entered AI SMS service phone number",
                protocolMode = current.protocolMode
            )
            saveCustomService(context, newProfile)
            newProfile
        } else {
            val modified = current.copy(
                name = customName ?: current.name,
                phoneNumber = cleanNumber
            )
            if (!current.isBuiltIn) {
                saveCustomService(context, modified)
            }
            modified
        }

        setActiveService(context, updated)
    }

    fun saveCustomService(context: Context, profile: CellularServiceProfile) {
        if (!isInitialized) initialize(context)
        customProfiles.removeAll { it.id == profile.id }
        customProfiles.add(profile)
        persistCustomProfiles(context)
    }

    fun deleteCustomService(context: Context, serviceId: String) {
        if (!isInitialized) initialize(context)
        customProfiles.removeAll { it.id == serviceId }
        persistCustomProfiles(context)

        if (_activeService.value.id == serviceId) {
            setActiveService(context, CellularServiceProfile.DEFAULT_PALLY)
        }
    }

    private fun persistCustomProfiles(context: Context) {
        val array = JSONArray()
        for (p in customProfiles) {
            array.put(p.toJson())
        }
        getPrefs(context).edit()
            .putString(KEY_CUSTOM_SERVICES_JSON, array.toString())
            .apply()
    }

    private const val KEY_LAST_OUTBOUND_DESTINATION = "key_last_outbound_destination"

    fun recordLastOutboundDestination(context: Context, destination: String) {
        val clean = normalizePhoneNumber(destination)
        if (clean.isNotBlank()) {
            getPrefs(context).edit().putString(KEY_LAST_OUTBOUND_DESTINATION, clean).apply()
        }
    }

    fun getLastOutboundDestination(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_OUTBOUND_DESTINATION, "") ?: ""
    }

    /**
     * Normalizes a phone number to digits only (or digits with leading +).
     */
    fun normalizePhoneNumber(number: String?): String {
        if (number.isNullOrBlank()) return ""
        return number.replace(Regex("[^0-9+]"), "")
    }

    /**
     * Checks whether an incoming SMS sender matches the active service,
     * any configured preset, any saved custom service number, or the last texted number.
     */
    fun isSenderRecognized(context: Context, senderAddress: String?): Boolean {
        if (senderAddress.isNullOrBlank()) return false
        val normalizedSender = normalizePhoneNumber(senderAddress)
        if (normalizedSender.isBlank()) return false

        // Check if sender matches the destination we most recently texted
        val lastOutbound = getLastOutboundDestination(context)
        if (lastOutbound.isNotBlank() && isNumberMatch(normalizedSender, lastOutbound)) {
            return true
        }

        val active = getActiveService(context)
        val activeNormalized = normalizePhoneNumber(active.phoneNumber)

        // Check active service
        if (isNumberMatch(normalizedSender, activeNormalized)) {
            return true
        }

        // Check all available services
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

        // Exact digit match
        if (digits1 == digits2) return true

        // Match on last 10 digits for US/international prefix variance (+1 vs local)
        if (digits1.length >= 10 && digits2.length >= 10) {
            return digits1.takeLast(10) == digits2.takeLast(10)
        }

        // Match on last 7 digits for local dialing
        if (digits1.length >= 7 && digits2.length >= 7) {
            return digits1.takeLast(7) == digits2.takeLast(7)
        }

        return false
    }
}
