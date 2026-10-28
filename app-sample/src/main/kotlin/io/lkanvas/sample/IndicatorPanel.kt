package io.lkanvas.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lkanvas.chart.LKanvasChart
import io.lkanvas.compose.state.ChartState

/**
 * 指标面板（批次 6 余量）：对齐上游 demo 的指标选择/参数/移除交互——
 * 全部经门面公开 API（createIndicator/overrideIndicator/removeIndicator）。
 */
@Composable
fun IndicatorPanel(facade: LKanvasChart, onClose: () -> Unit) {
    val indicators = listOf(
        Triple("MA", listOf(5.0, 10.0, 30.0, 60.0), listOf(5.0, 10.0)),
        Triple("EMA", listOf(6.0, 12.0, 20.0), listOf(12.0)),
        Triple("BOLL", listOf(20.0, 2.0), listOf(20.0, 3.0)),
        Triple("KDJ", listOf(9.0, 3.0, 3.0), listOf(9.0, 3.0, 3.0)),
        Triple("RSI", listOf(6.0, 12.0, 24.0), listOf(14.0)),
        Triple("MACD", listOf(12.0, 26.0, 9.0), listOf(12.0, 26.0, 9.0)),
    )
    var visibleNames by remember { androidx.compose.runtime.mutableStateOf(setOf("MA")) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC101020))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text("指标面板", color = Color(0xFFD0D0E0), fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "关闭",
                color = Color(0xFF9295A5),
                fontSize = 12.sp,
                modifier = Modifier.clickable { onClose() },
            )
        }
        indicators.forEach { (name, defaultParams, altParams) ->
            val instances = facade.getIndicators(io.lkanvas.compose.state.ChartState.IndicatorFilter(name = name))
                .filter { it.id.startsWith("ind_") }
            val created = instances.isNotEmpty()
            val usingAlt = created && instances.first().params == altParams
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    text = (if (created) "●" else "○") + " $name" + (if (usingAlt) " (参数B)" else ""),
                    color = if (created) Color(0xFF2DC08E) else Color(0xFF9295A5),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (!created) {
                                facade.createIndicator(name, calcParams = defaultParams)
                            } else {
                                facade.removeIndicator(ChartState.IndicatorFilter(name = name))
                            }
                        },
                )
                // 参数切换：默认 ↔ 备选（overrideIndicator 语义）
                Text(
                    text = "切参数",
                    color = Color(0xFF76808F),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .clickable {
                            instances.firstOrNull()?.let {
                                facade.overrideIndicator(it.id, calcParams = if (usingAlt) defaultParams else altParams)
                            }
                        },
                )
            }
        }
    }
}
