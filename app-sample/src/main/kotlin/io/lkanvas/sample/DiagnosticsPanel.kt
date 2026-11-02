package io.lkanvas.sample

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lkanvas.chart.LKanvasChart
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun DiagnosticsPanel(facade: LKanvasChart) {
    var refresh by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val diagnostics = facade.state.diagnostics
    LaunchedEffect(diagnostics) {
        while (true) { delay(1_000); refresh++ }
    }
    val entries = remember(refresh) { diagnostics.entries().takeLast(20).asReversed() }
    val enabled = remember(refresh) { diagnostics.isEnabled() }
    val context = LocalContext.current
    Column {
        PanelCaption("采集在进程内进行，默认关闭，不上传。每秒刷新；可复制摘要与事件。")
        SettingSwitch("诊断采集", if (enabled) "采集已开启" else "采集已关闭", enabled) {
            diagnostics.setEnabled(it)
            refresh++
        }
        PanelHeading("当前图表")
        SelectionContainer {
            Text(
                "品种：${facade.getSymbol()?.ticker ?: "STRESS"}\n" +
                    "周期：${facade.getPeriod()?.toMillis()?.let { "$it ms" } ?: "60,000 ms（压力模式）"}\n" +
                    "数据：${facade.state.dataSnapshot.candles.size} 根\n" +
                    "视口：${facade.getVisibleRange()}\n" +
                    "视图版本：${facade.state.viewRevision} · 覆盖物版本：${facade.state.overlayRevision}",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 12.sp,
            )
        }
        PanelHeading("计算与渲染摘要")
        SelectionContainer {
            Text(remember(refresh) { diagnostics.summary() }, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { refresh++ }, modifier = Modifier.weight(1f)) { Text("刷新") }
            FilledTonalButton(onClick = {
                val out = File(context.cacheDir, "lkanvas-diagnostics.txt")
                runCatching {
                    out.writeText(buildString {
                        appendLine("summary: ${diagnostics.summary()}")
                        appendLine("entries (newest first):")
                        diagnostics.entries().asReversed().forEach { appendLine("#${it.revision} ${it.kind}: ${it.detail}") }
                    })
                }.onSuccess { feedback = "已导出到应用缓存：${out.absolutePath}" }
                    .onFailure { feedback = "导出失败：${it.message ?: "无法写入文件"}" }
            }, modifier = Modifier.weight(1f)) { Text("导出日志") }
        }
        feedback?.let { SelectionContainer { PanelCaption(it) } }
        PanelHeading("最近事件 · 新 → 旧 · 最多 20 条")
        if (entries.isEmpty()) PanelCaption(if (enabled) "尚无事件，操作图表后可在这里查看。" else "开启采集后，操作图表即可记录事件。")
        entries.forEach { entry ->
            ListItem(
                headlineContent = { Text("#${entry.revision} · ${entry.kind}", fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                supportingContent = { SelectionContainer { Text(entry.detail, fontSize = 12.sp) } },
            )
        }
    }
}
