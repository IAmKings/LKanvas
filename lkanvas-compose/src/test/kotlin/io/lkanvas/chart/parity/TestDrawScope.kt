package io.lkanvas.chart.parity

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.drawscope.DrawContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

/**
 * 测试用录制 DrawScope（第六轮复审 M02/M03/M04 验收探针）：
 * 渲染器真实调用路径下记录 draw/clip/translate 操作——
 * - [ops]：操作流水；
 * - [rects]/[circles]/[lines]：绘制参数（**translate 感知的有效屏幕坐标**，供
 *   M03 attrs 几何 / M04 控点屏幕位置断言）；
 * - [clipDepth]：当前裁剪栈深度（M04 控点裁剪归属断言）；
 * - [clips]：裁剪矩形（有效屏幕坐标）+ 深度。
 */
class TestDrawScope(override val size: Size) : DrawScope {

    data class RectOp(val l: Float, val t: Float, val r: Float, val b: Float, val depth: Int)
    data class CircleOp(val cx: Float, val cy: Float, val radius: Float, val depth: Int)
    data class LineOp(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val depth: Int)

    val ops = mutableListOf<String>()
    val rects = mutableListOf<RectOp>()
    val circles = mutableListOf<CircleOp>()
    val lines = mutableListOf<LineOp>()
    val clips = mutableListOf<Pair<Rect, Int>>()
    internal var tx = 0f
    internal var ty = 0f
    internal var _clipDepth = 0
    val clipDepth: Int get() = _clipDepth

    private fun effX(x: Float) = x + tx
    private fun effY(y: Float) = y + ty

    override val drawContext: DrawContext = object : DrawContext {
        override var size: Size = this@TestDrawScope.size
        override val transform: DrawTransform = RecTransform(this@TestDrawScope)
        override var canvas: Canvas = RecordingCanvas(this@TestDrawScope)
        override var layoutDirection: LayoutDirection = LayoutDirection.Ltr
        override var density: androidx.compose.ui.unit.Density = this@TestDrawScope
    }

    override val layoutDirection: LayoutDirection = LayoutDirection.Ltr
    override val density = 1f
    override val fontScale = 1f

    // ---- 绘制原语：记录有效屏幕坐标 + 当前裁剪深度 ----

    override fun drawLine(color: Color, start: Offset, end: Offset, strokeWidth: Float, cap: StrokeCap, pathEffect: PathEffect?, alpha: Float, colorFilter: ColorFilter?, blendMode: BlendMode) {
        ops += "line"
        lines += LineOp(effX(start.x), effY(start.y), effX(end.x), effY(end.y), _clipDepth)
    }

    override fun drawLine(brush: Brush, start: Offset, end: Offset, strokeWidth: Float, cap: StrokeCap, pathEffect: PathEffect?, alpha: Float, colorFilter: ColorFilter?, blendMode: BlendMode) {
        ops += "line-brush"
    }

    override fun drawRect(color: Color, topLeft: Offset, size: Size, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) {
        ops += "rect"
        val l = effX(topLeft.x); val t = effY(topLeft.y)
        rects += RectOp(l, t, l + size.width, t + size.height, _clipDepth)
    }

    override fun drawRect(brush: Brush, topLeft: Offset, size: Size, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) {
        ops += "rect-brush"
    }

    override fun drawRoundRect(color: Color, topLeft: Offset, size: Size, cornerRadius: CornerRadius, style: DrawStyle, alpha: Float, colorFilter: ColorFilter?, blendMode: BlendMode) {
        drawRect(color, topLeft, size, alpha, style, colorFilter, blendMode)
    }

    override fun drawRoundRect(brush: Brush, topLeft: Offset, size: Size, cornerRadius: CornerRadius, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) {
        drawRect(Color.Black, topLeft, size, alpha, style, colorFilter, blendMode)
    }

    override fun drawCircle(color: Color, radius: Float, center: Offset, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) {
        ops += "circle"
        circles += CircleOp(effX(center.x), effY(center.y), radius, _clipDepth)
    }

    override fun drawCircle(brush: Brush, radius: Float, center: Offset, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) {
        drawCircle(Color.Black, radius, center, alpha, style, colorFilter, blendMode)
    }

    override fun drawOval(color: Color, topLeft: Offset, size: Size, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) {
        drawRect(color, topLeft, size, alpha, style, colorFilter, blendMode)
    }

    override fun drawOval(brush: Brush, topLeft: Offset, size: Size, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) = Unit

    override fun drawArc(color: Color, startAngle: Float, sweepAngle: Float, useCenter: Boolean, topLeft: Offset, size: Size, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) {
        ops += "arc"
    }

    override fun drawArc(brush: Brush, startAngle: Float, sweepAngle: Float, useCenter: Boolean, topLeft: Offset, size: Size, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) {
        ops += "arc-brush"
    }

    override fun drawPath(path: Path, color: Color, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) {
        ops += "path"
    }

    override fun drawPath(path: Path, brush: Brush, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) {
        ops += "path-brush"
    }

    override fun drawPoints(points: List<Offset>, pointMode: PointMode, color: Color, strokeWidth: Float, cap: StrokeCap, pathEffect: PathEffect?, alpha: Float, colorFilter: ColorFilter?, blendMode: BlendMode) {
        ops += "points"
    }

    override fun drawPoints(points: List<Offset>, pointMode: PointMode, brush: Brush, strokeWidth: Float, cap: StrokeCap, pathEffect: PathEffect?, alpha: Float, colorFilter: ColorFilter?, blendMode: BlendMode) {
        ops += "points-brush"
    }

    override fun drawImage(image: ImageBitmap, topLeft: Offset, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) = Unit

    override fun drawImage(image: ImageBitmap, srcOffset: IntOffset, srcSize: IntSize, dstOffset: IntOffset, dstSize: IntSize, alpha: Float, style: DrawStyle, colorFilter: ColorFilter?, blendMode: BlendMode) = Unit

    // ---- 变换：经 DrawContext.transform/canvas（translate/clip 为 inline 不可覆写，
    // 追踪逻辑在 RecTransform/RecordingCanvas） ----

    private class RecTransform(private val s: TestDrawScope) : DrawTransform {
        override var size: Size = s.size
        override fun translate(left: Float, top: Float) {
            s.ops += "translate($left,$top)"
            s.tx += left; s.ty += top
        }
        override fun scale(scaleX: Float, scaleY: Float, pivot: Offset) = Unit
        override fun rotate(degrees: Float, pivot: Offset) = Unit
        override fun transform(matrix: Matrix) = Unit
        override fun inset(left: Float, top: Float, right: Float, bottom: Float) = translate(left, top)
        override fun clipRect(left: Float, top: Float, right: Float, bottom: Float, clipOp: ClipOp) {
            s.ops += "clip"
            s._clipDepth++
            s.clips += Rect(s.effX(left), s.effY(top), s.effX(right), s.effY(bottom)) to s._clipDepth
        }
        override fun clipPath(path: Path, clipOp: ClipOp) = Unit
    }

    class RecordingCanvas(private val s: TestDrawScope) : Canvas {
        private val stack = ArrayDeque<Triple<Float, Float, Int>>()
        override fun save() {
            stack.addLast(Triple(s.tx, s.ty, s._clipDepth))
        }
        override fun restore() {
            val top = stack.removeLastOrNull() ?: return
            s.tx = top.first; s.ty = top.second; s._clipDepth = top.third
        }
        override fun saveLayer(bounds: Rect, paint: Paint) = save()
        override fun translate(dx: Float, dy: Float) { s.tx += dx; s.ty += dy }
        override fun scale(sx: Float, sy: Float) = Unit
        override fun rotate(degrees: Float) = Unit
        override fun skew(sx: Float, sy: Float) = Unit
        override fun concat(matrix: Matrix) = Unit
        override fun clipRect(left: Float, top: Float, right: Float, bottom: Float, clipOp: ClipOp) {
            s._clipDepth++
            s.clips += Rect(s.effX(left), s.effY(top), s.effX(right), s.effY(bottom)) to s._clipDepth
        }
        override fun clipPath(path: Path, clipOp: ClipOp) = Unit
        override fun drawRect(rect: Rect, paint: Paint) = Unit
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, radiusX: Float, radiusY: Float, paint: Paint) = Unit
        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawArc(left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint) = Unit
        override fun drawPath(path: Path, paint: Paint) = Unit
        override fun drawLine(p1: Offset, p2: Offset, paint: Paint) = Unit
        override fun drawCircle(center: Offset, radius: Float, paint: Paint) = Unit
        override fun drawImage(image: ImageBitmap, topLeftOffset: Offset, paint: Paint) = Unit
        override fun drawImageRect(image: ImageBitmap, srcOffset: IntOffset, srcSize: IntSize, dstOffset: IntOffset, dstSize: IntSize, paint: Paint) = Unit
        override fun drawPoints(pointMode: PointMode, points: List<Offset>, paint: Paint) = Unit
        override fun drawRawPoints(pointMode: PointMode, points: FloatArray, paint: Paint) = Unit
        override fun drawVertices(vertices: Vertices, blendMode: BlendMode, paint: Paint) = Unit
        override fun enableZ() = Unit
        override fun disableZ() = Unit
    }
}

/** draw 钩子探针哨兵：读取 yOf 后中止后续绘制（对齐审计 §探针 口径）。 */
class DrawProbeAbort(val yOf: (Double) -> Double) : RuntimeException("probe-abort")

/** 测试用 ChartText（TextMeasurer 经反射免构造（FontFamily.Resolver 为 sealed）；
 * 钩子探针在文本绘制前中止，measurer 从不触达）。 */
fun fakeChartText(): io.lkanvas.compose.draw.ChartText {
    val unsafeClass = Class.forName("sun.misc.Unsafe")
    val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    val measurer = unsafeClass
        .getMethod("allocateInstance", Class::class.java)
        .invoke(unsafe, androidx.compose.ui.text.TextMeasurer::class.java) as androidx.compose.ui.text.TextMeasurer
    return io.lkanvas.compose.draw.ChartText(measurer, Color.White, 10f)
}
