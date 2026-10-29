package io.lkanvas.core.extension.yaxis

import io.lkanvas.core.axis.YAxisSpec

/** 百分比轴模板（上游 `extension/y-axis/percentage.ts` 镜像；基准零点 D-01）。 */
object PercentageAxisTemplate {
    val name = "percentage"
    fun createSpec(base: Double? = null): YAxisSpec = YAxisSpec.Percentage(base)
}
