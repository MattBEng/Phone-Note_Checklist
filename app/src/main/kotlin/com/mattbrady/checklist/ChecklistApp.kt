package com.mattbrady.checklist

import android.app.Application
import android.content.Context
import com.mattbrady.checklist.data.ChecklistRepository
import com.mattbrady.checklist.data.local.AppDatabase
import com.mattbrady.checklist.data.remote.ApiClientProvider
import com.mattbrady.checklist.sync.SyncScheduler

class ChecklistApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncScheduler.schedulePeriodic(this)
    }

    companion object {
        @Volatile private var repositoryInstance: ChecklistRepository? = null

        fun repository(context: Context): ChecklistRepository =
            repositoryInstance ?: synchronized(this) {
                repositoryInstance ?: ChecklistRepository(
                    AppDatabase.getInstance(context.applicationContext),
                    ApiClientProvider(context.applicationContext),
                    context.applicationContext,
                ).also { repositoryInstance = it }
            }
    }
}
