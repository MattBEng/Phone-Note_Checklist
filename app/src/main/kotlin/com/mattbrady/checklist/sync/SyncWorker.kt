package com.mattbrady.checklist.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mattbrady.checklist.ChecklistApp
import com.mattbrady.checklist.data.SyncResult
import com.mattbrady.checklist.widget.ChecklistWidget

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = ChecklistApp.repository(applicationContext)
        return when (repo.syncNow()) {
            is SyncResult.Success -> {
                ChecklistWidget().updateAll(applicationContext)
                Result.success()
            }
            is SyncResult.NotConfigured -> Result.success()
            is SyncResult.Failed -> Result.retry()
        }
    }
}
