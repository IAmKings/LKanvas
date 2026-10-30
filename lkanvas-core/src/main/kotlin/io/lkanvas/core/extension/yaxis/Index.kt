package io.lkanvas.core.extension.yaxis

import io.lkanvas.core.axis.YAxisSpec

/**
 * Y 轴模板全局注册表（上游 `extension/y-axis/index.ts` 镜像，SLA-010）。
 * 注册与轴构造（LKanvasChart.createYAxis/overrideYAxis）共用同一张表。
 */
object YAxisTemplates {

    /**
     * 轴模板（T02，第三轮复审 2026-10-30）：携带完整默认配置——
     * reverse/inside/positionLeft/scrollZoomEnabled/gap 与 spec 一并进构造
     * （createYAxis 以模板字段为缺省，override 参数覆盖）。
     */
    interface AxisTemplate {
        val name: String
        val reverse: Boolean get() = false
        val inside: Boolean get() = false
        val positionLeft: Boolean get() = false
        val scrollZoomEnabled: Boolean get() = true
        val gapValues: List<Double> get() = emptyList()
        fun createSpec(base: Double? = null): YAxisSpec
    }

    private val templates = LinkedHashMap<String, AxisTemplate>()

    @Synchronized
    fun register(template: AxisTemplate, replace: Boolean = false): Boolean {
        if (!replace && templates.containsKey(template.name)) return false
        templates[template.name] = template
        return true
    }

    @Synchronized
    fun get(name: String): AxisTemplate? = templates[name]

    @Synchronized
    fun supported(): List<String> = templates.keys.toList()

    init {
        register(object : AxisTemplate {
            override val name = NormalAxisTemplate.name
            override fun createSpec(base: Double?) = NormalAxisTemplate.createSpec()
        })
        register(object : AxisTemplate {
            override val name = LogarithmAxisTemplate.name
            override fun createSpec(base: Double?) = LogarithmAxisTemplate.createSpec()
        })
        register(object : AxisTemplate {
            override val name = PercentageAxisTemplate.name
            override fun createSpec(base: Double?) = PercentageAxisTemplate.createSpec(base)
        })
    }
}
