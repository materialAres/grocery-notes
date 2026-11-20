package com.example.stickers.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs database writes one at a time, in the order they were submitted, on a scope that
 * outlives screens. The editor uses it so that a debounced save, a structural change and
 * the purge on exit can never overtake each other, and still complete after the editor
 * has been closed.
 *
 * Submit from a single thread (the main thread): the mutex is FIFO and each write queues
 * on it before [launch]/[run] return, so submission order is execution order.
 */
class WriteQueue(private val scope: CoroutineScope) {
    private val mutex = Mutex()

    /** Fire-and-forget write. */
    fun launch(write: suspend () -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { mutex.withLock { write() } }
    }

    /** Write whose result the caller needs (e.g. a new block id). */
    suspend fun <T> run(write: suspend () -> T): T =
        scope.async(start = CoroutineStart.UNDISPATCHED) { mutex.withLock { write() } }.await()
}
