package io.lkanvas.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.lkanvas.chart.ActionType
import io.lkanvas.chart.LKanvasChart
import io.lkanvas.chart.Period
import io.lkanvas.chart.PeriodType
import io.lkanvas.chart.SymbolInfo
import io.lkanvas.compose.state.ChartState

val samplePeriods = listOf(
    "1m" to Period(PeriodType.MINUTE),
    "5m" to Period(PeriodType.MINUTE, 5 * 60_000L),
    "15m" to Period(PeriodType.MINUTE, 15 * 60_000L),
    "1h" to Period(PeriodType.HOUR),
    "1d" to Period(PeriodType.DAY),
)
val sampleSymbols = listOf("DEMO", "DEMO-B", "DEMO-C")

class MainActivity : ComponentActivity() {
    private var createdAtNanos = 0L

    override fun reportFullyDrawn() {
        super.reportFullyDrawn()
        android.util.Log.i("LKanvas_READY", "created=$createdAtNanos readyMs=${(android.os.SystemClock.elapsedRealtimeNanos() - createdAtNanos) / 1_000_000.0}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        createdAtNanos = android.os.SystemClock.elapsedRealtimeNanos()
        super.onCreate(savedInstanceState)
        // 示例以 60fps 为基础档；高刷诊断显式跟随屏幕，库不修改系统刷新率。
        window.attributes = window.attributes.apply {
            preferredRefreshRate = if (intent.getBooleanExtra("follow_display_refresh_rate", false)) 0f else 60f
        }
        enableEdgeToEdge()
        FigureSamples.registerAll()
        val stress = takeIf { intent?.getBooleanExtra("stress_mode", false) == true }?.let {
            StressConfig(
                candles = intent?.getIntExtra("stress_candles", 50_000) ?: 50_000,
                indicators = intent?.getIntExtra("stress_indicators", 10) ?: 10,
                overlays = intent?.getIntExtra("stress_overlays", 100) ?: 100,
            )
        }
        setContent {
            val scope = rememberCoroutineScope()
            val state = remember { ChartState(scope) }
            val facade = remember { LKanvasChart.init(state) }
            val feed = remember { LocalFeed(scope) }
            var dark by rememberSaveable { mutableStateOf(true) }
            var periodIndex by rememberSaveable { mutableIntStateOf(0) }
            var symbolIndex by rememberSaveable { mutableIntStateOf(0) }
            DisposableEffect(facade) {
                onDispose { LKanvasChart.dispose(facade) }
            }
            LaunchedEffect(facade) {
                facade.createIndicator("MA", paneId = "main", isStack = true)
                facade.createIndicator("VOL", paneId = "vol")
                facade.createIndicator("MACD", paneId = "macd")
                facade.setStyles(facade.getStyles().let {
                    it.copy(candle = it.candle.copy(tooltip = it.candle.tooltip.copy(showRule = "follow_cross")))
                })
                facade.subscribeAction(ActionType.ON_CANDLE_TOOLTIP_FEATURE_CLICK) { data ->
                    android.util.Log.i("LKanvas", "feature click: $data")
                }
                if (stress != null) feed.startStress(state, stress, facade) else {
                    facade.setOffsetRightDistance(24.0 * resources.displayMetrics.density)
                    facade.setSymbol(SymbolInfo(sampleSymbols[symbolIndex]))
                    facade.setPeriod(samplePeriods[periodIndex].second)
                    facade.setDataLoader(feed)
                }
            }
            LaunchedEffect(dark) { facade.setStyles(sampleStyles(dark, facade.getStyles())) }
            // 覆盖物列表不是快照状态，观察 revision 才能报告完整压力负载就绪。
            val stressOverlayCount = if (stress != null) remember(state.overlayRevision) { state.overlays.all().size } else 0
            ReportDrawnWhen {
                state.dataSnapshot.candles.size >= (stress?.candles ?: 500) &&
                    state.maResult != null && state.volResult != null && state.macdResult != null &&
                    (stress == null || stressOverlayCount >= stress.overlays)
            }
            SampleTheme(dark) {
                SampleWorkspace(
                    facade, feed, dark, periodIndex, stress,
                    onThemeChange = { dark = !dark },
                    onPeriodChange = {
                        periodIndex = it
                        facade.setPeriod(samplePeriods[it].second)
                    },
                    onSymbolChange = {
                        symbolIndex = (symbolIndex + 1) % sampleSymbols.size
                        facade.setSymbol(SymbolInfo(sampleSymbols[symbolIndex]))
                    },
                )
            }
        }
    }
}
