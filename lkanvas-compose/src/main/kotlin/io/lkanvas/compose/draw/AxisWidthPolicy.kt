package io.lkanvas.compose.draw

/**
 * 右轴自适应宽度策略（上游 `component/YAxis.ts` `getAutoSize`/`_settleWidth` 的
 * 平台层移植，structure-map §4 平台替代映射；issue #1）。
 *
 * 上游语义：轴槽宽 = marginStart + 实测最大刻度文本宽 + marginEnd，且
 * [settle] 立即扩宽、静默 [SETTLE_MS] 后才收窄、按 [GRID_PX] 网格取整——
 * 防止数字噪声引起列宽抖动，并打破"槽宽→可见区→刻度文本→槽宽"的布局回环。
 * 纯 Kotlin 无 Android 依赖，时钟可注入（JVM 测试）。
 */
class AxisWidthPolicy(
    private val clock: () -> Long = { android.os.SystemClock.uptimeMillis() },
) {
    private var settledWidth = 0f
    private var settledWidthLastNeededAt = 0L

    /**
     * 刻度文本组所需槽宽：6px 起始边距（与轴标签绘制偏移 `axisRight + 6f` 同源）
     * + 最宽文本。空文本组返回 0（调用方以配置 gutter 为下限）。
     */
    fun neededWidth(texts: List<String>, measure: (String) -> Float): Float {
        var maxW = 0f
        for (t in texts) {
            if (t.isEmpty()) continue
            val w = measure(t)
            if (w > maxW) maxW = w
        }
        return if (maxW <= 0f) 0f else LABEL_MARGIN_START + maxW
    }

    /**
     * 上游 `_settleWidth`：立即扩（标签必须放得下），收窄仅在更宽文本消失
     * [SETTLE_MS] 静默后发生，宽度按 [GRID_PX] 向上取整。
     */
    fun settle(measured: Float): Float {
        if (!measured.isFinite() || measured <= 0f) return measured
        val wanted = kotlin.math.ceil(measured / GRID_PX) * GRID_PX
        val now = clock()
        val held = settledWidth
        if (held <= 0f || wanted > held) {
            settledWidth = wanted
            settledWidthLastNeededAt = now
            return wanted
        }
        // 时间戳记录"宽文本最后被需要"的时刻：收窄等待从宽文本离开起算，
        // 宽文本回归则重新起算。
        if (wanted == held) {
            settledWidthLastNeededAt = now
            return held
        }
        if (now - settledWidthLastNeededAt >= SETTLE_MS) {
            settledWidth = wanted
            settledWidthLastNeededAt = now
            return wanted
        }
        return held
    }

    companion object {
        /** 轴标签起始边距（PaneRenderers `axisRight + 6f` 同源）。 */
        const val LABEL_MARGIN_START = 6f
        /** 末价/十字线 tag 底色块起始边距（ChartText.drawTag `rightEdge + 2f` 同源）。 */
        const val TAG_MARGIN_START = 2f
        /** 宽度网格（上游 Y_AXIS_WIDTH_GRID_PX）。 */
        const val GRID_PX = 4f
        /** 收窄静默窗（上游 Y_AXIS_WIDTH_SETTLE_MS）。 */
        const val SETTLE_MS = 400L

        /**
         * 上游 `widthAnchorValue`：当前量级十位锚点——量级顶 `9.99 * 10^floor(log10(magnitude))`，
         * 任一端为负取负。十字线/tag 在该量级内可能产生的最坏文本估值（宽度
         * 只取决于形状/位数，不随具体数字漂移）。
         */
        fun widthAnchorValue(displayFrom: Double, displayTo: Double): Double {
            val magnitude = maxOf(kotlin.math.abs(displayFrom), kotlin.math.abs(displayTo))
            if (!magnitude.isFinite() || magnitude == 0.0) return displayTo
            val anchored = 9.99 * Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(magnitude)))
            return if (displayFrom < 0 || displayTo < 0) -anchored else anchored
        }
    }
}
