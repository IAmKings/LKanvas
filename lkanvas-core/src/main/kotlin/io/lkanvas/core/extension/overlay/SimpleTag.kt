package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayDefinition

/** O-15 simpleTag：简单标签（上游 simpleTag.ts 镜像）。 */
object SimpleTagDefinition : OverlayDefinition {
    override val name = "simpleTag"
    override val pointCount = 1
    override val needDefaultPointFigure = false
    override val needDefaultXAxisFigure = false
    override val needDefaultYAxisFigure = false
}
