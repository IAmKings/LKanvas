package io.lkanvas.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lkanvas.chart.Period
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
            val chartFacade = remember { io.lkanvas.chart.LKanvasChart.init(state) }
            var showIndicatorPanel by remember { androidx.compose.runtime.mutableStateOf(false) }
            var showStylesPanel by remember { androidx.compose.runtime.mutableStateOf(false) }
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
                    // —— 批次 6 余量：周期 tab + 指标面板（对齐上游 demo 信息架构） ——
                    val currentPeriod = remember { androidx.compose.runtime.mutableStateOf(Period(Period.PeriodType.MINUTE)) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
                        val periods: List<Pair<String, Period>> = listOf(
                            "1m" to Period(Period.PeriodType.MINUTE),
                            "5m" to Period(Period.PeriodType.MINUTE, 5 * 60_000L),
                            "15m" to Period(Period.PeriodType.MINUTE, 15 * 60_000L),
                            "1h" to Period(Period.PeriodType.HOUR),
                            "1d" to Period(Period.PeriodType.DAY),
                        )
                        periods.forEach { (label, period) ->
                            val selected = currentPeriod.value == period
                            Button(
                                onClick = {
                                    if (!selected) {
                                        currentPeriod.value = period
                                        chartFacade.setPeriod(period)
                                    }
                                },
                                modifier = Modifier.padding(end = 4.dp),
                            ) {
                                Text(label, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { showIndicatorPanel = !showIndicatorPanel }) { Text("指标") }
                    }
                    if (showIndicatorPanel) {
                        IndicatorPanel(facade = chartFacade, onClose = { showIndicatorPanel = false })
                    }
                    if (showStylesPanel) {
                        StylesPanel(facade = chartFacade, onClose = { showStylesPanel = false })
                    }
                    // 门面 Styles 消费：resolveTheme 从 styles 树推导主题快照
                    val facadeTheme = remember(chartFacade.getStyles()) { chartFacade.resolveTheme() }
                    KLineChart(
                        state = state,
                        modifier = Modifier.fillMaxSize().weight(1f),
                        theme = facadeTheme,
                        // T-24 legendSlot 演示：宿主自定义图例（时间 + 收盘 + MA5，英文）
                        legendSlot = { data ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(androidx.compose.ui.graphics.Color(0xCC101020)),
                            ) {
                                androidx.compose.material3.Text(
                                    "${data.timeText}  C ${"%.2f".format(data.candle.close)}" +
                                        "  MA5 ${data.maValues["ma1"]?.let { "%.2f".format(it) } ?: "—"}",
                                    color = androidx.compose.ui.graphics.Color(0xFFD0D0E0),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        },
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    ) {
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
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    ) {
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
                            onClick = { exportPng(state, textMeasurer, chartFacade.resolveTheme()) },
                            modifier = Modifier.padding(start = 8.dp),
                        ) { Text("导出") }
                        // Styles 树演示（批次 5 余量）：dark/light 主题切换经 setStyles
                        var stylesDark by remember { androidx.compose.runtime.mutableStateOf(false) }
                        Button(
                            onClick = {
                                stylesDark = !stylesDark
                                chartFacade.setStyles(if (stylesDark) "dark" else "light")
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        ) { Text(if (stylesDark) "暗" else "亮") }
                        Button(
                            onClick = { showStylesPanel = !showStylesPanel },
                            modifier = Modifier.padding(start = 8.dp),
                        ) { Text("样式") }
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
