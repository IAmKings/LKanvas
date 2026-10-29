package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-09 verticalStraightLine：全高垂直线（上游 verticalStraightLine.ts 镜像）。 */
object VerticalStraightLineDefinition : OverlayDefinition {
    override val name = "verticalStraightLine"
    override val pointCount = 1
    override val constraint = OverlayConstraint.VERTICAL
}
