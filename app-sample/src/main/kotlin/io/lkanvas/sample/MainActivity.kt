package io.lkanvas.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.lkanvas.compose.KLineChart
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.core.data.Candle
import io.lkanvas.core.store.MarketKey
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val feed = remember { LocalFeed() }
            val state = remember { ChartState() }
            LaunchedEffect(state) { feed.start(state) }
            // 左缘分页：滚动到边缘时补充更早历史（锚点保持由 core 保证）
            LaunchedEffect(state) {
                snapshotFlow { state.viewRevision }
                    .collect {
                        if (state.viewport.scrollIndex < 8 && feed.hasMoreEarlier) {
                            feed.loadMoreEarlier(state)
                        }
                    }
            }
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    KLineChart(
                        state = state,
                        modifier = Modifier.fillMaxSize().weight(1f),
                        theme = ChartTheme.dark(),
                    )
                    Row(Modifier.fillMaxWidth().padding(8.dp)) {
                        Button(onClick = { state.beginCreateSegment() }) { Text("画线") }
                        Button(onClick = { state.cancelCreateOverlay() }, modifier = Modifier.padding(start = 8.dp)) { Text("取消") }
                        Button(
                            onClick = {
                                state.overlays.removeSelected()
                                state.notifyOverlayChanged()
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        ) { Text("删除选中") }
                    }
                }
            }
        }
    }
}

/** 本地确定性行情：初始 500 根 + 高频末根替换 + 每 5 tick 追加 + 左缘分页。 */
class LocalFeed(private val seed: Int = 2026) {
    private val rand = Random(seed)
    private var price = 100.0
    private var nextTs = 0L
    private var earliestTs = 0L
    private var pages = 0
    val hasMoreEarlier: Boolean get() = pages < 3

    private fun candleAt(ts: Long, open: Double, close: Double, vol: Double) = Candle(
        ts,
        open,
        maxOf(open, close) + rand.nextDouble() * 0.5,
        minOf(open, close) - rand.nextDouble() * 0.5,
        close,
        vol,
    )

    suspend fun start(state: ChartState) {
        val nowMin = System.currentTimeMillis() / 60_000L * 60_000L
        nextTs = nowMin
        earliestTs = nowMin - 500 * 60_000L
        val initial = ArrayList<Candle>(500)
        repeat(500) { i ->
            val open = price
            val close = open + (rand.nextDouble() - 0.5) * 2
            initial += candleAt(earliestTs + i * 60_000L, open, close, rand.nextDouble() * 5000 + 100)
            price = close
        }
        state.reset(MarketKey("DEMO", "1m"), initial)

        var tick = 0
        while (currentCoroutineContext().isActive) {
            delay(200)
            tick++
            if (tick % 5 == 0) {
                nextTs += 60_000L
                val open = price
                price += (rand.nextDouble() - 0.5)
                state.upsertLatest(candleAt(nextTs, open, price, rand.nextDouble() * 5000 + 100))
            } else {
                val last = state.dataSnapshot.candles.last()
                val close = last.close + (rand.nextDouble() - 0.5) * 0.4
                state.upsertLatest(
                    Candle(
                        last.timestamp,
                        last.open,
                        maxOf(last.open, close) + rand.nextDouble() * 0.2,
                        minOf(last.open, close) - rand.nextDouble() * 0.2,
                        close,
                        last.volume,
                    ),
                )
                price = close
            }
        }
    }

    /** 左缘分页：向更早方向补充一页（200 根），视口锚点保持由 core onPrepended 保证。 */
    suspend fun loadMoreEarlier(state: ChartState) {
        if (!hasMoreEarlier) return
        pages++
        val page = ArrayList<Candle>(200)
        val newEarliest = earliestTs - 200 * 60_000L
        var p = 90.0
        for (i in 0 until 200) {
            val open = p
            p += (rand.nextDouble() - 0.5) * 2
            page += candleAt(newEarliest + i * 60_000L, open, p, rand.nextDouble() * 5000 + 100)
        }
        state.prependHistory(page)
        earliestTs = newEarliest
        delay(400) // 防抖：分页后暂停触发
    }
}
