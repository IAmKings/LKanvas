package io.lkanvas.chart

import io.lkanvas.core.i18n.ChartLocale
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorRegistry
import io.lkanvas.core.overlay.OverlayDefinition
import io.lkanvas.core.overlay.OverlayTemplates

/**
 * 顶层注册 API——对齐上游 `index.ts` 的 register 系 / getSupported 系同名函数
 * （PRD 对齐批次 3-4）。全局作用域（与上游一致）：
 * 宿主注册的自定义模板对所有 [LKanvasChart] 实例可见。
 *
 * 差异登记：上游 registerStyles/registerXAxis/registerYAxis 依赖完整样式/
 * 轴体系（批次 5/4），此处仅暴露指标/覆盖物/文案三组 + figure 命名空间。
 */
object LkanvasRegistry {

    /** 27 内置指标模板工厂（避免全局注册表与实例注册表共享可变状态）。 */
    internal val BUILT_IN_INDICATOR_FACTORIES: Map<String, () -> IndicatorDefinition> = mapOf(
        "MA" to { io.lkanvas.core.indicator.builtin.MovingAverageIndicator() },
        "VOL" to { io.lkanvas.core.indicator.builtin.VolumeIndicator() },
        "MACD" to { io.lkanvas.core.indicator.builtin.MacdIndicator() },
        "EMA" to { io.lkanvas.core.indicator.builtin.ExponentialMovingAverageIndicator() },
        "SMA" to { io.lkanvas.core.indicator.builtin.SimpleMovingAverageIndicator() },
        "BOLL" to { io.lkanvas.core.indicator.builtin.BollingerBandsIndicator() },
        "BBI" to { io.lkanvas.core.indicator.builtin.BullAndBearIndexIndicator() },
        "BIAS" to { io.lkanvas.core.indicator.builtin.BiasIndicator() },
        "AO" to { io.lkanvas.core.indicator.builtin.AwesomeOscillatorIndicator() },
        "MTM" to { io.lkanvas.core.indicator.builtin.MomentumIndicator() },
        "ROC" to { io.lkanvas.core.indicator.builtin.RateOfChangeIndicator() },
        "PSY" to { io.lkanvas.core.indicator.builtin.PsychologicalLineIndicator() },
        "KDJ" to { io.lkanvas.core.indicator.builtin.StochIndicator() },
        "RSI" to { io.lkanvas.core.indicator.builtin.RelativeStrengthIndexIndicator() },
        "WR" to { io.lkanvas.core.indicator.builtin.WilliamsRIndicator() },
        "CCI" to { io.lkanvas.core.indicator.builtin.CommodityChannelIndexIndicator() },
        "DMI" to { io.lkanvas.core.indicator.builtin.DirectionalMovementIndexIndicator() },
        "BRAR" to { io.lkanvas.core.indicator.builtin.BrarIndicator() },
        "CR" to { io.lkanvas.core.indicator.builtin.CurrentRatioIndicator() },
        "VR" to { io.lkanvas.core.indicator.builtin.VolumeRatioIndicator() },
        "EMV" to { io.lkanvas.core.indicator.builtin.EaseOfMovementValueIndicator() },
        "SAR" to { io.lkanvas.core.indicator.builtin.StopAndReverseIndicator() },
        "TRIX" to { io.lkanvas.core.indicator.builtin.TrixIndicator() },
        "OBV" to { io.lkanvas.core.indicator.builtin.OnBalanceVolumeIndicator() },
        "PVT" to { io.lkanvas.core.indicator.builtin.PriceAndVolumeTrendIndicator() },
        "AVP" to { io.lkanvas.core.indicator.builtin.AveragePriceIndicator() },
        "DMA" to { io.lkanvas.core.indicator.builtin.DifferentOfMovingAverageIndicator() },
    )

    /** 全局指标模板注册表（27 内置已预注册）。 */
    val indicators = IndicatorRegistry().apply {
        // 预注册 27 内置（与 ChartState.registry 初始一致；自定义指标经 registerIndicator 追加）
        BUILT_IN_INDICATOR_FACTORIES.forEach { (name, factory) ->
            if (get(name) == null) register(factory())
        }
    }

    /** 全局覆盖物模板注册表（16 内置已预注册）。 */
    private val overlays = LinkedHashMap<String, OverlayDefinition>(OverlayTemplates.all)

    /** 全局文案注册表（zh-CN/en-US 已预注册）。 */
    val locales = LinkedHashMap<String, ChartLocale>().apply {
        put("zh-CN", ChartLocale.ZH_CN)
        put("en-US", ChartLocale.EN_US)
    }

    /** 上游 `registerIndicator(template)`：同名冲突返回 false（需宿主显式 replace）。 */
    fun registerIndicator(template: IndicatorDefinition, replace: Boolean = false): Boolean =
        indicators.register(template, replace)

    /** 上游 `getSupportedIndicators()`。 */
    fun getSupportedIndicators(): List<String> = indicators.all().map { it.name }

    /** 上游 `registerOverlay(template)`。 */
    fun registerOverlay(template: OverlayDefinition, replace: Boolean = false): Boolean {
        if (!replace && overlays.containsKey(template.name)) return false
        overlays[template.name] = template
        return true
    }

    /** 上游 `getSupportedOverlays()`。 */
    fun getSupportedOverlays(): List<String> = overlays.keys.toList()

    /** 上游 `getOverlayClass(name)` 等价：返回模板定义。 */
    fun getOverlayDefinition(name: String): OverlayDefinition? = overlays[name]

    /** 上游 `registerLocale(name, locale)`。 */
    fun registerLocale(name: String, locale: ChartLocale) {
        locales[name] = locale
    }

    /** 上游 `getSupportedLocales()`。 */
    fun getSupportedLocales(): List<String> = locales.keys.toList()

    fun getLocale(name: String): ChartLocale? = locales[name]

}
