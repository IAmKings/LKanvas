package io.lkanvas.compose.draw

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.geometry.Offset
import io.lkanvas.compose.state.ChartState
import io.lkanvas.core.common.utils.Format
import java.text.DecimalFormat
import java.util.TimeZone

/**
 * 文本绘制辅助：持有 TextMeasurer 与格式化规则（PRD R-11 基础部分）。
 * 绘制期无状态分配以外的无界缓存；formatter 复用 DecimalFormat。
 *
 * SLA-011 格式链接线：price/volume 按 [ChartState.FormatConfig] 消费
 * thousandsSeparator/decimalFold/formatter，应用顺序与上游一致：
 * `decimalFold(thousandsSeparator(formatPrecision(v, precision)))`。
 */
class ChartText(
    internal val measurer: TextMeasurer,
    internal val textColor: Color,
    val textSizePx: Float,
    /** 价格精度（小数位）。 */
    val pricePrecision: Int = 2,
    /** 成交量精度。 */
    val volumePrecision: Int = 2,
    /** 格式配置（千分位/小数折叠/自定义钩子）；null 用内置默认。 */
    val format: ChartState.FormatConfig? = null,
) : io.lkanvas.compose.draw.TooltipRenderer.NumericText {
    // Pixel measurements must not inherit a screen TextMeasurer density during PNG export.
    private val density = androidx.compose.ui.unit.Density(1f)
    // 延迟构造格式化器，保留创建 ChartText 时的数字区域设置。
    private val formatLocale = if (android.os.Build.VERSION.SDK_INT >= 24) {
        java.util.Locale.getDefault(java.util.Locale.Category.FORMAT)
    } else java.util.Locale.getDefault()
    private val volumeFormat by lazy { createFormat(volumePrecision) }

    val textHeight: Float by lazy {
        layout("Hg国").size.height.toFloat()
    }
    val tagHeight: Float get() = textHeight + 6f
    val tagWidth: Float = textSizePx * (pricePrecision + 4)

    override fun price(v: Double): String {
        if (!v.isFinite()) return "—"
        // 上游顺序：decimalFold(thousandsSeparator(formatPrecision(v, precision)))
        val precisioned = Format.formatPrecision(v, pricePrecision)
        val grouped = Format.formatThousands(precisioned, format?.thousandsSign ?: "")
        val fold = format?.decimalFoldThreshold
        return if (fold != null) Format.formatFoldDecimal(grouped, fold) else grouped
    }

    override fun volume(v: Double): String {
        if (!v.isFinite()) return "—"
        format?.formatBigNumber?.let { return it(v) }
        return when {
            v >= 1e8 -> String.format("%.2f亿", v / 1e8)
            v >= 1e4 -> String.format("%.2f万", v / 1e4)
            else -> volumeFormat.format(v)
        }
    }

    internal var markerExclusions: List<androidx.compose.ui.geometry.Rect> = emptyList()

    private val layoutCache = HashMap<String, androidx.compose.ui.text.TextLayoutResult>()
    private val pixelStyle = pixelTextStyle(textSizePx, density, textColor)
    private var layoutLocales = currentLayoutLocales()

    /** 测宽与绘字复用同一布局；保留有界缓存及字体、区域设置失效规则。 */
    fun measureWidth(s: String): Float = layout(s).size.width.toFloat()

    internal fun layout(s: String): androidx.compose.ui.text.TextLayoutResult {
        val locales = currentLayoutLocales()
        if (locales != layoutLocales) {
            layoutCache.clear()
            layoutLocales = locales
        }
        val cached = layoutCache[s]
        if (cached != null && !cached.multiParagraph.intrinsics.hasStaleResolvedFonts) return cached
        return measurer.measure(s, style = pixelStyle, density = density).also {
            if (layoutCache.size >= 256) layoutCache.clear()
            layoutCache[s] = it
        }
    }

    private fun currentLayoutLocales(): Any = if (android.os.Build.VERSION.SDK_INT >= 24) {
        android.os.LocaleList.getDefault()
    } else java.util.Locale.getDefault()

    private fun createFormat(precision: Int): DecimalFormat =
        (java.text.NumberFormat.getNumberInstance(formatLocale) as DecimalFormat).apply {
            maximumFractionDigits = precision
            minimumFractionDigits = precision
            isGroupingUsed = false
        }

    companion object {
        internal fun pixelTextStyle(sizePx: Float, density: androidx.compose.ui.unit.Density, color: Color) =
            TextStyle(fontSize = with(density) { sizePx.toSp() }, color = color)

        /** 时间标签（Alpha 基础规则：按 candle 间隔选择 日/时分 格式；UTC 存储，显示本地时区）。 */
        // 缓存全部格式化上下文，首个可见位置/间隔/区域/时区变化不能沿用旧标签。
        private const val LABEL_CACHE_MAX = 4096
        private data class LabelKey(
            val timestamp: Long,
            val previousTimestamp: Long?,
            val intervalMs: Long,
            val timeZoneId: String,
            val offset: Int,
            val previousOffset: Int,
            val locale: java.util.Locale,
            val formatLocale: java.util.Locale,
            val axis: Boolean,
        )
        private val labelCache = HashMap<LabelKey, String>()

        private fun labelKey(timestamp: Long, previous: Long?, interval: Long, zone: TimeZone, axis: Boolean) = LabelKey(
            timestamp, previous, interval, zone.id, zone.getOffset(timestamp),
            if (previous != null) zone.getOffset(previous) else 0,
            java.util.Locale.getDefault(),
            if (android.os.Build.VERSION.SDK_INT >= 24) java.util.Locale.getDefault(java.util.Locale.Category.FORMAT) else java.util.Locale.getDefault(),
            axis,
        )

        private fun cachedLabel(key: LabelKey, compute: () -> String): String {
            val hit = labelCache[key]
            if (hit != null) return hit
            val v = compute()
            if (labelCache.size >= LABEL_CACHE_MAX) labelCache.clear()
            labelCache[key] = v
            return v
        }

        fun timeLabel(timestamp: Long, intervalMs: Long, tz: TimeZone = TimeZone.getDefault()): String = cachedLabel(labelKey(timestamp, null, intervalMs, tz, false)) {
            val cal = java.util.Calendar.getInstance(tz)
            cal.timeInMillis = timestamp
            val dayFmt = { "%02d-%02d".format(cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH)) }
            val timeFmt = { "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)) }
            if (intervalMs >= 24 * 3600_000L) dayFmt() else timeFmt()
        }

        /** 跨日显示日期、同日显示时间（时间轴）。 */
        fun timeAxisLabel(timestamp: Long, prevTimestamp: Long?, intervalMs: Long, tz: TimeZone = TimeZone.getDefault()): String = cachedLabel(labelKey(timestamp, prevTimestamp, intervalMs, tz, true)) {
            val cal = java.util.Calendar.getInstance(tz)
            cal.timeInMillis = timestamp
            val sameDay = intervalMs < 24 * 3600_000L && prevTimestamp != null && run {
                val p = java.util.Calendar.getInstance(tz)
                p.timeInMillis = prevTimestamp
                p.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR) &&
                    p.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR)
            }
            val dayFmt = { "%02d-%02d".format(cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH)) }
            val timeFmt = { "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)) }
            when {
                intervalMs >= 24 * 3600_000L -> "%d/%s".format(cal.get(java.util.Calendar.YEAR) % 100, dayFmt())
                sameDay -> timeFmt()
                else -> dayFmt()
            }
        }
    }
}

/** 顶层绘制扩展：文本/标签（DrawScope + ChartText 双接收）。 */

fun DrawScope.drawText(text: ChartText, s: String, topLeft: Offset, color: Color = Color.Unspecified) {
    val actualColor = if (color == Color.Unspecified) text.textColor else color
    val layout = text.layout(s)
    if (layout.size.width <= size.width - topLeft.x && layout.size.height <= size.height - topLeft.y) {
        drawText(layout, topLeft = topLeft, color = actualColor)
    } else {
        drawText(text.measurer, s, topLeft = topLeft, style = ChartText.pixelTextStyle(text.textSizePx, this, actualColor))
    }
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
