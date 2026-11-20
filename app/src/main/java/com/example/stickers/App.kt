package com.example.stickers

import android.app.Application
import android.content.Context
import com.example.stickers.data.AppDatabase
import com.example.stickers.data.NoteRepository
import com.example.stickers.data.WriteQueue
import com.example.stickers.widget.GlanceWidgetUpdater
import com.example.stickers.widget.PendingPin
import com.example.stickers.widget.WidgetPinner
import com.example.stickers.work.BinPurgeWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        BinPurgeWorker.schedule(this)
    }
}

/** Manual dependency container (DESIGN.md §2). */
class AppContainer(context: Context) {
    /** Lives as long as the process: work that must finish after a screen closes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.build(context) }

    val repository: NoteRepository by lazy { NoteRepository(database, GlanceWidgetUpdater(context)) }

    /** Ordered queue for the editor's writes, shared so a reopened editor waits for the previous one. */
    val editorWrites: WriteQueue by lazy { WriteQueue(appScope) }

    val pendingPin: PendingPin by lazy { PendingPin(context) }

    val widgetPinner: WidgetPinner by lazy { WidgetPinner(context, pendingPin) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as App).container
