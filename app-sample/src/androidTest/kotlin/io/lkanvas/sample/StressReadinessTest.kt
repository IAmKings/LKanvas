package io.lkanvas.sample

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StressReadinessTest {
    @Test fun staticLoadReportsFullyDrawnAfterItsOverlaysAreCreated() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        closeSampleActivities()
        try {
            device.executeShellCommand("am start -W -n io.lkanvas.sample/.MainActivity --ez stress_mode true --ei stress_candles 5000 --ei stress_indicators 3 --ei stress_overlays 10")
            assertTrue(device.findObject(UiSelector().textContains("5000 根")).waitForExists(15_000))
            assertTrue(device.findObject(UiSelector().descriptionContains("画线共 10 条")).waitForExists(5_000))
            var reported = false
            for (attempt in 0 until 100) {
                instrumentation.runOnMainSync {
                    reported = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<MainActivity>().any { it.fullyDrawnReporter.isFullyDrawnReported }
                }
                if (reported) break
                Thread.sleep(50)
            }
            assertTrue("static stress mode must report readiness without waiting for another market tick", reported)
        } finally { closeSampleActivities() }
    }
}
