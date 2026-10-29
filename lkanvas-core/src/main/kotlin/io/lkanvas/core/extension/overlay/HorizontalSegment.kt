package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-05 horizontalSegment：水平线段（上游 horizontalSegment.ts 镜像）。 */
object HorizontalSegmentDefinition : OverlayDefinition {
    override val name = "horizontalSegment"
    override val pointCount = 2
    override val constraint = OverlayConstraint.HORIZONTAL
}
