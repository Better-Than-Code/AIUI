package com.cellular.rpc.transport.receiver

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.util.Log

class MmsDownloadWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i("MmsDownloadWorker", "Starting background MMS poll via WorkManager")
        PallyMmsHelper.pollMmsInboxWithRetry(applicationContext, maxAttempts = 3)
        return Result.success()
    }
}
