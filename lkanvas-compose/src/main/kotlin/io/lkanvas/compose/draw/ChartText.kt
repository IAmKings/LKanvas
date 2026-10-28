package io.lkanvas.compose.draw

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat
import java.util.TimeZone

/**
 * 文本绘制辅助：持有 TextMeasurer 与格式化规则（PRD R-11 基础部分）。
 * 绘制期无状态分配以外的无界缓存；formatter 复用 DecimalFormat。
 */
class ChartText(
    internal val measurer: TextMeasurer,
    internal val textColor: Color,
    val textSizePx: Float,
    /** 价格精度（小数位）。 */
    val pricePrecision: Int = 2,
    /** 成交量精度。 */
    val volumePrecision: Int = 2,
) : io.lkanvas.compose.draw.TooltipRenderer.NumericText {
    private val priceFormat = createFormat(pricePrecision)
    private val volumeFormat = createFormat(volumePrecision)
    private val compactFormat = DecimalFormat("#,###.##")

    val textHeight: Float = textSizePx * 1.1f
    val tagHeight: Float = textHeight + 6f
    val tagWidth: Float = textSizePx * (pricePrecision + 4)

    override fun price(v: Double): String = if (v.isFinite()) priceFormat.format(v) else "—"
    override fun volume(v: Double): String = when {
        !v.isFinite() -> "—"
        v >= 1e8 -> String.format("%.2f亿", v / 1e8)
        v >= 1e4 -> String.format("%.2f万", v / 1e4)
        else -> volumeFormat.format(v)
    }

    private val widthCache = HashMap<String, Float>()

    /** 文本宽度测量（带缓存；仅用于图例/轴标签排布，键空间有界）。 */
    fun measureWidth(s: String): Float = widthCache.getOrPut(s) {
        measurer.measure(
            s,
            style = TextStyle(fontSize = textSizePx.sp),
        ).size.width.toFloat()
    }

    private fun createFormat(precision: Int): DecimalFormat =
        DecimalFormat().apply {
            maximumFractionDigits = precision
            minimumFractionDigits = precision
            isGroupingUsed = false
        }

    companion object {
        /** 时间标签（Alpha 基础规则：按 candle 间隔选择 日/时分 格式；UTC 存储，显示本地时区）。 */
        fun timeLabel(timestamp: Long, intervalMs: Long, tz: TimeZone = TimeZone.getDefault()): String {
            val cal = java.util.Calendar.getInstance(tz)
            cal.timeInMillis = timestamp
            val dayFmt = { "%02d-%02d".format(cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH)) }
            val timeFmt = { "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)) }
            return if (intervalMs >= 24 * 3600_000L) dayFmt() else timeFmt()
        }

        /** 跨日显示日期、同日显示时间（时间轴）。 */
        fun timeAxisLabel(timestamp: Long, prevTimestamp: Long?, intervalMs: Long, tz: TimeZone = TimeZone.getDefault()): String {
            val cal = java.util.Calendar.getInstance(tz)
            cal.timeInMillis = timestamp
            val sameDay = prevTimestamp != null && run {
                val p = java.util.Calendar.getInstance(tz)
                p.timeInMillis = prevTimestamp
                p.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR) &&
                    p.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR)
            }
            val day = "%02d-%02d".format(cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
            val time = "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
            return when {
                intervalMs >= 24 * 3600_000L -> "%d/%s".format(cal.get(java.util.Calendar.YEAR) % 100, day)
                sameDay -> time
                else -> day
            }
        }
    }
}

/** 顶层绘制扩展：文本/标签（DrawScope + ChartText 双接收）。 */

fun DrawScope.drawText(text: ChartText, s: String, topLeft: Offset, color: Color = Color.Unspecified) {
    drawText(
        text.measurer,
        s,
        topLeft = topLeft,
        style = TextStyle(
            fontSize = text.textSizePx.sp,
            color = if (color == Color.Unspecified) text.textColor else color,
        ),
    )
}

fun DrawScope.drawText(text: ChartText, v: Double, topLeft: Offset, color: Color = Color.Unspecified) =
    drawText(text, text.price(v), topLeft, color)

/** 右侧末价/轴值标签底色块 + 白字。 */
fun DrawScope.drawTag(text: ChartText, value: Double, cy: Float, rightEdge: Float, bg: Color) {
    drawRect(
        bg,
        topLeft = Offset(rightEdge + 2f, cy - text.tagHeight / 2f),
        size = androidx.compose.ui.geometry.Size(text.tagWidth, text.tagHeight),
    )
    drawText(text, text.price(value), Offset(rightEdge + 6f, cy - text.textHeight / 2f), Color.White)
}
