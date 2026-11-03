package io.lkanvas.core.viewport

import io.lkanvas.core.data.Candle
import kotlin.math.roundToLong

/**
 * 视口状态：横向坐标契约（PRD R-05）。
 *
 * 坐标模型（全部 Double px，密度换算由 Compose 层在最后一步完成）：
 * - `barSpacing`：每根 candle 占用的完整槽位宽度（含间隙）。
 * - `scrollIndex`：x=0（内容区左边缘）对应的逻辑索引，可为小数（平滑滚动）。
 * - 逻辑索引 `i` 的 candle 中心 x = `barSpacing * (i - scrollIndex + 0.5)`；
 *   逻辑索引允许延伸到数据末尾之后的留白区（未来留白用逻辑位置表达）。
 * - 数据索引与逻辑索引在本类中一致；前插历史时由 [onPrepended] 统一平移，
 *   保证既有数据像素位置不动（A-13 锚点保持）。
 *
 * 线程模型：由串行上下文持有与更新；布局后只读方法可并发调用。
 */
class Viewport(
    var barSpacing: Double = DEFAULT_BAR_SPACING,
    var minBarSpacing: Double = DEFAULT_MIN_BAR_SPACING,
    var maxBarSpacing: Double = DEFAULT_MAX_BAR_SPACING,
    /** 右侧初始留白（槽位，可为小数——上游 offsetRightDistance 保持 double，SLA-014）。 */
    var rightOffsetSlots: Double = DEFAULT_RIGHT_OFFSET_SLOTS.toDouble(),
    /** 左向最大越界（槽位；上游 setMaxOffsetLeftDistance，独立于右向）。 */
    var overscrollSlots: Double = DEFAULT_OVERSCROLL_SLOTS,
    /** 右向最大越界（槽位；上游 setMaxOffsetRightDistance，独立于左向/初始留白）。 */
    var maxOffsetRightSlots: Double = DEFAULT_OVERSCROLL_SLOTS,
) {

    /** 左边缘逻辑索引（可为小数）。 */
    var scrollIndex: Double = 0.0
        private set

    /** 最近一次布局的内容宽度（px）。 */
    var contentWidth: Double = 0.0
        private set

    /** 是否完成过首次布局（首次布局默认定位到最新）。 */
    private var initialized = false

    val visibleCount: Double get() = if (barSpacing > 0) contentWidth / barSpacing else 0.0

    // ---------- 布局与坐标 ----------

    /** 内容区尺寸变化时调用；保持锚定行为（首次布局默认贴最新）。 */
    fun layout(width: Double, dataCount: Int) {
        val wasAtLatest = isAtLatest(dataCount)
        val wasAtStart = scrollIndex <= minScroll(dataCount) + 1e-9
        contentWidth = width.coerceAtLeast(0.0)
        when {
            !initialized -> {
                scrollToLatest(dataCount)
                initialized = true
            }
            wasAtLatest -> scrollToLatest(dataCount)
            wasAtStart -> scrollIndex = minScroll(dataCount)
        }
        clamp(dataCount)
    }

    /** 逻辑索引（含未来留白）的 candle 中心 x。 */
    fun candleCenterX(logicalIndex: Double): Double =
        barSpacing * (logicalIndex - scrollIndex + 0.5)

    /** x 位置对应的逻辑索引（小数；十字线/命中用）。 */
    fun logicalIndexAt(x: Double): Double =
        if (barSpacing > 0) x / barSpacing + scrollIndex - 0.5 else scrollIndex

    /** 数据索引 i 的 candle 中心（与 [candleCenterX] 同一来源，主图/十字线/画线共用）。 */
    fun dataIndexCenterX(dataIndex: Int): Double = candleCenterX(dataIndex.toDouble())

    // ---------- 滚动与缩放 ----------

    fun scrollBy(deltaX: Double, dataCount: Int) {
        if (barSpacing <= 0) return
        scrollIndex += deltaX / barSpacing
        clamp(dataCount)
    }

    /** 缩放：保持 anchorX 处的逻辑索引不动（触点锚点）。 */
    fun zoomBy(scale: Double, anchorX: Double, dataCount: Int) {
        val anchorIndex = logicalIndexAt(anchorX)
        val newSpacing = (barSpacing * scale).coerceIn(minBarSpacing, maxBarSpacing)
        if (newSpacing == barSpacing) return
        barSpacing = newSpacing
        scrollIndex = anchorIndex - anchorX / barSpacing + 0.5
        clamp(dataCount)
    }

    /** 以末根数据 candle 为锚点缩放。 */
    fun zoomAtLatest(scale: Double, dataCount: Int) {
        if (dataCount <= 0) {
            zoomBy(scale, anchorX = 0.0, dataCount = dataCount)
            return
        }
        zoomBy(scale, anchorX = candleCenterX((dataCount - 1).toDouble()), dataCount = dataCount)
    }

    fun scrollToIndex(index: Double, dataCount: Int) {
        scrollIndex = index - visibleCount / 2.0
        clamp(dataCount)
    }

    /** 定位到时间戳（二分查找最近的 candle），返回命中的数据索引或 null。 */
    fun scrollToTimestamp(timestamp: Long, candles: List<Candle>): Int? {
        val idx = indexOfTimestamp(timestamp, candles) ?: return null
        scrollToIndex(idx.toDouble(), candles.size)
        return idx
    }

    fun scrollToLatest(dataCount: Int) {
        scrollIndex = maxScroll(dataCount)
    }

    /** 是否跟随在最新（用于新行情到达时决定是否自动滚动，PRD R-05）。 */
    fun isAtLatest(dataCount: Int): Boolean =
        scrollIndex >= maxScroll(dataCount) - 1e-9

    // ---------- 数据变更时的锚点维护 ----------

    /** 前插 n 根：既有数据整体平移 n 个槽位，像素位置保持不变。 */
    fun onPrepended(n: Int) {
        scrollIndex += n
    }

    /** 追加 n 根：只有原来就在末端时跟随，否则视口不动（不强制拉回）。 */
    fun onAppended(n: Int, dataCountBefore: Int, followLatest: Boolean) {
        if (followLatest && isAtLatest(dataCountBefore)) {
            scrollIndex += n
        }
    }

    /** Reset：回到最新。 */
    fun onReset(dataCount: Int) {
        scrollIndex = 0.0
        scrollToLatest(dataCount)
    }

    /** 从存档锚点恢复视口（PRD R-12：锚点为滚动索引+间距，恢复时钳制）。 */
    fun restoreAnchor(anchor: Anchor, dataCount: Int) {
        barSpacing = anchor.barSpacing.coerceIn(minBarSpacing, maxBarSpacing)
        scrollIndex = anchor.scrollIndex
        clamp(dataCount)
    }

    /** 锚点恢复（兼容 ChartArchive.ViewportAnchor 值）。 */
    fun restoreAnchor(scrollIndex: Double, barSpacing: Double, dataCount: Int) {
        restoreAnchor(Anchor(scrollIndex, barSpacing), dataCount)
    }

    /** 当前视口锚点（导出用）。 */
    fun currentAnchor(): Anchor = Anchor(scrollIndex = scrollIndex, barSpacing = barSpacing)

    /** 视口锚点（存档 DTO）。 */
    data class Anchor(val scrollIndex: Double, val barSpacing: Double)

    // ---------- 边界 ----------

    private fun minScroll(dataCount: Int): Double = -overscrollSlots

    /** 有效右侧留白槽位：不超过可视区一半，避免小视口下数据被留白推出屏幕。 */
    private fun effectiveRightSlots(): Double =
        if (visibleCount <= 0.0) 0.0 else minOf(rightOffsetSlots, visibleCount / 2.0)

    private fun maxScroll(dataCount: Int): Double =
        (dataCount + maxOf(effectiveRightSlots(), maxOffsetRightSlots.coerceAtMost(visibleCount / 2.0)) - visibleCount)
            .coerceAtLeast(minScroll(dataCount))

    /** 上游 scrollToDataIndex 模型：目标索引置于右侧留白锚点（距右缘 rightOffset 处，非居中）。 */
    fun scrollToDataIndexAtRightAnchor(index: Int, dataCount: Int) {
        // 有效右留白（与 maxScroll 同封顶：min(rightOffsetSlots, 可见区/2)）
        scrollIndex = index - visibleCount + effectiveRightSlots()
        clamp(dataCount)
    }

    /** 末端滚动边界（公开只读；门面 scrollToRealTime 距离差计算用）。 */
    fun maxScrollFor(dataCount: Int): Double = maxScroll(dataCount)

    /** 有效右留白（公开只读；门面 scrollToDataIndex 距离模型用）。 */
    fun effectiveRightSlotsFor(): Double = effectiveRightSlots()

    /** 左侧最小可见 bar 数（上游 setLeftMinVisibleBarCount；0=不限制）。 */
    var leftMinVisibleBarCount: Int = 0

    /** 右侧最小可见 bar 数（上游 setRightMinVisibleBarCount；0=不限制）。 */
    var rightMinVisibleBarCount: Int = 0

    private fun clamp(dataCount: Int) {
        scrollIndex = scrollIndex.coerceIn(minScroll(dataCount), maxScroll(dataCount))
        // 最小可见数：可见首/尾不得越过此界（上游 setLeft/RightMinVisibleBarCount 语义）
        if (leftMinVisibleBarCount > 0) {
            scrollIndex = scrollIndex.coerceAtLeast(leftMinVisibleBarCount.toDouble() - 1)
        }
        if (rightMinVisibleBarCount > 0) {
            scrollIndex = scrollIndex.coerceAtMost(dataCount - rightMinVisibleBarCount.toDouble())
        }
    }

    companion object {
        const val DEFAULT_BAR_SPACING = 8.0
        const val DEFAULT_MIN_BAR_SPACING = 1.0
        const val DEFAULT_MAX_BAR_SPACING = 50.0

        /** 末根右侧留白槽位数（可配置）。 */
        const val DEFAULT_RIGHT_OFFSET_SLOTS = 50

        /** 左侧超滚（橡皮筋阻尼由手势层实现，这里只限定状态范围）。 */
        const val DEFAULT_OVERSCROLL_SLOTS = 0.0

        /** 最近时间索引；等距取较早一根，范围外取端点，空数据返回 null。 */
        fun indexOfTimestamp(timestamp: Long, candles: List<Candle>): Int? {
            if (candles.isEmpty()) return null
            var lo = 0
            var hi = candles.size - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val ts = candles[mid].timestamp
                when {
                    ts == timestamp -> return mid
                    ts < timestamp -> {
                        lo = mid + 1
                    }
                    else -> hi = mid - 1
                }
            }
            if (hi < 0) return 0
            if (lo >= candles.size) return candles.lastIndex
            return if (timestamp.toDouble() - candles[hi].timestamp <= candles[lo].timestamp.toDouble() - timestamp) hi else lo
        }

        /** 连续画笔保留槽内精度，范围外按首尾间隔外推。 */
        fun floatIndexToTimestamp(index: Double, candles: List<Candle>): Long? {
            if (candles.isEmpty() || !index.isFinite()) return null
            if (candles.size == 1) return candles[0].timestamp
            val left = kotlin.math.floor(index).toInt().coerceIn(0, candles.lastIndex - 1)
            val start = candles[left].timestamp
            return start + ((candles[left + 1].timestamp - start).toDouble() * (index - left)).roundToLong()
        }

        /** 连续时间锚点的逆映射，补历史或缩放后仍保留原来的槽内位置。 */
        fun timestampToFloatIndex(timestamp: Long, candles: List<Candle>): Double? {
            val nearest = indexOfTimestamp(timestamp, candles) ?: return null
            if (candles.size == 1) return 0.0
            val left = (if (timestamp < candles[nearest].timestamp) nearest - 1 else nearest).coerceIn(0, candles.lastIndex - 1)
            return left + (timestamp - candles[left].timestamp).toDouble() / (candles[left + 1].timestamp - candles[left].timestamp)
        }
    }
}
