package io.lkanvas.core.extension.yaxis

import io.lkanvas.core.axis.YAxisSpec

/** 对数轴模板（上游 `extension/y-axis/logarithm.ts` 镜像；带符号 log10）。 */
object LogarithmAxisTemplate {
    val name = "logarithm"
    fun createSpec(): YAxisSpec = YAxisSpec.Logarithm
}
