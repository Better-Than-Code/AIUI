package com.example

import android.app.Application
import com.cellular.rpc.data.local.AppDatabase
import com.cellular.rpc.transport.queue.CarrierSafeQueueEngine

class CellularRpcApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var queueEngine: CarrierSafeQueueEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        queueEngine = CarrierSafeQueueEngine(
            context = this,
            outboxDao = database.outboxDao(),
            destinationAddress = "+16462619684",
            destinationPort = 8901
        )
        // Automatically start queue engine for responsive offline RPC
        queueEngine.start()

        // Initialize Watchdog Safe-Boot Interceptor & Dynamic Chat Theming Engine
        try {
            com.cellular.rpc.ui.chat.theme.ChatThemeManager.initialize(this)
        } catch (e: Exception) {
            android.util.Log.e("CellularRpcApp", "Failed to initialize ChatThemeManager: ${e.message}")
        }

        // Legacy ContentObserver was removed in favor of static broadcast receivers
    }

    companion object {
        lateinit var instance: CellularRpcApp
            private set
    }
}
