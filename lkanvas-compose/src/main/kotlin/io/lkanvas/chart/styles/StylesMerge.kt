package io.lkanvas.chart.styles

import kotlin.reflect.full.primaryConstructor

/**
 * 上游 `utils.merge` 语义：DeepPartial 深合并（对象递归合并）。
 * 对齐上游 common/utils/typeChecks.ts merge 行为——setStyles(DeepPartial) 的实现基础。
 * 实现：kotlin-reflect 主构造遍历；非 data/异类型整体替换。
 */
object StylesMerge {

    /**
     * DeepPartial 深合并。Kotlin data class 无 undefined，约定：
     * patch 字段值 **等于 patch 类型默认实例同字段值** 时视为「未提供」，保留 base
     * （差异登记：上游以 undefined 表达未提供；Kotlin 以默认值等价表达）。
     */
    fun merge(base: Any?, patch: Any?): Any? = mergeInternal(base, patch, skipDefaults = true)

    /** 严格合并（字段值非 null 即覆盖；无默认值跳过语义）。 */
    fun mergeStrict(base: Any?, patch: Any?): Any? = mergeInternal(base, patch, skipDefaults = false)

    private fun mergeInternal(base: Any?, patch: Any?, skipDefaults: Boolean): Any? {
        if (patch == null) return base
        if (base == null) return patch
        if (base::class != patch::class) return patch
        val k = base::class
        if (!k.isData) return patch
        val primary = k.primaryConstructor ?: return patch
        val membersByName = k.members.associateBy { it.name }

        // 默认实例：本树 data class 全参数带默认值 → callBy(emptyMap)
        val patchDefault: Any? = if (skipDefaults) {
            runCatching { primary.callBy(emptyMap()) }.getOrNull()
        } else null

        val args = HashMap<kotlin.reflect.KParameter, Any?>()
        for (param in primary.parameters) {
            val name = param.name ?: continue
            val getter = membersByName[name] ?: continue
            val baseVal = getter.call(base)
            val patchVal = getter.call(patch)
            // DeepPartial 约定：patch 字段 == patch 类型默认值 → 视为未提供
            val isUnset = patchDefault != null && getter.call(patchDefault) == patchVal
            args[param] = when {
                patchVal == null -> baseVal
                isUnset -> baseVal
                baseVal != null && baseVal::class.isData && baseVal::class == patchVal::class ->
                    mergeInternal(baseVal, patchVal, skipDefaults)
                else -> patchVal
            }
        }
        return primary.callBy(args)
    }

    /** 便捷：字符串主题名 → Styles（注册表查询；未知名回退 light）。 */
    fun resolveTheme(name: String?): Styles = StylesThemes.getStyles(name) ?: StylesThemes.light
}

/** 上游 extension/styles：light/dark 主题注册表（默认样式之上的色板覆盖）。 */
object StylesThemes {

    val light: Styles = Styles()

    val dark: Styles = Styles(
        grid = Styles().grid.copy(horizontal = LineStyle(color = "#33353D", style = "dashed"), vertical = LineStyle(color = "#33353D", style = "dashed")),
        candle = Styles().candle.copy(
            bar = CandleBarColor(
                upColor = "#2DC08E", downColor = "#F92855", noChangeColor = "#76808F",
            ),
            priceMark = CandlePriceMarkStyle(
                high = CandleHighLowPriceMarkStyle(color = "#76808F"),
                low = CandleHighLowPriceMarkStyle(color = "#76808F"),
            ),
            tooltip = CandleTooltipStyle(
                rect = CandleTooltipRectStyle(color = "#24262E", borderColor = "#33353D"),
                title = TooltipTitleStyle(color = "#9295A5"),
                legend = TooltipLegendStyle(color = "#9295A5"),
            ),
        ),
        xAxis = Styles().xAxis.copy(
            axisLine = AxisLineStyle(color = "#33353D"),
            tickText = AxisTickTextStyle(color = "#9295A5"),
            tickLine = AxisTickLineStyle(color = "#33353D"),
        ),
        yAxis = Styles().yAxis.copy(
            axisLine = AxisLineStyle(color = "#33353D"),
            tickText = AxisTickTextStyle(color = "#9295A5"),
            tickLine = AxisTickLineStyle(color = "#33353D"),
        ),
        separator = SeparatorStyle(color = "#33353D"),
        crosshair = Styles().crosshair.copy(
            horizontal = CrosshairDirectionStyle(line = StateLineStyle(color = "#9295A5")),
            vertical = CrosshairDirectionStyle(line = StateLineStyle(color = "#9295A5")),
        ),
    )

    private val registry = linkedMapOf("light" to light, "dark" to dark)

    /** 上游 `registerStyles(name, styles)`。 */
    fun registerStyles(name: String, styles: Styles) {
        registry[name] = styles
    }

    /** 上游 getStyles(name)。 */
    fun getStyles(name: String?): Styles? = name?.let { registry[it] }

    fun registeredNames(): List<String> = registry.keys.toList()
}
