package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-06 horizontalStraightLine：全宽水平线（上游 horizontalStraightLine.ts 镜像）。 */
object HorizontalStraightLineDefinition : OverlayDefinition {
    override val name = "horizontalStraightLine"
    override val pointCount = 1
    override val constraint = OverlayConstraint.HORIZONTAL
}
