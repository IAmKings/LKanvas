package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayDefinition

/**
 * 覆盖物模板全局注册表（上游 `src/extension/overlay/index.ts` 镜像，SLA-004/013）。
 *
 * 上游：`overlays[name] = template`、`registerOverlay` / `getOverlayClass` /
 * `getSupportedOverlays` 操作**同一张表**，图表创建、渲染与命中测试经同一入口查询。
 * 本地对齐：门面创建（LKanvasChart.createOverlay）、渲染（OverlayRenderer）、
 * 命中（hitTest）与手势事件派发（OverlayEventDispatch）全部查本表——宿主经
 * [register] 注册的自定义模板端到端可用（审计发现 4/13 收口）。
 *
 * 同名注册：上游直接覆盖；本地 [register] 默认拒绝静默覆盖（PRD R-14），
 * `replace=true` 显式替换——差异登记 gap-matrix。
 */
object OverlayTemplates {

    private val overlays = LinkedHashMap<String, OverlayDefinition>()

    /** 上游 registerOverlay：注册/替换模板。 */
    @Synchronized
    fun register(definition: OverlayDefinition, replace: Boolean = false): Boolean {
        if (!replace && overlays.containsKey(definition.name)) return false
        overlays[definition.name] = definition
        return true
    }

    /** 上游 getOverlayClass：按名查模板。 */
    @Synchronized
    operator fun get(name: String): OverlayDefinition? = overlays[name]

    /** 命名查询别名（上游 getOverlayClass）。 */
    @Synchronized
    fun getDefinition(name: String): OverlayDefinition? = overlays[name]

    /** 上游 getSupportedOverlays。 */
    @Synchronized
    fun supported(): List<String> = overlays.keys.toList()

    init {
        // 内置 16 模板（上游 extensions 注册）
        register(SegmentDefinition)
        register(StraightLineDefinition)
        register(RayLineDefinition)
        register(HorizontalRayLineDefinition)
        register(HorizontalSegmentDefinition)
        register(HorizontalStraightLineDefinition)
        register(VerticalRayLineDefinition)
        register(VerticalSegmentDefinition)
        register(VerticalStraightLineDefinition)
        register(ParallelStraightLineDefinition)
        register(PriceChannelLineDefinition)
        register(FibonacciLineDefinition)
        register(PriceLineDefinition)
        register(SimpleAnnotationDefinition)
        register(SimpleTagDefinition)
        register(BrushDefinition)
    }
}
