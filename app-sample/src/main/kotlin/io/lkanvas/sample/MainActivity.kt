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
            val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
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
                        // 画线工具循环（16 种，T-20/21/22）
                        val tools = io.lkanvas.core.overlay.OverlayTemplates.all.keys.toList()
                        Button(
                            onClick = {
                                val next = tools[(tools.indexOf(state.currentTool) + 1) % tools.size]
                                state.currentTool = next
                            },
                        ) { Text(state.currentTool) }
                        Button(onClick = { state.beginCreateOverlay() }, modifier = Modifier.padding(start = 8.dp)) { Text("画线") }
                        Button(onClick = { if (!state.finishCreateOverlay()) state.cancelCreateOverlay() }, modifier = Modifier.padding(start = 8.dp)) { Text("完成/取消") }
                        Button(
                            onClick = {
                                state.overlays.removeSelected()
                                state.notifyOverlayChanged()
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        ) { Text("删除") }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        // 主图类型循环切换（六形态，T-18）
                        val types = io.lkanvas.compose.theme.MainChartType.entries
                        Button(
                            onClick = {
                                val next = types[(types.indexOf(state.mainChartType) + 1) % types.size]
                                state.mainChartType = next
                                state.notifyViewportChanged()
                            },
                        ) { Text("图型") }
                        // 纵轴规格循环：normal → logarithm → percentage（T-16）
                        Button(
                            onClick = {
                                state.mainAxisSpec = when (state.mainAxisSpec) {
                                    is io.lkanvas.core.axis.YAxisSpec.Normal -> io.lkanvas.core.axis.YAxisSpec.Logarithm
                                    is io.lkanvas.core.axis.YAxisSpec.Logarithm -> io.lkanvas.core.axis.YAxisSpec.Percentage(null)
                                    else -> io.lkanvas.core.axis.YAxisSpec.Normal
                                }
                                state.notifyViewportChanged()
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        ) { Text("轴") }
                        // PNG 导出（T-29）：cacheDir/lkanvas-export.png（主题与屏幕一致）
                        Button(
                            onClick = { exportPng(state, textMeasurer, io.lkanvas.compose.theme.ChartTheme.dark()) },
                            modifier = Modifier.padding(start = 8.dp),
                        ) { Text("导出") }
                    }
                }
            }
        }
    }

    /** PNG 导出演示（T-29）：写 cacheDir，宿主自行决定分享/存储。 */
    private fun exportPng(
        state: ChartState,
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
        theme: io.lkanvas.compose.theme.ChartTheme,
    ) {
        val out = java.io.File(cacheDir, "lkanvas-export.png")
        runCatching {
            java.io.FileOutputStream(out).use { fos ->
                io.lkanvas.compose.export.ChartSnapshotExporter.exportPng(
                    state = state,
                    textMeasurer = textMeasurer,
                    output = fos,
                    theme = theme,
                    widthPx = 1080,
                    heightPx = 1600,
                )
            }
        }.onSuccess {
            android.util.Log.i("LKanvas", "PNG 导出成功：${out.absolutePath} (${out.length()} bytes)")
        }.onFailure {
            android.util.Log.e("LKanvas", "PNG 导出失败", it)
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
