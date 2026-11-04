package io.lkanvas.chart

import io.lkanvas.compose.draw.ChartText
import io.lkanvas.compose.state.ChartState
import io.lkanvas.core.data.Candle
import io.lkanvas.core.store.MarketKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * 日期 formatter 接线契约（0.16.0-beta02，复核文档 §6）：
 * - 公开三参 formatDate 契约保持；库在真实消费点传入场景（'xAxis'/'tooltip'）
 *   与模板 token（HH:mm / MM-DD / YYYY-MM-DD，随库默认规则演进）；
 * - formatter 未配置 → 调用点回退默认规则（含有效时区与既有标签缓存）；
 *   配置后 formatter 非空返回值即最终文案（公开类型契约）；
 * - legendData 默认时间文案消费有效时区（修复前跟随设备时区）；
 * - setFormatter 触发重绘版本 + 缓存身份变化；清除恢复默认；双图无串扰。
 */
class FormatterSceneWiringTest {

    private val ts = 1_700_000_000_000L // 2023-11-14 22:13:20 UTC

    private val dispatcher = UnconfinedTestDispatcher()
    private val originalTimeZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        TimeZone.setDefault(originalTimeZone)
    }

    @Before
    fun clearDeviceTz() {
        // JVM 层固定非上海默认时区，验证消费点不依赖设备时区
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
    }

    @Test
    fun `formatter receives scene and library template through facade`() {
        val state = ChartState()
        val chart = LKanvasChart.init(state)
        try {
            val seen = mutableListOf<Triple<Long, String?, String?>>()
            chart.setFormatter(
                Formatter(formatDate = { t, tpl, type -> seen += Triple(t, tpl, type); "$type#$tpl" }),
            )
            assertEquals("xAxis#HH:mm", state.formattedTimeLabel(ts, ChartState.TEMPLATE_TIME, ChartState.FORMAT_SCENE_X_AXIS))
            assertEquals("tooltip#MM-DD", state.formattedTimeLabel(ts, ChartState.TEMPLATE_DATE, ChartState.FORMAT_SCENE_TOOLTIP))
            assertEquals("crosshair#HH:mm", state.formattedTimeLabel(ts, ChartState.TEMPLATE_TIME, ChartState.FORMAT_SCENE_CROSSHAIR))
            org.junit.Assert.assertTrue("公开三参 type 不再恒 null", seen.all { it.third != null })
            assertEquals(ChartState.FORMAT_SCENE_X_AXIS, seen[0].third)
        } finally {
            LKanvasChart.dispose(chart)
        }
    }

    @Test
    fun `configured formatter owns the output for every template`() {
        // 公开契约：formatDate 返回非空 String —— 配置后 formatter 全权接管
        // （不感知库模板 token 语义）；回退默认仅发生在未配置时
        val state = ChartState()
        val chart = LKanvasChart.init(state)
        try {
            chart.setFormatter(Formatter(formatDate = { _, tpl, _ -> "<$tpl>" }))
            assertEquals("<HH:mm>", state.formattedTimeLabel(ts, ChartState.TEMPLATE_TIME, ChartState.FORMAT_SCENE_X_AXIS))
        } finally {
            LKanvasChart.dispose(chart)
        }
    }

    @Test
    fun `unconfigured state returns null for formatted label`() {
        val state = ChartState()
        assertNull(state.formattedTimeLabel(ts, ChartState.TEMPLATE_TIME, ChartState.FORMAT_SCENE_X_AXIS))
    }

    @Test
    fun `axis template mirrors default label rules`() {
        val tz = TimeZone.getTimeZone("Asia/Shanghai")
        val dayStart = 1_699_468_800_000L // 2023-11-13 00:00 Shanghai
        assertEquals(ChartState.TEMPLATE_DATE_YEAR, ChartText.axisTemplate(dayStart, null, 86_400_000L, tz))
        assertEquals(ChartState.TEMPLATE_TIME, ChartText.axisTemplate(dayStart + 60_000L, dayStart, 60_000L, tz))
        assertEquals(ChartState.TEMPLATE_DATE, ChartText.axisTemplate(dayStart + 86_400_000L, dayStart, 60_000L, tz))
    }

    @Test
    fun `legendData default time uses effective timezone not device default`() {
        val state = ChartState()
        runBlocking {
            state.reset(MarketKey("FMT", "1m"), (0 until 60).map {
                Candle(ts + it * 60_000L, 10.0, 11.0, 9.0, 10.0, 100.0)
            })
        }
        state.timezoneId = "Asia/Shanghai"
        val legend = state.legendData()
        val lastTs = ts + 59 * 60_000L // legendData 默认取末根
        assertEquals(
            ChartText.timeLabel(lastTs, 60_000L, java.util.TimeZone.getTimeZone("Asia/Shanghai")),
            legend?.timeText,
        )
        // 上海时刻 ≠ 洛杉矶时刻（默认设备时区）——证明消费的是有效时区
        assertNotEquals(
            ChartText.timeLabel(lastTs, 60_000L, java.util.TimeZone.getDefault()),
            legend?.timeText,
        )
    }

    @Test
    fun `setFormatter bumps revision and changes format config identity`() {
        val state = ChartState()
        val chart = LKanvasChart.init(state)
        try {
            val beforeRev = state.viewRevision
            val beforeCfg = System.identityHashCode(state.formatConfig)
            chart.setFormatter(Formatter(formatDate = { t, _, _ -> t.toString() }))
            org.junit.Assert.assertTrue("setFormatter 触发重绘版本递增", state.viewRevision > beforeRev)
            assertNotEquals(beforeCfg, System.identityHashCode(state.formatConfig))
            // 清除恢复默认
            chart.setFormatter(null)
            assertNull(state.formattedTimeLabel(ts, ChartState.TEMPLATE_TIME, ChartState.FORMAT_SCENE_X_AXIS))
        } finally {
            LKanvasChart.dispose(chart)
        }
    }

    @Test
    fun `two charts with different formatters do not cross talk`() {
        val a = ChartState()
        val b = ChartState()
        val fa = LKanvasChart.init(a)
        val fb = LKanvasChart.init(b)
        try {
            fa.setFormatter(Formatter(formatDate = { _, _, t -> "A:$t" }))
            fb.setFormatter(Formatter(formatDate = { _, _, t -> "B:$t" }))
            assertEquals("A:xAxis", a.formattedTimeLabel(ts, ChartState.TEMPLATE_TIME, ChartState.FORMAT_SCENE_X_AXIS))
            assertEquals("B:xAxis", b.formattedTimeLabel(ts, ChartState.TEMPLATE_TIME, ChartState.FORMAT_SCENE_X_AXIS))
        } finally {
            LKanvasChart.dispose(fa)
            LKanvasChart.dispose(fb)
        }
    }
}
