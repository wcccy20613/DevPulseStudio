package com.chunyan.devpulsestudio.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs one discovery request at a time. A newer filter or pagination request
 * cancels the previous job and receives a new generation token, so a source
 * that handles cancellation late cannot overwrite newer UI state.
 */
internal class LatestRequestCoordinator(
    private val scope: CoroutineScope,
) {
    private var generation = 0L
    private var activeJob: Job? = null

    fun launchLatest(
        debounceMs: Long = 0L,
        block: suspend (generation: Long) -> Unit,
    ) {
        activeJob?.cancel()
        val requestGeneration = ++generation
        activeJob = scope.launch {
            if (debounceMs > 0) delay(debounceMs)
            block(requestGeneration)
        }
    }

    fun isCurrent(requestGeneration: Long): Boolean = requestGeneration == generation

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        generation++
    }
}
