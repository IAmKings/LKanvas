package io.lkanvas.chart

import io.lkanvas.core.overlay.OverlayPoint
import io.lkanvas.core.extension.overlay.OverlayTemplates

/**
 * 覆盖物操作 API——对齐上游 `createOverlay/getOverlays/overrideOverlay/removeOverlay`
 * 与 `OverlayMode`（'normal'|'weak_magnet'|'strong_magnet'）。
 *
 * 编程式创建（上游 OverlayCreate 带 points 的用法）与交互式创建（不带 points，
 * 进入点选流程）都支持；数组重载批量创建。
 */

/** 上游 `OverlayMode`：磁吸三档（门面同名枚举，映射内核 MagnetMode）。 */
enum class OverlayMode { NORMAL, WEAK_MAGNET, STRONG_MAGNET }

/** 上游 `OverlayCreate`：name + 可选 points（带 points 即编程式创建）。 */
data class OverlayCreate(
    val name: String,
    val groupId: String? = null,
    val points: List<OverlayPoint> = emptyList(),
    val extendData: Map<String, String> = emptyMap(),
    val lock: Boolean = false,
    val visible: Boolean = true,
    val zLevel: Int = 0,
    /** 上游 modeSensitivity：磁吸灵敏度像素（默认 8）。 */
    val modeSensitivity: Double = 8.0,
    /** 上游 styles：实例级样式覆盖。 */
    val styles: Any? = null,
)

/** 上游 `OverlayOverride`：按 filter 定位后的字段级覆盖。 */
data class OverlayOverride(
    val points: List<OverlayPoint>? = null,
    val groupId: String? = null,
    val extendData: Map<String, String>? = null,
    val lock: Boolean? = null,
    val visible: Boolean? = null,
    val zLevel: Int? = null,
    val fixedZLevel: Boolean? = null,
    val modeSensitivity: Double? = null,
    val styles: Any? = null,
)

/** 上游 `OverlayFilter`。 */
data class OverlayFilter(
    val id: String? = null,
    val groupId: String? = null,
    val name: String? = null,
    val paneId: String? = null,
) {
    fun matches(id: String, templateName: String, actualPaneId: String, groupId: String? = null): Boolean =
        (this.id == null || this.id == id) &&
            (name == null || name == templateName) &&
            (paneId == null || paneId == actualPaneId) &&
            (this.groupId == null || this.groupId == groupId)
}

/** 图表上的覆盖物只读视图（上游 Overlay 信息面，24 字段对齐子集）。 */
data class OverlayInfo(
    val id: String,
    val groupId: String?,
    val name: String,
    val paneId: String,
    val points: List<OverlayPoint>,
    val locked: Boolean,
    val visible: Boolean,
    val zLevel: Int,
    val extendData: Map<String, String>,
    /** 上游 currentStep：当前交互步（创建中 = points.size）。 */
    val currentStep: Int = points.size,
    /** 上游 fixedZLevel：层级是否固定（true=不随选中浮起）。 */
    val fixedZLevel: Boolean = false,
    /** 上游 modeSensitivity：磁吸灵敏度像素（默认 8）。 */
    val modeSensitivity: Double = 8.0,
    /** 上游 styles：实例级样式覆盖（宿主自定义结构）。 */
    val styles: Any? = null,
)
