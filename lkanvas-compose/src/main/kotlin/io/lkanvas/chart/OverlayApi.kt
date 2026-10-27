package io.lkanvas.chart

import io.lkanvas.core.overlay.OverlayPoint
import io.lkanvas.core.overlay.OverlayTemplates

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
    val points: List<OverlayPoint> = emptyList(),
    val extendData: Map<String, String> = emptyMap(),
    val lock: Boolean = false,
    val visible: Boolean = true,
    val zLevel: Int = 0,
)

/** 上游 `OverlayOverride`：按 filter 定位后的字段级覆盖。 */
data class OverlayOverride(
    val points: List<OverlayPoint>? = null,
    val extendData: Map<String, String>? = null,
    val lock: Boolean? = null,
    val visible: Boolean? = null,
    val zLevel: Int? = null,
)

/** 上游 `OverlayFilter`。 */
data class OverlayFilter(
    val id: String? = null,
    val groupId: String? = null,
    val name: String? = null,
    val paneId: String? = null,
) {
    fun matches(id: String, templateName: String, actualPaneId: String, hasGroup: Boolean): Boolean =
        (this.id == null || this.id == id) &&
            (name == null || name == templateName) &&
            (paneId == null || paneId == actualPaneId) &&
            (groupId == null || (hasGroup && groupId == id)) // 内核无 group 模型，暂以 id 同名兜底
}

/** 图表上的覆盖物只读视图（上游 Overlay 信息面）。 */
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
)
