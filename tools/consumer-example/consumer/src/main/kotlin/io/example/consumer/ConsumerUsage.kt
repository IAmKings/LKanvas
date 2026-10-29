package io.example.consumer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.lkanvas.chart.ActionCallback
import io.lkanvas.chart.ActionType
import io.lkanvas.chart.DataLoadMore
import io.lkanvas.chart.DataLoadType
import io.lkanvas.chart.GetBarsParams
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
        chart.setDataLoader(object : io.lkanvas.chart.DataLoader {
            override fun getBars(params: GetBarsParams) {
                if (params.type == DataLoadType.INIT) {
                    params.callback(
                        (0 until 100).map {
                            Candle(
                                it.toLong(), 100.0 + it, 101.0 + it, 99.0 + it, 100.5 + it, 1000.0,
                            )
                        },
                        DataLoadMore.NONE,
                    )
                }
            }
        })
        chart.setSymbol(SymbolInfo("EXAMPLE"))
        chart.setPeriod(Period.ofMinutes(1))
        // 上游 createIndicator 语义（0.6.0：缺省自动新建指标 pane + isStack=false）
        chart.createIndicator("MA")
        // 0.6.0 严格语义：图表不预置指标，宿主按需创建 VOL/MACD 副图
        chart.createIndicator("VOL", paneId = "vol")
        chart.createIndicator("MACD", paneId = "macd")
        // 上游 subscribeAction 语义
        chart.subscribeAction(ActionType.ON_CROSSHAIR_CHANGE, ActionCallback { })
    }

    @Composable
    fun Chart() {
        KLineChart(state = chart.state, modifier = Modifier.fillMaxSize())
    }
}
