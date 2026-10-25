package io.lkanvas.compose

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * K 线图组件占位实现（骨架阶段）。
 * 真正的绘制/手势/指标链路在 T-07/T-08/T-19 中逐步接入。
 */
@Composable
fun KLineChart(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawPlaceholder()
    }
}

private fun DrawScope.drawPlaceholder() {
    drawLine(
        color = Color(0xFF2196F3),
        start = Offset(0f, size.height / 2f),
        end = Offset(size.width, size.height / 2f),
        strokeWidth = 2f,
    )
}
