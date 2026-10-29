package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-02 straightLine：两点确定的无限直线（上游 straightLine.ts 镜像）。 */
object StraightLineDefinition : OverlayDefinition {
    override val name = "straightLine"
    override val pointCount = 2
}
