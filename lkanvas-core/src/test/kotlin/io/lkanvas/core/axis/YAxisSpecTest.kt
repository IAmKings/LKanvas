package io.lkanvas.core.axis

import kotlin.math.abs
import kotlin.math.log10
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A-17/A-18：signed-log 往返、percentage 零基准（D-01）、自定义轴保护。 */
class YAxisSpecTest {

    private fun Double.tol(o: Double) = 1e-9 + 1e-9 * maxOf(abs(this), abs(o))

    // ---------- A-17：signed-log 对负/零/小正值往返 ----------

    @Test
    fun `logarithm负零小正往返一致`() {
        val values = listOf(-1000.0, -100.5, -1.0, -0.001, 0.0, 0.001, 0.5, 1.0, 99.9, 12345.6)
        for (v in values) {
            val real = YAxisMath.valueToReal(YAxisSpec.Logarithm, v)
            assertTrue(real.isFinite(), "v=$v real 有限")
            val back = YAxisMath.realToValue(YAxisSpec.Logarithm, real)
            assertTrue(abs(back - v) <= abs(v).tol(back), "往返 v=$v → $real → $back")
        }
    }

    @Test
    fun `logarithm公式为带符号log10_1加绝对值`() {
        assertEquals(log10(101.0), YAxisMath.valueToReal(YAxisSpec.Logarithm, 100.0), 1e-12)
        assertEquals(-log10(101.0), YAxisMath.valueToReal(YAxisSpec.Logarithm, -100.0), 1e-12)
        assertEquals(0.0, YAxisMath.valueToReal(YAxisSpec.Logarithm, 0.0), 1e-12)
        // 小正值：log10(1.001)
        assertEquals(log10(1.001), YAxisMath.valueToReal(YAxisSpec.Logarithm, 0.001), 1e-12)
    }

    @Test
    fun `logarithm轴值与像素往返且刻度显示原价`() {
        val spec = YAxisSpec.Logarithm
        val (from, to) = 50.0 to 200.0
        for (v in listOf(50.0, 80.0, 123.45, 200.0)) {
            val y = YAxisMath.valueToY(spec, v, from, to, 0.0, 300.0)
            val back = YAxisMath.yToValue(spec, y, from, to, 0.0, 300.0)
            assertTrue(abs(back - v) <= abs(v).tol(back), "往返 v=$v back=$back")
        }
        val ticks = YAxisMath.ticks(spec, from, to)
        assertTrue(ticks.ticks.size >= 2)
        // 刻度显示的是原价（实数空间逆变换），且位于范围内
        ticks.ticks.forEach { t ->
            assertTrue(t.display == null || (t.display!! >= 49.9 && t.display!! <= 200.1), "刻度 ${t.display} 在范围内")
        }
    }

    // ---------- A-17/D-01：percentage 零基准 ----------

    @Test
    fun `percentage正常基准映射与首可见close口径`() {
        val base = 100.0
        val spec = YAxisSpec.Percentage(base)
        assertEquals(0.0, YAxisMath.valueToReal(spec, 100.0), 1e-12, "基准自身为 0%")
        assertEquals(50.0, YAxisMath.valueToReal(spec, 150.0), 1e-12)
        assertEquals(-50.0, YAxisMath.valueToReal(spec, 50.0), 1e-12)
        assertEquals(150.0, YAxisMath.realToValue(spec, 50.0), 1e-12)
        // 值↔像素往返
        val y = YAxisMath.valueToY(spec, 120.0, 90.0, 130.0, 0.0, 200.0)
        val back = YAxisMath.yToValue(spec, y, 90.0, 130.0, 0.0, 200.0)
        assertTrue(abs(back - 120.0) < 1e-9)
        // 刻度文本为百分比
        val ticks = YAxisMath.ticks(spec, 90.0, 130.0)
        assertTrue(ticks.ticks.all { it.text.endsWith("%") })
    }

    @Test
    fun `percentage零基准按D01处理无NaN且像素范围有限稳定`() {
        val spec = YAxisSpec.Percentage(0.0)
        // 值映射有限（全部折到稳定 real 区间）
        val y1 = YAxisMath.valueToY(spec, 123.0, 100.0, 200.0, 0.0, 300.0)
        val y2 = YAxisMath.valueToY(spec, 150.0, 100.0, 200.0, 0.0, 300.0)
        assertTrue(y1.isFinite() && y2.isFinite(), "无 NaN/Infinity")
        assertTrue(y1 == y2, "零基准不产生虚假涨幅区分")
        // 刻度标签全部为「—」
        val ticks = YAxisMath.ticks(spec, 100.0, 200.0)
        assertTrue(ticks.ticks.isNotEmpty())
        assertTrue(ticks.ticks.all { it.text == "—" }, "D-01：零基准显示「—」")
        // null 基准（空数据）同样安全
        val nullBase = YAxisSpec.Percentage(null)
        assertTrue(YAxisMath.valueToY(nullBase, 1.0, 0.0, 10.0, 0.0, 100.0).isFinite())
        assertTrue(YAxisMath.ticks(nullBase, 0.0, 10.0).ticks.all { it.text == "—" })
    }

    // ---------- A-18：自定义轴保护 ----------

    private class BrokenTickAxis(
        private val ticksProvider: (Double, Double) -> List<Double>?,
    ) : CustomYAxis {
        override fun valueToReal(value: Double) = value
        override fun realToValue(real: Double) = real
        override fun customTicks(realFrom: Double, realTo: Double) = ticksProvider(realFrom, realTo)
    }

    @Test
    fun `自定义刻度重复值被降级为默认并报告`() {
        val spec = YAxisSpec.Custom(BrokenTickAxis { rf, rt -> List(5) { rf + (rt - rf) * 0.25 } })
        val result = YAxisMath.ticks(spec, 0.0, 100.0)
        assertTrue(result.degradedFromCustom, "重复刻度必须降级")
        assertTrue(result.degradeReason != null)
        assertTrue(result.ticks.size >= 2, "降级后仍有默认刻度")
    }

    @Test
    fun `自定义刻度逆序被拒绝`() {
        val spec = YAxisSpec.Custom(BrokenTickAxis { rf, rt -> listOf(rt, rf) })
        val result = YAxisMath.ticks(spec, 0.0, 100.0)
        assertTrue(result.degradedFromCustom)
    }

    @Test
    fun `自定义刻度含NaN或Infinity被拒绝`() {
        val spec = YAxisSpec.Custom(BrokenTickAxis { rf, rt -> listOf(rf, Double.NaN, rt) })
        assertTrue(YAxisMath.ticks(spec, 0.0, 100.0).degradedFromCustom)
        val spec2 = YAxisSpec.Custom(BrokenTickAxis { rf, rt -> listOf(rf, Double.POSITIVE_INFINITY, rt) })
        assertTrue(YAxisMath.ticks(spec2, 0.0, 100.0).degradedFromCustom)
    }

    @Test
    fun `自定义刻度超量被拒绝不挂死`() {
        val spec = YAxisSpec.Custom(BrokenTickAxis { rf, rt -> (0..100).map { rf + (rt - rf) * it / 100.0 } })
        val result = YAxisMath.ticks(spec, 0.0, 100.0)
        assertTrue(result.degradedFromCustom, "超过 30 个刻度拒绝")
    }

    @Test
    fun `合法自定义刻度被采用`() {
        val spec = YAxisSpec.Custom(BrokenTickAxis { _, _ -> listOf(0.0, 25.0, 50.0, 75.0, 100.0) })
        val result = YAxisMath.ticks(spec, 0.0, 100.0)
        assertTrue(!result.degradedFromCustom)
        assertEquals(listOf(0.0, 25.0, 50.0, 75.0, 100.0), result.ticks.map { it.display })
    }

    @Test
    fun `自定义变换非有限输出时real范围回退identity`() {
        val spec = YAxisSpec.Custom(object : CustomYAxis {
            override fun valueToReal(value: Double) = Double.NaN
            override fun realToValue(real: Double) = real
        })
        val (rf, rt) = YAxisMath.realRange(spec, 0.0, 100.0)
        assertEquals(0.0, rf, 1e-12)
        assertEquals(100.0, rt, 1e-12, "NaN 变换回退 identity，范围有限")
    }
}
