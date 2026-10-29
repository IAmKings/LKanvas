package io.lkanvas.core.indicator.builtin

import io.lkanvas.core.extension.indicator.*
import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.ChangeKind
import io.lkanvas.core.indicator.IndicatorEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 上游 TS 对照 fixtures（tools/oracle 生成，PRD 8.1 第二层验证）。
 * 误差：abs(a-b) <= 1e-9 + 1e-10*max(|a|,|b|)；null ↔ NaN 位置一致。
 */
class BuiltinIndicatorOracleTest {

    private val json = Json { ignoreUnknownKeys = true }

    private data class Fixture(
        val indicator: String,
        val params: List<Double>,
        val candles: List<Candle>,
        val keys: List<String>,
        val expected: List<Map<String, Double?>>,
    )

    private fun load(name: String): Fixture {
        val text = javaClass.getResourceAsStream("/fixtures/indicator/$name.json")!!
            .bufferedReader().readText()
        val root = json.parseToJsonElement(text).jsonObject
        val meta = root["meta"]!!.jsonObject
        val params = meta["params"]!!.jsonArray.map { it.jsonPrimitive.double }
        val candles = root["candles"]!!.jsonArray.map { el ->
            val o = el.jsonObject
            Candle(
                timestamp = o["timestamp"]!!.jsonPrimitive.content.toLong(),
                open = o["open"]!!.jsonPrimitive.double,
                high = o["high"]!!.jsonPrimitive.double,
                low = o["low"]!!.jsonPrimitive.double,
                close = o["close"]!!.jsonPrimitive.double,
                volume = o["volume"]?.jsonPrimitive?.doubleOrNull,
                turnover = o["turnover"]?.jsonPrimitive?.doubleOrNull,
            )
        }
        val results = root["results"]!!.jsonArray
        val keys = results.first().jsonObject.keys.toList()
        val expected = results.map { rowEl ->
            val row = rowEl.jsonObject
            keys.associateWith { row[it]?.jsonPrimitive?.doubleOrNull }
        }
        return Fixture(meta["indicator"]!!.jsonPrimitive.content, params, candles, keys, expected)
    }

    private fun Double.tol(other: Double): Double = 1e-9 + 1e-10 * maxOf(abs(this), abs(other))

    private fun assertAgainstFixture(fixtureName: String, def: io.lkanvas.core.indicator.IndicatorDefinition) {
        val f = load(fixtureName)
        assertEquals(f.indicator, def.name, "fixture 与模板对应")
        val state = IndicatorEngine.recompute(def, f.params, f.candles)
        assertEquals(f.expected.size, state.result.size(), "输出长度")
        for (key in f.keys) {
            val column = state.result[key]
            f.expected.forEachIndexed { i, row ->
                val expect = row[key]
                if (expect == null) {
                    assertTrue(column[i].isNaN(), "$fixtureName $key[$i] 应 Missing")
                } else {
                    assertTrue(
                        !column[i].isNaN() && abs(column[i] - expect) <= column[i].tol(expect),
                        "$fixtureName $key[$i]=${column[i]} 期望 $expect",
                    )
                }
            }
        }
    }

    // ---------- A-22：oracle 逐位对照 ----------

    @Test
    fun `MA与上游TS逐位一致`() = assertAgainstFixture("MA", MovingAverageIndicator())

    @Test
    fun `VOL与上游TS逐位一致`() = assertAgainstFixture("VOL", VolumeIndicator())

    @Test
    fun `MACD与上游TS逐位一致`() = assertAgainstFixture("MACD", MacdIndicator())

    // ---------- 手算独立样例（第一层验证，防复制上游错误） ----------

    @Test
    fun `MA手算样例`() {
        // close 1..10，MA5：ma[4]=3，ma[9]=8；MA3：ma[2]=2，ma[9]=9
        val candles = (0 until 10).map {
            Candle(it.toLong(), (it + 1).toDouble(), (it + 2).toDouble(), it.toDouble(), (it + 1).toDouble(), 100.0)
        }
        val state = IndicatorEngine.recompute(MovingAverageIndicator(), listOf(5.0, 3.0), candles)
        val ma1 = state.result["ma1"] // 周期 5
        val ma2 = state.result["ma2"] // 周期 3
        assertTrue(ma1[3].isNaN() && ma2[1].isNaN(), "预热区间 Missing")
        assertEquals(3.0, ma1[4], 1e-12)
        assertEquals(8.0, ma1[9], 1e-12)
        assertEquals(2.0, ma2[2], 1e-12)
        assertEquals(9.0, ma2[9], 1e-12)
    }

    @Test
    fun `MACD种子与预热手算一致`() {
        val f = load("MACD")
        val state = IndicatorEngine.recompute(MacdIndicator(), f.params, f.candles)
        val dif = state.result["dif"]
        val dea = state.result["dea"]
        val macd = state.result["macd"]
        val maxPeriod = 26
        val signal = 9
        // DIF 自 maxPeriod-1 有效，DEA/MACD 自 maxPeriod+signal-2 有效
        assertTrue(dif[maxPeriod - 2].isNaN() && dif[maxPeriod - 1].isFinite())
        assertTrue(dea[maxPeriod + signal - 3].isNaN() && dea[maxPeriod + signal - 2].isFinite())
        // 独立推导：DEA 种子 = 首批 signal 个 DIF 的算术平均
        val seedIdx = maxPeriod + signal - 2
        val difSeed = (maxPeriod - 1..seedIdx).map { dif[it] }
        assertEquals(difSeed.average(), dea[seedIdx], 1e-9, "DEA 种子应为首批 DIF 均值")
        // 柱 = (DIF-DEA)*2 关系成立
        assertEquals((dif[seedIdx] - dea[seedIdx]) * 2, macd[seedIdx], 1e-9)
    }

    @Test
    fun `VOL缺失volume输出Missing而非零（D-03）`() {
        val candles = (0 until 10).map {
            Candle(
                it.toLong(), 10.0, 11.0, 9.0, 10.5,
                volume = if (it == 3) null else 100.0 + it, // 第 3 根缺失
            )
        }
        val state = IndicatorEngine.recompute(VolumeIndicator(), listOf(5.0), candles)
        val vol = state.result["volume"]
        val ma1 = state.result["ma1"]
        assertTrue(vol[3].isNaN(), "缺失 volume 为 Missing，不是 0")
        // MA5 窗口含第 3 根的位置（i=3..7）均 Missing
        for (i in 3..7) assertTrue(ma1[i].isNaN(), "窗口含缺失的 ma[$i] 应 Missing")
        assertTrue(ma1[2].isNaN(), "i=2 仍在预热期（Missing），与缺失无关")
        assertTrue(ma1[8].isFinite() && ma1[9].isFinite(), "缺失滑出窗口后恢复")
        assertEquals((105.0 + 106.0 + 107.0 + 108.0 + 109.0) / 5.0, ma1[9], 1e-12, "恢复后数值精确")
    }

    // ---------- A-23：同根多次替换后追加与全量一致 ----------

    private fun randomReplaceAppendSequence(
        def: io.lkanvas.core.indicator.IndicatorDefinition,
        params: List<Double>,
        steps: Int,
        seed: Long,
    ) {
        val rand = Random(seed)
        val candles = ArrayList<Candle>()
        var ts = 0L
        var price = 100.0
        fun newCandle(t: Long, p: Double) = Candle(t, p, p + 1, p - 1, p, 1000.0 + rand.nextInt(500))
        repeat(60) { price += (rand.nextDouble() - 0.5); candles += newCandle(ts++, price) }
        var state = IndicatorEngine.recompute(def, params, candles)

        for (step in 0 until steps) {
            when (rand.nextInt(4)) {
                0, 1 -> { // 高频替换末根
                    repeat(1 + rand.nextInt(8)) {
                        price += (rand.nextDouble() - 0.5) * 0.3
                        candles[candles.size - 1] = newCandle(candles.last().timestamp, price)
                        state = IndicatorEngine.advance(def, state, candles, ChangeKind.ReplaceLatest)
                    }
                }
                2 -> { // 追加
                    price += (rand.nextDouble() - 0.5)
                    candles += newCandle(candles.last().timestamp + 1, price)
                    state = IndicatorEngine.advance(def, state, candles, ChangeKind.Append(1))
                }
                else -> { // 替换后立刻追加（跨根边界）
                    candles[candles.size - 1] = newCandle(candles.last().timestamp, price + 0.5)
                    state = IndicatorEngine.advance(def, state, candles, ChangeKind.ReplaceLatest)
                    price += (rand.nextDouble() - 0.5)
                    candles += newCandle(candles.last().timestamp + 1, price)
                    state = IndicatorEngine.advance(def, state, candles, ChangeKind.Append(1))
                }
            }
            if (step % 101 == 0) {
                val full = IndicatorEngine.recompute(def, params, candles)
                for (k in full.result.keys) {
                    val x = state.result[k]
                    val y = full.result[k]
                    for (i in x.indices) {
                        assertTrue(
                            (x[i].isNaN() && y[i].isNaN()) || abs(x[i] - y[i]) <= x[i].tol(y[i]),
                            "${def.name} step=$step $k[$i] 增量${x[i]} vs 全量${y[i]}",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `MACD同根多次替换后追加与全量一致`() =
        randomReplaceAppendSequence(MacdIndicator(), listOf(12.0, 26.0, 9.0), steps = 2_000, seed = 1)

    @Test
    fun `MA高频替换追加与全量一致`() =
        randomReplaceAppendSequence(MovingAverageIndicator(), listOf(5.0, 10.0), steps = 2_000, seed = 2)

    @Test
    fun `VOL高频替换追加与全量一致`() =
        randomReplaceAppendSequence(VolumeIndicator(), listOf(5.0, 10.0, 20.0), steps = 2_000, seed = 3)

    // ---------- B-01 工作量门槛：末根替换不遍历全历史 ----------

    /** 计数访问的 candles 包装：证明增量替换工作量与历史长度无关。 */
    private class CountingList(private val delegate: List<Candle>) : AbstractList<Candle>() {
        var reads = 0
        override val size: Int get() = delegate.size
        override fun get(index: Int): Candle {
            reads++
            return delegate[index]
        }
    }

    @Test
    fun `末根替换不遍历全部历史（工作量随参数而非N）`() {
        for (def in listOf(MovingAverageIndicator(), VolumeIndicator(), MacdIndicator())) {
            val n = 10_000
            val rand = Random(5)
            val base = ArrayList<Candle>(n)
            var p = 100.0
            repeat(n) {
                p += (rand.nextDouble() - 0.5)
                base += Candle(it.toLong(), p, p + 1, p - 1, p, 1000.0)
            }
            val counting = CountingList(base)
            val state = IndicatorEngine.recompute(def, def.calcParams, counting)
            assertTrue(counting.reads >= n, "全量必然遍历全部")

            counting.reads = 0
            val last = base.last()
            base[n - 1] = Candle(last.timestamp, last.close + 1, last.close + 2, last.close, last.close + 1, last.volume)
            val advanced = IndicatorEngine.advance(def, state, counting, ChangeKind.ReplaceLatest)
            assertEquals(io.lkanvas.core.indicator.ComputeMode.INCREMENTAL_REPLACE, advanced.mode)
            // 访问次数只与参数相关（每个窗口回看一根 + 末根本身），远小于 n
            assertTrue(
                counting.reads < 30,
                "${def.name} 末根替换访问 ${counting.reads} 次，应 O(参数) 而非 O(N=$n)",
            )
        }
    }
}
