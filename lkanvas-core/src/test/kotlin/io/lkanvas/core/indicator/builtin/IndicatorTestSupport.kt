package io.lkanvas.core.indicator.builtin

import io.lkanvas.core.extension.indicator.*
import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.ChangeKind
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorEngine
import io.lkanvas.core.indicator.IndicatorResultState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.random.Random

/** 指标测试共享工具：fixture 加载、逐位断言、增量/全量一致性序列。 */
object IndicatorTestSupport {

    data class Fixture(
        val indicator: String,
        val params: List<Double>,
        val candles: List<Candle>,
        val keys: List<String>,
        val expected: List<Map<String, Double?>>,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun load(name: String): Fixture {
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

    /** oracle 逐位对照（null↔NaN 位置一致 + 误差容忍）。 */
    fun assertAgainstFixture(fixtureName: String, def: IndicatorDefinition) {
        val f = load(fixtureName)
        val state = IndicatorEngine.recompute(def, f.params, f.candles)
        for (key in f.keys) {
            val column = state.result[key]
            f.expected.forEachIndexed { i, row ->
                val expect = row[key]
                if (expect == null) {
                    kotlin.test.assertTrue(column[i].isNaN(), "$fixtureName $key[$i] 应 Missing")
                } else {
                    kotlin.test.assertTrue(
                        !column[i].isNaN() && abs(column[i] - expect) <= column[i].tol(expect),
                        "$fixtureName $key[$i]=${column[i]} 期望 $expect",
                    )
                }
            }
        }
    }

    fun assertResultsEqual(a: IndicatorResultState, b: IndicatorResultState, label: String) {
        kotlin.test.assertEquals(a.result.keys, b.result.keys, "$label 输出键一致")
        for (k in a.result.keys) {
            val x = a.result[k]
            val y = b.result[k]
            kotlin.test.assertEquals(x.size, y.size, "$label 列 $k 长度")
            for (i in x.indices) {
                kotlin.test.assertTrue(
                    (x[i].isNaN() && y[i].isNaN()) || abs(x[i] - y[i]) <= x[i].tol(y[i]),
                    "$label 列 $k 第 $i 位不一致：${x[i]} vs ${y[i]}",
                )
            }
        }
    }

    /**
     * 固定随机替换/追加序列：增量结果与全量逐位一致（A-23/A-24 模式）。
     */
    fun randomReplaceAppendSequence(
        def: IndicatorDefinition,
        params: List<Double>,
        steps: Int,
        seed: Long,
    ) {
        val rand = Random(seed)
        val candles = ArrayList<Candle>()
        var price = 100.0
        fun newCandle(t: Long, p: Double) =
            Candle(t, p, p + 1, p - 1, p, 1000.0 + rand.nextInt(500))
        repeat(60) { price += (rand.nextDouble() - 0.5); candles += newCandle(it.toLong(), price) }
        var state = IndicatorEngine.recompute(def, params, candles)

        for (step in 0 until steps) {
            when (rand.nextInt(4)) {
                0, 1 -> repeat(1 + rand.nextInt(8)) {
                    price += (rand.nextDouble() - 0.5) * 0.3
                    candles[candles.size - 1] = newCandle(candles.last().timestamp, price)
                    state = IndicatorEngine.advance(def, state, candles, ChangeKind.ReplaceLatest)
                }
                2 -> {
                    price += (rand.nextDouble() - 0.5)
                    candles += newCandle(candles.last().timestamp + 1, price)
                    state = IndicatorEngine.advance(def, state, candles, ChangeKind.Append(1))
                }
                else -> {
                    candles[candles.size - 1] = newCandle(candles.last().timestamp, price + 0.5)
                    state = IndicatorEngine.advance(def, state, candles, ChangeKind.ReplaceLatest)
                    price += (rand.nextDouble() - 0.5)
                    candles += newCandle(candles.last().timestamp + 1, price)
                    state = IndicatorEngine.advance(def, state, candles, ChangeKind.Append(1))
                }
            }
            if (step % 101 == 0) {
                assertResultsEqual(IndicatorEngine.recompute(def, params, candles), state, "${def.name} step=$step")
            }
        }
        assertResultsEqual(IndicatorEngine.recompute(def, params, candles), state, "${def.name} 终态")
    }
}
