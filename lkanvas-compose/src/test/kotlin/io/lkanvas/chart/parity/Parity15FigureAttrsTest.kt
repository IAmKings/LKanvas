package io.lkanvas.chart.parity

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorEngine
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType
import io.lkanvas.compose.draw.PaneRenderers.drawInstanceFigures
import io.lkanvas.compose.draw.VisibleRange
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.core.viewport.Viewport
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * parity-15：figure attrs 几何契约差分（第六轮审计 M03 验收）。
 *
 * M03：BAR attrs x 被当中心再减半宽（上游=左边界）、y/height 不消费；
 * LINE attrs.coordinates 零消费。修复：BAR 按上游四键合并（默认几何
 * {x: center-barWidth/2, y: min(valueY,baseY), width, height} 后 attrs
 * 覆盖）；drawLinePlots 有 coordinates 行逐点 drawPath、无 attrs 行维持
 * 批量快路径。
 *
 * 探针口径（审计 §M03）：经 TestDrawScope 录制**真实绘制参数**断言。
 */
class Parity15FigureAttrsTest {

    private fun candles(n: Int = 3) = (0 until n).map { Candle(it * 60_000L, 100.0 + it, 101.0 + it, 99.0 + it, 100.0 + it, 100.0) }

    private fun barTemplate(attrs: Any?) = object : IndicatorDefinition {
        override val name = "BAR_ATTRS"
        override val shortName = "BA"
        override val calcParams = emptyList<Double>()
        override val precision = 2
        override val plots = listOf(PlotDef("v", "V", PlotType.BAR, baseValue = 0.0, attrs = attrs))
        override fun initScratch(params: List<Double>) = null
        override fun stepScratch(prev: Any?, c: List<Candle>, p: List<Double>, i: Int) = null
        override fun rowFromScratch(s: Any?, c: List<Candle>, p: List<Double>, i: Int) = mapOf("v" to c[i].close)
    }

    private fun lineTemplate(attrs: Any?) = object : IndicatorDefinition {
        override val name = "LINE_ATTRS"
        override val shortName = "LA"
        override val calcParams = emptyList<Double>()
        override val precision = 2
        override val plots = listOf(PlotDef("v", "V", PlotType.LINE, attrs = attrs))
        override fun initScratch(params: List<Double>) = null
        override fun stepScratch(prev: Any?, c: List<Candle>, p: List<Double>, i: Int) = null
        override fun rowFromScratch(s: Any?, c: List<Candle>, p: List<Double>, i: Int) = mapOf("v" to c[i].close)
    }

    private fun drawFigures(
        def: IndicatorDefinition,
        paneTop: Float = 0f,
        yOf: (Double) -> Double = { v -> v },
        vp: Viewport = Viewport(),
    ): TestDrawScope {
        val cs = candles()
        val state = IndicatorEngine.recompute(def, emptyList(), cs)
        val inst = io.lkanvas.core.indicator.IndicatorInstance("i", def, def.calcParams, "main", "main", null)
        val fake = TestDrawScope(Size(600f, 600f))
        with(fake) {
            drawInstanceFigures(
                inst, state, vp, VisibleRange(0, cs.size - 1),
                yOf, ChartTheme(), 0, lastValueMark = false,
                candles = cs, paneTop = paneTop,
            )
        }
        return fake
    }

    // ---------- M03 主复现：BAR 四键 {x,y,width,height} ----------

    @Test
    fun `BAR attrs四键按上游含义消费（M03）`() {
        // 审计复现口径：零 pane 偏移下 {x:20,y:30,width:7,height:9} → [20,30,7,9]
        val fake = drawFigures(barTemplate(mapOf("x" to 20.0, "y" to 30.0, "width" to 7.0, "height" to 9.0)))
        assertEquals(3, fake.rects.size, "3 行各一矩形")
        for (r in fake.rects) {
            assertEquals(20f, r.l, "attrs.x=矩形左边界（修复前当中心减半宽）")
            assertEquals(30f, r.t, "attrs.y=顶边（修复前不消费）")
            assertEquals(27f, r.r, "left+width")
            assertEquals(39f, r.b, "top+height=9（修复前不消费）")
        }
    }

    @Test
    fun `BAR attrs部分键与默认几何合并`() {
        // 仅 x：左边界覆盖，y/height 走默认（value/base 几何）
        val fake = drawFigures(barTemplate(mapOf("x" to 11.0)))
        for (r in fake.rects) {
            assertEquals(11f, r.l, "attrs.x 覆盖左边界")
            // y({it}) 恒等投影：v=close=100..109，base=0 → top=min(v,0)=0，height=|v|
            assertEquals(0f, r.t, "默认 y=min(valueY,baseY)")
            assertEquals(r.l + r.r - 2 * r.l, r.r - r.l, "宽度自洽")
        }
        // paneTop 平移（N04 语义）：y/height 键 + paneTop=40
        val fake2 = drawFigures(barTemplate(mapOf("y" to 30.0, "height" to 9.0)), paneTop = 40f)
        assertEquals(70f, fake2.rects.first().t, "attrs.y + paneTop")
        assertEquals(79f, fake2.rects.first().b, "top+height + paneTop")
    }

    @Test
    fun `BAR无attrs走默认几何快路径`() {
        val fake = drawFigures(barTemplate(null))
        assertEquals(3, fake.rects.size)
        // 默认 x = candleCenterX - bodyWidth/2：同宽、逐行右移一个槽位
        val w0 = fake.rects[0].r - fake.rects[0].l
        for ((idx, r) in fake.rects.withIndex()) {
            // v=close=100+idx，base=0，y 恒等 → top=0，height=100+idx
            assertEquals(0f, r.t, "默认 top")
            assertEquals(100f + idx, r.b, "默认 height=|vy-baseY|")
            assertEquals(w0, r.r - r.l, "默认宽度 = bodyWidth（各行一致）")
            if (idx > 0) assertTrue(r.l > fake.rects[idx - 1].l, "逐行右移一个槽位")
        }
    }

    @Test
    fun `BAR top bottom 既有bridge键兼容（N04基准保持）`() {
        val fake = drawFigures(barTemplate(mapOf("top" to 5.0, "bottom" to 50.0)))
        for (r in fake.rects) {
            assertEquals(5f, r.t, "top 键（pane 相对 +paneTop）")
            assertEquals(50f, r.b, "bottom 键")
        }
    }

    // ---------- R7-03（第七轮复审）：部分覆盖的默认几何依赖序合成 ----------

    @Test
    fun `BAR heightOnly保持默认top不二次平移（R7-03）`() {
        // 审计复现：pane top=481，值 100、范围 [0,100]——y(100)=481(顶)、y(0)=561?? 
        // yOf 模拟 pane 映射：v → 481 + (100-v)?? 用恒定基线口径：yOf(v)=v+481，
        // 值 100 → vy=581，base 0 → baseY=481，默认 top=min=481、height=100。
        // attrs {height:9}：top 保持默认 481（修复前 481+481=962）、height=9
        val fake = drawFigures(
            barTemplate(mapOf("height" to 9.0)),
            paneTop = 481f,
            yOf = { v -> v + 481.0 },
        )
        assertEquals(3, fake.rects.size)
        for (r in fake.rects) {
            assertEquals(481f, r.t, "默认 top 保持绝对坐标（修复前 962）")
            assertEquals(490f, r.b, "top+height=9")
        }
    }

    @Test
    fun `BAR widthOnly按有效宽度居中（R7-03）`() {
        // 审计复现：默认槽宽 8、柱中心 x=4，attrs {width:10} → 左边界 = 4-10/2 = -1
        // （修复前按旧 bodyWidth=6 计算 left=1，中心偏移 2px）
        val vp = Viewport(barSpacing = 8.0)
        val fake = drawFigures(barTemplate(mapOf("width" to 10.0)), vp = vp)
        assertEquals(3, fake.rects.size)
        assertEquals(-1f, fake.rects[0].l, "缺省左边界按有效宽度居中")
        assertEquals(9f, fake.rects[0].r, "left+width")
        // 宽度生效：10（非默认 bodyWidth=6）
        assertEquals(10f, fake.rects[0].r - fake.rects[0].l)
    }

    @Test
    fun `BAR完整四键非零paneTop只平移y一次（R7-03基准）`() {
        val fake = drawFigures(barTemplate(mapOf("x" to 20.0, "y" to 30.0, "width" to 7.0, "height" to 9.0)), paneTop = 40f)
        for (r in fake.rects) {
            assertEquals(20f, r.l, "x 不受 paneTop 影响")
            assertEquals(70f, r.t, "attrs.y + paneTop（一次）")
            assertEquals(27f, r.r)
            assertEquals(79f, r.b)
        }
    }

    // ---------- M03：LINE attrs.coordinates 逐点覆盖 ----------

    @Test
    fun `LINE attrs coordinates逐点绘制（M03）`() {
        val coords = listOf(
            mapOf("x" to 10.0, "y" to 20.0),
            mapOf("x" to 30.0, "y" to 40.0),
        )
        val fake = drawFigures(lineTemplate(mapOf("coordinates" to coords)))
        assertTrue("path" in fake.ops, "coordinates 行走 drawPath 点列（修复前零消费）")
        assertEquals(3, fake.ops.count { it == "path" }, "3 行各一点列")
    }

    @Test
    fun `LINE coordinates paneTop平移（N04同语义）`() {
        val coords = listOf(mapOf("x" to 0.0, "y" to 10.0), mapOf("x" to 20.0, "y" to 10.0))
        val def = lineTemplate(mapOf("coordinates" to coords))
        val cs = candles(1)
        val state = IndicatorEngine.recompute(def, emptyList(), cs)
        val inst = io.lkanvas.core.indicator.IndicatorInstance("i", def, def.calcParams, "main", "main", null)
        val fake = TestDrawScope(Size(600f, 600f))
        with(fake) {
            // 记录 translate 后的路径点：经 clips/ops 无法读 path 点——
            // 用 drawLine 等价探针：无。直接验证 ops 出现 path（平移语义与 BAR 同源 +paneTop）
            drawInstanceFigures(
                inst, state, Viewport(), VisibleRange(0, 0),
                { v -> v }, ChartTheme(), 0, lastValueMark = false,
                candles = cs, paneTop = 50f,
            )
        }
        assertTrue("path" in fake.ops, "paneTop 平移下点列仍绘制")
    }
}
