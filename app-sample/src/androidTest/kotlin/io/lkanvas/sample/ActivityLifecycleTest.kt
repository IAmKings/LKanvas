package io.lkanvas.sample

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.lkanvas.chart.LKanvasChart
import io.lkanvas.compose.state.ChartState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityLifecycleTest {
    private fun chartCount(): Int = LKanvasChart::class.java.getDeclaredField("liveCharts").let {
        it.isAccessible = true
        (it.get(null) as Map<*, *>).size
    }

    @Test fun recreatingAndClosingActivityReleasesItsFacade() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        closeSampleActivities()
        val before = chartCount()
        // MIUI blocks an instrumentation process launching an activity from the background.
        instrumentation.uiAutomation.executeShellCommand("am start -W -n io.lkanvas.sample/.MainActivity").close()
        fun resumed(): Activity? {
            var activity: Activity? = null
            instrumentation.runOnMainSync {
                activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
            }
            return activity
        }
        fun awaitActivity(previous: Activity? = null): Activity {
            repeat(100) {
                val activity = resumed()
                if (activity != null && activity !== previous) return activity
                Thread.sleep(100)
            }
            error("sample activity failed to resume")
        }
        val scopes = mutableListOf<CoroutineScope>()
        fun captureScope() {
            instrumentation.waitForIdleSync()
            val live = LKanvasChart::class.java.getDeclaredField("liveCharts").apply { isAccessible = true }.get(null) as Map<*, *>
            val scopeField = ChartState::class.java.getDeclaredField("scope").apply { isAccessible = true }
            live.values.filterIsInstance<LKanvasChart>().forEach { scopes += scopeField.get(it.state) as CoroutineScope }
        }
        var activity = awaitActivity()
        captureScope()
        repeat(29) {
            val old = activity
            instrumentation.runOnMainSync { old.recreate() }
            activity = awaitActivity(old)
            captureScope()
        }
        instrumentation.runOnMainSync { activity.finish() }
        repeat(100) {
            if (activity.isDestroyed) return@repeat
            Thread.sleep(50)
        }
        instrumentation.waitForIdleSync()
        assertTrue("sample scopes must be observed", scopes.isNotEmpty())
        scopes.forEach { assertFalse("destroyed sample scope must be cancelled", it.isActive) }
        assertEquals("destroyed sample activities must not retain chart facades", before, chartCount())
    }
}
