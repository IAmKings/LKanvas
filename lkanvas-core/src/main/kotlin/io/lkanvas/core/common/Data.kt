package io.lkanvas.core.common

import io.lkanvas.core.data.Candle

/**
 * 上游 `common/Data.ts` 的 Kotlin 镜像（结构对齐批次 06）。
 *
 * - [KLineData]：上游 KLineData 的本域实现名保持 [Candle]（core/data/Candle.kt），
 *   此处以别名登记上游名，保证补丁检索时可按上游符号定位。
 * - [NeighborData]：点击/命中 payload 的邻居三元组 `{prev, current, next}`（嵌套结构）。
 * - [VisibleRangeData]：上游点击 payload `{dataIndex, x, data: NeighborData}`。
 */
typealias Timestamp = Long

/** 上游 `KLineData`（common/Data.ts）→ 本域实现 [Candle]（data/Candle.kt）。 */
typealias KLineData = Candle

/** 上游 `NeighborData<D>`：prev/current/next 邻居数据。 */
data class NeighborData<D>(
    val prev: D?,
    val current: D,
    val next: D?,
)

/**
 * 上游 `VisibleRangeData`：单根 candle 的命中/点击数据。
 * 与上游一致为嵌套结构：`data = NeighborData{prev, current, next}`。
 */
data class VisibleRangeData(
    val dataIndex: Int,
    val x: Double,
    val data: NeighborData<KLineData>,
)
