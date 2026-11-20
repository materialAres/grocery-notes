package com.example.stickers.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.stickers.appContainer

/**
 * Moves the notes of removed widgets to the bin (DESIGN.md §5.4).
 *
 * Runs as work rather than inside the receiver: Glance's receiver already claims the
 * broadcast's goAsync(), and a plain coroutine could be killed with the process.
 */
class WidgetsRemovedWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ids = inputData.getIntArray(KEY_WIDGET_IDS) ?: return Result.success()
        applicationContext.appContainer.repository.onWidgetsRemoved(ids)
        return Result.success()
    }

    companion object {
        private const val KEY_WIDGET_IDS = "widgetIds"

        fun enqueue(context: Context, appWidgetIds: IntArray) {
            val request = OneTimeWorkRequestBuilder<WidgetsRemovedWorker>()
                .setInputData(workDataOf(KEY_WIDGET_IDS to appWidgetIds))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
