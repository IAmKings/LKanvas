package io.lkanvas.chart.styles

import io.lkanvas.core.common.utils.Merge

/**
 * 上游 `utils.merge` 语义：DeepPartial 深合并（对象递归合并）。
 * 对齐上游 common/utils/typeChecks.ts merge 行为——**按提供的属性合并**
 * （Kotlin 约定：字段值非 null 即已提供，null 即未提供；显式设回默认值
 * 的 patch 会正常生效）。实现委托 core/common/utils/Merge.kt。
 */
object StylesMerge {

    /** 上游 `merge(target, source)` 语义（提供即覆盖；null=未提供）。 */
    fun merge(base: Any?, patch: Any?): Any? = Merge.merge(base, patch)

    /** 历史 API：与 [merge] 同语义（上游无 skip-defaults 分支；保留名以兼容调用方）。 */
    @Deprecated(
        message = "上游 merge 无 skip-defaults 语义；本函数与 merge 相同，请直接使用 merge",
        replaceWith = ReplaceWith("merge(base, patch)"),
    )
    fun mergeStrict(base: Any?, patch: Any?): Any? = Merge.merge(base, patch)

    /** 便捷：字符串主题名 → Styles（注册表查询；未知名回退 light）。 */
    fun resolveTheme(name: String?): Styles = StylesThemes.getStyles(name) ?: StylesThemes.light
}

/** 上游 extension/styles：light/dark 主题注册表（默认样式之上的色板覆盖）。 */
object StylesThemes {

    /**
     * 上游 `extension/styles/light.ts` 镜像：在 getDefaultStyles() 基础上应用
     * light 覆盖路径（与基础默认不同的字段：crosshair text 边框/背景 #686D76）。
     * SLA-016：StylesDefaultsTest 逐字段 diff 守护。
     */
    val light: Styles = run {
        val base = Styles()
        val ch = base.crosshair
        base.copy(
            crosshair = ch.copy(
                horizontal = ch.horizontal.copy(
                    text = ch.horizontal.text.copy(borderColor = "#686D76", backgroundColor = "#686D76"),
                ),
                vertical = ch.vertical.copy(
                    text = ch.vertical.text.copy(borderColor = "#686D76", backgroundColor = "#686D76"),
                ),
            ),
        )
    }

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
