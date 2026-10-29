package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-08 verticalSegment：垂直线段（上游 verticalSegment.ts 镜像）。 */
object VerticalSegmentDefinition : OverlayDefinition {
    override val name = "verticalSegment"
    override val pointCount = 2
    override val constraint = OverlayConstraint.VERTICAL
}
