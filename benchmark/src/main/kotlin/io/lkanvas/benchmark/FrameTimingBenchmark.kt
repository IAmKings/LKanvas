package io.lkanvas.benchmark

import android.content.Intent
import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiSelector
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Strict AndroidX metric with explicit process discovery; no vendor frame-duration substitution. */
@RunWith(AndroidJUnit4::class)
class FrameTimingBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test(timeout = 180000) fun normalScroll() = scroll(1000, 1, 0)
    @Test(timeout = 180000) fun realtimeScroll() = scroll(null)
    @Test(timeout = 180000) fun stressScroll() = scroll()
    @Test(timeout = 180000) fun stressScrollFollowingDisplay() = scroll(followDisplay = true)

    @OptIn(androidx.benchmark.ExperimentalBenchmarkConfigApi::class, androidx.benchmark.perfetto.ExperimentalPerfettoCaptureApi::class)
    private fun scroll(candles: Int? = 50000, indicators: Int = 10, overlays: Int = 100, followDisplay: Boolean = false) = inPortrait {
        rule.measureRepeated(
            packageName = StartupBenchmark.PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.WARM,
            iterations = 1,
            experimentalConfig = androidx.benchmark.ExperimentalConfig(perfettoConfig = WarmStartupBenchmark.CONFIG),
            setupBlock = {
                startActivityAndWait(Intent().apply {
                    setClassName(StartupBenchmark.PACKAGE, "io.lkanvas.sample.MainActivity")
                    putExtra("follow_display_refresh_rate", followDisplay)
                    if (candles != null) {
                        putExtra("stress_mode", true)
                        putExtra("stress_candles", candles)
                        putExtra("stress_indicators", indicators)
                        putExtra("stress_overlays", overlays)
                    }
                })
                Thread.sleep(22000)
                check(device.findObject(UiSelector().descriptionContains("，高 ")).exists()) { "loaded chart semantics missing" }
                if (candles != null) check(device.findObject(UiSelector().textContains("$candles 根")).exists()) { "fixture candle count differs from requested load" }
                val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("validation"), "frame-environment").apply { mkdirs() }
                File(output, "${candles ?: "live"}-$followDisplay.txt").writeText(
                    "pid=${device.executeShellCommand("pidof ${StartupBenchmark.PACKAGE}").trim()}\n" +
                        device.executeShellCommand("dumpsys display") + "\n" + device.executeShellCommand("dumpsys window windows"),
                )
            },
        ) {
            val w = device.displayWidth
            val y = (device.displayHeight * 0.4).toInt()
            repeat(10) { device.swipe((w * 0.2).toInt(), y, (w * 0.8).toInt(), y, 25) }
            repeat(10) { device.swipe((w * 0.8).toInt(), y, (w * 0.2).toInt(), y, 25) }
        }
    }
}
