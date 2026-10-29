package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorEngine
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SLA-002 双路径互验：内置 27 模板的规范 `calc`（上游模板函数镜像）与
 * Scratch 折叠路径输出必须逐位置一致（有效值误差预算 + NaN 位置一致）。
 *
 * 数据源：确定性合成序列（趋势/震荡/零量/缺失 volume/缺失 turnover 混合），
 * 覆盖预热边界、窗口滑出、除零防护与 D-03 缺失掩码路径。
 */
class DualPathParityTest {

    private fun tolerance(a: Double, b: Double): Double = 1e-9 + 1e-10 * maxOf(abs(a), abs(b))

    /** 合成序列：120 根，含价格台阶/回撤、volume 缺失段、turnover 缺失根。 */
    private fun syntheticCandles(): List<Candle> {
        val out = ArrayList<Candle>(120)
        var price = 100.0
        for (i in 0 until 120) {
            val drift = if (i % 17 == 0) -3.5 else 0.8
            val wave = kotlin.math.sin(i / 7.0) * 2.0
            price = (price + drift + wave).coerceAtLeast(1.0)
            val volume = when {
                i in 30..41 -> null // 缺失 volume 段（D-03 掩码路径）
                i % 11 == 0 -> 0.0  // 有效 0（上游显式 0 语义路径）
                else -> 1000.0 + i * 37.0
            }
            val turnover = if (i % 23 == 0) null else price * (volume ?: 0.0)
            out.add(
                Candle(
                    timestamp = 1_700_000_000_000L + i * 60_000L,
                    open = price - 0.5,
                    high = price + 1.5,
                    low = price - 1.5,
                    close = price,
                    volume = volume,
                    turnover = turnover,
                ),
            )
        }
        return out
    }

    private fun assertDualPath(def: IndicatorDefinition, candles: List<Candle>, params: List<Double>) {
        val calcRows = def.calc(candles, params)
        assertEquals(candles.size, calcRows.size, "${def.name}: calc 行数")

        // Scratch 折叠
        var scratch = def.initScratch(params)
        val scratchRows = ArrayList<Map<String, Double>>(candles.size)
        for (i in candles.indices) {
            scratch = def.stepScratch(scratch, candles, params, i)
            scratchRows.add(def.rowFromScratch(scratch, candles, params, i))
        }

        // 键集合一致（顺序无关；NaN 位置一致）
        val calcKeys = calcRows.flatMap { it.keys }.toSet()
        val scratchKeys = scratchRows.flatMap { it.keys }.toSet()
        assertEquals(scratchKeys, calcKeys, "${def.name}: 双路径输出键集合")

        for (key in calcKeys) {
            for (i in candles.indices) {
                val a = calcRows[i][key]
                val b = scratchRows[i][key]
                if (a == null || b == null) {
                    assertEquals(b, a, "${def.name}[$key][$i]: Missing 位置一致（一侧缺键）")
                } else if (a.isNaN() || b.isNaN()) {
                    assertEquals(a.isNaN(), b.isNaN(), "${def.name}[$key][$i]: NaN 位置一致")
                } else {
                    assertTrue(
                        abs(a - b) <= tolerance(a, b),
                        "${def.name}[$key][$i]: calc=$b scratch=$a 超出误差预算",
                    )
                }
            }
        }
    }

    @Test
    fun `27个内置模板 calc 与 Scratch 双路径逐位一致`() {
        val candles = syntheticCandles()
        // 全局注册表的 27 内置（上游 extensions 全集）
        val defs = IndicatorTemplates.supported().mapNotNull { IndicatorTemplates.get(it) }
        assertEquals(27, defs.size, "内置模板数量")
        for (def in defs) {
            assertDualPath(def, candles, def.calcParams)
        }
    }

    @Test
    fun `动态参数实例 calc 与 Scratch 双路径一致（MA 多参数）`() {
        val candles = syntheticCandles()
        // SLA-008 守护：参数个数变化下两路径仍一致
        assertDualPath(IndicatorTemplates.get("MA")!!, candles, listOf(3.0, 5.0, 8.0, 13.0, 21.0))
        assertDualPath(IndicatorTemplates.get("EMA")!!, candles, listOf(2.0))
        assertDualPath(IndicatorTemplates.get("VOL")!!, candles, listOf(2.0, 4.0))
    }

    @Test
    fun `引擎全量路径经 calc 产出且与 oracle 一致性成立`() {
        val candles = syntheticCandles()
        val def = IndicatorTemplates.get("MA")!!
        val state = IndicatorEngine.recompute(def, def.calcParams, candles)
        // 引擎 recompute 现在走 calc 列（SLA-002）；抽查列与 calc 直接输出一致
        val calcRows = def.calc(candles, def.calcParams)
        for (i in intArrayOf(0, 4, 59, 119)) {
            val v = state.result["ma2"][i]
            val expect = calcRows[i]["ma2"] ?: Double.NaN
            if (expect.isNaN()) {
                assertTrue(v.isNaN(), "ma2[$i] 应为 Missing")
            } else {
                assertTrue(abs(v - expect) <= tolerance(v, expect), "ma2[$i] 引擎=calc")
            }
        }
    }
}
