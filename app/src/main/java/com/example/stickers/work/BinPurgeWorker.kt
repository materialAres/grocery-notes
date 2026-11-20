package com.example.stickers.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.stickers.appContainer
import java.util.concurrent.TimeUnit

/** Daily: permanently deletes notes that have been in the bin for more than 7 days (DESIGN.md §5.6). */
class BinPurgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        applicationContext.appContainer.repository.purgeExpiredBin()
        return Result.success()
    }

    companion object {
        private const val NAME = "bin-purge"

        /** Safe to call on every app start: an existing schedule is kept. */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BinPurgeWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
