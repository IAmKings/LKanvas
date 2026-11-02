package io.lkanvas.chart

import io.lkanvas.compose.state.ChartState
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class EpochWatermarkTest {
    @Test fun concurrentRejectionsKeepTheMaximumAndAllowANewerEpoch() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = ChartState(scope)
            val start = CountDownLatch(1)
            val workers = (0 until 8).map { worker ->
                thread {
                    start.await()
                    for (epoch in 500 downTo 1) state.markInitRejected(worker * 500L + epoch)
                }
            }
            start.countDown()
            workers.forEach { it.join(5000); assertFalse(it.isAlive) }
            assertTrue(state.isEpochRejected(4000))
            assertFalse(state.isEpochRejected(4001))
            state.markInitRejected(1)
            assertTrue(state.isEpochRejected(4000))
        } finally { scope.cancel() }
    }
}
