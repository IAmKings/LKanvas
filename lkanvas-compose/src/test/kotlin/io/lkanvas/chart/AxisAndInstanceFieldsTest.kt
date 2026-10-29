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

    // ---------- 0.5.x 多轴完整版：position/inside 元数据 ----------

    @Test
    fun extraAxisMeta_positionInsideSynced() {
        val chart = newChart()
        val st = chart.state
        chart.createYAxis(YAxisOverride(id = "ax_in", inside = true))
        chart.createYAxis(YAxisOverride(id = "ax_left", position = AxisPosition.LEFT))
        chart.createYAxis(YAxisOverride(id = "ax_right"))
        assertTrue(st.extraYAxisMeta["ax_in"]!!.inside, "inside 同步")
        assertTrue(st.extraYAxisMeta["ax_left"]!!.positionLeft, "LEFT 同步")
        assertTrue(!st.extraYAxisMeta["ax_right"]!!.inside && !st.extraYAxisMeta["ax_right"]!!.positionLeft, "right 默认独立列")
        // override 翻转
        chart.overrideYAxis(YAxisOverride(id = "ax_right", inside = true))
        assertTrue(st.extraYAxisMeta["ax_right"]!!.inside, "override 翻转 inside")
        LKanvasChart.dispose(chart)
    }

    // ---------- 0.5.x 多轴完整版：自定义变换轴 ----------

    /** log10 变换轴（宿主自定义 CustomYAxis 契约实现）。 */
    private val log10Axis = object : io.lkanvas.core.axis.CustomYAxis {
        override fun valueToReal(value: Double): Double =
            if (value < 0) -kotlin.math.log10(1 - value) else kotlin.math.log10(1 + value)
        override fun realToValue(real: Double): Double =
            if (real < 0) 1 - Math.pow(10.0, -real) else Math.pow(10.0, real) - 1
    }

    @Test
    fun customTransformAxis_syncsAndProjects() {
        val chart = newChart()
        val st = chart.state
        // 建带 customAxis 的轴 → 同步到渲染消费源
        chart.createYAxis(YAxisOverride(id = "ax_log", customAxis = log10Axis))
        assertTrue(st.extraYAxisCustom.containsKey("ax_log"))
        // override 不带 customAxis → 按上游 merge 语义保留（null=未提供不可区分清除）
        chart.overrideYAxis(YAxisOverride(id = "ax_log"))
        assertTrue(st.extraYAxisCustom.containsKey("ax_log"), "override 保留 custom")
        // 提供新 customAxis → 覆盖
        chart.overrideYAxis(YAxisOverride(id = "ax_log", customAxis = log10Axis))
        assertTrue(st.extraYAxisCustom.containsKey("ax_log"), "override 覆盖 custom")
        // removeYAxis → 清理
        chart.removeYAxis(YAxisFilter(id = "ax_log"))
        assertTrue(st.extraYAxisCustom["ax_log"] === null, "移除后 custom 清理")
        // 重建并投影校验：值域经 log10 后压缩（万级价格 → real 域 ~4）
        chart.createYAxis(YAxisOverride(id = "ax_log2", customAxis = log10Axis))
        kotlinx.coroutines.runBlocking {
            st.reset(
                io.lkanvas.core.store.MarketKey("T", "1m"),
                listOf(candleOf(1, 9999.0), candleOf(2, 10000.0), candleOf(3, 10001.0)),
            )
        }
        chart.createIndicator("MA", calcParams = listOf(2.0), yAxisId = "ax_log2")
        val (alo, ahi) = st.extraAxisRange("ax_log2", 0, 2)!!
        assertTrue(ahi > 9900, "原始值域（万级）：$alo~$ahi")
        // log10 real 域显著压缩：realRange 上界 ≈ log10(10001+1) ≈ 4.0
        val (rf, rt) = io.lkanvas.core.axis.YAxisMath.realRange(
            io.lkanvas.core.axis.YAxisSpec.Custom(log10Axis), alo, ahi,
        )
        assertTrue(rt < 5.0 && rt > 3.0, "log10 real 域上界 ≈4：$rf~$rt")
        assertTrue(rt - rf < 0.01, "万级窄幅 → real 域极窄（变换生效）")
        LKanvasChart.dispose(chart)
    }

    // ---------- 0.5.x 动态副图 pane ----------

    @Test
    fun dynamicSubPane_createOnBindPruneOnRemove() {
        val chart = newChart()
        val st = chart.state
        // 无自定义 pane
        assertTrue(st.extraPanes.value.isEmpty())
        // 绑定自定义 paneId 的指标 → pane 自动创建 + 布局生成
        val id = chart.createIndicator("MA", calcParams = listOf(5.0), paneId = "pane_a")
        assertNotNull(id)
        assertTrue(st.extraPanes.value.contains("pane_a"), "pane 应创建：${st.extraPanes.value}")
        assertTrue(st.extraPaneLayout.any { it.first == "pane_a" }, "布局应含 pane_a：${st.extraPaneLayout}")
        // 两个指标同 pane → 仍一个 pane
        chart.createIndicator("MA", calcParams = listOf(10.0), paneId = "pane_a")
        assertEquals(1, st.extraPanes.value.size)
        // 删除 pane 内全部指标 → pane 移除
        chart.removeIndicator(ChartState.IndicatorFilter(paneId = "pane_a"))
        assertTrue(st.extraPanes.value.isEmpty(), "pane 空后移除")
        assertTrue(st.extraPaneLayout.isEmpty())
        // 内置 pane 不进动态列表
        chart.createIndicator("MA", calcParams = listOf(5.0), paneId = "main")
        assertTrue(st.extraPanes.value.isEmpty())
        LKanvasChart.dispose(chart)
    }

    // ---------- 0.5.x pane 排序 ----------

    @Test
    fun moveExtraPane_reordersRenderSequence() {
        val chart = newChart()
        val st = chart.state
        chart.createIndicator("MA", calcParams = listOf(5.0), paneId = "pane_a")
        chart.createIndicator("MA", calcParams = listOf(10.0), paneId = "pane_b")
        chart.createIndicator("MA", calcParams = listOf(20.0), paneId = "pane_c")
        // 建立顺序 pane_a, pane_b, pane_c（创建序）
        assertEquals(listOf("pane_a", "pane_b", "pane_c"), st.extraPanes.value)
        // pane_c 移到最前
        chart.moveExtraPane("pane_c", 0)
        assertEquals(listOf("pane_c", "pane_a", "pane_b"), st.extraPanes.value)
        // 布局（从上到下）跟随新序
        assertEquals("pane_c", st.extraPaneLayout.first().first)
        // 未知 paneId 忽略；越界钳制到尾部（pane_c 已在首位 → 移至末位）
        chart.moveExtraPane("nope", 0)
        chart.moveExtraPane("pane_c", 99)
        assertEquals(listOf("pane_a", "pane_b", "pane_c"), st.extraPanes.value, "越界钳制到末位")
        LKanvasChart.dispose(chart)
    }

    // ---------- 0.5.x 动态副图分隔条 ----------

    @Test
    fun dynamicSubPane_separatorHitAndDragResize() {
        val chart = newChart()
        val st = chart.state
        // 模拟布局（容器 1200：main+vol+macd+timeAxis + 1 extra）
        st.onLayout(800f, ChartState.PaneHeights(600f, 200f, 200f, 60f))
        chart.createIndicator("MA", calcParams = listOf(5.0), paneId = "pane_a")
        val layout = st.extraPaneLayout.first { it.first == "pane_a" }
        val defaultH = layout.second.second - layout.second.first
        // 分隔命中：pane 下边界 y
        assertEquals("pane_a", st.extraPaneSeparatorAt(layout.second.second, 14f))
        // 内置分隔优先：主|VOL 分隔处不返回 extra
        assertEquals(null, st.extraPaneSeparatorAt(st.paneHeightsPx.main, 14f))
        // 拖动：+80px → 高度增大且 main 压缩
        val mainBefore = st.paneHeightsPx.main
        st.adjustExtraPane("pane_a", defaultH, 80f)
        val after = st.extraPaneLayout.first { it.first == "pane_a" }
        val newH = after.second.second - after.second.first
        assertEquals(defaultH + 80f, newH, "拖动后高度")
        assertEquals(mainBefore - 80f, st.paneHeightsPx.main, "main 相应压缩")
        // 最小高度钳制
        st.adjustExtraPane("pane_a", defaultH, -10_000f)
        assertTrue(
            st.extraPaneLayout.first { it.first == "pane_a" }.let { it.second.second - it.second.first } >= 40f,
            "最小副图高钳制",
        )
        LKanvasChart.dispose(chart)
    }

    // ---------- isStack 语义（CH-044 收口；上游 Store.addIndicator） ----------

    @Test
    fun createIndicator_isStackReplaceAndStack() {
        val chart = newChart()
        // isStack=true：同 pane 叠加两个实例
        val a = chart.createIndicator("MA", calcParams = listOf(5.0), paneId = "main", isStack = true)!!
        val b = chart.createIndicator("MA", calcParams = listOf(10.0), paneId = "main", isStack = true)!!
        val mainAfterStack = chart.getIndicators(ChartState.IndicatorFilter(paneId = "main"))
        assertTrue(mainAfterStack.any { it.id == a } && mainAfterStack.any { it.id == b }, "叠加：两实例共存")
        // isStack=false（上游缺省）：清空该 pane 全部实例后创建（上游 Store.addIndicator 语义）
        val c = chart.createIndicator("MA", calcParams = listOf(20.0), paneId = "main", isStack = false)!!
        val mainAfterReplace = chart.getIndicators(ChartState.IndicatorFilter(paneId = "main"))
        assertTrue(mainAfterReplace.any { it.id == c }, "替换：新实例存在")
        assertTrue(mainAfterReplace.none { it.id == a || it.id == b }, "替换：旧实例被清除")
        // 不同 pane 不受替换影响（vol pane 放一个实例验证隔离）
        val volInst = chart.createIndicator("MA", calcParams = listOf(8.0), paneId = "vol", isStack = true)!!
        chart.createIndicator("MA", calcParams = listOf(30.0), paneId = "main", isStack = false)
        assertTrue(chart.getIndicators(ChartState.IndicatorFilter(paneId = "vol")).any { it.id == volInst }, "vol pane 实例不受 main 替换影响")
        LKanvasChart.dispose(chart)
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
        assertEquals(true, state.extraYAxisMeta["ax1"]?.reverse)
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

    // ---------- 0.7.0 R06：轴注册/实例/执行统一 ----------

    @Test
    fun `公开registerYAxis进入构造表（R06）`() {
        // 公开注册自定义模板（log spec）→ createYAxis(template=name) 实际使用
        io.lkanvas.chart.LkanvasRegistry.registerYAxis(object : io.lkanvas.chart.LkanvasRegistry.AxisTemplate {
            override val name = "audit_log_axis"
            override fun createSpec(base: Double?) = io.lkanvas.core.axis.YAxisSpec.Logarithm
        })
        val chart = LKanvasChart.init()
        chart.createYAxis(io.lkanvas.chart.YAxisOverride(id = "ax_pub", template = "audit_log_axis"))!!
        assertTrue(
            chart.state.extraYAxisSpec["ax_pub"] is io.lkanvas.core.axis.YAxisSpec.Logarithm,
            "公开注册模板进入执行映射（复审 R06 复现收口）",
        )
        assertEquals(
            io.lkanvas.core.axis.YAxisSpec.Logarithm,
            chart.state.effectiveExtraAxisSpec("ax_pub"),
            "转换与绘制同源 spec",
        )
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `额外log轴不改写主轴spec（R06）`() {
        val chart = LKanvasChart.init()
        val before = chart.state.mainAxisSpec
        chart.createYAxis(io.lkanvas.chart.YAxisOverride(id = "ax_log", paneId = "main", template = "logarithm"))!!
        assertEquals(before, chart.state.mainAxisSpec, "额外轴不改写 mainAxisSpec（复审 R06 复现收口）")
        // 额外轴自身按 log 投影
        assertTrue(chart.state.extraYAxisSpec["ax_log"] is io.lkanvas.core.axis.YAxisSpec.Logarithm)
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `动态pane的reverse轴进入执行映射（R06）`() {
        val chart = LKanvasChart.init()
        val rsiPane = "pane_rsi_audit"
        // 数据先就绪（与 multiAxis 用例同模式：runBlocking reset 后再建指标）
        kotlinx.coroutines.runBlocking {
            chart.state.reset(
                io.lkanvas.core.store.MarketKey("T", "1m"),
                (1..40L).map { candleOf(it, 100.0 + it + (if (it % 5 == 0L) -15.0 else 0.0)) },
            )
        }
        val id = chart.createIndicator("RSI", paneId = rsiPane)!!
        org.junit.Assert.assertNotNull(id)
        val ax = chart.createYAxis(io.lkanvas.chart.YAxisOverride(id = "ax_rsi", paneId = rsiPane, reverse = true))!!
        // 复审 R06 复现：syncExtraAxes 只同步 paneId=main——动态 pane 轴不进映射
        assertEquals(true, chart.state.extraYAxisMeta["ax_rsi"]?.reverse, "副 pane 轴进入执行映射")
        // 绑定实例到轴
        val boundId = chart.getIndicators(io.lkanvas.compose.state.ChartState.IndicatorFilter(paneId = rsiPane)).first().id
        chart.overrideIndicator(io.lkanvas.compose.state.ChartState.IndicatorFilter(id = boundId), yAxisId = ax)
        // pane 几何（无 Compose 挂载的单元测试环境）
        chart.state.onLayout(600f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        val y = chart.state.axisValueToY(ax, 50.0)
        assertNotNull(y, "动态 pane 轴可转换（复审 R06 复现收口）")
        assertTrue(y != null && y > 0, "动态 pane 轴转换有效")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `overrideXAxis的scrollZoomEnabled门禁轴区缩放（R06余项）`() {
        val state = ChartState()
        val chart = LKanvasChart.init(state)
        chart.overrideXAxis(io.lkanvas.chart.XAxisOverride(scrollZoomEnabled = false))
        val barBefore = state.viewport.barSpacing
        state.axisZoomBy(200f)
        assertEquals(barBefore, state.viewport.barSpacing, "轴区缩放被门禁")
        chart.overrideXAxis(io.lkanvas.chart.XAxisOverride(scrollZoomEnabled = true))
        state.axisZoomBy(200f)
        assertTrue(state.viewport.barSpacing != barBefore, "恢复后可轴区缩放")
        LKanvasChart.dispose(chart)
    }


    // ---------- 0.7.0 R07：pane 集合单一来源 ----------

    @Test
    fun `动态pane可查询与设高（R07）`() {
        val chart = LKanvasChart.init()
        kotlinx.coroutines.runBlocking {
            chart.state.reset(
                io.lkanvas.core.store.MarketKey("T", "1m"),
                (1..40L).map { candleOf(it, 100.0 + it) },
            )
        }
        val pane = "pane_r07"
        chart.state.onLayout(600f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        chart.createIndicator("RSI", paneId = pane)!!
        // 复审 R07 复现：getPaneOptions(newPane) 返回空
        val opts = chart.getPaneOptions(pane)
        assertEquals(1, opts.size, "动态 pane 可查询")
        assertTrue((opts[0].height ?: 0.0) > 0, "动态 pane 有高度")
        // setPaneOptions 对动态 pane 生效
        chart.setPaneOptions(io.lkanvas.chart.PaneOptions(pane, 120.0))
        val after = chart.getPaneOptions(pane)
        assertEquals(120.0, after[0].height ?: -1.0, 1e-6, "动态 pane 设高生效")
        // 全列表含动态 pane
        assertTrue(chart.getPaneOptions().any { it.id == pane })
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `overrideIndicator迁移paneId维护pane集合（R07）`() {
        val chart = LKanvasChart.init()
        kotlinx.coroutines.runBlocking {
            chart.state.reset(
                io.lkanvas.core.store.MarketKey("T", "1m"),
                (1..40L).map { candleOf(it, 100.0 + it) },
            )
        }
        val srcPane = "pane_src"
        val dstPane = "pane_dst"
        val id = chart.createIndicator("RSI", paneId = srcPane)!!
        assertTrue(chart.state.extraPanes.value.contains(srcPane))
        // 迁移到新 pane：目标 pane 创建
        chart.overrideIndicator(io.lkanvas.compose.state.ChartState.IndicatorFilter(id = id), paneId = dstPane)
        assertTrue(chart.state.extraPanes.value.contains(dstPane), "目标 pane 创建（复审 R07 复现收口）")
        // 原 pane 空了自动回收
        assertTrue(!chart.state.extraPanes.value.contains(srcPane), "空 pane 回收")
        assertEquals(dstPane, chart.getIndicatorByInstanceId(id)!!.paneId)
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `vol与macd按需存在且无实例不占布局（R07）`() {
        val chart = LKanvasChart.init()
        kotlinx.coroutines.runBlocking {
            chart.state.reset(
                io.lkanvas.core.store.MarketKey("T", "1m"),
                (1..40L).map { candleOf(it, 100.0 + it) },
            )
        }
        chart.state.onLayout(600f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        // 无 VOL/MACD 实例 → pane 集合不含 vol/macd（高度 0）
        assertTrue(chart.state.paneList().containsAll(listOf("main")), "main 恒在")
        assertEquals(0f, chart.state.paneHeightsPx.vol, "vol 无实例不占布局（严格上游默认）")
        assertEquals(0f, chart.state.paneHeightsPx.macd, "macd 无实例不占布局")
        // 创建 VOL/MACD 后 pane 出现
        chart.createIndicator("VOL", paneId = "vol")
        chart.createIndicator("MACD", paneId = "macd")
        assertTrue(chart.state.paneList().containsAll(listOf("vol", "macd")))
        assertTrue(chart.state.paneHeightsPx.vol > 0f)
        assertTrue(chart.state.paneHeightsPx.macd > 0f)
        // 严格 paneId：volumePaneResult 不再有按名回退
        org.junit.Assert.assertNotNull(chart.state.volumePaneResult())
        org.junit.Assert.assertNotNull(chart.state.macdPaneResult())
        LKanvasChart.dispose(chart)
    }
}
