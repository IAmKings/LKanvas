package io.lkanvas.chart

import io.lkanvas.core.common.utils.Format
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * utils 对齐测试（IDX-005/013~018/021/022/027~031）：
 * 类型判断/合并克隆/格式化五件套/线性数学/命中包装。
 */
class LkanvasUtilsTest {

    // ---------- 类型判断（isValid 语义为核心） ----------

    @Test
    fun isValid_matchesUpstreamSemantics() {
        assertTrue(LkanvasUtils.isValid(1.0))
        assertTrue(LkanvasUtils.isValid("a"))
        assertTrue(LkanvasUtils.isValid(0))
        assertTrue(LkanvasUtils.isValid(false)) // 上游 isValid(false) === true（0/false 有效）
        assertFalse(LkanvasUtils.isValid(null))
        assertFalse(LkanvasUtils.isValid(Double.NaN))
        assertFalse(LkanvasUtils.isValid(Double.POSITIVE_INFINITY))
        assertFalse(LkanvasUtils.isValid(""))
        assertFalse(LkanvasUtils.isValid("undefined"))
        assertFalse(LkanvasUtils.isValid("null"))
    }

    @Test
    fun typeChecks() {
        assertTrue(LkanvasUtils.isString("x"))
        assertTrue(LkanvasUtils.isNumber(1.5))
        assertTrue(LkanvasUtils.isBoolean(true))
        assertTrue(LkanvasUtils.isArray(listOf(1)))
        assertTrue(LkanvasUtils.isFunction { })
        assertTrue(LkanvasUtils.isObject(mapOf<String, Any>()))
        assertFalse(LkanvasUtils.isObject(null))
        assertFalse(LkanvasUtils.isArray("x"))
    }

    // ---------- merge / clone ----------

    @Test
    fun merge_overridesPatchKeysKeepingBase() {
        val base = mutableMapOf<String, Any?>("a" to 1, "b" to 2)
        val out = LkanvasUtils.merge(base, mapOf("b" to 20, "c" to 30))
        assertEquals(1, out["a"])
        assertEquals(20, out["b"])
        assertEquals(30, out["c"])
        assertEquals(3, out.size)
    }

    @Test
    fun clone_copiesCollections() {
        val list = listOf(1, 2, 3)
        val clonedList = LkanvasUtils.clone(list)
        assertTrue(clonedList is List<*> && clonedList == list)
        val map = mapOf("a" to 1)
        val clonedMap = LkanvasUtils.clone(map)
        assertTrue(clonedMap is Map<*, *> && clonedMap == map)
        assertEquals("x", LkanvasUtils.clone("x"))
        assertNull(LkanvasUtils.clone(null))
    }

    // ---------- 格式化 ----------

    @Test
    fun formatValue_fallsBackOnInvalid() {
        assertEquals("—", LkanvasUtils.formatValue(Double.NaN))
        assertEquals(0.0, LkanvasUtils.formatValue(0.0, "—"))
        assertEquals("—", LkanvasUtils.formatValue(null))
    }

    @Test
    fun formatPrecision_padsToFixed() {
        assertEquals("3.14", LkanvasUtils.formatPrecision(3.14159, 2))
        assertEquals("3.141590", LkanvasUtils.formatPrecision(3.14159, 6))
        assertEquals("4", LkanvasUtils.formatPrecision(3.7, 0)) // toFixed 四舍五入
        assertEquals("3.14", LkanvasUtils.formatPrecision(3.14159)) // 默认 2 位
    }

    @Test
    fun formatBigNumber_scalesKMB() {
        assertEquals("150B", LkanvasUtils.formatBigNumber(1.5e11))
        assertEquals("-1.5B", LkanvasUtils.formatBigNumber(-1.5e9))
        assertEquals("150M", LkanvasUtils.formatBigNumber(1.5e8))
        assertEquals("15K", LkanvasUtils.formatBigNumber(15000.0))
        assertEquals("1.5K", LkanvasUtils.formatBigNumber(1500.0))
        assertEquals("999", LkanvasUtils.formatBigNumber(999.0))
    }

    @Test
    fun formatDate_replacesTemplatePlaceholders() {
        // 2026-03-05 07:08:09 UTC；formatDate 走上游 formatTimestampByTemplate（YYYY/MM/DD/HH/mm/ss），
        // 未传时区=系统时区，期望值按同一时区构造
        val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, 2, 5, 7, 8, 9)
        }.timeInMillis
        val expected = {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = utc
            "%04d-%02d-%02d %02d:%02d".format(
                cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.DAY_OF_MONTH), cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE),
            )
        }()
        assertEquals(expected, LkanvasUtils.formatDate(utc))
        assertEquals(
            {
                val cal = java.util.Calendar.getInstance()
                cal.timeInMillis = utc
                "%02d:%02d:%02d".format(
                    cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE),
                    cal.get(java.util.Calendar.SECOND),
                )
            }(),
            LkanvasUtils.formatDate(utc, "HH:mm:ss"),
        )
        assertEquals(
            {
                val cal = java.util.Calendar.getInstance()
                cal.timeInMillis = utc
                "%04d/%02d/%02d".format(
                    cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1,
                    cal.get(java.util.Calendar.DAY_OF_MONTH),
                )
            }(),
            LkanvasUtils.formatDate(utc, "YYYY/MM/DD"),
        )
        // 显式时区：上游 Options.timezone 消费
        assertEquals("2026-03-05 15:08", Format.formatTimestampByTemplate(utc, "YYYY-MM-DD HH:mm", "Asia/Shanghai"))
    }

    @Test
    fun formatThousands_insertsSeparator() {
        assertEquals("1,234,567.89", LkanvasUtils.formatThousands(1234567.89))
        assertEquals("123", LkanvasUtils.formatThousands(123.0))
        assertEquals("1,000", LkanvasUtils.formatThousands(1000.0))
    }

    @Test
    fun formatFoldDecimal_collapsesLeadingZeros() {
        assertEquals("0.0{4}12", LkanvasUtils.formatFoldDecimal(0.000012))
        assertEquals("0.001", LkanvasUtils.formatFoldDecimal(0.001)) // 2 个 0，未达阈值
        assertEquals("123.45", LkanvasUtils.formatFoldDecimal(123.45))
    }

    @Test
    fun calcTextWidth_isProportional() {
        assertEquals(6.0, LkanvasUtils.calcTextWidth("abc", 10.0, 0.2))
        assertEquals(0.0, LkanvasUtils.calcTextWidth("", 10.0))
    }

    // ---------- 线性数学 ----------

    @Test
    fun linearMath_slopeInterceptRoundTrip() {
        val a = io.lkanvas.core.figure.P(0.0, 0.0)
        val b = io.lkanvas.core.figure.P(10.0, 10.0)
        val (k, intercept) = LkanvasUtils.getLinearSlopeIntercept(a, b)!!
        assertEquals(1.0, k)
        assertEquals(0.0, intercept)
        assertEquals(5.0, LkanvasUtils.getLinearYFromSlopeIntercept(5.0, k, intercept))
        assertEquals(5.0, LkanvasUtils.getLinearYFromCoordinates(5.0, a, b))
        assertNull(LkanvasUtils.getLinearSlopeIntercept(a, io.lkanvas.core.figure.P(0.0, 9.0)))
        assertNull(LkanvasUtils.getLinearYFromCoordinates(3.0, a, io.lkanvas.core.figure.P(0.0, 9.0)))
    }

    // ---------- 命中包装（委托 FigureHit） ----------

    @Test
    fun checkCoordinateOn_familyDelegatesToFigureHit() {
        assertTrue(LkanvasUtils.checkCoordinateOnCircle(0.0, 0.0, 10.0, 0.0, 10.0))
        assertFalse(LkanvasUtils.checkCoordinateOnCircle(0.0, 0.0, 10.0, 50.0, 50.0))
        assertTrue(LkanvasUtils.checkCoordinateOnLine(0.0, 0.0, 10.0, 10.0, 5.0, 5.0))
        assertTrue(LkanvasUtils.checkCoordinateOnRect(0.0, 0.0, 10.0, 10.0, 5.0, 5.0))
        assertTrue(
            LkanvasUtils.checkCoordinateOnPolygon(
                listOf(
                    io.lkanvas.core.figure.P(0.0, 0.0),
                    io.lkanvas.core.figure.P(10.0, 0.0),
                    io.lkanvas.core.figure.P(5.0, 10.0),
                ),
                5.0,
                3.0,
            ),
        )
        assertTrue(LkanvasUtils.checkCoordinateOnArc(0.0, 0.0, 10.0, 0.0, 360.0, 10.0, 0.0))
        assertTrue(LkanvasUtils.checkCoordinateOnText(0.0, 0.0, "hi", 0.0, 0.0))
    }
}
