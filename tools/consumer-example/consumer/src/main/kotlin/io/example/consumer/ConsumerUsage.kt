package io.example.consumer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.lkanvas.compose.KLineChart
import io.lkanvas.compose.state.ChartState
import io.lkanvas.core.data.Candle
import io.lkanvas.core.store.MarketKey

/**
 * 独立消费者接入示例（PRD A-46/T-31）：只使用发布制品的公开 API 完成
 * 基础图表 + 实时更新（PRD 1.3「可接入」验收的编译期证明）。
 *
 * 若此处引用了 internal/隐藏接口，编译将直接失败。
 */
class ConsumerUsage {

    fun createChartState(): ChartState = ChartState()

    suspend fun feedData(state: ChartState) {
        state.reset(
            MarketKey("EXAMPLE", "1m"),
            (0 until 100).map {
                Candle(
                    it.toLong(), 100.0 + it, 101.0 + it, 99.0 + it, 100.5 + it, 1000.0,
                )
            },
        )
        state.upsertLatest(Candle(200L, 200.0, 201.0, 199.0, 200.5, 500.0))
    }

    @Composable
    fun Chart() {
        val state = remember { ChartState() }
        LaunchedEffect(state) { feedData(state) }
        KLineChart(state = state, modifier = Modifier.fillMaxSize())
    }
}
