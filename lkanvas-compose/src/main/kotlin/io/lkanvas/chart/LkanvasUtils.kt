package io.lkanvas.chart

import io.lkanvas.core.figure.FigureHit
import io.lkanvas.core.figure.P
import kotlin.math.abs

/**
 * 上游 `utils` 对象的 Kotlin 对齐（index.ts utils 22 项，批次 4/5 余量收口）。
 * 类型判断 7 项由 Kotlin 类型系统天然承担（保留占位以对齐 API 面）。
 */
object LkanvasUtils {

    // ---------- 类型判断（上游语义；Kotlin 中主要对齐 API 面） ----------

    fun isString(v: Any?): Boolean = v is String
    fun isNumber(v: Any?): Boolean = v is Number
    fun isBoolean(v: Any?): Boolean = v is Boolean
    fun isObject(v: Any?): Boolean = v != null && !isString(v) && !isNumber(v) && !isBoolean(v) &&
        !isArray(v) && !isFunction(v)
    fun isArray(v: Any?): Boolean = v is List<*>
    fun isFunction(v: Any?): Boolean = v is Function<*>
    fun isValid(v: Any?): Boolean = when (v) {
        null -> false
        is Double -> v.isFinite()
        is Float -> v.isFinite()
        is String -> v.isNotEmpty() && v != "undefined" && v != "null"
        else -> true
    }

    // ---------- 合并/克隆 ----------

    /** 上游 `merge`：浅层属性覆盖（Styles 深合并见 StylesMerge）。 */
    @Suppress("UNCHECKED_CAST")
    fun merge(base: MutableMap<String, Any?>, patch: Map<String, Any?>): MutableMap<String, Any?> {
        val out = base.toMutableMap()
        patch.forEach { (k, v) -> out[k] = v }
        return out
    }

    /** 上游 `clone`：浅拷贝（不可变集合天然拷贝）。 */
    fun clone(v: Any?): Any? = when (v) {
        is List<*> -> v.toList()
        is Map<*, *> -> v.toMap()
        else -> v
    }

    // ---------- 格式化 ----------

    fun formatValue(value: Any?, defaultValue: Any? = "—"): Any? = if (isValid(value)) value else defaultValue

    /** JS Number→string 等价：整值无 ".0" 尾缀；科学计数展开为普通表示（JS 仅 <1e-6 用指数）。 */
    private fun jsNumString(v: Double): String {
        if (v == v.toLong().toDouble() && abs(v) < 1e15) return v.toLong().toString()
        val s = v.toString()
        return if (s.contains('E')) v.toBigDecimal().toPlainString() else s
    }

    /** 上游 formatPrecision：toFixed 四舍五入（默认 2 位）。 */
    fun formatPrecision(value: Double, precision: Int = 2): String =
        String.format(java.util.Locale.US, "%.${precision.coerceAtLeast(0)}f", value)

    /** 上游 formatBigNumber：K/M/B 分档（>=1e3/1e6/1e9），toFixed(3) 后去尾零。 */
    fun formatBigNumber(value: Double): String {
        val a = abs(value)
        val sign = if (value < 0) "-" else ""
        fun scaled(div: Double): String =
            String.format(java.util.Locale.US, "%.3f", a / div).trimEnd('0').trimEnd('.')
        return when {
            a >= 1e9 -> "$sign${scaled(1e9)}B"
            a >= 1e6 -> "$sign${scaled(1e6)}M"
            a >= 1e3 -> "$sign${scaled(1e3)}K"
            else -> jsNumString(value)
        }
    }

    fun formatDate(timestamp: Long, template: String = "yyyy-MM-dd HH:mm"): String {
        val fmt = template
            .replace("HH", "%1$02d").replace("mm", "%2$02d")
            .replace("ss", "%3$02d")
            .replace("yyyy", "%4$04d").replace("MM", "%5$02d").replace("dd", "%6$02d")
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = timestamp
        return fmt.format(
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND),
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
        )
    }

    /** 上游 formatThousands：整数部分千分位分组，小数部分原样。 */
    fun formatThousands(value: Double, sign: String = ","): String {
        val vl = jsNumString(value)
        if (sign.isEmpty()) return vl
        val dot = vl.indexOf('.')
        val intPart = if (dot >= 0) vl.substring(0, dot) else vl
        val decPart = if (dot >= 0) vl.substring(dot) else ""
        val grouped = intPart.reversed().chunked(3).joinToString(sign).reversed()
        return grouped + decPart
    }

    /**
     * 上游 formatFoldDecimal：小数部分前导连续 0 达 [threshold] 时折叠为
     * `0{count}` 标记（显示端按标记缩排）；不命中原样返回。
     */
    fun formatFoldDecimal(value: Double, threshold: Int = 3): String {
        val vl = jsNumString(value)
        val dot = vl.indexOf('.')
        if (dot < 0) return vl
        val dec = vl.substring(dot + 1)
        val m = Regex("^0{$threshold,}([1-9][0-9]*)$").find(dec) ?: return vl
        val count = m.value.takeWhile { it == '0' }.length
        return "${vl.substring(0, dot)}.0{$count}${m.groupValues[1]}"
    }

    fun calcTextWidth(text: String, fontSizePx: Double, charWidthRatio: Double = 0.6): Double =
        text.length * fontSizePx * charWidthRatio

    // ---------- 线性数学 ----------

    /** 上游 getLinearSlopeIntercept：两点斜率与截距（垂直线返回 null）。 */
    fun getLinearSlopeIntercept(a: P, b: P): Pair<Double, Double>? {
        if (a.x == b.x) return null
        val k = (b.y - a.y) / (b.x - a.x)
        return k to (a.y - k * a.x)
    }

    fun getLinearYFromSlopeIntercept(x: Double, k: Double, b: Double): Double = k * x + b

    fun getLinearYFromCoordinates(x: Double, a: P, b: P): Double? {
        val (k, b) = getLinearSlopeIntercept(a, b) ?: return null
        return getLinearYFromSlopeIntercept(x, k, b)
    }

    // ---------- 图形命中（同名包装，委托 FigureHit） ----------

    fun checkCoordinateOnArc(centerX: Double, centerY: Double, radius: Double, startDeg: Double, endDeg: Double, x: Double, y: Double): Boolean =
        FigureHit.hit(io.lkanvas.core.figure.Figure.Arc(centerX, centerY, radius, startDeg, endDeg), x, y)

    fun checkCoordinateOnCircle(centerX: Double, centerY: Double, radius: Double, x: Double, y: Double): Boolean =
        FigureHit.hit(io.lkanvas.core.figure.Figure.Circle(centerX, centerY, radius), x, y)

    fun checkCoordinateOnLine(x0: Double, y0: Double, x1: Double, y1: Double, x: Double, y: Double): Boolean =
        FigureHit.hit(io.lkanvas.core.figure.Figure.Line(x0, y0, x1, y1), x, y)

    fun checkCoordinateOnPolygon(points: List<P>, x: Double, y: Double): Boolean =
        FigureHit.hit(io.lkanvas.core.figure.Figure.Polygon(points), x, y)

    fun checkCoordinateOnRect(left: Double, top: Double, right: Double, bottom: Double, x: Double, y: Double): Boolean =
        FigureHit.hit(io.lkanvas.core.figure.Figure.Rect(left, top, right, bottom), x, y)

    fun checkCoordinateOnText(x: Double, y: Double, content: String, px: Double, py: Double): Boolean =
        FigureHit.hit(io.lkanvas.core.figure.Figure.Text(x, y, content), px, py)

    private fun abs(v: Double): Double = kotlin.math.abs(v)
}
