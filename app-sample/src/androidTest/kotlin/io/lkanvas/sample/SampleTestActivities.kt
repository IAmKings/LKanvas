package io.lkanvas.sample

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertTrue

/** 清理手动打开的页面与测试留下的后台页面，生命周期基线只统计本用例。 */
internal fun closeSampleActivities() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    var closing = emptyList<MainActivity>()
    instrumentation.runOnMainSync {
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        closing = Stage.values().flatMap { monitor.getActivitiesInStage(it) }
            .filterIsInstance<MainActivity>().distinct().filterNot { it.isDestroyed }
        closing.forEach { it.finish() }
    }
    var destroyed = false
    for (attempt in 0 until 100) {
        instrumentation.runOnMainSync { destroyed = closing.all { it.isDestroyed } }
        if (destroyed) break
        Thread.sleep(25)
    }
    instrumentation.waitForIdleSync()
    assertTrue("sample activities must finish before the next test", destroyed)
}
