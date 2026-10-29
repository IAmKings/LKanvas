package io.lkanvas.core.common.utils

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * 上游 `common/utils/format.ts` 的 Kotlin 镜像（本域格式化唯一权威实现；
 * LkanvasUtils 同名函数委托至此）。
 *
 * 上游视图应用顺序：`decimalFold(thousandsSeparator(formatPrecision(v, precision)))`
 * （见 CandleHighLowPriceView.ts:42），本文件函数保持可组合的纯函数形态。
 */
object Format {

    /** JS Number→string 等价：整值无 ".0" 尾缀；科学计数展开为普通表示（JS 仅 <1e-6 用指数）。 */
    fun jsNumString(v: Double): String {
        if (v == v.toLong().toDouble() && abs(v) < 1e15) return v.toLong().toString()
        val s = v.toString()
        return if (s.contains('E')) v.toBigDecimal().toPlainString() else s
    }

    /** 上游 formatPrecision：toFixed 四舍五入（默认 2 位）。 */
    fun formatPrecision(value: Double, precision: Int = 2): String =
        String.format(Locale.US, "%.${precision.coerceAtLeast(0)}f", value)

    /** 上游 formatBigNumber：K/M/B 分档（>=1e3/1e6/1e9），toFixed(3) 后去尾零。 */
    fun formatBigNumber(value: Double): String {
        val a = abs(value)
        val sign = if (value < 0) "-" else ""
        fun scaled(div: Double): String =
            String.format(Locale.US, "%.3f", a / div).trimEnd('0').trimEnd('.')
        return when {
            a >= 1e9 -> "$sign${scaled(1e9)}B"
            a >= 1e6 -> "$sign${scaled(1e6)}M"
            a >= 1e3 -> "$sign${scaled(1e3)}K"
            else -> jsNumString(value)
        }
    }

    /** 上游 formatThousands：整数部分千分位分组，小数部分原样（与上游一致按字符串处理）。 */
    fun formatThousands(value: String, sign: String): String {
        if (sign.isEmpty()) return value
        return if (value.contains('.')) {
            val arr = value.split('.', limit = 2)
            "${groupInt(arr[0], sign)}.${arr[1]}"
        } else {
            groupInt(value, sign)
        }
    }

    fun formatThousands(value: Double, sign: String = ","): String = formatThousands(jsNumString(value), sign)

    private fun groupInt(s: String, sign: String): String =
        s.reversed().chunked(3).joinToString(sign).reversed()

    /**
     * 上游 formatFoldDecimal：小数部分前导连续 0 达 [threshold] 时折叠为
     * `0{count}` 标记（显示端按标记缩排）；不命中原样返回。
     */
    fun formatFoldDecimal(value: String, threshold: Int = 3): String {
        val reg = Regex("\\.0{$threshold,}[1-9][0-9]*$")
        if (!reg.containsMatchIn(value)) return value
        val result = value.split('.').toMutableList()
        val lastIndex = result.size - 1
        val v = result[lastIndex]
        val count = v.takeWhile { it == '0' }.length
        result[lastIndex] = v.replaceFirst(Regex("0+"), "0{$count}")
        return result.joinToString(".")
    }

    fun formatFoldDecimal(value: Double, threshold: Int = 3): String = formatFoldDecimal(jsNumString(value), threshold)

    /**
     * 上游 formatTimestampByTemplate：按模板 `YYYY/MM/DD/HH/mm/ss` 格式化
     * （[timeZone] 消费 Options.timezone；null 回退系统时区）。
     */
    fun formatTimestampByTemplate(
        timestamp: Long,
        template: String,
        timeZone: String? = null,
    ): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone(timeZone ?: TimeZone.getDefault().id))
        cal.timeInMillis = timestamp
        val YYYY = "%04d".format(cal.get(Calendar.YEAR))
        val MM = "%02d".format(cal.get(Calendar.MONTH) + 1)
        val DD = "%02d".format(cal.get(Calendar.DAY_OF_MONTH))
        val HH = "%02d".format(cal.get(Calendar.HOUR_OF_DAY))
        val mm = "%02d".format(cal.get(Calendar.MINUTE))
        val ss = "%02d".format(cal.get(Calendar.SECOND))
        return template
            .replace("YYYY", YYYY).replace("MM", MM).replace("DD", DD)
            .replace("HH", HH).replace("mm", mm).replace("ss", ss)
    }

    /**
     * 上游 formatValue：路径键取值（支持 `a.b` 复合键）；无效值回退默认「—」。
     * 本域数据为类型化字段，复合键经 Map/反射解析；标量直接判定。
     */
    fun formatValue(data: Any?, key: String, defaultValue: Any? = "—"): Any? {
        val value: Any? = when {
            data == null -> null
            key.isEmpty() -> data
            data is Map<*, *> -> if (key.contains('.') || key.contains('[')) {
                key.split('.', '[', ']')
                    .filter { it.isNotBlank() }
                    .fold(data as Any?) { acc, k -> (acc as? Map<*, *>)?.get(k) }
            } else {
                data[key]
            }
            else -> null
        }
        return if (isValid(value)) value else defaultValue
    }

    /** 上游 isValid：非 null 即有效（数值需有限）。 */
    fun isValid(v: Any?): Boolean = when (v) {
        null -> false
        is Double -> v.isFinite()
        is Float -> v.isFinite()
        else -> true
    }
}
