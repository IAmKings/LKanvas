package io.lkanvas.chart

import io.lkanvas.core.i18n.ChartLocale
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorRegistry
import io.lkanvas.core.overlay.OverlayDefinition
import io.lkanvas.core.extension.overlay.OverlayTemplates

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
        "MA" to { io.lkanvas.core.extension.indicator.MovingAverageIndicator() },
        "VOL" to { io.lkanvas.core.extension.indicator.VolumeIndicator() },
        "MACD" to { io.lkanvas.core.extension.indicator.MacdIndicator() },
        "EMA" to { io.lkanvas.core.extension.indicator.ExponentialMovingAverageIndicator() },
        "SMA" to { io.lkanvas.core.extension.indicator.SimpleMovingAverageIndicator() },
        "BOLL" to { io.lkanvas.core.extension.indicator.BollingerBandsIndicator() },
        "BBI" to { io.lkanvas.core.extension.indicator.BullAndBearIndexIndicator() },
        "BIAS" to { io.lkanvas.core.extension.indicator.BiasIndicator() },
        "AO" to { io.lkanvas.core.extension.indicator.AwesomeOscillatorIndicator() },
        "MTM" to { io.lkanvas.core.extension.indicator.MomentumIndicator() },
        "ROC" to { io.lkanvas.core.extension.indicator.RateOfChangeIndicator() },
        "PSY" to { io.lkanvas.core.extension.indicator.PsychologicalLineIndicator() },
        "KDJ" to { io.lkanvas.core.extension.indicator.StochIndicator() },
        "RSI" to { io.lkanvas.core.extension.indicator.RelativeStrengthIndexIndicator() },
        "WR" to { io.lkanvas.core.extension.indicator.WilliamsRIndicator() },
        "CCI" to { io.lkanvas.core.extension.indicator.CommodityChannelIndexIndicator() },
        "DMI" to { io.lkanvas.core.extension.indicator.DirectionalMovementIndexIndicator() },
        "BRAR" to { io.lkanvas.core.extension.indicator.BrarIndicator() },
        "CR" to { io.lkanvas.core.extension.indicator.CurrentRatioIndicator() },
        "VR" to { io.lkanvas.core.extension.indicator.VolumeRatioIndicator() },
        "EMV" to { io.lkanvas.core.extension.indicator.EaseOfMovementValueIndicator() },
        "SAR" to { io.lkanvas.core.extension.indicator.StopAndReverseIndicator() },
        "TRIX" to { io.lkanvas.core.extension.indicator.TrixIndicator() },
        "OBV" to { io.lkanvas.core.extension.indicator.OnBalanceVolumeIndicator() },
        "PVT" to { io.lkanvas.core.extension.indicator.PriceAndVolumeTrendIndicator() },
        "AVP" to { io.lkanvas.core.extension.indicator.AveragePriceIndicator() },
        "DMA" to { io.lkanvas.core.extension.indicator.DifferentOfMovingAverageIndicator() },
    )

    /** 全局文案注册表（zh-CN/en-US 已预注册）。 */
    val locales = LinkedHashMap<String, ChartLocale>().apply {
        put("zh-CN", ChartLocale.ZH_CN)
        put("en-US", ChartLocale.EN_US)
    }

    /**
     * 上游 `registerIndicator(template)`：写入 core 全局模板表（SLA-004 统一源：
     * 注册与图表创建共用同一张表，既有/新建图表均可创建）。
     * 同名冲突返回 false（需宿主显式 replace；上游为直接覆盖，差异登记 R-14）。
     */
    fun registerIndicator(template: IndicatorDefinition, replace: Boolean = false): Boolean =
        io.lkanvas.core.extension.indicator.IndicatorTemplates.register(template, replace)

    /** 上游 `getSupportedIndicators()`。 */
    fun getSupportedIndicators(): List<String> =
        io.lkanvas.core.extension.indicator.IndicatorTemplates.supported()

    /**
     * 上游 `registerOverlay(template)`：写入 core 全局覆盖物表（SLA-004/013 统一源：
     * 创建/渲染/命中/手势事件共用同一张表）。
     */
    fun registerOverlay(template: OverlayDefinition, replace: Boolean = false): Boolean =
        io.lkanvas.core.extension.overlay.OverlayTemplates.register(template, replace)

    /** 上游 `getSupportedOverlays()`。 */
    fun getSupportedOverlays(): List<String> =
        io.lkanvas.core.extension.overlay.OverlayTemplates.supported()

    /** 上游 `getOverlayClass(name)` 等价：返回模板定义。 */
    fun getOverlayDefinition(name: String): OverlayDefinition? =
        io.lkanvas.core.extension.overlay.OverlayTemplates.getDefinition(name)

    /** 上游 `registerLocale(name, locale)`。 */
    fun registerLocale(name: String, locale: ChartLocale) {
        locales[name] = locale
    }

    /** 上游 `getSupportedLocales()`。 */
    fun getSupportedLocales(): List<String> = locales.keys.toList()

    fun getLocale(name: String): ChartLocale? = locales[name]

    // ---- 样式与轴模板注册（上游 extension/styles|x-axis|y-axis 同名） ----

    /**
     * 上游 `registerStyles(name, styles)` / `getStyles(name)`。
     * light/dark 已预注册（[io.lkanvas.chart.styles.StylesThemes]）。
     */
    fun registerStyles(name: String, styles: io.lkanvas.chart.styles.Styles) =
        io.lkanvas.chart.styles.StylesThemes.registerStyles(name, styles)

    fun getStyleTheme(name: String): io.lkanvas.chart.styles.Styles? =
        io.lkanvas.chart.styles.StylesThemes.getStyles(name)

    /** 上游 `getSupportedStyles()` 等价：已注册主题名。 */
    fun getSupportedStyles(): List<String> = io.lkanvas.chart.styles.StylesThemes.registeredNames()

    /**
     * 上游 `AxisTemplate` 门面建模（R06，复审 2026-10-30：注册与 createYAxis
     * 构造**共用 core 一张表**——注册即进入构造查询，公开注册的模板可被
     * `YAxisOverride(template=name)` 使用）。
     */
    interface AxisTemplate {
        val name: String
        val reverse: Boolean get() = false
        val inside: Boolean get() = false
        val position: AxisPosition get() = AxisPosition.RIGHT
        val scrollZoomEnabled: Boolean get() = true
        val gap: AxisGap get() = AxisGap()

        /** 模板构造轴规格（宿主可覆写；默认线性）。 */
        fun createSpec(base: Double? = null): io.lkanvas.core.axis.YAxisSpec = io.lkanvas.core.axis.YAxisSpec.Normal
    }

    /** facade 模板 → core 注册表适配器（spec/reverse/inside 桥接）。 */
    private fun toCoreTemplate(axis: AxisTemplate): io.lkanvas.core.extension.yaxis.YAxisTemplates.AxisTemplate =
        object : io.lkanvas.core.extension.yaxis.YAxisTemplates.AxisTemplate {
            override val name = axis.name
            override fun createSpec(base: Double?): io.lkanvas.core.axis.YAxisSpec = axis.createSpec(base)
        }

    /** x 轴模板视图（core 表的门面视图；x 轴当前无构造消费，登记差异）。 */
    val xAxisTemplates = LinkedHashMap<String, AxisTemplate>().apply {
        put("normal", object : AxisTemplate { override val name = "normal" })
    }

    /** 上游 `registerXAxis(axis)`。 */
    fun registerXAxis(axis: AxisTemplate) { xAxisTemplates[axis.name] = axis }

    /** 上游 `registerYAxis(axis)`：写 **core 全局模板表**（与 createYAxis 同源）。 */
    fun registerYAxis(axis: AxisTemplate) {
        io.lkanvas.core.extension.yaxis.YAxisTemplates.register(toCoreTemplate(axis))
    }

    /** 上游 `getXAxisClass(name)` 等价：门面视图。 */
    fun getXAxisTemplate(name: String): AxisTemplate? = xAxisTemplates[name]

    /** 上游 `getYAxisClass(name)` 等价：core 表门面视图（spec 构造保留）。 */
    fun getYAxisTemplate(name: String): AxisTemplate? {
        val core = io.lkanvas.core.extension.yaxis.YAxisTemplates.get(name) ?: return null
        return object : AxisTemplate {
            override val name = core.name
            override fun createSpec(base: Double?): io.lkanvas.core.axis.YAxisSpec = core.createSpec(base)
        }
    }

}
