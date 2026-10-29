package io.lkanvas.core.common

/**
 * 上游 `common/BarSpace.ts` 的 Kotlin 镜像：槽位四值。
 */
data class BarSpace(val bar: Double, val halfBar: Double, val gapBar: Double, val halfGapBar: Double)

/**
 * 上游 `common/Point.ts` 的 Kotlin 镜像：二维坐标点。
 */
data class Point(val x: Double, val y: Double)
