package io.lkanvas.core.overlay

/**
 * 覆盖物约束与模板（PRD 6.2，上游 totalStep/几何口径逐项对齐）。
 *
 * 约束：HORIZONTAL（水平族）、VERTICAL（垂直族）、NONE。
 * continuous=true 表示连续采样模板（brush），由宿主显式调用 finishCreate 完成；
 * 采样点数达 [maxPoints] 时终止采样并报告（PRD R-09：brush 建议上限 5000 点）。
 */
enum class OverlayConstraint { NONE, HORIZONTAL, VERTICAL }

/**
 * 覆盖物模板定义（批次 4：补齐上游 OverlayTemplate 字段）。
 */
interface OverlayDefinition {
    val name: String

    /** 控制点数量（continuous 模板忽略）；= 上游 totalStep - 1。 */
    val pointCount: Int

    /** 绘制约束族。 */
    val constraint: OverlayConstraint get() = OverlayConstraint.NONE

    /** 是否连续采样（上游 drawingMode = 'continuous'）。 */
    val continuous: Boolean get() = false

    /** 连续采样点数上限。 */
    val maxPoints: Int get() = Int.MAX_VALUE

    /** 是否支持整体拖动。 */
    val supportWholeDrag: Boolean get() = true

    /** 是否支持磁吸。 */
    val supportMagnet: Boolean get() = true

    // ---- 上游 OverlayTemplate 字段对齐（批次 4；默认值对齐上游缺省） ----

    /** 上游 totalStep：控制点数 + 1（交互步数）。 */
    val totalStep: Int get() = pointCount + 1

    /** 上游 needDefaultPointFigure：渲染默认控制点。 */
    val needDefaultPointFigure: Boolean get() = true

    /** 上游 needDefaultXAxisFigure：渲染 x 轴方向附加图形。 */
    val needDefaultXAxisFigure: Boolean get() = true

    /** 上游 needDefaultYAxisFigure：渲染 y 轴方向附加图形。 */
    val needDefaultYAxisFigure: Boolean get() = true

    /** 上游 extendData：模板扩展数据（宿主自定义）。 */
    val extendData: Any? get() = null

    /**
     * 上游 createPointFigures 钩子：自定义模板可返回 Figure 原语描述
     * （params=控制点像素坐标 + bounding）；null=使用内置渲染分支。
     * 返回元素为 Triple(figureType, attrs描述, styles键值对) 的简化建模。
     */
    fun createPointFigures(
        points: List<OverlayPoint>,
        boundingWidth: Double,
        boundingHeight: Double,
    ): List<Triple<String, List<List<Double>>, Map<String, String>>>? = null

    /** 上游 createXAxisFigures 钩子（x 轴区域附加图形）。 */
    fun createXAxisFigures(
        points: List<OverlayPoint>,
        boundingWidth: Double,
        boundingHeight: Double,
    ): List<Triple<String, List<List<Double>>, Map<String, String>>>? = null

    /** 上游 createYAxisFigures 钩子（y 轴区域附加图形）。 */
    fun createYAxisFigures(
        points: List<OverlayPoint>,
        boundingWidth: Double,
        boundingHeight: Double,
    ): List<Triple<String, List<List<Double>>, Map<String, String>>>? = null

    /** 上游 checkEventOn 命中测试开关。 */
    val checkEventOn: Boolean get() = true

    /**
     * 上游 performEventPressedMove / performEventMoveForDrawing：
     * 模板自定义拖动钩子（按压移动已画点 / 绘制中移动末点）。
     * Alpha 建模接口位：默认 null 走内置手势（整拖/点拖），宿主模板可覆写。
     * 参数为 (instanceId, 事件点索引, 目标锚点)。
     */
    val performEventPressedMove: ((String, Int, OverlayPoint) -> Unit)? get() = null
    val performEventMoveForDrawing: ((String, OverlayPoint) -> Unit)? get() = null

    /**
     * 上游事件回调族建模（onClick/onDoubleClick/onRightClick、onMouse 系、onTouch 系）。
     * Android 等价映射：onClick=轻点命中、onDoubleClick=双击命中、onRightClick=长按菜单；
     * onMouse 系与 onTouch 系统一映射到触控生命周期。数据载荷为
     * (instanceId, 命中点索引或 -1, 坐标px)。Alpha 建模接口位，手势端分发随批次 5+。
     */
    val onClick: ((String, Int, Double, Double) -> Unit)? get() = null
    val onDoubleClick: ((String, Int, Double, Double) -> Unit)? get() = null
    val onRightClick: ((String, Int, Double, Double) -> Unit)? get() = null
    val onMouseEnter: ((String) -> Unit)? get() = null
    val onMouseLeave: ((String) -> Unit)? get() = null
    val onMouseDown: ((String, Int, Double, Double) -> Unit)? get() = null
    val onMouseMove: ((String, Int, Double, Double) -> Unit)? get() = null
    val onMouseUp: ((String, Int, Double, Double) -> Unit)? get() = null
    val onTouchStart: ((String, Int, Double, Double) -> Unit)? get() = null
    val onTouchMove: ((String, Int, Double, Double) -> Unit)? get() = null
    val onTouchEnd: ((String, Int, Double, Double) -> Unit)? get() = null
}

/** O-01 segment：两点线段（Alpha 已交付）。 */
object SegmentDefinition : OverlayDefinition {
    override val name = "segment"
    override val pointCount = 2
}

// ---------- T-20 基础线工具组（O-02~O-09，上游 totalStep-1 = 控制点数） ----------

/** O-02 straightLine：两点确定的无限直线（双向延伸裁剪）。 */
object StraightLineDefinition : OverlayDefinition {
    override val name = "straightLine"
    override val pointCount = 2
}

/** O-03 rayLine：射线（自第一点经第二点单向无限延伸）。 */
object RayLineDefinition : OverlayDefinition {
    override val name = "rayLine"
    override val pointCount = 2
}

/** O-04 horizontalRayLine：水平射线（自第一点沿第二点方向水平延伸）。 */
object HorizontalRayLineDefinition : OverlayDefinition {
    override val name = "horizontalRayLine"
    override val pointCount = 2
    override val constraint = OverlayConstraint.HORIZONTAL
}

/** O-05 horizontalSegment：水平线段（两端同 y，x 区间为两点之间）。 */
object HorizontalSegmentDefinition : OverlayDefinition {
    override val name = "horizontalSegment"
    override val pointCount = 2
    override val constraint = OverlayConstraint.HORIZONTAL
}

/** O-06 horizontalStraightLine：全宽水平线（1 点）。 */
object HorizontalStraightLineDefinition : OverlayDefinition {
    override val name = "horizontalStraightLine"
    override val pointCount = 1
    override val constraint = OverlayConstraint.HORIZONTAL
}

/** O-07 verticalRayLine：垂直射线（自第一点沿第二点方向垂直延伸）。 */
object VerticalRayLineDefinition : OverlayDefinition {
    override val name = "verticalRayLine"
    override val pointCount = 2
    override val constraint = OverlayConstraint.VERTICAL
}

/** O-08 verticalSegment：垂直线段（两端同 x，y 区间为两点之间）。 */
object VerticalSegmentDefinition : OverlayDefinition {
    override val name = "verticalSegment"
    override val pointCount = 2
    override val constraint = OverlayConstraint.VERTICAL
}

/** O-09 verticalStraightLine：全高垂直线（1 点；时间锚点）。 */
object VerticalStraightLineDefinition : OverlayDefinition {
    override val name = "verticalStraightLine"
    override val pointCount = 1
    override val constraint = OverlayConstraint.VERTICAL
}

// ---------- T-21 通道与斐波那契（O-10~O-12） ----------

/** O-10 parallelStraightLine：平行线（3 点：p0p1 主线 + 过 p2 平行线）。 */
object ParallelStraightLineDefinition : OverlayDefinition {
    override val name = "parallelStraightLine"
    override val pointCount = 3
}

/** O-11 priceChannelLine：价格通道（3 点；主线 + 平行线 + 按间距延伸的通道线，上游 count=1）。 */
object PriceChannelLineDefinition : OverlayDefinition {
    override val name = "priceChannelLine"
    override val pointCount = 3
}

/** O-12 fibonacciLine：斐波那契（2 点；比例与上游一致，层级按价格空间计算后投影，D-02）。 */
object FibonacciLineDefinition : OverlayDefinition {
    override val name = "fibonacciLine"
    override val pointCount = 2

    /** 上游 fibonacciLine.ts 的固定比例（自高点到低点）。 */
    val ratios: List<Double> = listOf(1.0, 0.786, 0.618, 0.5, 0.382, 0.236, 0.0)
}

// ---------- T-22 文字/价格/brush（O-13~O-16） ----------

/** O-13 priceLine：价格线（1 点；该点价格处水平线 + 标签）。 */
object PriceLineDefinition : OverlayDefinition {
    override val name = "priceLine"
    override val pointCount = 1
    override val constraint = OverlayConstraint.HORIZONTAL
}

/** O-14 simpleAnnotation：简单注解（1 点 + extendData["text"]）。 */
object SimpleAnnotationDefinition : OverlayDefinition {
    override val name = "simpleAnnotation"
    override val pointCount = 1
    override val needDefaultPointFigure = false
    override val needDefaultXAxisFigure = false
    override val needDefaultYAxisFigure = false
}

/** O-15 simpleTag：简单标签（1 点 + extendData["text"]；带底色标签）。 */
object SimpleTagDefinition : OverlayDefinition {
    override val name = "simpleTag"
    override val pointCount = 1
    override val needDefaultPointFigure = false
    override val needDefaultXAxisFigure = false
    override val needDefaultYAxisFigure = false
}

/** O-16 brush：毛笔（连续采样；上限 5000 点，超限终止采样并报告）。 */
object BrushDefinition : OverlayDefinition {
    override val name = "brush"
    override val pointCount = 0
    override val continuous = true
    override val maxPoints = 5000
    override val supportMagnet = false
    override val totalStep = 2
    override val needDefaultPointFigure = false
    override val needDefaultXAxisFigure = false
    override val needDefaultYAxisFigure = false
}

/** 全部内置覆盖物模板（16 种，键=上游名）。 */
object OverlayTemplates {
    val all: Map<String, OverlayDefinition> = listOf(
        SegmentDefinition,
        StraightLineDefinition,
        RayLineDefinition,
        HorizontalRayLineDefinition,
        HorizontalSegmentDefinition,
        HorizontalStraightLineDefinition,
        VerticalRayLineDefinition,
        VerticalSegmentDefinition,
        VerticalStraightLineDefinition,
        ParallelStraightLineDefinition,
        PriceChannelLineDefinition,
        FibonacciLineDefinition,
        PriceLineDefinition,
        SimpleAnnotationDefinition,
        SimpleTagDefinition,
        BrushDefinition,
    ).associateBy { it.name }

    operator fun get(name: String): OverlayDefinition? = all[name]
}
