package io.lkanvas.compose.draw

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import io.lkanvas.core.figure.Figure
import io.lkanvas.core.figure.FigureHit

/**
 * 基础图形绘制映射（PRD 6.3：7 种原语，供自定义覆盖物/指标模板渲染）。
 * 命中测试与 core [FigureHit] 同一几何，保证「所见即所点」。
 */
object FigureRenderer {

    fun DrawScope.drawFigure(
        figure: Figure,
        color: Color,
        strokeWidth: Float = 2f,
        textSizePx: Float = 30f,
    ) {
        when (figure) {
            is Figure.Circle -> {
                drawCircle(color, radius = figure.radius.toFloat(), center = androidx.compose.ui.geometry.Offset(figure.centerX.toFloat(), figure.centerY.toFloat()), style = Stroke(strokeWidth))
            }
            is Figure.Line -> drawLine(
                color,
                androidx.compose.ui.geometry.Offset(figure.x0.toFloat(), figure.y0.toFloat()),
                androidx.compose.ui.geometry.Offset(figure.x1.toFloat(), figure.y1.toFloat()),
                strokeWidth,
            )
            is Figure.Polygon -> if (figure.points.size >= 3) {
                val path = Path()
                path.moveTo(figure.points[0].x.toFloat(), figure.points[0].y.toFloat())
                for (i in 1 until figure.points.size) path.lineTo(figure.points[i].x.toFloat(), figure.points[i].y.toFloat())
                path.close()
                drawPath(path, color, style = Stroke(strokeWidth))
            }
            is Figure.Rect -> drawRect(
                color,
                topLeft = androidx.compose.ui.geometry.Offset(figure.left.toFloat(), figure.top.toFloat()),
                size = androidx.compose.ui.geometry.Size(
                    (figure.right - figure.left).toFloat(),
                    (figure.bottom - figure.top).toFloat(),
                ),
                style = Stroke(strokeWidth),
            )
            is Figure.Text -> drawContext.canvas.nativeCanvas.drawText(
                figure.content,
                figure.x.toFloat(),
                (figure.y + if (figure.baselineBottom) 0f else textSizePx).toFloat(),
                android.graphics.Paint().apply {
                    setColor(
                        android.graphics.Color.argb(
                            (color.alpha * 255).toInt(),
                            (color.red * 255).toInt(),
                            (color.green * 255).toInt(),
                            (color.blue * 255).toInt(),
                        ),
                    )
                    textSize = textSizePx
                    isAntiAlias = true
                },
            )
            is Figure.Arc -> drawArc(
                color,
                startAngle = figure.startAngleDeg.toFloat(),
                sweepAngle = (figure.endAngleDeg - figure.startAngleDeg).toFloat(),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(
                    (figure.centerX - figure.radius).toFloat(),
                    (figure.centerY - figure.radius).toFloat(),
                ),
                size = androidx.compose.ui.geometry.Size(figure.radius.toFloat() * 2, figure.radius.toFloat() * 2),
                style = Stroke(strokeWidth),
            )
            is Figure.Path -> if (figure.points.size >= 2) {
                val path = Path()
                path.moveTo(figure.points[0].x.toFloat(), figure.points[0].y.toFloat())
                for (i in 1 until figure.points.size) path.lineTo(figure.points[i].x.toFloat(), figure.points[i].y.toFloat())
                drawPath(path, color, style = Stroke(strokeWidth))
            }
        }
    }
}
