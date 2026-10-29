package io.lkanvas.core.common

/**
 * 上游 `common/Crosshair.ts` 的 Kotlin 镜像：十字线状态（全字段可选，
 * 与上游 Partial 语义一致——null 即未提供）。
 */
data class Crosshair(
    val x: Double? = null,
    val y: Double? = null,
    val paneId: String? = null,
    val realX: Double? = null,
    val timestamp: Long? = null,
    val kLineData: KLineData? = null,
    val dataIndex: Int? = null,
    val realDataIndex: Double? = null,
)
