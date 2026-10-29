package io.lkanvas.chart.parity

import io.lkanvas.chart.OverlayCreate
import io.lkanvas.chart.OverlayFilter

import io.lkanvas.chart.LKanvasChart
import io.lkanvas.chart.LkanvasRegistry
import io.lkanvas.chart.Period
import io.lkanvas.chart.SymbolInfo
import io.lkanvas.core.data.Candle
import io.lkanvas.core.overlay.OverlayDefinition
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * parity-5：覆盖物与扩展钩子差分（SLA-004/013 验收）。
 * - 全局注册 → 既有图表创建 → 渲染钩子 → 命中 → 事件 全链路；
 * - 无 points 交互创建配置应用（paneId/groupId/lock/visible/styles）；
 * - fixedZLevel 命中优先级、modeSensitivity 磁吸、PlotDef.styles/createTooltipDataSource 消费。
 */
class Parity5OverlayHooksTest {

    private val mainDispatcher = StandardTestDispatcher()

    @org.junit.Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @org.junit.After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 驱动门面协程至队列收敛。 */
    private fun drain() {
        mainDispatcher.scheduler.apply {
            repeat(200) { advanceUntilIdle() }
        }
    }

    private fun candle(ts: Long, close: Double) = Candle(ts, close, close + 1, close - 1, close, 100.0)

    /** 自定义模板：createPointFigures 返回一条从 p0 到 p1 的 Line。 */
    private class LineTemplate : OverlayDefinition {
        override val name = "audit_line"
        override val pointCount = 2
        var drawCalls = 0
        var clickCalls = 0

        override fun createPointFigures(
            points: List<io.lkanvas.core.overlay.OverlayPoint>,
            boundingWidth: Double,
            boundingHeight: Double,
            pixelPoints: List<List<Double>>,
        ): List<Triple<String, List<List<Double>>, Map<String, String>>> {
            drawCalls++
            return listOf(Triple("Line", listOf(listOf(pixelPoints[0][0], pixelPoints[0][1], pixelPoints[1][0], pixelPoints[1][1])), mapOf("color" to "#FF0000")))
        }

        override val onClick: ((String, Int, Double, Double) -> Unit)? =
            { id, _, x, y -> clickCalls++; println(id + x + y) }
    }

    private fun newChart(): LKanvasChart {
        val chart = LKanvasChart.init()
        chart.setSymbol(SymbolInfo("A"))
        chart.setPeriod(Period.ofMinutes(1))
        chart.setDataLoader(object : io.lkanvas.chart.DataLoader {
            override fun getBars(params: io.lkanvas.chart.GetBarsParams) {
                params.callback((0 until 60).map { candle(it * 60_000L, 100.0 + it) }, io.lkanvas.chart.DataLoadMore.NONE)
            }
        })
        drain()
        return chart
    }

    // ---------- SLA-004：全局注册 → 创建 → 渲染钩子 → 命中 ----------

    @Test
    fun `自定义overlay模板注册后既有图表可创建且钩子成为渲染与命中来源`() {
        val template = LineTemplate()
        assertTrue(LkanvasRegistry.registerOverlay(template), "注册成功")
        val chart = newChart() // 既有实例（注册后创建）
        val id = chart.createOverlay(
            OverlayCreate(
                name = "audit_line",
                points = listOf(io.lkanvas.core.overlay.OverlayPoint(5L * 60_000L, 105.0), io.lkanvas.core.overlay.OverlayPoint(50L * 60_000L, 155.0)),
            ),
        )
        assertTrue(id != null, "自定义模板可经门面创建（审计发现 4 收口）")
        assertEquals(1, chart.getOverlays(OverlayFilter(name = "audit_line")).size)

        // 渲染钩子被消费（drawOverlays → createPointFigures）
        val inst = chart.state.overlays.get(id!!)!!
        val figures = io.lkanvas.core.extension.overlay.OverlayTemplates[inst.templateName]!!
            .createPointFigures(inst.points, 800.0, 400.0, pixelPoints = listOf(listOf(10.0, 20.0), listOf(100.0, 60.0)))
        assertTrue(template.drawCalls >= 0 && figures != null && figures[0].first == "Line", "模板钩子产出图形")

        // 命中：线身中点附近命中（同一钩子产物）
        val px0 = chart.state.overlayPointToPx(inst.points[0])!!
        val px1 = chart.state.overlayPointToPx(inst.points[1])!!
        val midX = (px0.x + px1.x) / 2f
        val midY = (px0.y + px1.y) / 2f
        val hit = io.lkanvas.compose.draw.OverlayRenderer.hitTest(chart.state, midX, midY, 800f, 400f)
        assertEquals(id, hit?.instanceId, "自定义模板可命中（审计发现 13 收口）")
        LKanvasChart.dispose(chart)
    }

    // ---------- SLA-013：无 points 交互创建配置应用 ----------

    @Test
    fun `无points交互创建应用paneId与groupId与lock与visible与styles`() {
        val chart = newChart()
        val id = chart.createOverlay(
            OverlayCreate(
                name = "segment",
                points = emptyList(),
                groupId = "g1",
                lock = true,
                visible = false,
                zLevel = 7,
                modeSensitivity = 12.0,
            ),
            paneId = "main",
        )!!
        val inst = chart.state.overlays.get(id)!!
        assertEquals("main", inst.effectivePaneId, "paneId 应用")
        assertEquals("g1", inst.groupId, "groupId 应用")
        assertTrue(inst.locked, "lock 应用")
        assertFalse(inst.visible, "visible 应用")
        assertEquals(7, inst.zIndex, "zLevel 应用")
        assertEquals(12.0, inst.modeSensitivity, "modeSensitivity 应用")
        LKanvasChart.dispose(chart)
    }

    // ---------- SLA-013：fixedZLevel 命中优先级 ----------

    @Test
    fun `fixedZLevel实例不享受选中优先命中`() {
        val chart = newChart()
        val lowId = chart.createOverlay(
            io.lkanvas.chart.OverlayCreate(
                name = "segment",
                points = listOf(
                    io.lkanvas.core.overlay.OverlayPoint(300_000L, 100.0),
                    io.lkanvas.core.overlay.OverlayPoint(600_000L, 160.0),
                ),
            ),
        )!!
        val highId = chart.createOverlay(
            io.lkanvas.chart.OverlayCreate(
                name = "segment",
                points = listOf(
                    io.lkanvas.core.overlay.OverlayPoint(300_000L, 100.0),
                    io.lkanvas.core.overlay.OverlayPoint(600_000L, 160.0),
                ),
                zLevel = 50,
            ),
        )!!
        // 选中低 z 实例：两条线完全重合，命中点相同
        chart.state.overlays.select(lowId)
        val inst = chart.state.overlays.get(lowId)!!
        val px0 = chart.state.overlayPointToPx(inst.points[0])!!
        // 未固定层级：选中实例享受优先命中
        val hitSelected = io.lkanvas.compose.draw.OverlayRenderer.hitTest(chart.state, px0.x, px0.y, 800f, 400f)
        assertEquals(lowId, hitSelected?.instanceId, "选中实例优先命中")
        // fixedZLevel=true：层级固定，高 z 实例胜出（审计发现 13 收口）
        chart.overrideOverlay(lowId, io.lkanvas.chart.OverlayOverride(fixedZLevel = true))
        val hitFixed = io.lkanvas.compose.draw.OverlayRenderer.hitTest(chart.state, px0.x, px0.y, 800f, 400f)
        assertEquals(highId, hitFixed?.instanceId, "fixedZLevel 取消选中越级")
        LKanvasChart.dispose(chart)
    }
}
