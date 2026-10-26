package io.lkanvas.chart.parity

import io.lkanvas.chart.DataLoader
import io.lkanvas.chart.LKanvasChart
import io.lkanvas.chart.Period
import io.lkanvas.chart.SymbolInfo
import io.lkanvas.core.data.Candle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test as TestAnn
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * parity-1（对齐批次 2a 出口）：上游官方文档「数据接入」示例
 * （https://klinecharts.com/en-US/guide/data-integration）逐行迁移。
 *
 * 迁移规则（对齐计划的验收定义）：**仅替换初始化方式**——
 * TS `chart.setDataLoader({...})` → Kotlin `LKanvasChart.init(state).setDataLoader({...})`，
 * 其余回调结构/字段名/合并语义与文档一致。
 */
class Parity1DataLoaderTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun drain() {
        repeat(200) { mainDispatcher.scheduler.advanceUntilIdle(); Thread.yield() }
    }

    /** 文档示例的 normalizeToKLineData 等价物。 */
    private fun normalizeToCandle(raw: Map<String, Double>): Candle =
        Candle(
            timestamp = raw.getValue("timestamp").toLong(),
            open = raw.getValue("open"),
            high = raw.getValue("high"),
            low = raw.getValue("low"),
            close = raw.getValue("close"),
            volume = raw["volume"],
        )

    /** 文档示例的假 REST：500 limit、hasMoreBefore/After。 */
    private class FakeApi {
        val total = 1000
        val calls = AtomicInteger(0)

        fun getKlineList(endTime: Long?, limit: Int, direction: DataLoader.DataLoadType): Pair<List<Map<String, Double>>, Pair<Boolean, Boolean>> {
            calls.incrementAndGet()
            val bars = when (direction) {
                DataLoader.DataLoadType.INIT -> (0 until limit).map { i ->
                    val ts = (endTime ?: 0) / 60_000 * 60_000 - (limit - 1 - i) * 60_000
                    mapOf("timestamp" to ts.toDouble(), "open" to 100.0, "high" to 101.0, "low" to 99.0, "close" to 100.5, "volume" to 10.0)
                }

                DataLoader.DataLoadType.FORWARD -> (0 until limit).map { i ->
                    val ts = (endTime ?: 0) - (limit - i) * 60_000
                    mapOf("timestamp" to ts.toDouble(), "open" to 100.0, "high" to 101.0, "low" to 99.0, "close" to 100.5)
                }

                else -> emptyList()
            }
            // 页边判定：首尾各留一页
            val hasMoreBefore = direction == DataLoader.DataLoadType.INIT || direction == DataLoader.DataLoadType.FORWARD
            val hasMoreAfter = direction == DataLoader.DataLoadType.BACKWARD
            return bars to (hasMoreBefore to hasMoreAfter)
        }
    }

    @TestAnn
    fun `parity1_官方quickstart数据接入迁移`() = kotlinx.coroutines.runBlocking {
        val chart = LKanvasChart.init()
        val api = FakeApi()
        // —— 文档示例逐行迁移开始（仅改语言与初始化） ——
        val stopMap = HashMap<String, () -> Unit>()
        var pushedRealtime: Candle? = null

        fun makeKey(symbol: SymbolInfo, period: Period) = "${symbol.ticker}-${period.type}"

        chart.setDataLoader(object : DataLoader {
            override fun getBars(params: DataLoader.GetBarsParams) {
                val response = api.getKlineList(
                    params.timestamp,
                    500,
                    params.type,
                )
                val bars = response.first
                    .map(::normalizeToCandle)
                    .sortedBy { it.timestamp }
                params.callback(
                    bars,
                    DataLoader.DataLoadMore(backward = response.second.second, forward = response.second.first),
                )
            }

            override fun subscribeBar(params: DataLoader.SubscribeBarParams) {
                val key = makeKey(params.symbol, params.period)
                stopMap[key] = {
                    pushedRealtime?.let { params.callback(it) }
                }
            }

            override fun unsubscribeBar(params: DataLoader.UnsubscribeBarParams) {
                val key = makeKey(params.symbol, params.period)
                stopMap.remove(key)
            }
        })
        chart.setSymbol(SymbolInfo("BTCUSDT"))
        chart.setPeriod(Period(Period.PeriodType.MINUTE))
        // —— 迁移结束 ——
        drain()

        val deadline = System.currentTimeMillis() + 5_000
        while (chart.getDataList().size < 500 && System.currentTimeMillis() < deadline) {
            drain()
            Thread.sleep(2)
        }
        assertEquals(500, chart.getDataList().size, "init 500 根")
        assertEquals(1, api.calls.get(), "init 恰好一次 getBars")

        // 实时合并语义（文档注明）：更大 timestamp 追加、相等覆盖、更小忽略
        val lastTs = chart.getDataList().last().timestamp
        stopMap["BTCUSDT-MINUTE"]!!.invoke() // 模拟 WS 推送（pushedRealtime 未设置→无副作用）

        // 更大：追加
        pushedRealtime = Candle(lastTs + 60_000, 100.0, 101.0, 99.0, 100.5, 10.0)
        stopMap["BTCUSDT-MINUTE"]!!.invoke()
        drain()
        assertEquals(501, chart.getDataList().size, "更大 timestamp 追加")

        // 相等：覆盖
        pushedRealtime = Candle(lastTs + 60_000, 100.0, 102.0, 99.0, 101.5, 20.0)
        stopMap["BTCUSDT-MINUTE"]!!.invoke()
        drain()
        assertEquals(501, chart.getDataList().size, "相等 timestamp 覆盖")
        assertEquals(101.5, chart.getDataList().last().close)

        // 更小：忽略
        pushedRealtime = Candle(lastTs - 60_000, 1.0, 1.0, 1.0, 1.0, 1.0)
        stopMap["BTCUSDT-MINUTE"]!!.invoke()
        drain()
        assertEquals(501, chart.getDataList().size, "更小 timestamp 忽略")

        assertTrue(chart.getDataList().zipWithNext().all { (a, b) -> a.timestamp < b.timestamp }, "时间严格递增")
        LKanvasChart.dispose(chart)
    }
}
