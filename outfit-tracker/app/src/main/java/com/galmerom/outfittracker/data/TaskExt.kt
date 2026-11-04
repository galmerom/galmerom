package com.galmerom.outfittracker.data

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Firestore acknowledges a write only after the server responds. Offline, the task stays pending
 * while the write sits in the local cache. A short wait treats that queued write as saved.
 */
suspend fun Task<Void>.awaitCommitted(timeoutMs: Long = 2_000): Result<Unit> {
    val completed = withTimeoutOrNull(timeoutMs) {
        runCatching { await() }
    }
    return when {
        completed == null -> Result.success(Unit)
        completed.isSuccess -> Result.success(Unit)
        else -> Result.failure(completed.exceptionOrNull() ?: IllegalStateException("write failed"))
    }
}
