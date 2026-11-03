package io.lkanvas.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.lkanvas.chart.LKanvasChart
import io.lkanvas.compose.state.ChartState
import io.lkanvas.core.common.DataLoader
import io.lkanvas.core.data.Candle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.math.abs
import kotlin.math.sin

/** 同一条确定性分钟行情按 UTC 周期聚合；模拟时钟每秒推进一分钟。 */
class LocalFeed(private val scope: CoroutineScope, private val seed: Int = 2026) : DataLoader {
    private var rand = Random(seed)
    private var minute = System.currentTimeMillis() / MINUTE * MINUTE
    private var phase = TICKS_PER_MINUTE
    private var loadJob: Job? = null
    private var pages = 0
    private var subscription: Job? = null
    var paused by mutableStateOf(false)

    private fun candleAt(ts: Long, open: Double, close: Double, volume: Double) = Candle(
        ts, open, maxOf(open, close) + rand.nextDouble() * 0.5,
        minOf(open, close) - rand.nextDouble() * 0.5, close, volume,
    )

    private fun price(timestamp: Long, salt: Long): Double {
        val m = timestamp.toDouble() / MINUTE + salt
        return 100 + 4 * sin(m * 0.11) + 8 * sin(m * 0.007) +
            12 * sin(m * 0.0007) + 16 * sin(m * 0.00003)
    }

    private fun aggregate(start: Long, step: Long, now: Long, tick: Int, salt: Long): Candle {
        val open = price(start - MINUTE, salt)
        var close = open
        var high = open
        var low = open
        var volume = 0.0
        var previous = open
        var timestamp = start
        val end = minOf(start + step, now + MINUTE)
        while (timestamp < end) {
            val fraction = if (timestamp == now) tick.toDouble() / TICKS_PER_MINUTE else 1.0
            val next = price(timestamp, salt)
            close = previous + (next - previous) * fraction
            val m = timestamp.toDouble() / MINUTE + salt
            val wick = (0.15 + 0.35 * abs(sin(m * 0.37))) * fraction
            high = maxOf(high, maxOf(previous, close) + wick)
            low = minOf(low, minOf(previous, close) - wick)
            volume += (100 + 2450 * (1 + sin(m * 1.7))) * fraction
            previous = next
            timestamp += MINUTE
        }
        return Candle(start, open, high, low, close, volume)
    }

    override fun getBars(params: DataLoader.GetBarsParams) {
        val step = params.period.toMillis()
        require(step in MINUTE..86_400_000L && step % MINUTE == 0L)
        val count = when (params.type) {
            DataLoader.DataLoadType.INIT -> {
                loadJob?.cancel()
                pages = 0
                500
            }
            DataLoader.DataLoadType.FORWARD ->
                if (params.timestamp == null || pages >= 3) 0 else { pages++; 200 }
            else -> 0
        }
        val now = minute
        val tick = phase
        val end = if (params.type == DataLoader.DataLoadType.INIT) now / step * step + step else params.timestamp ?: 0L
        val salt = (seed.toLong() xor params.symbol.ticker.hashCode().toLong()) % 100_000
        val more = DataLoader.DataLoadMore(backward = false, forward = pages < 3)
        loadJob = scope.launch {
            val data = withContext(Dispatchers.Default) {
                List(count) { i ->
                    currentCoroutineContext().ensureActive()
                    aggregate(end - (count - i) * step, step, now, tick, salt)
                }
            }
            params.callback(data, more)
        }
    }

    override fun subscribeBar(params: DataLoader.SubscribeBarParams) {
        subscription?.cancel()
        val step = params.period.toMillis()
        require(step in MINUTE..86_400_000L && step % MINUTE == 0L)
        val salt = (seed.toLong() xor params.symbol.ticker.hashCode().toLong()) % 100_000
        subscription = scope.launch {
            while (isActive) {
                delay(200)
                if (paused) continue
                if (phase == TICKS_PER_MINUTE) { minute += MINUTE; phase = 1 } else phase++
                val now = minute
                val tick = phase
                val next = withContext(Dispatchers.Default) { aggregate(now / step * step, step, now, tick, salt) }
                if (!paused) params.callback(next)
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
    private companion object {
        const val MINUTE = 60_000L
        const val TICKS_PER_MINUTE = 5
    }
}

data class StressConfig(val candles: Int, val indicators: Int, val overlays: Int)
