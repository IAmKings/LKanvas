package io.lkanvas.chart

import io.lkanvas.compose.state.ChartState
import io.lkanvas.core.overlay.OverlayPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 多轴 API 与实例字段暴露测试（CH-053~057、IND-002/003/017、OVL-020/022/026/031/036/039）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AxisAndInstanceFieldsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newChart(): LKanvasChart = LKanvasChart.init(ChartState())

    // ---------- CH-053 createYAxis ----------

    @Test
    fun createYAxis_generatesIdAndRegisters() {
        val chart = newChart()
        val id = chart.createYAxis(YAxisOverride(name = "price_axis"))
        assertNotNull(id)
        assertTrue(id.startsWith("yAxis_"))
        val axes = chart.getYAxes(YAxisFilter())
        assertEquals(2, axes.size) // 主轴 + 新轴
        assertTrue(axes.any { it.id == id && it.name == "price_axis" && !it.isDefault })
    }

    @Test
    fun createYAxis_explicitIdIsIdempotent() {
        val chart = newChart()
        val id = chart.createYAxis(YAxisOverride(id = "ax1", paneId = "vol"))
        assertEquals("ax1", id)
        assertEquals("ax1", chart.createYAxis(YAxisOverride(id = "ax1", paneId = "vol")))
        assertEquals(2, chart.getYAxes(YAxisFilter()).size)
    }

    @Test
    fun createYAxis_rejectsXAxisPane() {
        val chart = newChart()
        assertNull(chart.createYAxis(YAxisOverride(id = "x", paneId = "candle_x")))
    }

    // ---------- CH-055 getYAxes 过滤 ----------

    @Test
    fun getYAxes_filtersByPaneIdNameId() {
        val chart = newChart()
        chart.createYAxis(YAxisOverride(id = "a1", name = "left", paneId = "vol"))
        chart.createYAxis(YAxisOverride(id = "a2", name = "right", paneId = "macd"))
        assertEquals(1, chart.getYAxes(YAxisFilter(paneId = "vol")).size)
        assertEquals(1, chart.getYAxes(YAxisFilter(name = "right")).size)
        assertEquals("a2", chart.getYAxes(YAxisFilter(id = "a2")).first().id)
        assertEquals(3, chart.getYAxes(YAxisFilter()).size)
    }

    // ---------- CH-054 removeYAxis 保护语义 ----------

    @Test
    fun removeYAxis_protectsDefaultAndBound() {
        val chart = newChart()
        // 主 pane 默认轴不可删
        assertFalse(chart.removeYAxis(YAxisFilter(id = "main")))
        // 未绑定指标的自定义轴可删
        val id = chart.createYAxis(YAxisOverride(id = "free"))!!
        assertTrue(chart.removeYAxis(YAxisFilter(id = id)))
        assertEquals(1, chart.getYAxes(YAxisFilter()).size)
        // 绑定指标的轴不可删
        val boundId = chart.createYAxis(YAxisOverride(id = "bound"))!!
        chart.createIndicator("MA", yAxisId = boundId)
        assertFalse(chart.removeYAxis(YAxisFilter(id = boundId)))
        // filter 双空直接拒绝
        assertFalse(chart.removeYAxis(YAxisFilter()))
    }

    // ---------- CH-056/057 override ----------

    @Test
    fun overrideYAxis_overridesOnlyProvidedFields() {
        val chart = newChart()
        val id = chart.createYAxis(YAxisOverride(id = "ax", position = AxisPosition.RIGHT))!!
        chart.overrideYAxis(YAxisOverride(id = id, position = AxisPosition.LEFT, reverse = true))
        val axis = chart.getYAxes(YAxisFilter(id = id)).first()
        assertEquals(AxisPosition.LEFT, axis.position)
        assertTrue(axis.reverse)
        assertFalse(axis.inside)
    }

    @Test
    fun overrideXAxis_mergesFields() {
        val chart = newChart()
        chart.overrideXAxis(XAxisOverride(reverse = true, position = AxisPosition.LEFT))
        assertTrue(chart.xAxisState.reverse == true)
        assertEquals(AxisPosition.LEFT, chart.xAxisState.position)
        chart.overrideXAxis(XAxisOverride(inside = true))
        assertTrue(chart.xAxisState.reverse == true) // 未提供的字段保持
        assertTrue(chart.xAxisState.inside == true)
    }

    // ---------- IND-002/003/017 实例字段 ----------

    @Test
    fun indicatorInstance_exposesPaneYAxisStyles() {
        val chart = newChart()
        val id = chart.createIndicator("MA", paneId = "main", yAxisId = "ax1", styles = mapOf("color" to "#F00"))
        assertNotNull(id)
        val inst = chart.getIndicatorByInstanceId(id)!!
        assertEquals("main", inst.paneId)
        assertEquals("ax1", inst.yAxisId)
        assertNotNull(inst.styles)
    }

    // ---------- OVL 实例字段 ----------

    @Test
    fun overlayInfo_exposesGroupCurrentStepSensitivity() {
        val chart = newChart()
        val id = chart.createOverlay(
            OverlayCreate(
                name = "segment",
                groupId = "g1",
                points = listOf(
                    OverlayPoint(timestamp = 100L, value = 1.0),
                    OverlayPoint(timestamp = 200L, value = 2.0),
                ),
                modeSensitivity = 12.0,
                styles = mapOf("color" to "#0F0"),
            ),
        )!!
        val info = chart.getOverlays(OverlayFilter(id = id)).first()
        assertEquals("g1", info.groupId)
        assertEquals(2, info.currentStep)
        assertEquals(12.0, info.modeSensitivity)
        assertNotNull(info.styles)

        // groupId 过滤命中
        assertEquals(1, chart.getOverlays(OverlayFilter(groupId = "g1")).size)

        // override 支持新字段
        assertTrue(chart.overrideOverlay(id, OverlayOverride(groupId = "g2", fixedZLevel = true)))
        val updated = chart.getOverlays(OverlayFilter(id = id)).first()
        assertEquals("g2", updated.groupId)
        assertTrue(updated.fixedZLevel)
    }

    // ---------- 0.4.0 多轴渲染消费 ----------

    @Test
    fun multiAxis_syncToStateAndIndependentRange() {
        val state = ChartState()
        val chart = LKanvasChart.init(state)
        // 空状态：无自定义轴
        assertTrue(state.extraYAxisMeta.isEmpty())
        // 创建自定义轴 → 同步到渲染消费源
        val ax = chart.createYAxis(YAxisOverride(id = "ax1", reverse = true))!!
        assertTrue(state.extraYAxisMeta.keys.contains("ax1"))
        assertEquals(true, state.extraYAxisMeta["ax1"])
        // 移除 → 同步清空
        assertTrue(chart.removeYAxis(YAxisFilter(id = ax)))
        assertTrue(state.extraYAxisMeta.isEmpty())
        // 绑定指标后：独立值域（与主轴无关）
        kotlinx.coroutines.runBlocking {
            state.reset(
                io.lkanvas.core.store.MarketKey("T", "1m"),
                listOf(candleOf(1, 100.0), candleOf(2, 110.0), candleOf(3, 120.0)),
            )
        }
        val id = chart.createIndicator("MA", calcParams = listOf(2.0), yAxisId = "ax2")
        assertNotNull(id)
        chart.createYAxis(YAxisOverride(id = "ax2"))
        val range = state.extraAxisRange("ax2", 0, 2)
        assertNotNull(range)
        val (lo, hi) = range
        assertTrue(hi > lo, "MA(2) 值域有效：$range")
        // 主轴无该绑定：extraAxisRange 对主轴不适用（主轴走 mainPaneRange）
        LKanvasChart.dispose(chart)
    }

    private fun candleOf(ts: Long, close: Double) =
        io.lkanvas.core.data.Candle(ts, close, close + 1, close - 1, close, 100.0)

    // ---------- registerStyles/XAxis/YAxis ----------

    @Test
    fun registry_registersStyleThemesAndAxisTemplates() {
        assertEquals(1, chart_registerStylesAndLookup())
        assertTrue(LkanvasRegistry.getSupportedStyles().containsAll(listOf("light", "dark")))
        assertNull(LkanvasRegistry.getXAxisTemplate("nope"))
        assertNotNull(LkanvasRegistry.getYAxisTemplate("percentage"))
        assertNotNull(LkanvasRegistry.getYAxisTemplate("logarithm"))
    }

    private fun chart_registerStylesAndLookup(): Int {
        LkanvasRegistry.registerStyles("mine", io.lkanvas.chart.styles.StylesThemes.dark)
        assertNotNull(LkanvasRegistry.getStyleTheme("mine"))
        assertEquals(
            io.lkanvas.chart.styles.StylesThemes.dark,
            io.lkanvas.chart.styles.StylesMerge.resolveTheme("mine"),
        )
        return LkanvasRegistry.getSupportedStyles().count { it == "mine" }
    }

    @Test
    fun setStyles_resolvesRegisteredThemeName() {
        val chart = newChart()
        LkanvasRegistry.registerStyles("corporate", io.lkanvas.chart.styles.StylesThemes.dark)
        chart.setStyles("corporate")
        assertEquals(io.lkanvas.chart.styles.StylesThemes.dark, chart.getStyles())
        // 未知主题名回退 light
        chart.setStyles("unknown-theme")
        assertEquals(io.lkanvas.chart.styles.StylesThemes.light, chart.getStyles())
    }
}
