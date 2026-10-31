package io.lkanvas.chart.parity

import androidx.compose.ui.geometry.Size
import io.lkanvas.chart.LKanvasChart
import io.lkanvas.chart.OverlayCreate
import io.lkanvas.chart.OverlayOverride
import io.lkanvas.chart.Period
import io.lkanvas.chart.SymbolInfo
import io.lkanvas.core.data.Candle
import io.lkanvas.compose.draw.OverlayRenderer.drawOverlays
import io.lkanvas.core.overlay.OverlayPoint
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * parity-16：控点裁剪/层级一致性差分（第六轮审计 M04+M06 验收）。
 *
 * - M04：控点块移入 clipRect 内、绝对坐标（修复前在裁剪外用 `y-paneTop`
 *   局部值——副图控点画到整图顶部；绘制坐标=命中坐标）；
 * - M06：单一 z 序命中——每实例先控点后线身，跨实例按 z 竞争；
 *   单对象端点编辑保持（N07 基准）。
 *
 * 探针口径（审计 §M04）：TestDrawScope 录制 translate/裁剪深度与控点
 * **有效屏幕坐标**，与 hitTest 的绝对坐标互证。
 */
class Parity16ControlPointTest {

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
                    (0 until 60).map { Candle(it * 60_000L, 100.0 + it, 101.0 + it, 99.0 + it, 100.0 + it, 1000.0 + it) },
                    io.lkanvas.chart.DataLoadMore.NONE,
                )
            }
        })
        drain()
        chart.state.onLayout(800f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        chart.state.relayoutExtraPanes()
        drain()
        return chart
    }

    // ---------- M04：副图控点绘制坐标 = 命中坐标 ----------

    @Test
    fun `副图控点绘制在裁剪块内且坐标与命中一致（M04）`() {
        val chart = newChart()
        chart.createIndicator("VOL", paneId = "vol")
        chart.state.onLayout(800f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        chart.state.relayoutExtraPanes()
        drain()
        val id = chart.createOverlay(
            OverlayCreate(
                name = "segment",
                points = listOf(
                    OverlayPoint(600_000L, 1020.0),
                    OverlayPoint(1_800_000L, 1040.0),
                ),
            ),
            paneId = "vol",
        )!!
        drain()
        chart.state.overlays.select(id)
        val inst = chart.state.overlays.get(id)!!
        val paneId = inst.effectivePaneId
        val px0 = chart.state.overlayPointToPx(inst.points[0], paneId)!!
        val px1 = chart.state.overlayPointToPx(inst.points[1], paneId)!!
        val volBounds = chart.state.paneBounds("vol")!!
        assertTrue(px0.y > volBounds.first && px0.y < volBounds.second, "锚点像素在 vol pane 内")

        val fake = TestDrawScope(Size(800f, 620f))
        with(fake) {
            drawOverlays(
                chart.state, io.lkanvas.compose.theme.ChartTheme(), 800f, 540f, fakeChartText(),
            )
        }
        // 控点圆心（彩色 POINT_RADIUS 圆）= 绝对坐标（=命中坐标），且在裁剪块内
        val points = fake.circles.filter { it.radius == 5f }
        assertEquals(2, points.size, "两枚控点（M04 复现：修复前画到整图顶部 y-paneTop）")
        for (c in points) {
            val near = listOf(px0, px1).minBy { abs(it.y - c.cy) }
            assertTrue(abs(near.y - c.cy) < 0.5f, "控点屏幕 y=命中绝对 y（${c.cy} vs ${near.y}）")
            assertTrue(c.depth >= 1, "控点在裁剪块内（深度 ${c.depth}）")
        }
        // 裁剪矩形=vol pane 边界（非整图顶部）
        val clip = fake.clips.lastOrNull { it.second == 1 }?.first
        assertNotNull(clip)
        assertTrue(abs(clip.top - volBounds.first) < 0.5f && abs(clip.bottom - volBounds.second) < 0.5f,
            "裁剪块 = vol pane（top=${clip.top} vs ${volBounds.first}）")
        LKanvasChart.dispose(chart)
    }

    // ---------- M06：单一 z 序命中 ----------

    @Test
    fun `z高层宽体覆盖低层选中实例控点时命中高层（M06）`() {
        val chart = newChart()
        // z=0 固定层级 segment（选中）+ z=10 水平 segment 覆盖其下端点
        val lowId = chart.createOverlay(
            OverlayCreate(
                name = "segment",
                points = listOf(OverlayPoint(600_000L, 130.0), OverlayPoint(1_800_000L, 130.0)),
                zLevel = 0,
            ),
        )!!
        chart.createOverlay(
            OverlayCreate(
                name = "segment",
                points = listOf(OverlayPoint(300_000L, 130.0), OverlayPoint(2_400_000L, 130.0)),
                zLevel = 10,
            ),
        )
        drain()
        chart.state.overlays.select(lowId)
        chart.overrideOverlay(lowId, OverlayOverride(fixedZLevel = true))
        val inst = chart.state.overlays.get(lowId)!!
        val px0 = chart.state.overlayPointToPx(inst.points[0], inst.effectivePaneId)!!
        // z=10 线身经过 z=0 控点位置（同值 130 → 同 y）
        val hit = io.lkanvas.compose.draw.OverlayRenderer.hitTest(chart.state, px0.x, px0.y, 800f, 540f)
        assertEquals(10, chart.state.overlays.get(hit!!.instanceId)?.zIndex,
            "高层线身先命中（M06：修复前低层固定控点全局特权返回 pointIndex=0）")
        assertTrue(hit.pointIndex == null, "命中为高层线身而非控点")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `单对象端点编辑与线身命中保持（N07基准）`() {
        val chart = newChart()
        val id = chart.createOverlay(
            OverlayCreate(
                name = "segment",
                points = listOf(OverlayPoint(600_000L, 130.0), OverlayPoint(1_800_000L, 150.0)),
            ),
        )!!
        drain()
        chart.state.overlays.select(id)
        val inst = chart.state.overlays.get(id)!!
        val px0 = chart.state.overlayPointToPx(inst.points[0], inst.effectivePaneId)!!
        val px1 = chart.state.overlayPointToPx(inst.points[1], inst.effectivePaneId)!!
        // 端点命中 → pointIndex（同实例控点优先于线身）
        val hitPoint = io.lkanvas.compose.draw.OverlayRenderer.hitTest(chart.state, px0.x, px0.y, 800f, 540f)
        assertEquals(id, hitPoint?.instanceId)
        assertEquals(0, hitPoint?.pointIndex, "端点编辑命中控制点（N07 基准保持）")
        // 线身中点命中 → wholeDrag
        val midX = (px0.x + px1.x) / 2f
        val midY = (px0.y + px1.y) / 2f
        val hitBody = io.lkanvas.compose.draw.OverlayRenderer.hitTest(chart.state, midX, midY, 800f, 540f)
        assertEquals(id, hitBody?.instanceId)
        assertEquals(null, hitBody?.pointIndex)
        assertEquals(true, hitBody?.wholeDrag, "线身中点命中整体拖动")
        LKanvasChart.dispose(chart)
    }
}
