package io.lkanvas.core.overlay

/**
 * 覆盖物约束与模板契约（PRD 6.2，上游 component/Overlay.ts + totalStep/几何口径）。
 *
 * 约束：HORIZONTAL（水平族）、VERTICAL（垂直族）、NONE。
 * continuous=true 表示连续采样模板（brush），由宿主显式调用 finishCreate 完成；
 * 采样点数达 [maxPoints] 时终止采样并报告（PRD R-09：brush 建议上限 5000 点）。
 */
enum class OverlayConstraint { NONE, HORIZONTAL, VERTICAL }

/**
 * 覆盖物模板定义（批次 4：补齐上游 OverlayTemplate 字段）。
 * 内置 16 模板见 `io.lkanvas.core.extension.overlay`（上游同名文件镜像）；
 * 全局注册表见 `io.lkanvas.core.extension.overlay.OverlayTemplates`（上游
 * extension/overlay/index.ts 镜像：注册与创建/渲染/命中共用同一张表）。
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
        /** 渲染层换算好的控制点像素坐标 [[x,y],...]（上游 coordinates 语义）。 */
        pixelPoints: List<List<Double>> = emptyList(),
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
     * (instanceId, 命中点索引或 -1, 坐标px)。
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
