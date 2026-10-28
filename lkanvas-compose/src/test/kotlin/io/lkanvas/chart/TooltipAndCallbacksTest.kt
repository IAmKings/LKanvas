package io.lkanvas.chart

import io.lkanvas.compose.state.ChartState
import io.lkanvas.core.data.Candle
import io.lkanvas.core.overlay.OverlayPoint
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorDrawContext
import io.lkanvas.core.indicator.IndicatorEngine
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType
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
 * 批次 7+8 测试：tooltip 规则/图例数据、feature 命中分发、
 * 覆盖物事件回调、draw 钩子上下文、时区接线。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TooltipAndCallbacksTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun candle(ts: Long, close: Double) = Candle(ts, close, close + 1, close - 1, close, 1234.0)

    // ---------- 批次 7a：showRule 判定（上游 isDrawTooltip 语义） ----------

    @Test
    fun shouldDraw_followsUpstreamRule() {
        assertTrue(io.lkanvas.compose.draw.TooltipRenderer.shouldDraw("always", crosshairActive = false))
        assertTrue(io.lkanvas.compose.draw.TooltipRenderer.shouldDraw("always", crosshairActive = true))
        assertFalse(io.lkanvas.compose.draw.TooltipRenderer.shouldDraw("follow_cross", crosshairActive = false))
        assertTrue(io.lkanvas.compose.draw.TooltipRenderer.shouldDraw("follow_cross", crosshairActive = true))
        assertFalse(io.lkanvas.compose.draw.TooltipRenderer.shouldDraw("none", crosshairActive = true))
    }

    // ---------- 批次 7a：candle 图例数据（change 着色规则） ----------

    @Test
    fun buildCandleLegends_colorsChangeByPriceMark() {
        val state = ChartState()
        kotlinx.coroutines.runBlocking {
            state.reset(io.lkanvas.core.store.MarketKey("T", "1m"), listOf(candle(1, 10.0), candle(2, 12.0)))
        }
        val styles = io.lkanvas.chart.styles.StylesThemes.light
        val rows = io.lkanvas.compose.draw.TooltipRenderer.buildCandleLegends(state, 1, styles, SkipText)
        assertEquals(6, rows.size)
        assertEquals("O", rows[0].title)
        assertEquals("涨跌", rows[5].title)
        // 上涨 → change 值色 = priceMark.last.upColor
        assertEquals(
            io.lkanvas.compose.theme.StylesThemeBridge.parse(styles.candle.priceMark.last.upColor),
            rows[5].valueColor,
        )
        assertTrue(rows[5].value.endsWith("%"))
    }

    /** JVM 替身（NumericText 最小契约，无需 TextMeasurer）。 */
    private object SkipText : io.lkanvas.compose.draw.TooltipRenderer.NumericText {
        override fun price(v: Double): String = if (v.isFinite()) "%.2f".format(v) else "—"
        override fun volume(v: Double): String = if (v.isFinite()) "%.0f".format(v) else "—"
    }

    // ---------- 批次 7c：feature 命中 + 三事件分发 ----------

    @Test
    fun tooltipFeatureHit_dispatchesActionType() {
        val state = ChartState()
        val chart = LKanvasChart.init(state)
        val received = ArrayList<Pair<ActionType, Any?>>()
        chart.subscribeAction(ActionType.ON_CANDLE_TOOLTIP_FEATURE_CLICK) { received += ActionType.ON_CANDLE_TOOLTIP_FEATURE_CLICK to it }

        // 渲染端记录命中区
        state.tooltipFeatureHits = listOf(
            ChartState.TooltipFeatureHit("main", "demo_flag", androidx.compose.ui.geometry.Rect(0f, 0f, 20f, 20f)),
        )
        assertEquals("main" to "demo_flag", state.tooltipFeatureAt(10f, 10f))
        assertNull(state.tooltipFeatureAt(30f, 30f))

        // 手势命中 → 门面钩子 → ActionType（paneId=main）
        state.onTooltipFeatureClick?.invoke("main", "demo_flag", 10.0, 10.0)
        assertEquals(1, received.size)
        assertEquals(ActionType.ON_CANDLE_TOOLTIP_FEATURE_CLICK, received[0].first)
        LKanvasChart.dispose(chart)
    }

    // ---------- 批次 8c：draw 钩子上下文 ----------

    @Test
    fun indicatorDrawHook_receivesContextAndSkipsPipeline() {
        var ctx: IndicatorDrawContext? = null
        val def = object : IndicatorDefinition {
            override val name = "DRAW_TEST"
            override val shortName = "DT"
            override val calcParams = emptyList<Double>()
            override val precision = 2
            override val plots = listOf(PlotDef("v", "V", PlotType.LINE))
            override val draw: ((IndicatorDrawContext) -> Unit)? = { ctx = it }
            override fun initScratch(params: List<Double>) = null
            override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int) = null
            override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int) =
                mapOf("v" to candles[i].close)
        }
        val candles = listOf(candle(1, 10.0), candle(2, 20.0), candle(3, 30.0))
        val state = IndicatorEngine.recompute(def, emptyList(), candles)
        assertNotNull(def.draw)
        // 模拟渲染端调用（scope=null 纯 JVM）
        def.draw?.invoke(
            IndicatorDrawContext(
                result = state.result, rangeFirst = 0, rangeLast = 2,
                yOf = { it }, xOf = { it.toDouble() }, palette = emptyList(), scope = null,
            ),
        )
        val captured = ctx
        assertNotNull(captured)
        assertEquals(3, captured.result["v"].size)
        assertEquals(2, captured.rangeLast)
    }

    // ---------- 批次 9a：覆盖物无障碍遍历 ----------

    @Test
    fun selectNextOverlay_cyclesAndAccessibilityTextMentionsOverlays() {
        val state = ChartState()
        val chart = LKanvasChart.init(state)
        kotlinx.coroutines.runBlocking {
            state.reset(io.lkanvas.core.store.MarketKey("T", "1m"), listOf(candle(1, 10.0), candle(2, 12.0), candle(3, 13.0)))
        }
        // 空覆盖物：false
        assertFalse(state.selectNextOverlay())
        // 画两条线
        val id1 = chart.createOverlay(OverlayCreate("segment", points = listOf(OverlayPoint(100L, 1.0), OverlayPoint(200L, 2.0))))!!
        val id2 = chart.createOverlay(OverlayCreate("segment", points = listOf(OverlayPoint(300L, 3.0), OverlayPoint(400L, 4.0))))!!
        // 从未选 → 选中第一个；再调 → 第二个；再调 → 循环回第一个
        assertTrue(state.selectNextOverlay())
        assertEquals(id1, state.overlays.selectedId)
        assertTrue(state.selectNextOverlay())
        assertEquals(id2, state.overlays.selectedId)
        assertTrue(state.selectNextOverlay())
        assertEquals(id1, state.overlays.selectedId)
        // 无障碍摘要含覆盖物信息
        val text = state.accessibilityText { ts -> ts.toString() }
        assertTrue(text.contains("画线"), "摘要应含覆盖物数量词: $text")
        assertTrue(text.contains("segment"), "摘要应含选中模板名: $text")
        LKanvasChart.dispose(chart)
    }

    // ---------- 批次 8e：时区接线 ----------

    @Test
    fun setTimezone_propagatesToState() {
        val state = ChartState()
        val chart = LKanvasChart.init(state)
        chart.setTimezone("Asia/Tokyo")
        assertEquals("Asia/Tokyo", state.timezoneId)
        assertEquals(java.util.TimeZone.getTimeZone("Asia/Tokyo"), state.effectiveTimeZone())
        // 空=系统时区
        chart.setTimezone("")
        assertEquals(java.util.TimeZone.getDefault(), state.effectiveTimeZone())
        // 非法名回退系统时区
        chart.setTimezone("Not/AZone")
        assertEquals(java.util.TimeZone.getDefault(), state.effectiveTimeZone())
        LKanvasChart.dispose(chart)
    }
}
