package io.lkanvas.benchmark

import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Measurement only: host-side event-id/frame-id joins must prove complete input-to-present samples. */
@RunWith(AndroidJUnit4::class)
class InputLatencyBenchmark {
    @Test fun normalInput() = measure("normal", 1000, 1, 0)
    @Test fun stressInput() = measure("stress", 50000, 10, 100)

    @OptIn(androidx.benchmark.perfetto.ExperimentalPerfettoCaptureApi::class)
    private fun measure(name: String, candles: Int, indicators: Int, overlays: Int) = inPortrait {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val output = File(instrumentation.targetContext.getExternalFilesDir("validation"), "input-$name").apply { mkdirs() }
        device.executeShellCommand("am force-stop io.lkanvas.sample")
        device.executeShellCommand("am start -W -n io.lkanvas.sample/.MainActivity --ez stress_mode true --ei stress_candles $candles --ei stress_indicators $indicators --ei stress_overlays $overlays")
        Thread.sleep(22000)
        val pid = device.executeShellCommand("pidof io.lkanvas.sample").trim()
        check(pid.isNotEmpty())
        val bounds = device.findObject(UiSelector().descriptionContains("，高 ")).bounds
        val config = File(output, "trace.pbtxt")
        config.writeText("duration_ms: 120000\n" + WarmStartupBenchmark.CONFIG.text.replace("size_kb: 65536", "size_kb: 131072").replace(
            "atrace_categories: \"gfx\"", "atrace_categories: \"input\" atrace_categories: \"gfx\"",
        ))
        val tracePath = "/data/misc/perfetto-traces/lkanvas-input-$name.perfetto-trace"
        val tracePid = device.startFrameTrace(config, tracePath)
        val samples = JSONArray()
        fun inject(action: Int, x: Float, y: Float, down: Long, event: Long): Boolean {
            val motion = MotionEvent.obtain(down, event, action, x, y, 0).apply { source = android.view.InputDevice.SOURCE_TOUCHSCREEN }
            return try { instrumentation.uiAutomation.injectInputEvent(motion, true) } finally { motion.recycle() }
        }
        try {
            device.executeShellCommand("dumpsys gfxinfo io.lkanvas.sample reset")
            repeat(100) { index ->
                check(device.executeShellCommand("pidof io.lkanvas.sample").trim() == pid)
                val direction = if (index % 2 == 0) 1 else -1
                val x = bounds.left + bounds.width() * 0.45f
                val y = bounds.top + bounds.height() * 0.25f
                val down = SystemClock.uptimeMillis()
                check(inject(MotionEvent.ACTION_DOWN, x, y, down, down))
                try {
                    Thread.sleep(50)
                    check(inject(MotionEvent.ACTION_MOVE, x + direction * 60, y, down, SystemClock.uptimeMillis()))
                    Thread.sleep(100)
                    // Android assigns the first gesture frame to DOWN. Flush one scrolling frame before timing MOVE.
                    check(inject(MotionEvent.ACTION_MOVE, x + direction * 90, y, down, SystemClock.uptimeMillis()))
                    Thread.sleep(150)
                    val eventTime = SystemClock.uptimeMillis()
                    val monoStart = System.nanoTime()
                    val bootStart = SystemClock.elapsedRealtimeNanos()
                    check(inject(MotionEvent.ACTION_MOVE, x + direction * 120, y, down, eventTime))
                    val monoEnd = System.nanoTime()
                    samples.put(JSONObject().put("sample", index).put("pid", pid).put("eventTimeNanos", eventTime * 1000000)
                        .put("injectStartMono", monoStart).put("injectEndMono", monoEnd).put("injectStartBoot", bootStart))
                    Thread.sleep(200)
                } finally { check(inject(MotionEvent.ACTION_CANCEL, x + direction * 120, y, down, SystemClock.uptimeMillis())) }
                Thread.sleep(50)
                if (index % 10 == 9) File(output, "frames-$index.txt").writeText(device.executeShellCommand("dumpsys gfxinfo io.lkanvas.sample framestats"))
            }
            // Drain deferred HWUI presentation reports; these untimed gestures are excluded by exact event id.
            val y = bounds.top + bounds.height() / 4
            device.swipe(bounds.left + bounds.width() / 3, y, bounds.left + bounds.width() * 2 / 3, y, 25)
            device.swipe(bounds.left + bounds.width() * 2 / 3, y, bounds.left + bounds.width() / 3, y, 25)
            Thread.sleep(1500)
            File(output, "frames-final.txt").writeText(device.executeShellCommand("dumpsys gfxinfo io.lkanvas.sample framestats"))
        } finally {
            device.executeShellCommand("kill -TERM $tracePid")
            Thread.sleep(1500)
            File(output, "samples.json").writeText(JSONObject().put("method", "synthetic MotionEvent timestamp -> actual presentation; event/frame joins required")
                .put("load", JSONArray(listOf(candles, indicators, overlays))).put("samples", samples).put("trace", tracePath).toString(2))
        }
        println("INPUT_MEASUREMENT_ONLY $name samples=${samples.length()}; host must validate IDs, clocks, complete frames and latency gates")
        check(samples.length() == 100)
    }
}
