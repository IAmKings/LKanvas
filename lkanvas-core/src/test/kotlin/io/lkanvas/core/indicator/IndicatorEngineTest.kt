package io.lkanvas.core.indicator

import io.lkanvas.core.data.Candle
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 测试用滚动均值模板（与上游 MA 公式同构，正式 MA 在 T-11 以 fixtures 对照）。 */
private class RollingMeanDefinition(
    override val name: String = "RollingMean",
    override val calcParams: List<Double> = listOf(5.0),
) : IndicatorDefinition {
    override val shortName = "RM"
    override val precision = 2
    override val plots = listOf(PlotDef("ma1", "MA5: ", PlotType.LINE))
    override val incremental = IncrementalCapability.REPLACE_APPEND

    private class Scratch(val sums: DoubleArray, val periods: IntArray)

    override fun initScratch(params: List<Double>): Any? =
        Scratch(DoubleArray(params.size), params.map { it.toInt() }.toIntArray())

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val sums = s.sums.copyOf()
        val close = candles[i].close
        for (k in s.periods.indices) {
            val p = s.periods[k]
            sums[k] += close
            // 状态语义：第 i 根后的 sums = 恰好以 i 结尾的 p 长窗口和
            if (i >= p) sums[k] -= candles[i - p].close
        }
        return Scratch(sums, s.periods)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        return if (i >= s.periods[0] - 1) mapOf("ma1" to s.sums[0] / s.periods[0]) else emptyMap()
    }
}

/** 只支持全量的模板（能力清单：FULL_ONLY）。 */
private class FullOnlyDefinition : IndicatorDefinition {
    override val name = "FullOnly"
    override val shortName = "FO"
    override val calcParams = listOf(3.0)
    override val precision = 2
    override val plots = listOf(PlotDef("v", "V: ", PlotType.LINE))
    override fun initScratch(params: List<Double>): Any? = 0.0
    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? =
        (prev as Double) + candles[i].close
    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> =
        mapOf("v" to (scratch as Double) / (i + 1))
}

/** 必然抛错的模板。 */
private class ThrowingDefinition : IndicatorDefinition {
    override val name = "Boom"
    override val shortName = "BOOM"
    override val calcParams = listOf(2.0)
    override val precision = 2
    override val plots = listOf(PlotDef("x", "X: ", PlotType.LINE))
    override fun initScratch(params: List<Double>): Any? = null
    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? =
        throw IllegalStateException("模板故意抛错 @ $i")
    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> = emptyMap()
}

class IndicatorEngineTest {

    private fun candle(ts: Long, close: Double) =
        Candle(ts, close, close + 1.0, close - 1.0, close, 1000.0)

    private fun genCandles(n: Int, seed: Long = 99): MutableList<Candle> {
        val rand = Random(seed)
        var price = 100.0
        return (0 until n).map {
            price += (rand.nextDouble() - 0.5) * 2
            candle(it.toLong(), price)
        }.toMutableList()
    }

    private fun assertResultsEqual(a: IndicatorResultState, b: IndicatorResultState, label: String) {
        assertEquals(a.result.keys, b.result.keys, "$label 输出键一致")
        for (k in a.result.keys) {
            val x = a.result[k]
            val y = b.result[k]
            assertEquals(x.size, y.size, "$label 列 $k 长度")
            for (i in x.indices) {
                assertTrue(
                    (x[i].isNaN() && y[i].isNaN()) || x[i] == y[i],
                    "$label 列 $k 第 $i 位不一致：${x[i]} vs ${y[i]}",
                )
            }
        }
    }

    // ---------- A-24：随机变更序列，增量与全量逐位一致 ----------

    @Test
    fun `五千步随机变更序列增量与全量一致`() {
        val def = RollingMeanDefinition()
        val rand = Random(2026)
        val candles = genCandles(60)
        var state = IndicatorEngine.recompute(def, def.calcParams, candles)

        // 参照：每步都做全量
        for (step in 0 until 5_000) {
            when (rand.nextInt(3)) {
                0 -> { // 替换末根
                    val last = candles.last()
                    val newClose = last.close + (rand.nextDouble() - 0.5)
                    candles[candles.size - 1] = candle(last.timestamp, newClose)
                    state = IndicatorEngine.advance(def, state, candles, ChangeKind.ReplaceLatest)
                }
                1 -> { // 追加 1 根
                    val last = candles.last()
                    candles.add(candle(last.timestamp + 1, last.close + (rand.nextDouble() - 0.5)))
                    state = IndicatorEngine.advance(def, state, candles, ChangeKind.Append(1))
                }
                else -> { // 连续替换（模拟高频末根更新）
                    repeat(1 + rand.nextInt(5)) {
                        val last = candles.last()
                        candles[candles.size - 1] = candle(last.timestamp, last.close + (rand.nextDouble() - 0.5) * 0.1)
                        state = IndicatorEngine.advance(def, state, candles, ChangeKind.ReplaceLatest)
                    }
                }
            }
            if (step % 97 == 0) {
                val full = IndicatorEngine.recompute(def, def.calcParams, candles)
                assertResultsEqual(full, state, "step=$step")
            }
        }
        // 终态全量对照
        assertResultsEqual(
            IndicatorEngine.recompute(def, def.calcParams, candles),
            state,
            "终态",
        )
        assertTrue(state.mode != ComputeMode.FULL_FALLBACK, "该模板不应回退全量")
    }

    @Test
    fun `前插回退全量且结果正确`() {
        val def = RollingMeanDefinition()
        val candles = genCandles(50)
        var state = IndicatorEngine.recompute(def, def.calcParams, candles)
        val prepended = (0 until 20).map { candle(-1L - it, 50.0 + it) }.reversed()
        candles.addAll(0, prepended)
        state = IndicatorEngine.advance(def, state, candles, ChangeKind.Prepend(20))
        assertEquals(ComputeMode.FULL_FALLBACK, state.mode)
        assertResultsEqual(IndicatorEngine.recompute(def, def.calcParams, candles), state, "前插后")
    }

    @Test
    fun `全量only模板追加也走全量但结果正确`() {
        val def = FullOnlyDefinition()
        val candles = genCandles(10)
        var state = IndicatorEngine.recompute(def, def.calcParams, candles)
        candles.add(candle(10, 42.0))
        state = IndicatorEngine.advance(def, state, candles, ChangeKind.Append(1))
        assertEquals(ComputeMode.FULL_FALLBACK, state.mode)
        val expect = (candles.sumOf { it.close } + 0.0) / candles.size
        assertEquals(expect, state.result["v"].last(), 1e-9)
    }

    // ---------- 缺失/预热语义 ----------

    @Test
    fun `预热区间为NaN且有效值从窗口满开始`() {
        val def = RollingMeanDefinition(calcParams = listOf(5.0))
        val candles = (0 until 10).map { candle(it.toLong(), it + 1.0) } // close 1..10
        val state = IndicatorEngine.recompute(def, def.calcParams, candles)
        val ma = state.result["ma1"]
        for (i in 0 until 4) assertTrue(ma[i].isNaN(), "第 $i 位应 Missing")
        assertEquals((1 + 2 + 3 + 4 + 5) / 5.0, ma[4], 1e-12)
        assertEquals((6 + 7 + 8 + 9 + 10) / 5.0, ma[9], 1e-12)
    }

    // ---------- A-25：同名不同参数实例隔离 ----------

    @Test
    fun `同名不同参数实例互不影响且参数改回后正确重算`() {
        val registry = IndicatorRegistry()
        assertTrue(registry.register(RollingMeanDefinition(calcParams = listOf(5.0))))
        assertTrue(!registry.register(RollingMeanDefinition(calcParams = listOf(10.0))), "同名注册必须冲突")

        val def = registry.get("RollingMean")!!
        val instA = IndicatorInstance("rm-1", def, params = listOf(5.0))
        val instB = IndicatorInstance("rm-2", def, params = listOf(10.0))
        val candles = genCandles(30)

        IndicatorEngine.computeInto(instA, candles, ChangeKind.Reset)
        IndicatorEngine.computeInto(instB, candles, ChangeKind.Reset)
        assertEquals(InstanceStatus.READY, instA.status)
        assertEquals(InstanceStatus.READY, instB.status)

        val maA = instA.state!!.result["ma1"]
        val maB = instB.state!!.result["ma1"]
        assertTrue(maA[4].isFinite() && maA[9].isFinite())
        assertTrue(maB[4].isNaN() && maB[9].isFinite(), "参数 10 的实例按自己的窗口预热")

        // 修改 A 参数 → A 缓存失效重算，B 不受影响
        instA.updateParams(listOf(10.0))
        assertEquals(null, instA.state, "参数变化使缓存失效")
        IndicatorEngine.computeInto(instA, candles, ChangeKind.ParamsChanged)
        assertResultsEqual(instB.state!!, instA.state!!, "A 改为与 B 相同参数")
        // 改回后正确重算
        instA.updateParams(listOf(5.0))
        IndicatorEngine.computeInto(instA, candles, ChangeKind.ParamsChanged)
        assertTrue(instA.state!!.result["ma1"][4].isFinite())
    }

    // ---------- R-08：异常实例隔离 ----------

    @Test
    fun `抛错模板置Error不拖垮其他实例`() {
        val boom = IndicatorInstance("boom", ThrowingDefinition())
        val ok = IndicatorInstance("ok", RollingMeanDefinition())
        val candles = genCandles(10)

        val boomResult = IndicatorEngine.computeInto(boom, candles, ChangeKind.Reset)
        assertEquals(null, boomResult)
        assertEquals(InstanceStatus.ERROR, boom.status)
        assertTrue(boom.error is IllegalStateException)

        assertTrue(IndicatorEngine.computeInto(ok, candles, ChangeKind.Reset) != null)
        assertEquals(InstanceStatus.READY, ok.status, "其他实例不受影响")

        // 错误实例移除后重新注册可恢复（这里以新实例模拟）
        val fresh = IndicatorInstance("boom-2", RollingMeanDefinition())
        assertTrue(IndicatorEngine.computeInto(fresh, candles, ChangeKind.Reset) != null)
        assertEquals(InstanceStatus.READY, fresh.status)
    }
}
