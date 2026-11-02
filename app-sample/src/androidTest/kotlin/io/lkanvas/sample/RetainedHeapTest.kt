package io.lkanvas.sample

import android.app.Activity
import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.lkanvas.chart.LKanvasChart
import io.lkanvas.compose.state.ChartState
import io.lkanvas.core.data.Candle
import io.lkanvas.core.store.MarketKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.ref.WeakReference

/** Heap evidence is diagnostic (debug); R8 performance remains separately measured. */
@RunWith(AndroidJUnit4::class)
class RetainedHeapTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun liveCharts() = LKanvasChart::class.java.getDeclaredField("liveCharts").let {
        it.isAccessible = true
        it.get(null) as Map<*, *>
    }
    private fun resumed(): Activity? {
        var activity: Activity? = null
        instrumentation.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
        }
        return activity
    }
    private fun awaitActivity(previous: Activity? = null): Activity {
        repeat(100) { resumed()?.let { if (it !== previous) return it }; Thread.sleep(100) }
        error("sample activity did not resume")
    }
    private fun exercise(): List<WeakReference<Any>> {
        instrumentation.uiAutomation.executeShellCommand("am start -W -n io.lkanvas.sample/.MainActivity").close()
        val watched = mutableListOf<WeakReference<Any>>()
        var activity = awaitActivity()
        repeat(30) { index ->
            instrumentation.waitForIdleSync()
            val chart = liveCharts().values.filterIsInstance<LKanvasChart>().single()
            watched += WeakReference(chart as Any)
            watched += WeakReference(chart.state as Any)
            watched += WeakReference(activity as Any)
            if (index == 29) {
                runBlocking(Dispatchers.Main) {
                    repeat(100) { market ->
                        chart.state.reset(MarketKey("HEAP-$market", "1m"), (0 until 1000).map {
                            Candle(it * 60000L, 100.0, 102.0, 99.0, 101.0, 1000.0)
                        })
                    }
                }
                instrumentation.runOnMainSync { activity.finish() }
            } else {
                val previous = activity
                instrumentation.runOnMainSync { previous.recreate() }
                activity = awaitActivity(previous)
            }
        }
        repeat(100) { if (!activity.isDestroyed) Thread.sleep(50) }
        assertTrue(activity.isDestroyed)
        return watched
    }
    @Test fun thirtyDisposalsAndHundredMarketsHaveRealHeapEvidence() {
        val before = liveCharts().size
        val watched = exercise() // All temporary strong references leave this stack before GC/dump.
        instrumentation.waitForIdleSync()
        repeat(5) { Runtime.getRuntime().gc(); System.runFinalization(); Thread.sleep(500) }
        val output = File(instrumentation.targetContext.getExternalFilesDir("validation"), "disposed-charts.hprof")
        Debug.dumpHprofData(output.absolutePath)
        File(output.parentFile, "disposed-charts.txt").writeText(
            "cycles=30; markets=100; watched=${watched.size}; survivingWeak=${watched.count { it.get() != null }}; registryBefore=$before; registryAfter=${liveCharts().size}\n",
        )
        assertEquals("facade registry", before, liveCharts().size)
        // A host-side Shark dominator analysis decides retained bytes; weak counts are not that metric.
        assertTrue(output.length() > 0)
    }
}
