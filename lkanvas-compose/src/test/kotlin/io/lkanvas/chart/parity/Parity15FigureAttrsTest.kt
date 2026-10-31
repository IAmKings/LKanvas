package io.lkanvas.chart.parity

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
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

    private fun lineTemplatePerRow(attrsOf: (Int) -> Any?) = object : IndicatorDefinition {
        override val name = "LINE_ATTRS_ROW"
        override val shortName = "LR"
        override val calcParams = emptyList<Double>()
        override val precision = 2
        override val plots = listOf(PlotDef("v", "V", PlotType.LINE, attrs = attrsOf))
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
        // R8-03：段存在=当前+下一点均有效——row 2 无下一点不登记
        assertEquals(2, fake.ops.count { it == "path" }, "row 0/1 点列登记（row 2 末端无下一点不登记）")
    }

    @Test
    fun `LINE coordinates paneTop平移（N04同语义）`() {
        val coords = listOf(mapOf("x" to 0.0, "y" to 10.0), mapOf("x" to 20.0, "y" to 10.0))
        val def = lineTemplate(mapOf("coordinates" to coords))
        val cs = candles(2)
        val state = IndicatorEngine.recompute(def, emptyList(), cs)
        val inst = io.lkanvas.core.indicator.IndicatorInstance("i", def, def.calcParams, "main", "main", null)
        val fake = TestDrawScope(Size(600f, 600f))
        with(fake) {
            drawInstanceFigures(
                inst, state, Viewport(), VisibleRange(0, 1),
                { v -> v }, ChartTheme(), 0, lastValueMark = false,
                candles = cs, paneTop = 50f,
            )
        }
        assertTrue("path" in fake.ops, "paneTop 平移下点列仍绘制")
    }

    // ---------- R7-04（第七轮复审）：混合自定义/默认 coordinates 行段独立 ----------

    /** 录制 native drawLines 数组与 drawPath 次数。 */
    private fun drawLines(def: IndicatorDefinition, candlesCount: Int = 3, yOf: (Double) -> Double = { v -> v }): TestDrawScope {
        val cs = candles(candlesCount)
        val state = IndicatorEngine.recompute(def, emptyList(), cs)
        val inst = io.lkanvas.core.indicator.IndicatorInstance("i", def, def.calcParams, "main", "main", null)
        val fake = TestDrawScope(Size(600f, 600f))
        with(fake) {
            drawInstanceFigures(
                inst, state, Viewport(), VisibleRange(0, cs.size - 1),
                yOf, ChartTheme(), 0, lastValueMark = false,
                candles = cs, paneTop = 0f,
            )
        }
        return fake
    }

    private fun batchPoints(fake: TestDrawScope): List<Float> =
        fake.drawContext.canvas.nativeCanvas.let { it as TestDrawScope.RecordingNative }
            .lineArrays.flatMap { it.toList() }

    @Test
    fun `LINE混合自定义与默认coordinates无伪桥线（R7-04）`() {
        // 审计复现口径：默认坐标 (4,100),(12,101),(20,102)（barSpacing=8、行值 close，x 恒等 y）；
        // 仅第 0 行自定义 [(300,400),(500,600)]——期望自定义点列 + 第 1 行默认段
        // (12,110)→(20,120)；修复前多出桥线 (500,600)→(12,110)
        val def = lineTemplatePerRow { row ->
            if (row == 0) mapOf("coordinates" to listOf(mapOf("x" to 300.0, "y" to 400.0), mapOf("x" to 500.0, "y" to 600.0)))
            else null
        }
        val fake = drawLines(def)
        assertEquals(1, fake.ops.count { it == "path" }, "1 个自定义点列段")
        val pts = batchPoints(fake)
        assertEquals(listOf(12f, 101f, 20f, 102f), pts, "默认段仅 (P1,P2)——无自定义终点桥线（R7-04 复现收口）")
    }

    @Test
    fun `LINE默认到覆盖到默认保持独立段（R7-04基准）`() {
        // 4 行 (4,100),(12,101),(20,102),(28,103)，第 1 行自定义：
        // 期望段 (P0,P1) + 自定义 + (P2,P3)——无 (P1,P2)、无桥线
        val def = lineTemplatePerRow { row ->
            if (row == 1) mapOf("coordinates" to listOf(mapOf("x" to 400.0, "y" to 10.0), mapOf("x" to 600.0, "y" to 20.0)))
            else null
        }
        val fake = drawLines(def, candlesCount = 4)
        assertEquals(1, fake.ops.count { it == "path" })
        val pts = batchPoints(fake)
        assertEquals(
            listOf(4f, 100f, 12f, 101f, 20f, 102f, 28f, 103f),
            pts,
            "默认段 (P0,P1)+(P2,P3) 独立提交",
        )
    }

    @Test
    fun `LINE自定义行后NaN间隔不产生跨断桥接（R7-04基准）`() {
        // 4 行，第 1 行自定义、第 2 行 NaN：期望段 (P0,P1) + 自定义；(P3) 无下一点不成段
        val cs = candles(4).mapIndexed { idx, c ->
            if (idx == 2) c.copy(close = Double.NaN) else c
        }
        val def = lineTemplatePerRow { row ->
            if (row == 1) mapOf("coordinates" to listOf(mapOf("x" to 400.0, "y" to 10.0), mapOf("x" to 600.0, "y" to 20.0)))
            else null
        }
        val state = IndicatorEngine.recompute(def, emptyList(), cs)
        val inst = io.lkanvas.core.indicator.IndicatorInstance("i", def, def.calcParams, "main", "main", null)
        val fake = TestDrawScope(Size(600f, 600f))
        with(fake) {
            drawInstanceFigures(
                inst, state, Viewport(), VisibleRange(0, cs.size - 1),
                { v -> v }, ChartTheme(), 0, lastValueMark = false,
                candles = cs, paneTop = 0f,
            )
        }
        // R8-03：custom row 1 的下一点（row 2）NaN → 自定义段不登记
        assertEquals(0, fake.ops.count { it == "path" }, "下一点缺失的自定义段不登记（R8-03）")
        val pts = batchPoints(fake)
        assertEquals(listOf(4f, 100f, 12f, 101f), pts, "NaN 断开后无跨断段")
    }

    // ---------- R8-02（第八轮复审）：行段样式归所属行 ----------

    @Test
    fun `LINE默认段样式取所属前行（R8-02）`() {
        // 审计复现：3 行 (4,100),(12,101),(20,102)；row 0 线宽 2、row 1/2 线宽 4——
        // 段 (P0,P1) 用 row 0 宽 2、(P1,P2) 用 row 1 宽 4（修复前取后行 [1,2] 合同组）
        val styleRows = mutableListOf<Int>()
        val def = object : IndicatorDefinition {
            override val name = "LINE_STYLED"
            override val shortName = "LS"
            override val calcParams = emptyList<Double>()
            override val precision = 2
            override val plots = listOf(
                PlotDef("v", "V", PlotType.LINE, styles = { row: Int ->
                    styleRows += row
                    mapOf("lineWidth" to if (row == 0) 2.0 else 4.0)
                }),
            )
            override fun initScratch(params: List<Double>) = null
            override fun stepScratch(prev: Any?, c: List<Candle>, p: List<Double>, i: Int) = null
            override fun rowFromScratch(s: Any?, c: List<Candle>, p: List<Double>, i: Int) = mapOf("v" to c[i].close)
        }
        val fake = drawLines(def)
        // 样式回调行号 = 段所属行 [0,1]（修复前 [1,2]）
        assertEquals(listOf(0, 1), styleRows, "默认段样式取前行（R8-02 复现收口）")
        // 两个宽度组 → 两次 native drawLines（修复前合同组一次提交）
        val arrays = fake.drawContext.canvas.nativeCanvas.let { it as TestDrawScope.RecordingNative }.lineArrays
        assertEquals(2, arrays.size, "两段不同样式分组提交")
        assertEquals(listOf(4f, 100f, 12f, 101f), arrays[0].toList(), "段 (P0,P1) 用 row 0 宽 2")
        assertEquals(listOf(12f, 101f, 20f, 102f), arrays[1].toList(), "段 (P1,P2) 用 row 1 宽 4")
    }

    // ---------- R8-03（第八轮复审）：自定义段下一点有效性门禁（审计复现） ----------

    @Test
    fun `LINE自定义段下一点缺失不登记（R8-03）`() {
        // 审计复现：行值 [100,101,NaN,103,104]，仅 row 1 返回自定义点列——
        // 上游登记行 [0,3] 默认段；row 1 下一点缺失，自定义段不登记
        // （修复前多画 1 次 drawPath；正式测试曾把该差异固定）
        val cs = candles(5).mapIndexed { idx, c -> if (idx == 2) c.copy(close = Double.NaN) else c }
        val def = lineTemplatePerRow { row ->
            if (row == 1) mapOf("coordinates" to listOf(mapOf("x" to 40.0, "y" to 10.0), mapOf("x" to 60.0, "y" to 20.0)))
            else null
        }
        val state = IndicatorEngine.recompute(def, emptyList(), cs)
        val inst = io.lkanvas.core.indicator.IndicatorInstance("i", def, def.calcParams, "main", "main", null)
        val fake = TestDrawScope(Size(600f, 600f))
        with(fake) {
            drawInstanceFigures(
                inst, state, Viewport(), VisibleRange(0, cs.size - 1),
                { v -> v }, ChartTheme(), 0, lastValueMark = false,
                candles = cs, paneTop = 0f,
            )
        }
        assertEquals(0, fake.ops.count { it == "path" }, "下一点缺失的自定义段不登记（R8-03 复现收口）")
        val pts = batchPoints(fake)
        // 默认段 row 0 (P0,P1) + row 3 (P3,P4)
        assertEquals(listOf(4f, 100f, 12f, 101f, 28f, 103f, 36f, 104f), pts, "默认段 [0,3] 独立登记")
    }

    // ---------- R9-01（第九轮复审）：下一点取完整结果列，非可见区 ----------

    @Test
    fun `末可见行自定义段在下一点越窗时保留（R9-01）`() {
        // 审计复现：完整行值 [100,101,102]，仅遍历行 [0,1]；row 1 返回覆盖段——
        // 下一点 row 2（值 102）在完整结果列有效 → 覆盖段登记（修复前 i<range.last
        // 可见区误限：attrs 只求值 row 0、drawPath=0）
        val def = lineTemplatePerRow { row ->
            if (row == 1) mapOf("coordinates" to listOf(mapOf("x" to 4.0, "y" to 20.0), mapOf("x" to 12.0, "y" to 30.0)))
            else null
        }
        val cs = candles(3)
        val state = IndicatorEngine.recompute(def, emptyList(), cs)
        val inst = io.lkanvas.core.indicator.IndicatorInstance("i", def, def.calcParams, "main", "main", null)

        fun draw(last: Int): TestDrawScope {
            val fake = TestDrawScope(Size(600f, 600f))
            with(fake) {
                drawInstanceFigures(
                    inst, state, Viewport(), VisibleRange(0, last),
                    { v -> v }, ChartTheme(), 0, lastValueMark = false,
                    candles = cs, paneTop = 0f,
                )
            }
            return fake
        }
        val narrow = draw(1)
        assertEquals(1, narrow.ops.count { it == "path" }, "末可见行覆盖段保留（R9-01 复现收口）")
        assertEquals(1, narrow.ops.count { it == "drawLines" }, "默认段 P0→P1 一次提交")
        assertEquals(listOf(4f, 100f, 12f, 101f), batchPoints(narrow), "默认段 [P0,P1]")
        // 对照：遍历范围扩大到 [0,2] → 相同两项 figure
        val wide = draw(2)
        assertEquals(1, wide.ops.count { it == "path" }, "扩窗后仍 1 覆盖段")
        assertEquals(listOf(4f, 100f, 12f, 101f), batchPoints(wide), "扩窗后默认段相同")
    }

    @Test
    fun `末可见行默认段延伸到窗外有效下一点（R9-01）`() {
        // 上游 segment_k=(P_k,P_{k+1}) 对每可见行登记——末可见行默认段同样
        // 延伸到完整结果列的窗外下一点（数据末端/NaN 不登记）
        val def = lineTemplate(null)
        val cs = candles(3)
        val state = IndicatorEngine.recompute(def, emptyList(), cs)
        val inst = io.lkanvas.core.indicator.IndicatorInstance("i", def, def.calcParams, "main", "main", null)
        val fake = TestDrawScope(Size(600f, 600f))
        with(fake) {
            drawInstanceFigures(
                inst, state, Viewport(), VisibleRange(0, 1),
                { v -> v }, ChartTheme(), 0, lastValueMark = false,
                candles = cs, paneTop = 0f,
            )
        }
        // 段 (P0,P1) + 末可见行段 (P1,P2)（P2 在窗外但数据有效）
        assertEquals(listOf(4f, 100f, 12f, 101f, 12f, 101f, 20f, 102f), batchPoints(fake),
            "末可见行默认段延伸到窗外下一点（与全量遍历同段集）")
        // 真正数据末端：全量遍历 (0,2) → range.last+1 越界 → 不延伸（同段集）
        val fake2 = TestDrawScope(Size(600f, 600f))
        with(fake2) {
            drawInstanceFigures(
                inst, state, Viewport(), VisibleRange(0, 2),
                { v -> v }, ChartTheme(), 0, lastValueMark = false,
                candles = cs, paneTop = 0f,
            )
        }
        assertEquals(batchPoints(fake), batchPoints(fake2), "窗口内外段集一致（数据末端不延伸）")
    }

    // ---------- R9-02（第九轮复审）：默认批次先于覆盖段提交 ----------

    @Test
    fun `重合段默认先提交覆盖后提交（R9-02）`() {
        // 审计复现：3 行 (4,100),(12,101),(20,102)；row 0 默认段红色宽 6，
        // row 1 coordinates 与 (P0,P1) 完全重合、蓝色宽 6——上游先提交红色默认
        // figure 再提交蓝色覆盖 figure（修复后顺序一致；修复前覆盖路径先画、
        // 默认批次循环后统一提交——反盖）
        val def = object : IndicatorDefinition {
            override val name = "LINE_OVERLAP"
            override val shortName = "LO"
            override val calcParams = emptyList<Double>()
            override val precision = 2
            override val plots = listOf(
                PlotDef(
                    "v", "V", PlotType.LINE,
                    styles = { row: Int ->
                        mapOf("color" to if (row == 0) "#FF0000" else "#0000FF", "lineWidth" to 6.0)
                    },
                    attrs = { row: Int ->
                        if (row == 1) mapOf("coordinates" to listOf(mapOf("x" to 4.0, "y" to 100.0), mapOf("x" to 12.0, "y" to 101.0)))
                        else null
                    },
                ),
            )
            override fun initScratch(params: List<Double>) = null
            override fun stepScratch(prev: Any?, c: List<Candle>, p: List<Double>, i: Int) = null
            override fun rowFromScratch(s: Any?, c: List<Candle>, p: List<Double>, i: Int) = mapOf("v" to c[i].close)
        }
        val fake = drawLines(def)
        // 提交顺序：默认批次（drawLines）先于覆盖段（path）
        val seq = fake.ops.filter { it == "drawLines" || it == "path" }
        assertEquals(listOf("drawLines", "path"), seq, "默认批次先提交、覆盖段后提交（R9-02 复现收口）")
        // 默认批次 = row 0 样式与几何 (P0,P1)
        assertEquals(listOf(4f, 100f, 12f, 101f), batchPoints(fake), "默认批次对应 row 0 红色样式与几何")
    }

    // ---------- R10-01（第十轮复审）：默认样式折返不重排提交顺序 ----------

    @Test
    fun `默认样式ABA折返保持行序提交（R10-01）`() {
        // 审计复现：4 行 (4,100),(12,110),(20,100),(28,110)，段所属行 0/1/2
        // 颜色红/蓝/红、线宽 6——上游三个连续批次顺序 [0,1,2]；修复前 segFor
        // 全局复用最早红组 → 提交 [0,2,1]
        val styleRows = mutableListOf<Int>()
        val def = object : IndicatorDefinition {
            override val name = "LINE_RETURN"
            override val shortName = "LR"
            override val calcParams = emptyList<Double>()
            override val precision = 2
            override val plots = listOf(
                PlotDef("v", "V", PlotType.LINE, styles = { row: Int ->
                    styleRows += row
                    mapOf("color" to if (row == 1) "#0000FF" else "#FF0000", "lineWidth" to 6.0)
                }),
            )
            override fun initScratch(params: List<Double>) = null
            override fun stepScratch(prev: Any?, c: List<Candle>, p: List<Double>, i: Int) = null
            override fun rowFromScratch(s: Any?, c: List<Candle>, p: List<Double>, i: Int) =
                mapOf("v" to if (i % 2 == 0) 100.0 else 110.0)
        }
        val cs = candles(4).mapIndexed { idx, c ->
            c.copy(close = if (idx % 2 == 0) 100.0 else 110.0)
        }
        val state = IndicatorEngine.recompute(def, emptyList(), cs)
        val inst = io.lkanvas.core.indicator.IndicatorInstance("i", def, def.calcParams, "main", "main", null)
        val fake = TestDrawScope(Size(600f, 600f))
        with(fake) {
            drawInstanceFigures(
                inst, state, Viewport(), VisibleRange(0, cs.size - 1),
                { v -> v }, ChartTheme(), 0, lastValueMark = false,
                candles = cs, paneTop = 0f,
            )
        }
        // 样式回调取值正确（[0,1,2]——与审计一致，差异在分组提交）
        assertEquals(listOf(0, 1, 2), styleRows, "样式回调行序")
        // 三个连续批次按行序提交（修复前 [0,2,1] 两批次）
        val arrays = fake.drawContext.canvas.nativeCanvas.let { it as TestDrawScope.RecordingNative }.lineArrays
        assertEquals(3, arrays.size, "红/蓝/红三个连续批次")
        assertEquals(listOf(4f, 100f, 12f, 110f), arrays[0].toList(), "批次 1 = 行 0（红）")
        assertEquals(listOf(12f, 110f, 20f, 100f), arrays[1].toList(), "批次 2 = 行 1（蓝）")
        assertEquals(listOf(20f, 100f, 28f, 110f), arrays[2].toList(), "批次 3 = 行 2（红）")
    }

    @Test
    fun `连续同样式默认段仍一次批量提交（R10-01基准）`() {
        // 审计验收条件：连续 A→A→A 仍可一次批量提交（批量优化保留）
        val def = object : IndicatorDefinition {
            override val name = "LINE_UNIFORM"
            override val shortName = "LU"
            override val calcParams = emptyList<Double>()
            override val precision = 2
            override val plots = listOf(
                PlotDef("v", "V", PlotType.LINE, styles = { _: Int -> mapOf("color" to "#FF0000", "lineWidth" to 6.0) }),
            )
            override fun initScratch(params: List<Double>) = null
            override fun stepScratch(prev: Any?, c: List<Candle>, p: List<Double>, i: Int) = null
            override fun rowFromScratch(s: Any?, c: List<Candle>, p: List<Double>, i: Int) = mapOf("v" to c[i].close)
        }
        val fake = drawLines(def, candlesCount = 4)
        val arrays = fake.drawContext.canvas.nativeCanvas.let { it as TestDrawScope.RecordingNative }.lineArrays
        assertEquals(1, arrays.size, "连续同样式一次提交")
        assertEquals(
            listOf(4f, 100f, 12f, 101f, 12f, 101f, 20f, 102f, 20f, 102f, 28f, 103f),
            arrays[0].toList(),
            "三段合并单批次",
        )
    }
}
