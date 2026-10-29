package io.lkanvas.core.common

/**
 * 上游 `common/VisibleRange.ts` 的 Kotlin 镜像：可见窗口。
 * from/to 为数据索引；realFrom/realTo 为含未来留白的实际范围（小数）。
 */
data class VisibleRange(
    val from: Int,
    val to: Int,
    val realFrom: Double,
    val realTo: Double,
)
