package io.lkanvas.chart.parity

import io.lkanvas.compose.draw.PaneRenderers.drawExtraPane
import io.lkanvas.compose.draw.PaneRenderers.drawVolumePane
import io.lkanvas.chart.LKanvasChart
import io.lkanvas.chart.Period
import io.lkanvas.chart.SymbolInfo
import io.lkanvas.chart.YAxisOverride
import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorDrawContext
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * parity-14：per-instance 轴投影差分（第六轮审计 M02 验收）。
 *
 * M02：drawExtraPane 的轴解析在实例循环外 firstOrNull——多个有效绑定共用第一条
 * 轴；内置 vol/macd 入口完全忽略绑定轴。修复：state.instanceBoundYOf 单一选择
 * 入口（固定 + 动态 pane 共用），实例循环内逐实例解析。
 *
 * 探针口径（审计 §探针）：draw 钩子读取真实 renderer 提供的 yOf 后中止绘制
 * （DrawProbeAbort），断言投影与轴绑定/值域/reverse 一致。
 */
class Parity14PerInstanceAxisTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun drain() {
        mainDispatcher.scheduler.apply { repeat(200) { advanceUntilIdle() } }
    }

    private fun newChart(): LKanvasChart {
        val chart = LKanvasChart.init()
        chart.setSymbol(SymbolInfo("A"))
        chart.setPeriod(Period.ofMinutes(1))
        chart.setDataLoader(object : io.lkanvas.chart.DataLoader {
            override fun getBars(params: io.lkanvas.chart.GetBarsParams) {
                params.callback(
                    (0 until 10).map { Candle(it * 60_000L, 100.0 + it, 101.0 + it, 99.0 + it, 100.0 + it, 1000.0 + it) },
                    io.lkanvas.chart.DataLoadMore.NONE,
                )
            }
        })
        drain()
        return chart
    }

    /** 探针模板：v = close × [mul]；draw 钩子捕获 yOf 后中止绘制（读取真实 renderer 投影）。 */
    private fun probeTemplate(name: String, mul: Double = 1.0) = object : IndicatorDefinition {
        override val name = name
        override val shortName = name
        override val calcParams = emptyList<Double>()
        override val precision = 2
        override val plots = listOf(PlotDef("v", name, PlotType.LINE))
        @Suppress("ObjectLiteralToLambda")
        override val draw: ((IndicatorDrawContext) -> Unit)? get() = { throw DrawProbeAbort(it.yOf) }
        override fun initScratch(params: List<Double>) = null
        override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int) = null
        override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int) =
            mapOf("v" to candles[i].close * mul)
    }

    /** 渲染该 pane（探针中止），返回 draw 钩子拿到的 yOf 与渲染时 pane bounds。 */
    private fun probeExtraPaneYOf(chart: LKanvasChart, paneId: String): Triple<(Double) -> Double, Float, Float> {
        val bounds = chart.state.paneBounds(paneId)!!
        val fake = TestDrawScope(androidx.compose.ui.geometry.Size(600f, 700f))
        try {
            with(fake) {
                drawExtraPane(
                    chart.state, io.lkanvas.compose.theme.ChartTheme(), fakeChartText(),
                    paneId, 0f, 500f, bounds.first, bounds.second,
                )
            }
        } catch (e: DrawProbeAbort) {
            return Triple(e.yOf, bounds.first, bounds.second)
        }
        throw AssertionError("draw 钩子未触发（无可见探针实例）")
    }

    /** 统一布局（onLayout 后布局稳定；探针在布局后读取 bounds 并渲染）。 */
    private fun layout(chart: LKanvasChart) {
        chart.state.onLayout(600f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        chart.state.relayoutExtraPanes()
        drain()
    }

    private fun probeVolPaneYOf(chart: LKanvasChart): Pair<(Double) -> Double, Pair<Float, Float>> {
        val bounds = chart.state.paneBounds("vol")!!
        val fake = TestDrawScope(androidx.compose.ui.geometry.Size(600f, 700f))
        try {
            with(fake) {
                drawVolumePane(
                    chart.state, io.lkanvas.compose.theme.ChartTheme(),
                    0f, 500f, bounds.first, bounds.second, fakeChartText(),
                )
            }
        } catch (e: DrawProbeAbort) {
            return e.yOf to bounds
        }
        throw AssertionError("draw 钩子未触发")
    }

    private fun setVisible(chart: LKanvasChart, id: String, visible: Boolean) {
        chart.state.overrideIndicator(
            io.lkanvas.compose.state.ChartState.IndicatorFilter(id = id),
            visible = visible,
        )
        drain()
    }

    // ---------- M02 主复现：同 pane 双实例双轴各自投影 ----------

    @Test
    fun `同pane双实例双轴各自投影且reverse翻转（M02）`() {
        val chart = newChart()
        // 实例 A（值 100..109）绑定 reverse 轴；实例 B（值 200..218）绑定 normal 轴；同 pane
        chart.state.registry.register(probeTemplate("PROBE_A"))
        chart.state.registry.register(probeTemplate("PROBE_B", mul = 2.0))
        val paneId = chart.createIndicator("PROBE_A", paneId = "pane_x", yAxisId = "ax_a", id = "i_a")!!
            .let { chart.getIndicatorByInstanceId(it)!!.paneId }
        assertTrue(chart.createYAxis(YAxisOverride(id = "ax_a", paneId = paneId, reverse = true)) != null)
        assertTrue(chart.createYAxis(YAxisOverride(id = "ax_b", paneId = paneId)) != null)
        val iB = chart.createIndicator("PROBE_B", paneId = paneId, yAxisId = "ax_b", id = "i_b", isStack = true)!!
        val iU = chart.createIndicator("PROBE_A", paneId = paneId, yAxisId = "main", id = "i_u", isStack = true)!!
        layout(chart)

        // 实例 A（reverse 轴）：值域 100..109，y(100)=bottom、y(109)=top（翻转）
        setVisible(chart, iB, false); setVisible(chart, iU, false)
        val (yA, t, b) = probeExtraPaneYOf(chart, paneId)
        assertTrue(abs(yA(100.0) - t) < 1e-6, "reverse 轴 y(lo)=top（实际 ${yA(100.0)} vs $t）")
        assertTrue(abs(yA(109.0) - b) < 1e-6, "reverse 轴 y(hi)=bottom（实际 ${yA(109.0)} vs $b）")
        // 实例 B（normal 轴，**自身值域** 200..218）：y(200)=top、y(218)=bottom
        setVisible(chart, "i_a", false); setVisible(chart, iB, true)
        val (yB, t2, b2) = probeExtraPaneYOf(chart, paneId)
        assertTrue(abs(yB(200.0) - b2) < 1e-6, "normal 轴 y(lo)=bottom（实际 ${yB(200.0)} vs $b2）")
        assertTrue(abs(yB(218.0) - t2) < 1e-6, "normal 轴 y(hi)=top（实际 ${yB(218.0)} vs $t2）")
        // 修复前 firstOrNull：B 共用 A 的轴/值域（100..109 + reverse）→ y(209) 越界/翻转；
        // 修复后 B 按自身值域投影：y(209)=pane 中点
        val mid = (t2 + b2) / 2f
        assertTrue(abs(yB(209.0) - mid) < 1e-6, "B 按自身值域中点投影（实际 ${yB(209.0)} vs $mid）")
        // 未绑定实例（yAxisId=main）：回退 paneVisibleRange 线性（仅 U 可见 → 值域 100..109）
        setVisible(chart, "i_b", false); setVisible(chart, iU, true)
        val (yU, t3, b3) = probeExtraPaneYOf(chart, paneId)
        assertTrue(abs(yU(100.0) - b3) < 1e-6, "未绑定实例线性 y(lo)=bottom（实际 ${yU(100.0)} vs $b3）")
        assertTrue(abs(yU(109.0) - t3) < 1e-6, "未绑定实例线性 y(hi)=top（实际 ${yU(109.0)} vs $t3）")
        LKanvasChart.dispose(chart)
    }

    // ---------- M02：内置 vol pane 绑定 reverse 轴 ----------

    @Test
    fun `内置vol绑定reverse轴后按轴投影（M02）`() {
        val chart = newChart()
        chart.state.registry.register(probeTemplate("PROBE_V"))
        assertTrue(chart.createYAxis(YAxisOverride(id = "ax_vol", paneId = "vol", reverse = true)) != null)
        chart.createIndicator("PROBE_V", paneId = "vol", yAxisId = "ax_vol", id = "i_v")
        layout(chart)
        // 探针值 100..109（轴值域），vol pane 数据量纲 1000..1009 不参与该轴
        val (yV, bounds) = probeVolPaneYOf(chart)
        val (t, b) = bounds
        assertTrue(abs(yV(100.0) - t) < 1e-6, "vol 绑定 reverse 轴 y(lo)=top（实际 ${yV(100.0)} vs $t）")
        assertTrue(abs(yV(109.0) - b) < 1e-6, "vol 绑定 reverse 轴 y(hi)=bottom（实际 ${yV(109.0)} vs $b）")
        // 修复前（忽略绑定轴、普通线性 vol 全量纲 100..1009）：y(100) 落在 bottom
        val wrongLo = t + (1.0 - 0.0) * (b - t)
        assertTrue(abs(yV(100.0) - wrongLo) > 1.0, "与旧线性全量纲投影可辨（旧应为 $wrongLo）")
        LKanvasChart.dispose(chart)
    }

    // ---------- 单一选择入口的排除规则 ----------

    @Test
    fun `instanceBoundYOf排除规则main未知轴与跨pane轴`() {
        val chart = newChart()
        chart.state.registry.register(probeTemplate("PROBE_X"))
        val paneId = chart.createIndicator("PROBE_X", paneId = "pane_y", id = "i_x")!!
            .let { chart.getIndicatorByInstanceId(it)!!.paneId }
        assertTrue(chart.createYAxis(YAxisOverride(id = "ax_v", paneId = "vol")) != null)
        layout(chart)
        val (t, b) = chart.state.paneBounds(paneId)!!
        assertNull(chart.state.instanceBoundYOf(paneId, "main", 0, 9, t, b), "main 回退 null")
        assertNull(chart.state.instanceBoundYOf(paneId, "ax_unknown", 0, 9, t, b), "未知轴 null")
        assertNull(chart.state.instanceBoundYOf(paneId, "ax_v", 0, 9, t, b), "跨 pane 轴 null（ax_v 属 vol）")
        // 正常绑定：返回闭包且与 axisValueToY 同式（reverse=false 线性一致）
        assertTrue(chart.createYAxis(YAxisOverride(id = "ax_y", paneId = paneId)) != null)
        chart.createIndicator("PROBE_X", paneId = paneId, yAxisId = "ax_y", id = "i_x2", isStack = true)
        drain()
        val y = chart.state.instanceBoundYOf(paneId, "ax_y", 0, 9, t, b)!!
        val alo = chart.state.extraAxisRange("ax_y", 0, 9)!!.first
        val expect = t + ((1.0 - (100.0 - alo) / 9.0) * (b - t)).toFloat()
        println("DEBUG-T3 y(100)=${y(100.0)} expect=$expect t=$t b=$b meta=${chart.state.extraYAxisMeta["ax_y"]} spec=${chart.state.effectiveExtraAxisSpec("ax_y")}")
        assertTrue(abs(y(100.0).toFloat() - expect) < 1e-3, "绑定闭包与线性投影同式")
        LKanvasChart.dispose(chart)
    }
}
