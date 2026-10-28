package io.example.consumer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.lkanvas.chart.ActionCallback
import io.lkanvas.chart.ActionType
import io.lkanvas.chart.DataLoader
import io.lkanvas.chart.LKanvasChart
import io.lkanvas.chart.Period
import io.lkanvas.chart.SymbolInfo
import io.lkanvas.core.data.Candle
import io.lkanvas.compose.KLineChart

/**
 * 独立消费者接入示例（PRD A-46/T-31 + 对齐批次 DoD-4）：
 * 只使用发布制品的公开 API，**经 LKanvasChart 门面**（上游 KLineChart API 面）
 * 完成数据接入 + 实时更新 + 指标创建 + 事件订阅。
 * 若此处引用了 internal/隐藏接口，编译将直接失败。
 */
class ConsumerUsage {

    val chart: LKanvasChart = LKanvasChart.init()

    init {
        // 上游 quickstart DataLoader 模式
        chart.setDataLoader(object : DataLoader {
            override fun getBars(params: DataLoader.GetBarsParams) {
                if (params.type == DataLoader.DataLoadType.INIT) {
                    params.callback(
                        (0 until 100).map {
                            Candle(
                                it.toLong(), 100.0 + it, 101.0 + it, 99.0 + it, 100.5 + it, 1000.0,
                            )
                        },
                        DataLoader.DataLoadMore.NONE,
                    )
                }
            }
        })
        chart.setSymbol(SymbolInfo("EXAMPLE"))
        chart.setPeriod(Period(Period.PeriodType.MINUTE))
        // 上游 createIndicator 语义
        chart.createIndicator("MA")
        // 上游 subscribeAction 语义
        chart.subscribeAction(ActionType.ON_CROSSHAIR_CHANGE, ActionCallback { })
    }

    @Composable
    fun Chart() {
        KLineChart(state = chart.state, modifier = Modifier.fillMaxSize())
    }
}
