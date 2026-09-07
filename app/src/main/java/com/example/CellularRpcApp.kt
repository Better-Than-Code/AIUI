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

        // Register ContentObserver for robust real-device SMS inbox syncing
        try {
            com.cellular.rpc.transport.receiver.PallySmsObserver.register(this)
        } catch (e: Exception) {
            android.util.Log.e("CellularRpcApp", "Failed to register SMS observer: ${e.message}")
        }
    }

    companion object {
        lateinit var instance: CellularRpcApp
            private set
    }
}
