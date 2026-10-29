package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayDefinition

/** O-16 brush：毛笔（上游 brush.ts 镜像；连续采样上限 5000 点）。 */
object BrushDefinition : OverlayDefinition {
    override val name = "brush"
    override val pointCount = 0
    override val continuous = true
    override val maxPoints = 5000
    override val supportMagnet = false
    override val totalStep = 2
    override val needDefaultPointFigure = false
    override val needDefaultXAxisFigure = false
    override val needDefaultYAxisFigure = false
}
