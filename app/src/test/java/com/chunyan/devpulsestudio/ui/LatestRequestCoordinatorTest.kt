package com.chunyan.devpulsestudio.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LatestRequestCoordinatorTest {

    @Test
    fun `only the latest debounced request starts`() = runTest {
        val coordinator = LatestRequestCoordinator(this)
        val started = mutableListOf<Long>()

        coordinator.launchLatest(debounceMs = 500) { generation ->
            started += generation
        }
        advanceTimeBy(250)
        coordinator.launchLatest(debounceMs = 500) { generation ->
            started += generation
        }

        advanceUntilIdle()

        assertEquals(listOf(2L), started)
    }

    @Test
    fun `stale callback cannot apply a result after swallowing cancellation`() = runTest {
        val coordinator = LatestRequestCoordinator(this)
        val firstRequestStarted = CompletableDeferred<Unit>()
        val applied = mutableListOf<String>()

        coordinator.launchLatest { generation ->
            firstRequestStarted.complete(Unit)
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                // Simulates a non-cooperative data source that returns after cancellation.
            }
            if (coordinator.isCurrent(generation)) applied += "first"
        }
        runCurrent()
        firstRequestStarted.await()

        coordinator.launchLatest { generation ->
            if (coordinator.isCurrent(generation)) applied += "second"
        }
        advanceUntilIdle()

        assertEquals(listOf("second"), applied)
    }
}
