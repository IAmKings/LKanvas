package io.lkanvas.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.lkanvas.compose.KLineChart
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.core.data.Candle
import io.lkanvas.core.store.MarketKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state = remember { ChartState() }
            remember { { startFeed(state) }.also { it() } }
            Surface(modifier = Modifier.fillMaxSize()) {
                KLineChart(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    theme = ChartTheme.dark(),
                )
            }
        }
    }

    /** 本地确定性模拟行情：初始 500 根 + 实时末根更新 + 每秒追加。 */
    private fun startFeed(state: ChartState) {
        scope.launch {
            val rand = Random(2026)
            var price = 100.0
            var ts = System.currentTimeMillis() / 60_000L * 60_000L - 500 * 60_000L
            val initial = ArrayList<Candle>(500)
            repeat(500) {
                val open = price
                val close = open + (rand.nextDouble() - 0.5) * 2
                val high = maxOf(open, close) + rand.nextDouble() * 0.5
                val low = minOf(open, close) - rand.nextDouble() * 0.5
                initial += Candle(ts, open, high, low, close, rand.nextDouble() * 5000 + 100)
                price = close
                ts += 60_000L
            }
            state.reset(MarketKey("DEMO", "1m"), initial)

            // 实时：高频末根替换；每 5 次追加一根新 candle
            var tick = 0
            while (isActive) {
                delay(200)
                tick++
                if (tick % 5 == 0) {
                    ts += 60_000L
                    state.upsertLatest(
                        Candle(ts, price, price + 1, price - 1, price + (rand.nextDouble() - 0.5), rand.nextDouble() * 5000 + 100),
                    ).let { }
                    price += (rand.nextDouble() - 0.5)
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
    }
}
