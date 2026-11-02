package io.lkanvas.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.lkanvas.chart.LKanvasChart
import io.lkanvas.compose.state.ChartState
import io.lkanvas.core.common.DataLoader
import io.lkanvas.core.data.Candle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** 固定种子的加速模拟行情；周期与分页均通过公开 DataLoader 契约。 */
class LocalFeed(private val scope: CoroutineScope, private val seed: Int = 2026) : DataLoader {
    private var rand = Random(seed)
    private var last: Candle? = null
    private var pages = 0
    private var subscription: Job? = null
    var paused by mutableStateOf(false)

    private fun candleAt(ts: Long, open: Double, close: Double, volume: Double) = Candle(
        ts, open, maxOf(open, close) + rand.nextDouble() * 0.5,
        minOf(open, close) - rand.nextDouble() * 0.5, close, volume,
    )

    override fun getBars(params: DataLoader.GetBarsParams) {
        val step = params.period.toMillis()
        val data = when (params.type) {
            DataLoader.DataLoadType.INIT -> {
                rand = Random(seed)
                pages = 0
                var price = 100.0
                val end = System.currentTimeMillis() / step * step
                List(500) { i ->
                    val open = price
                    price += (rand.nextDouble() - 0.5) * 2
                    candleAt(end - (499 - i) * step, open, price, rand.nextDouble() * 5000 + 100)
                }.also { last = it.last() }
            }
            DataLoader.DataLoadType.FORWARD -> {
                val before = params.timestamp
                if (before == null || pages >= 3) emptyList() else {
                    pages++
                    var price = 90.0
                    List(200) { i ->
                        val open = price
                        price += (rand.nextDouble() - 0.5) * 2
                        candleAt(before - (200 - i) * step, open, price, rand.nextDouble() * 5000 + 100)
                    }
                }
            }
            else -> emptyList()
        }
        params.callback(data, DataLoader.DataLoadMore(backward = false, forward = pages < 3))
    }

    override fun subscribeBar(params: DataLoader.SubscribeBarParams) {
        subscription?.cancel()
        subscription = scope.launch {
            var tick = 0
            while (isActive) {
                delay(200)
                if (paused) continue
                val previous = last ?: continue
                tick++
                val next = if (tick % 5 == 0) {
                    candleAt(previous.timestamp + params.period.toMillis(), previous.close,
                        previous.close + (rand.nextDouble() - 0.5), rand.nextDouble() * 5000 + 100)
                } else {
                    val close = previous.close + (rand.nextDouble() - 0.5) * 0.4
                    previous.copy(close = close, high = maxOf(previous.high, close), low = minOf(previous.low, close))
                }
                last = next
                params.callback(next)
            }
        }
    }

    override fun unsubscribeBar(params: DataLoader.UnsubscribeBarParams) {
        subscription?.cancel()
        subscription = null
    }

    /** 保留既有静态压力负载与分批注入，独立于普通模拟订阅。 */
    suspend fun startStress(state: ChartState, stress: StressConfig, facade: LKanvasChart) {
        val injectStartMs = System.currentTimeMillis()
        android.util.Log.i("LKanvas", "stress 注入开始：${stress.candles} candle / ${stress.indicators} 指标 / ${stress.overlays} 覆盖物")
        val nowMin = System.currentTimeMillis() / 60_000L * 60_000L
        val count = stress.candles
        val initial = ArrayList<Candle>(count)
        val startTs = nowMin - count * 60_000L
        var p = 100.0
        for (i in 0 until count) {
            val open = p
            val close = open + (rand.nextDouble() - 0.5) * 2
            initial += candleAt(startTs + i * 60_000L, open, close, rand.nextDouble() * 5000 + 100)
            p = close
        }
        // B-03 改进（分批注入，PRD「就绪期间 UI 可响应」）：首批 reset 后指标在
        // 小数据量上全量（毫秒级），其余经 appendBatch 分批推进（每批间让出一帧，
        // 指标走 REPLACE_APPEND 增量）——避免单次 3.7s 的主线程全量阻塞
        val firstBatch = minOf(1_000, count)
        state.reset(io.lkanvas.core.store.MarketKey("STRESS", "1m"), initial.subList(0, firstBatch))
        android.util.Log.i("LKanvas", "stress 首批可交互：$firstBatch candle，距注入开始 ${System.currentTimeMillis() - injectStartMs}ms")
        // 初始 MA 计入 K 个实例；显式主图叠加，避免上游默认新建 pane 改变压力口径。
        repeat((stress.indicators - 1).coerceAtLeast(0)) { i ->
            facade.createIndicator("MA", calcParams = listOf((i + 2).toDouble()), paneId = "main", isStack = true)
        }
        check(state.getIndicators(ChartState.IndicatorFilter(name = "MA")).size == maxOf(1, stress.indicators))
        check(state.extraPanes.value.isEmpty())
        var appended = firstBatch
        while (appended < count) {
            val end = minOf(appended + 2_000, count)
            state.appendBatch(initial.subList(appended, end))
            appended = end
            delay(16) // 让出一帧：就绪期间 UI 可响应
        }
        // M 条 segment（时间均分 + 价格在数据范围内固定模式取点）
        val lo = initial.minOf { it.low }
        val hi = initial.maxOf { it.high }
        val ts0 = initial.first().timestamp
        val ts1 = initial.last().timestamp
        repeat(stress.overlays) { i ->
            val t1 = ts0 + (ts1 - ts0) * i / stress.overlays
            val t2 = ts0 + (ts1 - ts0) * (i + 1) / stress.overlays
            facade.createOverlay(
                io.lkanvas.chart.OverlayCreate(
                    "segment",
                    points = listOf(
                        io.lkanvas.core.overlay.OverlayPoint(t1, lo + (hi - lo) * (i % 7) / 7.0),
                        io.lkanvas.core.overlay.OverlayPoint(t2, lo + (hi - lo) * ((i + 3) % 7) / 7.0),
                    ),
                ),
            )
        }
        // B-03 口径：注入开始→完成 = 「初始主图及必需指标就绪」实测（同步 reset+全量计算）
        android.util.Log.i(
            "LKanvas",
            "stress 注入完成：${stress.candles} candle / ${stress.indicators} 指标 / ${stress.overlays} 覆盖物，" +
                "就绪耗时 ${System.currentTimeMillis() - injectStartMs}ms",
        )
    }
}

data class StressConfig(val candles: Int, val indicators: Int, val overlays: Int)
