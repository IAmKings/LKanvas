package io.lkanvas.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Explicit equivalent measurement; not StartupTimingMetric or process-start latency. */
@RunWith(AndroidJUnit4::class)
class WarmReadinessTest {
    @Test fun fiveRecreationsInTheSameProcessReachFullyDrawnWithin500ms() = inPortrait {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val launch = "am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10008000 -n io.lkanvas.sample/.MainActivity"
        fun ready(pid: String, previous: Long): Pair<Long, Double> {
            repeat(100) {
                val rows = Regex("created=(\\d+) readyMs=([\\d.]+)")
                    .findAll(device.executeShellCommand("logcat -d --pid=$pid -s LKanvas_READY")).toList()
                val last = rows.lastOrNull()
                if (last != null && last.groupValues[1].toLong() > previous) {
                    return last.groupValues[1].toLong() to last.groupValues[2].toDouble()
                }
                Thread.sleep(50)
            }
            error("reportFullyDrawn readiness log missing")
        }
        device.executeShellCommand("am force-stop io.lkanvas.sample")
        device.executeShellCommand(launch)
        val pid = device.executeShellCommand("pidof io.lkanvas.sample").trim()
        assertTrue(pid.isNotEmpty())
        var previous = ready(pid, 0).first
        val rows = JSONArray()
        repeat(5) {
            device.pressHome()
            device.executeShellCommand(launch)
            assertEquals("warm measurement must keep its process", pid, device.executeShellCommand("pidof io.lkanvas.sample").trim())
            val result = ready(pid, previous)
            previous = result.first
            rows.put(JSONObject().put("pid", pid).put("createdAtNanos", result.first).put("readyMs", result.second))
        }
        val output = JSONObject().put("method", "Activity.onCreate -> ReportDrawnWhen/reportFullyDrawn; same process; not StartupTimingMetric").put("runs", rows)
        File(instrumentation.targetContext.getExternalFilesDir("validation"), "warm-readiness.json").writeText(output.toString(2))
        repeat(rows.length()) { assertTrue("warm fully drawn must be <=500ms: $output", rows.getJSONObject(it).getDouble("readyMs") <= 500.0) }
    }
}
