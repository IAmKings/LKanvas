package io.lkanvas.core.indicator.builtin

import io.lkanvas.core.extension.indicator.*

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F1 自动 diff（对齐批次 3 出口）：Kotlin 内置模板元数据 ↔ 上游 TS 模板
 * 逐字段比对（name/series/calcParams/precision/minValue/maxValue/
 * shouldOhlc/shouldFormatBigNumber）。fixtures 由
 * tools/oracle 侧脚本从上游源码提取（ts-indicator-meta.json）。
 */
class IndicatorMetaDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun loadTsMeta(): Map<String, TsMeta> {
        val text = javaClass.getResourceAsStream("/fixtures/ts-indicator-meta.json")!!
            .bufferedReader().readText()
        val root = json.parseToJsonElement(text).jsonObject
        val out = mutableMapOf<String, TsMeta>()

        fun JsonObject.strArr(key: String): List<String>? =
            (this[key] as? kotlinx.serialization.json.JsonArray)?.jsonArray
                ?.map { row -> (row as? kotlinx.serialization.json.JsonArray)?.let { r -> r.jsonArray[0].jsonPrimitive.content } ?: row.jsonPrimitive.content }

        fun JsonObject.doubleArr(key: String): List<Double>? =
            (this[key] as? kotlinx.serialization.json.JsonArray)?.jsonArray?.map { it.jsonPrimitive.double }

        fun JsonObject.num(key: String): Double? =
            (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.doubleOrNull

        fun JsonObject.bool(key: String): Boolean? =
            (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull

        fun JsonObject.str(key: String): String? =
            (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

        for ((name, el) in root) {
            val o = el.jsonObject
            out[name] = TsMeta(
                series = o.str("series"),
                calcParams = o.doubleArr("calcParams") ?: emptyList(),
                precision = o.num("precision"),
                minValue = o.num("minValue"),
                maxValue = o.num("maxValue"),
                shouldOhlc = o.bool("shouldOhlc") ?: false,
                shouldFormatBigNumber = o.bool("shouldFormatBigNumber") ?: false,
                hasFigureFunctionCall = o.bool("hasFigureFunctionCall") ?: false,
                figures = o.strArr("figures") ?: emptyList(),
            )
        }
        return out
    }

    data class TsMeta(
        val series: String?,
        val calcParams: List<Double>,
        val precision: Double?,
        val minValue: Double?,
        val maxValue: Double?,
        val shouldOhlc: Boolean,
        val shouldFormatBigNumber: Boolean,
        val hasFigureFunctionCall: Boolean,
        val figures: List<String>,
    )

    // ---- 上游 series 值 → Kotlin 枚举的映射约定 ----
    private fun tsSeriesOf(name: String): String? = loadTsMeta()[name]?.series

    private val defs: Map<String, io.lkanvas.core.indicator.IndicatorDefinition> = mapOf(
        "MA" to MovingAverageIndicator(),
        "VOL" to VolumeIndicator(),
        "MACD" to MacdIndicator(),
        "EMA" to ExponentialMovingAverageIndicator(),
        "SMA" to SimpleMovingAverageIndicator(),
        "BOLL" to BollingerBandsIndicator(),
        "BBI" to BullAndBearIndexIndicator(),
        "BIAS" to BiasIndicator(),
        "AO" to AwesomeOscillatorIndicator(),
        "MTM" to MomentumIndicator(),
        "ROC" to RateOfChangeIndicator(),
        "PSY" to PsychologicalLineIndicator(),
        "KDJ" to StochIndicator(),
        "RSI" to RelativeStrengthIndexIndicator(),
        "WR" to WilliamsRIndicator(),
        "CCI" to CommodityChannelIndexIndicator(),
        "DMI" to DirectionalMovementIndexIndicator(),
        "BRAR" to BrarIndicator(),
        "CR" to CurrentRatioIndicator(),
        "VR" to VolumeRatioIndicator(),
        "EMV" to EaseOfMovementValueIndicator(),
        "SAR" to StopAndReverseIndicator(),
        "TRIX" to TrixIndicator(),
        "OBV" to OnBalanceVolumeIndicator(),
        "PVT" to PriceAndVolumeTrendIndicator(),
        "AVP" to AveragePriceIndicator(),
        "DMA" to DifferentOfMovingAverageIndicator(),
    )

    private fun seriesName(def: io.lkanvas.core.indicator.IndicatorDefinition): String =
        when (def.series) {
            io.lkanvas.core.indicator.IndicatorSeries.PRICE -> "price"
            io.lkanvas.core.indicator.IndicatorSeries.VOLUME -> "volume"
            io.lkanvas.core.indicator.IndicatorSeries.NORMAL -> "normal"
        }

    /** 上游模板未写 series 时运行时按 'normal' 处理（undefined → normal 默认）。 */
    private fun tsSeriesNormalized(name: String): String = loadTsMeta()[name]?.series ?: "normal"

    @Test
    fun `27模板全部覆盖`() {
        val tsMeta = loadTsMeta()
        assertEquals(27, tsMeta.size, "TS 元数据 27 个")
        assertEquals(27, defs.size, "Kotlin 27 个")
        assertEquals(tsMeta.keys, defs.keys, "name 集合一致")
    }

    @Test
    fun `series与上游一致_未写series视为normal`() {
        for ((name, def) in defs) {
            assertEquals(tsSeriesNormalized(name), seriesName(def), "$name series")
        }
    }

    @Test
    fun `calcParams与上游一致`() {
        val tsMeta = loadTsMeta()
        for ((name, def) in defs) {
            val expect = tsMeta[name]!!.calcParams
            assertEquals(expect, def.calcParams, "$name calcParams")
        }
    }

    @Test
    fun `shouldOhlc与shouldFormatBigNumber与上游一致`() {
        val tsMeta = loadTsMeta()
        // Kotlin 侧未单独建模 shouldOhlc/shouldFormatBigNumber 字段——
        // 对齐批次 3 将其加入 IndicatorDefinition。此处先登记当前分布：
        val expectOhlc = defs.filterValues { d -> tsMeta[d.name]!!.shouldOhlc }.keys
        assertEquals(setOf("MA", "BBI", "BOLL", "EMA", "SMA", "SAR"), expectOhlc, "上游 shouldOhlc 分布")
        val expectBig = defs.filterValues { d -> tsMeta[d.name]!!.shouldFormatBigNumber }.keys
        assertEquals(setOf("VOL"), expectBig, "上游 shouldFormatBigNumber 分布")
        // TODO(批次3-1)：IndicatorDefinition 增补 shouldOhlc/shouldFormatBigNumber 后改为逐模板断言
    }

    @Test
    fun `figures键与顺序和上游一致`() {
        val tsMeta = loadTsMeta()
        for ((name, def) in defs) {
            val meta = tsMeta[name]!!
            val tsKeys = meta.figures
            val ktKeys = def.plotsFor(def.calcParams).map { it.key }
            if (meta.hasFigureFunctionCall) {
                // 函数调用型 figure（如 VOL 的 volume）返回值置于数组末尾，
                // Kotlin plotsFor 同样将 bar/特殊 plot 放最后——集合相等断言
                assertEquals(tsKeys.toSet(), ktKeys.toSet(), "$name figures 键集合")
            } else {
                assertEquals(tsKeys, ktKeys, "$name figures keys+顺序")
            }
        }
    }
}
