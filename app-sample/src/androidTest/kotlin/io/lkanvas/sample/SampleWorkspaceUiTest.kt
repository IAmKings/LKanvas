package io.lkanvas.sample

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.activity.compose.setContent
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.*
import io.lkanvas.core.data.Candle
import io.lkanvas.core.store.MarketKey
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.UiScrollable
import androidx.compose.ui.graphics.toArgb
import io.lkanvas.chart.LKanvasChart
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.theme.MainChartType
import io.lkanvas.core.axis.YAxisSpec
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SampleWorkspaceUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Before fun launch() {
        closeSampleActivities()
        shell("am start -W -n io.lkanvas.sample/.MainActivity")
        assertTrue(device.findObject(UiSelector().descriptionContains("，高 ")).waitForExists(15_000))
        device.findObject(UiSelector().description("关闭面板")).let { if (it.exists()) it.click() }
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

    @After fun closeActivity() {
        closeSampleActivities()
    }

    private fun tap(text: String) {
        val target = device.findObject(UiSelector().text(text))
        try {
            if (!target.waitForExists(2000)) {
                val scroll = UiScrollable(UiSelector().scrollable(true)).setAsVerticalList()
                assertTrue("scroll container ready for $text", scroll.waitForExists(5000))
                var attempts = 0
                // 本组操作顺序向下遍历面板；scrollIntoView 的回顶手势会影响底部面板。
                while (!target.waitForExists(300) && attempts++ < 12) {
                    assertTrue("panel remains open for $text", device.findObject(UiSelector().description("关闭面板")).exists())
                    if (!scroll.scrollForward()) break
                }
            }
            assertTrue("control visible: $text", target.waitForExists(5000))
            target.click()
        } catch (error: Throwable) {
            runCatching {
                val evidence = File(instrumentation.targetContext.getExternalFilesDir("validation"), "workspace-scroll").apply { mkdirs() }
                device.dumpWindowHierarchy(File(evidence, "$text-failure.xml"))
                device.takeScreenshot(File(evidence, "$text-failure.png"))
            }
            throw error
        }
    }
    private fun chart(): LKanvasChart = (LKanvasChart::class.java.getDeclaredField("liveCharts").apply { isAccessible = true }.get(null) as Map<*, *>).values.filterIsInstance<LKanvasChart>().last()

    @Test fun periodSelectionChangesRealTimestampsAndThemeCoversTheWorkspace() {
        var previous = emptyList<Double>()
        instrumentation.runOnMainSync { previous = chart().state.dataSnapshot.candles.take(400).map { it.close } }
        for ((label, period) in samplePeriods.drop(1) + samplePeriods.take(1)) {
            tap(label)
            var matched = false
            for (attempt in 0 until 300) {
                instrumentation.runOnMainSync {
                    val bars = chart().state.dataSnapshot.candles
                    matched = bars.size >= 500 && bars.zipWithNext().all { (a, b) -> b.timestamp - a.timestamp == period.toMillis() }
                    if (matched) {
                        val current = bars.take(400).map { it.close }
                        assertTrue("$label must change prices, not only timestamps", current != previous)
                        previous = current
                    }
                }
                if (matched) break
                Thread.sleep(50)
            }
            assertTrue("$label must load the selected period", matched)
        }
        device.findObject(UiSelector().description("切换浅色主题")).click()
        assertTrue(device.findObject(UiSelector().description("切换深色主题")).waitForExists(5_000))
        tap("指标")
        assertTrue(device.findObject(UiSelector().text("主图叠加")).waitForExists(5_000))
        tap("EMA")
        instrumentation.runOnMainSync { assertTrue(chart().getIndicators().any { it.definition.name == "EMA" }) }
        device.findObject(UiSelector().description("关闭面板")).click()
        device.findObject(UiSelector().description("切换深色主题")).click()
        tap("1m")
    }

    @Test fun drawingAndDiagnosticControlsOperateWithoutShrinkingTheChart() {
        val before = device.findObject(UiSelector().descriptionContains("，高 ")).bounds
        tap("画线")
        assertTrue(device.findObject(UiSelector().text("segment")).waitForExists(5_000))
        tap("线段")
        assertTrue(device.findObject(UiSelector().textContains("已取 0 点")).waitForExists(5_000))
        tap("取消")
        tap("画线")
        tap("线段")
        val drawingBounds = device.findObject(UiSelector().descriptionContains("，高 ")).bounds
        device.click(drawingBounds.left + drawingBounds.width() / 4, drawingBounds.top + drawingBounds.height() / 4)
        device.click(drawingBounds.left + drawingBounds.width() / 2, drawingBounds.top + drawingBounds.height() / 3)
        instrumentation.runOnMainSync {
            assertNull(chart().state.overlays.creatingId)
            assertEquals(2, chart().state.overlays.all().last().points.size)
        }
        device.findObject(UiSelector().description("打开诊断")).click()
        assertTrue(device.findObject(UiSelector().text("诊断采集")).waitForExists(5_000))
        tap("诊断采集")
        assertTrue(device.findObject(UiSelector().text("采集已开启")).waitForExists(5_000))
        tap("导出日志")
        assertTrue(device.findObject(UiSelector().textContains("已导出到应用缓存")).waitForExists(5_000))
        device.findObject(UiSelector().description("关闭面板")).click()
        assertEquals(before, device.findObject(UiSelector().descriptionContains("，高 ")).bounds)
    }

    @Test fun chartSettingsAndAdvancedDemosPreserveDefaultIndicatorsAndExportTheTheme() {
        tap("图表")
        tap("面积图")
        instrumentation.runOnMainSync { assertEquals(MainChartType.AREA, chart().state.mainChartType) }
        tap("百分比")
        instrumentation.runOnMainSync { assertTrue(chart().state.mainAxisSpec is YAxisSpec.Percentage) }
        device.findObject(UiSelector().description("关闭面板")).click()
        tap("更多")
        var originalMa = emptySet<String>()
        instrumentation.runOnMainSync { originalMa = chart().getIndicators(ChartState.IndicatorFilter(name = "MA")).map { it.id }.toSet() }
        tap("独立 MA10 坐标轴")
        instrumentation.runOnMainSync { assertEquals(originalMa.size + 1, chart().getIndicators(ChartState.IndicatorFilter(name = "MA")).size) }
        tap("独立 MA10 坐标轴")
        instrumentation.runOnMainSync { assertEquals(originalMa, chart().getIndicators(ChartState.IndicatorFilter(name = "MA")).map { it.id }.toSet()) }
        tap("独立 BOLL 副图")
        instrumentation.runOnMainSync { assertTrue(chart().state.extraPanes.value.contains("pane_boll")) }
        tap("独立 BOLL 副图")
        instrumentation.runOnMainSync { assertFalse(chart().state.extraPanes.value.contains("pane_boll")) }
        tap("导出图表 PNG · 1080 × 1600")
        assertTrue(device.findObject(UiSelector().textContains("PNG 已导出到应用缓存")).waitForExists(5_000))
        val output = File(instrumentation.targetContext.cacheDir, "lkanvas-export.png")
        val bitmap = android.graphics.BitmapFactory.decodeFile(output.absolutePath)
        assertNotNull(bitmap)
        assertEquals(1080, bitmap.width)
        assertEquals(1600, bitmap.height)
        instrumentation.runOnMainSync { // 左上角可能被网格/面积图抗锯齿覆盖；右下 gutter 留白才是纯背景。
            assertEquals(chart().resolveTheme().background.toArgb(), bitmap.getPixel(bitmap.width - 1, bitmap.height - 1)) }
        bitmap.recycle()
        instrumentation.runOnMainSync {
            val styles = chart().getStyles()
            chart().setStyles(styles.copy(candle = styles.candle.copy(type = "candle_solid")))
            chart().state.mainAxisSpec = YAxisSpec.Normal
            chart().state.notifyViewportChanged()
        }
    }
    @Test fun sameCountQuotesUpdateTheHeaderAndChartWithoutChangingBounds() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var owned: LKanvasChart? = null
        lateinit var state: ChartState
        try {
            runBlocking(Dispatchers.Main) {
                state = ChartState(scope)
                val facade = LKanvasChart.init(state)
                owned = facade
                facade.createIndicator("MA", paneId = "main", isStack = true)
                facade.createIndicator("VOL", paneId = "vol")
                facade.createIndicator("MACD", paneId = "macd")
                state.reset(MarketKey("QUOTE", "1m"), (0 until 300).map { Candle(it * 60_000L, 100.0, 102.0, 99.0, 100.0, 1000.0) })
                val feed = LocalFeed(scope)
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                activity.setContent { SampleTheme(true) { SampleWorkspace(facade, feed, true, 0, null, {}, {}, {}) } }
            }
            val chart = device.findObject(UiSelector().descriptionContains("，高 "))
            assertTrue(chart.waitForExists(10000))
            val bounds = chart.bounds
            for (price in listOf(123.45, 87.65)) {
                runBlocking(Dispatchers.Main) {
                    state.upsertLatest(state.dataSnapshot.candles.last().copy(high = 130.0, low = 80.0, close = price))
                    assertEquals("quote replaces the existing candle", 300, state.dataSnapshot.candles.size)
                }
                val value = java.lang.String.format(java.util.Locale.US, "%.2f", price)
                assertTrue("header must display the new quote $value", device.findObject(UiSelector().textContains(value)).waitForExists(10000))
                assertTrue("chart semantics must display the new quote $value", device.findObject(UiSelector().descriptionContains(value)).waitForExists(5000))
                assertEquals("quotes must not resize the chart", bounds, chart.bounds)
            }
        } finally {
            runBlocking(Dispatchers.Main) { owned?.let { LKanvasChart.dispose(it) } }
            scope.cancel()
        }
    }

}
