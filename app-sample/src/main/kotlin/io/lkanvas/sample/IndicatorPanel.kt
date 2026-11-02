package io.lkanvas.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import io.lkanvas.chart.LKanvasChart
import io.lkanvas.compose.state.ChartState
import io.lkanvas.core.extension.indicator.IndicatorTemplates
import io.lkanvas.core.indicator.IndicatorSeries

@Composable
fun IndicatorPanel(facade: LKanvasChart) {
    val instances = remember(facade.state.viewRevision) { facade.getIndicators().filterNot { it.id.startsWith("sample_") } }
    val names = remember { IndicatorTemplates.supported() }
    val alternates = remember {
        mapOf("MA" to listOf(5.0, 10.0), "EMA" to listOf(12.0), "BOLL" to listOf(20.0, 3.0),
            "RSI" to listOf(14.0), "KDJ" to listOf(14.0, 3.0, 3.0), "MACD" to listOf(8.0, 17.0, 9.0))
    }
    var error by remember { mutableStateOf<String?>(null) }
    Column {
        PanelCaption("${names.size} 种内置指标 · 开关立即生效；部分指标可切换参数 B。")
        error?.let { PanelCaption(it) }
        listOf(true to "主图叠加", false to "副图指标").forEach { (price, title) ->
            PanelHeading(title)
            names.filter { (IndicatorTemplates.get(it)?.series == IndicatorSeries.PRICE) == price }.forEach { name ->
                val definition = IndicatorTemplates.get(name) ?: return@forEach
                val active = instances.filter { it.definition.name == name }
                val params = active.firstOrNull()?.params ?: definition.calcParams
                val alternate = alternates[name]
                val usingAlternate = alternate != null && params == alternate
                ListItem(
                    headlineContent = { Text(name, fontSize = 15.sp) },
                    supportingContent = { Text(params.joinToString(", ") { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }, fontSize = 12.sp) },
                    trailingContent = {
                        Row {
                            if (alternate != null && active.isNotEmpty()) {
                                TextButton(onClick = {
                                    active.forEach { facade.overrideIndicator(it.id, calcParams = if (usingAlternate) definition.calcParams else alternate) }
                                }) { Text(if (usingAlternate) "恢复默认" else "参数 B", fontSize = 12.sp) }
                            }
                            Switch(active.isNotEmpty(), onCheckedChange = null)
                        }
                    },
                    modifier = Modifier.toggleable(active.isNotEmpty(), role = Role.Switch) { enabled ->
                        error = null
                        if (enabled) {
                            val pane = when { price -> "main"; name == "VOL" -> "vol"; name == "MACD" -> "macd"; else -> null }
                            if (facade.createIndicator(name, paneId = pane, isStack = price) == null) error = "$name 添加失败，请先移除不需要的副图"
                        } else active.forEach { facade.removeIndicator(ChartState.IndicatorFilter(id = it.id)) }
                    },
                )
            }
        }
    }
}
