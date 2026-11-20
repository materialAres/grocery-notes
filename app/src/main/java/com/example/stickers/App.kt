package com.example.stickers

import android.app.Application
import android.content.Context
import com.example.stickers.data.AppDatabase
import com.example.stickers.data.NoteRepository
import com.example.stickers.widget.GlanceWidgetUpdater

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency container (DESIGN.md §2). */
class AppContainer(context: Context) {
    val database: AppDatabase by lazy { AppDatabase.build(context) }

    val repository: NoteRepository by lazy { NoteRepository(database, GlanceWidgetUpdater(context)) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as App).container
