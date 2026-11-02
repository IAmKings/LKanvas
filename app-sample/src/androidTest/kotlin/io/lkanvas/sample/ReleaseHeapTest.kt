package io.lkanvas.sample

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.ref.WeakReference

/** Same-signed instrumentation dumps its own target heap; no debug/profileable APK modification. */
@RunWith(AndroidJUnit4::class)
class ReleaseHeapTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val outputDir get() = instrumentation.targetContext.getExternalFilesDir("validation")!!
    // Read the exact APK's mapping instead of retaining library names or weakening R8 rules.
    private val names by lazy { JSONObject(File(outputDir, "r8-probe.json").readText()) }
    private fun field(owner: String, name: String, receiver: Any?): Any? =
        Class.forName(names.getJSONObject(owner).getString("class")).getDeclaredField(names.getJSONObject(owner).getString(name)).let {
            it.isAccessible = true
            it.get(receiver)
        }
    private fun liveCharts() = field("chart", "liveCharts", null) as Map<*, *>
    private fun snapshot(state: Any): Any = Class.forName(names.getJSONObject("state").getString("class"))
        .getDeclaredMethod(names.getJSONObject("state").getString("snapshot")).invoke(state)!!
    private fun switchMarkets(watched: MutableList<WeakReference<Any>>) {
        val device = UiDevice.getInstance(instrumentation)
        assertTrue(device.findObject(UiSelector().description("切换品种")).waitForExists(15_000))
        val rows = JSONArray()
        var lastSession = -1L
        var initialChart: Any? = null
        instrumentation.runOnMainSync { initialChart = liveCharts().values.single() }
        repeat(100) { index ->
            var previous: Any? = null
            instrumentation.runOnMainSync {
                assertEquals(1, liveCharts().size)
                assertSame("market changes must reuse the same facade", initialChart, liveCharts().values.single())
                val state = field("chart", "state", liveCharts().values.single())!!
                previous = snapshot(state)
                lastSession = (field("snapshot", "sessionId", previous) as Long)
                watched += WeakReference(previous!!)
                watched += WeakReference(field("snapshot", "candles", previous)!!)
            }
            val button = device.findObject(UiSelector().description("切换品种")).bounds
            assertTrue(device.click(button.centerX(), button.centerY()))
            var matched = false
            val expected = listOf("DEMO-B", "DEMO-C", "DEMO")[index % 3]
            for (attempt in 0 until 100) {
                instrumentation.runOnMainSync {
                    val state = field("chart", "state", liveCharts().values.single())!!
                    assertSame(initialChart, liveCharts().values.single())
                    val current = snapshot(state)
                    val session = field("snapshot", "sessionId", current) as Long
                    val market = field("snapshot", "marketKey", current)
                    val symbol = market?.let { field("market", "symbol", it) }
                    val candles = field("snapshot", "candles", current) as List<*>
                    matched = symbol == expected && session > lastSession && candles.size >= 500 && current !== previous
                    if (matched) rows.put(JSONObject().put("switch", index + 1).put("symbol", symbol)
                        .put("previousSession", lastSession).put("session", session).put("candles", candles.size))
                }
                if (matched) break
                Thread.sleep(50)
            }
            assertTrue("switch ${index + 1} must reload $expected into a new session", matched)
            previous = null
        }
        assertEquals(100, rows.length())
        File(outputDir, "release-market-switches.json").writeText(rows.toString(2))
    }
    private fun resumed(): Activity? {
        var value: Activity? = null
        instrumentation.runOnMainSync {
            value = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
        }
        return value
    }
    private fun awaitActivity(previous: Activity? = null): Activity {
        repeat(100) { resumed()?.let { if (it !== previous) return it }; Thread.sleep(100) }
        error("sample Activity did not resume")
    }
    private fun exercise(): List<WeakReference<Any>> {
        instrumentation.uiAutomation.executeShellCommand("am start -W -n io.lkanvas.sample/.MainActivity").close()
        val watched = mutableListOf<WeakReference<Any>>()
        var activity = awaitActivity()
        repeat(30) { index ->
            File(outputDir, "release-progress.txt").appendText("cycle=$index\n")
            instrumentation.waitForIdleSync()
            watched += WeakReference(activity)
            instrumentation.runOnMainSync {
                assertEquals(1, liveCharts().size)
                val chart = liveCharts().values.single()!!
                watched += WeakReference(chart)
                watched += WeakReference(field("chart", "state", chart)!!)
            }
            if (index == 29) {
                switchMarkets(watched)
                instrumentation.runOnMainSync { activity.finish() }
            }
            else {
                val previous = activity
                instrumentation.runOnMainSync { previous.recreate() }
                activity = awaitActivity(previous)
            }
        }
        repeat(100) { if (!activity.isDestroyed) Thread.sleep(50) }
        assertTrue(activity.isDestroyed)
        return watched
    }
    @Test(timeout = 300000) fun exactNonDebuggableReleaseHasHeapEvidenceAfterThirtyDisposalsAndHundredMarkets() {
        org.junit.Assume.assumeTrue("release-only heap probe; explicit non-debuggable candidate required",
            instrumentation.targetContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0)
        val watched = exercise()
        instrumentation.waitForIdleSync()
        repeat(5) { Runtime.getRuntime().gc(); System.runFinalization(); Thread.sleep(500) }
        assertEquals("facade registry after disposal", 0, liveCharts().size)
        val output = File(outputDir, "release-disposed.hprof")
        Debug.dumpHprofData(output.absolutePath)
        File(output.parentFile, "release-disposed.txt").writeText("variant=non-debuggable release; cycles=30; markets=100; watched=${watched.size}; survivingWeak=${watched.count { it.get() != null }}; registryAfter=${liveCharts().size}\n")
        assertEquals("all disposed targets and old data must release", 0, watched.count { it.get() != null })
        assertTrue(output.length() > 0)
    }
}
