package io.lkanvas.chart

import io.lkanvas.compose.draw.AxisWidthPolicy
import org.junit.Assert.*
import org.junit.Test

/**
 * 右轴自适应宽度策略（issue #1 / 上游 `yAxis.size='auto'` getAutoSize/_settleWidth
 * 平台移植）JVM 行为测试：neededWidth 下限语义、settle 防抖状态机（立即扩/静默收/
 * 网格取整/时钟注入）与 widthAnchorValue 十位锚点。
 */
class AxisWidthPolicyTest {
    private fun policy(clock: LongArray) = AxisWidthPolicy { clock[0] }

    // ---------- neededWidth ----------

    @Test fun neededWidthTakesWidestTextPlusStartMargin() {
        val p = AxisWidthPolicy()
        val texts = listOf("0.00", "1200", "-1234.56")
        val needed = p.neededWidth(texts) { it.length.toFloat() }
        assertEquals(AxisWidthPolicy.LABEL_MARGIN_START + "-1234.56".length, needed, 0.001f)
    }

    @Test fun emptyTextsNeedNoWidth() {
        val p = AxisWidthPolicy()
        assertEquals(0f, p.neededWidth(emptyList()) { it.length.toFloat() }, 0.001f)
        assertEquals(0f, p.neededWidth(listOf("", "")) { it.length.toFloat() }, 0.001f)
    }

    /**
     * issue #1 回归：VOL 量级 ≥ 1e7 的刻度文本（千分位 + 两位小数）所需宽度
     * 必须显著大于宿主固定 46dp 槽（密度 3 = 138px），槽宽按 max(下限, 实测) 扩展。
     */
    @Test fun largeMagnitudeVolumeTickTextDrivesGutterBeyondConfiguredLowerBound() {
        val p = AxisWidthPolicy()
        val configGutterPx = 138f // 46dp @ density 3（issue #1 宿主配置）
        // "10,000,000.00"：假测量按字符宽度 16.5px（10sp @3x 轴字号量级）
        val measure = { s: String -> s.length * 16.5f }
        val texts = listOf("0.00", "4,000,000.00", "8,000,000.00", "12,000,000.00")
        val needed = p.neededWidth(texts, measure)
        val effective = maxOf(configGutterPx, p.settle(needed))
        assertTrue("自适应槽宽应超过配置下限：$effective", effective > configGutterPx)
        // 最宽文本 + 起始边距必须放得下（网格取整只增不减）
        val widest = "12,000,000.00".length * 16.5f + AxisWidthPolicy.LABEL_MARGIN_START
        assertTrue(effective >= widest)
    }

    // ---------- settle（上游 _settleWidth 状态机） ----------

    @Test fun firstSettleGrowsImmediatelyToGridRoundedWidth() {
        val clock = longArrayOf(1000L)
        val p = policy(clock)
        // 181px → ceil 到 4px 网格 = 184
        assertEquals(184f, p.settle(181f), 0.001f)
    }

    @Test fun widerLabelGrowsImmediatelyEvenWithoutQuietPeriod() {
        val clock = longArrayOf(1000L)
        val p = policy(clock)
        p.settle(100f)
        clock[0] = 1010L
        assertEquals(200f, p.settle(200f), 0.001f) // 标签必须立刻放得下
    }

    @Test fun narrowerLabelIsHeldUntilSettleQuietWindowElapses() {
        val clock = longArrayOf(1000L)
        val p = policy(clock)
        p.settle(200f)
        clock[0] = 1000L + AxisWidthPolicy.SETTLE_MS - 1
        assertEquals(200f, p.settle(100f), 0.001f) // 静默未满 → 保持
        clock[0] = 1000L + AxisWidthPolicy.SETTLE_MS
        assertEquals(100f, p.settle(100f), 0.001f) // 静默满 → 收窄
    }

    @Test fun quietWindowRestartsWhenWideLabelReturns() {
        val clock = longArrayOf(1000L)
        val p = policy(clock)
        p.settle(200f)
        clock[0] = 1300L
        p.settle(100f) // 开始静默计时（未满）
        clock[0] = 1500L
        p.settle(200f) // 宽文本回归 → 重新起算
        clock[0] = 1500L + AxisWidthPolicy.SETTLE_MS - 1
        assertEquals(200f, p.settle(100f), 0.001f)
        clock[0] = 1500L + AxisWidthPolicy.SETTLE_MS
        assertEquals(100f, p.settle(100f), 0.001f)
    }

    @Test fun heldWidthRefreshesQuietStampWhileLabelNeedsIt() {
        val clock = longArrayOf(1000L)
        val p = policy(clock)
        p.settle(200f)
        // 宽文本持续被需要（wanted == held）→ 时间戳持续刷新，永不收窄
        for (t in 1..10) {
            clock[0] = 1000L + t * AxisWidthPolicy.SETTLE_MS
            assertEquals(200f, p.settle(200f), 0.001f)
        }
    }

    @Test fun nonPositiveOrNonFiniteMeasurementIsIgnored() {
        val clock = longArrayOf(1000L)
        val p = policy(clock)
        assertEquals(0f, p.settle(0f), 0.001f)
        assertEquals(-1f, p.settle(-1f), 0.001f)
        assertEquals(Float.NaN, p.settle(Float.NaN), 0.001f)
        // 状态未建立：后续正常测量仍立即扩
        clock[0] = 2000L
        assertEquals(104f, p.settle(102f), 0.001f)
    }

    // ---------- widthAnchorValue（上游移植） ----------

    @Test fun anchorTopsTheCurrentDecadeOfTheRange() {
        assertEquals(9990000.0, AxisWidthPolicy.widthAnchorValue(0.0, 8000000.0), 1e-6) // 量级 1e7 内锚 9.99e6
        assertEquals(9990.0, AxisWidthPolicy.widthAnchorValue(120.0, 9500.0), 1e-6) // 取量级更大端
        assertEquals(9.99, AxisWidthPolicy.widthAnchorValue(0.0, 8.5), 1e-6)
    }

    @Test fun negativeRangeAnchorsNegative() {
        assertEquals(-9990000.0, AxisWidthPolicy.widthAnchorValue(-8000000.0, 0.0), 1e-6)
        assertEquals(-9990000.0, AxisWidthPolicy.widthAnchorValue(-8000000.0, 120.0), 1e-6)
    }

    @Test fun zeroOrNonFiniteMagnitudeFallsBackToDisplayTo() {
        assertEquals(0.0, AxisWidthPolicy.widthAnchorValue(0.0, 0.0), 1e-6)
        // 上游 Math.max(NaN, x)=NaN → 非有限量级回退 displayTo
        assertEquals(3.0, AxisWidthPolicy.widthAnchorValue(Double.NaN, 3.0), 1e-6)
    }
}
