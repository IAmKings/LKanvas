package io.lkanvas.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lkanvas.chart.LKanvasChart

/**
 * 诊断面板（T-30 余量）：宿主诊断入口 UI 演示——开关诊断、summary、
 * 最近环形缓冲条目、导出到 cacheDir/lkanvas-diagnostics.txt。
 * 数据全部来自 [io.lkanvas.core.diagnostics.ChartDiagnostics]（进程内，
 * 不记录认证信息、不上传）。
 */
@Composable
fun DiagnosticsPanel(facade: LKanvasChart, onClose: () -> Unit) {
    // revision 驱动刷新（每秒自动刷 + 点手动刷）
    var tick by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            tick++
        }
    }
    val diag = facade.state.diagnostics
    val entries = remember(tick) { diag.entries().takeLast(12) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC101020))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text("诊断（进程内，不上传）", color = Color(0xFFD0D0E0), fontSize = 12.sp)
            Spacer0()
            Text(
                "关闭",
                color = Color(0xFF9295A5),
                fontSize = 12.sp,
                modifier = Modifier.clickable { onClose() },
            )
        }

        Row(Modifier.fillMaxWidth()) {
            Text("采集", color = Color(0xFFD0D0E0), fontSize = 12.sp)
            Spacer0()
            Text(
                if (diag.isEnabled()) "开启中" else "已关闭",
                color = Color(0xFF2DC08E),
                fontSize = 12.sp,
                modifier = Modifier.clickable { diag.setEnabled(!diag.isEnabled()) },
            )
        }

        Text(
            remember(tick) { diag.summary() },
            color = Color(0xFF9295A5),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp),
        )

        Text(
            "最近条目（新→旧，最多 12 条）",
            color = Color(0xFFD0D0E0),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        for (e in entries) {
            Text(
                "#${e.revision} ${e.kind}: ${e.detail}",
                color = Color(0xFF76808F),
                fontSize = 10.sp,
            )
        }

        val ctx = androidx.compose.ui.platform.LocalContext.current
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                "导出到 cacheDir",
                color = Color(0xFF2DC08E),
                fontSize = 12.sp,
                modifier = Modifier.clickable {
                    val out = java.io.File(ctx.cacheDir, "lkanvas-diagnostics.txt")
                    out.writeText(
                        buildString {
                            appendLine("summary: ${diag.summary()}")
                            appendLine("entries:")
                            diag.entries().forEach { appendLine("  #${it.revision} ${it.kind}: ${it.detail}") }
                        },
                    )
                    android.util.Log.i("LKanvas", "诊断导出：${out.absolutePath}")
                },
            )
            Spacer0()
            Text(
                "手动刷新",
                color = Color(0xFF9295A5),
                fontSize = 12.sp,
                modifier = Modifier.clickable { tick++ },
            )
        }
    }
}

@Composable
private fun RowScope.Spacer0() {
    Spacer(Modifier.weight(1f))
}
