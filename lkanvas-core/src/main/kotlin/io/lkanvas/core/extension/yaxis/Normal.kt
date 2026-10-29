package io.lkanvas.core.extension.yaxis

import io.lkanvas.core.axis.YAxisSpec

/** 线性轴模板（上游 `extension/y-axis/normal.ts` 镜像）。 */
object NormalAxisTemplate {
    val name = "normal"
    fun createSpec(): YAxisSpec = YAxisSpec.Normal
}
