package io.lkanvas.benchmark

import android.graphics.Point
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real input and accessibility actions against the installed candidate (including R8 release). */
@RunWith(AndroidJUnit4::class)
class FinalAcceptanceUiTest {
    @Test fun periodButtonsChangeThePricePlotWithTheFeedPaused() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val output = File(instrumentation.targetContext.getExternalFilesDir("validation"), "periods").apply { mkdirs() }
        device.executeShellCommand("am force-stop io.lkanvas.sample")
        device.executeShellCommand("am start -W -n io.lkanvas.sample/.MainActivity --ez stress_mode false")
        val chart = device.findObject(UiSelector().descriptionContains("，高 "))
        assertTrue(chart.waitForExists(15000))
        device.findObject(UiSelector().text("更多")).click()
        val push = device.findObject(UiSelector().text("模拟行情推送"))
        assertTrue(push.waitForExists(5000))
        push.click()
        device.findObject(UiSelector().description("关闭面板")).click()
        assertTrue(device.findObject(UiSelector().text("模拟已暂停")).waitForExists(5000))
        fun pricePlotHash(): Int {
            val b = chart.bounds
            val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("screenshot unavailable")
            try {
                // 只取主图内部，排除周期按钮、图例、轴标签与时间标签。
                val w = (b.width() * 0.7).toInt()
                val h = (b.height() * 0.3).toInt()
                val pixels = IntArray(w * h)
                bitmap.getPixels(pixels, 0, w, b.left + b.width() / 20, b.top + b.height() / 5, w, h)
                return pixels.contentHashCode()
            } finally { bitmap.recycle() }
        }
        Thread.sleep(300)
        var before = pricePlotHash()
        val rows = mutableListOf("1m initial=$before")
        for (label in listOf("5m", "15m", "1h", "1d", "1m")) {
            device.findObject(UiSelector().text(label)).click()
            var after = before
            var stable = false
            for (attempt in 0 until 150) {
                Thread.sleep(100)
                if (chart.exists()) after = pricePlotHash()
                if (after != before && chart.exists()) {
                    Thread.sleep(300)
                    if (pricePlotHash() == after) { stable = true; break }
                }
            }
            assertNotEquals("$label must change the price plot while live updates are paused", before, after)
            assertTrue("$label must reach a stable rendered price plot", stable)
            device.takeScreenshot(File(output, "$label.png"))
            rows += "$label plotHash=$after"
            before = after
        }
        File(output, "plot-hashes.txt").writeText(rows.joinToString("\n"))
        device.findObject(UiSelector().text("更多")).click()
        device.findObject(UiSelector().text("模拟行情推送")).click()
        device.findObject(UiSelector().description("关闭面板")).click()
    }

    @Test fun pinchAccessibilityAndConfigurationMatrix() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val output = File(instrumentation.targetContext.getExternalFilesDir("validation"), "ui").apply { mkdirs() }
        val originalScale = device.executeShellCommand("settings get system font_scale").trim()
        val originalRotation = device.executeShellCommand("settings get system user_rotation").trim()
        val originalAuto = device.executeShellCommand("settings get system accelerometer_rotation").trim()
        fun launch() {
            device.executeShellCommand("am force-stop io.lkanvas.sample")
            device.executeShellCommand("am start -W -n io.lkanvas.sample/.MainActivity --ez stress_mode true --ei stress_candles 1000 --ei stress_indicators 1 --ei stress_overlays 0")
            assertTrue("chart must expose loaded OHLC", device.findObject(UiSelector().descriptionContains("，高 ")).waitForExists(15000))
            Thread.sleep(500)
        }
        fun chartNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.contentDescription?.contains("，高 ") == true) return node
            for (i in 0 until node.childCount) chartNode(node.getChild(i))?.let { return it }
            return null
        }
        fun hash(): Int {
            val bounds = device.findObject(UiSelector().descriptionContains("，高 ")).bounds
            val image = instrumentation.uiAutomation.takeScreenshot() ?: error("screenshot unavailable")
            val pixels = IntArray(bounds.width() * bounds.height())
            image.getPixels(pixels, 0, bounds.width(), bounds.left, bounds.top, bounds.width(), bounds.height())
            image.recycle()
            return pixels.contentHashCode()
        }
        try {
            device.setOrientationNatural()
            device.executeShellCommand("settings put system font_scale 1.0")
            launch()
            val chart = device.findObject(UiSelector().descriptionContains("，高 "))
            val b = chart.bounds
            val x = b.centerX(); val y = b.top + b.height() / 5
            val before = hash()
            assertTrue(chart.performTwoPointerGesture(Point(x - 80, y), Point(x + 80, y), Point(x - 250, y), Point(x + 250, y), 30))
            Thread.sleep(500)
            val enlarged = hash()
            assertNotEquals("pinch out must change the static chart", before, enlarged)
            device.takeScreenshot(File(output, "pinch-out.png"))
            assertTrue(chart.performTwoPointerGesture(Point(x - 250, y), Point(x + 250, y), Point(x - 80, y), Point(x + 80, y), 30))
            Thread.sleep(500)
            assertNotEquals("pinch in must change the chart", enlarged, hash())
            val beforeTap = hash()
            device.click(x, y)
            Thread.sleep(300)
            val afterTap = hash()
            assertNotEquals("short tap must display the crosshair after release", beforeTap, afterTap)
            Thread.sleep(300)
            assertEquals("short tap selection must persist", afterTap, hash())
            device.takeScreenshot(File(output, "tap-release.png"))
            device.click(x + 180, y + 100)
            Thread.sleep(300)
            assertEquals("distant touch dismisses the retained selection", beforeTap, hash())
            val downTime = SystemClock.uptimeMillis()
            fun touch(action: Int) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x.toFloat(), y.toFloat(), 0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
            }
            val beforeLongPress = hash()
            touch(MotionEvent.ACTION_DOWN)
            try {
                Thread.sleep(800)
                assertNotEquals("long press must display the crosshair", beforeLongPress, hash())
                device.takeScreenshot(File(output, "long-press.png"))
            } finally { touch(MotionEvent.ACTION_UP) }
            val node = chartNode(instrumentation.uiAutomation.rootInActiveWindow) ?: error("chart semantics missing")
            val labels = node.actionList.mapNotNull { it.label?.toString() }
            File(output, "accessibility-actions.txt").writeText(labels.joinToString("\n"))
            assertTrue(labels.containsAll(listOf("上一根", "下一根", "放大", "缩小", "删除选中画线", "选中下一画线")))
            repeat(2) {
                val current = chartNode(instrumentation.uiAutomation.rootInActiveWindow) ?: error("chart missing")
                val previous = current.actionList.first { it.label?.toString() == "上一根" }
                val oldDescription = current.contentDescription.toString()
                assertTrue(current.performAction(previous.id))
                var changed = false
                for (attempt in 0 until 50) {
                    Thread.sleep(100)
                    if (oldDescription != chartNode(instrumentation.uiAutomation.rootInActiveWindow)?.contentDescription.toString()) {
                        changed = true
                        break
                    }
                }
                assertTrue("each consecutive accessibility action must update its summary", changed)
            }
            File(output, "interaction.txt").writeText("pinch out/in: PASS\nshort tap/release/persistence/dismiss: PASS\nlong press: PASS\nconsecutive previous candle accessibility actions: PASS\n")
            for (scale in listOf("1.0", "1.3", "2.0")) {
                device.executeShellCommand("settings put system font_scale $scale")
                for (landscape in listOf(false, true)) {
                    device.executeShellCommand("wm user-rotation lock ${if (landscape) 1 else 0}")
                    launch()
                    var captured = false
                    for (attempt in 0 until 50) {
                        device.executeShellCommand("wm user-rotation lock ${if (landscape) 1 else 0}")
                        Thread.sleep(1500)
                        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("screenshot unavailable")
                        val correctOrientation = if (landscape) bitmap.width > bitmap.height else bitmap.width < bitmap.height
                        if (correctOrientation) {
                            val bounds = device.findObject(UiSelector().descriptionContains("，高 ")).bounds
                            assertTrue("chart must have nonempty layout", bounds.width() > 0 && bounds.height() > 0)
                            java.io.FileOutputStream(File(output, "font-$scale-${if (landscape) "landscape" else "portrait"}.png")).use {
                                assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                            }
                            captured = true
                        }
                        bitmap.recycle()
                        if (captured) break
                        Thread.sleep(100)
                    }
                    assertTrue("requested orientation must match screenshot dimensions: font=$scale landscape=$landscape", captured)
                }
            }
        } finally {
            device.unfreezeRotation()
            device.executeShellCommand("settings put system font_scale $originalScale")
            device.executeShellCommand("settings put system user_rotation $originalRotation")
            device.executeShellCommand("settings put system accelerometer_rotation $originalAuto")
        }
    }
}
